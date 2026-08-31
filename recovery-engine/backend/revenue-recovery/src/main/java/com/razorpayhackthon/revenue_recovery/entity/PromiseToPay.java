package com.razorpayhackthon.revenue_recovery.entity;

import com.razorpayhackthon.revenue_recovery.enums.PromiseToPayStatus;
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
		name = "promise_to_pay",
		indexes = {
				@Index(name = "idx_ptp_case", columnList = "case_id"),
				@Index(name = "idx_ptp_customer", columnList = "customer_id"),
				@Index(name = "idx_ptp_status", columnList = "status")
		}
)
public class PromiseToPay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "promise_id", nullable = false, unique = true, length = 50)
	private String promiseId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "case_id", referencedColumnName = "case_id", nullable = false)
	private RecoveryCase recoveryCase;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", referencedColumnName = "customer_id", nullable = false)
	private Customer customer;

	@Column(name = "promised_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal promisedAmount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "promised_date", nullable = false)
	private LocalDateTime promisedDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PromiseToPayStatus status = PromiseToPayStatus.PENDING;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "fulfilled_at")
	private LocalDateTime fulfilledAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
