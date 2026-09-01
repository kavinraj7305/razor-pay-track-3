package com.razorpayhackthon.revenue_recovery.webhook;

import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;

public record PreparedWebhook(RecoverySource source, String sourceId, String eventId, String body) {}
