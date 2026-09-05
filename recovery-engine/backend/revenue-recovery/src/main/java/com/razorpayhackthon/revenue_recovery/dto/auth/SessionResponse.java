package com.razorpayhackthon.revenue_recovery.dto.auth;

import com.razorpayhackthon.revenue_recovery.enums.DeskRole;

public record SessionResponse(
		String token,
		String userId,
		String email,
		String displayName,
		DeskRole role) {}
