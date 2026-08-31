package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.PromiseToPay;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromiseToPayRepository extends JpaRepository<PromiseToPay, Long> {

	Optional<PromiseToPay> findByPromiseId(String promiseId);

	List<PromiseToPay> findByRecoveryCase_CaseId(String caseId);

	List<PromiseToPay> findByCustomer_CustomerId(String customerId);
}
