package com.maple.utility.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.maple.utility.config.OAuthProperties;

abstract class AbstractOAuthClient implements OAuthClient {

	private final RestClient restClient;
	private final OAuthProperties.Provider properties;

	AbstractOAuthClient(RestClient restClient, OAuthProperties.Provider properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public OAuthUserInfo getUserInfo(String code) {
		String accessToken = exchangeAccessToken(code);
		JsonNode userInfo = restClient.get()
				.uri(properties.userInfoUri())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(JsonNode.class);

		return parseUserInfo(userInfo);
	}

	protected abstract OAuthUserInfo parseUserInfo(JsonNode userInfo);

	private String exchangeAccessToken(String code) {
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "authorization_code");
		body.add("client_id", properties.clientId());
		body.add("client_secret", properties.clientSecret());
		body.add("redirect_uri", properties.redirectUri());
		body.add("code", code);

		JsonNode tokenResponse = restClient.post()
				.uri(properties.tokenUri())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(body)
				.retrieve()
				.body(JsonNode.class);

		if (tokenResponse == null || tokenResponse.path("access_token").isMissingNode()) {
			throw new IllegalStateException("OAuth access token response is invalid");
		}

		return tokenResponse.path("access_token").asText();
	}
}
