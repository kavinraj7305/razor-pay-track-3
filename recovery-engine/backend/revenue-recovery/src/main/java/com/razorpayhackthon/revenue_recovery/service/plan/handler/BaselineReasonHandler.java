package com.razorpayhackthon.revenue_recovery.service.plan.handler;

import com.razorpayhackthon.revenue_recovery.dto.PlaybookStepPreview;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.service.plan.PlannedDecision;
import java.util.List;

public interface BaselineReasonHandler {

	boolean supports(RecoveryCase recoveryCase);

	PlannedDecision decide(RecoveryCase recoveryCase);

	int executeNext(RecoveryCase recoveryCase);

	default List<PlaybookStepPreview> playbook() {
		return List.of();
	}

	default String reasonOf(RecoveryCase recoveryCase) {
		return recoveryCase.getReason() == null ? "" : recoveryCase.getReason().toLowerCase();
	}
}
