package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryPolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryPolicyRepository extends JpaRepository<RecoveryPolicy, Long> {

	Optional<RecoveryPolicy> findByPolicyId(String policyId);

	List<RecoveryPolicy> findByMerchant_MerchantId(String merchantId);

	List<RecoveryPolicy> findByMerchant_MerchantIdAndActiveTrue(String merchantId);
}
