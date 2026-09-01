package com.razorpayhackthon.revenue_recovery.service.ingest;

import com.razorpayhackthon.revenue_recovery.entity.CheckoutSession;
import com.razorpayhackthon.revenue_recovery.entity.Customer;
import com.razorpayhackthon.revenue_recovery.entity.Merchant;
import com.razorpayhackthon.revenue_recovery.entity.Payment;
import com.razorpayhackthon.revenue_recovery.enums.CheckoutSessionStatus;
import com.razorpayhackthon.revenue_recovery.enums.PaymentStatus;
import com.razorpayhackthon.revenue_recovery.repository.CheckoutSessionRepository;
import com.razorpayhackthon.revenue_recovery.repository.PaymentRepository;
import com.razorpayhackthon.revenue_recovery.webhook.ParsedRazorpayEvent;
import com.razorpayhackthon.revenue_recovery.webhook.RazorpayWebhookParser;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
class RecoverySourceWriter {

	private final PaymentRepository paymentRepository;
	private final CheckoutSessionRepository checkoutSessionRepository;

	RecoverySourceWriter(
			PaymentRepository paymentRepository, CheckoutSessionRepository checkoutSessionRepository) {
		this.paymentRepository = paymentRepository;
		this.checkoutSessionRepository = checkoutSessionRepository;
	}

	void write(ParsedRazorpayEvent parsed, Merchant merchant, Customer customer, RecoveryCaseDraft draft) {
		if ("payment.failed".equals(parsed.eventType())) {
			writeFailedPayment(merchant, customer, parsed, draft);
		}
		if ("checkout.abandoned".equals(parsed.eventType())) {
			writeAbandonedCheckout(merchant, customer, parsed, draft);
		}
	}

	private void writeFailedPayment(
			Merchant merchant, Customer customer, ParsedRazorpayEvent parsed, RecoveryCaseDraft draft) {
		JsonNode paymentNode = RazorpayWebhookParser.nestedEntity(parsed.root().get("payload"), "payment");
		String paymentId = WebhookPayloadSupport.text(paymentNode, "id");
		Payment payment = paymentRepository.findByPaymentId(paymentId).orElseGet(Payment::new);
		payment.setPaymentId(paymentId);
		payment.setMerchant(merchant);
		payment.setCustomer(customer);
		payment.setAmount(draft.amountAtRisk());
		payment.setCurrency(draft.currency());
		payment.setStatus(PaymentStatus.FAILED);
		payment.setPaymentType(WebhookPayloadSupport.textOrNull(paymentNode, "method"));
		paymentRepository.save(payment);
	}

	private void writeAbandonedCheckout(
			Merchant merchant, Customer customer, ParsedRazorpayEvent parsed, RecoveryCaseDraft draft) {
		JsonNode checkoutNode = RazorpayWebhookParser.nestedEntity(parsed.root().get("payload"), "checkout");
		String checkoutId = WebhookPayloadSupport.text(checkoutNode, "id");
		CheckoutSession session =
				checkoutSessionRepository.findByCheckoutSessionId(checkoutId).orElseGet(CheckoutSession::new);
		session.setCheckoutSessionId(checkoutId);
		session.setMerchant(merchant);
		session.setCustomer(customer);
		session.setAmount(draft.amountAtRisk());
		session.setCurrency(draft.currency());
		session.setStatus(CheckoutSessionStatus.ABANDONED);
		session.setAbandonedAt(LocalDateTime.now());
		checkoutSessionRepository.save(session);
	}
}
