package com.razorpayhackthon.revenue_recovery.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.razorpayhackthon.revenue_recovery.entity.AuditEvent;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryPolicyRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

	private AuditEventRepository auditEventRepository;
	private PolicyEngine policyEngine;

	@BeforeEach
	void setUp() {
		auditEventRepository = mock(AuditEventRepository.class);
		when(auditEventRepository.findTopByRecoveryCase_CaseIdAndEventTypeOrderByCreatedAtDesc(any(), any()))
				.thenReturn(Optional.empty());
		policyEngine = new PolicyEngine(
				auditEventRepository,
				mock(RecoveryPolicyRepository.class),
				mock(RecoveryActionRepository.class),
				mock(AuditWriter.class));
	}

	@Test
	void riskReasonBlocksEvenWithoutAgent() {
		when(auditEventRepository.findTopByRecoveryCase_CaseIdAndEventTypeOrderByCreatedAtDesc(
						"rc_risk", PolicyEngine.AGENT_PROPOSE))
				.thenReturn(Optional.empty());
		PolicyDecision decision = policyEngine.evaluate(caseWith("rc_risk", "payment_risk_check_failed", "500"));
		assertThat(decision.blocked()).isTrue();
		assertThat(decision.reason()).isEqualTo("RISK_OR_CANCELLED");
	}

	@Test
	void agentEscalateBlocks() {
		stubProposal("rc_nsf", Map.of("escalate", true, "recommendedAction", "DO_NOT_RETRY"));
		PolicyDecision decision = policyEngine.evaluate(caseWith("rc_nsf", "insufficient_funds", "499"));
		assertThat(decision.blocked()).isTrue();
		assertThat(decision.reason()).isEqualTo("AGENT_ESCALATE");
	}

	@Test
	void skipExtraRetryDoesNotBlockExecute() {
		stubProposal("rc_nsf", Map.of("escalate", false, "recommendedAction", "SKIP_EXTRA_RETRY"));
		PolicyDecision decision = policyEngine.evaluate(caseWith("rc_nsf", "insufficient_funds", "499"));
		assertThat(decision.allowExecute()).isTrue();
		assertThat(decision.skipRetry()).isTrue();
		assertThat(decision.reason()).isEqualTo("AGENT_SKIP_EXTRA_RETRY");
	}

	@Test
	void humanApprovalOverridesRiskBlock() {
		AuditEvent approved = new AuditEvent();
		approved.setCreatedAt(LocalDateTime.now());
		when(auditEventRepository.findTopByRecoveryCase_CaseIdAndEventTypeOrderByCreatedAtDesc(
						eq("rc_risk"), eq(PolicyEngine.POLICY_APPROVED)))
				.thenReturn(Optional.of(approved));
		PolicyDecision decision = policyEngine.evaluate(caseWith("rc_risk", "payment_risk_check_failed", "80000"));
		assertThat(decision.blocked()).isFalse();
		assertThat(decision.reason()).isEqualTo("HUMAN_OVERRIDE");
	}

	@Test
	void largeAmountBlocksForHuman() {
		when(auditEventRepository.findTopByRecoveryCase_CaseIdAndEventTypeOrderByCreatedAtDesc(
						"rc_big", PolicyEngine.AGENT_PROPOSE))
				.thenReturn(Optional.empty());
		PolicyDecision decision = policyEngine.evaluate(caseWith("rc_big", "insufficient_funds", "90000"));
		assertThat(decision.blocked()).isTrue();
		assertThat(decision.reason()).isEqualTo("HUMAN_APPROVAL_AMOUNT");
	}

	private void stubProposal(String caseId, Map<String, Object> details) {
		AuditEvent event = new AuditEvent();
		event.setDetails(new LinkedHashMap<>(details));
		when(auditEventRepository.findTopByRecoveryCase_CaseIdAndEventTypeOrderByCreatedAtDesc(
						caseId, PolicyEngine.AGENT_PROPOSE))
				.thenReturn(Optional.of(event));
	}

	private RecoveryCase caseWith(String caseId, String reason, String amount) {
		RecoveryCase recoveryCase = new RecoveryCase();
		recoveryCase.setCaseId(caseId);
		recoveryCase.setReason(reason);
		recoveryCase.setAmountAtRisk(new BigDecimal(amount));
		return recoveryCase;
	}
}
