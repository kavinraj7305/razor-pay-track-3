package com.razorpayhackthon.revenue_recovery.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RazorpayWebhookParser {

	private final JsonMapper jsonMapper;
	private final WebhookTopicRouter topicRouter;

	public RazorpayWebhookParser(JsonMapper jsonMapper, WebhookTopicRouter topicRouter) {
		this.jsonMapper = jsonMapper;
		this.topicRouter = topicRouter;
	}

	public ParsedRazorpayEvent parse(String rawBody) {
		JsonNode root;
		try {
			root = jsonMapper.readTree(rawBody);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Webhook body is not valid JSON");
		}
		if (root == null || !root.isObject()) {
			throw new IllegalArgumentException("Webhook body must be a JSON object");
		}

		String eventType = requiredText(root, "event");
		String accountId = requiredText(root, "account_id");
		JsonNode payload = root.get("payload");
		if (payload == null || !payload.isObject()) {
			throw new IllegalArgumentException("payload must be a JSON object");
		}
		validatePayloadForEvent(eventType, payload);

		return new ParsedRazorpayEvent(
				resolveEventId(rawBody, root),
				eventType,
				accountId,
				topicRouter.topicFor(eventType),
				root);
	}

	private static void validatePayloadForEvent(String eventType, JsonNode payload) {
		if (eventType.startsWith("payment.")) {
			JsonNode payment = nestedEntity(payload, "payment");
			requireId(payment, "payload.payment.entity.id");
			if ("payment.failed".equals(eventType)) {
				requirePositiveAmount(payment, "payload.payment.entity.amount");
			}
			return;
		}
		if (eventType.startsWith("subscription.")) {
			requireId(nestedEntity(payload, "subscription"), "payload.subscription.entity.id");
			return;
		}
		if (eventType.startsWith("invoice.")) {
			JsonNode invoice = nestedEntity(payload, "invoice");
			requireId(invoice, "payload.invoice.entity.id");
			if ("invoice.expired".equals(eventType)) {
				requirePositiveAmount(invoice, "payload.invoice.entity.amount");
			}
			return;
		}
		if (eventType.startsWith("order.")) {
			JsonNode order = payload.get("order") != null ? nestedEntity(payload, "order") : null;
			JsonNode payment = payload.get("payment") != null ? nestedEntity(payload, "payment") : null;
			if (order == null && payment == null) {
				throw new IllegalArgumentException("payload.order.entity or payload.payment.entity is required");
			}
			if (order != null) {
				requireId(order, "payload.order.entity.id");
			}
			if (payment != null) {
				requireId(payment, "payload.payment.entity.id");
			}
		}
	}

	public static JsonNode nestedEntity(JsonNode payload, String name) {
		JsonNode wrapper = payload.get(name);
		if (wrapper == null || !wrapper.isObject()) {
			throw new IllegalArgumentException("payload." + name + " must be an object");
		}
		JsonNode entity = wrapper.get("entity");
		if (entity == null || !entity.isObject()) {
			throw new IllegalArgumentException("payload." + name + ".entity must be an object");
		}
		return entity;
	}

	public static JsonNode nestedEntityOrNull(JsonNode payload, String name) {
		JsonNode wrapper = payload.get(name);
		if (wrapper == null || !wrapper.isObject()) {
			return null;
		}
		JsonNode entity = wrapper.get("entity");
		if (entity == null || !entity.isObject()) {
			return null;
		}
		return entity;
	}

	private static void requireId(JsonNode entity, String field) {
		JsonNode id = entity.get("id");
		if (id == null || id.isNull() || id.asText().isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
	}

	private static void requirePositiveAmount(JsonNode entity, String field) {
		JsonNode amount = entity.get("amount");
		if (amount == null || !amount.isNumber() || amount.asLong() <= 0) {
			throw new IllegalArgumentException(field + " must be a positive number (paise)");
		}
	}

	private static String requiredText(JsonNode root, String field) {
		JsonNode node = root.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return node.asText();
	}

	private static String resolveEventId(String rawBody, JsonNode root) {
		JsonNode id = root.get("id");
		if (id != null && !id.isNull()) {
			String value = id.asText();
			if (!value.isBlank() && value.length() <= 50) {
				return value;
			}
		}
		return "rp_" + sha256Hex(rawBody).substring(0, 32);
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
