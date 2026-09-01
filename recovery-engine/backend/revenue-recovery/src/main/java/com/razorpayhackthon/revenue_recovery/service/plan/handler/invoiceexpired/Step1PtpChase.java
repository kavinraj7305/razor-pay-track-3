package com.razorpayhackthon.revenue_recovery.service.plan.handler.invoiceexpired;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("invoiceexpiredStep1PtpChase")
class Step1PtpChase implements InvoiceExpiredStep {

	private final DevPlaybookOps ops;

	Step1PtpChase(DevPlaybookOps ops) {
		this.ops = ops;
	}

	@Override
	public int stepNumber() {
		return 1;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.REQUEST_PROMISE_TO_PAY;
	}

	@Override
	public String planNote() {
		return "Step 1: SMS chase for a promise-to-pay date";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(
				recoveryCase,
				"Invoice for ₹"
						+ recoveryCase.getAmountAtRisk()
						+ " expired. Reply with a pay-by date. (DEV SMS)");
		ops.finish(action, planNote());
	}
}
