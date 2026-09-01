package com.razorpayhackthon.revenue_recovery.dto;

import java.math.BigDecimal;

public record SimulatedFailureResult(
		boolean stored,
		String scenario,
		String eventId,
		String eventType,
		String caseId,
		String sourceId,
		BigDecimal amountAtRisk,
		String reason,
		String status,
		String actionType,
		String actionStatus,
		String intendedAction) {}
