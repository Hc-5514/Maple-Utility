package com.maple.utility.security;

import java.time.Duration;

public record JwtToken(
		String value,
		String tokenId,
		Duration ttl
) {
}
