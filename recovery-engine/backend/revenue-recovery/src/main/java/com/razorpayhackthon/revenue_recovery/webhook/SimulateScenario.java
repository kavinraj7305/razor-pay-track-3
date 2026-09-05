package com.razorpayhackthon.revenue_recovery.webhook;

/**
 * Desk simulate catalog: original Track-03 cases plus the live Razorpay failure mix.
 * Hit {@code /api/webhooks/simulate/{slug}} or {@code /all}.
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
			"Live mix ~25% — do not retry, escalate"),
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
	CARD_NOT_ENROLLED(
			"card-not-enrolled",
			"payment.failed",
			"card_not_enrolled",
			"Live mix ~40% — send payment link (complete 3DS)"),
	PAYMENT_TIMED_OUT(
			"payment-timed-out",
			"payment.failed",
			"payment_timed_out",
			"Live mix ~15% — short wait, then retry"),
	CARD_DECLINED(
			"card-declined",
			"payment.failed",
			"card_declined",
			"Live mix ~10% — one retry, then a payment link"),
	CURRENCY_NOT_SUPPORTED(
			"currency-not-supported",
			"payment.failed",
			"currency_not_supported",
			"Live mix ~5% — send payment link (other method)"),
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
