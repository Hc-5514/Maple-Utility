package com.maple.utility.dto.response;

import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;

public record MeResponse(
		Long id,
		OAuthProvider oauthProvider,
		String nickname,
		String email
) {
	public static MeResponse from(User user) {
		return new MeResponse(user.getId(), user.getOauthProvider(), user.getNickname(), user.getEmail());
	}
}
