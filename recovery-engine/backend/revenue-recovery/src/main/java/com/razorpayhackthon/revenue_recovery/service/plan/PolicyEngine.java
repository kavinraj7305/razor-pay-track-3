package com.razorpayhackthon.revenue_recovery.service.plan;

import com.razorpayhackthon.revenue_recovery.dto.PolicyPeek;
import com.razorpayhackthon.revenue_recovery.entity.AuditEvent;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryPolicy;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryPolicyRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Java owns money. Reads the latest AGENT_PROPOSE audit row plus amount/risk hard rules.
 * Agent cannot execute — this class only allows, skips retries, or blocks.
 */
@Service
public class PolicyEngine {

	public static final String AGENT_PROPOSE = "AGENT_PROPOSE";
	public static final String POLICY_APPROVED = "POLICY_APPROVED";
	public static final String POLICY_REJECTED = "POLICY_REJECTED";
	static final BigDecimal DEFAULT_HUMAN_APPROVAL = new BigDecimal("80000");

	private final AuditEventRepository auditEventRepository;
	private final RecoveryPolicyRepository recoveryPolicyRepository;
	private final RecoveryActionRepository recoveryActionRepository;
	private final AuditWriter auditWriter;

	public PolicyEngine(
			AuditEventRepository auditEventRepository,
			RecoveryPolicyRepository recoveryPolicyRepository,
			RecoveryActionRepository recoveryActionRepository,
			AuditWriter auditWriter) {
		this.auditEventRepository = auditEventRepository;
		this.recoveryPolicyRepository = recoveryPolicyRepository;
		this.recoveryActionRepository = recoveryActionRepository;
		this.auditWriter = auditWriter;
	}

	public PolicyDecision evaluate(RecoveryCase recoveryCase) {
		Map<String, Object> proposal = latestProposal(recoveryCase.getCaseId());
		String recommended = stringOf(proposal.get("recommendedAction"));
		boolean escalate = boolOf(proposal.get("escalate"));
		String reason = recoveryCase.getReason() == null ? "" : recoveryCase.getReason().toLowerCase();

		if (humanOverride(recoveryCase.getCaseId())) {
			return new PolicyDecision(
					PolicyDecision.Verdict.ALLOW, false, first(recommended, "DELAYED_RETRY"), "HUMAN_OVERRIDE");
		}
		if (reason.contains("risk") || reason.contains("cancelled")) {
			return new PolicyDecision(PolicyDecision.Verdict.BLOCK, true, first(recommended, "DO_NOT_RETRY"), "RISK_OR_CANCELLED");
		}
		if (escalate || "DO_NOT_RETRY".equals(recommended)) {
			return new PolicyDecision(PolicyDecision.Verdict.BLOCK, true, first(recommended, "DO_NOT_RETRY"), "AGENT_ESCALATE");
		}
		if (atOrAboveHumanApproval(recoveryCase)) {
			return new PolicyDecision(PolicyDecision.Verdict.BLOCK, true, first(recommended, "DO_NOT_RETRY"), "HUMAN_APPROVAL_AMOUNT");
		}
		if ("SKIP_EXTRA_RETRY".equals(recommended)) {
			return new PolicyDecision(PolicyDecision.Verdict.SKIP_RETRY, false, recommended, "AGENT_SKIP_EXTRA_RETRY");
		}
		return new PolicyDecision(PolicyDecision.Verdict.ALLOW, false, first(recommended, "DELAYED_RETRY"), "ALLOW_PLAYBOOK");
	}

	public PolicyPeek peek(RecoveryCase recoveryCase) {
		PolicyDecision decision = evaluate(recoveryCase);
		return new PolicyPeek(
				decision.verdict().name(),
				decision.allowExecute(),
				decision.skipRetry(),
				decision.escalate(),
				decision.recommendedAction(),
				decision.reason());
	}

