package com.razorpayhackthon.revenue_recovery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * India NSF sandbox rates (percent 0–100). Not real Razorpay charges — demo knobs.
 */
@Component
@ConfigurationProperties(prefix = "recovery.sandbox.nsf")
public class NsfSandboxProperties {

	/** First delayed retry after payday-ish wait. */
	private int step1Percent = 24;
	/** Second silent retry; remaining pool is harder. */
	private int step2Percent = 11;
	/** Last retry + SMS; India SMS nudge lift. */
	private int step3Percent = 18;
	/** Pay-link after retries exhausted. */
	private int step4Percent = 14;
	private int loyalBonusPercent = 10;
	private int newPenaltyPercent = 8;
	private int repeatNsfPenaltyPercent = 10;
	private int loyalSuccessPayments = 5;
	private int repeatNsfCases = 2;

	public int percentForStep(int step) {
		return switch (step) {
			case 1 -> step1Percent;
			case 2 -> step2Percent;
			case 3 -> step3Percent;
			case 4 -> step4Percent;
			default -> 0;
		};
	}

	public int getStep1Percent() {
		return step1Percent;
	}

	public void setStep1Percent(int step1Percent) {
		this.step1Percent = step1Percent;
	}

	public int getStep2Percent() {
		return step2Percent;
	}

	public void setStep2Percent(int step2Percent) {
		this.step2Percent = step2Percent;
	}

	public int getStep3Percent() {
		return step3Percent;
	}

	public void setStep3Percent(int step3Percent) {
		this.step3Percent = step3Percent;
	}

	public int getStep4Percent() {
		return step4Percent;
	}

	public void setStep4Percent(int step4Percent) {
		this.step4Percent = step4Percent;
	}

	public int getLoyalBonusPercent() {
		return loyalBonusPercent;
	}

	public void setLoyalBonusPercent(int loyalBonusPercent) {
		this.loyalBonusPercent = loyalBonusPercent;
	}

	public int getNewPenaltyPercent() {
		return newPenaltyPercent;
	}

	public void setNewPenaltyPercent(int newPenaltyPercent) {
		this.newPenaltyPercent = newPenaltyPercent;
	}

	public int getRepeatNsfPenaltyPercent() {
		return repeatNsfPenaltyPercent;
	}

	public void setRepeatNsfPenaltyPercent(int repeatNsfPenaltyPercent) {
		this.repeatNsfPenaltyPercent = repeatNsfPenaltyPercent;
	}

	public int getLoyalSuccessPayments() {
		return loyalSuccessPayments;
	}

	public void setLoyalSuccessPayments(int loyalSuccessPayments) {
		this.loyalSuccessPayments = loyalSuccessPayments;
	}

	public int getRepeatNsfCases() {
		return repeatNsfCases;
	}

	public void setRepeatNsfCases(int repeatNsfCases) {
		this.repeatNsfCases = repeatNsfCases;
	}
}
