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
import com.maple.utility.dto.request.NexonApiKeyLoginRequest;
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
	private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1";

	private final AuthService authService;
    private final com.maple.utility.service.DataContextService contexts;

	public AuthController(AuthService authService, com.maple.utility.service.DataContextService contexts) {
		this.authService = authService;
        this.contexts = contexts;
	}

	@PostMapping("/kakao")
	public ResponseEntity<AuthResponse> kakaoLogin(@Valid @RequestBody OAuthLoginRequest request) {
		return login(OAuthProvider.KAKAO, request.code());
	}

	@PostMapping("/nexon-apikey")
	public ResponseEntity<AuthResponse> nexonApiKeyLogin(@Valid @RequestBody NexonApiKeyLoginRequest request) {
		AuthService.LoginResult result = authService.loginWithNexonApiKey(request.apiKey());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, expireLegacyRefreshTokenCookie().toString(), refreshTokenCookie(result.refreshToken()).toString())
				.body(result.response());
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(@CookieValue(REFRESH_TOKEN_COOKIE_NAME) String refreshToken) {
        var result = authService.refreshSession(refreshToken);
        if (result.upgradedRefreshToken() == null) return ResponseEntity.ok(result.response());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, expireLegacyRefreshTokenCookie().toString(),
                refreshTokenCookie(result.upgradedRefreshToken()).toString()).body(result.response());
    }

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(REFRESH_TOKEN_COOKIE_NAME) String refreshToken,
			Authentication authentication
	) {
		currentUserId(authentication);
		authService.logout(refreshToken);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, expireLegacyRefreshTokenCookie().toString(), expireRefreshTokenCookie().toString())
				.build();
	}

	@GetMapping("/me")
	public MeResponse me(Authentication authentication) {
		return authService.me(currentUserId(authentication));
	}

    @GetMapping("/data-contexts")
    public java.util.List<com.maple.utility.service.DataContextService.DataContextResponse> dataContexts(Authentication authentication) {
        return contexts.list((JwtAuthentication) authentication.getPrincipal());
    }

    @PostMapping("/data-context")
    public ResponseEntity<AuthResponse> selectContext(
            @Valid @RequestBody com.maple.utility.dto.request.DataContextSelectRequest request,
            Authentication authentication, @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        var result = authService.selectContext(currentUserId(authentication), request.credentialId(), refreshToken);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, expireLegacyRefreshTokenCookie().toString(), refreshTokenCookie(result.refreshToken()).toString()).body(result.response());
    }

	private ResponseEntity<AuthResponse> login(OAuthProvider provider, String code) {
		AuthService.LoginResult result = authService.login(provider, code);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, expireLegacyRefreshTokenCookie().toString(), refreshTokenCookie(result.refreshToken()).toString())
				.body(result.response());
	}

	private Long currentUserId(Authentication authentication) {
		JwtAuthentication principal = (JwtAuthentication) authentication.getPrincipal();
		return principal.identityUserId();
	}

	public static ResponseCookie refreshTokenCookie(JwtToken refreshToken) {
		return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken.value())
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path(REFRESH_TOKEN_COOKIE_PATH)
				.maxAge(refreshToken.ttl())
				.build();
	}

    public static ResponseCookie expireLegacyRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "").httpOnly(true).secure(true).sameSite("Strict")
                .path("/api/v1/auth").maxAge(Duration.ZERO).build();
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
