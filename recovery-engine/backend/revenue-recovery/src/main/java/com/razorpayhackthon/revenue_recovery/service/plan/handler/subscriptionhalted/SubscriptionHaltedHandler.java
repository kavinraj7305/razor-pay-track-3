package com.razorpayhackthon.revenue_recovery.service.plan.handler.subscriptionhalted;

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
@Order(50)
public class SubscriptionHaltedHandler implements BaselineReasonHandler {

	private final List<SubscriptionHaltedStep> steps;
	private final PlaybookRunner playbookRunner;

	public SubscriptionHaltedHandler(List<SubscriptionHaltedStep> steps, PlaybookRunner playbookRunner) {
		this.steps = steps;
		this.playbookRunner = playbookRunner;
	}

	@Override
	public boolean supports(RecoveryCase recoveryCase) {
		return reasonOf(recoveryCase).contains("subscription.halted");
	}

	@Override
	public PlannedDecision decide(RecoveryCase recoveryCase) {
		return new PlannedDecision(
				RecoveryActionType.SEND_PAYMENT_LINK,
				RecoveryActionStatus.PLANNED,
				RecoveryCaseStatus.ACTION_PLANNED,
				false,
				"Step 1: retries exhausted — send link to update mandate");
	}

	@Override
	public int executeNext(RecoveryCase recoveryCase) {
		return playbookRunner.executeNext(recoveryCase, steps, "subscription.halted");
	}

	@Override
	public List<PlaybookStepPreview> playbook() {
		return PlaybookPreviews.from(steps);
	}
}
