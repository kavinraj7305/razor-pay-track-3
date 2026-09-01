package com.razorpayhackthon.revenue_recovery.service.ingest;

import com.razorpayhackthon.revenue_recovery.webhook.RazorpayWebhookParser;
import java.math.BigDecimal;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

final class WebhookPayloadSupport {

	private WebhookPayloadSupport() {}

	static String idOf(JsonNode payload, String name) {
		JsonNode entity = RazorpayWebhookParser.nestedEntityOrNull(payload, name);
		return entity == null ? null : textOrNull(entity, "id");
	}

	static String text(JsonNode node, String field) {
		String value = textOrNull(node, field);
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	static String textOrNull(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || value.asText().isBlank()) {
			return null;
		}
		return value.asText();
	}

	static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	static String currency(JsonNode entity) {
		String value = textOrNull(entity, "currency");
		if (value == null || value.length() != 3) {
			return "INR";
		}
		return value.toUpperCase(Locale.ROOT);
	}

	static String reason(JsonNode payment, String eventType) {
		return truncate(
				firstNonBlank(textOrNull(payment, "error_reason"), textOrNull(payment, "error_description"), eventType),
				100);
	}

	static String truncate(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	static BigDecimal fromPaise(JsonNode amountNode) {
		if (amountNode == null || !amountNode.isNumber()) {
			return BigDecimal.ZERO;
		}
		return BigDecimal.valueOf(amountNode.asLong()).movePointLeft(2);
	}
}
