package com.razorpayhackthon.revenue_recovery.service.ml;

import java.math.BigDecimal;

/** Features sent to ml-service POST /predict. */
public record PredictPayload(
		String reason,
		String source,
		String priority,
		String paymentMethod,
		BigDecimal amountInr,
		int retryCount,
		long hoursSinceFail,
		double historicalRecoveryRate,
		int retryHistoryCount,
		double paymentSuccessRate,
		double paymentFailureRate,
		double avgPaymentDelay,
		int subscriptionAgeMonths,
		double lifetimeValue,
		double avgOrderValue,
		int daysSinceLastActivity,
		int historyPaymentCount) {}
