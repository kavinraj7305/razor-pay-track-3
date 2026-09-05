package com.razorpayhackthon.revenue_recovery.dto;

public record PlaybookStepPreview(int step, String actionType, String note, String when, Integer waitHours) {

	public PlaybookStepPreview(int step, String actionType, String note) {
		this(step, actionType, note, null, null);
	}
}
