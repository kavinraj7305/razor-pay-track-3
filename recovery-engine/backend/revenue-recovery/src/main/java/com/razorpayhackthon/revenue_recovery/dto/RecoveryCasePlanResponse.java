package com.razorpayhackthon.revenue_recovery.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record RecoveryCasePlanResponse(
		String caseId,
		String source,
		String sourceId,
		String reason,
		String status,
		BigDecimal amountAtRisk,
		String currency,
		String priority,
		String merchantId,
		String customerId,
		LocalDateTime createdAt,
		LocalDateTime closedAt,
		PlannedAction plan,
		List<PlannedAction> actions,
		List<AuditLine> audit,
		List<PlaybookStepPreview> playbook,
		ScorePeek score) {

	public record PlannedAction(
			String actionId,
			String actionType,
			String status,
			Integer attemptNumber,
			String note,
			LocalDateTime createdAt) {}

	public record AuditLine(
			String eventId, String eventType, String action, Map<String, Object> details, LocalDateTime createdAt) {}
}
