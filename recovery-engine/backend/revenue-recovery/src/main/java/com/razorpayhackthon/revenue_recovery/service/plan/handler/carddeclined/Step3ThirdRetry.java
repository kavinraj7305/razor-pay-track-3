package com.razorpayhackthon.revenue_recovery.service.plan.handler.carddeclined;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("carddeclinedStep3ThirdRetry")
class Step3ThirdRetry implements CardDeclinedStep {

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
		return "Step 3: last auto-retry before a payment link";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.retryAndFail(recoveryCase, action, 3, planNote());
	}
}
