package com.razorpayhackthon.revenue_recovery.service.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.BaselineReasonHandler;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.DefaultReasonHandler;
import com.razorpayhackthon.revenue_recovery.service.plan.handler.insufficientfunds.InsufficientFundsHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaselineActionPlannerTest {

	private final List<BaselineReasonHandler> handlers =
			List.of(new InsufficientFundsHandler(List.of(), null, null), new DefaultReasonHandler());

	@Test
	void insufficientFundsUsesOwnHandler() {
		RecoveryCase recoveryCase = caseWith("insufficient_funds", RecoverySource.PAYMENT);
		BaselineReasonHandler handler = pick(recoveryCase);
		assertThat(handler).isInstanceOf(InsufficientFundsHandler.class);
		assertThat(handler.decide(recoveryCase).actionType()).isEqualTo(RecoveryActionType.RETRY_PAYMENT);
		assertThat(handler.decide(recoveryCase).note()).contains("silent delayed retry");
	}

	@Test
	void otherReasonsStillMap() {
		assertThat(decide("card_expired", RecoverySource.PAYMENT).actionType())
				.isEqualTo(RecoveryActionType.SEND_PAYMENT_LINK);
		assertThat(decide("payment_risk_check_failed", RecoverySource.PAYMENT).status())
				.isEqualTo(RecoveryActionStatus.CANCELLED);
		assertThat(decide("subscription.pending", RecoverySource.SUBSCRIPTION).actionType())
				.isEqualTo(RecoveryActionType.RETRY_PAYMENT);
		assertThat(decide("subscription.halted", RecoverySource.SUBSCRIPTION).actionType())
				.isEqualTo(RecoveryActionType.SEND_PAYMENT_LINK);
		assertThat(decide("invoice.expired", RecoverySource.INVOICE).actionType())
				.isEqualTo(RecoveryActionType.REQUEST_PROMISE_TO_PAY);
		assertThat(decide("checkout.abandoned", RecoverySource.CHECKOUT_SESSION).actionType())
				.isEqualTo(RecoveryActionType.SEND_PAYMENT_LINK);
	}

	private PlannedDecision decide(String reason, RecoverySource source) {
		RecoveryCase recoveryCase = caseWith(reason, source);
		return pick(recoveryCase).decide(recoveryCase);
	}

	private BaselineReasonHandler pick(RecoveryCase recoveryCase) {
		return handlers.stream()
				.filter(handler -> handler.supports(recoveryCase))
				.findFirst()
				.orElseThrow();
	}

	private static RecoveryCase caseWith(String reason, RecoverySource source) {
		RecoveryCase recoveryCase = new RecoveryCase();
		recoveryCase.setReason(reason);
		recoveryCase.setSource(source);
		return recoveryCase;
	}
}
