package com.razorpayhackthon.revenue_recovery.webhook;

import org.springframework.http.HttpStatus;

public class WebhookRejectedException extends RuntimeException {

	private final HttpStatus status;

	public WebhookRejectedException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
