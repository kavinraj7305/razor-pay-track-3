package com.razorpayhackthon.revenue_recovery.service.ingest;

import com.razorpayhackthon.revenue_recovery.enums.RecoveryPriority;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.webhook.ParsedRazorpayEvent;
import com.razorpayhackthon.revenue_recovery.webhook.RazorpayWebhookParser;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class RecoveryCaseDraftFactory {

	RecoveryCaseDraft from(ParsedRazorpayEvent parsed) {
		JsonNode payload = parsed.root().get("payload");
		return switch (parsed.eventType()) {
			case "payment.failed" -> paymentFailed(parsed, payload);
			case "subscription.pending", "subscription.halted" -> subscription(parsed, payload);
			case "invoice.expired" -> invoiceExpired(payload);
			case "checkout.abandoned" -> checkoutAbandoned(payload);
			default -> throw new IllegalArgumentException("Unsupported recovery event " + parsed.eventType());
		};
	}

	private RecoveryCaseDraft paymentFailed(ParsedRazorpayEvent parsed, JsonNode payload) {
		JsonNode payment = RazorpayWebhookParser.nestedEntity(payload, "payment");
		String failReason = WebhookPayloadSupport.reason(payment, parsed.eventType());
		RecoveryPriority priority =
				"payment_risk_check_failed".equals(failReason)
						? RecoveryPriority.CRITICAL
						: "insufficient_funds".equals(failReason) || "payment_timed_out".equals(failReason)
								? RecoveryPriority.MEDIUM
								: RecoveryPriority.HIGH;
		return new RecoveryCaseDraft(
				RecoverySource.PAYMENT,
				WebhookPayloadSupport.text(payment, "id"),
				WebhookPayloadSupport.fromPaise(payment.get("amount")),
				WebhookPayloadSupport.currency(payment),
				failReason,
				priority);
	}

	private RecoveryCaseDraft subscription(ParsedRazorpayEvent parsed, JsonNode payload) {
		JsonNode subscription = RazorpayWebhookParser.nestedEntity(payload, "subscription");
		JsonNode payment = RazorpayWebhookParser.nestedEntityOrNull(payload, "payment");
		BigDecimal amount =
				payment != null && payment.get("amount") != null && payment.get("amount").isNumber()
						? WebhookPayloadSupport.fromPaise(payment.get("amount"))
						: BigDecimal.ZERO;
		boolean halted = "subscription.halted".equals(parsed.eventType());
		return new RecoveryCaseDraft(
				RecoverySource.SUBSCRIPTION,
				WebhookPayloadSupport.text(subscription, "id"),
				amount,
				payment != null ? WebhookPayloadSupport.currency(payment) : "INR",
				halted ? "subscription.halted" : "subscription.pending",
				halted ? RecoveryPriority.HIGH : RecoveryPriority.MEDIUM);
	}

	private RecoveryCaseDraft invoiceExpired(JsonNode payload) {
		JsonNode invoice = RazorpayWebhookParser.nestedEntity(payload, "invoice");
		BigDecimal amount = WebhookPayloadSupport.fromPaise(invoice.get("amount"));
		JsonNode paidNode = invoice.get("amount_paid");
		if (paidNode != null && paidNode.isNumber() && paidNode.asLong() > 0) {
			amount = amount.subtract(WebhookPayloadSupport.fromPaise(paidNode));
		}
		return new RecoveryCaseDraft(
				RecoverySource.INVOICE,
				WebhookPayloadSupport.text(invoice, "id"),
				amount,
				WebhookPayloadSupport.currency(invoice),
				WebhookPayloadSupport.truncate("invoice.expired", 100),
				RecoveryPriority.MEDIUM);
	}

	private RecoveryCaseDraft checkoutAbandoned(JsonNode payload) {
		JsonNode checkout = RazorpayWebhookParser.nestedEntity(payload, "checkout");
		return new RecoveryCaseDraft(
				RecoverySource.CHECKOUT_SESSION,
				WebhookPayloadSupport.text(checkout, "id"),
				WebhookPayloadSupport.fromPaise(checkout.get("amount")),
				WebhookPayloadSupport.currency(checkout),
				WebhookPayloadSupport.truncate("checkout.abandoned", 100),
				RecoveryPriority.MEDIUM);
	}
}
