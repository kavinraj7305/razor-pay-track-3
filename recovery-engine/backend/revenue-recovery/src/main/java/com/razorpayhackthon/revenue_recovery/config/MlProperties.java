package com.razorpayhackthon.revenue_recovery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Less labelled data → playbook only. At/above the floor → playbook + P(recovery),
 * and only if this customer also has enough of their own history.
 * Production labelled floor should use 10000. Local demo uses 400 so the 500-row seed crosses it.
 */
@Component
@ConfigurationProperties(prefix = "recovery.ml")
public class MlProperties {

	private String predictUrl = "http://localhost:8001/predict";
	/** Count of recovery_outcome rows required before we consult /predict. */
	private long minLabelledOutcomes = 400;
	/** Below this P, do not extra-retry if the customer also has enough history. */
	private double considerMinProbability = 0.12;
	/** This customer's own payment records required before we score or let P change the plan. */
	private int minHistoryPaymentsToScore = 10;
	/** Personal history needed before P can skip a retry. */
	private int minHistoryPaymentsToOverride = 10;

	public String getPredictUrl() {
		return predictUrl;
	}

	public void setPredictUrl(String predictUrl) {
		this.predictUrl = predictUrl;
	}

	public long getMinLabelledOutcomes() {
		return minLabelledOutcomes;
	}

	public void setMinLabelledOutcomes(long minLabelledOutcomes) {
		this.minLabelledOutcomes = minLabelledOutcomes;
	}

	public double getConsiderMinProbability() {
		return considerMinProbability;
	}

	public void setConsiderMinProbability(double considerMinProbability) {
		this.considerMinProbability = considerMinProbability;
	}

	public int getMinHistoryPaymentsToScore() {
		return minHistoryPaymentsToScore;
	}

	public void setMinHistoryPaymentsToScore(int minHistoryPaymentsToScore) {
		this.minHistoryPaymentsToScore = minHistoryPaymentsToScore;
	}

	public int getMinHistoryPaymentsToOverride() {
		return minHistoryPaymentsToOverride;
	}

	public void setMinHistoryPaymentsToOverride(int minHistoryPaymentsToOverride) {
		this.minHistoryPaymentsToOverride = minHistoryPaymentsToOverride;
	}
}
