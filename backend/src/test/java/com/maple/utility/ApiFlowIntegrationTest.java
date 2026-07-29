package com.maple.utility;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.persistence.EntityManager;

import com.maple.utility.repository.BossDropItemRepository;
import com.maple.utility.repository.BossItemAcquisitionRepository;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.DataSyncLogRepository;
import com.maple.utility.repository.HuntingRecordRepository;
import com.maple.utility.repository.SchedulerBossRecordRepository;
import com.maple.utility.repository.SchedulerDailyRecordRepository;
import com.maple.utility.repository.SchedulerWeeklyRecordRepository;
import com.maple.utility.repository.StatsQueryRepository;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.JwtTokenProvider;
import com.maple.utility.security.NexonApiGateway;
import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.security.RefreshTokenRedisService;
import com.maple.utility.service.AuthService;
import com.maple.utility.service.CharacterService;

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

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private MockMvc mockMvc;

	@MockitoBean
	private EntityManager entityManager;

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
	private BossMasterRepository bossMasterRepository;

	@MockitoBean
	private BossDropItemRepository bossDropItemRepository;

	@MockitoBean
	private BossItemAcquisitionRepository bossItemAcquisitionRepository;

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
	private CharacterService characterService;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();
	}

	@Test
	void 미인증_보호_엔드포인트_401() throws Exception {
		mockMvc.perform(get("/api/v1/characters"))
				.andExpect(status().isUnauthorized());
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
