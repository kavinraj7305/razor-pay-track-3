package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.AuditEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

	Optional<AuditEvent> findByEventId(String eventId);

	List<AuditEvent> findByRecoveryCase_CaseIdOrderByCreatedAtAsc(String caseId);
}
