package com.razorpayhackthon.revenue_recovery.service.plan.handler.insufficientfunds;

import com.razorpayhackthon.revenue_recovery.entity.Payment;
import com.razorpayhackthon.revenue_recovery.entity.PaymentAttempt;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.PaymentAttemptStatus;
import com.razorpayhackthon.revenue_recovery.repository.PaymentAttemptRepository;
import com.razorpayhackthon.revenue_recovery.repository.PaymentRepository;
import com.razorpayhackthon.revenue_recovery.service.retry.DevPaymentRetryService.Result;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class InsufficientFundsAttemptRecorder {

	private final PaymentRepository paymentRepository;
	private final PaymentAttemptRepository paymentAttemptRepository;

	InsufficientFundsAttemptRecorder(
			PaymentRepository paymentRepository, PaymentAttemptRepository paymentAttemptRepository) {
		this.paymentRepository = paymentRepository;
		this.paymentAttemptRepository = paymentAttemptRepository;
	}

	void record(RecoveryCase recoveryCase, int attemptNumber, Result result) {
		Payment payment = paymentRepository.findByPaymentId(recoveryCase.getSourceId()).orElse(null);
		if (payment == null) {
			return;
		}
		PaymentAttempt attempt = new PaymentAttempt();
		attempt.setAttemptId("pat_" + UUID.randomUUID().toString().replace("-", ""));
		attempt.setPayment(payment);
		attempt.setAttemptNumber(attemptNumber);
		attempt.setStatus(result.success() ? PaymentAttemptStatus.SUCCESS : PaymentAttemptStatus.FAILED);
		attempt.setFailureCode(result.success() ? null : result.code());
		attempt.setFailureMessage(result.success() ? null : result.message());
		paymentAttemptRepository.save(attempt);
	}
}
