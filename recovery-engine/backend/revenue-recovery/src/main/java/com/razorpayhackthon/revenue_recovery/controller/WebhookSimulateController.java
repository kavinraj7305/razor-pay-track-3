package com.razorpayhackthon.revenue_recovery.controller;

import com.razorpayhackthon.revenue_recovery.dto.ScenarioCatalogItem;
import com.razorpayhackthon.revenue_recovery.dto.SimulatedFailureResult;
import com.razorpayhackthon.revenue_recovery.service.webhook.WebhookSimulateService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookSimulateController {

	private final WebhookSimulateService webhookSimulateService;

	public WebhookSimulateController(WebhookSimulateService webhookSimulateService) {
		this.webhookSimulateService = webhookSimulateService;
	}

	@RequestMapping(
			path = "/api/webhooks/simulate",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ScenarioCatalogItem> catalog() {
		return webhookSimulateService.catalog();
	}

	@RequestMapping(
			path = "/api/webhooks/simulate/all",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public List<SimulatedFailureResult> simulateAll() {
		return webhookSimulateService.simulateAll();
	}

	@RequestMapping(
			path = "/api/webhooks/simulate/payment-failed",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public SimulatedFailureResult legacyPaymentFailed() {
		return webhookSimulateService.simulateLegacyPaymentFailed();
	}

	@RequestMapping(
			path = "/api/webhooks/simulate/{slug}",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public SimulatedFailureResult simulateOne(@PathVariable String slug) {
		return webhookSimulateService.simulateOne(slug);
	}
}
