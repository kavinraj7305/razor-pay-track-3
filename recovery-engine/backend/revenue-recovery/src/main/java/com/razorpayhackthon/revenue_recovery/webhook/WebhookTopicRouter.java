package com.razorpayhackthon.revenue_recovery.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookTopicRouter {

	private final String paymentEventsTopic;
	private final String invoiceEventsTopic;
	private final String checkoutEventsTopic;

	public WebhookTopicRouter(
			@Value("${recovery.kafka.payment-events-topic:payment.events}") String paymentEventsTopic,
			@Value("${recovery.kafka.invoice-events-topic:invoice.events}") String invoiceEventsTopic,
			@Value("${recovery.kafka.checkout-events-topic:checkout.events}") String checkoutEventsTopic) {
		this.paymentEventsTopic = paymentEventsTopic;
		this.invoiceEventsTopic = invoiceEventsTopic;
		this.checkoutEventsTopic = checkoutEventsTopic;
	}

	public String topicFor(String eventType) {
		if (eventType.startsWith("invoice.")) {
			return invoiceEventsTopic;
		}
		if (eventType.startsWith("order.")
				|| eventType.startsWith("payment_link.")
				|| eventType.contains("checkout")) {
			return checkoutEventsTopic;
		}
		return paymentEventsTopic;
	}

	public String paymentEventsTopic() {
		return paymentEventsTopic;
	}

	public String invoiceEventsTopic() {
		return invoiceEventsTopic;
	}

	public String checkoutEventsTopic() {
		return checkoutEventsTopic;
	}
}
