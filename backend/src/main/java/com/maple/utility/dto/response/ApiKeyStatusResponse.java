package com.maple.utility.dto.response;

import java.time.LocalDateTime;

import com.maple.utility.entity.ApiKeyStatus;
import com.maple.utility.entity.UserApiKey;

public record ApiKeyStatusResponse(
		boolean registered,
		ApiKeyStatus keyStatus,
		LocalDateTime lastVerifiedAt
) {
	public static ApiKeyStatusResponse registered(UserApiKey userApiKey) {
		return new ApiKeyStatusResponse(true, userApiKey.getKeyStatus(), userApiKey.getLastVerifiedAt());
	}

	public static ApiKeyStatusResponse unregistered() {
		return new ApiKeyStatusResponse(false, null, null);
	}
}
