package com.razorpayhackthon.revenue_recovery.dto.auth;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSnapshot(
		long cases,
		long open,
		long recovered,
		long failed,
		long pendingApprovals,
		BigDecimal amountAtRisk,
		long adminCount,
		long approverCount,
		long operatorCount,
		List<ReasonCount> byReason,
		List<UserRow> users) {}
