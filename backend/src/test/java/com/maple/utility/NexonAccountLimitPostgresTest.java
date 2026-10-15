package com.maple.utility;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.security.JwtAuthentication;
import com.maple.utility.security.JwtTokenProvider;
import com.maple.utility.security.KakaoOAuthClient;
import com.maple.utility.security.NexonAccountCharacters;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.security.OAuthUserInfo;
import com.maple.utility.security.RefreshTokenRedisService;
import com.maple.utility.service.DataContextService;
import com.maple.utility.service.DataContextService.PrecheckStatus;

import jakarta.servlet.http.Cookie;

/** #329: 카카오 계정당 넥슨 계정 5개 제한과 읽기 전용 연동 사전 확인. */
@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
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
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true",
		"spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'Asia/Seoul'",
		"management.health.redis.enabled=false"
})
class NexonAccountLimitPostgresTest {
	static final String SCHEMA = "task329_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", () -> System.getenv("FLYWAY_TEST_URL") + "?currentSchema=" + SCHEMA);
		r.add("spring.datasource.username", () -> System.getenv("FLYWAY_TEST_USER"));
		r.add("spring.datasource.password", () -> System.getenv("FLYWAY_TEST_PASSWORD"));
		r.add("spring.flyway.schemas", () -> SCHEMA);
		r.add("spring.flyway.default-schema", () -> SCHEMA);
		r.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
	}

	@AfterAll
	static void cleanup() throws Exception {
		try (var c = DriverManager.getConnection(System.getenv("FLYWAY_TEST_URL"), System.getenv("FLYWAY_TEST_USER"),
				System.getenv("FLYWAY_TEST_PASSWORD")); var s = c.createStatement()) {
			s.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
		}
	}

	@Autowired WebApplicationContext web;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtTokenProvider tokens;
	@Autowired DataContextService contexts;
	@MockitoBean(name = "syncExecutor") java.util.concurrent.Executor executor;
	@MockitoBean NexonOpenApiClient nexon;
	@MockitoBean RefreshTokenRedisService redis;
	@MockitoSpyBean KakaoOAuthClient kakao;
	@MockitoBean(name = "cacheManager") org.springframework.cache.CacheManager caches;

	MockMvc mvc;
	ObjectMapper json = new ObjectMapper();
	Map<String, String> refreshStore;

	record Session(String access, String refresh) { }

	@BeforeEach
	void setup() {
		jdbc.execute("TRUNCATE users CASCADE");
		mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
		refreshStore = new ConcurrentHashMap<>();
		doAnswer(i -> { refreshStore.put(i.getArgument(0) + ":" + i.getArgument(1), i.getArgument(2)); return null; })
				.when(redis).save(anyLong(), anyString(), anyString(), any());
		when(redis.find(anyLong(), anyString())).thenAnswer(i -> Optional.ofNullable(refreshStore.get(i.getArgument(0) + ":" + i.getArgument(1))));
		doAnswer(i -> { refreshStore.remove(i.getArgument(0) + ":" + i.getArgument(1)); return null; })
				.when(redis).delete(anyLong(), anyString());
		doAnswer(i -> new OAuthUserInfo(OAuthProvider.KAKAO, i.getArgument(0), null, "카카오")).when(kakao).getUserInfo(anyString());
		when(caches.getCacheNames()).thenReturn(List.of());
		// "acct-<n>-<suffix>" keys all belong to Nexon account <n>
		when(nexon.verifyAccounts(anyLong(), anyString())).thenAnswer(i -> {
			String account = ((String) i.getArgument(1)).split("-")[1];
			return new NexonAccountCharacters(Set.of(account),
					List.of(new NexonCharacterSummary(account + "-ocid", "캐릭터" + account, "루나", "비숍", 280)));
		});
	}

	Session session(MvcResult result, boolean registration) throws Exception {
		var node = json.readTree(result.getResponse().getContentAsString());
		String access = (registration ? node.path("auth") : node).path("accessToken").asText();
		Cookie fresh = Arrays.stream(result.getResponse().getCookies())
				.filter(c -> c.getName().equals("refreshToken") && !c.getValue().isBlank()).findFirst().orElseThrow();
		return new Session(access, fresh.getValue());
	}

	Session social(String code) throws Exception {
		return session(mvc.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"" + code + "\"}")).andExpect(status().isOk()).andReturn(), false);
	}

	Session direct(String key) throws Exception {
		return session(mvc.perform(post("/api/v1/auth/nexon-apikey").contentType(MediaType.APPLICATION_JSON)
				.content("{\"apiKey\":\"" + key + "\"}")).andExpect(status().isOk()).andReturn(), false);
	}

	Session register(Session s, String key) throws Exception {
		return session(mvc.perform(post("/api/v1/api-key").header("Authorization", "Bearer " + s.access())
				.cookie(new Cookie("refreshToken", s.refresh())).contentType(MediaType.APPLICATION_JSON)
				.content("{\"apiKey\":\"" + key + "\"}")).andExpect(status().isOk()).andReturn(), true);
	}

	JwtAuthentication principal(Session s) { return tokens.parseAccessToken(s.access()); }

	String precheck(Session s, String key) throws Exception {
		var result = mvc.perform(post("/api/v1/auth/data-contexts/precheck").header("Authorization", "Bearer " + s.access())
				.contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"" + key + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsString()).path("status").asText();
	}

	/** Registers acct-1..acct-n in order, returning the last session. */
	Session registerAccounts(Session s, int n) throws Exception {
		for (int i = 1; i <= n; i++) s = register(s, "acct-" + i + "-a");
		return s;
	}

	@Test
	void sixthAccountIsRejectedAndRollsBackWithoutChangingConnections() throws Exception {
		Session s = registerAccounts(social("social-1"), 5);
		assertThat(contexts.list(principal(s).identityUserId())).hasSize(5);
		long links = jdbc.queryForObject("SELECT count(*) FROM user_data_contexts", Long.class);
		long keys = jdbc.queryForObject("SELECT count(*) FROM user_api_keys", Long.class);
		long datasets = jdbc.queryForObject("SELECT count(*) FROM data_contexts", Long.class);

		mvc.perform(post("/api/v1/api-key").header("Authorization", "Bearer " + s.access())
				.contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"acct-6-a\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONTEXT_LIMIT_EXCEEDED"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM user_data_contexts", Long.class)).isEqualTo(links);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys", Long.class)).isEqualTo(keys);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM data_contexts", Long.class)).isEqualTo(datasets);
		assertThat(contexts.list(principal(s).identityUserId())).hasSize(5);
	}

	@Test
	void fifthAccountIsAcceptedAndAdditionalKeyOfExistingAccountIsAllowedAtLimit() throws Exception {
		Session s = registerAccounts(social("social-1"), 5);
		// A second key of an already connected account does not add a dataset.
		Session again = register(s, "acct-3-b");
		assertThat(principal(again).contextId()).isEqualTo(principal(register(s, "acct-3-a")).contextId());
		assertThat(contexts.list(principal(again).identityUserId())).hasSize(6); // 5 datasets, one has 2 keys
		assertThat(jdbc.queryForObject("SELECT count(DISTINCT context_id) FROM user_api_keys", Long.class)).isEqualTo(5);
	}

	@Test
	void directKeyLoginIsNotSubjectToTheKakaoLimit() throws Exception {
		for (int i = 1; i <= 6; i++) direct("acct-" + i + "-a");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM data_contexts", Long.class)).isEqualTo(6);
	}

	@Test
	void precheckReportsEachStatusWithoutMutatingAnything() throws Exception {
		Session first = register(social("social-1"), "acct-1-a");
		Session second = social("social-2");

		long users = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
		long links = jdbc.queryForObject("SELECT count(*) FROM user_data_contexts", Long.class);
		long keys = jdbc.queryForObject("SELECT count(*) FROM user_api_keys", Long.class);
		long accounts = jdbc.queryForObject("SELECT count(*) FROM data_context_accounts", Long.class);

		assertThat(precheck(first, "acct-2-a")).isEqualTo(PrecheckStatus.AVAILABLE.name());
		assertThat(precheck(first, "acct-1-a")).isEqualTo(PrecheckStatus.ALREADY_LINKED.name());
		assertThat(precheck(first, "acct-1-b")).isEqualTo(PrecheckStatus.ALREADY_LINKED.name());
		assertThat(precheck(second, "acct-1-a")).isEqualTo(PrecheckStatus.LINKED_TO_OTHER_KAKAO.name());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(users);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM user_data_contexts", Long.class)).isEqualTo(links);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys", Long.class)).isEqualTo(keys);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM data_context_accounts", Long.class)).isEqualTo(accounts);
	}

	@Test
	void precheckReportsLimitExceededOnlyForNewAccounts() throws Exception {
		Session s = registerAccounts(social("social-1"), 5);
		assertThat(precheck(s, "acct-6-a")).isEqualTo(PrecheckStatus.LIMIT_EXCEEDED.name());
		assertThat(precheck(s, "acct-2-b")).isEqualTo(PrecheckStatus.ALREADY_LINKED.name());
	}

	@Test
	void precheckRequiresAuthentication() throws Exception {
		mvc.perform(post("/api/v1/auth/data-contexts/precheck").contentType(MediaType.APPLICATION_JSON)
				.content("{\"apiKey\":\"acct-1-a\"}")).andExpect(status().isUnauthorized());
	}
}
