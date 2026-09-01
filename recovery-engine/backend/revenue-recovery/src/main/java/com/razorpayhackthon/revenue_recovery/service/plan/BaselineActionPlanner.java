package com.razorpayhackthon.revenue_recovery.service.plan;

import com.razorpayhackthon.revenue_recovery.entity.AuditEvent;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic (no ML) first action for a new case. Kafka/Redis come next.
 */
@Service
public class BaselineActionPlanner {

	private static final Logger log = LoggerFactory.getLogger(BaselineActionPlanner.class);

	private final RecoveryActionRepository recoveryActionRepository;
	private final AuditEventRepository auditEventRepository;
	private final RecoveryCaseRepository recoveryCaseRepository;

	public BaselineActionPlanner(
			RecoveryActionRepository recoveryActionRepository,
			AuditEventRepository auditEventRepository,
			RecoveryCaseRepository recoveryCaseRepository) {
		this.recoveryActionRepository = recoveryActionRepository;
		this.auditEventRepository = auditEventRepository;
		this.recoveryCaseRepository = recoveryCaseRepository;
	}

	@Transactional
	public void planFor(RecoveryCase recoveryCase) {
		if (!recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId()).isEmpty()) {
			log.info("Baseline action already exists caseId={}", recoveryCase.getCaseId());
			return;
		}

		Planned planned = decide(recoveryCase);

		RecoveryAction action = new RecoveryAction();
		action.setActionId("act_" + UUID.randomUUID().toString().replace("-", ""));
		action.setRecoveryCase(recoveryCase);
		action.setActionType(planned.actionType());
		action.setStatus(planned.status());
		action.setAttemptNumber(1);
		action.setReason(planned.note());
		recoveryActionRepository.save(action);

		recoveryCase.setStatus(planned.caseStatus());
		recoveryCaseRepository.save(recoveryCase);

		audit(
				recoveryCase,
				planned.blocked() ? "BASELINE_ACTION_BLOCKED" : "BASELINE_ACTION_PLANNED",
				planned.blocked() ? "BLOCK" : "PLAN",
				Map.of(
						"actionType", planned.actionType().name(),
						"actionStatus", planned.status().name(),
						"failureReason", String.valueOf(recoveryCase.getReason()),
						"source", recoveryCase.getSource().name(),
						"note", planned.note()));

		log.info(
				"Baseline {} caseId={} action={} status={}",
				planned.blocked() ? "blocked" : "planned",
				recoveryCase.getCaseId(),
				planned.actionType(),
				planned.status());
	}

	@Transactional
	public void recordRecovered(RecoveryCase recoveryCase) {
		audit(
				recoveryCase,
				"RECOVERY_CASE_RECOVERED",
				"CLOSE",
				Map.of(
						"sourceId", recoveryCase.getSourceId(),
						"amountAtRisk", String.valueOf(recoveryCase.getAmountAtRisk())));
	}

	static Planned decide(RecoveryCase recoveryCase) {
		String reason = recoveryCase.getReason() == null ? "" : recoveryCase.getReason().toLowerCase();
		RecoverySource source = recoveryCase.getSource();

		if (reason.contains("payment_risk_check_failed") || reason.contains("payment_cancelled")) {
			return new Planned(
					RecoveryActionType.SEND_EMAIL,
					RecoveryActionStatus.CANCELLED,
					RecoveryCaseStatus.OPEN,
					true,
					"STOP: no auto-retry on risk/cancel — escalate to human");
		}
		if (source == RecoverySource.INVOICE || reason.contains("invoice.expired")) {
			return new Planned(
					RecoveryActionType.REQUEST_PROMISE_TO_PAY,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"B2B chase: capture promise-to-pay");
		}
		if (source == RecoverySource.CHECKOUT_SESSION || reason.contains("checkout.abandoned")) {
			return new Planned(
					RecoveryActionType.SEND_PAYMENT_LINK,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"Checkout drop-off: send payment link");
		}
		if (reason.contains("subscription.halted")) {
			return new Planned(
					RecoveryActionType.SEND_PAYMENT_LINK,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"Retries exhausted: send link to update mandate");
		}
		if (reason.contains("card_expired") || reason.contains("invalid_vpa")) {
			return new Planned(
					RecoveryActionType.SEND_PAYMENT_LINK,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"Instrument dead: send payment link (do not retry same card)");
		}
		if (reason.contains("insufficient_funds")
				|| reason.contains("gateway_technical")
				|| reason.contains("bank_technical")
				|| reason.contains("subscription.pending")) {
			return new Planned(
					RecoveryActionType.RETRY_PAYMENT,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"Transient fail: delayed retry");
		}
		return new Planned(
				RecoveryActionType.RETRY_PAYMENT,
				RecoveryActionStatus.PLANNED,
				RecoveryCaseStatus.ACTION_PLANNED,
				false,
				"Default baseline: retry then link, max 3");
	}

	private void audit(RecoveryCase recoveryCase, String eventType, String action, Map<String, Object> details) {
		AuditEvent auditEvent = new AuditEvent();
		auditEvent.setEventId("aud_" + UUID.randomUUID().toString().replace("-", ""));
		auditEvent.setRecoveryCase(recoveryCase);
		auditEvent.setEventType(eventType);
		auditEvent.setActorType("SYSTEM");
		auditEvent.setActorId("baseline-engine");
		auditEvent.setAction(action);
		auditEvent.setDetails(new LinkedHashMap<>(details));
		auditEventRepository.save(auditEvent);
	}

	record Planned(
			RecoveryActionType actionType,
			RecoveryActionStatus status,
			RecoveryCaseStatus caseStatus,
			boolean blocked,
			String note) {}
}
