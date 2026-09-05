package com.razorpayhackthon.revenue_recovery.dto.auth;

import java.math.BigDecimal;

public record ApprovalItem(
		String caseId,
		String reason,
		String status,
		BigDecimal amountAtRisk,
		String policyReason,
		String recommendedAction,
		String agentDiagnosis,
		String agentReasoning,
		boolean escalate,
		String priority,
		String source,
		String sourceId,
		String customerId,
		String merchantId,
		String playbookAction,
		Double mlScore,
		Double confidence,
		boolean deviatesFromPlaybook,
		boolean fallbackUsed,
		String agentModel,
		boolean agentExecutes) {}
