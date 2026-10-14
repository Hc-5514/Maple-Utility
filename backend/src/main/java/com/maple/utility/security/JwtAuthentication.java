package com.maple.utility.security;

public record JwtAuthentication(Long userId, Long identityUserId, Long contextId, Long credentialId) {
    public JwtAuthentication(Long userId) { this(userId, userId, null, null); }
}
