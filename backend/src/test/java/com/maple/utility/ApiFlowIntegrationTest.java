package com.maple.utility;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.persistence.EntityManager;

import com.maple.utility.repository.BossDropItemRepository;
import com.maple.utility.repository.BossItemAcquisitionRepository;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.BossPeriodEntryRepository;
import com.maple.utility.repository.BossPeriodSelectionRepository;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.DataSyncLogRepository;
import com.maple.utility.repository.HuntingRecordRepository;
import com.maple.utility.repository.HuntingGroundRepository;
import com.maple.utility.repository.UserHuntingGroundFavoriteRepository;
import com.maple.utility.repository.SchedulerBossRecordRepository;
import com.maple.utility.repository.SchedulerDailyRecordRepository;
import com.maple.utility.repository.SchedulerWeeklyRecordRepository;
import com.maple.utility.repository.StatsQueryRepository;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.repository.UserSelectedWorldRepository;
import com.maple.utility.security.JwtTokenProvider;
import com.maple.utility.security.NexonApiGateway;
import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.security.RefreshTokenRedisService;
import com.maple.utility.service.AuthService;
import com.maple.utility.service.CharacterService;
import com.maple.utility.service.HuntingService;
import com.maple.utility.service.WorldSelectionService;
import com.maple.utility.dto.response.WorldSelectionResponse;

@SpringBootTest(properties = {
		"JWT_SECRET=test-jwt-secret-for-context-load-integration-test-key-32b",
		"KAKAO_CLIENT_ID=test-kakao-client-id",
		"KAKAO_CLIENT_SECRET=test-kakao-client-secret",
		"KAKAO_REDIRECT_URI=http://localhost/oauth/kakao/callback",
		"NEXON_CLIENT_ID=test-nexon-client-id",
		"NEXON_CLIENT_SECRET=test-nexon-client-secret",
		"NEXON_REDIRECT_URI=http://localhost/oauth/nexon/callback",
		"NEXON_TOKEN_URI=http://localhost/oauth/nexon/token",
		"NEXON_USER_INFO_URI=http://localhost/oauth/nexon/user",
		"NEXON_API_KEY_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"APP_CORS_ALLOWED_ORIGINS=http://localhost:3000",
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
		"management.health.redis.enabled=false"
})
class ApiFlowIntegrationTest {
	@MockitoBean
	private org.springframework.transaction.PlatformTransactionManager transactionManager;

	@MockitoBean
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private com.maple.utility.security.ApiKeyCryptoService apiKeyCryptoService;

	private MockMvc mockMvc;

	@MockitoBean
	private EntityManager entityManager;

	@MockitoBean
	private UserSelectedWorldRepository userSelectedWorldRepository;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private UserApiKeyRepository userApiKeyRepository;

	@MockitoBean
	private CharacterRepository characterRepository;

	@MockitoBean
	private DataSyncLogRepository dataSyncLogRepository;

	@MockitoBean
	private HuntingRecordRepository huntingRecordRepository;

	@MockitoBean
	private HuntingGroundRepository huntingGroundRepository;

	@MockitoBean
	private UserHuntingGroundFavoriteRepository userHuntingGroundFavoriteRepository;

	@MockitoBean
	private BossMasterRepository bossMasterRepository;

	@MockitoBean
	private BossDropItemRepository bossDropItemRepository;

	@MockitoBean
	private BossItemAcquisitionRepository bossItemAcquisitionRepository;

	@MockitoBean
	private BossPeriodEntryRepository bossPeriodEntryRepository;

	@MockitoBean
	private BossPeriodSelectionRepository bossPeriodSelectionRepository;

	@MockitoBean
	private SchedulerDailyRecordRepository schedulerDailyRecordRepository;

	@MockitoBean
	private SchedulerWeeklyRecordRepository schedulerWeeklyRecordRepository;

	@MockitoBean
	private SchedulerBossRecordRepository schedulerBossRecordRepository;

	@MockitoBean
	private StatsQueryRepository statsQueryRepository;

	@MockitoBean
	private RefreshTokenRedisService refreshTokenRedisService;

	@MockitoBean
	private NexonApiGateway nexonApiGateway;

	@MockitoBean
	private NexonOpenApiClient nexonOpenApiClient;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private com.maple.utility.service.DataContextService dataContexts;

	@MockitoBean
	private CharacterService characterService;

	@MockitoBean
	private HuntingService huntingService;

	@MockitoBean
	private WorldSelectionService worldSelectionService;

