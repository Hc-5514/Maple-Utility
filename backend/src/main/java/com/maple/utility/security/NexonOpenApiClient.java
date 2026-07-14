package com.maple.utility.security;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.maple.utility.config.NexonProperties;
import com.maple.utility.exception.ApiException;

@Component
public class NexonOpenApiClient {

	private static final String NEXON_API_KEY_HEADER = "x-nxopen-api-key";

	private final RestClient restClient;
	private final NexonProperties properties;

	public NexonOpenApiClient(RestClient restClient, NexonProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public List<NexonCharacterSummary> getCharacters(String apiKey) {
		try {
			JsonNode response = restClient.get()
					.uri(properties.characterListUri())
					.header(NEXON_API_KEY_HEADER, apiKey)
					.retrieve()
					.body(JsonNode.class);
			return parseCharacters(response);
		} catch (RestClientResponseException exception) {
			if (exception.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "API_KEY_INVALID", "유효하지 않은 Nexon API Key");
			}
			throw new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_API_ERROR", "Nexon OpenAPI 호출 실패");
		}
	}

	private List<NexonCharacterSummary> parseCharacters(JsonNode response) {
		List<NexonCharacterSummary> characters = new ArrayList<>();
		if (response == null) {
			return characters;
		}
		appendCharacters(response.path("character_list"), characters);
		for (JsonNode account : response.path("account_list")) {
			appendCharacters(account.path("character_list"), characters);
		}
		return characters;
	}

	private void appendCharacters(JsonNode characterList, List<NexonCharacterSummary> characters) {
		if (!characterList.isArray()) {
			return;
		}
		for (JsonNode character : characterList) {
			String ocid = character.path("ocid").asText(null);
			String characterName = character.path("character_name").asText(null);
			if (ocid == null || characterName == null) {
				continue;
			}
			characters.add(new NexonCharacterSummary(
					ocid,
					characterName,
					character.path("world_name").asText(null),
					character.path("character_class").asText(null),
					character.path("character_level").isNumber() ? character.path("character_level").asInt() : null
			));
		}
	}
}
