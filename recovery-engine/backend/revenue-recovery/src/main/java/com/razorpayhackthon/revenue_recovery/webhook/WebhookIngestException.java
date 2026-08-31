package com.razorpayhackthon.revenue_recovery.webhook;

public class WebhookIngestException extends RuntimeException {

	public WebhookIngestException(String message, Throwable cause) {
		super(message, cause);
	}
}
