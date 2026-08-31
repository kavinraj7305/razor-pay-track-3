package com.razorpayhackthon.revenue_recovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
		name = "webhook_event",
		indexes = {
				@Index(name = "idx_webhook_event_type", columnList = "event_type"),
				@Index(name = "idx_webhook_received_at", columnList = "received_at"),
				@Index(name = "idx_webhook_processed", columnList = "processed")
		}
)
public class WebhookEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true, length = 50)
	private String eventId;

	@Column(nullable = false, length = 30)
	private String provider = "RAZORPAY";

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@Column(name = "received_at", nullable = false)
	private LocalDateTime receivedAt;

	@Column(nullable = false)
	private boolean processed = false;

	@PrePersist
	void onCreate() {
		if (receivedAt == null) {
			receivedAt = LocalDateTime.now();
		}
	}
}
