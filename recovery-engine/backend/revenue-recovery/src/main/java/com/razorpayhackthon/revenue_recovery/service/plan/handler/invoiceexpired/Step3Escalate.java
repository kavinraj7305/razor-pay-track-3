package com.razorpayhackthon.revenue_recovery.service.plan.handler.invoiceexpired;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("invoiceexpiredStep3Escalate")
class Step3Escalate implements InvoiceExpiredStep {

	private final DevPlaybookOps ops;

	Step3Escalate(DevPlaybookOps ops) {
		this.ops = ops;
	}

	@Override
	public int stepNumber() {
		return 3;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.SEND_EMAIL;
	}

	@Override
	public String planNote() {
		return "Step 3: escalate to collections (DEV email placeholder)";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(
				recoveryCase,
				"OPS: escalate unpaid invoice ₹"
						+ recoveryCase.getAmountAtRisk()
						+ " case "
						+ recoveryCase.getCaseId()
						+ " (DEV SMS)");
		ops.finish(action, planNote());
	}
}
