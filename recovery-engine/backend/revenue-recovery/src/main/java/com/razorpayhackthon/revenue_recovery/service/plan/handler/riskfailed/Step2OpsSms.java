package com.razorpayhackthon.revenue_recovery.service.plan.handler.riskfailed;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("riskfailedStep2OpsSms")
class Step2OpsSms implements RiskFailedStep {

	private final DevPlaybookOps ops;

	Step2OpsSms(DevPlaybookOps ops) {
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
		return "Step 2: notify ops (DEV SMS)";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(
				recoveryCase,
				"OPS: risk check failed for ₹"
						+ recoveryCase.getAmountAtRisk()
						+ " case "
						+ recoveryCase.getCaseId()
						+ " — do not retry (DEV SMS)");
		ops.finish(action, planNote());
	}
}
