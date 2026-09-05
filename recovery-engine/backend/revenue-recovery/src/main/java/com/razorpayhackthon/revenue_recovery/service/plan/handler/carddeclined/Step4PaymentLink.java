package com.razorpayhackthon.revenue_recovery.service.plan.handler.carddeclined;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("carddeclinedStep4PaymentLink")
class Step4PaymentLink implements CardDeclinedStep {

	private final DevPlaybookOps ops;

	Step4PaymentLink(DevPlaybookOps ops) {
		this.ops = ops;
	}

	@Override
	public int stepNumber() {
		return 4;
	}

	@Override
	public RecoveryActionType actionType() {
		return RecoveryActionType.SEND_PAYMENT_LINK;
	}

	@Override
	public String planNote() {
		return "Step 4: stop hitting this card, send one payment link";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.payLink(recoveryCase, action, planNote());
	}
}
