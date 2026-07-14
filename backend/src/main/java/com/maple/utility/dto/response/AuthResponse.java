package com.maple.utility.dto.response;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		AuthUserResponse user
) {
	public static AuthResponse bearer(String accessToken, long expiresIn, AuthUserResponse user) {
		return new AuthResponse(accessToken, "Bearer", expiresIn, user);
	}
}
