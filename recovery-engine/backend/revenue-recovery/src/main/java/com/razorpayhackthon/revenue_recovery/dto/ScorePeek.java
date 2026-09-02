package com.razorpayhackthon.revenue_recovery.dto;

public record ScorePeek(
		String status,
		long labelledOutcomes,
		long minLabelledOutcomes,
		Double recoveryProbability,
		boolean skipRetry,
		String label) {}
