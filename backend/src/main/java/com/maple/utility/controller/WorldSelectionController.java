package com.maple.utility.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.maple.utility.dto.request.WorldSelectionRequest;
import com.maple.utility.dto.response.WorldSelectionResponse;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.WorldSelectionService;

@RestController
@RequestMapping("/api/v1/characters/world-selection")
public class WorldSelectionController {
	private final WorldSelectionService service;

	public WorldSelectionController(WorldSelectionService service) {
		this.service = service;
	}

	@GetMapping
	public WorldSelectionResponse getSelection(Authentication authentication) {
		return service.getSelection(((JwtAuthentication) authentication.getPrincipal()).userId());
	}

	@PutMapping
	public WorldSelectionResponse saveSelection(
			@Valid @RequestBody WorldSelectionRequest request, Authentication authentication) {
		return service.saveSelection(((JwtAuthentication) authentication.getPrincipal()).userId(), request.worldNames());
	}
}
