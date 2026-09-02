package com.razorpayhackthon.revenue_recovery.service.ml;

import com.razorpayhackthon.revenue_recovery.config.MlProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MlPredictClient {

	private static final Logger log = LoggerFactory.getLogger(MlPredictClient.class);

	private final RestClient restClient;
	private final String predictUrl;

	public MlPredictClient(RestClient.Builder builder, MlProperties properties) {
		this.predictUrl = properties.getPredictUrl();
		this.restClient = builder
				.requestFactory(requestFactory())
				.build();
	}

	public Optional<PredictApiResponse> predict(PredictPayload payload) {
		try {
			PredictApiResponse body = restClient
					.post()
					.uri(predictUrl)
					.contentType(MediaType.APPLICATION_JSON)
					.body(toJson(payload))
					.retrieve()
					.body(PredictApiResponse.class);
			return Optional.ofNullable(body);
		} catch (RestClientException ex) {
			log.warn("ml /predict failed url={}: {}", predictUrl, ex.getMessage());
			return Optional.empty();
		}
	}

	private static org.springframework.http.client.ClientHttpRequestFactory requestFactory() {
		var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(3));
		return factory;
	}

	private static Map<String, Object> toJson(PredictPayload payload) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("reason", payload.reason());
		json.put("source", payload.source());
		json.put("priority", payload.priority());
		json.put("paymentMethod", payload.paymentMethod());
		json.put("amountInr", payload.amountInr());
		json.put("retryCount", payload.retryCount());
		json.put("hoursSinceFail", payload.hoursSinceFail());
		json.put("historicalRecoveryRate", payload.historicalRecoveryRate());
		json.put("retryHistoryCount", payload.retryHistoryCount());
		json.put("paymentSuccessRate", payload.paymentSuccessRate());
		json.put("paymentFailureRate", payload.paymentFailureRate());
		json.put("avgPaymentDelay", payload.avgPaymentDelay());
		json.put("subscriptionAgeMonths", payload.subscriptionAgeMonths());
		json.put("lifetimeValue", payload.lifetimeValue());
		json.put("avgOrderValue", payload.avgOrderValue());
		json.put("daysSinceLastActivity", payload.daysSinceLastActivity());
		json.put("historyPaymentCount", payload.historyPaymentCount());
		return json;
	}
}
