package com.razorpayhackthon.revenue_recovery.service.ml;

import com.razorpayhackthon.revenue_recovery.config.MlProperties;
import com.razorpayhackthon.revenue_recovery.dto.ScorePeek;
import com.razorpayhackthon.revenue_recovery.entity.AuditEvent;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryOutcomeRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Less labelled data → playbook only. Enough data → playbook + P(recovery).
 */
@Service
public class MlDataGate {

	private final MlProperties properties;
	private final RecoveryOutcomeRepository recoveryOutcomeRepository;
	private final CustomerFeatureService customerFeatureService;
	private final MlPredictClient mlPredictClient;
	private final AuditEventRepository auditEventRepository;
	private final RecoveryActionRepository recoveryActionRepository;

	public MlDataGate(
			MlProperties properties,
			RecoveryOutcomeRepository recoveryOutcomeRepository,
			CustomerFeatureService customerFeatureService,
			MlPredictClient mlPredictClient,
			AuditEventRepository auditEventRepository,
			RecoveryActionRepository recoveryActionRepository) {
		this.properties = properties;
		this.recoveryOutcomeRepository = recoveryOutcomeRepository;
		this.customerFeatureService = customerFeatureService;
		this.mlPredictClient = mlPredictClient;
		this.auditEventRepository = auditEventRepository;
		this.recoveryActionRepository = recoveryActionRepository;
	}

	public record Decision(boolean useProbability, long labelledOutcomes, Double probability, boolean skipRetry) {}

	/** Read-only P(recovery) for the desk UI. Does not write audit or cancel retries. */
	public ScorePeek peek(RecoveryCase recoveryCase) {
		long labelled = recoveryOutcomeRepository.count();
		long min = properties.getMinLabelledOutcomes();
		if (labelled < min) {
			return new ScorePeek("LOW_DATA", labelled, min, null, false, null);
		}
		PredictPayload features = customerFeatureService.snapshot(recoveryCase);
		Optional<PredictApiResponse> scored = mlPredictClient.predict(features);
		if (scored.isEmpty()) {
			return new ScorePeek("UNAVAILABLE", labelled, min, null, false, null);
		}
		double probability = scored.get().recoveryProbability();
		return new ScorePeek(
				"SCORED",
				labelled,
				min,
				probability,
				shouldSkipRetry(features, probability),
				scored.get().label());
	}

	@Transactional
	public Decision beforeExecute(RecoveryCase recoveryCase) {
		long labelled = recoveryOutcomeRepository.count();
		long min = properties.getMinLabelledOutcomes();
		if (labelled < min) {
			audit(
					recoveryCase,
					"ML_SKIPPED_LOW_DATA",
					"PLAYBOOK_ONLY",
					Map.of("labelledOutcomes", labelled, "minLabelledOutcomes", min));
			return new Decision(false, labelled, null, false);
		}
		PredictPayload features = customerFeatureService.snapshot(recoveryCase);
		Optional<PredictApiResponse> scored = mlPredictClient.predict(features);
		if (scored.isEmpty()) {
			audit(
					recoveryCase,
					"ML_PREDICT_UNAVAILABLE",
					"PLAYBOOK_ONLY",
					Map.of("labelledOutcomes", labelled, "minLabelledOutcomes", min));
			return new Decision(false, labelled, null, false);
		}
		double probability = scored.get().recoveryProbability();
		boolean skipRetry = shouldSkipRetry(features, probability);
		audit(
				recoveryCase,
				skipRetry ? "ML_SKIP_RETRY" : "ML_SCORED",
				skipRetry ? "SKIP_RETRY" : "CONSIDER",
				Map.of(
						"labelledOutcomes", labelled,
						"minLabelledOutcomes", min,
						"recoveryProbability", probability,
						"label", scored.get().label(),
						"historyPaymentCount", features.historyPaymentCount(),
						"skipRetry", skipRetry));
		if (skipRetry) {
			cancelPlannedRetry(recoveryCase, probability);
		}
		return new Decision(true, labelled, probability, skipRetry);
	}

	private boolean shouldSkipRetry(PredictPayload features, double probability) {
		return probability < properties.getConsiderMinProbability()
				&& features.historyPaymentCount() >= properties.getMinHistoryPaymentsToOverride();
	}

	private void cancelPlannedRetry(RecoveryCase recoveryCase, double probability) {
		recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId()).stream()
				.filter(action -> action.getActionType() == RecoveryActionType.RETRY_PAYMENT)
				.filter(action -> action.getStatus() == RecoveryActionStatus.PLANNED)
				.forEach(action -> {
					action.setStatus(RecoveryActionStatus.CANCELLED);
					action.setExecutedAt(LocalDateTime.now());
					action.setReason("ML skip retry: P=" + probability);
					recoveryActionRepository.save(action);
				});
	}

	private void audit(RecoveryCase recoveryCase, String eventType, String action, Map<String, Object> details) {
		AuditEvent auditEvent = new AuditEvent();
		auditEvent.setEventId("aud_" + UUID.randomUUID().toString().replace("-", ""));
		auditEvent.setRecoveryCase(recoveryCase);
		auditEvent.setEventType(eventType);
		auditEvent.setActorType("SYSTEM");
		auditEvent.setActorId("ml-data-gate");
		auditEvent.setAction(action);
		auditEvent.setDetails(new LinkedHashMap<>(details));
		auditEventRepository.save(auditEvent);
	}
}
