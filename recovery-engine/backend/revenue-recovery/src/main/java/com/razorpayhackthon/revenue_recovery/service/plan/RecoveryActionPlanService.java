package com.razorpayhackthon.revenue_recovery.service.plan;

import com.razorpayhackthon.revenue_recovery.dto.RecoveryCasePlanResponse;
import com.razorpayhackthon.revenue_recovery.dto.RecoveryCasePlanResponse.AuditLine;
import com.razorpayhackthon.revenue_recovery.dto.RecoveryCasePlanResponse.PlannedAction;
import com.razorpayhackthon.revenue_recovery.dto.RecoveryCaseSummary;
import com.razorpayhackthon.revenue_recovery.entity.AuditEvent;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.insufficientfunds.InsufficientFundsHandler;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecoveryActionPlanService {

	private final RecoveryCaseRepository recoveryCaseRepository;
	private final RecoveryActionRepository recoveryActionRepository;
	private final AuditEventRepository auditEventRepository;
	private final BaselineActionPlanner baselineActionPlanner;
	private final InsufficientFundsHandler insufficientFundsHandler;

	public RecoveryActionPlanService(
			RecoveryCaseRepository recoveryCaseRepository,
			RecoveryActionRepository recoveryActionRepository,
			AuditEventRepository auditEventRepository,
			BaselineActionPlanner baselineActionPlanner,
			InsufficientFundsHandler insufficientFundsHandler) {
		this.recoveryCaseRepository = recoveryCaseRepository;
		this.recoveryActionRepository = recoveryActionRepository;
		this.auditEventRepository = auditEventRepository;
		this.baselineActionPlanner = baselineActionPlanner;
		this.insufficientFundsHandler = insufficientFundsHandler;
	}

	@Transactional(readOnly = true)
	public List<RecoveryCaseSummary> list() {
		return recoveryCaseRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(this::toSummary)
				.toList();
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
		if (!insufficientFundsHandler.supports(recoveryCase)) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "execute is only wired for insufficient_funds right now");
		}
		baselineActionPlanner.planFor(recoveryCase);
		insufficientFundsHandler.executeNext(recoveryCase);
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

	private RecoveryCaseSummary toSummary(RecoveryCase recoveryCase) {
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
				first == null ? null : first.getStatus().name());
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
				audit.stream().map(this::toAudit).toList());
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