	@Transactional
	public PolicyDecision apply(RecoveryCase recoveryCase) {
		PolicyDecision decision = evaluate(recoveryCase);
		if (decision.skipRetry()) {
			cancelPlannedRetries(recoveryCase, decision);
		}
		if (decision.verdict() != PolicyDecision.Verdict.ALLOW) {
			Map<String, Object> details = detailsOf(decision);
			if (decision.skipRetry()) {
				details.putAll(PlaybookClock.auditFields(recoveryCase.getReason(), 1, recoveryCase.getCreatedAt()));
			}
			auditWriter.write(
					recoveryCase,
					decision.blocked() ? "POLICY_BLOCK" : "POLICY_SKIP_RETRY",
					decision.blocked() ? "BLOCK" : "SKIP_RETRY",
					"SYSTEM",
					"policy-engine",
					details);
		}
		return decision;
	}

	private boolean humanOverride(String caseId) {
		var approved = auditEventRepository.findTopByRecoveryCase_CaseIdAndEventTypeOrderByCreatedAtDesc(
				caseId, POLICY_APPROVED);
		if (approved.isEmpty()) {
			return false;
		}
		var rejected = auditEventRepository.findTopByRecoveryCase_CaseIdAndEventTypeOrderByCreatedAtDesc(
				caseId, POLICY_REJECTED);
		if (rejected.isEmpty() || approved.get().getCreatedAt() == null) {
			return true;
		}
		if (rejected.get().getCreatedAt() == null) {
			return true;
		}
		return approved.get().getCreatedAt().isAfter(rejected.get().getCreatedAt());
	}

	private Map<String, Object> latestProposal(String caseId) {
		return auditEventRepository
				.findTopByRecoveryCase_CaseIdAndEventTypeOrderByCreatedAtDesc(caseId, AGENT_PROPOSE)
				.map(AuditEvent::getDetails)
				.orElse(Map.of());
	}

	private boolean atOrAboveHumanApproval(RecoveryCase recoveryCase) {
		BigDecimal amount = recoveryCase.getAmountAtRisk() == null ? BigDecimal.ZERO : recoveryCase.getAmountAtRisk();
		BigDecimal threshold = DEFAULT_HUMAN_APPROVAL;
		if (recoveryCase.getMerchant() != null) {
			threshold = recoveryPolicyRepository
					.findByMerchant_MerchantIdAndActiveTrue(recoveryCase.getMerchant().getMerchantId())
					.stream()
					.findFirst()
					.map(RecoveryPolicy::getHumanApprovalThreshold)
					.filter(value -> value != null && value.signum() > 0)
					.orElse(DEFAULT_HUMAN_APPROVAL);
		}
		return amount.compareTo(threshold) >= 0;
	}

	private void cancelPlannedRetries(RecoveryCase recoveryCase, PolicyDecision decision) {
		recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId()).stream()
				.filter(action -> action.getActionType() == RecoveryActionType.RETRY_PAYMENT)
				.filter(action -> action.getStatus() == RecoveryActionStatus.PLANNED)
				.forEach(action -> {
					action.setStatus(RecoveryActionStatus.CANCELLED);
					action.setExecutedAt(LocalDateTime.now());
					int step = action.getAttemptNumber() == null ? 1 : action.getAttemptNumber();
					PlaybookClock.Window window = PlaybookClock.of(recoveryCase.getReason(), step);
					action.setWaitHours(window.hours());
					action.setScheduleLabel(window.label());
					action.setReason(
							window.label() + " · Policy " + decision.reason() + ": " + decision.recommendedAction());
					recoveryActionRepository.save(action);
				});
	}

	private Map<String, Object> detailsOf(PolicyDecision decision) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("verdict", decision.verdict().name());
		details.put("reason", decision.reason());
		details.put("recommendedAction", decision.recommendedAction());
		details.put("escalate", decision.escalate());
		details.put("allowExecute", decision.allowExecute());
		details.put("skipRetry", decision.skipRetry());
		return details;
	}

	private static boolean boolOf(Object value) {
		if (value instanceof Boolean bool) {
			return bool;
		}
		return Boolean.parseBoolean(String.valueOf(value == null ? "" : value));
	}

	private static String stringOf(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static String first(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
