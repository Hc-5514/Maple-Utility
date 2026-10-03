package com.maple.utility.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.maple.utility.dto.request.CharacterSortOrderRequest;
import com.maple.utility.dto.response.CharacterResponse;
import com.maple.utility.dto.response.SyncJobResponse;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.CharacterService;
import com.maple.utility.service.SyncJobService;

@RestController
@RequestMapping("/api/v1/characters")
public class CharacterController {

	private final CharacterService characterService;
	private final SyncJobService syncJobService;

	public CharacterController(CharacterService characterService, SyncJobService syncJobService) {
		this.characterService = characterService;
		this.syncJobService = syncJobService;
	}

	@GetMapping
	public List<CharacterResponse> getCharacters(Authentication authentication) {
		return characterService.getCharacters(currentUserId(authentication));
	}

	@GetMapping("/favorites")
	public List<CharacterResponse> getFavoriteCharacters(Authentication authentication) {
		return characterService.getFavoriteCharacters(currentUserId(authentication));
	}

	@PatchMapping("/{id}/favorite")
	public CharacterResponse toggleFavorite(
			@PathVariable Long id,
			Authentication authentication
	) {
		return characterService.toggleFavorite(currentUserId(authentication), id);
	}

	@PatchMapping("/{id}/sort-order")
	public CharacterResponse updateSortOrder(
			@PathVariable Long id,
			@Valid @RequestBody CharacterSortOrderRequest request,
			Authentication authentication
	) {
		return characterService.updateSortOrder(currentUserId(authentication), id, request.sortOrder());
	}

	@PostMapping("/sync")
	public SyncJobResponse syncCharacters(Authentication authentication) {
		return syncJobService.startCharacters(currentUserId(authentication));
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
