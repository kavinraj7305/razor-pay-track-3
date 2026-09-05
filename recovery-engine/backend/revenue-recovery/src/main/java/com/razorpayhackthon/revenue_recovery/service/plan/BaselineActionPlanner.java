package com.razorpayhackthon.revenue_recovery.service.plan;

import com.razorpayhackthon.revenue_recovery.entity.AuditEvent;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.BaselineReasonHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks the first matching reason handler and writes recovery_action. No ML.
 */
@Service
public class BaselineActionPlanner {

	private static final Logger log = LoggerFactory.getLogger(BaselineActionPlanner.class);

	private final RecoveryActionRepository recoveryActionRepository;
	private final AuditEventRepository auditEventRepository;
	private final RecoveryCaseRepository recoveryCaseRepository;
	private final List<BaselineReasonHandler> reasonHandlers;

	public BaselineActionPlanner(
			RecoveryActionRepository recoveryActionRepository,
			AuditEventRepository auditEventRepository,
			RecoveryCaseRepository recoveryCaseRepository,
			List<BaselineReasonHandler> reasonHandlers) {
		this.recoveryActionRepository = recoveryActionRepository;
		this.auditEventRepository = auditEventRepository;
		this.recoveryCaseRepository = recoveryCaseRepository;
		this.reasonHandlers = reasonHandlers;
	}

	@Transactional
	public void planFor(RecoveryCase recoveryCase) {
		if (!recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId()).isEmpty()) {
			log.info("Baseline action already exists caseId={}", recoveryCase.getCaseId());
			return;
		}

		BaselineReasonHandler handler = pick(recoveryCase);
		PlannedDecision planned = handler.decide(recoveryCase);

		PlaybookClock.Window window = PlaybookClock.of(recoveryCase.getReason(), 1);
		RecoveryAction action = new RecoveryAction();
		action.setActionId("act_" + UUID.randomUUID().toString().replace("-", ""));
		action.setRecoveryCase(recoveryCase);
		action.setActionType(planned.actionType());
		action.setStatus(planned.status());
		action.setAttemptNumber(1);
		action.setReason(window.label() + " · " + planned.note());
		action.setWaitHours(window.hours());
		action.setScheduleLabel(window.label());
		recoveryActionRepository.save(action);

		recoveryCase.setStatus(planned.caseStatus());
		recoveryCaseRepository.save(recoveryCase);

		Map<String, Object> plannedAudit = new LinkedHashMap<>();
		plannedAudit.put("actionType", planned.actionType().name());
		plannedAudit.put("actionStatus", planned.status().name());
		plannedAudit.put("failureReason", String.valueOf(recoveryCase.getReason()));
		plannedAudit.put("source", recoveryCase.getSource().name());
		plannedAudit.put("handler", handler.getClass().getSimpleName());
		plannedAudit.put("note", planned.note());
		plannedAudit.putAll(PlaybookClock.auditFields(recoveryCase.getReason(), 1, recoveryCase.getCreatedAt()));
		audit(
				recoveryCase,
				planned.blocked() ? "BASELINE_ACTION_BLOCKED" : "BASELINE_ACTION_PLANNED",
				planned.blocked() ? "BLOCK" : "PLAN",
				plannedAudit);

		log.info(
				"Baseline {} caseId={} handler={} action={} status={}",
				planned.blocked() ? "blocked" : "planned",
				recoveryCase.getCaseId(),
				handler.getClass().getSimpleName(),
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

	public BaselineReasonHandler pick(RecoveryCase recoveryCase) {
		return reasonHandlers.stream()
				.filter(handler -> handler.supports(recoveryCase))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No baseline handler for case " + recoveryCase.getCaseId()));
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
}
