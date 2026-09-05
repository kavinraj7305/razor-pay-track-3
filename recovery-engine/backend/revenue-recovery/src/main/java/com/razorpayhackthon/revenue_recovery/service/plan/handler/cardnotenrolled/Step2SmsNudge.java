package com.razorpayhackthon.revenue_recovery.service.plan.handler.cardnotenrolled;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("cardnotenrolledStep2SmsNudge")
class Step2SmsNudge implements CardNotEnrolledStep {

	private final DevPlaybookOps ops;

	Step2SmsNudge(DevPlaybookOps ops) {
		this.ops = ops;
	}

	@Override
	public int stepNumber() {
		return 2;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.SEND_SMS;
	}

	@Override
	public String planNote() {
		return "Step 2: text once to finish 3DS on the link";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(
				recoveryCase,
				"Complete the payment of ₹" + recoveryCase.getAmountAtRisk() + " using the link (DEV SMS)");
		ops.finish(action, planNote());
	}
}
