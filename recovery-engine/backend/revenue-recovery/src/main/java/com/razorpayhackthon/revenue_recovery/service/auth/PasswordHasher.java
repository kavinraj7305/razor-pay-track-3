package com.razorpayhackthon.revenue_recovery.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Demo hash only — not a production password store. */
public final class PasswordHasher {

	private static final String PEPPER = "recovery-desk|";

	private PasswordHasher() {}

	public static String hash(String password) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest((PEPPER + password).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static boolean matches(String password, String stored) {
		return stored != null && hash(password).equals(stored);
	}
}
