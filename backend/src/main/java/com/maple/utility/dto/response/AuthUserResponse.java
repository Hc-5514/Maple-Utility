package com.maple.utility.dto.response;

import com.maple.utility.entity.User;

public record AuthUserResponse(
		Long id,
		String nickname,
		String email,
		boolean isNewUser
) {
	public static AuthUserResponse from(User user, boolean isNewUser) {
		return new AuthUserResponse(user.getId(), user.getNickname(), user.getEmail(), isNewUser);
	}
}
