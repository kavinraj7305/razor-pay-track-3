package com.razorpayhackthon.revenue_recovery.service.retry;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Free local retry: does not call Razorpay. Always fails so we can walk all 4 insufficient-funds steps.
 */
@Service
public class DevPaymentRetryService {

	private static final Logger log = LoggerFactory.getLogger(DevPaymentRetryService.class);

	public record Result(boolean success, String code, String message) {}

	public Result retry(RecoveryCase recoveryCase, int attemptNumber) {
		log.info(
				"DEV retry attempt={} paymentId={} amount={} {}",
				attemptNumber,
				recoveryCase.getSourceId(),
				recoveryCase.getAmountAtRisk(),
				recoveryCase.getCurrency());
		return new Result(false, "insufficient_funds", "DEV: still insufficient_funds");
	}
}
