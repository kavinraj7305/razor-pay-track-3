package com.razorpayhackthon.revenue_recovery.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecoveryCaseSummary(
		String caseId,
		String source,
		String sourceId,
		String reason,
		String status,
		BigDecimal amountAtRisk,
		String actionType,
		String actionStatus,
		Double recoveryProbability,
		String scoreStatus,
		List<PlaybookStepPreview> playbook) {}
