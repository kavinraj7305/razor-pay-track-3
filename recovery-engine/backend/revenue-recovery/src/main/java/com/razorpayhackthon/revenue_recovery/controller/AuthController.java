package com.razorpayhackthon.revenue_recovery.controller;

import com.razorpayhackthon.revenue_recovery.auth.AuthContext;
import com.razorpayhackthon.revenue_recovery.dto.auth.DemoAccount;
import com.razorpayhackthon.revenue_recovery.dto.auth.LoginRequest;
import com.razorpayhackthon.revenue_recovery.dto.auth.SessionResponse;
import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import com.razorpayhackthon.revenue_recovery.service.auth.AuthService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping(path = "/demo", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<DemoAccount> demo() {
		return AuthService.DEMO_ACCOUNTS.stream()
				.filter(account -> account.role() != DeskRole.OPERATOR)
				.toList();
	}

	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public SessionResponse login(@RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
	public SessionResponse me() {
		return authService.me(AuthContext.require());
	}

	@PostMapping(path = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
	public SessionResponse logout() {
		authService.logout(AuthContext.require());
		return new SessionResponse(null, null, null, null, null);
	}
}
