package com.razorpayhackthon.revenue_recovery.ingest;

import com.razorpayhackthon.revenue_recovery.baseline.BaselineActionPlanner;
import com.razorpayhackthon.revenue_recovery.entity.CheckoutSession;
import com.razorpayhackthon.revenue_recovery.entity.Customer;
import com.razorpayhackthon.revenue_recovery.entity.Merchant;
import com.razorpayhackthon.revenue_recovery.entity.Payment;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.entity.WebhookEvent;
import com.razorpayhackthon.revenue_recovery.enums.CheckoutSessionStatus;
import com.razorpayhackthon.revenue_recovery.enums.CustomerStatus;
import com.razorpayhackthon.revenue_recovery.enums.MerchantStatus;
import com.razorpayhackthon.revenue_recovery.enums.PaymentStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryPriority;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.repository.CheckoutSessionRepository;
import com.razorpayhackthon.revenue_recovery.repository.CustomerRepository;
import com.razorpayhackthon.revenue_recovery.repository.MerchantRepository;
import com.razorpayhackthon.revenue_recovery.repository.PaymentRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.repository.WebhookEventRepository;
import com.razorpayhackthon.revenue_recovery.webhook.ParsedRazorpayEvent;
import com.razorpayhackthon.revenue_recovery.webhook.RazorpayWebhookParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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
	private final MerchantRepository merchantRepository;
	private final CustomerRepository customerRepository;
	private final PaymentRepository paymentRepository;
	private final CheckoutSessionRepository checkoutSessionRepository;
	private final RecoveryCaseRepository recoveryCaseRepository;
	private final BaselineActionPlanner baselineActionPlanner;

	public RecoveryCaseIngestService(
			RazorpayWebhookParser parser,
			JsonMapper jsonMapper,
			WebhookEventRepository webhookEventRepository,
			MerchantRepository merchantRepository,
			CustomerRepository customerRepository,
			PaymentRepository paymentRepository,
			CheckoutSessionRepository checkoutSessionRepository,
			RecoveryCaseRepository recoveryCaseRepository,
			BaselineActionPlanner baselineActionPlanner) {
		this.parser = parser;
		this.jsonMapper = jsonMapper;
		this.webhookEventRepository = webhookEventRepository;
		this.merchantRepository = merchantRepository;
		this.customerRepository = customerRepository;
		this.paymentRepository = paymentRepository;
		this.checkoutSessionRepository = checkoutSessionRepository;
		this.recoveryCaseRepository = recoveryCaseRepository;
		this.baselineActionPlanner = baselineActionPlanner;
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
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = jsonMapper.convertValue(parsed.root(), Map.class);
		stored.setPayload(payload);
		return webhookEventRepository.save(stored);
	}

	private void openCase(ParsedRazorpayEvent parsed) {
		CaseDraft draft = draftFrom(parsed);
		if (draft.amountAtRisk().compareTo(BigDecimal.ZERO) <= 0) {
			log.warn(
					"Skipping RecoveryCase eventId={} — amountAtRisk must be > 0", parsed.eventId());
			return;
		}

		List<RecoveryCase> existing =
				recoveryCaseRepository.findBySourceAndSourceId(draft.source(), draft.sourceId());
		if (!existing.isEmpty()) {
			log.info(
					"RecoveryCase already exists source={} sourceId={}",
					draft.source(),
					draft.sourceId());
			return;
		}

		Merchant merchant = upsertMerchant(parsed.accountId(), draft.currency());
		Customer customer = upsertCustomer(merchant, parsed);

		if ("payment.failed".equals(parsed.eventType())) {
			upsertFailedPayment(merchant, customer, parsed, draft);
		}
		if ("checkout.abandoned".equals(parsed.eventType())) {
			upsertAbandonedCheckout(merchant, customer, parsed, draft);
		}

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
		closeIfPresent(RecoverySource.PAYMENT, idOf(payload, "payment"));
		closeIfPresent(RecoverySource.INVOICE, idOf(payload, "invoice"));
		closeIfPresent(RecoverySource.SUBSCRIPTION, idOf(payload, "subscription"));
		closeIfPresent(RecoverySource.CHECKOUT_SESSION, idOf(payload, "order"));
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

	private CaseDraft draftFrom(ParsedRazorpayEvent parsed) {
		JsonNode payload = parsed.root().get("payload");
		return switch (parsed.eventType()) {
			case "payment.failed" -> {
				JsonNode payment = RazorpayWebhookParser.nestedEntity(payload, "payment");
				String failReason = reason(payment, parsed.eventType());
				RecoveryPriority priority =
						"payment_risk_check_failed".equals(failReason)
								? RecoveryPriority.CRITICAL
								: "insufficient_funds".equals(failReason)
										? RecoveryPriority.MEDIUM
										: RecoveryPriority.HIGH;
				yield new CaseDraft(
						RecoverySource.PAYMENT,
						text(payment, "id"),
						fromPaise(payment.get("amount")),
						currency(payment),
						failReason,
						priority);
			}
			case "subscription.pending", "subscription.halted" -> {
				JsonNode subscription = RazorpayWebhookParser.nestedEntity(payload, "subscription");
				JsonNode payment = RazorpayWebhookParser.nestedEntityOrNull(payload, "payment");
				BigDecimal amount =
						payment != null && payment.get("amount") != null && payment.get("amount").isNumber()
								? fromPaise(payment.get("amount"))
								: BigDecimal.ZERO;
				String reason = "subscription.halted".equals(parsed.eventType())
						? "subscription.halted"
						: "subscription.pending";
				yield new CaseDraft(
						RecoverySource.SUBSCRIPTION,
						text(subscription, "id"),
						amount,
						payment != null ? currency(payment) : "INR",
						reason,
						"subscription.halted".equals(parsed.eventType())
								? RecoveryPriority.HIGH
								: RecoveryPriority.MEDIUM);
			}
			case "invoice.expired" -> {
				JsonNode invoice = RazorpayWebhookParser.nestedEntity(payload, "invoice");
				BigDecimal amount = fromPaise(invoice.get("amount"));
				JsonNode paidNode = invoice.get("amount_paid");
				if (paidNode != null && paidNode.isNumber() && paidNode.asLong() > 0) {
					amount = amount.subtract(fromPaise(paidNode));
				}
				yield new CaseDraft(
						RecoverySource.INVOICE,
						text(invoice, "id"),
						amount,
						currency(invoice),
						truncate("invoice.expired", 100),
						RecoveryPriority.MEDIUM);
			}
			case "checkout.abandoned" -> {
				JsonNode checkout = RazorpayWebhookParser.nestedEntity(payload, "checkout");
				yield new CaseDraft(
						RecoverySource.CHECKOUT_SESSION,
						text(checkout, "id"),
						fromPaise(checkout.get("amount")),
						currency(checkout),
						truncate("checkout.abandoned", 100),
						RecoveryPriority.MEDIUM);
			}
			default -> throw new IllegalArgumentException("Unsupported recovery event " + parsed.eventType());
		};
	}

	private Merchant upsertMerchant(String accountId, String currency) {
		return merchantRepository
				.findByMerchantId(accountId)
				.orElseGet(
						() -> {
							Merchant merchant = new Merchant();
							merchant.setMerchantId(accountId);
							merchant.setName("Razorpay " + accountId);
							merchant.setDefaultCurrency(currency);
							merchant.setStatus(MerchantStatus.ACTIVE);
							return merchantRepository.save(merchant);
						});
	}

	private Customer upsertCustomer(Merchant merchant, ParsedRazorpayEvent parsed) {
		JsonNode payload = parsed.root().get("payload");
		JsonNode payment = RazorpayWebhookParser.nestedEntityOrNull(payload, "payment");
		JsonNode subscription = RazorpayWebhookParser.nestedEntityOrNull(payload, "subscription");
		JsonNode invoice = RazorpayWebhookParser.nestedEntityOrNull(payload, "invoice");

		JsonNode checkout = RazorpayWebhookParser.nestedEntityOrNull(payload, "checkout");

		String razorpayCustomerId = firstNonBlank(
				textOrNull(subscription, "customer_id"),
				textOrNull(invoice, "customer_id"),
				textOrNull(checkout, "customer_id"));
		String email = firstNonBlank(textOrNull(payment, "email"), textOrNull(checkout, "email"));
		String phone = firstNonBlank(textOrNull(payment, "contact"), textOrNull(checkout, "contact"));
		if (razorpayCustomerId == null && email == null && phone == null) {
			return null;
		}

		String customerId =
				razorpayCustomerId != null
						? razorpayCustomerId
						: "cust_" + sha256Prefix(merchant.getMerchantId() + "|" + (email != null ? email : phone));
		if (customerId.length() > 50) {
			customerId = customerId.substring(0, 50);
		}

		final String resolvedId = customerId;
		final String resolvedEmail = email;
		final String resolvedPhone = phone;
		return customerRepository
				.findByCustomerId(resolvedId)
				.orElseGet(
						() -> {
							Customer customer = new Customer();
							customer.setCustomerId(resolvedId);
							customer.setMerchant(merchant);
							customer.setName(nameFrom(resolvedEmail, resolvedId));
							customer.setEmail(resolvedEmail);
							customer.setPhone(resolvedPhone);
							customer.setStatus(CustomerStatus.ACTIVE);
							return customerRepository.save(customer);
						});
	}

	private void upsertFailedPayment(
			Merchant merchant, Customer customer, ParsedRazorpayEvent parsed, CaseDraft draft) {
		JsonNode paymentNode =
				RazorpayWebhookParser.nestedEntity(parsed.root().get("payload"), "payment");
		String paymentId = text(paymentNode, "id");
		Payment payment = paymentRepository.findByPaymentId(paymentId).orElseGet(Payment::new);
		payment.setPaymentId(paymentId);
		payment.setMerchant(merchant);
		payment.setCustomer(customer);
		payment.setAmount(draft.amountAtRisk());
		payment.setCurrency(draft.currency());
		payment.setStatus(PaymentStatus.FAILED);
		payment.setPaymentType(textOrNull(paymentNode, "method"));
		paymentRepository.save(payment);
	}

	private void upsertAbandonedCheckout(
			Merchant merchant, Customer customer, ParsedRazorpayEvent parsed, CaseDraft draft) {
		JsonNode checkoutNode =
				RazorpayWebhookParser.nestedEntity(parsed.root().get("payload"), "checkout");
		String checkoutId = text(checkoutNode, "id");
		CheckoutSession session =
				checkoutSessionRepository.findByCheckoutSessionId(checkoutId).orElseGet(CheckoutSession::new);
		session.setCheckoutSessionId(checkoutId);
		session.setMerchant(merchant);
		session.setCustomer(customer);
		session.setAmount(draft.amountAtRisk());
		session.setCurrency(draft.currency());
		session.setStatus(CheckoutSessionStatus.ABANDONED);
		session.setAbandonedAt(LocalDateTime.now());
		checkoutSessionRepository.save(session);
	}

	private static String idOf(JsonNode payload, String name) {
		JsonNode entity = RazorpayWebhookParser.nestedEntityOrNull(payload, name);
		return entity == null ? null : textOrNull(entity, "id");
	}

	private static String text(JsonNode node, String field) {
		String value = textOrNull(node, field);
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private static String textOrNull(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || value.asText().isBlank()) {
			return null;
		}
		return value.asText();
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static String currency(JsonNode entity) {
		String value = textOrNull(entity, "currency");
		if (value == null || value.length() != 3) {
			return "INR";
		}
		return value.toUpperCase(Locale.ROOT);
	}

	private static String reason(JsonNode payment, String eventType) {
		String value = firstNonBlank(
				textOrNull(payment, "error_reason"),
				textOrNull(payment, "error_description"),
				eventType);
		return truncate(value, 100);
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	private static BigDecimal fromPaise(JsonNode amountNode) {
		if (amountNode == null || !amountNode.isNumber()) {
			return BigDecimal.ZERO;
		}
		return BigDecimal.valueOf(amountNode.asLong()).movePointLeft(2);
	}

	private static String nameFrom(String email, String fallbackId) {
		if (email != null && email.contains("@")) {
			return email.substring(0, email.indexOf('@'));
		}
		return "Razorpay customer " + fallbackId;
	}

	private static String sha256Prefix(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of()
					.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
					.substring(0, 16);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required", ex);
		}
	}

	private record CaseDraft(
			RecoverySource source,
			String sourceId,
			BigDecimal amountAtRisk,
			String currency,
			String reason,
			RecoveryPriority priority) {}
}
