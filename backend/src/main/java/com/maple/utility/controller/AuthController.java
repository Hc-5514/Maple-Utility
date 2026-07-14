package com.maple.utility.controller;

import java.time.Duration;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.maple.utility.dto.request.OAuthLoginRequest;
import com.maple.utility.dto.response.AuthResponse;
import com.maple.utility.dto.response.MeResponse;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.security.JwtToken;
import com.maple.utility.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
	private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/kakao")
	public ResponseEntity<AuthResponse> kakaoLogin(@Valid @RequestBody OAuthLoginRequest request) {
		return login(OAuthProvider.KAKAO, request.code());
	}

	@PostMapping("/nexon")
	public ResponseEntity<AuthResponse> nexonLogin(@Valid @RequestBody OAuthLoginRequest request) {
		return login(OAuthProvider.NEXON, request.code());
	}

	@PostMapping("/refresh")
	public AuthResponse refresh(@CookieValue(REFRESH_TOKEN_COOKIE_NAME) String refreshToken) {
		return authService.refresh(refreshToken);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(REFRESH_TOKEN_COOKIE_NAME) String refreshToken,
			Authentication authentication
	) {
		currentUserId(authentication);
		authService.logout(refreshToken);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, expireRefreshTokenCookie().toString())
				.build();
	}

	@GetMapping("/me")
	public MeResponse me(Authentication authentication) {
		return authService.me(currentUserId(authentication));
	}

	private ResponseEntity<AuthResponse> login(OAuthProvider provider, String code) {
		AuthService.LoginResult result = authService.login(provider, code);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken()).toString())
				.body(result.response());
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.userId();
	}

	private ResponseCookie refreshTokenCookie(JwtToken refreshToken) {
		return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken.value())
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path(REFRESH_TOKEN_COOKIE_PATH)
				.maxAge(refreshToken.ttl())
				.build();
	}

	private ResponseCookie expireRefreshTokenCookie() {
		return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path(REFRESH_TOKEN_COOKIE_PATH)
				.maxAge(Duration.ZERO)
				.build();
	}
}
