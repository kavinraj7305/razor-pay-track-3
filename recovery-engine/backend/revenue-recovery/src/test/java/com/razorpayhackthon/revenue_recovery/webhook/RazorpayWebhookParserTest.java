package com.razorpayhackthon.revenue_recovery.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RazorpayWebhookParserTest {

	private final RazorpayWebhookParser parser =
			new RazorpayWebhookParser(JsonMapper.builder().build(), new WebhookTopicRouter(
					"payment.events", "invoice.events", "checkout.events"));

	@Test
	void parsesPaymentFailedAndRoutesToPaymentTopic() {
		String body =
				"""
				{"id":"evt_1","entity":"event","account_id":"acc_test","event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_1","amount":49900,"currency":"INR","status":"failed"}}}}""";

		ParsedRazorpayEvent parsed = parser.parse(body);

		assertThat(parsed.eventId()).isEqualTo("evt_1");
		assertThat(parsed.eventType()).isEqualTo("payment.failed");
		assertThat(parsed.accountId()).isEqualTo("acc_test");
		assertThat(parsed.topic()).isEqualTo("payment.events");
	}

	@Test
	void routesInvoiceExpiredToInvoiceTopic() {
		String body =
				"""
				{"id":"evt_inv","account_id":"acc_test","event":"invoice.expired","payload":{"invoice":{"entity":{"id":"inv_1","amount":10000,"currency":"INR"}}}}""";

		assertThat(parser.parse(body).topic()).isEqualTo("invoice.events");
	}

	@Test
	void routesOrderPaidToCheckoutTopic() {
		String body =
				"""
				{"id":"evt_ord","account_id":"acc_test","event":"order.paid","payload":{"order":{"entity":{"id":"order_1","amount":10000}},"payment":{"entity":{"id":"pay_1"}}}}""";

		assertThat(parser.parse(body).topic()).isEqualTo("checkout.events");
	}

	@Test
	void rejectsMissingPayload() {
		assertThatThrownBy(() -> parser.parse("{\"event\":\"payment.failed\",\"account_id\":\"acc\"}"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload");
	}

	@Test
	void rejectsPaymentFailedWithoutAmount() {
		String body =
				"""
				{"account_id":"acc_test","event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_1"}}}}""";
		assertThatThrownBy(() -> parser.parse(body))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("amount");
	}

	@Test
	void rejectsInvalidJson() {
		assertThatThrownBy(() -> parser.parse("{"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("JSON");
	}
}
