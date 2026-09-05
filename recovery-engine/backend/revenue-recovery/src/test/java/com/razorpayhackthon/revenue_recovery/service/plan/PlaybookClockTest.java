package com.razorpayhackthon.revenue_recovery.service.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaybookClockTest {

	@Test
	void insufficientFundsUsesPaydayThenLongerThenFiveDays() {
		assertThat(PlaybookClock.of("insufficient_funds", 1).label()).isEqualTo("T+48h");
		assertThat(PlaybookClock.of("insufficient_funds", 1).hours()).isEqualTo(48);
		assertThat(PlaybookClock.of("insufficient_funds", 2).label()).isEqualTo("T+96h");
		assertThat(PlaybookClock.of("insufficient_funds", 3).label()).isEqualTo("T+5d");
	}

	@Test
	void auditStoresWhenTheRetryWouldRun() {
		LocalDateTime failed = LocalDateTime.of(2026, 9, 1, 10, 0);
		Map<String, Object> fields = PlaybookClock.auditFields("insufficient_funds", 1, failed);
		assertThat(fields.get("scheduleLabel")).isEqualTo("T+48h");
		assertThat(fields.get("waitHours")).isEqualTo(48);
		assertThat(fields.get("runAfter")).isEqualTo(failed.plusHours(48).toString());
	}
}
