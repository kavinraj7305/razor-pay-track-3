package com.razorpayhackthon.revenue_recovery.service.webhook;

import com.razorpayhackthon.revenue_recovery.dto.ScenarioCatalogItem;
import com.razorpayhackthon.revenue_recovery.dto.SimulatedFailureResult;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.service.ingest.RecoveryCaseIngestService;
import com.razorpayhackthon.revenue_recovery.webhook.PreparedWebhook;
import com.razorpayhackthon.revenue_recovery.webhook.SimulateScenario;
import com.razorpayhackthon.revenue_recovery.webhook.SimulatedWebhookFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WebhookSimulateService {

	private final RecoveryCaseIngestService ingestService;
	private final RecoveryCaseRepository recoveryCaseRepository;
	private final RecoveryActionRepository recoveryActionRepository;

	public WebhookSimulateService(
			RecoveryCaseIngestService ingestService,
			RecoveryCaseRepository recoveryCaseRepository,
			RecoveryActionRepository recoveryActionRepository) {
		this.ingestService = ingestService;
		this.recoveryCaseRepository = recoveryCaseRepository;
		this.recoveryActionRepository = recoveryActionRepository;
	}

	public List<ScenarioCatalogItem> catalog() {
		return Arrays.stream(SimulateScenario.values())
				.map(
						s -> new ScenarioCatalogItem(
								s.slug(), s.eventType(), s.reason(), s.intendedAction(), s.path()))
				.toList();
	}

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

	public SimulatedFailureResult simulateLegacyPaymentFailed() {
		return run(SimulateScenario.INSUFFICIENT_FUNDS, Instant.now().toEpochMilli(), null);
	}

	public SimulatedFailureResult simulateOne(String slug) {
		if ("all".equals(slug)) {
			throw new IllegalArgumentException("Use GET /api/webhooks/simulate/all for the full pack");
		}
		return run(SimulateScenario.fromSlug(slug), Instant.now().toEpochMilli(), null);
	}

	private SimulatedFailureResult run(SimulateScenario scenario, long stamp, String capturedPaymentId) {
		String sourceId = sourceIdFor(scenario, stamp, capturedPaymentId);
		PreparedWebhook prepared = SimulatedWebhookFactory.prepare(scenario, stamp, sourceId);
		ingestService.consume(prepared.body());
		return toResult(scenario, prepared);
	}

	private String sourceIdFor(SimulateScenario scenario, long stamp, String capturedPaymentId) {
		return switch (scenario) {
			case INSUFFICIENT_FUNDS -> "pay_fnd_" + stamp;
			case CARD_EXPIRED -> "pay_exp_" + stamp;
			case RISK_FAILED -> "pay_rsk_" + stamp;
			case SUBSCRIPTION_PENDING -> "sub_pnd_" + stamp;
			case SUBSCRIPTION_HALTED -> "sub_hlt_" + stamp;
			case INVOICE_EXPIRED -> "inv_exp_" + stamp;
			case CHECKOUT_ABANDONED -> "chk_abd_" + stamp;
			case CARD_NOT_ENROLLED -> "pay_enr_" + stamp;
			case PAYMENT_TIMED_OUT -> "pay_tmo_" + stamp;
			case CARD_DECLINED -> "pay_dcl_" + stamp;
			case CURRENCY_NOT_SUPPORTED -> "pay_ccy_" + stamp;
			case PAYMENT_CAPTURED -> resolvePaymentToCapture(capturedPaymentId, stamp);
		};
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

	private SimulatedFailureResult toResult(SimulateScenario scenario, PreparedWebhook prepared) {
		List<RecoveryCase> cases =
				recoveryCaseRepository.findBySourceAndSourceId(prepared.source(), prepared.sourceId());
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
				prepared.eventId(),
				scenario.eventType(),
				recoveryCase != null ? recoveryCase.getCaseId() : null,
				prepared.sourceId(),
				recoveryCase != null ? recoveryCase.getAmountAtRisk() : null,
				recoveryCase != null ? recoveryCase.getReason() : scenario.reason(),
				recoveryCase != null ? recoveryCase.getStatus().name() : "NONE",
				action != null ? action.getActionType().name() : null,
				action != null ? action.getStatus().name() : null,
				action != null ? action.getReason() : scenario.intendedAction());
	}
}
