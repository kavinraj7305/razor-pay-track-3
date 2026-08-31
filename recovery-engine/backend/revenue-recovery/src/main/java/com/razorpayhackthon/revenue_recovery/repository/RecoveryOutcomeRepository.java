package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryOutcome;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryOutcomeRepository extends JpaRepository<RecoveryOutcome, Long> {

	Optional<RecoveryOutcome> findByOutcomeId(String outcomeId);

	List<RecoveryOutcome> findByRecoveryCase_CaseId(String caseId);
}
