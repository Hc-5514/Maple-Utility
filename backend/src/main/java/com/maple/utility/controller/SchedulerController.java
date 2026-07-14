package com.maple.utility.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.maple.utility.dto.response.SchedulerBossDetailResponse;
import com.maple.utility.dto.response.SchedulerDailyResponse;
import com.maple.utility.dto.response.SchedulerSummaryResponse;
import com.maple.utility.dto.response.SchedulerWeeklyResponse;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.SchedulerService;

@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerController {

	private final SchedulerService schedulerService;

	public SchedulerController(SchedulerService schedulerService) {
		this.schedulerService = schedulerService;
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

	@GetMapping("/{characterId}/guild")
	public List<SchedulerWeeklyResponse> getGuild(
			@PathVariable Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Authentication authentication
	) {
		return schedulerService.getGuild(currentUserId(authentication), characterId, date);
	}

	@PostMapping("/sync")
	public SchedulerSummaryResponse sync(Authentication authentication) {
		return schedulerService.sync(currentUserId(authentication));
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.userId();
	}
}
