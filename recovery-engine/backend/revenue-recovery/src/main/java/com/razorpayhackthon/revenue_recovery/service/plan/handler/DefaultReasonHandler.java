package com.razorpayhackthon.revenue_recovery.service.plan.handler;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.service.plan.PlannedDecision;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Catch-all until each remaining reason gets its own handler file.
 */
@Service
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultReasonHandler implements BaselineReasonHandler {

	@Override
	public boolean supports(RecoveryCase recoveryCase) {
		return true;
	}

	@Override
	public PlannedDecision decide(RecoveryCase recoveryCase) {
		String reason = reasonOf(recoveryCase);
		RecoverySource source = recoveryCase.getSource();

		if (reason.contains("payment_risk_check_failed") || reason.contains("payment_cancelled")) {
			return new PlannedDecision(
					RecoveryActionType.SEND_EMAIL,
					RecoveryActionStatus.CANCELLED,
					RecoveryCaseStatus.OPEN,
					true,
					"STOP: no auto-retry on risk/cancel — escalate to human");
		}
		if (source == RecoverySource.INVOICE || reason.contains("invoice.expired")) {
			return new PlannedDecision(
					RecoveryActionType.REQUEST_PROMISE_TO_PAY,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"B2B chase: capture promise-to-pay");
		}
		if (source == RecoverySource.CHECKOUT_SESSION || reason.contains("checkout.abandoned")) {
			return new PlannedDecision(
					RecoveryActionType.SEND_PAYMENT_LINK,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"Checkout drop-off: send payment link");
		}
		if (reason.contains("subscription.halted")) {
			return new PlannedDecision(
					RecoveryActionType.SEND_PAYMENT_LINK,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"Retries exhausted: send link to update mandate");
		}
		if (reason.contains("card_expired") || reason.contains("invalid_vpa")) {
			return new PlannedDecision(
					RecoveryActionType.SEND_PAYMENT_LINK,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"Instrument dead: send payment link (do not retry same card)");
		}
		if (reason.contains("gateway_technical")
				|| reason.contains("bank_technical")
				|| reason.contains("subscription.pending")) {
			return new PlannedDecision(
					RecoveryActionType.RETRY_PAYMENT,
					RecoveryActionStatus.PLANNED,
					RecoveryCaseStatus.ACTION_PLANNED,
					false,
					"Transient fail: delayed retry");
		}
		return new PlannedDecision(
				RecoveryActionType.RETRY_PAYMENT,
				RecoveryActionStatus.PLANNED,
				RecoveryCaseStatus.ACTION_PLANNED,
				false,
				"Default baseline: retry then link, max 3");
	}
}
