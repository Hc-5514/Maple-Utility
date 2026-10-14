package com.maple.utility.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import com.maple.utility.dto.response.ApiKeyRegistrationResponse;
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
	public ResponseEntity<ApiKeyRegistrationResponse> register(
			@Valid @RequestBody ApiKeyRegisterRequest request,
			Authentication authentication,
            @CookieValue(value = "refreshToken", required = false) String refreshToken
    ) {
        var result = apiKeyService.register((JwtAuthentication) authentication.getPrincipal(), request.apiKey(), refreshToken);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, AuthController.expireLegacyRefreshTokenCookie().toString(), AuthController.refreshTokenCookie(result.refreshToken()).toString()).body(result.response());
	}

	@GetMapping("/status")
	public ApiKeyStatusResponse status(Authentication authentication) {
		return apiKeyService.status((JwtAuthentication) authentication.getPrincipal());
	}

	@DeleteMapping
	public ResponseEntity<Void> delete(Authentication authentication) {
		apiKeyService.delete((JwtAuthentication) authentication.getPrincipal());
		return ResponseEntity.noContent().build();
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.userId();
	}
}
