package com.razorpayhackthon.revenue_recovery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Less labelled data → playbook only. At/above the floor → playbook + P(recovery).
 * Production should use 10000. Local demo uses 400 so the 500-row seed crosses it.
 */
@Component
@ConfigurationProperties(prefix = "recovery.ml")
public class MlProperties {

	private String predictUrl = "http://localhost:8001/predict";
	/** Count of recovery_outcome rows required before we consult /predict. */
	private long minLabelledOutcomes = 400;
	/** Below this P, do not extra-retry if the customer also has enough history. */
	private double considerMinProbability = 0.25;
	/** Personal history needed before P can skip a retry (demo simulate users have ~0–1). */
	private int minHistoryPaymentsToOverride = 5;

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

	public int getMinHistoryPaymentsToOverride() {
		return minHistoryPaymentsToOverride;
	}

	public void setMinHistoryPaymentsToOverride(int minHistoryPaymentsToOverride) {
		this.minHistoryPaymentsToOverride = minHistoryPaymentsToOverride;
	}
}
