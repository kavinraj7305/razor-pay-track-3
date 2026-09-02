package com.razorpayhackthon.revenue_recovery.service.plan.handler;

import com.razorpayhackthon.revenue_recovery.dto.PlaybookStepPreview;
import java.util.Comparator;
import java.util.List;

public final class PlaybookPreviews {

	private PlaybookPreviews() {}

	public static List<PlaybookStepPreview> from(List<? extends PlaybookStep> steps) {
		return steps.stream()
				.sorted(Comparator.comparingInt(PlaybookStep::stepNumber))
				.map(step -> new PlaybookStepPreview(step.stepNumber(), step.actionType().name(), step.planNote()))
				.toList();
	}
}
