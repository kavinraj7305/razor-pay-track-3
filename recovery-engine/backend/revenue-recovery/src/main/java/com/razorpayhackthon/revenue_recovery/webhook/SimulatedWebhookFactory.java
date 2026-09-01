package com.razorpayhackthon.revenue_recovery.webhook;

import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;

public final class SimulatedWebhookFactory {

	private SimulatedWebhookFactory() {}

	public static PreparedWebhook prepare(SimulateScenario scenario, long stamp, String sourceId) {
		String eventId = trimId("evt_" + stamp + "_" + scenario.ordinal());
		String body =
				switch (scenario) {
					case INSUFFICIENT_FUNDS -> WebhookScenarioPayloads.paymentFailed(
							eventId,
							sourceId,
							49900,
							"card",
							"funds.user@example.com",
							"insufficient_funds",
							"BAD_REQUEST_ERROR",
							"Insufficient funds in account",
							stamp);
					case CARD_EXPIRED -> WebhookScenarioPayloads.paymentFailed(
							eventId,
							sourceId,
							129900,
							"card",
							"expired.card@example.com",
							"card_expired",
							"BAD_REQUEST_ERROR",
							"Card has expired",
							stamp);
					case RISK_FAILED -> WebhookScenarioPayloads.paymentFailed(
							eventId,
							sourceId,
							8_000_000,
							"card",
							"risk.user@example.com",
							"payment_risk_check_failed",
							"BAD_REQUEST_ERROR",
							"Payment declined due to risk checks",
							stamp);
					case SUBSCRIPTION_PENDING -> WebhookScenarioPayloads.subscription(
							"subscription.pending",
							eventId,
							sourceId,
							"pay_subp_" + stamp,
							99900,
							"insufficient_funds",
							"pending",
							stamp);
					case SUBSCRIPTION_HALTED -> WebhookScenarioPayloads.subscription(
							"subscription.halted",
							eventId,
							sourceId,
							"pay_subh_" + stamp,
							199900,
							"card_declined",
							"halted",
							stamp);
					case INVOICE_EXPIRED -> WebhookScenarioPayloads.invoiceExpired(eventId, sourceId, 250000, stamp);
					case CHECKOUT_ABANDONED ->
							WebhookScenarioPayloads.checkoutAbandoned(eventId, sourceId, 34900, stamp);
					case PAYMENT_CAPTURED -> WebhookScenarioPayloads.paymentCaptured(eventId, sourceId, 49900, stamp);
				};
		return new PreparedWebhook(sourceOf(scenario), sourceId, eventId, body);
	}

	public static RecoverySource sourceOf(SimulateScenario scenario) {
		return switch (scenario) {
			case INSUFFICIENT_FUNDS, CARD_EXPIRED, RISK_FAILED, PAYMENT_CAPTURED -> RecoverySource.PAYMENT;
			case SUBSCRIPTION_PENDING, SUBSCRIPTION_HALTED -> RecoverySource.SUBSCRIPTION;
			case INVOICE_EXPIRED -> RecoverySource.INVOICE;
			case CHECKOUT_ABANDONED -> RecoverySource.CHECKOUT_SESSION;
		};
	}

	private static String trimId(String value) {
		return value.length() <= 50 ? value : value.substring(0, 50);
	}
}
