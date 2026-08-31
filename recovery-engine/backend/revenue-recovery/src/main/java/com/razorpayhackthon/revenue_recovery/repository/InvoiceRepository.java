package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.Invoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

	Optional<Invoice> findByInvoiceId(String invoiceId);

	List<Invoice> findByCustomer_CustomerId(String customerId);
}
