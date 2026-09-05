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
		boolean escalate) {}
