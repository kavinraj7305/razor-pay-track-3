package com.razorpayhackthon.revenue_recovery.service.retry;

import com.razorpayhackthon.revenue_recovery.entity.Payment;
import com.razorpayhackthon.revenue_recovery.entity.PaymentAttempt;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.PaymentAttemptStatus;
import com.razorpayhackthon.revenue_recovery.repository.PaymentAttemptRepository;
import com.razorpayhackthon.revenue_recovery.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DevPaymentAttemptRecorder {

	private final PaymentRepository paymentRepository;
	private final PaymentAttemptRepository paymentAttemptRepository;

	public DevPaymentAttemptRecorder(
			PaymentRepository paymentRepository, PaymentAttemptRepository paymentAttemptRepository) {
		this.paymentRepository = paymentRepository;
		this.paymentAttemptRepository = paymentAttemptRepository;
	}

	public void record(RecoveryCase recoveryCase, int attemptNumber, DevPaymentRetryService.Result result) {
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
