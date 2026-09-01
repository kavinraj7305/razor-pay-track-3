package com.razorpayhackthon.revenue_recovery.webhook;

final class WebhookScenarioPayloads {

	private WebhookScenarioPayloads() {}

	static String paymentFailed(
			String eventId,
			String paymentId,
			long amountPaise,
			String method,
			String email,
			String reason,
			String errorCode,
			String description,
			long createdAt) {
		return """
				{"id":"%s","entity":"event","account_id":"acc_test_recovery","event":"payment.failed","contains":["payment"],"payload":{"payment":{"entity":{"id":"%s","entity":"payment","amount":%d,"currency":"INR","status":"failed","order_id":"order_%s","method":"%s","email":"%s","contact":"+919000000000","error_code":"%s","error_description":"%s","error_reason":"%s"}}},"created_at":%d}"""
				.formatted(
						eventId,
						paymentId,
						amountPaise,
						paymentId,
						method,
						email,
						errorCode,
						description,
						reason,
						createdAt);
	}

	static String paymentCaptured(String eventId, String paymentId, long amountPaise, long createdAt) {
		return """
				{"id":"%s","entity":"event","account_id":"acc_test_recovery","event":"payment.captured","contains":["payment"],"payload":{"payment":{"entity":{"id":"%s","entity":"payment","amount":%d,"currency":"INR","status":"captured","captured":true}}},"created_at":%d}"""
				.formatted(eventId, paymentId, amountPaise, createdAt);
	}

	static String subscription(
			String eventType,
			String eventId,
			String subscriptionId,
			String paymentId,
			long amountPaise,
			String paymentReason,
			String subStatus,
			long createdAt) {
		return """
				{"id":"%s","entity":"event","account_id":"acc_test_recovery","event":"%s","contains":["subscription","payment"],"payload":{"subscription":{"entity":{"id":"%s","entity":"subscription","plan_id":"plan_sim_1","customer_id":"cust_sub_sim","status":"%s","quantity":1}},"payment":{"entity":{"id":"%s","entity":"payment","amount":%d,"currency":"INR","status":"failed","email":"sub.user@example.com","contact":"+919111111111","error_reason":"%s"}}},"created_at":%d}"""
				.formatted(
						eventId,
						eventType,
						subscriptionId,
						subStatus,
						paymentId,
						amountPaise,
						paymentReason,
						createdAt);
	}

	static String invoiceExpired(String eventId, String invoiceId, long amountPaise, long createdAt) {
		return """
				{"id":"%s","entity":"event","account_id":"acc_test_recovery","event":"invoice.expired","contains":["invoice"],"payload":{"invoice":{"entity":{"id":"%s","entity":"invoice","customer_id":"cust_inv_sim","status":"expired","amount":%d,"amount_paid":0,"currency":"INR"}}},"created_at":%d}"""
				.formatted(eventId, invoiceId, amountPaise, createdAt);
	}

	static String checkoutAbandoned(String eventId, String checkoutId, long amountPaise, long createdAt) {
		return """
				{"id":"%s","entity":"event","account_id":"acc_test_recovery","event":"checkout.abandoned","contains":["checkout"],"payload":{"checkout":{"entity":{"id":"%s","entity":"checkout","amount":%d,"currency":"INR","status":"abandoned","customer_id":"cust_cart_sim","email":"cart.drop@example.com","contact":"+919222222222"}}},"created_at":%d}"""
				.formatted(eventId, checkoutId, amountPaise, createdAt);
	}
}
