package com.razorpayhackthon.revenue_recovery.service.plan.handler.insufficientfunds;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("insufficientfundsStep3RetryWithSms")
class Step3RetryWithSms implements InsufficientFundsStep {

	private final DevPlaybookOps ops;

	Step3RetryWithSms(DevPlaybookOps ops) {
		this.ops = ops;
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
		ops.retryAndFail(recoveryCase, action, 3, planNote());
		ops.sms(
				recoveryCase,
				"Your ₹"
						+ recoveryCase.getAmountAtRisk()
						+ " payment failed (insufficient funds). Last auto-retry ran. (DEV SMS)");
	}
}
