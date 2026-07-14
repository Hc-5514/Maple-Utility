package com.maple.utility.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.maple.utility.dto.request.ApiKeyRegisterRequest;
import com.maple.utility.dto.response.ApiKeyStatusResponse;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.service.ApiKeyService;

@RestController
@RequestMapping("/api/v1/api-key")
public class ApiKeyController {

	private final ApiKeyService apiKeyService;

	public ApiKeyController(ApiKeyService apiKeyService) {
		this.apiKeyService = apiKeyService;
	}

	@PostMapping
	public ApiKeyStatusResponse register(
			@Valid @RequestBody ApiKeyRegisterRequest request,
			Authentication authentication
	) {
		return apiKeyService.register(currentUserId(authentication), request.apiKey());
	}

	@GetMapping("/status")
	public ApiKeyStatusResponse status(Authentication authentication) {
		return apiKeyService.status(currentUserId(authentication));
	}

	@DeleteMapping
	public ResponseEntity<Void> delete(Authentication authentication) {
		apiKeyService.delete(currentUserId(authentication));
		return ResponseEntity.noContent().build();
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.userId();
	}
}
