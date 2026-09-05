package com.razorpayhackthon.revenue_recovery.service.plan.handler.carddeclined;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("carddeclinedStep2SecondRetry")
class Step2SecondRetry implements CardDeclinedStep {

	private final DevPlaybookOps ops;

	Step2SecondRetry(DevPlaybookOps ops) {
		this.ops = ops;
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
		return "Step 2: second silent retry if still unpaid";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.retryAndFail(recoveryCase, action, 2, planNote());
	}
}
