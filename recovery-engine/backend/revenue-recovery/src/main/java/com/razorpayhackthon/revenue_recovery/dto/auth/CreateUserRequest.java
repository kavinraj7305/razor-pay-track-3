package com.razorpayhackthon.revenue_recovery.dto.auth;

import com.razorpayhackthon.revenue_recovery.enums.DeskRole;

public record CreateUserRequest(String email, String displayName, String password, DeskRole role) {}
