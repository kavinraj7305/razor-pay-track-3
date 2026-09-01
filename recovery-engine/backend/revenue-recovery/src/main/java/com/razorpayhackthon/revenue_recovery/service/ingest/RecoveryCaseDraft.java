package com.razorpayhackthon.revenue_recovery.service.ingest;

import com.razorpayhackthon.revenue_recovery.enums.RecoveryPriority;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import java.math.BigDecimal;

public record RecoveryCaseDraft(
		RecoverySource source,
		String sourceId,
		BigDecimal amountAtRisk,
		String currency,
		String reason,
		RecoveryPriority priority) {}
