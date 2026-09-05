package com.razorpayhackthon.revenue_recovery.service.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razorpayhackthon.revenue_recovery.config.MlProperties;
import com.razorpayhackthon.revenue_recovery.dto.ScorePeek;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryOutcomeRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MlDataGateTest {

	private RecoveryOutcomeRepository outcomes;
	private CustomerFeatureService features;
	private MlPredictClient predictClient;
	private MlDataGate gate;

	@BeforeEach
	void setUp() {
		outcomes = mock(RecoveryOutcomeRepository.class);
		features = mock(CustomerFeatureService.class);
		predictClient = mock(MlPredictClient.class);
		MlProperties properties = new MlProperties();
		properties.setMinLabelledOutcomes(400);
		properties.setMinHistoryPaymentsToScore(10);
		properties.setMinHistoryPaymentsToOverride(10);
		gate = new MlDataGate(
				properties,
				outcomes,
				features,
				predictClient,
				mock(AuditEventRepository.class),
				mock(RecoveryActionRepository.class));
	}

	@Test
	void thinCustomerHistoryStaysPlaybookOnly() {
		when(outcomes.count()).thenReturn(500L);
		when(features.snapshot(any())).thenReturn(payload(3));

		ScorePeek peek = gate.peek(new RecoveryCase());
		MlDataGate.Decision decision = gate.beforeExecute(new RecoveryCase());

		assertThat(peek.status()).isEqualTo("LOW_DATA");
		assertThat(peek.recoveryProbability()).isNull();
		assertThat(decision.useProbability()).isFalse();
		assertThat(decision.skipRetry()).isFalse();
		verify(predictClient, never()).predict(any());
	}

	@Test
	void tenRecordsLetsTheScoreRun() {
		when(outcomes.count()).thenReturn(500L);
		PredictPayload payload = payload(10);
		when(features.snapshot(any())).thenReturn(payload);
		when(predictClient.predict(payload)).thenReturn(Optional.of(new PredictApiResponse(0.72, "LIKELY")));

		ScorePeek peek = gate.peek(new RecoveryCase());

		assertThat(peek.status()).isEqualTo("SCORED");
		assertThat(peek.recoveryProbability()).isEqualTo(0.72);
	}

	private static PredictPayload payload(int history) {
		return new PredictPayload(
				"insufficient_funds",
				"PAYMENT",
				"NORMAL",
				"card",
				BigDecimal.TEN,
				1,
				2,
				0,
				0,
				0,
				0,
				0,
				0,
				0,
				0,
				0,
				history);
	}
}
