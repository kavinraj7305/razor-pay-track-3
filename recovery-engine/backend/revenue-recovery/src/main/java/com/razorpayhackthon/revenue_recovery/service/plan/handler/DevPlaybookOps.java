package com.razorpayhackthon.revenue_recovery.service.plan.handler;

import com.razorpayhackthon.revenue_recovery.entity.Customer;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.service.notify.DevSmsService;
import com.razorpayhackthon.revenue_recovery.service.retry.DevPaymentAttemptRecorder;
import com.razorpayhackthon.revenue_recovery.service.retry.DevPaymentRetryService;
import com.razorpayhackthon.revenue_recovery.service.retry.DevPaymentRetryService.Result;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class DevPlaybookOps {

	private final DevPaymentRetryService retryService;
	private final DevPaymentAttemptRecorder attemptRecorder;
	private final DevSmsService smsService;

	public DevPlaybookOps(
			DevPaymentRetryService retryService,
			DevPaymentAttemptRecorder attemptRecorder,
			DevSmsService smsService) {
		this.retryService = retryService;
		this.attemptRecorder = attemptRecorder;
		this.smsService = smsService;
	}

	public void retryAndFail(RecoveryCase recoveryCase, RecoveryAction action, int attemptNumber, String note) {
		Result result = retryService.retry(recoveryCase, attemptNumber);
		attemptRecorder.record(recoveryCase, attemptNumber, result);
		action.setStatus(result.success() ? RecoveryActionStatus.EXECUTED : RecoveryActionStatus.FAILED);
		action.setExecutedAt(LocalDateTime.now());
		action.setReason(note + " — " + result.message());
	}

	public void sms(RecoveryCase recoveryCase, String body) {
		Customer customer = recoveryCase.getCustomer();
		smsService.send(customer == null ? null : customer.getPhone(), body);
	}

	public void payLink(RecoveryCase recoveryCase, RecoveryAction action, String note) {
		String link = "https://rzp.io/i/dev-" + recoveryCase.getCaseId();
		sms(recoveryCase, "Pay ₹" + recoveryCase.getAmountAtRisk() + " : " + link + " (DEV SMS)");
		action.setStatus(RecoveryActionStatus.EXECUTED);
		action.setExecutedAt(LocalDateTime.now());
		action.setReason(note + " — " + link);
		recoveryCase.setStatus(RecoveryCaseStatus.ACTION_PLANNED);
	}

	public void block(RecoveryAction action, String note) {
		action.setStatus(RecoveryActionStatus.CANCELLED);
		action.setExecutedAt(LocalDateTime.now());
		action.setReason(note);
	}

	public void finish(RecoveryAction action, String note) {
		action.setStatus(RecoveryActionStatus.EXECUTED);
		action.setExecutedAt(LocalDateTime.now());
		action.setReason(note);
	}
}