	@BeforeEach
	void setUp() {
		Mockito.when(transactionManager.getTransaction(Mockito.any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
		Mockito.when(dataContexts.resolve(Mockito.any())).thenAnswer(i -> i.getArgument(0));
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();
	}

	@Test
	void API_Key_상태_암호화된_기존키_끝_네자리만_응답() throws Exception {
		String token = jwtTokenProvider.createAccessToken(new com.maple.utility.security.JwtAuthentication(7L, 7L, 70L, 10L)).value();
		String plainKey = "sensitive-api-key-AB12";
		String encrypted = apiKeyCryptoService.encrypt(plainKey);
		var user = com.maple.utility.entity.User.create(com.maple.utility.entity.OAuthProvider.KAKAO,
				"key-owner", null, "사용자");
		var key = com.maple.utility.entity.UserApiKey.create(user, encrypted, java.time.LocalDateTime.of(2026, 10, 12, 12, 0));
		Mockito.when(userApiKeyRepository.findById(10L)).thenReturn(java.util.Optional.of(key));
		Mockito.when(dataContexts.canonical(null)).thenReturn(new com.maple.utility.service.DataContextService.Context(70L, 7L, 7L, null));
		String body = mockMvc.perform(get("/api/v1/api-key/status").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.keySuffix").value("AB12"))
				.andExpect(jsonPath("$.registered").value(true))
				.andExpect(jsonPath("$.lastVerifiedAt").value("2026-10-12T12:00:00"))
				.andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(body).doesNotContain(plainKey, encrypted, "sensitive-api-key");
	}

	@Test
	void API_Key_미등록_끝자리_null_응답() throws Exception {
		String token = jwtTokenProvider.createAccessToken(new com.maple.utility.security.JwtAuthentication(7L, 7L, 70L, 10L)).value();
		Mockito.when(userApiKeyRepository.findById(10L)).thenReturn(java.util.Optional.empty());
		mockMvc.perform(get("/api/v1/api-key/status").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(false))
				.andExpect(jsonPath("$.keySuffix").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void 캐릭터_설정_일괄_저장_JWT_사용자_전달() throws Exception {
		String token = jwtTokenProvider.createAccessToken(new com.maple.utility.security.JwtAuthentication(7L, 7L, 70L, 10L)).value();
		var items = List.of(new com.maple.utility.dto.request.CharacterPreferencesRequest.Preference(10L, true));
		Mockito.when(characterService.updatePreferences(7L, items)).thenReturn(List.of());
		mockMvc.perform(put("/api/v1/characters/preferences").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"characters\":[{\"characterId\":10,\"favorite\":true}]}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
		Mockito.verify(characterService).updatePreferences(7L, items);
	}

	@Test
	void 캐릭터_설정_미인증_저장_거부() throws Exception {
		mockMvc.perform(put("/api/v1/characters/preferences").contentType(MediaType.APPLICATION_JSON)
				.content("{\"characters\":[]}")).andExpect(status().isUnauthorized());
	}

	@Test
	void 월드_선택_JWT_사용자별_조회_저장() throws Exception {
		String token = jwtTokenProvider.createAccessToken(new com.maple.utility.security.JwtAuthentication(7L, 7L, 70L, 10L)).value();
		Mockito.when(worldSelectionService.getSelection(7L)).thenReturn(new WorldSelectionResponse(List.of("루나")));
		Mockito.when(worldSelectionService.saveSelection(7L, List.of("베라")))
				.thenReturn(new WorldSelectionResponse(List.of("베라")));
		mockMvc.perform(get("/api/v1/characters/world-selection").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.worldNames[0]").value("루나"));
		mockMvc.perform(put("/api/v1/characters/world-selection").header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"worldNames\":[\"베라\"]}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.worldNames[0]").value("베라"));
		Mockito.verify(worldSelectionService).getSelection(7L);
		Mockito.verify(worldSelectionService).saveSelection(7L, List.of("베라"));
	}

	@Test
	void 월드_선택_미인증_조회_저장_401() throws Exception {
		mockMvc.perform(get("/api/v1/characters/world-selection")).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/v1/characters/world-selection").contentType(MediaType.APPLICATION_JSON)
				.content("{\"worldNames\":[]}")).andExpect(status().isUnauthorized());
	}

	@Test
	void 미인증_보호_엔드포인트_401() throws Exception {
		mockMvc.perform(get("/api/v1/characters"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 갱신_쿠키_누락_401() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_COOKIE_MISSING"));
	}

	@Test
	void 로그아웃_쿠키_누락_401() throws Exception {
		String token = jwtTokenProvider.createAccessToken(1L).value();

		mockMvc.perform(post("/api/v1/auth/logout")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_COOKIE_MISSING"));
	}

	@Test
	void 유효_토큰_보호_엔드포인트_200() throws Exception {
		String token = jwtTokenProvider.createAccessToken(1L).value();
		Mockito.when(characterService.getCharacters(1L))
				.thenReturn(Collections.emptyList());

		mockMvc.perform(get("/api/v1/characters")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void 즐겨찾기_순서_저장_경로_200() throws Exception {
		String token = jwtTokenProvider.createAccessToken(1L).value();
		Mockito.when(characterService.updateFavoriteSortOrder(1L, List.of(3L, 2L)))
				.thenReturn(Collections.emptyList());

		mockMvc.perform(patch("/api/v1/characters/favorites/sort-order")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"characterIds\":[3,2]}")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		Mockito.verify(characterService).updateFavoriteSortOrder(1L, List.of(3L, 2L));
	}

	@Test
	void 캐릭터_ID_없이_사냥_전체_조회_200() throws Exception {
		String token = jwtTokenProvider.createAccessToken(1L).value();
		LocalDate from = LocalDate.parse("2026-10-01");
		LocalDate to = LocalDate.parse("2026-10-06");
		Mockito.when(huntingService.getRecords(1L, null, from, to))
				.thenReturn(Collections.emptyList());

		mockMvc.perform(get("/api/v1/hunting")
					.param("from", from.toString())
					.param("to", to.toString())
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		Mockito.verify(huntingService).getRecords(1L, null, from, to);
	}

	@Test
	void 유효하지않은_토큰_401() throws Exception {
		mockMvc.perform(get("/api/v1/characters")
						.header("Authorization", "Bearer invalid.jwt.token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void actuator_health_공개_200() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

	@Test
	void actuator_metrics_공개_200() throws Exception {
		mockMvc.perform(get("/actuator/metrics"))
				.andExpect(status().isOk());
	}

	@Test
	void 보안_헤더_Referrer_Policy_포함() throws Exception {
		String token = jwtTokenProvider.createAccessToken(1L).value();
		Mockito.when(characterService.getCharacters(1L))
				.thenReturn(Collections.emptyList());

		mockMvc.perform(get("/api/v1/characters")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
	}

	@Test
	void 보안_헤더_nosniff_포함() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}
}
