package com.razorpayhackthon.revenue_recovery.service.webhook;

import com.razorpayhackthon.revenue_recovery.config.RazorpayProperties;
import com.razorpayhackthon.revenue_recovery.entity.WebhookEvent;
import com.razorpayhackthon.revenue_recovery.enums.WebhookIntake;
import com.razorpayhackthon.revenue_recovery.repository.WebhookEventRepository;
import com.razorpayhackthon.revenue_recovery.webhook.ParsedRazorpayEvent;
import com.razorpayhackthon.revenue_recovery.webhook.RazorpaySignatureVerifier;
import com.razorpayhackthon.revenue_recovery.webhook.RazorpayWebhookParser;
import com.razorpayhackthon.revenue_recovery.webhook.WebhookAck;
import com.razorpayhackthon.revenue_recovery.webhook.WebhookIdempotencyStore;
import com.razorpayhackthon.revenue_recovery.webhook.WebhookIngestException;
import com.razorpayhackthon.revenue_recovery.webhook.WebhookRejectedException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RazorpayWebhookService {

	private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);

	private final RazorpayProperties properties;
	private final RazorpaySignatureVerifier signatureVerifier;
	private final RazorpayWebhookParser parser;
	private final WebhookIdempotencyStore idempotencyStore;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final WebhookEventRepository webhookEventRepository;
	private final JsonMapper jsonMapper;

	public RazorpayWebhookService(
			RazorpayProperties properties,
			RazorpaySignatureVerifier signatureVerifier,
			RazorpayWebhookParser parser,
			WebhookIdempotencyStore idempotencyStore,
			KafkaTemplate<String, String> kafkaTemplate,
			WebhookEventRepository webhookEventRepository,
			JsonMapper jsonMapper) {
		this.properties = properties;
		this.signatureVerifier = signatureVerifier;
		this.parser = parser;
		this.idempotencyStore = idempotencyStore;
		this.kafkaTemplate = kafkaTemplate;
		this.webhookEventRepository = webhookEventRepository;
		this.jsonMapper = jsonMapper;
	}

	public WebhookAck ingest(String signature, byte[] rawBytes) {
		if (!properties.isWebhookSecretConfigured()) {
			log.error("RAZORPAY_WEBHOOK_SECRET is not set");
			throw new WebhookRejectedException(HttpStatus.SERVICE_UNAVAILABLE, "webhook secret is not set");
		}
		if (rawBytes == null || rawBytes.length == 0) {
			throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "empty body");
		}
		if (signature == null || signature.isBlank()) {
			throw new WebhookRejectedException(HttpStatus.UNAUTHORIZED, "missing signature");
		}
		String rawBody = new String(rawBytes, StandardCharsets.UTF_8);
		if (!signatureVerifier.isValid(rawBody, signature, properties.getWebhookSecret())) {
			log.warn("Rejected Razorpay webhook with invalid signature");
			throw new WebhookRejectedException(HttpStatus.UNAUTHORIZED, "invalid signature");
		}
		try {
			return publish(rawBody);
		} catch (IllegalArgumentException ex) {
			log.warn("Rejected malformed Razorpay webhook: {}", ex.getMessage());
			throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	private WebhookAck publish(String rawBody) {
		ParsedRazorpayEvent parsed = parser.parse(rawBody);
		recordSignedInbox(parsed);
		boolean claimed;
		try {
			claimed = idempotencyStore.tryClaim(parsed.eventId());
		} catch (RuntimeException ex) {
			throw new WebhookIngestException("Idempotency store unavailable", ex);
		}
		if (!claimed) {
			log.info(
					"Duplicate Razorpay webhook ignored eventId={} eventType={}",
					parsed.eventId(),
					parsed.eventType());
			return new WebhookAck(true, parsed.eventId(), parsed.eventType(), true);
		}
		try {
			kafkaTemplate.send(parsed.topic(), parsed.eventId(), rawBody).get(3, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			idempotencyStore.release(parsed.eventId());
			throw new WebhookIngestException("Interrupted while publishing webhook", ex);
		} catch (Exception ex) {
			idempotencyStore.release(parsed.eventId());
			throw new WebhookIngestException("Failed to publish webhook to Kafka", ex);
		}
		log.info(
				"Acked Razorpay webhook eventId={} eventType={} topic={}",
				parsed.eventId(),
				parsed.eventType(),
				parsed.topic());
		return new WebhookAck(true, parsed.eventId(), parsed.eventType(), false);
	}

	private void recordSignedInbox(ParsedRazorpayEvent parsed) {
		WebhookEvent stored = webhookEventRepository.findByEventId(parsed.eventId()).orElseGet(WebhookEvent::new);
		stored.setEventId(parsed.eventId());
		stored.setProvider("RAZORPAY");
		stored.setEventType(parsed.eventType());
		stored.setIntake(WebhookIntake.HMAC_SIGNED.name());
		stored.setSignatureVerified(true);
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = jsonMapper.convertValue(parsed.root(), Map.class);
		stored.setPayload(payload);
		webhookEventRepository.save(stored);
		log.info(
				"HMAC verified webhook stored eventId={} eventType={} accountId={}",
				parsed.eventId(),
				parsed.eventType(),
				parsed.accountId());
	}
}
