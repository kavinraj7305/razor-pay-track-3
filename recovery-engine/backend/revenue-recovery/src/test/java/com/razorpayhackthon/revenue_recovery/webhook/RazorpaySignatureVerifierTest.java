package com.razorpayhackthon.revenue_recovery.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RazorpaySignatureVerifierTest {

	private final RazorpaySignatureVerifier verifier = new RazorpaySignatureVerifier();

	@Test
	void acceptsMatchingHmac() {
		String body = "{\"event\":\"payment.failed\"}";
		String secret = "whsec_test";
		String signature = verifier.sign(body, secret);

		assertThat(verifier.isValid(body, signature, secret)).isTrue();
	}

	@Test
	void rejectsTamperedBody() {
		String secret = "whsec_test";
		String signature = verifier.sign("{\"event\":\"payment.failed\"}", secret);

		assertThat(verifier.isValid("{\"event\":\"payment.captured\"}", signature, secret)).isFalse();
	}

	@Test
	void rejectsWrongSecret() {
		String body = "{\"event\":\"payment.failed\"}";
		String signature = verifier.sign(body, "whsec_test");

		assertThat(verifier.isValid(body, signature, "other_secret")).isFalse();
	}

	@Test
	void rejectsMalformedSignature() {
		assertThat(verifier.isValid("{}", "not-hex", "whsec_test")).isFalse();
		assertThat(verifier.isValid("{}", null, "whsec_test")).isFalse();
		assertThat(verifier.isValid("{}", "ab", "")).isFalse();
	}
}
