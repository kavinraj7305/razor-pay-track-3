package com.razorpayhackthon.revenue_recovery.controller;

import com.razorpayhackthon.revenue_recovery.entity.WebhookEvent;
import com.razorpayhackthon.revenue_recovery.enums.WebhookIntake;
import com.razorpayhackthon.revenue_recovery.repository.WebhookEventRepository;
import com.razorpayhackthon.revenue_recovery.webhook.RazorpaySignatureVerifier;
import com.razorpayhackthon.revenue_recovery.webhook.RedisWebhookIdempotencyStore;
import com.razorpayhackthon.revenue_recovery.webhook.WebhookAck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
		properties = {
			"razorpay.webhook-secret=whsec_test",
			"spring.kafka.listener.auto-startup=false"
		})
class RazorpayWebhookControllerTest {

	private static final String BODY =
			"""
			{"id":"evt_test_fail_1","entity":"event","account_id":"acc_test","event":"payment.failed","contains":["payment"],"payload":{"payment":{"entity":{"id":"pay_test_fail_1","entity":"payment","amount":50000,"currency":"INR","status":"failed","order_id":"order_test_1","method":"card","error_code":"BAD_REQUEST_ERROR","error_description":"Payment failed","error_reason":"payment_failed"}}},"created_at":1710000000}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RazorpaySignatureVerifier signatureVerifier;

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private WebhookEventRepository webhookEventRepository;

	@MockitoBean
	private KafkaTemplate<String, String> kafkaTemplate;

	@BeforeEach
	void reset() {
		when(kafkaTemplate.send(anyString(), anyString(), anyString()))
				.thenReturn(CompletableFuture.completedFuture(null));
		redis.delete(RedisWebhookIdempotencyStore.key("evt_test_fail_1"));
		webhookEventRepository.findByEventId("evt_test_fail_1").ifPresent(webhookEventRepository::delete);
	}

	@Test
	void acceptsSignedPayloadPublishesToPaymentEventsAndIsIdempotent() throws Exception {
		String signature = signatureVerifier.sign(BODY, "whsec_test");

		MvcResult first =
				mockMvc.perform(
								post("/webhooks/razorpay")
										.contentType(MediaType.APPLICATION_JSON)
										.header("X-Razorpay-Signature", signature)
										.content(BODY))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.received").value(true))
						.andExpect(jsonPath("$.eventType").value("payment.failed"))
						.andExpect(jsonPath("$.duplicate").value(false))
						.andReturn();

		WebhookAck ack = jsonMapper.readValue(first.getResponse().getContentAsByteArray(), WebhookAck.class);
		assertThat(ack.eventId()).isEqualTo("evt_test_fail_1");
		verify(kafkaTemplate).send(eq("payment.events"), eq("evt_test_fail_1"), eq(BODY));

		WebhookEvent stored = webhookEventRepository.findByEventId("evt_test_fail_1").orElseThrow();
		assertThat(stored.getIntake()).isEqualTo(WebhookIntake.HMAC_SIGNED.name());
		assertThat(stored.isSignatureVerified()).isTrue();
		assertThat(stored.getEventType()).isEqualTo("payment.failed");

		mockMvc.perform(
						post("/webhooks/razorpay")
								.contentType(MediaType.APPLICATION_JSON)
								.header("X-Razorpay-Signature", signature)
								.content(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value("evt_test_fail_1"))
				.andExpect(jsonPath("$.duplicate").value(true));

		verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
	}

	@Test
	void rejectsMalformedPayloadWith400() throws Exception {
		String body = "{\"event\":\"payment.failed\",\"account_id\":\"acc_test\"}";
		String signature = signatureVerifier.sign(body, "whsec_test");

		mockMvc.perform(
						post("/webhooks/razorpay")
								.contentType(MediaType.APPLICATION_JSON)
								.header("X-Razorpay-Signature", signature)
								.content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInvalidSignature() throws Exception {
		mockMvc.perform(
						post("/webhooks/razorpay")
								.contentType(MediaType.APPLICATION_JSON)
								.header("X-Razorpay-Signature", "ab".repeat(32))
								.content(BODY))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsMissingSignature() throws Exception {
		mockMvc.perform(
						post("/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized());
	}
}
