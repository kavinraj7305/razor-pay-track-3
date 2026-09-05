package com.razorpayhackthon.revenue_recovery.service.plan;

public record PolicyDecision(
		Verdict verdict,
		boolean escalate,
		String recommendedAction,
		String reason) {

	public enum Verdict {
		ALLOW,
		SKIP_RETRY,
		BLOCK
	}

	public boolean blocked() {
		return verdict == Verdict.BLOCK;
	}

	public boolean skipRetry() {
		return verdict == Verdict.SKIP_RETRY || verdict == Verdict.BLOCK;
	}

	public boolean allowExecute() {
		return verdict != Verdict.BLOCK;
	}
}
