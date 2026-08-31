package com.razorpayhackthon.revenue_recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class Phase1SchemaTest {

	private static final List<String> PHASE1_TABLES = List.of(
			"merchant",
			"customer",
			"payment",
			"payment_attempt",
			"subscription",
			"invoice",
			"checkout_session",
			"recovery_case",
			"recovery_action",
			"recovery_outcome",
			"recovery_policy",
			"promise_to_pay",
			"audit_event"
	);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void allThirteenPhase1TablesExist() {
		List<String> tables = jdbcTemplate.queryForList(
				"""
						SELECT table_name
						FROM information_schema.tables
						WHERE table_schema = 'public'
						  AND table_type = 'BASE TABLE'
						  AND table_name <> 'flyway_schema_history'
						ORDER BY table_name
						""",
				String.class
		);

		assertThat(tables).containsAll(PHASE1_TABLES);
		assertThat(tables).contains("webhook_event");
	}
}
