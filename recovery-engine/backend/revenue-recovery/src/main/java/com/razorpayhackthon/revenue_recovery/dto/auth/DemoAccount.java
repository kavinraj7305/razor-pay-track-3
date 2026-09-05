package com.razorpayhackthon.revenue_recovery.dto.auth;

import com.razorpayhackthon.revenue_recovery.enums.DeskRole;

public record DemoAccount(String email, String password, DeskRole role, String displayName, String sees) {}
