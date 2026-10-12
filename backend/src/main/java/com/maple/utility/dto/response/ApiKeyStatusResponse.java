package com.maple.utility.dto.response;

import java.time.LocalDateTime;

import com.maple.utility.entity.ApiKeyStatus;
import com.maple.utility.entity.UserApiKey;

public record ApiKeyStatusResponse(
		boolean registered,
		ApiKeyStatus keyStatus,
		LocalDateTime lastVerifiedAt,
		String keySuffix
) {
	public static ApiKeyStatusResponse registered(UserApiKey userApiKey, String keySuffix) {
		return new ApiKeyStatusResponse(true, userApiKey.getKeyStatus(), userApiKey.getLastVerifiedAt(), keySuffix);
	}

	public static ApiKeyStatusResponse unregistered() {
		return new ApiKeyStatusResponse(false, null, null, null);
	}
}
