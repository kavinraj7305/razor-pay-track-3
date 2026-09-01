package com.razorpayhackthon.revenue_recovery.service.plan.handler;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;

public interface PlaybookStep {

	int stepNumber();

	RecoveryActionType actionType();

	String planNote();

	void execute(RecoveryCase recoveryCase, RecoveryAction action);
}
