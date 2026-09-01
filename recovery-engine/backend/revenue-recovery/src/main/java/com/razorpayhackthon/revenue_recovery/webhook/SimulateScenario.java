package com.razorpayhackthon.revenue_recovery.webhook;

/**
 * The 8 Track-03 demo scenarios. Hit {@code /api/webhooks/simulate/{slug}} or {@code /all}.
 */
public enum SimulateScenario {
	INSUFFICIENT_FUNDS(
			"insufficient-funds",
			"payment.failed",
			"insufficient_funds",
			"Delayed retry (payday)"),
	CARD_EXPIRED("card-expired", "payment.failed", "card_expired", "Send payment link (new card)"),
	RISK_FAILED(
			"risk-failed",
			"payment.failed",
			"payment_risk_check_failed",
			"Do not retry — escalate"),
	SUBSCRIPTION_PENDING(
			"subscription-pending",
			"subscription.pending",
			"subscription.pending",
			"Retry sequencer"),
	SUBSCRIPTION_HALTED(
			"subscription-halted", "subscription.halted", "subscription.halted", "Escalate — retries exhausted"),
	INVOICE_EXPIRED("invoice-expired", "invoice.expired", "invoice.expired", "B2B receivables chase"),
	CHECKOUT_ABANDONED(
			"checkout-abandoned", "checkout.abandoned", "checkout.abandoned", "Checkout drop-off pay-link"),
	PAYMENT_CAPTURED("payment-captured", "payment.captured", "captured", "Close case — ₹ recovered");

	private final String slug;
	private final String eventType;
	private final String reason;
	private final String intendedAction;

	SimulateScenario(String slug, String eventType, String reason, String intendedAction) {
		this.slug = slug;
		this.eventType = eventType;
		this.reason = reason;
		this.intendedAction = intendedAction;
	}

	public String slug() {
		return slug;
	}

	public String eventType() {
		return eventType;
	}

	public String reason() {
		return reason;
	}

	public String intendedAction() {
		return intendedAction;
	}

	public String path() {
		return "/api/webhooks/simulate/" + slug;
	}

	public static SimulateScenario fromSlug(String slug) {
		for (SimulateScenario scenario : values()) {
			if (scenario.slug.equals(slug)) {
				return scenario;
			}
		}
		throw new IllegalArgumentException("Unknown scenario: " + slug);
	}
}
