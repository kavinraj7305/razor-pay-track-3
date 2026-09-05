package com.razorpayhackthon.revenue_recovery.dto;

import java.util.List;

public record WebhookInboxSnapshot(
		int signedCount, int razorpayCount, int simulateCount, List<WebhookInboxItem> events) {}
