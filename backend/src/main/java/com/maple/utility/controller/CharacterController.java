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
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.CharacterService;

@RestController
@RequestMapping("/api/v1/characters")
public class CharacterController {

	private final CharacterService characterService;

	public CharacterController(CharacterService characterService) {
		this.characterService = characterService;
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
	public List<CharacterResponse> syncCharacters(Authentication authentication) {
		return characterService.syncCharacters(currentUserId(authentication));
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.userId();
	}
}
