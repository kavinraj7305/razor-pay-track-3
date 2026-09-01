package com.razorpayhackthon.revenue_recovery.service.plan.handler.subscriptionhalted;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("subscriptionhaltedStep2SmsNudge")
class Step2SmsNudge implements SubscriptionHaltedStep {

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
		return "Step 2: SMS nudge to restart the subscription";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(recoveryCase, "Your subscription is halted. Use the link to pay ₹"
				+ recoveryCase.getAmountAtRisk()
				+ " and restart. (DEV SMS)");
		ops.finish(action, planNote());
	}
}
