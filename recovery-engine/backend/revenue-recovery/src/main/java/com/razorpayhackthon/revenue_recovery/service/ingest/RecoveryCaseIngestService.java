package com.razorpayhackthon.revenue_recovery.service.ingest;

import com.razorpayhackthon.revenue_recovery.entity.Customer;
import com.razorpayhackthon.revenue_recovery.entity.Merchant;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.entity.WebhookEvent;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.enums.WebhookIntake;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.repository.WebhookEventRepository;
import com.razorpayhackthon.revenue_recovery.service.plan.AuditWriter;
import com.razorpayhackthon.revenue_recovery.service.plan.BaselineActionPlanner;
import com.razorpayhackthon.revenue_recovery.webhook.ParsedRazorpayEvent;
import com.razorpayhackthon.revenue_recovery.webhook.RazorpayWebhookParser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RecoveryCaseIngestService {

	private static final Logger log = LoggerFactory.getLogger(RecoveryCaseIngestService.class);

	private final RazorpayWebhookParser parser;
	private final JsonMapper jsonMapper;
	private final WebhookEventRepository webhookEventRepository;
	private final RecoveryCaseRepository recoveryCaseRepository;
	private final RecoveryCaseDraftFactory draftFactory;
	private final MerchantCustomerService merchantCustomerService;
	private final RecoverySourceWriter recoverySourceWriter;
	private final BaselineActionPlanner baselineActionPlanner;
	private final AuditWriter auditWriter;

	public RecoveryCaseIngestService(
			RazorpayWebhookParser parser,
			JsonMapper jsonMapper,
			WebhookEventRepository webhookEventRepository,
			RecoveryCaseRepository recoveryCaseRepository,
			RecoveryCaseDraftFactory draftFactory,
			MerchantCustomerService merchantCustomerService,
			RecoverySourceWriter recoverySourceWriter,
			BaselineActionPlanner baselineActionPlanner,
			AuditWriter auditWriter) {
		this.parser = parser;
		this.jsonMapper = jsonMapper;
		this.webhookEventRepository = webhookEventRepository;
		this.recoveryCaseRepository = recoveryCaseRepository;
		this.draftFactory = draftFactory;
		this.merchantCustomerService = merchantCustomerService;
		this.recoverySourceWriter = recoverySourceWriter;
		this.baselineActionPlanner = baselineActionPlanner;
		this.auditWriter = auditWriter;
	}

	@Transactional
	public void consume(String rawBody) {
		ParsedRazorpayEvent parsed = parser.parse(rawBody);
		WebhookEvent stored = persistInbox(parsed);
		switch (parsed.eventType()) {
			case "payment.failed",
					"subscription.pending",
					"subscription.halted",
					"invoice.expired",
					"checkout.abandoned" ->
					openCase(parsed);
			case "payment.captured", "order.paid", "invoice.paid", "subscription.charged" ->
					closeCase(parsed);
			default -> log.info("No recovery action for eventType={}", parsed.eventType());
		}
		stored.setProcessed(true);
		webhookEventRepository.save(stored);
		auditHmacIfSigned(stored, parsed);
	}

	private WebhookEvent persistInbox(ParsedRazorpayEvent parsed) {
		Optional<WebhookEvent> existing = webhookEventRepository.findByEventId(parsed.eventId());
		if (existing.isPresent()) {
			return existing.get();
		}
		WebhookEvent stored = new WebhookEvent();
		stored.setEventId(parsed.eventId());
		stored.setProvider("RAZORPAY");
		stored.setEventType(parsed.eventType());
		stored.setIntake(WebhookIntake.DESK_SIMULATE.name());
		stored.setSignatureVerified(false);
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = jsonMapper.convertValue(parsed.root(), Map.class);
		stored.setPayload(payload);
		return webhookEventRepository.save(stored);
	}

	private void auditHmacIfSigned(WebhookEvent stored, ParsedRazorpayEvent parsed) {
		if (!stored.isSignatureVerified()) {
			return;
		}
		JsonNode payload = parsed.root().get("payload");
		String sourceId = WebhookPayloadSupport.firstNonBlank(
				WebhookPayloadSupport.idOf(payload, "payment"),
				WebhookPayloadSupport.idOf(payload, "subscription"),
				WebhookPayloadSupport.idOf(payload, "invoice"),
				WebhookPayloadSupport.idOf(payload, "checkout"),
				WebhookPayloadSupport.idOf(payload, "order"));
		if (sourceId == null) {
			return;
		}
		for (RecoverySource source : RecoverySource.values()) {
			for (RecoveryCase recoveryCase : recoveryCaseRepository.findBySourceAndSourceId(source, sourceId)) {
				auditWriter.write(
						recoveryCase,
						"WEBHOOK_HMAC_VERIFIED",
						"ACK",
						"SYSTEM",
						"razorpay-webhook",
						Map.of(
								"eventId",
								parsed.eventId(),
								"eventType",
								parsed.eventType(),
								"accountId",
								parsed.accountId(),
								"intake",
								WebhookIntake.HMAC_SIGNED.name(),
								"signatureVerified",
								true));
				return;
			}
		}
	}

	private void openCase(ParsedRazorpayEvent parsed) {
		RecoveryCaseDraft draft = draftFactory.from(parsed);
		if (draft.amountAtRisk().compareTo(BigDecimal.ZERO) <= 0) {
			log.warn("Skipping RecoveryCase eventId={} — amountAtRisk must be > 0", parsed.eventId());
			return;
		}
		if (!recoveryCaseRepository.findBySourceAndSourceId(draft.source(), draft.sourceId()).isEmpty()) {
			log.info("RecoveryCase already exists source={} sourceId={}", draft.source(), draft.sourceId());
			return;
		}
		Merchant merchant = merchantCustomerService.upsertMerchant(parsed.accountId(), draft.currency());
		Customer customer = merchantCustomerService.upsertCustomer(merchant, parsed);
		recoverySourceWriter.write(parsed, merchant, customer, draft);

		RecoveryCase recoveryCase = new RecoveryCase();
		recoveryCase.setCaseId("rc_" + UUID.randomUUID().toString().replace("-", ""));
		recoveryCase.setMerchant(merchant);
		recoveryCase.setCustomer(customer);
		recoveryCase.setSource(draft.source());
		recoveryCase.setSourceId(draft.sourceId());
		recoveryCase.setAmountAtRisk(draft.amountAtRisk());
		recoveryCase.setCurrency(draft.currency());
		recoveryCase.setReason(draft.reason());
		recoveryCase.setStatus(RecoveryCaseStatus.OPEN);
		recoveryCase.setPriority(draft.priority());
		recoveryCaseRepository.save(recoveryCase);
		baselineActionPlanner.planFor(recoveryCase);
		log.info(
				"Opened RecoveryCase caseId={} source={} sourceId={} amountAtRisk={}",
				recoveryCase.getCaseId(),
				draft.source(),
				draft.sourceId(),
				draft.amountAtRisk());
	}

	private void closeCase(ParsedRazorpayEvent parsed) {
		JsonNode payload = parsed.root().get("payload");
		closeIfPresent(RecoverySource.PAYMENT, WebhookPayloadSupport.idOf(payload, "payment"));
		closeIfPresent(RecoverySource.INVOICE, WebhookPayloadSupport.idOf(payload, "invoice"));
		closeIfPresent(RecoverySource.SUBSCRIPTION, WebhookPayloadSupport.idOf(payload, "subscription"));
		closeIfPresent(RecoverySource.CHECKOUT_SESSION, WebhookPayloadSupport.idOf(payload, "order"));
	}

	private void closeIfPresent(RecoverySource source, String sourceId) {
		if (sourceId == null) {
			return;
		}
		LocalDateTime now = LocalDateTime.now();
		for (RecoveryCase recoveryCase : recoveryCaseRepository.findBySourceAndSourceId(source, sourceId)) {
			if (recoveryCase.getStatus() == RecoveryCaseStatus.RECOVERED
					|| recoveryCase.getStatus() == RecoveryCaseStatus.FAILED
					|| recoveryCase.getStatus() == RecoveryCaseStatus.EXPIRED) {
				continue;
			}
			recoveryCase.setStatus(RecoveryCaseStatus.RECOVERED);
			recoveryCase.setClosedAt(now);
			recoveryCaseRepository.save(recoveryCase);
			baselineActionPlanner.recordRecovered(recoveryCase);
			log.info(
					"Closed RecoveryCase caseId={} source={} sourceId={}",
					recoveryCase.getCaseId(),
					source,
					sourceId);
		}
	}
}
