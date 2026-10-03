package com.maple.utility.security;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.maple.utility.config.NexonProperties;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.SyncType;
import com.maple.utility.exception.ApiException;

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

	public NexonSchedulerResponse getCharacterScheduler(Long userId, String ocid, boolean force) {
		if (!force) {
			return getCharacterScheduler(userId, ocid);
		}
		JsonNode response = nexonApiGateway.getWithStoredKey(userId, characterSchedulerUri(ocid), NexonRequestMode.REALTIME, SyncType.SCHEDULER_REALTIME, !force);
		return parseScheduler(response);
	}

	public NexonSchedulerResponse getCharacterSchedulerForBatch(Long userId, String ocid) {
		JsonNode response = nexonApiGateway.getWithStoredKey(
				userId,
				characterSchedulerUri(ocid),
				NexonRequestMode.BATCH,
				SyncType.SCHEDULER_BATCH,
				false
		);
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
			String ocid = text(character, "ocid");
			String characterName = text(character, "character_name");
			if (ocid == null || characterName == null) {
				continue;
			}
			characters.add(new NexonCharacterSummary(
					ocid,
					characterName,
					text(character, "world_name"),
					text(character, "character_class"),
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
		if (response == null || !response.isObject()) {
			throw invalidSchedulerResponse();
		}
		LocalDate recordDate;
		try {
			recordDate = LocalDate.parse(requiredText(response, "date"));
		} catch (DateTimeParseException exception) {
			throw invalidSchedulerResponse();
		}
		return new NexonSchedulerResponse(
				recordDate,
				parseDailyRecords(requiredArray(response, "daily_contents"), recordDate),
				parseWeeklyRecords(requiredArray(response, "weekly_contents"), recordDate),
				parseBossRecords(requiredArray(response, "boss_contents"), recordDate)
		);
	}

	private List<NexonSchedulerResponse.Daily> parseDailyRecords(JsonNode records, LocalDate recordDate) {
		List<NexonSchedulerResponse.Daily> dailyRecords = new ArrayList<>();
		for (JsonNode record : records) {
			if (!flag(record, "registration_flag")) {
				continue;
			}
			String contentName = requiredText(record, "content_name");
			int nowCount = requiredInt(record, "now_count");
			int maxCount = requiredInt(record, "max_count");
			boolean quest = "quest".equals(text(record, "type"));
			dailyRecords.add(new NexonSchedulerResponse.Daily(
					recordDate,
					contentName,
					quest ? ("2".equals(text(record, "quest_state")) ? 1 : 0) : nowCount,
					quest ? 1 : Math.max(maxCount, 1)
			));
		}
		return dailyRecords;
	}

	private List<NexonSchedulerResponse.Weekly> parseWeeklyRecords(JsonNode records, LocalDate recordDate) {
		List<NexonSchedulerResponse.Weekly> weeklyRecords = new ArrayList<>();
		LocalDate weekStartDate = recordDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		for (JsonNode record : records) {
			if (!flag(record, "registration_flag")) {
				continue;
			}
			String contentName = requiredText(record, "content_name");
			int nowCount = requiredInt(record, "now_count");
			int maxCount = requiredInt(record, "max_count");
			boolean completed = "quest".equals(text(record, "type"))
					? "2".equals(text(record, "quest_state"))
					: maxCount > 0 && nowCount >= maxCount;
			weeklyRecords.add(new NexonSchedulerResponse.Weekly(
					weekStartDate,
					contentName,
					completed,
					nowCount
			));
		}
		return weeklyRecords;
	}

	private List<NexonSchedulerResponse.Boss> parseBossRecords(JsonNode records, LocalDate recordDate) {
		List<NexonSchedulerResponse.Boss> bossRecords = new ArrayList<>();
		for (JsonNode record : records) {
			if (!flag(record, "registration_flag")) {
				continue;
			}
			String bossName = requiredText(record, "content_name");
			Difficulty difficulty = difficulty(requiredText(record, "difficulty"));
			ResetPeriod resetPeriod = resetPeriod(requiredText(record, "cycle"));
			if (difficulty == null || resetPeriod == null) {
				throw invalidSchedulerResponse();
			}
			bossRecords.add(new NexonSchedulerResponse.Boss(
					recordDate,
					bossName,
					difficulty,
					resetPeriod,
					flag(record, "complete_flag")
			));
		}
		return bossRecords;
	}

	private JsonNode requiredArray(JsonNode response, String fieldName) {
		JsonNode value = response.path(fieldName);
		if (!value.isArray()) {
			throw invalidSchedulerResponse();
		}
		return value;
	}

	private String text(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode value = node.path(fieldName);
			if (value.isTextual() && !value.asText().isBlank()) {
				return value.asText().strip();
			}
		}
		return null;
	}

	private String requiredText(JsonNode node, String fieldName) {
		String value = text(node, fieldName);
		if (value == null) {
			throw invalidSchedulerResponse();
		}
		return value;
	}

	private int requiredInt(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw invalidSchedulerResponse();
		}
		return value.asInt();
	}

	private boolean flag(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		if (value.isBoolean()) {
			return value.asBoolean();
		}
		if (value.isTextual() && ("true".equalsIgnoreCase(value.asText()) || "false".equalsIgnoreCase(value.asText()))) {
			return Boolean.parseBoolean(value.asText());
		}
		throw invalidSchedulerResponse();
	}

	private ApiException invalidSchedulerResponse() {
		return new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_RESPONSE_INVALID", "Nexon 스케줄러 응답 형식 오류");
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
