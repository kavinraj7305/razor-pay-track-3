package com.razorpayhackthon.revenue_recovery.service.plan.handler.paymenttimedout;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("paymenttimedoutStep1SilentRetry")
class Step1SilentRetry implements PaymentTimedOutStep {

	private final DevPlaybookOps ops;

	Step1SilentRetry(DevPlaybookOps ops) {
		this.ops = ops;
	}

	@Override
	public int stepNumber() {
		return 1;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.RETRY_PAYMENT;
	}

	@Override
	public String planNote() {
		return "Step 1: short wait, then retry the same instrument once";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.retryAndFail(recoveryCase, action, 1, planNote());
	}
}
