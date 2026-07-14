package com.maple.utility.security;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.maple.utility.config.OAuthProperties;
import com.maple.utility.entity.OAuthProvider;

@Component
public class NexonOAuthClient extends AbstractOAuthClient {

	public NexonOAuthClient(RestClient restClient, OAuthProperties properties) {
		super(restClient, properties.nexon());
	}

	@Override
	public OAuthProvider provider() {
		return OAuthProvider.NEXON;
	}

	@Override
	protected OAuthUserInfo parseUserInfo(JsonNode userInfo) {
		JsonNode source = userInfo.path("user");
		if (source.isMissingNode()) {
			source = userInfo;
		}
		String oauthId = firstText(source, "id", "sub", "user_id", "account_id");
		String email = firstText(source, "email");
		String nickname = firstText(source, "nickname", "name");

		return new OAuthUserInfo(provider(), oauthId, email, nickname);
	}

	private String firstText(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode value = node.path(fieldName);
			if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
				return value.asText();
			}
		}
		return null;
	}
}
