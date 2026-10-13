package com.maple.utility.dto.response;

import java.time.LocalDateTime;
import com.maple.utility.entity.ApiKeyStatus;

public record ApiKeyRegistrationResponse(boolean registered, ApiKeyStatus keyStatus,
        LocalDateTime lastVerifiedAt, String keySuffix, AuthResponse auth) { }
