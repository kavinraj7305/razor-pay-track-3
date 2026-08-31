package com.razorpayhackthon.revenue_recovery.webhook;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class RazorpayWebhookService {

	private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);

	private final RazorpayWebhookParser parser;
	private final WebhookIdempotencyStore idempotencyStore;
	private final KafkaTemplate<String, String> kafkaTemplate;

	public RazorpayWebhookService(
			RazorpayWebhookParser parser,
			WebhookIdempotencyStore idempotencyStore,
			KafkaTemplate<String, String> kafkaTemplate) {
		this.parser = parser;
		this.idempotencyStore = idempotencyStore;
		this.kafkaTemplate = kafkaTemplate;
	}

	public WebhookAck ingest(String rawBody) {
		ParsedRazorpayEvent parsed = parser.parse(rawBody);

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
}
