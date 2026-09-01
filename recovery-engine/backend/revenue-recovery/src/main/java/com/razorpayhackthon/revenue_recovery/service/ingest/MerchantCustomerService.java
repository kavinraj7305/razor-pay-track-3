package com.razorpayhackthon.revenue_recovery.service.ingest;

import com.razorpayhackthon.revenue_recovery.entity.Customer;
import com.razorpayhackthon.revenue_recovery.entity.Merchant;
import com.razorpayhackthon.revenue_recovery.enums.CustomerStatus;
import com.razorpayhackthon.revenue_recovery.enums.MerchantStatus;
import com.razorpayhackthon.revenue_recovery.repository.CustomerRepository;
import com.razorpayhackthon.revenue_recovery.repository.MerchantRepository;
import com.razorpayhackthon.revenue_recovery.webhook.ParsedRazorpayEvent;
import com.razorpayhackthon.revenue_recovery.webhook.RazorpayWebhookParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
class MerchantCustomerService {

	private final MerchantRepository merchantRepository;
	private final CustomerRepository customerRepository;

	MerchantCustomerService(MerchantRepository merchantRepository, CustomerRepository customerRepository) {
		this.merchantRepository = merchantRepository;
		this.customerRepository = customerRepository;
	}

	Merchant upsertMerchant(String accountId, String currency) {
		return merchantRepository
				.findByMerchantId(accountId)
				.orElseGet(
						() -> {
							Merchant merchant = new Merchant();
							merchant.setMerchantId(accountId);
							merchant.setName("Razorpay " + accountId);
							merchant.setDefaultCurrency(currency);
							merchant.setStatus(MerchantStatus.ACTIVE);
							return merchantRepository.save(merchant);
						});
	}

	Customer upsertCustomer(Merchant merchant, ParsedRazorpayEvent parsed) {
		JsonNode payload = parsed.root().get("payload");
		JsonNode payment = RazorpayWebhookParser.nestedEntityOrNull(payload, "payment");
		JsonNode subscription = RazorpayWebhookParser.nestedEntityOrNull(payload, "subscription");
		JsonNode invoice = RazorpayWebhookParser.nestedEntityOrNull(payload, "invoice");
		JsonNode checkout = RazorpayWebhookParser.nestedEntityOrNull(payload, "checkout");

		String razorpayCustomerId = WebhookPayloadSupport.firstNonBlank(
				WebhookPayloadSupport.textOrNull(subscription, "customer_id"),
				WebhookPayloadSupport.textOrNull(invoice, "customer_id"),
				WebhookPayloadSupport.textOrNull(checkout, "customer_id"));
		String email = WebhookPayloadSupport.firstNonBlank(
				WebhookPayloadSupport.textOrNull(payment, "email"),
				WebhookPayloadSupport.textOrNull(checkout, "email"));
		String phone = WebhookPayloadSupport.firstNonBlank(
				WebhookPayloadSupport.textOrNull(payment, "contact"),
				WebhookPayloadSupport.textOrNull(checkout, "contact"));
		if (razorpayCustomerId == null && email == null && phone == null) {
			return null;
		}

		String customerId =
				razorpayCustomerId != null
						? razorpayCustomerId
						: "cust_" + sha256Prefix(merchant.getMerchantId() + "|" + (email != null ? email : phone));
		if (customerId.length() > 50) {
			customerId = customerId.substring(0, 50);
		}

		final String resolvedId = customerId;
		final String resolvedEmail = email;
		final String resolvedPhone = phone;
		return customerRepository
				.findByCustomerId(resolvedId)
				.orElseGet(
						() -> {
							Customer customer = new Customer();
							customer.setCustomerId(resolvedId);
							customer.setMerchant(merchant);
							customer.setName(nameFrom(resolvedEmail, resolvedId));
							customer.setEmail(resolvedEmail);
							customer.setPhone(resolvedPhone);
							customer.setStatus(CustomerStatus.ACTIVE);
							return customerRepository.save(customer);
						});
	}

	private static String nameFrom(String email, String fallbackId) {
		if (email != null && email.contains("@")) {
			return email.substring(0, email.indexOf('@'));
		}
		return "Razorpay customer " + fallbackId;
	}

	private static String sha256Prefix(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of()
					.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
					.substring(0, 16);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required", ex);
		}
	}
}
