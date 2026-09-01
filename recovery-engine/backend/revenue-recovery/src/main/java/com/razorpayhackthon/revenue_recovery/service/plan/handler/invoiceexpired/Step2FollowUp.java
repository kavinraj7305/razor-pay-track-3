package com.razorpayhackthon.revenue_recovery.service.plan.handler.invoiceexpired;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("invoiceexpiredStep2FollowUp")
class Step2FollowUp implements InvoiceExpiredStep {

	private final DevPlaybookOps ops;

	Step2FollowUp(DevPlaybookOps ops) {
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
		return "Step 2: follow-up SMS if no PTP";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(
				recoveryCase,
				"Follow-up: ₹"
						+ recoveryCase.getAmountAtRisk()
						+ " invoice is still unpaid. Share a pay-by date. (DEV SMS)");
		ops.finish(action, planNote());
	}
}
