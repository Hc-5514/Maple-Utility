package com.maple.utility.security;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.maple.utility.config.OAuthProperties;
import com.maple.utility.entity.OAuthProvider;

import tools.jackson.databind.JsonNode;

@Component
public class KakaoOAuthClient extends AbstractOAuthClient {

	public KakaoOAuthClient(RestClient restClient, OAuthProperties properties) {
		super(restClient, properties.kakao());
	}

	@Override
	public OAuthProvider provider() {
		return OAuthProvider.KAKAO;
	}

	@Override
	protected OAuthUserInfo parseUserInfo(JsonNode userInfo) {
		JsonNode kakaoAccount = userInfo.path("kakao_account");
		JsonNode profile = kakaoAccount.path("profile");
		String oauthId = userInfo.path("id").asText();
		String email = kakaoAccount.path("email").asText(null);
		String nickname = profile.path("nickname").asText(null);

		return new OAuthUserInfo(provider(), oauthId, email, nickname);
	}
}
