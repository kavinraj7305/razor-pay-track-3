package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.CheckoutSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {

	Optional<CheckoutSession> findByCheckoutSessionId(String checkoutSessionId);

	List<CheckoutSession> findByMerchant_MerchantId(String merchantId);
}
