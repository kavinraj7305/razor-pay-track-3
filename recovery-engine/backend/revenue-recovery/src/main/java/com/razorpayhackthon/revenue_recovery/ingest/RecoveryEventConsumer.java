package com.razorpayhackthon.revenue_recovery.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RecoveryEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(RecoveryEventConsumer.class);

	private final RecoveryCaseIngestService ingestService;

	public RecoveryEventConsumer(RecoveryCaseIngestService ingestService) {
		this.ingestService = ingestService;
	}

	@KafkaListener(
			topics = {
				"${recovery.kafka.payment-events-topic:payment.events}",
				"${recovery.kafka.invoice-events-topic:invoice.events}",
				"${recovery.kafka.checkout-events-topic:checkout.events}"
			})
	public void onEvent(String rawBody) {
		try {
			ingestService.consume(rawBody);
		} catch (IllegalArgumentException ex) {
			log.warn("Skipping unusable Kafka webhook payload: {}", ex.getMessage());
		}
	}
}
