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
					case CARD_NOT_ENROLLED -> WebhookScenarioPayloads.paymentFailed(
							eventId,
							sourceId,
							79900,
							"card",
							"unenrolled.card@example.com",
							"card_not_enrolled",
							"BAD_REQUEST_ERROR",
							"Card is not enrolled for 3D Secure",
							stamp);
					case PAYMENT_TIMED_OUT -> WebhookScenarioPayloads.paymentFailed(
							eventId,
							sourceId,
							59900,
							"card",
							"timeout.user@example.com",
							"payment_timed_out",
							"GATEWAY_ERROR",
							"Payment timed out",
							stamp);
					case CARD_DECLINED -> WebhookScenarioPayloads.paymentFailed(
							eventId,
							sourceId,
							89900,
							"card",
							"declined.card@example.com",
							"card_declined",
							"BAD_REQUEST_ERROR",
							"Card was declined by the issuer",
							stamp);
					case CURRENCY_NOT_SUPPORTED -> WebhookScenarioPayloads.paymentFailed(
							eventId,
							sourceId,
							69900,
							"card",
							"currency.user@example.com",
							"currency_not_supported",
							"BAD_REQUEST_ERROR",
							"Currency is not supported on this method",
							stamp);
					case PAYMENT_CAPTURED -> WebhookScenarioPayloads.paymentCaptured(eventId, sourceId, 49900, stamp);
				};
		return new PreparedWebhook(sourceOf(scenario), sourceId, eventId, body);
	}

	public static RecoverySource sourceOf(SimulateScenario scenario) {
		return switch (scenario) {
			case INSUFFICIENT_FUNDS,
					CARD_EXPIRED,
					RISK_FAILED,
					CARD_NOT_ENROLLED,
					PAYMENT_TIMED_OUT,
					CARD_DECLINED,
					CURRENCY_NOT_SUPPORTED,
					PAYMENT_CAPTURED -> RecoverySource.PAYMENT;
			case SUBSCRIPTION_PENDING, SUBSCRIPTION_HALTED -> RecoverySource.SUBSCRIPTION;
			case INVOICE_EXPIRED -> RecoverySource.INVOICE;
			case CHECKOUT_ABANDONED -> RecoverySource.CHECKOUT_SESSION;
		};
	}

	private static String trimId(String value) {
		return value.length() <= 50 ? value : value.substring(0, 50);
	}
}
