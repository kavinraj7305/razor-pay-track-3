package com.razorpayhackthon.revenue_recovery.service.plan.handler.insufficientfunds;

import com.razorpayhackthon.revenue_recovery.entity.Customer;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.service.notify.DevSmsService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
class Step4PaymentLink implements InsufficientFundsStep {

	private final DevSmsService smsService;

	Step4PaymentLink(DevSmsService smsService) {
		this.smsService = smsService;
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
		return "Step 4: stop auto-retry, send payment link once";
	}

	@Override
	public void execute(RecoveryCase recoveryCase, RecoveryAction action) {
		String link = "https://rzp.io/i/dev-" + recoveryCase.getCaseId();
		Customer customer = recoveryCase.getCustomer();
		String to = customer == null ? null : customer.getPhone();
		smsService.send(
				to,
				"Pay ₹" + recoveryCase.getAmountAtRisk() + " when you have funds: " + link + " (DEV SMS)");
		action.setStatus(RecoveryActionStatus.EXECUTED);
		action.setExecutedAt(LocalDateTime.now());
		action.setReason(planNote() + " — " + link);
		recoveryCase.setStatus(RecoveryCaseStatus.ACTION_PLANNED);
	}
}
