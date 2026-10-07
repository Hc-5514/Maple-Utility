package com.maple.utility.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.maple.utility.dto.response.StatsBossItemResponse;
import com.maple.utility.dto.response.PageResponse;
import com.maple.utility.dto.response.StatsCompletionResponse;
import com.maple.utility.dto.response.StatsCrystalSummaryResponse;
import com.maple.utility.dto.response.StatsHuntingSummaryResponse;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.StatsService;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

	private final StatsService statsService;

	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@GetMapping("/hunting")
	public StatsHuntingSummaryResponse getHuntingStats(
			@RequestParam(required = false) Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			Authentication authentication
	) {
		return statsService.getHuntingStats(currentUserId(authentication), characterId, dateFrom, dateTo);
	}

	@GetMapping("/crystal")
	public StatsCrystalSummaryResponse getCrystalStats(
			@RequestParam(required = false) Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			Authentication authentication
	) {
		return statsService.getCrystalStats(currentUserId(authentication), characterId, dateFrom, dateTo);
	}

	@GetMapping("/boss-items")
	public PageResponse<StatsBossItemResponse> getBossItemStats(
			@RequestParam(required = false) Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			Authentication authentication
	) {
		return statsService.getBossItemStats(currentUserId(authentication), characterId, dateFrom, dateTo, page, size);
	}

	@GetMapping("/completion")
	public StatsCompletionResponse getCompletionStats(
			@RequestParam(required = false) Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			Authentication authentication
	) {
		return statsService.getCompletionStats(currentUserId(authentication), characterId, dateFrom, dateTo);
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.userId();
	}
}
