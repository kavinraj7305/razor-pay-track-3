package com.razorpayhackthon.revenue_recovery.entity;

import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "recovery_action",
		indexes = {
				@Index(name = "idx_action_case", columnList = "case_id"),
				@Index(name = "idx_action_status", columnList = "status")
		}
)
public class RecoveryAction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "action_id", nullable = false, unique = true, length = 50)
	private String actionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "case_id", referencedColumnName = "case_id", nullable = false)
	private RecoveryCase recoveryCase;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_type", nullable = false, length = 50)
	private RecoveryActionType actionType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RecoveryActionStatus status;

	@Column(name = "attempt_number")
	private Integer attemptNumber;

	@Column(length = 500)
	private String reason;

	@Column(name = "executed_at")
	private LocalDateTime executedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
