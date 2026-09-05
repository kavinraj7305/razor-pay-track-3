package com.razorpayhackthon.revenue_recovery.dto;

public record PolicyPeek(
		String verdict,
		boolean allowExecute,
		boolean skipRetry,
		boolean escalate,
		String recommendedAction,
		String reason) {}
