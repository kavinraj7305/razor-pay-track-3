package com.razorpayhackthon.revenue_recovery.service.auth;

import com.razorpayhackthon.revenue_recovery.dto.auth.DashboardSnapshot;
import com.razorpayhackthon.revenue_recovery.dto.auth.ReasonCount;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.repository.DeskUserRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

	private static final String TRAINING = "acc_syn_training";

	private final RecoveryCaseRepository recoveryCaseRepository;
	private final DeskUserRepository deskUserRepository;
	private final ApprovalService approvalService;
	private final AuthService authService;

	public DashboardService(
			RecoveryCaseRepository recoveryCaseRepository,
			DeskUserRepository deskUserRepository,
			ApprovalService approvalService,
			AuthService authService) {
		this.recoveryCaseRepository = recoveryCaseRepository;
		this.deskUserRepository = deskUserRepository;
		this.approvalService = approvalService;
		this.authService = authService;
	}

	@Transactional(readOnly = true)
	public DashboardSnapshot snapshot() {
		List<RecoveryCase> cases = recoveryCaseRepository.findByMerchant_MerchantIdNotOrderByCreatedAtDesc(TRAINING);
		long open = 0;
		long recovered = 0;
		long failed = 0;
		BigDecimal atRisk = BigDecimal.ZERO;
		Map<String, Long> reasons = new LinkedHashMap<>();
		for (RecoveryCase recoveryCase : cases) {
			RecoveryCaseStatus status = recoveryCase.getStatus();
			if (status == RecoveryCaseStatus.RECOVERED) {
				recovered++;
			} else if (status == RecoveryCaseStatus.FAILED || status == RecoveryCaseStatus.EXPIRED) {
				failed++;
			} else {
				open++;
				if (recoveryCase.getAmountAtRisk() != null) {
					atRisk = atRisk.add(recoveryCase.getAmountAtRisk());
				}
			}
			String reason = recoveryCase.getReason() == null ? "unknown" : recoveryCase.getReason();
			reasons.merge(reason, 1L, Long::sum);
		}
		List<ReasonCount> byReason = new ArrayList<>();
		reasons.forEach((reason, count) -> byReason.add(new ReasonCount(reason, count)));
		return new DashboardSnapshot(
				cases.size(),
				open,
				recovered,
				failed,
				approvalService.pendingCount(),
				atRisk,
				deskUserRepository.countByRoleAndActiveTrue(DeskRole.ADMIN),
				deskUserRepository.countByRoleAndActiveTrue(DeskRole.APPROVER),
				deskUserRepository.countByRoleAndActiveTrue(DeskRole.OPERATOR),
				byReason,
				authService.listUsers());
	}
}
