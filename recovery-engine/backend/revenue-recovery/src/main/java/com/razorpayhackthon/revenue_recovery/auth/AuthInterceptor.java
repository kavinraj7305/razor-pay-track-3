package com.razorpayhackthon.revenue_recovery.auth;

import com.razorpayhackthon.revenue_recovery.entity.DeskUser;
import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import com.razorpayhackthon.revenue_recovery.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

	private static final Set<String> PUBLIC = Set.of(
			"/api/auth/login",
			"/api/auth/demo",
			"/webhooks/razorpay");

	private final AuthService authService;

	public AuthInterceptor(AuthService authService) {
		this.authService = authService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		String path = request.getRequestURI();
		if (PUBLIC.contains(path) || path.startsWith("/actuator")) {
			return true;
		}
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		try {
			DeskUser user = authService.requireToken(tokenOf(request));
			request.setAttribute(AuthContext.ATTR, user);
			RequireRole required = method.getMethodAnnotation(RequireRole.class);
			if (required == null) {
				required = method.getBeanType().getAnnotation(RequireRole.class);
			}
			if (required != null && !allowed(user.getRole(), required.value())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "this role cannot do that");
			}
			return true;
		} catch (ResponseStatusException ex) {
			write(response, ex.getStatusCode().value(), ex.getReason());
			return false;
		}
	}

	private static boolean allowed(DeskRole have, DeskRole[] need) {
		for (DeskRole role : need) {
			if (role == have) {
				return true;
			}
		}
		return false;
	}

	private static String tokenOf(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return header.substring(7).trim();
		}
		return request.getHeader("X-Auth-Token");
	}

	private static void write(HttpServletResponse response, int status, String reason) {
		try {
			response.setStatus(status);
			response.setContentType("application/json");
			String message = reason == null ? "unauthorized" : reason.replace("\"", "'");
			response.getWriter().write("{\"error\":\"" + message + "\"}");
		} catch (Exception ignored) {
			response.setStatus(status);
		}
	}
}
