package com.razorpayhackthon.revenue_recovery.controller;

import com.razorpayhackthon.revenue_recovery.auth.AuthContext;
import com.razorpayhackthon.revenue_recovery.auth.RequireRole;
import com.razorpayhackthon.revenue_recovery.dto.RecoveryCasePlanResponse;
import com.razorpayhackthon.revenue_recovery.dto.auth.ApprovalItem;
import com.razorpayhackthon.revenue_recovery.dto.auth.ApprovalNoteRequest;
import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import com.razorpayhackthon.revenue_recovery.service.auth.ApprovalService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
@RequireRole({DeskRole.APPROVER, DeskRole.ADMIN})
public class ApprovalController {

	private final ApprovalService approvalService;

	public ApprovalController(ApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@GetMapping(path = "/pending", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ApprovalItem> pending() {
		return approvalService.pending();
	}

	@PostMapping(
			path = "/{caseId}/approve",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public RecoveryCasePlanResponse approve(
			@PathVariable String caseId, @RequestBody(required = false) ApprovalNoteRequest request) {
		return approvalService.approve(caseId, request, AuthContext.require());
	}

	@PostMapping(
			path = "/{caseId}/reject",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public RecoveryCasePlanResponse reject(
			@PathVariable String caseId, @RequestBody(required = false) ApprovalNoteRequest request) {
		return approvalService.reject(caseId, request, AuthContext.require());
	}
}
