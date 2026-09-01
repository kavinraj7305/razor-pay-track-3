package com.razorpayhackthon.revenue_recovery.service.plan.handler.checkoutabandoned;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("checkoutabandonedStep2SmsNudge")
class Step2SmsNudge implements CheckoutAbandonedStep {

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
		return "Step 2: SMS nudge to complete checkout";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(recoveryCase, "You left ₹"
				+ recoveryCase.getAmountAtRisk()
				+ " unpaid. Finish with the payment link. (DEV SMS)");
		ops.finish(action, planNote());
	}
}
