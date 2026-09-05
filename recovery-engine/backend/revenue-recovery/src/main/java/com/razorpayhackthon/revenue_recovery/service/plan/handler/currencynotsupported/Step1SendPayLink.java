package com.razorpayhackthon.revenue_recovery.service.plan.handler.currencynotsupported;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("currencynotsupportedStep1SendPayLink")
class Step1SendPayLink implements CurrencyNotSupportedStep {

	private final DevPlaybookOps ops;

	Step1SendPayLink(DevPlaybookOps ops) {
		this.ops = ops;
	}

	@Override
	public int stepNumber() {
		return 1;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.SEND_PAYMENT_LINK;
	}

	@Override
	public String planNote() {
		return "Step 1: send a link so they can pay in a supported currency or method";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.payLink(recoveryCase, action, planNote());
	}
}
