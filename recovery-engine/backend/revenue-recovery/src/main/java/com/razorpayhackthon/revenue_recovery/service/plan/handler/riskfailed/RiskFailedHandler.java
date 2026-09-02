package com.razorpayhackthon.revenue_recovery.service.plan.handler.riskfailed;

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
@Order(15)
public class RiskFailedHandler implements BaselineReasonHandler {

	private final List<RiskFailedStep> steps;
	private final PlaybookRunner playbookRunner;

	public RiskFailedHandler(List<RiskFailedStep> steps, PlaybookRunner playbookRunner) {
		this.steps = steps;
		this.playbookRunner = playbookRunner;
	}

	@Override
	public boolean supports(RecoveryCase recoveryCase) {
		return reasonOf(recoveryCase).contains("payment_risk_check_failed");
	}

	@Override
	public PlannedDecision decide(RecoveryCase recoveryCase) {
		return new PlannedDecision(
				RecoveryActionType.SEND_EMAIL,
				RecoveryActionStatus.CANCELLED,
				RecoveryCaseStatus.OPEN,
				true,
				"STOP: no auto-retry on risk — escalate to human");
	}

	@Override
	public int executeNext(RecoveryCase recoveryCase) {
		return playbookRunner.executeNext(recoveryCase, steps, "payment_risk_check_failed");
	}

	@Override
	public List<PlaybookStepPreview> playbook() {
		return PlaybookPreviews.from(steps);
	}
}
