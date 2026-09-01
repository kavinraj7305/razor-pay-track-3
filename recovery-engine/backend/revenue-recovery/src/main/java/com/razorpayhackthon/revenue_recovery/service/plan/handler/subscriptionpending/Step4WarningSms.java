package com.razorpayhackthon.revenue_recovery.service.plan.handler.subscriptionpending;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("subscriptionpendingStep4WarningSms")
class Step4WarningSms implements SubscriptionPendingStep {

	private final DevPlaybookOps ops;

	Step4WarningSms(DevPlaybookOps ops) {
		this.ops = ops;
	}

	@Override
	public int stepNumber() {
		return 4;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.SEND_SMS;
	}

	@Override
	public String planNote() {
		return "Step 4: warning SMS — subscription may halt next";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(
				recoveryCase,
				"Your ₹"
						+ recoveryCase.getAmountAtRisk()
						+ " subscription retry failed. Update payment method to avoid halt. (DEV SMS)");
		ops.finish(action, planNote());
	}
}
