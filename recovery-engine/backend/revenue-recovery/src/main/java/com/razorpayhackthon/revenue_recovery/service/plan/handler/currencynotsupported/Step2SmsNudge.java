package com.razorpayhackthon.revenue_recovery.service.plan.handler.currencynotsupported;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("currencynotsupportedStep2SmsNudge")
class Step2SmsNudge implements CurrencyNotSupportedStep {

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
		return "Step 2: text once to open the link";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.sms(
				recoveryCase,
				"This method cannot take that currency. Pay ₹"
						+ recoveryCase.getAmountAtRisk()
						+ " on the link (DEV SMS)");
		ops.finish(action, planNote());
	}
}
