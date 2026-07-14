package com.maple.utility.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.maple.utility.dto.request.BossItemAcquisitionCreateRequest;
import com.maple.utility.dto.response.BossDropItemAcquisitionStatusResponse;
import com.maple.utility.dto.response.BossDropItemResponse;
import com.maple.utility.dto.response.BossItemAcquisitionResponse;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.BossDropService;

@RestController
@RequestMapping("/api/v1/boss")
public class BossDropController {

	private final BossDropService bossDropService;

	public BossDropController(BossDropService bossDropService) {
		this.bossDropService = bossDropService;
	}

	@GetMapping("/{bossId}/drop-items")
	public List<BossDropItemResponse> getDropItems(@PathVariable Long bossId) {
		return bossDropService.getDropItems(bossId);
	}

	@GetMapping("/{bossId}/drop-items/acquisitions")
	public List<BossDropItemAcquisitionStatusResponse> getAcquisitionStatus(
			@PathVariable Long bossId,
			@RequestParam Long characterId,
			Authentication authentication
	) {
		return bossDropService.getAcquisitionStatus(currentUserId(authentication), bossId, characterId);
	}

	@PostMapping("/item-acquisition")
	@ResponseStatus(HttpStatus.CREATED)
	public BossItemAcquisitionResponse createAcquisition(
			@Valid @RequestBody BossItemAcquisitionCreateRequest request,
			Authentication authentication
	) {
		return bossDropService.createAcquisition(currentUserId(authentication), request);
	}

	@DeleteMapping("/item-acquisition/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAcquisition(
			@PathVariable Long id,
			Authentication authentication
	) {
		bossDropService.deleteAcquisition(currentUserId(authentication), id);
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.userId();
	}
}
