package com.razorpayhackthon.revenue_recovery.auth;

import com.razorpayhackthon.revenue_recovery.entity.DeskUser;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public final class AuthContext {

	public static final String ATTR = "deskUser";

	private AuthContext() {}

	public static DeskUser current() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes == null) {
			return null;
		}
		Object value = attributes.getAttribute(ATTR, RequestAttributes.SCOPE_REQUEST);
		return value instanceof DeskUser user ? user : null;
	}

	public static DeskUser require() {
		DeskUser user = current();
		if (user == null) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.UNAUTHORIZED, "sign in required");
		}
		return user;
	}
}
