package com.razorpayhackthon.revenue_recovery.webhook;

import com.razorpayhackthon.revenue_recovery.config.RazorpayProperties;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RazorpayWebhookController {

	private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);
	static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

	private final RazorpayProperties properties;
	private final RazorpaySignatureVerifier signatureVerifier;
	private final RazorpayWebhookService webhookService;

	public RazorpayWebhookController(
			RazorpayProperties properties,
			RazorpaySignatureVerifier signatureVerifier,
			RazorpayWebhookService webhookService) {
		this.properties = properties;
		this.signatureVerifier = signatureVerifier;
		this.webhookService = webhookService;
	}

	@PostMapping(
			path = "/webhooks/razorpay",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<WebhookAck> ingest(
			@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
			@RequestBody byte[] rawBytes) {
		if (!properties.isWebhookSecretConfigured()) {
			log.error("RAZORPAY_WEBHOOK_SECRET is not set");
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}
		if (rawBytes == null || rawBytes.length == 0) {
			return ResponseEntity.badRequest().build();
		}
		if (signature == null || signature.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String rawBody = new String(rawBytes, StandardCharsets.UTF_8);
		if (!signatureVerifier.isValid(rawBody, signature, properties.getWebhookSecret())) {
			log.warn("Rejected Razorpay webhook with invalid signature");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		try {
			return ResponseEntity.ok(webhookService.ingest(rawBody));
		} catch (IllegalArgumentException ex) {
			log.warn("Rejected malformed Razorpay webhook: {}", ex.getMessage());
			return ResponseEntity.badRequest().build();
		} catch (WebhookIngestException ex) {
			log.error("Webhook ingest unavailable: {}", ex.getMessage());
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}
	}
}
