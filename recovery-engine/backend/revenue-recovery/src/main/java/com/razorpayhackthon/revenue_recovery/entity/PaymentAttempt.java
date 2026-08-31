package com.razorpayhackthon.revenue_recovery.entity;

import com.razorpayhackthon.revenue_recovery.enums.PaymentAttemptStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "payment_attempt",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_payment_attempt_number", columnNames = {"payment_id", "attempt_number"})
		},
		indexes = {
				@Index(name = "idx_attempt_payment", columnList = "payment_id")
		}
)
public class PaymentAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "attempt_id", nullable = false, unique = true, length = 50)
	private String attemptId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_id", referencedColumnName = "payment_id", nullable = false)
	private Payment payment;

	@Column(name = "attempt_number", nullable = false)
	private Integer attemptNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentAttemptStatus status;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", length = 500)
	private String failureMessage;

	@Column(name = "attempted_at", nullable = false)
	private LocalDateTime attemptedAt;

	@PrePersist
	void onCreate() {
		if (attemptedAt == null) {
			attemptedAt = LocalDateTime.now();
		}
	}
}
