package com.razorpayhackthon.revenue_recovery.service.plan.handler;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.service.plan.PlannedDecision;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultReasonHandler implements BaselineReasonHandler {

	@Override
	public boolean supports(RecoveryCase recoveryCase) {
		return true;
	}

	@Override
	public PlannedDecision decide(RecoveryCase recoveryCase) {
		return new PlannedDecision(
				RecoveryActionType.RETRY_PAYMENT,
				RecoveryActionStatus.PLANNED,
				RecoveryCaseStatus.ACTION_PLANNED,
				false,
				"Default baseline: retry then link, max 3");
	}

	@Override
	public int executeNext(RecoveryCase recoveryCase) {
		throw new ResponseStatusException(
				HttpStatus.CONFLICT, "no playbook for reason " + reasonOf(recoveryCase));
	}
}
