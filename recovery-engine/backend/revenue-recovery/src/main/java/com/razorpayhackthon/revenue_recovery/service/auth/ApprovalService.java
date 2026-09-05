package com.razorpayhackthon.revenue_recovery.service.auth;

import com.razorpayhackthon.revenue_recovery.dto.RecoveryCasePlanResponse;
import com.razorpayhackthon.revenue_recovery.dto.auth.ApprovalItem;
import com.razorpayhackthon.revenue_recovery.dto.auth.ApprovalNoteRequest;
import com.razorpayhackthon.revenue_recovery.entity.DeskUser;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.service.plan.AuditWriter;
import com.razorpayhackthon.revenue_recovery.service.plan.PolicyDecision;
import com.razorpayhackthon.revenue_recovery.service.plan.PolicyEngine;
import com.razorpayhackthon.revenue_recovery.service.plan.RecoveryActionPlanService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApprovalService {

	private static final String TRAINING = "acc_syn_training";

	private final RecoveryCaseRepository recoveryCaseRepository;
	private final AuditEventRepository auditEventRepository;
	private final PolicyEngine policyEngine;
	private final AuditWriter auditWriter;
	private final RecoveryActionPlanService recoveryActionPlanService;

	public ApprovalService(
			RecoveryCaseRepository recoveryCaseRepository,
			AuditEventRepository auditEventRepository,
			PolicyEngine policyEngine,
			AuditWriter auditWriter,
			RecoveryActionPlanService recoveryActionPlanService) {
		this.recoveryCaseRepository = recoveryCaseRepository;
		this.auditEventRepository = auditEventRepository;
		this.policyEngine = policyEngine;
		this.auditWriter = auditWriter;
		this.recoveryActionPlanService = recoveryActionPlanService;
	}

	@Transactional(readOnly = true)
	public List<ApprovalItem> pending() {
		List<ApprovalItem> rows = new ArrayList<>();
		for (RecoveryCase recoveryCase : recoveryCaseRepository.findByMerchant_MerchantIdNotOrderByCreatedAtDesc(TRAINING)) {
			if (closed(recoveryCase)) {
				continue;
			}
			PolicyDecision decision = policyEngine.evaluate(recoveryCase);
			if (!decision.blocked()) {
				continue;
			}
			rows.add(toItem(recoveryCase, decision));
		}
		return rows;
	}

	@Transactional
	public RecoveryCasePlanResponse approve(String caseId, ApprovalNoteRequest request, DeskUser actor) {
		return decide(caseId, true, request, actor);
	}

	@Transactional
	public RecoveryCasePlanResponse reject(String caseId, ApprovalNoteRequest request, DeskUser actor) {
		return decide(caseId, false, request, actor);
	}

	private RecoveryCasePlanResponse decide(String caseId, boolean approve, ApprovalNoteRequest request, DeskUser actor) {
		RecoveryCase recoveryCase = recoveryCaseRepository
				.findByCaseId(caseId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "recovery case not found"));
		if (closed(recoveryCase)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "case is closed");
		}
		PolicyDecision before = policyEngine.evaluate(recoveryCase);
		if (approve && !before.blocked() && !"HUMAN_OVERRIDE".equals(before.reason())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "case is not waiting on policy");
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("note", request == null || request.note() == null ? "" : request.note());
		details.put("previousReason", before.reason());
		details.put("recommendedAction", before.recommendedAction());
		details.put("verdict", approve ? "ALLOW" : "BLOCK");
		auditWriter.write(
				recoveryCase,
				approve ? PolicyEngine.POLICY_APPROVED : PolicyEngine.POLICY_REJECTED,
				approve ? "APPROVE" : "REJECT",
				"HUMAN",
				actor.getUserId(),
				details);
		return recoveryActionPlanService.getPlan(caseId);
	}

	public long pendingCount() {
		return pending().size();
	}

	private ApprovalItem toItem(RecoveryCase recoveryCase, PolicyDecision decision) {
		Map<String, Object> proposal = auditEventRepository
				.findTopByRecoveryCase_CaseIdAndEventTypeOrderByCreatedAtDesc(
						recoveryCase.getCaseId(), PolicyEngine.AGENT_PROPOSE)
				.map(event -> event.getDetails() == null ? Map.<String, Object>of() : event.getDetails())
				.orElse(Map.of());
		return new ApprovalItem(
				recoveryCase.getCaseId(),
				recoveryCase.getReason(),
				recoveryCase.getStatus().name(),
				recoveryCase.getAmountAtRisk(),
				decision.reason(),
				decision.recommendedAction(),
				String.valueOf(proposal.getOrDefault("diagnosis", "")),
				String.valueOf(proposal.getOrDefault("reasoning", proposal.getOrDefault("reason", ""))),
				Boolean.TRUE.equals(proposal.get("escalate")) || decision.escalate());
	}

	private static boolean closed(RecoveryCase recoveryCase) {
		RecoveryCaseStatus status = recoveryCase.getStatus();
		return status == RecoveryCaseStatus.RECOVERED
				|| status == RecoveryCaseStatus.FAILED
				|| status == RecoveryCaseStatus.EXPIRED;
	}
}
