package com.maple.utility.security;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.maple.utility.config.NexonProperties;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.ResetPeriod;

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

	public NexonSchedulerResponse getCharacterScheduler(Long userId, String ocid) {
		JsonNode response = nexonApiGateway.getWithStoredKey(userId, characterSchedulerUri(ocid), NexonRequestMode.REALTIME);
		return parseScheduler(response);
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

	private NexonSchedulerResponse parseScheduler(JsonNode response) {
		if (response == null) {
			return new NexonSchedulerResponse(List.of(), List.of(), List.of());
		}
		return new NexonSchedulerResponse(
				parseDailyRecords(firstArray(response, "daily", "daily_records", "scheduler_daily_records")),
				parseWeeklyRecords(firstArray(response, "weekly", "weekly_records", "scheduler_weekly_records")),
				parseBossRecords(firstArray(response, "boss", "boss_records", "scheduler_boss_records"))
		);
	}

	private List<NexonSchedulerResponse.Daily> parseDailyRecords(JsonNode records) {
		List<NexonSchedulerResponse.Daily> dailyRecords = new ArrayList<>();
		if (!records.isArray()) {
			return dailyRecords;
		}
		for (JsonNode record : records) {
			String contentName = text(record, "content_name", "contentName", "name");
			if (contentName == null) {
				continue;
			}
			dailyRecords.add(new NexonSchedulerResponse.Daily(
					date(record, "record_date", "date"),
					contentName,
					integer(record, 0, "completed_count", "completedCount", "current_count"),
					integer(record, 1, "total_count", "totalCount", "max_count")
			));
		}
		return dailyRecords;
	}

	private List<NexonSchedulerResponse.Weekly> parseWeeklyRecords(JsonNode records) {
		List<NexonSchedulerResponse.Weekly> weeklyRecords = new ArrayList<>();
		if (!records.isArray()) {
			return weeklyRecords;
		}
		for (JsonNode record : records) {
			String contentName = text(record, "content_name", "contentName", "name");
			if (contentName == null) {
				continue;
			}
			weeklyRecords.add(new NexonSchedulerResponse.Weekly(
					date(record, "week_start_date", "weekStartDate", "date"),
					contentName,
					bool(record, "is_completed", "completed", "complete"),
					nullableInteger(record, "score")
			));
		}
		return weeklyRecords;
	}

	private List<NexonSchedulerResponse.Boss> parseBossRecords(JsonNode records) {
		List<NexonSchedulerResponse.Boss> bossRecords = new ArrayList<>();
		if (!records.isArray()) {
			return bossRecords;
		}
		for (JsonNode record : records) {
			String bossName = text(record, "boss_name", "bossName", "name");
			Difficulty difficulty = difficulty(text(record, "difficulty", "boss_difficulty"));
			ResetPeriod resetPeriod = resetPeriod(text(record, "reset_period", "resetPeriod"));
			if (bossName == null || difficulty == null || resetPeriod == null) {
				continue;
			}
			bossRecords.add(new NexonSchedulerResponse.Boss(
					date(record, "record_date", "date"),
					bossName,
					difficulty,
					resetPeriod,
					bool(record, "is_completed", "completed", "complete")
			));
		}
		return bossRecords;
	}

	private JsonNode firstArray(JsonNode response, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode node = response.path(fieldName);
			if (node.isArray()) {
				return node;
			}
		}
		return MissingNode.getInstance();
	}

	private String text(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode value = node.path(fieldName);
			if (value.isTextual() && !value.asText().isBlank()) {
				return value.asText();
			}
		}
		return null;
	}

	private LocalDate date(JsonNode node, String... fieldNames) {
		String value = text(node, fieldNames);
		return value == null ? null : LocalDate.parse(value);
	}

	private Integer nullableInteger(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode value = node.path(fieldName);
			if (value.isNumber()) {
				return value.asInt();
			}
		}
		return null;
	}

	private int integer(JsonNode node, int fallback, String... fieldNames) {
		Integer value = nullableInteger(node, fieldNames);
		return value == null ? fallback : value;
	}

	private boolean bool(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode value = node.path(fieldName);
			if (value.isBoolean()) {
				return value.asBoolean();
			}
		}
		return false;
	}

	private Difficulty difficulty(String value) {
		if (value == null) {
			return null;
		}
		return switch (value.toUpperCase()) {
			case "EASY", "이지" -> Difficulty.EASY;
			case "NORMAL", "노멀", "노말" -> Difficulty.NORMAL;
			case "HARD", "하드" -> Difficulty.HARD;
			case "CHAOS", "카오스" -> Difficulty.CHAOS;
			case "EXTREME", "익스트림" -> Difficulty.EXTREME;
			default -> null;
		};
	}

	private ResetPeriod resetPeriod(String value) {
		if (value == null) {
			return null;
		}
		return switch (value.toUpperCase()) {
			case "WEEKLY", "주간" -> ResetPeriod.WEEKLY;
			case "MONTHLY", "월간" -> ResetPeriod.MONTHLY;
			default -> null;
		};
	}

	private String characterSchedulerUri(String ocid) {
		return UriComponentsBuilder.fromUriString(properties.characterSchedulerUri())
				.queryParam("ocid", ocid)
				.build()
				.encode()
				.toUriString();
	}
}
