package com.razorpayhackthon.revenue_recovery.webhook;

import tools.jackson.databind.JsonNode;

public record ParsedRazorpayEvent(
		String eventId, String eventType, String accountId, String topic, JsonNode root) {}
