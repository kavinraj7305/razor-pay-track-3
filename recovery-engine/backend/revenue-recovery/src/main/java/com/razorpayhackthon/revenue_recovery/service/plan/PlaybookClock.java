package com.razorpayhackthon.revenue_recovery.service.plan;

import com.razorpayhackthon.revenue_recovery.dto.PlaybookStepPreview;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real wait windows. The desk shortens them so a judge can watch one run. */
public final class PlaybookClock {

	public record Window(String label, int hours) {}

	private PlaybookClock() {}

	public static Window of(String reason, int step) {
		String key = reason == null ? "" : reason.toLowerCase();
		if (key.contains("insufficient_funds")) {
			return switch (step) {
				case 1 -> new Window("T+48h", 48);
				case 2 -> new Window("T+96h", 96);
				case 3 -> new Window("T+5d", 120);
				default -> new Window("After last retry", 120);
			};
		}
		if (key.contains("subscription.pending")) {
			return switch (step) {
				case 1 -> new Window("T+24h", 24);
				case 2 -> new Window("T+48h", 48);
				case 3 -> new Window("T+72h", 72);
				default -> new Window("After last retry", 72);
			};
		}
		if (key.contains("payment_timed_out")) {
			return switch (step) {
				case 1 -> new Window("T+2h", 2);
				case 2 -> new Window("T+24h", 24);
				case 3 -> new Window("T+48h", 48);
				default -> new Window("After last retry", 48);
			};
		}
		if (key.contains("card_declined")) {
			return switch (step) {
				case 1 -> new Window("T+24h", 24);
				case 2 -> new Window("T+48h", 48);
				case 3 -> new Window("T+72h", 72);
				default -> new Window("After last retry", 72);
			};
		}
		if (key.contains("gateway") || key.contains("bank_technical")) {
			return switch (step) {
				case 1 -> new Window("T+2h", 2);
				case 2 -> new Window("T+24h", 24);
				case 3 -> new Window("T+48h", 48);
				default -> new Window("After last retry", 48);
			};
		}
		if (key.contains("risk") || key.contains("cancelled")) {
			return switch (step) {
				case 1 -> new Window("T+0", 0);
				case 2 -> new Window("T+15m", 0);
				case 3 -> new Window("T+24h", 24);
				default -> new Window("Held", 24);
			};
		}
		if (key.contains("card_expired")
				|| key.contains("card_not_enrolled")
				|| key.contains("currency_not_supported")
				|| key.contains("invalid_vpa")
				|| key.contains("checkout")) {
			return switch (step) {
				case 1 -> new Window("T+0", 0);
				case 2 -> new Window("T+24h", 24);
				case 3 -> new Window("T+72h", 72);
				default -> new Window("Stop", 72);
			};
		}
		if (key.contains("invoice")) {
			return switch (step) {
				case 1 -> new Window("T+0", 0);
				case 2 -> new Window("T+3d", 72);
				case 3 -> new Window("T+7d", 168);
				default -> new Window("Stop", 168);
			};
		}
		if (key.contains("subscription.halted")) {
			return switch (step) {
				case 1 -> new Window("T+0", 0);
				case 2 -> new Window("T+24h", 24);
				case 3 -> new Window("T+72h", 72);
				default -> new Window("Stop", 72);
			};
		}
		return switch (step) {
			case 1 -> new Window("T+48h", 48);
			case 2 -> new Window("T+96h", 96);
			default -> new Window("T+5d", 120);
		};
	}

	public static PlaybookStepPreview stamp(String reason, PlaybookStepPreview step) {
		Window window = of(reason, step.step());
		return new PlaybookStepPreview(step.step(), step.actionType(), step.note(), window.label(), window.hours());
	}

	public static LocalDateTime runAfter(LocalDateTime failedAt, int waitHours) {
		LocalDateTime start = failedAt == null ? LocalDateTime.now() : failedAt;
		return start.plusHours(Math.max(0, waitHours));
	}

	public static Map<String, Object> auditFields(String reason, int step, LocalDateTime failedAt) {
		Window window = of(reason, step);
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("scheduleLabel", window.label());
		fields.put("waitHours", window.hours());
		fields.put("runAfter", runAfter(failedAt, window.hours()).toString());
		return fields;
	}
}
