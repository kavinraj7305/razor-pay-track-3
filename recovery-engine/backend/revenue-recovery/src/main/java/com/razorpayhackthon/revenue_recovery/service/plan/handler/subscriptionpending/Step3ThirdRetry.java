package com.razorpayhackthon.revenue_recovery.service.plan.handler.subscriptionpending;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("subscriptionpendingStep3ThirdRetry")
class Step3ThirdRetry implements SubscriptionPendingStep {

	private final DevPlaybookOps ops;

	Step3ThirdRetry(DevPlaybookOps ops) {
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
		return "Step 3: last auto-retry before warning SMS";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.retryAndFail(recoveryCase, action, 3, planNote());
	}
}
