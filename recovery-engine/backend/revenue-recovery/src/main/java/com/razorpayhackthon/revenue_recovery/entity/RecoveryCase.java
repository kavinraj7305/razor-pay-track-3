package com.razorpayhackthon.revenue_recovery.entity;

import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryPriority;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
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
import jakarta.persistence.PreUpdate;
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
		name = "recovery_case",
		indexes = {
				@Index(name = "idx_recovery_merchant", columnList = "merchant_id"),
				@Index(name = "idx_recovery_customer", columnList = "customer_id"),
				@Index(name = "idx_recovery_status", columnList = "status"),
				@Index(name = "idx_recovery_source", columnList = "source, source_id"),
				@Index(name = "idx_recovery_priority", columnList = "priority")
		}
)
public class RecoveryCase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "case_id", nullable = false, unique = true, length = 50)
	private String caseId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "merchant_id", referencedColumnName = "merchant_id", nullable = false)
	private Merchant merchant;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", referencedColumnName = "customer_id")
	private Customer customer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private RecoverySource source;

	@Column(name = "source_id", nullable = false, length = 50)
	private String sourceId;

	@Column(name = "amount_at_risk", nullable = false, precision = 19, scale = 2)
	private BigDecimal amountAtRisk;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3)
	private String currency;

	@Column(nullable = false, length = 100)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RecoveryCaseStatus status = RecoveryCaseStatus.OPEN;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RecoveryPriority priority = RecoveryPriority.MEDIUM;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "closed_at")
	private LocalDateTime closedAt;

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
