package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, Long> {

	Optional<RecoveryAction> findByActionId(String actionId);

	List<RecoveryAction> findByRecoveryCase_CaseId(String caseId);
}
