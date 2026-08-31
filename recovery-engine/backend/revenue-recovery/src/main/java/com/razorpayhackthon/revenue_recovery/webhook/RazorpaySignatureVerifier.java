package com.razorpayhackthon.revenue_recovery.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class RazorpaySignatureVerifier {

	private static final HexFormat HEX = HexFormat.of();

	public boolean isValid(String rawBody, String signature, String webhookSecret) {
		if (rawBody == null || signature == null || webhookSecret == null || webhookSecret.isBlank()) {
			return false;
		}
		byte[] provided;
		try {
			provided = HEX.parseHex(signature.trim());
		} catch (IllegalArgumentException ex) {
			return false;
		}
		byte[] expected = hmacSha256(rawBody, webhookSecret);
		return MessageDigest.isEqual(expected, provided);
	}

	public String sign(String rawBody, String webhookSecret) {
		return HEX.formatHex(hmacSha256(rawBody, webhookSecret));
	}

	private static byte[] hmacSha256(String rawBody, String webhookSecret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HMAC-SHA256 is required for Razorpay webhook verification", ex);
		}
	}
}
