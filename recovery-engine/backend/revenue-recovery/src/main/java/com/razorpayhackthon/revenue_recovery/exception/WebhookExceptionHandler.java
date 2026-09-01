package com.razorpayhackthon.revenue_recovery.exception;

import com.razorpayhackthon.revenue_recovery.controller.RazorpayWebhookController;
import com.razorpayhackthon.revenue_recovery.webhook.WebhookIngestException;
import com.razorpayhackthon.revenue_recovery.webhook.WebhookRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RazorpayWebhookController.class)
public class WebhookExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(WebhookExceptionHandler.class);

	@ExceptionHandler(WebhookRejectedException.class)
	public ResponseEntity<Void> rejected(WebhookRejectedException ex) {
		return ResponseEntity.status(ex.status()).build();
	}

	@ExceptionHandler(WebhookIngestException.class)
	public ResponseEntity<Void> unavailable(WebhookIngestException ex) {
		log.error("Webhook ingest unavailable: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
	}
}
