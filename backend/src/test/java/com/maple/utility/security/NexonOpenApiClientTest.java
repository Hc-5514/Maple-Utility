package com.maple.utility.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.maple.utility.config.NexonProperties;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class NexonOpenApiClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private NexonApiGateway nexonApiGateway;

	private NexonOpenApiClient nexonOpenApiClient;

	@BeforeEach
	void setUp() {
		NexonProperties properties = new NexonProperties(
				"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
				"https://example.test/character/list",
				"https://example.test/character/basic",
				"https://example.test/scheduler/character-state",
				500,
				450,
				Duration.ofSeconds(1)
		);
		nexonOpenApiClient = new NexonOpenApiClient(nexonApiGateway, properties);
	}

	@Test
	void getCharactersParsesRootCharacterList() throws Exception {
		when(nexonApiGateway.get(1L, "api-key", "https://example.test/character/list", NexonRequestMode.REALTIME))
				.thenReturn(objectMapper.readTree("""
						{
						  "character_list": [
						    {
						      "ocid": "ocid",
						      "character_name": "캐릭터",
						      "world_name": "스카니아",
						      "character_class": "히어로",
						      "character_level": 280
						    }
						  ]
						}
						"""));

		List<NexonCharacterSummary> characters = nexonOpenApiClient.getCharacters(1L, "api-key");

		assertThat(characters).containsExactly(new NexonCharacterSummary("ocid", "캐릭터", "스카니아", "히어로", 280));
	}

	@Test
	void getCharacterBasicBuildsOcidQueryAndParsesBasic() throws Exception {
		when(nexonApiGateway.get(eq(1L), eq("api-key"), contains("ocid=ocid"), eq(NexonRequestMode.REALTIME)))
				.thenReturn(objectMapper.readTree("""
						{
						  "character_name": "캐릭터",
						  "world_name": "스카니아",
						  "character_class": "히어로",
						  "character_level": 280,
						  "character_image": "image-url",
						  "character_guild_name": "길드"
						}
						"""));

		NexonCharacterBasic basic = nexonOpenApiClient.getCharacterBasic(1L, "api-key", "ocid");

		assertThat(basic).isEqualTo(new NexonCharacterBasic("ocid", "캐릭터", "스카니아", "히어로", 280, "image-url", "길드"));
	}

	@Test
	void getCharacterSchedulerBuildsOcidQueryAndParsesScheduler() throws Exception {
		when(nexonApiGateway.getWithStoredKey(eq(1L), eq("https://example.test/scheduler/character-state?ocid=ocid"), eq(NexonRequestMode.REALTIME)))
				.thenReturn(objectMapper.readTree("""
						{
						  "date": "2026-07-14",
						  "daily_contents": [
						    {
						      "content_name": "일일 퀘스트",
						      "type": "quest",
						      "registration_flag": "true",
						      "now_count": 0,
						      "max_count": 0,
						      "quest_state": "2"
						    },
						    {
						      "content_name": "일일 콘텐츠",
						      "type": "contents",
						      "registration_flag": "true",
						      "now_count": 1,
						      "max_count": 3
						    },
						    {
						      "content_name": "미등록 콘텐츠",
						      "registration_flag": "false"
						    }
						  ],
						  "weekly_contents": [
						    {
						      "content_name": "길드 주간 미션",
						      "type": "contents",
						      "registration_flag": "true",
						      "now_count": 1000,
						      "max_count": 1000
						    },
						    {
						      "content_name": "주간 퀘스트",
						      "type": "quest",
						      "registration_flag": "true",
						      "now_count": 0,
						      "max_count": 0,
						      "quest_state": "1"
						    }
						  ],
						  "boss_contents": [
						    {
						      "content_name": "스우",
						      "difficulty": "HARD",
						      "cycle": "WEEKLY",
						      "registration_flag": "true",
						      "complete_flag": "true"
						    }
						  ]
						}
						"""));

		NexonSchedulerResponse response = nexonOpenApiClient.getCharacterScheduler(1L, "ocid");

		assertThat(response.date()).isEqualTo(LocalDate.of(2026, 7, 14));
		assertThat(response.daily()).containsExactly(
				new NexonSchedulerResponse.Daily(response.date(), "일일 퀘스트", 1, 1),
				new NexonSchedulerResponse.Daily(response.date(), "일일 콘텐츠", 1, 3));
		assertThat(response.weekly()).containsExactly(
				new NexonSchedulerResponse.Weekly(LocalDate.of(2026, 7, 13), "길드 주간 미션", true, 1000),
				new NexonSchedulerResponse.Weekly(LocalDate.of(2026, 7, 13), "주간 퀘스트", false, 0));
		assertThat(response.boss()).containsExactly(new NexonSchedulerResponse.Boss(response.date(), "스우", Difficulty.HARD, ResetPeriod.WEEKLY, true));
	}

	@Test
	void getCharacterSchedulerRejectsUnexpectedResponseShape() throws Exception {
		when(nexonApiGateway.getWithStoredKey(eq(1L), contains("ocid=ocid"), eq(NexonRequestMode.REALTIME)))
				.thenReturn(objectMapper.readTree("""
						{"date":"2026-07-14","daily":[],"weekly":[],"boss":[]}
						"""));

		assertThatThrownBy(() -> nexonOpenApiClient.getCharacterScheduler(1L, "ocid"))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("NEXON_RESPONSE_INVALID"));
	}

	@Test
	void getCharacterSchedulerAcceptsEmptyOfficialArrays() throws Exception {
		when(nexonApiGateway.getWithStoredKey(eq(1L), contains("ocid=ocid"), eq(NexonRequestMode.REALTIME)))
				.thenReturn(objectMapper.readTree("""
						{"date":"2026-07-14","daily_contents":[],"weekly_contents":[],"boss_contents":[]}
						"""));

		NexonSchedulerResponse response = nexonOpenApiClient.getCharacterScheduler(1L, "ocid");

		assertThat(response.date()).isEqualTo(LocalDate.of(2026, 7, 14));
		assertThat(response.daily()).isEmpty();
		assertThat(response.weekly()).isEmpty();
		assertThat(response.boss()).isEmpty();
	}
}
