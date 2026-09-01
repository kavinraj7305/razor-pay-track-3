package com.razorpayhackthon.revenue_recovery.service.plan.handler.paymentcancelled;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("paymentcancelledStep2LowPriSms")
class Step2LowPriSms implements PaymentCancelledStep {

	private final DevPlaybookOps ops;

	Step2LowPriSms(DevPlaybookOps ops) {
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
		return "Step 2: low-priority SMS if they still want to pay";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(
				recoveryCase,
				"You cancelled a ₹"
						+ recoveryCase.getAmountAtRisk()
						+ " payment. Reply if you still want to complete it. (DEV SMS)");
		ops.finish(action, planNote());
	}
}
