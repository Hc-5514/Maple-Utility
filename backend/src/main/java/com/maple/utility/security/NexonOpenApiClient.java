package com.maple.utility.security;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

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
