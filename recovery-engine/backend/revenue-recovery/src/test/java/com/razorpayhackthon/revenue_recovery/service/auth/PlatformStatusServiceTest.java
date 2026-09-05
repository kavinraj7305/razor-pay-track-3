package com.razorpayhackthon.revenue_recovery.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.razorpayhackthon.revenue_recovery.dto.auth.PlatformComponent;
import com.razorpayhackthon.revenue_recovery.dto.auth.PlatformStatus;
import org.junit.jupiter.api.Test;

class PlatformStatusServiceTest {

	@Test
	void readyOnlyWhenRedisKafkaAndLedgerAreUp() {
		PlatformComponent redis = new PlatformComponent("redis", "Redis", true, "Duplicate-event lock", "ok");
		PlatformComponent kafka = new PlatformComponent("kafka", "Kafka", true, "Payment event bus", "ok");
		PlatformComponent ledger = new PlatformComponent("postgres", "Postgres", true, "Case ledger", "ok");
		PlatformComponent kafkaDown = new PlatformComponent("kafka", "Kafka", false, "Payment event bus", "Unreachable");

		PlatformStatus ready = PlatformStatusService.assemble(redis, kafka, ledger);
		assertThat(ready.ready()).isTrue();
		assertThat(ready.components()).extracting(PlatformComponent::id).containsExactly("redis", "kafka", "postgres");

		assertThat(PlatformStatusService.assemble(redis, kafkaDown, ledger).ready()).isFalse();
	}
}
