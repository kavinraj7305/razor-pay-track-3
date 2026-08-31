package com.razorpayhackthon.revenue_recovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "audit_event",
		indexes = {
				@Index(name = "idx_audit_case", columnList = "case_id"),
				@Index(name = "idx_audit_event_type", columnList = "event_type"),
				@Index(name = "idx_audit_created_at", columnList = "created_at")
		}
)
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true, length = 50)
	private String eventId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id", referencedColumnName = "case_id")
	private RecoveryCase recoveryCase;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "actor_type", nullable = false, length = 50)
	private String actorType;

	@Column(name = "actor_id", length = 100)
	private String actorId;

	@Column(length = 100)
	private String action;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> details;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
