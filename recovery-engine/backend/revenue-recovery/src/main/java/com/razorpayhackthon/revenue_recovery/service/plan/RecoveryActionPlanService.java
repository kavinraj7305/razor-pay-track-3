package com.razorpayhackthon.revenue_recovery.service.plan;

import com.razorpayhackthon.revenue_recovery.dto.PlaybookStepPreview;
import com.razorpayhackthon.revenue_recovery.dto.RecoveryCasePlanResponse;
import com.razorpayhackthon.revenue_recovery.dto.RecoveryCasePlanResponse.AuditLine;
import com.razorpayhackthon.revenue_recovery.dto.RecoveryCasePlanResponse.PlannedAction;
import com.razorpayhackthon.revenue_recovery.dto.RecoveryCaseSummary;
import com.razorpayhackthon.revenue_recovery.dto.ScorePeek;
import com.razorpayhackthon.revenue_recovery.entity.AuditEvent;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.service.ml.MlDataGate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecoveryActionPlanService {

	private static final String ML_TRAINING_MERCHANT_ID = "acc_syn_training";

	private final RecoveryCaseRepository recoveryCaseRepository;
	private final RecoveryActionRepository recoveryActionRepository;
	private final AuditEventRepository auditEventRepository;
	private final BaselineActionPlanner baselineActionPlanner;
	private final MlDataGate mlDataGate;

	public RecoveryActionPlanService(
			RecoveryCaseRepository recoveryCaseRepository,
			RecoveryActionRepository recoveryActionRepository,
			AuditEventRepository auditEventRepository,
			BaselineActionPlanner baselineActionPlanner,
			MlDataGate mlDataGate) {
		this.recoveryCaseRepository = recoveryCaseRepository;
		this.recoveryActionRepository = recoveryActionRepository;
		this.auditEventRepository = auditEventRepository;
		this.baselineActionPlanner = baselineActionPlanner;
		this.mlDataGate = mlDataGate;
	}

	@Transactional(readOnly = true)
	public List<RecoveryCaseSummary> list() {
		List<RecoveryCase> cases = recoveryCaseRepository.findByMerchant_MerchantIdNotOrderByCreatedAtDesc(
				ML_TRAINING_MERCHANT_ID);
		List<RecoveryCaseSummary> rows = new ArrayList<>(cases.size());
		ScorePeek fallback = null;
		for (RecoveryCase recoveryCase : cases) {
			ScorePeek score = fallback != null ? fallback : mlDataGate.peek(recoveryCase);
			if (fallback == null && "UNAVAILABLE".equals(score.status())) {
				fallback = score;
			}
			rows.add(toSummary(recoveryCase, score));
		}
		return rows;
	}

	@Transactional
	public RecoveryCasePlanResponse runBaselinePlan(String caseId) {
		RecoveryCase recoveryCase = requireOpen(caseId);
		baselineActionPlanner.planFor(recoveryCase);
		return getPlan(caseId);
	}

	@Transactional
	public RecoveryCasePlanResponse executeNext(String caseId) {
		RecoveryCase recoveryCase = requireOpen(caseId);
		baselineActionPlanner.planFor(recoveryCase);
		MlDataGate.Decision gate = mlDataGate.beforeExecute(recoveryCase);
		if (!gate.skipRetry()) {
			baselineActionPlanner.pick(recoveryCase).executeNext(recoveryCase);
		}
		return getPlan(caseId);
	}

	@Transactional(readOnly = true)
	public RecoveryCasePlanResponse getPlan(String caseId) {
		RecoveryCase recoveryCase = requireCase(caseId);
		List<RecoveryAction> actions = recoveryActionRepository.findByRecoveryCase_CaseId(caseId);
		List<AuditEvent> audit = auditEventRepository.findByRecoveryCase_CaseIdOrderByCreatedAtAsc(caseId);
		return toPlan(recoveryCase, actions, audit);
	}

	private RecoveryCase requireOpen(String caseId) {
		RecoveryCase recoveryCase = requireCase(caseId);
		if (recoveryCase.getStatus() == RecoveryCaseStatus.RECOVERED
				|| recoveryCase.getStatus() == RecoveryCaseStatus.FAILED
				|| recoveryCase.getStatus() == RecoveryCaseStatus.EXPIRED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "case is closed");
		}
		return recoveryCase;
	}

	private RecoveryCase requireCase(String caseId) {
		return recoveryCaseRepository
				.findByCaseId(caseId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "recovery case not found"));
	}

	private RecoveryCaseSummary toSummary(RecoveryCase recoveryCase, ScorePeek score) {
		List<RecoveryAction> actions =
				recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId());
		RecoveryAction first = actions.isEmpty() ? null : actions.getFirst();
		return new RecoveryCaseSummary(
				recoveryCase.getCaseId(),
				recoveryCase.getSource().name(),
				recoveryCase.getSourceId(),
				recoveryCase.getReason(),
				recoveryCase.getStatus().name(),
				recoveryCase.getAmountAtRisk(),
				first == null ? null : first.getActionType().name(),
				first == null ? null : first.getStatus().name(),
				score.recoveryProbability(),
				score.status(),
				playbookFor(recoveryCase));
	}

	private RecoveryCasePlanResponse toPlan(
			RecoveryCase recoveryCase, List<RecoveryAction> actions, List<AuditEvent> audit) {
		List<PlannedAction> planned = actions.stream().map(this::toAction).toList();
		return new RecoveryCasePlanResponse(
				recoveryCase.getCaseId(),
				recoveryCase.getSource().name(),
				recoveryCase.getSourceId(),
				recoveryCase.getReason(),
				recoveryCase.getStatus().name(),
				recoveryCase.getAmountAtRisk(),
				recoveryCase.getCurrency(),
				recoveryCase.getPriority().name(),
				recoveryCase.getMerchant() == null ? null : recoveryCase.getMerchant().getMerchantId(),
				recoveryCase.getCustomer() == null ? null : recoveryCase.getCustomer().getCustomerId(),
				recoveryCase.getCreatedAt(),
				recoveryCase.getClosedAt(),
				planned.isEmpty() ? null : planned.getFirst(),
				planned,
				audit.stream().map(this::toAudit).toList(),
				playbookFor(recoveryCase),
				mlDataGate.peek(recoveryCase));
	}

	private List<PlaybookStepPreview> playbookFor(RecoveryCase recoveryCase) {
		String reason = recoveryCase.getReason() == null ? "" : recoveryCase.getReason().toLowerCase();
		if (recoveryCase.getStatus() == RecoveryCaseStatus.RECOVERED || reason.contains("captured")) {
			return List.of(new PlaybookStepPreview(1, "NO_ACTION", "Already recovered — close, do not chase"));
		}
		return baselineActionPlanner.pick(recoveryCase).playbook();
	}

	private PlannedAction toAction(RecoveryAction action) {
		return new PlannedAction(
				action.getActionId(),
				action.getActionType().name(),
				action.getStatus().name(),
				action.getAttemptNumber(),
				action.getReason(),
				action.getCreatedAt());
	}

	private AuditLine toAudit(AuditEvent event) {
		return new AuditLine(
				event.getEventId(), event.getEventType(), event.getAction(), event.getDetails(), event.getCreatedAt());
	}
}
