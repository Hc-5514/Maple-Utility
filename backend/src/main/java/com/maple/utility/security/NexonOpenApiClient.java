package com.maple.utility.security;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.maple.utility.config.NexonProperties;

@Component
public class NexonOpenApiClient {

	private final NexonApiGateway nexonApiGateway;
	private final NexonProperties properties;

	public NexonOpenApiClient(NexonApiGateway nexonApiGateway, NexonProperties properties) {
		this.nexonApiGateway = nexonApiGateway;
		this.properties = properties;
	}

	public List<NexonCharacterSummary> getCharacters(Long userId, String apiKey) {
		JsonNode response = nexonApiGateway.get(userId, apiKey, properties.characterListUri(), NexonRequestMode.REALTIME);
		return parseCharacters(response);
	}

	public List<NexonCharacterSummary> getCharacters(Long userId) {
		JsonNode response = nexonApiGateway.getWithStoredKey(userId, properties.characterListUri(), NexonRequestMode.REALTIME);
		return parseCharacters(response);
	}

	public NexonCharacterBasic getCharacterBasic(Long userId, String apiKey, String ocid) {
		JsonNode response = nexonApiGateway.get(userId, apiKey, characterBasicUri(ocid), NexonRequestMode.REALTIME);
		return parseCharacterBasic(ocid, response);
	}

	public NexonCharacterBasic getCharacterBasic(Long userId, String ocid) {
		JsonNode response = nexonApiGateway.getWithStoredKey(userId, characterBasicUri(ocid), NexonRequestMode.REALTIME);
		return parseCharacterBasic(ocid, response);
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

	private NexonCharacterBasic parseCharacterBasic(String ocid, JsonNode response) {
		if (response == null) {
			return new NexonCharacterBasic(ocid, null, null, null, null, null, null);
		}
		return new NexonCharacterBasic(
				ocid,
				response.path("character_name").asText(null),
				response.path("world_name").asText(null),
				response.path("character_class").asText(null),
				response.path("character_level").isNumber() ? response.path("character_level").asInt() : null,
				response.path("character_image").asText(null),
				response.path("character_guild_name").asText(null)
		);
	}

	private String characterBasicUri(String ocid) {
		return UriComponentsBuilder.fromUriString(properties.characterBasicUri())
				.queryParam("ocid", ocid)
				.build()
				.encode()
				.toUriString();
	}
}
