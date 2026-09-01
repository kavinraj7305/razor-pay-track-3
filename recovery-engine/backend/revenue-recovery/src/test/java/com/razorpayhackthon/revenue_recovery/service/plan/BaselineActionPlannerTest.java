package com.razorpayhackthon.revenue_recovery.service.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import org.junit.jupiter.api.Test;

class BaselineActionPlannerTest {

	@Test
	void mapsEightScenarios() {
		assertThat(decide("insufficient_funds", RecoverySource.PAYMENT).actionType())
				.isEqualTo(RecoveryActionType.RETRY_PAYMENT);
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

	private static BaselineActionPlanner.Planned decide(String reason, RecoverySource source) {
		RecoveryCase recoveryCase = new RecoveryCase();
		recoveryCase.setReason(reason);
		recoveryCase.setSource(source);
		return BaselineActionPlanner.decide(recoveryCase);
	}
}
