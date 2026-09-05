package com.razorpayhackthon.revenue_recovery.dto.auth;

import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import java.time.LocalDateTime;

public record UserRow(
		String userId,
		String email,
		String displayName,
		DeskRole role,
		boolean active,
		LocalDateTime createdAt) {}
