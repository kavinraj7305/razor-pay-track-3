package com.razorpayhackthon.revenue_recovery.entity;

import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
		name = "recovery_policy",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_active_policy", columnNames = {"merchant_id", "policy_version"})
		},
		indexes = {
				@Index(name = "idx_policy_merchant", columnList = "merchant_id")
		}
)
public class RecoveryPolicy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "policy_id", nullable = false, unique = true, length = 50)
	private String policyId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "merchant_id", referencedColumnName = "merchant_id", nullable = false)
	private Merchant merchant;

	@Column(name = "max_payment_retries", nullable = false)
	private Integer maxPaymentRetries = 3;

	@Column(name = "max_discount_percentage", nullable = false, precision = 5, scale = 2)
	private BigDecimal maxDiscountPercentage = BigDecimal.ZERO;

	@Column(name = "human_approval_threshold", precision = 19, scale = 2)
	private BigDecimal humanApprovalThreshold;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "allowed_actions", nullable = false, columnDefinition = "jsonb")
	private List<RecoveryActionType> allowedActions = new ArrayList<>();

	@Column(name = "policy_version", nullable = false)
	private Integer policyVersion = 1;

	@Column(nullable = false)
	private Boolean active = Boolean.TRUE;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

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
