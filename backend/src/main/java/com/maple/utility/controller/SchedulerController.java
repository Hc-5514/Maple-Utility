package com.maple.utility.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.maple.utility.dto.response.SchedulerBossDetailResponse;
import com.maple.utility.dto.request.ManualBossRecordSaveRequest;
import com.maple.utility.dto.response.BossCandidateResponse;
import com.maple.utility.dto.response.SchedulerBossResponse;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.dto.response.SchedulerDailyResponse;
import com.maple.utility.dto.response.SchedulerSummaryResponse;
import com.maple.utility.dto.response.SchedulerWeeklyResponse;
import com.maple.utility.dto.response.SyncJobResponse;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.SchedulerService;
import com.maple.utility.service.SyncJobService;

@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerController {

	private final SchedulerService schedulerService;
	private final SyncJobService syncJobService;

	public SchedulerController(SchedulerService schedulerService, SyncJobService syncJobService) {
		this.schedulerService = schedulerService;
		this.syncJobService = syncJobService;
	}

	@GetMapping("/summary")
	public SchedulerSummaryResponse getSummary(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Authentication authentication
	) {
		return schedulerService.getSummary(currentUserId(authentication), date);
	}

	@GetMapping("/{characterId}/daily")
	public List<SchedulerDailyResponse> getDaily(
			@PathVariable Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Authentication authentication
	) {
		return schedulerService.getDaily(currentUserId(authentication), characterId, date);
	}

	@GetMapping("/{characterId}/weekly")
	public List<SchedulerWeeklyResponse> getWeekly(
			@PathVariable Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Authentication authentication
	) {
		return schedulerService.getWeekly(currentUserId(authentication), characterId, date);
	}

	@GetMapping("/{characterId}/boss")
	public SchedulerBossDetailResponse getBoss(
			@PathVariable Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Authentication authentication
	) {
		return schedulerService.getBoss(currentUserId(authentication), characterId, date);
	}

	@GetMapping("/{characterId}/boss/candidates")
	public List<BossCandidateResponse> getBossCandidates(
			@PathVariable Long characterId,
			@RequestParam ResetPeriod resetPeriod,
			Authentication authentication
	) {
		return schedulerService.getBossCandidates(currentUserId(authentication), characterId, resetPeriod);
	}

	@PostMapping("/{characterId}/boss/manual")
	public List<SchedulerBossResponse> saveManualBossRecords(
			@PathVariable Long characterId,
			@Valid @RequestBody ManualBossRecordSaveRequest request,
			Authentication authentication
	) {
		return schedulerService.saveManualBossRecords(currentUserId(authentication), characterId, request);
	}

	@GetMapping("/{characterId}/guild")
	public List<SchedulerWeeklyResponse> getGuild(
			@PathVariable Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Authentication authentication
	) {
		return schedulerService.getGuild(currentUserId(authentication), characterId, date);
	}

	@PostMapping("/sync")
	public SyncJobResponse sync(@RequestParam(defaultValue = "false") boolean force, Authentication authentication) {
		return syncJobService.startScheduler(currentUserId(authentication), force, false);
	}

	@GetMapping("/sync-jobs/{jobId}")
	public SyncJobResponse getSyncJob(@PathVariable Long jobId, Authentication authentication) {
		return syncJobService.get(currentUserId(authentication), jobId);
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.userId();
	}
}
