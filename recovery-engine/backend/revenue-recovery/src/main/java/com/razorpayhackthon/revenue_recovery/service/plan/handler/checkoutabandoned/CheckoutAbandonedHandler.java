package com.razorpayhackthon.revenue_recovery.service.plan.handler.checkoutabandoned;

import com.razorpayhackthon.revenue_recovery.dto.PlaybookStepPreview;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.service.plan.PlannedDecision;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.BaselineReasonHandler;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.PlaybookPreviews;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.PlaybookRunner;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(70)
public class CheckoutAbandonedHandler implements BaselineReasonHandler {

	private final List<CheckoutAbandonedStep> steps;
	private final PlaybookRunner playbookRunner;

	public CheckoutAbandonedHandler(List<CheckoutAbandonedStep> steps, PlaybookRunner playbookRunner) {
		this.steps = steps;
		this.playbookRunner = playbookRunner;
	}

	@Override
	public boolean supports(RecoveryCase recoveryCase) {
		return recoveryCase.getSource() == RecoverySource.CHECKOUT_SESSION
				|| reasonOf(recoveryCase).contains("checkout.abandoned");
	}

	@Override
	public PlannedDecision decide(RecoveryCase recoveryCase) {
		return new PlannedDecision(
				RecoveryActionType.SEND_PAYMENT_LINK,
				RecoveryActionStatus.PLANNED,
				RecoveryCaseStatus.ACTION_PLANNED,
				false,
				"Step 1: checkout drop-off — send payment link");
	}

	@Override
	public int executeNext(RecoveryCase recoveryCase) {
		return playbookRunner.executeNext(recoveryCase, steps, "checkout.abandoned");
	}

	@Override
	public List<PlaybookStepPreview> playbook() {
		return PlaybookPreviews.from(steps);
	}
}
