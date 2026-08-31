package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.Merchant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

	Optional<Merchant> findByMerchantId(String merchantId);
}
