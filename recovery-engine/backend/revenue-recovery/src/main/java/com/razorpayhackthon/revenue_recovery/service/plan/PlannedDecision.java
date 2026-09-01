package com.razorpayhackthon.revenue_recovery.service.plan;

import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;

public record PlannedDecision(
		RecoveryActionType actionType,
		RecoveryActionStatus status,
		RecoveryCaseStatus caseStatus,
		boolean blocked,
		String note) {}
