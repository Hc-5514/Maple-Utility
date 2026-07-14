package com.maple.utility.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.maple.utility.config.NexonProperties;

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
				"https://example.test/character/scheduler",
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
		when(nexonApiGateway.getWithStoredKey(eq(1L), contains("ocid=ocid"), eq(NexonRequestMode.REALTIME)))
				.thenReturn(objectMapper.readTree("""
						{
						  "daily": [
						    {
						      "record_date": "2026-07-14",
						      "content_name": "일일 퀘스트",
						      "completed_count": 1,
						      "total_count": 3
						    }
						  ],
						  "weekly": [
						    {
						      "week_start_date": "2026-07-13",
						      "content_name": "길드 주간 미션",
						      "is_completed": true,
						      "score": 1000
						    }
						  ],
						  "boss": [
						    {
						      "record_date": "2026-07-14",
						      "boss_name": "스우",
						      "difficulty": "HARD",
						      "reset_period": "WEEKLY",
						      "is_completed": true
						    }
						  ]
						}
						"""));

		NexonSchedulerResponse response = nexonOpenApiClient.getCharacterScheduler(1L, "ocid");

		assertThat(response.daily()).hasSize(1);
		assertThat(response.weekly()).hasSize(1);
		assertThat(response.boss()).hasSize(1);
		assertThat(response.boss().get(0).bossName()).isEqualTo("스우");
	}
}
