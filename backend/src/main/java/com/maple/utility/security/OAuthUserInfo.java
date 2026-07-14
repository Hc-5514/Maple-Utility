package com.maple.utility.security;

import com.maple.utility.entity.OAuthProvider;

public record OAuthUserInfo(
		OAuthProvider provider,
		String oauthId,
		String email,
		String nickname
) {
}
