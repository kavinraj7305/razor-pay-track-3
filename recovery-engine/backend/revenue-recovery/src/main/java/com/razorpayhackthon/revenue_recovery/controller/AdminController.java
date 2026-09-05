package com.razorpayhackthon.revenue_recovery.controller;

import com.razorpayhackthon.revenue_recovery.auth.AuthContext;
import com.razorpayhackthon.revenue_recovery.auth.RequireRole;
import com.razorpayhackthon.revenue_recovery.dto.auth.AssignRoleRequest;
import com.razorpayhackthon.revenue_recovery.dto.auth.CreateUserRequest;
import com.razorpayhackthon.revenue_recovery.dto.auth.DashboardSnapshot;
import com.razorpayhackthon.revenue_recovery.dto.auth.PlatformStatus;
import com.razorpayhackthon.revenue_recovery.dto.auth.UserRow;
import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import com.razorpayhackthon.revenue_recovery.service.auth.AuthService;
import com.razorpayhackthon.revenue_recovery.service.auth.BenchmarkService;
import com.razorpayhackthon.revenue_recovery.service.auth.DashboardService;
import com.razorpayhackthon.revenue_recovery.service.auth.PlatformStatusService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequireRole(DeskRole.ADMIN)
public class AdminController {

	private final DashboardService dashboardService;
	private final AuthService authService;
	private final BenchmarkService benchmarkService;
	private final PlatformStatusService platformStatusService;

	public AdminController(
			DashboardService dashboardService,
			AuthService authService,
			BenchmarkService benchmarkService,
			PlatformStatusService platformStatusService) {
		this.dashboardService = dashboardService;
		this.authService = authService;
		this.benchmarkService = benchmarkService;
		this.platformStatusService = platformStatusService;
	}

	@GetMapping(path = "/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
	public DashboardSnapshot dashboard() {
		return dashboardService.snapshot();
	}

	@GetMapping(path = "/benchmark", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> benchmark() {
		return benchmarkService.latest();
	}

	@GetMapping(path = "/platform", produces = MediaType.APPLICATION_JSON_VALUE)
	public PlatformStatus platform() {
		return platformStatusService.snapshot();
	}

	@GetMapping(path = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<UserRow> users() {
		return authService.listUsers();
	}

	@PostMapping(path = "/users", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public UserRow create(@RequestBody CreateUserRequest request) {
		return authService.createUser(request);
	}

	@PostMapping(
			path = "/users/{userId}/role",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public UserRow assign(@PathVariable String userId, @RequestBody AssignRoleRequest request) {
		return authService.assignRole(userId, request, AuthContext.require());
	}
}
