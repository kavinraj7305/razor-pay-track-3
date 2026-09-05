package com.razorpayhackthon.revenue_recovery.dto;

import java.time.LocalDateTime;

public record WebhookInboxItem(
		String eventId,
		String eventType,
		String accountId,
		String intake,
		boolean signatureVerified,
		String origin,
		boolean processed,
		LocalDateTime receivedAt,
		String sourceId,
		String caseId,
		String reason) {}
