package com.razorpayhackthon.revenue_recovery.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BenchmarkServiceTest {

	@Test
	void loadsTheRan500EventScoreboard() {
		BenchmarkService service = new BenchmarkService(new JsonMapper());
		Map<String, Object> report = service.latest();
		assertThat(report.get("events")).isEqualTo(500);
		assertThat(report.get("merchantId")).isEqualTo("acc_syn_training");
		assertThat(report.get("pitch")).asString().contains("gave up 9 people who later paid");
		assertThat(report.get("baseline")).isInstanceOf(Map.class);
		assertThat(report.get("ai")).isInstanceOf(Map.class);
	}
}
