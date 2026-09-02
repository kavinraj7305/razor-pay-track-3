package com.razorpayhackthon.revenue_recovery.service.plan.handler.invalidvpa;

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
@Order(25)
public class InvalidVpaHandler implements BaselineReasonHandler {

	private final List<InvalidVpaStep> steps;
	private final PlaybookRunner playbookRunner;

	public InvalidVpaHandler(List<InvalidVpaStep> steps, PlaybookRunner playbookRunner) {
		this.steps = steps;
		this.playbookRunner = playbookRunner;
	}

	@Override
	public boolean supports(RecoveryCase recoveryCase) {
		return reasonOf(recoveryCase).contains("invalid_vpa");
	}

	@Override
	public PlannedDecision decide(RecoveryCase recoveryCase) {
		return new PlannedDecision(
				RecoveryActionType.SEND_PAYMENT_LINK,
				RecoveryActionStatus.PLANNED,
				RecoveryCaseStatus.ACTION_PLANNED,
				false,
				"Step 1: VPA is dead — send payment link (do not retry UPI)");
	}

	@Override
	public int executeNext(RecoveryCase recoveryCase) {
		return playbookRunner.executeNext(recoveryCase, steps, "invalid_vpa");
	}

	@Override
	public List<PlaybookStepPreview> playbook() {
		return PlaybookPreviews.from(steps);
	}
}
