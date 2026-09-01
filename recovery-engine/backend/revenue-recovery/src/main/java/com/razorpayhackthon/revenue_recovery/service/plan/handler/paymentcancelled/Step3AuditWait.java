package com.razorpayhackthon.revenue_recovery.service.plan.handler.paymentcancelled;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DevPlaybookOps;
import org.springframework.stereotype.Component;

@Component("paymentcancelledStep3AuditWait")
class Step3AuditWait implements PaymentCancelledStep {

	private final DevPlaybookOps ops;

	Step3AuditWait(DevPlaybookOps ops) {
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
		return "Step 3: wait — do not chase hard";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		ops.finish(action, planNote());
	}
}
