package com.razorpayhackthon.revenue_recovery.entity;

import com.razorpayhackthon.revenue_recovery.enums.RecoveryOutcomeResult;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
		name = "recovery_outcome",
		indexes = {
				@Index(name = "idx_outcome_case", columnList = "case_id")
		}
)
public class RecoveryOutcome {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "outcome_id", nullable = false, unique = true, length = 50)
	private String outcomeId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "case_id", referencedColumnName = "case_id", nullable = false)
	private RecoveryCase recoveryCase;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private RecoveryOutcomeResult result;

	@Column(name = "amount_recovered", nullable = false, precision = 19, scale = 2)
	private BigDecimal amountRecovered = BigDecimal.ZERO;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "resolution_reason", length = 500)
	private String resolutionReason;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
