package com.razorpayhackthon.revenue_recovery.service.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PredictApiResponse(
		@JsonProperty("recoveryProbability") double recoveryProbability, String label) {}
