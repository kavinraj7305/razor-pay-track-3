package com.razorpayhackthon.revenue_recovery.service.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.razorpayhackthon.revenue_recovery.entity.WebhookEvent;
import com.razorpayhackthon.revenue_recovery.enums.WebhookIntake;
import org.junit.jupiter.api.Test;

class WebhookInboxServiceTest {

	@Test
	void originIsRazorpayOnlyWhenHmacAndAccountIsNotALocalDemo() {
		WebhookEvent signed = new WebhookEvent();
		signed.setIntake(WebhookIntake.HMAC_SIGNED.name());
		signed.setSignatureVerified(true);

		assertThat(WebhookInboxService.originOf(signed, "acc_N6mGvM1tlY39Q7")).isEqualTo("RAZORPAY");
		assertThat(WebhookInboxService.originOf(signed, "acc_test_recovery")).isEqualTo("LOCAL_SCRIPT");

		WebhookEvent desk = new WebhookEvent();
		desk.setIntake(WebhookIntake.DESK_SIMULATE.name());
		desk.setSignatureVerified(false);
		assertThat(WebhookInboxService.originOf(desk, "acc_N6mGvM1tlY39Q7")).isEqualTo("DESK_SIMULATE");
	}
}
