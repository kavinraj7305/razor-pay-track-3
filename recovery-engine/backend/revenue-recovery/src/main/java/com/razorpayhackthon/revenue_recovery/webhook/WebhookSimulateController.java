package com.razorpayhackthon.revenue_recovery.webhook;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.ingest.RecoveryCaseIngestService;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookSimulateController {

	private final RecoveryCaseIngestService ingestService;
	private final RecoveryCaseRepository recoveryCaseRepository;
	private final RecoveryActionRepository recoveryActionRepository;

	public WebhookSimulateController(
			RecoveryCaseIngestService ingestService,
			RecoveryCaseRepository recoveryCaseRepository,
			RecoveryActionRepository recoveryActionRepository) {
		this.ingestService = ingestService;
		this.recoveryCaseRepository = recoveryCaseRepository;
		this.recoveryActionRepository = recoveryActionRepository;
	}

	@RequestMapping(
			path = "/api/webhooks/simulate",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ScenarioCatalogItem> catalog() {
		return Arrays.stream(SimulateScenario.values())
				.map(
						s -> new ScenarioCatalogItem(
								s.slug(), s.eventType(), s.reason(), s.intendedAction(), s.path()))
				.toList();
	}

	@RequestMapping(
			path = "/api/webhooks/simulate/all",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public List<SimulatedFailureResult> simulateAll() {
		long stamp = Instant.now().toEpochMilli();
		List<SimulatedFailureResult> results = new ArrayList<>();
		String fundsPaymentId = null;
		for (SimulateScenario scenario : SimulateScenario.values()) {
			if (scenario == SimulateScenario.PAYMENT_CAPTURED) {
				results.add(run(scenario, stamp, fundsPaymentId));
			} else {
				SimulatedFailureResult result = run(scenario, stamp, null);
				results.add(result);
				if (scenario == SimulateScenario.INSUFFICIENT_FUNDS) {
					fundsPaymentId = result.sourceId();
				}
			}
		}
		return results;
	}

	@RequestMapping(
			path = "/api/webhooks/simulate/payment-failed",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public SimulatedFailureResult legacyPaymentFailed() {
		return run(SimulateScenario.INSUFFICIENT_FUNDS, Instant.now().toEpochMilli(), null);
	}

	@RequestMapping(
			path = "/api/webhooks/simulate/{slug}",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public SimulatedFailureResult simulateOne(@PathVariable String slug) {
		if ("all".equals(slug)) {
			throw new IllegalArgumentException("Use GET /api/webhooks/simulate/all for the full pack");
		}
		return run(SimulateScenario.fromSlug(slug), Instant.now().toEpochMilli(), null);
	}

	private SimulatedFailureResult run(SimulateScenario scenario, long stamp, String capturedPaymentId) {
		String eventId = trimId("evt_" + stamp + "_" + scenario.ordinal());

		String body;
		RecoverySource source;
		String sourceId;
		switch (scenario) {
			case INSUFFICIENT_FUNDS -> {
				sourceId = "pay_fnd_" + stamp;
				source = RecoverySource.PAYMENT;
				body = WebhookScenarioPayloads.paymentFailed(
						eventId,
						sourceId,
						49900,
						"card",
						"funds.user@example.com",
						"insufficient_funds",
						"BAD_REQUEST_ERROR",
						"Insufficient funds in account",
						stamp);
			}
			case CARD_EXPIRED -> {
				sourceId = "pay_exp_" + stamp;
				source = RecoverySource.PAYMENT;
				body = WebhookScenarioPayloads.paymentFailed(
						eventId,
						sourceId,
						129900,
						"card",
						"expired.card@example.com",
						"card_expired",
						"BAD_REQUEST_ERROR",
						"Card has expired",
						stamp);
			}
			case RISK_FAILED -> {
				sourceId = "pay_rsk_" + stamp;
				source = RecoverySource.PAYMENT;
				body = WebhookScenarioPayloads.paymentFailed(
						eventId,
						sourceId,
						8_000_000,
						"card",
						"risk.user@example.com",
						"payment_risk_check_failed",
						"BAD_REQUEST_ERROR",
						"Payment declined due to risk checks",
						stamp);
			}
			case SUBSCRIPTION_PENDING -> {
				sourceId = "sub_pnd_" + stamp;
				source = RecoverySource.SUBSCRIPTION;
				body = WebhookScenarioPayloads.subscription(
						"subscription.pending",
						eventId,
						sourceId,
						"pay_subp_" + stamp,
						99900,
						"insufficient_funds",
						"pending",
						stamp);
			}
			case SUBSCRIPTION_HALTED -> {
				sourceId = "sub_hlt_" + stamp;
				source = RecoverySource.SUBSCRIPTION;
				body = WebhookScenarioPayloads.subscription(
						"subscription.halted",
						eventId,
						sourceId,
						"pay_subh_" + stamp,
						199900,
						"card_declined",
						"halted",
						stamp);
			}
			case INVOICE_EXPIRED -> {
				sourceId = "inv_exp_" + stamp;
				source = RecoverySource.INVOICE;
				body = WebhookScenarioPayloads.invoiceExpired(eventId, sourceId, 250000, stamp);
			}
			case CHECKOUT_ABANDONED -> {
				sourceId = "chk_abd_" + stamp;
				source = RecoverySource.CHECKOUT_SESSION;
				body = WebhookScenarioPayloads.checkoutAbandoned(eventId, sourceId, 34900, stamp);
			}
			case PAYMENT_CAPTURED -> {
				source = RecoverySource.PAYMENT;
				sourceId = resolvePaymentToCapture(capturedPaymentId, stamp);
				body = WebhookScenarioPayloads.paymentCaptured(eventId, sourceId, 49900, stamp);
			}
			default -> throw new IllegalArgumentException("Unhandled scenario " + scenario);
		}

		ingestService.consume(body);
		List<RecoveryCase> cases = recoveryCaseRepository.findBySourceAndSourceId(source, sourceId);
		RecoveryCase recoveryCase = cases.isEmpty() ? null : cases.getFirst();
		RecoveryAction action = null;
		if (recoveryCase != null) {
			List<RecoveryAction> actions =
					recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId());
			if (!actions.isEmpty()) {
				action = actions.getFirst();
			}
		}
		return new SimulatedFailureResult(
				recoveryCase != null,
				scenario.slug(),
				eventId,
				scenario.eventType(),
				recoveryCase != null ? recoveryCase.getCaseId() : null,
				sourceId,
				recoveryCase != null ? recoveryCase.getAmountAtRisk() : null,
				recoveryCase != null ? recoveryCase.getReason() : scenario.reason(),
				recoveryCase != null ? recoveryCase.getStatus().name() : "NONE",
				action != null ? action.getActionType().name() : null,
				action != null ? action.getStatus().name() : null,
				action != null ? action.getReason() : scenario.intendedAction());
	}

	private String resolvePaymentToCapture(String capturedPaymentId, long stamp) {
		if (capturedPaymentId != null && !capturedPaymentId.isBlank()) {
			return capturedPaymentId;
		}
		List<RecoveryCase> open =
				recoveryCaseRepository.findBySourceAndStatus(RecoverySource.PAYMENT, RecoveryCaseStatus.OPEN);
		if (!open.isEmpty()) {
			return open.getFirst().getSourceId();
		}
		run(SimulateScenario.INSUFFICIENT_FUNDS, stamp, null);
		return "pay_fnd_" + stamp;
	}

	private static String trimId(String value) {
		return value.length() <= 50 ? value : value.substring(0, 50);
	}

	public record ScenarioCatalogItem(
			String slug, String eventType, String reason, String intendedAction, String path) {}

	public record SimulatedFailureResult(
			boolean stored,
			String scenario,
			String eventId,
			String eventType,
			String caseId,
			String sourceId,
			BigDecimal amountAtRisk,
			String reason,
			String status,
			String actionType,
			String actionStatus,
			String intendedAction) {}
}
