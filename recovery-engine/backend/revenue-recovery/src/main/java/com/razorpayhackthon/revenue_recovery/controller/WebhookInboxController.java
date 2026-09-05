package com.razorpayhackthon.revenue_recovery.controller;

import com.razorpayhackthon.revenue_recovery.auth.RequireRole;
import com.razorpayhackthon.revenue_recovery.dto.WebhookInboxSnapshot;
import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import com.razorpayhackthon.revenue_recovery.service.webhook.WebhookInboxService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
@RequireRole(DeskRole.ADMIN)
public class WebhookInboxController {

	private final WebhookInboxService webhookInboxService;

	public WebhookInboxController(WebhookInboxService webhookInboxService) {
		this.webhookInboxService = webhookInboxService;
	}

	@GetMapping(path = "/inbox", produces = MediaType.APPLICATION_JSON_VALUE)
	public WebhookInboxSnapshot inbox() {
		return webhookInboxService.snapshot();
	}
}
