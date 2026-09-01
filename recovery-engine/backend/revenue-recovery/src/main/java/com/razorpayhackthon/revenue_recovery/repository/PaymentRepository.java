package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.Payment;
import com.razorpayhackthon.revenue_recovery.enums.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByPaymentId(String paymentId);

	List<Payment> findByMerchant_MerchantId(String merchantId);

	List<Payment> findByCustomer_CustomerId(String customerId);

	long countByCustomer_CustomerIdAndStatus(String customerId, PaymentStatus status);
}
