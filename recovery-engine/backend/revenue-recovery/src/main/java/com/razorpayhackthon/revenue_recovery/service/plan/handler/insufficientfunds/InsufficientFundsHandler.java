package com.razorpayhackthon.revenue_recovery.service.plan.handler.insufficientfunds;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.service.plan.PlannedDecision;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.BaselineReasonHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Order(10)
public class InsufficientFundsHandler implements BaselineReasonHandler {

	private static final int LAST_STEP = 4;

	private final List<InsufficientFundsStep> steps;
	private final RecoveryActionRepository recoveryActionRepository;
	private final RecoveryCaseRepository recoveryCaseRepository;

	public InsufficientFundsHandler(
			List<InsufficientFundsStep> steps,
			RecoveryActionRepository recoveryActionRepository,
			RecoveryCaseRepository recoveryCaseRepository) {
		this.steps = steps;
		this.recoveryActionRepository = recoveryActionRepository;
		this.recoveryCaseRepository = recoveryCaseRepository;
	}

	@Override
	public boolean supports(RecoveryCase recoveryCase) {
		return reasonOf(recoveryCase).contains("insufficient_funds");
	}

	@Override
	public PlannedDecision decide(RecoveryCase recoveryCase) {
		return new PlannedDecision(
				RecoveryActionType.RETRY_PAYMENT,
				RecoveryActionStatus.PLANNED,
				RecoveryCaseStatus.ACTION_PLANNED,
				false,
				"Step 1: silent delayed retry (same instrument)");
	}

	public int executeNext(RecoveryCase recoveryCase) {
		int next = nextStepNumber(recoveryCase);
		if (next > LAST_STEP) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "insufficient_funds steps are finished");
		}
		InsufficientFundsStep step = step(next);
		RecoveryAction action = openAction(recoveryCase, step);
		recoveryCase.setStatus(RecoveryCaseStatus.RECOVERING);
		step.execute(recoveryCase, action);
		if (action.getStatus() == RecoveryActionStatus.EXECUTED
				&& action.getActionType() == RecoveryActionType.RETRY_PAYMENT) {
			recoveryCase.setStatus(RecoveryCaseStatus.RECOVERED);
			recoveryCase.setClosedAt(LocalDateTime.now());
		}
		recoveryActionRepository.save(action);
		recoveryCaseRepository.save(recoveryCase);
		return next;
	}

	private int nextStepNumber(RecoveryCase recoveryCase) {
		return recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId()).stream()
						.filter(
								action ->
										action.getStatus() == RecoveryActionStatus.EXECUTED
												|| action.getStatus() == RecoveryActionStatus.FAILED
												|| action.getStatus() == RecoveryActionStatus.CANCELLED)
						.mapToInt(action -> action.getAttemptNumber() == null ? 0 : action.getAttemptNumber())
						.max()
						.orElse(0)
				+ 1;
	}

	private RecoveryAction openAction(RecoveryCase recoveryCase, InsufficientFundsStep step) {
		List<RecoveryAction> existing =
				recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId());
		for (RecoveryAction action : existing) {
			if (action.getAttemptNumber() != null
					&& action.getAttemptNumber() == step.stepNumber()
					&& action.getStatus() == RecoveryActionStatus.PLANNED) {
				return action;
			}
		}
		RecoveryAction action = new RecoveryAction();
		action.setActionId("act_" + UUID.randomUUID().toString().replace("-", ""));
		action.setRecoveryCase(recoveryCase);
		action.setActionType(step.actionType());
		action.setStatus(RecoveryActionStatus.PLANNED);
		action.setAttemptNumber(step.stepNumber());
		action.setReason(step.planNote());
		return recoveryActionRepository.save(action);
	}

	private InsufficientFundsStep step(int number) {
		return steps.stream()
				.filter(candidate -> candidate.stepNumber() == number)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No insufficient_funds step " + number));
	}
}
