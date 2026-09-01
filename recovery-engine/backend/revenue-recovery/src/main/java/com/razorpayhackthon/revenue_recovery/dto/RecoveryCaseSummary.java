package com.razorpayhackthon.revenue_recovery.dto;

import java.math.BigDecimal;

public record RecoveryCaseSummary(
		String caseId,
		String source,
		String sourceId,
		String reason,
		String status,
		BigDecimal amountAtRisk,
		String actionType,
		String actionStatus) {}
