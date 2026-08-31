package com.razorpayhackthon.revenue_recovery.webhook;

public record WebhookAck(boolean received, String eventId, String eventType, boolean duplicate) {
}
