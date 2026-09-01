package com.razorpayhackthon.revenue_recovery.controller;

import com.razorpayhackthon.revenue_recovery.service.webhook.RazorpayWebhookService;
import com.razorpayhackthon.revenue_recovery.webhook.WebhookAck;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RazorpayWebhookController {

	static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

	private final RazorpayWebhookService webhookService;

	public RazorpayWebhookController(RazorpayWebhookService webhookService) {
		this.webhookService = webhookService;
	}

	@PostMapping(
			path = "/webhooks/razorpay",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public WebhookAck ingest(
			@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
			@RequestBody byte[] rawBytes) {
		return webhookService.ingest(signature, rawBytes);
	}
}
