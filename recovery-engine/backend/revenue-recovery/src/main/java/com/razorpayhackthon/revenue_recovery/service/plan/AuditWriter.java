package com.razorpayhackthon.revenue_recovery.service.plan;

import com.razorpayhackthon.revenue_recovery.entity.AuditEvent;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.repository.AuditEventRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** One place writes audit_event so propose / policy / ML share the same row shape. */
@Service
public class AuditWriter {

	private final AuditEventRepository auditEventRepository;

	public AuditWriter(AuditEventRepository auditEventRepository) {
		this.auditEventRepository = auditEventRepository;
	}

	public AuditEvent write(
			RecoveryCase recoveryCase,
			String eventType,
			String action,
			String actorType,
			String actorId,
			Map<String, Object> details) {
		AuditEvent event = new AuditEvent();
		event.setEventId("aud_" + UUID.randomUUID().toString().replace("-", ""));
		event.setRecoveryCase(recoveryCase);
		event.setEventType(eventType);
		event.setAction(action);
		event.setActorType(actorType);
		event.setActorId(actorId);
		event.setDetails(new LinkedHashMap<>(details == null ? Map.of() : details));
		return auditEventRepository.save(event);
	}
}
