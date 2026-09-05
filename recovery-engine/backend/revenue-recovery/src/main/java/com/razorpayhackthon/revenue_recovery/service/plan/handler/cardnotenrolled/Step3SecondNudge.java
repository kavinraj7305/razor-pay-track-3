package com.razorpayhackthon.revenue_recovery.service.plan.handler.cardnotenrolled;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("cardnotenrolledStep3SecondNudge")
class Step3SecondNudge implements CardNotEnrolledStep {

	private final DevPlaybookOps ops;

	Step3SecondNudge(DevPlaybookOps ops) {
		this.ops = ops;
	}

	@Override
	public int stepNumber() {
		return 3;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.SEND_SMS;
	}

	@Override
	public String planNote() {
		return "Step 3: second reminder";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(
				recoveryCase,
				"Reminder: finish ₹" + recoveryCase.getAmountAtRisk() + " on the payment link (DEV SMS)");
		ops.finish(action, planNote());
	}
}
