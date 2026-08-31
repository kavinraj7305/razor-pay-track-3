package com.razorpayhackthon.revenue_recovery.webhook;

import com.razorpayhackthon.revenue_recovery.entity.WebhookEvent;
import com.razorpayhackthon.revenue_recovery.repository.WebhookEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RazorpayWebhookService {

	public static final String KAFKA_TOPIC = "razorpay.webhooks";

	private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);

	private final WebhookEventRepository webhookEventRepository;
	private final JsonMapper jsonMapper;
	private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate;

	public RazorpayWebhookService(
			WebhookEventRepository webhookEventRepository,
			JsonMapper jsonMapper,
			ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate) {
		this.webhookEventRepository = webhookEventRepository;
		this.jsonMapper = jsonMapper;
		this.kafkaTemplate = kafkaTemplate;
	}

	@Transactional
	public WebhookAck ingest(String rawBody) {
		JsonNode root;
		try {
			root = jsonMapper.readTree(rawBody);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Webhook body is not valid JSON");
		}

		String eventType = textOrDefault(root, "event", "unknown");
		String eventId = resolveEventId(rawBody, root);

		if (webhookEventRepository.existsByEventId(eventId)) {
			log.info("Duplicate Razorpay webhook ignored eventId={} eventType={}", eventId, eventType);
			return new WebhookAck(true, eventId, eventType, true);
		}

		WebhookEvent stored = new WebhookEvent();
		stored.setEventId(eventId);
		stored.setProvider("RAZORPAY");
		stored.setEventType(eventType);
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = jsonMapper.convertValue(root, Map.class);
		stored.setPayload(payload);

		try {
			webhookEventRepository.saveAndFlush(stored);
		} catch (DataIntegrityViolationException ex) {
			log.info("Duplicate Razorpay webhook raced eventId={}", eventId);
			return new WebhookAck(true, eventId, eventType, true);
		}

		publish(eventType, rawBody);
		log.info("Stored Razorpay webhook eventId={} eventType={}", eventId, eventType);
		return new WebhookAck(true, eventId, eventType, false);
	}

	private void publish(String eventType, String rawBody) {
		KafkaTemplate<String, String> template = kafkaTemplate.getIfAvailable();
		if (template == null) {
			return;
		}
		try {
			template.send(KAFKA_TOPIC, eventType, rawBody);
		} catch (Exception ex) {
			log.warn("Kafka publish failed for topic {} eventType={}: {}", KAFKA_TOPIC, eventType, ex.getMessage());
		}
	}

	private static String resolveEventId(String rawBody, JsonNode root) {
		String id = textOrDefault(root, "id", "");
		if (!id.isBlank() && id.length() <= 50) {
			return id;
		}
		return "rp_" + sha256Hex(rawBody).substring(0, 32);
	}

	private static String textOrDefault(JsonNode root, String field, String fallback) {
		JsonNode node = root.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			return fallback;
		}
		return node.asText();
	}

	private static String sha256Hex(String rawBody) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawBody.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required", ex);
		}
	}
}
