package com.razorpayhackthon.revenue_recovery.service.plan.handler.subscriptionpending;

import com.razorpayhackthon.revenue_recovery.dto.PlaybookStepPreview;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.service.plan.PlannedDecision;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.BaselineReasonHandler;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.PlaybookPreviews;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.PlaybookRunner;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(40)
public class SubscriptionPendingHandler implements BaselineReasonHandler {

	private final List<SubscriptionPendingStep> steps;
	private final PlaybookRunner playbookRunner;

	public SubscriptionPendingHandler(List<SubscriptionPendingStep> steps, PlaybookRunner playbookRunner) {
		this.steps = steps;
		this.playbookRunner = playbookRunner;
	}

	@Override
	public boolean supports(RecoveryCase recoveryCase) {
		return reasonOf(recoveryCase).contains("subscription.pending");
	}

	@Override
	public PlannedDecision decide(RecoveryCase recoveryCase) {
		return new PlannedDecision(
				RecoveryActionType.RETRY_PAYMENT,
				RecoveryActionStatus.PLANNED,
				RecoveryCaseStatus.ACTION_PLANNED,
				false,
				"Step 1: silent delayed retry (subscription still alive)");
	}

	@Override
	public int executeNext(RecoveryCase recoveryCase) {
		return playbookRunner.executeNext(recoveryCase, steps, "subscription.pending");
	}

	@Override
	public List<PlaybookStepPreview> playbook() {
		return PlaybookPreviews.from(steps);
	}
}
