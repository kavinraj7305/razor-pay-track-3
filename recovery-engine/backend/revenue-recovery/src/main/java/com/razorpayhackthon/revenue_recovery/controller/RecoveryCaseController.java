package com.razorpayhackthon.revenue_recovery.controller;

import com.razorpayhackthon.revenue_recovery.dto.RecoveryCasePlanResponse;
import com.razorpayhackthon.revenue_recovery.dto.RecoveryCaseSummary;
import com.razorpayhackthon.revenue_recovery.service.plan.RecoveryActionPlanService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recovery-cases")
public class RecoveryCaseController {

	private final RecoveryActionPlanService recoveryActionPlanService;

	public RecoveryCaseController(RecoveryActionPlanService recoveryActionPlanService) {
		this.recoveryActionPlanService = recoveryActionPlanService;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<RecoveryCaseSummary> list() {
		return recoveryActionPlanService.list();
	}

	@GetMapping(path = "/{caseId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public RecoveryCasePlanResponse get(@PathVariable String caseId) {
		return recoveryActionPlanService.getPlan(caseId);
	}

	@RequestMapping(
			path = "/{caseId}/plan",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public RecoveryCasePlanResponse runBaselinePlan(@PathVariable String caseId) {
		return recoveryActionPlanService.runBaselinePlan(caseId);
	}

	@RequestMapping(
			path = "/{caseId}/execute",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = MediaType.APPLICATION_JSON_VALUE)
	public RecoveryCasePlanResponse executeNext(@PathVariable String caseId) {
		return recoveryActionPlanService.executeNext(caseId);
	}
}
