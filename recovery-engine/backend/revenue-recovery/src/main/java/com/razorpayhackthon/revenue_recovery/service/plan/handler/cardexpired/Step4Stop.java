package com.razorpayhackthon.revenue_recovery.service.plan.handler.cardexpired;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("cardexpiredStep4Stop")
class Step4Stop implements CardExpiredStep {

	private final DevPlaybookOps ops;

	Step4Stop(DevPlaybookOps ops) {
		this.ops = ops;
	}

	@Override
	public int stepNumber() {
		return 4;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.SEND_EMAIL;
	}

	@Override
	public String planNote() {
		return "Step 4: stop — no more nudges";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.finish(action, planNote());
	}
}
