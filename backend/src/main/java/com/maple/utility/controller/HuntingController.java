package com.maple.utility.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.maple.utility.dto.request.HuntingRecordRequest;
import com.maple.utility.dto.response.HuntingRecordResponse;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.HuntingService;

@RestController
@RequestMapping("/api/v1/hunting")
public class HuntingController {

	private final HuntingService huntingService;

	public HuntingController(HuntingService huntingService) {
		this.huntingService = huntingService;
	}

	@GetMapping
	public List<HuntingRecordResponse> getRecords(
			@RequestParam Long characterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			Authentication authentication
	) {
		return huntingService.getRecords(currentUserId(authentication), characterId, from, to);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public HuntingRecordResponse createRecord(
			@Valid @RequestBody HuntingRecordRequest request,
			Authentication authentication
	) {
		return huntingService.createRecord(currentUserId(authentication), request);
	}

	@PutMapping("/{id}")
	public HuntingRecordResponse updateRecord(
			@PathVariable Long id,
			@Valid @RequestBody HuntingRecordRequest request,
			Authentication authentication
	) {
		return huntingService.updateRecord(currentUserId(authentication), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteRecord(
			@PathVariable Long id,
			Authentication authentication
	) {
		huntingService.deleteRecord(currentUserId(authentication), id);
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.userId();
	}
}
