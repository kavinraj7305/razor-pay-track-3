package com.razorpayhackthon.revenue_recovery.service.plan.handler;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.service.plan.PlannedDecision;

public interface BaselineReasonHandler {

	boolean supports(RecoveryCase recoveryCase);

	PlannedDecision decide(RecoveryCase recoveryCase);

	int executeNext(RecoveryCase recoveryCase);

	default String reasonOf(RecoveryCase recoveryCase) {
		return recoveryCase.getReason() == null ? "" : recoveryCase.getReason().toLowerCase();
	}
}
