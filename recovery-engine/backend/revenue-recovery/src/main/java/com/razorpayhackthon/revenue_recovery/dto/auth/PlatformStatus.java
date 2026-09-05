package com.razorpayhackthon.revenue_recovery.dto.auth;

import java.util.List;

public record PlatformStatus(boolean ready, List<PlatformComponent> components) {}
