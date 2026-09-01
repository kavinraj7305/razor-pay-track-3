package com.razorpayhackthon.revenue_recovery.dto;

public record ScenarioCatalogItem(
		String slug, String eventType, String reason, String intendedAction, String path) {}
