package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, Long> {

	Optional<RecoveryCase> findByCaseId(String caseId);

	List<RecoveryCase> findByMerchant_MerchantId(String merchantId);

	List<RecoveryCase> findBySourceAndSourceId(RecoverySource source, String sourceId);
}
