package com.razorpayhackthon.revenue_recovery.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.razorpayhackthon.revenue_recovery.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "razorpay.webhook-secret=whsec_test")
class RazorpayWebhookControllerTest {

	private static final String BODY = """
			{"entity":"event","account_id":"acc_test","event":"payment.failed","contains":["payment"],"payload":{"payment":{"entity":{"id":"pay_test_fail_1","entity":"payment","amount":50000,"currency":"INR","status":"failed","order_id":"order_test_1","method":"card","error_code":"BAD_REQUEST_ERROR","error_description":"Payment failed","error_reason":"payment_failed"}}},"created_at":1710000000}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RazorpaySignatureVerifier signatureVerifier;

	@Autowired
	private WebhookEventRepository webhookEventRepository;

	@Autowired
	private JsonMapper jsonMapper;

	@BeforeEach
	void cleanInbox() {
		webhookEventRepository.deleteAll();
	}

	@Test
	void acceptsSignedPayloadAndIsIdempotent() throws Exception {
		String signature = signatureVerifier.sign(BODY, "whsec_test");

		MvcResult first = mockMvc.perform(post("/webhooks/razorpay")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Razorpay-Signature", signature)
						.content(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.received").value(true))
				.andExpect(jsonPath("$.eventType").value("payment.failed"))
				.andExpect(jsonPath("$.duplicate").value(false))
				.andReturn();

		WebhookAck ack = jsonMapper.readValue(first.getResponse().getContentAsByteArray(), WebhookAck.class);
		assertThat(webhookEventRepository.existsByEventId(ack.eventId())).isTrue();

		mockMvc.perform(post("/webhooks/razorpay")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Razorpay-Signature", signature)
						.content(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(ack.eventId()))
				.andExpect(jsonPath("$.duplicate").value(true));
	}

	@Test
	void rejectsInvalidSignature() throws Exception {
		mockMvc.perform(post("/webhooks/razorpay")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Razorpay-Signature", "ab".repeat(32))
						.content(BODY))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsMissingSignature() throws Exception {
		mockMvc.perform(post("/webhooks/razorpay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isUnauthorized());
	}
}
