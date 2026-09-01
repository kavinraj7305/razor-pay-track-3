package com.razorpayhackthon.revenue_recovery.service.plan.handler.insufficientfunds;

import com.razorpayhackthon.revenue_recovery.entity.Customer;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.notify.DevSmsService;
import com.razorpayhackthon.revenue_recovery.service.retry.DevPaymentRetryService;
import com.razorpayhackthon.revenue_recovery.service.retry.DevPaymentRetryService.Result;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
class Step3RetryWithSms implements InsufficientFundsStep {

	private final DevPaymentRetryService retryService;
	private final InsufficientFundsAttemptRecorder attemptRecorder;
	private final DevSmsService smsService;

	Step3RetryWithSms(
			DevPaymentRetryService retryService,
			InsufficientFundsAttemptRecorder attemptRecorder,
			DevSmsService smsService) {
		this.retryService = retryService;
		this.attemptRecorder = attemptRecorder;
		this.smsService = smsService;
	}

	@Override
	public int stepNumber() {
		return 3;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.RETRY_PAYMENT;
	}

	@Override
	public String planNote() {
		return "Step 3: last auto-retry plus SMS nudge";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		Result result = retryService.retry(recoveryCase, 3);
		attemptRecorder.record(recoveryCase, 3, result);
		Customer customer = recoveryCase.getCustomer();
		String to = customer == null ? null : customer.getPhone();
		smsService.send(
				to,
				"Your ₹"
						+ recoveryCase.getAmountAtRisk()
						+ " payment failed (insufficient funds). Last auto-retry ran. (DEV SMS)");
		action.setStatus(result.success() ? RecoveryActionStatus.EXECUTED : RecoveryActionStatus.FAILED);
		action.setExecutedAt(LocalDateTime.now());
		action.setReason(planNote() + " — " + result.message() + " + DEV SMS");
	}
}
