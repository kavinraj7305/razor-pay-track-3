package com.razorpayhackthon.revenue_recovery.service.plan.handler.insufficientfunds;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.retry.DevPaymentRetryService;
import com.razorpayhackthon.revenue_recovery.service.retry.DevPaymentRetryService.Result;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
class Step2SecondRetry implements InsufficientFundsStep {

	private final DevPaymentRetryService retryService;
	private final InsufficientFundsAttemptRecorder attemptRecorder;

	Step2SecondRetry(DevPaymentRetryService retryService, InsufficientFundsAttemptRecorder attemptRecorder) {
		this.retryService = retryService;
		this.attemptRecorder = attemptRecorder;
	}

	@Override
	public int stepNumber() {
		return 2;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.RETRY_PAYMENT;
	}

	@Override
	public String planNote() {
		return "Step 2: second silent retry after a longer wait";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		Result result = retryService.retry(recoveryCase, 2);
		attemptRecorder.record(recoveryCase, 2, result);
		action.setStatus(result.success() ? RecoveryActionStatus.EXECUTED : RecoveryActionStatus.FAILED);
		action.setExecutedAt(LocalDateTime.now());
		action.setReason(planNote() + " — " + result.message());
	}
}
