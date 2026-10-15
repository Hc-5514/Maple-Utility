package com.maple.utility;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.fasterxml.jackson.databind.*;
import com.maple.utility.entity.*;
import com.maple.utility.exception.ApiException;
import com.maple.utility.security.*;
import com.maple.utility.service.*;
import jakarta.servlet.http.Cookie;

@EnabledIfEnvironmentVariable(named="FLYWAY_TEST_URL",matches=".+")
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
class SharedLoginPostgresTest {
    static final String SCHEMA="task259_api_"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("FLYWAY_TEST_URL")+"?currentSchema="+SCHEMA);
        r.add("spring.datasource.username", () -> System.getenv("FLYWAY_TEST_USER"));
        r.add("spring.datasource.password", () -> System.getenv("FLYWAY_TEST_PASSWORD"));
        r.add("spring.flyway.schemas", () -> SCHEMA);
        r.add("spring.flyway.default-schema", () -> SCHEMA);
        r.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }
    @AfterAll static void cleanup() throws Exception {
        try(var c=DriverManager.getConnection(System.getenv("FLYWAY_TEST_URL"),System.getenv("FLYWAY_TEST_USER"),System.getenv("FLYWAY_TEST_PASSWORD"));var s=c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");
        }
    }
    @Autowired WebApplicationContext web;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired AuthService auth;
    @Autowired DataContextService contexts;
    @Autowired ApiKeyCryptoService crypto;
    @Autowired NexonApiGateway gateway;
    @MockitoBean(name="syncExecutor") java.util.concurrent.Executor executor;
    @MockitoBean NexonOpenApiClient nexon;
    @MockitoBean RefreshTokenRedisService redis;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean KakaoOAuthClient kakao;
    @MockitoBean(name="cacheManager") org.springframework.cache.CacheManager caches;
    MockMvc mvc;
    ObjectMapper json=new ObjectMapper();
    Map<String,String> refreshStore;
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE users CASCADE");
        mvc=MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        refreshStore=new ConcurrentHashMap<>();
        doAnswer(i -> {refreshStore.put(i.getArgument(0)+":"+i.getArgument(1),i.getArgument(2));return null;}).when(redis).save(anyLong(),anyString(),anyString(),any());
        when(redis.find(anyLong(),anyString())).thenAnswer(i -> Optional.ofNullable(refreshStore.get(i.getArgument(0)+":"+i.getArgument(1))));
        doAnswer(i -> {refreshStore.remove(i.getArgument(0)+":"+i.getArgument(1));return null;}).when(redis).delete(anyLong(),anyString());
        doAnswer(i -> new OAuthUserInfo(OAuthProvider.KAKAO,i.getArgument(0),null,"카카오")).when(kakao).getUserInfo(anyString());
        when(caches.getCacheNames()).thenReturn(List.of());
        when(nexon.verifyAccounts(anyLong(),anyString())).thenAnswer(i -> {
            String key=i.getArgument(1);
            if(key.equals("invalid"))throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,"API_KEY_INVALID","무효 키");
            if(key.equals("missing"))throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"NEXON_ACCOUNT_UNIDENTIFIABLE","계정 식별 불가");
            String account=key.startsWith("account-b")?"B":key.startsWith("account-c")?"C":"A";
            return new NexonAccountCharacters(Set.of(account),List.of(new NexonCharacterSummary(account+"-ocid","캐릭터"+account,"루나","비숍",280)));
        });
    }
    record Session(String access,String refresh) {}
    Session session(MvcResult result,boolean registration) throws Exception {
        var node=json.readTree(result.getResponse().getContentAsString());
        String access=(registration?node.path("auth"):node).path("accessToken").asText();
        Cookie fresh=Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("refreshToken")&&!c.getValue().isBlank()).findFirst().orElseThrow();
        assertThat(fresh.getPath()).isEqualTo("/api/v1");
        assertThat(fresh.isHttpOnly()).isTrue();
        return new Session(access,fresh.getValue());
    }
    Session social(String code)throws Exception {
        return session(mvc.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\"}")).andExpect(status().isOk()).andReturn(),false);
    }
    Session direct(String key)throws Exception {
        return session(mvc.perform(post("/api/v1/auth/nexon-apikey").contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\""+key+"\"}")).andExpect(status().isOk()).andReturn(),false);
    }
    Session register(Session s,String key)throws Exception {
        return session(mvc.perform(post("/api/v1/api-key").header("Authorization","Bearer "+s.access()).cookie(new Cookie("refreshToken",s.refresh())).contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\""+key+"\"}")).andExpect(status().isOk()).andReturn(),true);
    }
    JwtAuthentication principal(Session s){return tokens.parseAccessToken(s.access());}
    String characters(Session s)throws Exception {
        return mvc.perform(get("/api/v1/characters").header("Authorization","Bearer "+s.access())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    void selectWorld(Session s,String world)throws Exception {
        mvc.perform(put("/api/v1/characters/world-selection").header("Authorization","Bearer "+s.access()).contentType(MediaType.APPLICATION_JSON).content("{\"worldNames\":[\""+world+"\"]}")).andExpect(status().isOk());
    }
    @Test void directFirstAndSocialRegistrationShareCharacterPreferencesAndWorlds()throws Exception {
        Session direct=direct("account-a-1");selectWorld(direct,"루나");
        Session linked=register(social("social-1"),"account-a-1");
        assertThat(principal(linked).userId()).isEqualTo(principal(direct).userId());
        assertThat(principal(linked).identityUserId()).isNotEqualTo(principal(direct).identityUserId());
        assertThat(characters(linked)).isEqualTo(characters(direct));
        mvc.perform(get("/api/v1/characters/world-selection").header("Authorization","Bearer "+linked.access())).andExpect(jsonPath("$.worldNames[0]").value("루나"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters",Long.class)).isEqualTo(1);
    }
    @Test void socialFirstAndSubsequentDirectLoginPreserveExistingData()throws Exception {
        Session linked=register(social("social-1"),"account-a-1");selectWorld(linked,"루나");
        Session direct=direct("account-a-1");
        assertThat(principal(direct).contextId()).isEqualTo(principal(linked).contextId());
        assertThat(characters(direct)).isEqualTo(characters(linked));
        mvc.perform(get("/api/v1/characters/world-selection").header("Authorization","Bearer "+direct.access())).andExpect(jsonPath("$.worldNames[0]").value("루나"));
    }
    @Test void differentKeysOfSameVerifiedAccountShareOneDatasetAndPreserveBothCredentials()throws Exception {
        Session first=direct("account-a-1"),second=direct("account-a-2");
        assertThat(principal(first).contextId()).isEqualTo(principal(second).contextId());
        assertThat(principal(first).credentialId()).isNotEqualTo(principal(second).credentialId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys",Long.class)).isEqualTo(2);
        assertThat(characters(first)).isEqualTo(characters(second));
    }
    @Test void switchingChangesOnlyCurrentRefreshSessionAndPreviousDatasetRemainsSelectable()throws Exception {
        Session first=register(social("social-1"),"account-a-1");selectWorld(first,"루나");
        Session other=social("social-1");
        Session switched=register(first,"account-b-1");
        assertThat(principal(switched).contextId()).isNotEqualTo(principal(first).contextId());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken",first.refresh()))).andExpect(status().isUnauthorized());
        var refreshed=mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken",other.refresh()))).andExpect(status().isOk()).andReturn();
        assertThat(tokens.parseAccessToken(json.readTree(refreshed.getResponse().getContentAsString()).path("accessToken").asText()).contextId()).isEqualTo(principal(first).contextId());
        mvc.perform(get("/api/v1/auth/data-contexts").header("Authorization","Bearer "+switched.access())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        var selected=mvc.perform(post("/api/v1/auth/data-context").header("Authorization","Bearer "+switched.access()).cookie(new Cookie("refreshToken",switched.refresh())).contentType(MediaType.APPLICATION_JSON).content("{\"credentialId\":"+principal(first).credentialId()+"}")).andExpect(status().isOk()).andReturn();
        Session back=session(selected,false);
        assertThat(principal(back).contextId()).isEqualTo(principal(first).contextId());
        mvc.perform(get("/api/v1/characters/world-selection").header("Authorization","Bearer "+back.access())).andExpect(jsonPath("$.worldNames[0]").value("루나"));
    }
    @Test void secondKakaoAccountConflictRollsBackConnectionAndPreservesCurrentSession()throws Exception {
        register(social("social-1"),"account-a-1");Session second=social("social-2");
        long links=jdbc.queryForObject("SELECT count(*) FROM user_data_contexts",Long.class);
        mvc.perform(post("/api/v1/api-key").header("Authorization","Bearer "+second.access()).contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"account-a-1\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONTEXT_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_data_contexts",Long.class)).isEqualTo(links);
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken",second.refresh()))).andExpect(status().isOk());
    }
    @Test void missingAccountIdReturns422WithoutMutatingKeyOrData()throws Exception {
        Session s=register(social("social-1"),"account-a-1");
        var before=jdbc.queryForList("SELECT * FROM user_api_keys ORDER BY id");
        mvc.perform(post("/api/v1/api-key").header("Authorization","Bearer "+s.access()).contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"missing\"}")).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForList("SELECT * FROM user_api_keys ORDER BY id")).isEqualTo(before);
        assertThat(characters(s)).contains("캐릭터A");
    }
    @Test void invalidNewDirectKeyDoesNotLeaveAnOrphanUserOrDataset()throws Exception {
        long users=jdbc.queryForObject("SELECT count(*) FROM users",Long.class);
        mvc.perform(post("/api/v1/auth/nexon-apikey").contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"invalid\"}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Long.class)).isEqualTo(users);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_contexts",Long.class)).isZero();
    }
    @Test void deletingSelectedKeyPreservesDataAndDoesNotFallBackToAnotherCredential()throws Exception {
        Session first=direct("account-a-1"),second=direct("account-a-2");String before=characters(first);
        mvc.perform(delete("/api/v1/api-key").header("Authorization","Bearer "+first.access())).andExpect(status().isNoContent());
        assertThat(characters(first)).isEqualTo(before);assertThat(characters(second)).isEqualTo(before);
        mvc.perform(get("/api/v1/api-key/status").header("Authorization","Bearer "+first.access())).andExpect(jsonPath("$.registered").value(false));
        try(var scope=CredentialScope.use(principal(first).credentialId())) {
            assertThatThrownBy(() -> gateway.getWithStoredKey(principal(first).userId(),"https://external.invalid",NexonRequestMode.REALTIME)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.getCode()).isEqualTo("API_KEY_NOT_REGISTERED"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_context_accounts",Long.class)).isEqualTo(1);
    }
    @Test void concurrentDirectConnectionsOfSameAccountConvergeWithoutDuplicateCharacters()throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Future<AuthService.LoginResult> a=pool.submit(() -> {start.await();return auth.loginWithNexonApiKey("account-a-1");});
            Future<AuthService.LoginResult> b=pool.submit(() -> {start.await();return auth.loginWithNexonApiKey("account-a-2");});
            start.countDown();
            assertThat(tokens.parseAccessToken(a.get(15,TimeUnit.SECONDS).response().accessToken()).contextId()).isEqualTo(tokens.parseAccessToken(b.get(15,TimeUnit.SECONDS).response().accessToken()).contextId());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys",Long.class)).isEqualTo(2);
    }
    Session unlink(Session s,long contextId)throws Exception {
        return session(mvc.perform(delete("/api/v1/auth/data-contexts/"+contextId+"/link").header("Authorization","Bearer "+s.access()).cookie(new Cookie("refreshToken",s.refresh()))).andExpect(status().isOk()).andReturn(),false);
    }
    @Test void unlinkingDatasetOwnedByKakaoKeepsRecordsAndAllowsAnotherKakaoToConnect()throws Exception {
        Session first=register(social("social-1"),"account-a-1");selectWorld(first,"루나");
        long context=principal(first).contextId();
        Session after=unlink(first,context);
        // The Kakao identity keeps its own, now empty dataset; the records moved to a dedicated key owner.
        assertThat(principal(after).userId()).isEqualTo(principal(first).identityUserId());
        assertThat(characters(after)).doesNotContain("캐릭터A");
        mvc.perform(get("/api/v1/auth/data-contexts").header("Authorization","Bearer "+after.access())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // Records stay, now owned by a dedicated key-owner instead of the Kakao identity.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters WHERE user_id=?",Long.class,principal(first).identityUserId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys WHERE user_id=?",Long.class,principal(first).identityUserId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_context_accounts a JOIN data_contexts c ON c.id=a.context_id WHERE c.kakao_user_id IS NOT NULL",Long.class)).isZero();
        // The same Kakao login still works and a different Kakao account can now take the dataset with its data.
        social("social-1");
        Session other=register(social("social-2"),"account-a-1");
        assertThat(characters(other)).contains("캐릭터A");
        mvc.perform(get("/api/v1/characters/world-selection").header("Authorization","Bearer "+other.access())).andExpect(jsonPath("$.worldNames[0]").value("루나"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters",Long.class)).isEqualTo(1);
    }
    @Test void unlinkingCurrentKeyOwnedDatasetSwitchesToRemainingDataset()throws Exception {
        Session a=register(social("social-1"),"account-a-1");
        Session b=register(a,"account-b-1");
        long contextA=principal(a).contextId(),contextB=principal(b).contextId();
        Session after=unlink(b,contextB);
        assertThat(principal(after).contextId()).isEqualTo(contextA);
        mvc.perform(get("/api/v1/auth/data-contexts").header("Authorization","Bearer "+after.access())).andExpect(jsonPath("$.length()").value(1));
        assertThat(characters(after)).contains("캐릭터A").doesNotContain("캐릭터B");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters",Long.class)).isEqualTo(2);
        Session other=register(social("social-2"),"account-b-1");
        assertThat(characters(other)).contains("캐릭터B");
    }
    @Test void unlinkingNonCurrentDatasetKeepsTheCurrentSessionDataset()throws Exception {
        Session a=register(social("social-1"),"account-a-1");
        Session b=register(a,"account-b-1");
        Session after=unlink(b,principal(a).contextId());
        assertThat(principal(after).contextId()).isEqualTo(principal(b).contextId());
        mvc.perform(get("/api/v1/auth/data-contexts").header("Authorization","Bearer "+after.access())).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void directKeyLoginCannotUnlinkAndForeignDatasetIsDenied()throws Exception {
        Session direct=direct("account-a-1");
        mvc.perform(delete("/api/v1/auth/data-contexts/"+principal(direct).contextId()+"/link").header("Authorization","Bearer "+direct.access())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DATA_CONTEXT_UNLINK_NOT_ALLOWED"));
        Session kakao=register(social("social-1"),"account-b-1");
        mvc.perform(delete("/api/v1/auth/data-contexts/"+principal(direct).contextId()+"/link").header("Authorization","Bearer "+kakao.access())).andExpect(status().isUnauthorized());
        assertThat(characters(direct)).contains("캐릭터A");
    }
    org.springframework.test.web.servlet.ResultActions deleteData(Session s,long contextId)throws Exception {
        return mvc.perform(delete("/api/v1/auth/data-contexts/"+contextId).header("Authorization","Bearer "+s.access()).cookie(new Cookie("refreshToken",s.refresh())));
    }
    long count(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}
    @Test void deletingKakaoOwnedDatasetRemovesItsKeysAndRecordsButKeepsOtherDatasets()throws Exception {
        Session a=register(social("social-1"),"account-a-1");selectWorld(a,"루나");
        Session b=register(a,"account-b-1");
        long contextA=principal(a).contextId(),contextB=principal(b).contextId();
        long kakaoUser=principal(a).identityUserId();
        var result=deleteData(b,contextA).andExpect(status().isOk()).andReturn();
        Session after=session(result,false);
        assertThat(principal(after).contextId()).isEqualTo(contextB);
        assertThat(characters(after)).contains("캐릭터B").doesNotContain("캐릭터A");
        assertThat(count("SELECT count(*) FROM characters WHERE character_name='캐릭터A'")).isZero();
        assertThat(count("SELECT count(*) FROM data_context_accounts WHERE account_id='A'")).isZero();
        assertThat(count("SELECT count(*) FROM user_selected_worlds")).isZero();
        assertThat(count("SELECT count(*) FROM user_api_keys WHERE context_id=?",contextA)).isZero();
        assertThat(count("SELECT count(*) FROM data_contexts WHERE id=?",contextA)).isZero();
        assertThat(count("SELECT count(*) FROM users WHERE id=?",kakaoUser)).isEqualTo(1);
        mvc.perform(get("/api/v1/auth/data-contexts").header("Authorization","Bearer "+after.access())).andExpect(jsonPath("$.length()").value(1));
        // The same account can be registered again from scratch.
        Session again=register(after,"account-a-1");
        assertThat(characters(again)).contains("캐릭터A");
    }
    @Test void deletingKeyOwnedDatasetRemovesItsOwnerUserAndLeavesNoBlockingRows()throws Exception {
        Session a=register(social("social-1"),"account-a-1");
        Session b=register(a,"account-b-1");
        long contextB=principal(b).contextId();
        long ownerB=principal(b).userId();
        assertThat(ownerB).isNotEqualTo(principal(b).identityUserId());
        deleteData(b,contextB).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM users WHERE id=?",ownerB)).isZero();
        assertThat(count("SELECT count(*) FROM characters WHERE user_id=?",ownerB)).isZero();
        assertThat(count("SELECT count(*) FROM data_context_accounts WHERE account_id='B'")).isZero();
        assertThat(count("SELECT count(*) FROM data_merge_provenance p WHERE p.source_user_id=?",ownerB)).isZero();
        assertThat(count("SELECT count(*) FROM data_preference_sources WHERE source_user_id=?",ownerB)).isZero();
        assertThat(count("SELECT count(*) FROM data_context_merge_audits WHERE target_user_id=? OR source_user_id=?",ownerB,ownerB)).isZero();
        assertThat(count("SELECT count(*) FROM characters WHERE character_name='캐릭터A'")).isEqualTo(1);
    }
    @Test void deletingDatasetSharedWithDirectKeyLoginRemovesBothIdentitiesAndMergedRecords()throws Exception {
        Session direct=direct("account-a-1");selectWorld(direct,"루나");
        Session kakao=register(social("social-1"),"account-a-2");
        assertThat(principal(kakao).userId()).isEqualTo(principal(direct).userId());
        long directUser=principal(direct).identityUserId();
        deleteData(kakao,principal(kakao).contextId()).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM users WHERE id=?",directUser)).isZero();
        assertThat(count("SELECT count(*) FROM characters")).isZero();
        assertThat(count("SELECT count(*) FROM user_api_keys")).isZero();
        assertThat(count("SELECT count(*) FROM data_merge_provenance")).isZero();
        assertThat(count("SELECT count(*) FROM data_preference_sources")).isZero();
        assertThat(count("SELECT count(*) FROM data_context_accounts")).isZero();
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken",direct.refresh()))).andExpect(status().isUnauthorized());
    }
    @Test void directKeyLoginDeletingItsOwnDatasetIsSignedOut()throws Exception {
        Session direct=direct("account-a-1");
        var result=deleteData(direct,principal(direct).contextId()).andExpect(status().isNoContent()).andReturn();
        assertThat(Arrays.stream(result.getResponse().getCookies()).filter(c->c.getName().equals("refreshToken")).allMatch(c->c.getMaxAge()==0)).isTrue();
        assertThat(count("SELECT count(*) FROM users")).isZero();
        assertThat(count("SELECT count(*) FROM characters")).isZero();
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken",direct.refresh()))).andExpect(status().isUnauthorized());
    }
    @Test void deletingAnotherAccountsDatasetIsDeniedWithoutChangingAnything()throws Exception {
        Session owner=register(social("social-1"),"account-a-1");
        Session intruder=register(social("social-2"),"account-b-1");
        long characters=count("SELECT count(*) FROM characters");
        deleteData(intruder,principal(owner).contextId()).andExpect(status().isUnauthorized());
        assertThat(count("SELECT count(*) FROM characters")).isEqualTo(characters);
        assertThat(characters(owner)).contains("캐릭터A");
    }
    @Test void deletingWhileSynchronizationIsRunningIsRejected()throws Exception {
        Session a=register(social("social-1"),"account-a-1");
        jdbc.update("INSERT INTO sync_jobs(user_id,job_type,status) VALUES (?, 'CHARACTER', 'STARTED')",principal(a).userId());
        deleteData(a,principal(a).contextId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONTEXT_SYNC_IN_PROGRESS"));
        assertThat(count("SELECT count(*) FROM characters")).isEqualTo(1);
    }


    @Test void dataContextListShowsCurrentAccountFirstThenRegistrationOrderWithLinkedAt()throws Exception {
        Session a=register(social("social-1"),"account-a-1");
        Session b=register(a,"account-b-1");
        Session c=register(b,"account-c-1");
        long ctxA=principal(a).contextId(),ctxB=principal(b).contextId(),ctxC=principal(c).contextId();
        var current=mvc.perform(get("/api/v1/auth/data-contexts").header("Authorization","Bearer "+c.access())).andExpect(status().isOk()).andReturn();
        var rows=json.readTree(current.getResponse().getContentAsString());
        assertThat(rows).hasSize(3);
        assertThat(List.of(rows.get(0).path("contextId").asLong(),rows.get(1).path("contextId").asLong(),rows.get(2).path("contextId").asLong())).containsExactly(ctxC,ctxA,ctxB);
        assertThat(rows.get(0).path("current").asBoolean()).isTrue();
        for(var row:rows) assertThat(row.path("linkedAt").isNull()).isFalse();
        var selected=mvc.perform(post("/api/v1/auth/data-context").header("Authorization","Bearer "+c.access()).cookie(new Cookie("refreshToken",c.refresh())).contentType(MediaType.APPLICATION_JSON).content("{\"credentialId\":"+principal(a).credentialId()+"}")).andExpect(status().isOk()).andReturn();
        Session back=session(selected,false);
        var again=json.readTree(mvc.perform(get("/api/v1/auth/data-contexts").header("Authorization","Bearer "+back.access())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(List.of(again.get(0).path("contextId").asLong(),again.get(1).path("contextId").asLong(),again.get(2).path("contextId").asLong())).containsExactly(ctxA,ctxB,ctxC);
    }
    @Test void kakaoLoginConnectsLastUsedNexonAccountAndFallsBackToRegistrationOrder()throws Exception {
        Session first=register(social("social-1"),"account-a-1");
        Session second=register(first,"account-b-1");
        long contextA=principal(first).contextId(),contextB=principal(second).contextId();
        assertThat(contextB).isNotEqualTo(contextA);
        // B was registered last, so a new Kakao login lands on B.
        assertThat(principal(social("social-1")).contextId()).isEqualTo(contextB);
        // Switching back to A makes A the last used account.
        var selected=mvc.perform(post("/api/v1/auth/data-context").header("Authorization","Bearer "+second.access()).cookie(new Cookie("refreshToken",second.refresh())).contentType(MediaType.APPLICATION_JSON).content("{\"credentialId\":"+principal(first).credentialId()+"}")).andExpect(status().isOk()).andReturn();
        assertThat(principal(session(selected,false)).contextId()).isEqualTo(contextA);
        assertThat(principal(social("social-1")).contextId()).isEqualTo(contextA);
        // A without an ACTIVE credential falls back to the next dataset.
        jdbc.update("UPDATE user_api_keys SET key_status='INVALID' WHERE context_id=?",contextA);
        assertThat(principal(social("social-1")).contextId()).isEqualTo(contextB);
    }
    @Test void kakaoLoginWithoutUsageHistoryKeepsRegistrationOrder()throws Exception {
        Session first=register(social("social-1"),"account-a-1");
        register(first,"account-b-1");
        jdbc.update("UPDATE user_data_contexts SET last_used_at=NULL");
        assertThat(principal(social("social-1")).contextId()).isEqualTo(principal(first).contextId());
    }
    @Test void reloginPrefersActiveCredentialOverOlderInvalidCredential()throws Exception {
        Session s=register(social("social-1"),"account-a-1");Session second=register(s,"account-a-2");
        jdbc.update("UPDATE user_api_keys SET key_status='INVALID' WHERE id=?",principal(s).credentialId());
        Session again=social("social-1");
        assertThat(principal(again).credentialId()).isEqualTo(principal(second).credentialId());
    }
    @Test void legacyUnmappedKeyDoesNotMergeDataWithNewDifferentAccount()throws Exception {
        Session legacy=register(social("social-1"),"account-a-1");selectWorld(legacy,"루나");
        jdbc.update("DELETE FROM data_context_accounts");
        jdbc.update("UPDATE user_api_keys SET key_fingerprint=NULL");
        Session switched=register(legacy,"account-b-1");
        assertThat(principal(switched).contextId()).isNotEqualTo(principal(legacy).contextId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters WHERE user_id=?",Long.class,principal(legacy).userId())).isEqualTo(1);
        assertThat(characters(switched)).contains("캐릭터B").doesNotContain("캐릭터A");
        assertThat(contexts.list(principal(legacy).identityUserId())).hasSize(2);
    }

    @Test void initialDatabaseSortUsesAllWorldLevelsThenKoreanNicknameAndDoesNotResort() throws Exception {
        var summaries=List.of(
            new NexonCharacterSummary("A-1","나무","루나","비숍",280),
            new NexonCharacterSummary("A-2","가람","베라","비숍",280),
            new NexonCharacterSummary("A-3","하늘","스카니아","비숍",290),
            new NexonCharacterSummary("A-4","미확인","루나","비숍",null));
        when(nexon.verifyAccounts(anyLong(),eq("account-a-1"))).thenReturn(new NexonAccountCharacters(Set.of("A"),summaries));
        Session first=direct("account-a-1");
        assertThat(jdbc.queryForList("SELECT character_name FROM characters ORDER BY sort_order,id",String.class)).containsExactly("하늘","가람","나무","미확인");
        jdbc.update("UPDATE characters SET sort_order=CASE ocid WHEN 'A-1' THEN 0 WHEN 'A-2' THEN 1 WHEN 'A-3' THEN 2 ELSE 3 END");
        var refreshed=new ArrayList<>(summaries);
        refreshed.add(new NexonCharacterSummary("A-5","새캐릭터","베라","비숍",300));
        when(nexon.verifyAccounts(anyLong(),eq("account-a-1"))).thenReturn(new NexonAccountCharacters(Set.of("A"),refreshed));
        direct("account-a-1");
        assertThat(jdbc.queryForList("SELECT character_name FROM characters ORDER BY sort_order,id",String.class)).containsExactly("나무","가람","하늘","미확인","새캐릭터");
    }
    @Test void selectingUnlinkedCredentialIsRejectedWithoutChangingCurrentDataset() throws Exception {
        Session first=direct("account-a-1"),other=direct("account-b-1");
        mvc.perform(post("/api/v1/auth/data-context").header("Authorization","Bearer "+first.access()).contentType(MediaType.APPLICATION_JSON).content("{\"credentialId\":"+principal(other).credentialId()+"}")).andExpect(status().isUnauthorized());
        assertThat(characters(first)).contains("캐릭터A").doesNotContain("캐릭터B");
    }
    @Test void invalidRegistrationDoesNotInvalidateExistingWorkingCredential() throws Exception {
        Session s=register(social("social-1"),"account-a-1");
        var before=jdbc.queryForList("SELECT * FROM user_api_keys ORDER BY id");
        mvc.perform(post("/api/v1/api-key").header("Authorization","Bearer "+s.access()).contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"invalid\"}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForList("SELECT * FROM user_api_keys ORDER BY id")).isEqualTo(before);
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken",s.refresh()))).andExpect(status().isOk());
    }
    @Test void legacyRefreshUpgradesDataContextClaimsAndOnlyOldSessionToken() throws Exception {
        Session s=direct("account-a-1");
        JwtToken legacy=tokens.createRefreshToken(principal(s).identityUserId());
        redis.save(principal(s).identityUserId(),legacy.tokenId(),legacy.value(),legacy.ttl());
        var result=mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken",legacy.value()))).andExpect(status().isOk()).andReturn();
        Session upgraded=session(result,false);
        assertThat(principal(upgraded).contextId()).isEqualTo(principal(s).contextId());
        assertThat(principal(upgraded).credentialId()).isEqualTo(principal(s).credentialId());
        assertThat(redis.find(principal(s).identityUserId(),legacy.tokenId())).isEmpty();
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken",s.refresh()))).andExpect(status().isOk());
    }
    @Test void fingerprintBackfillIsIdempotentWithoutImplicitAccountConnection() throws Exception {
        Session s=direct("account-a-1");
        jdbc.update("UPDATE user_api_keys SET key_fingerprint=NULL");
        jdbc.update("DELETE FROM data_context_accounts");
        assertThat(contexts.backfillFingerprints()).isEqualTo(1);
        assertThat(contexts.backfillFingerprints()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_context_accounts",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT key_fingerprint FROM user_api_keys",String.class)).isEqualTo(ApiKeyFingerprint.of("account-a-1"));
    }

    @Test void committedQueueRejectionPersistsFailedJobUsingIndependentTransaction() throws Exception {
        Session s=direct("account-a-1");
        doThrow(new RejectedExecutionException("test queue full")).when(executor).execute(any());
        mvc.perform(post("/api/v1/characters/sync").header("Authorization","Bearer "+s.access()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STARTED"));
        assertThat(jdbc.queryForObject("SELECT status FROM sync_jobs WHERE user_id=?",String.class,principal(s).userId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_jobs WHERE status='STARTED'",Long.class)).isZero();
        Session linked=register(social("social-1"),"account-a-1");
        assertThat(principal(linked).contextId()).isEqualTo(principal(s).contextId());
    }
    @Test void deletingKeyAlsoRemovesLegacyAliasesWhileOtherKeysAndDataRemain() throws Exception {
        Session first=direct("account-a-1"),second=direct("account-a-2");
        var p=principal(first);
        long alias=jdbc.queryForObject("INSERT INTO user_api_keys(user_id,context_id,encrypted_key,key_status,key_fingerprint) VALUES (?,?,?,'ACTIVE',NULL) RETURNING id",Long.class,p.identityUserId(),p.contextId(),crypto.encrypt("account-a-1"));
        String aliasAccess=tokens.createAccessToken(new JwtAuthentication(p.userId(),p.identityUserId(),p.contextId(),alias)).value();
        mvc.perform(delete("/api/v1/api-key").header("Authorization","Bearer "+first.access())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys WHERE id IN (?,?)",Long.class,p.credentialId(),alias)).isZero();
        mvc.perform(get("/api/v1/api-key/status").header("Authorization","Bearer "+aliasAccess)).andExpect(jsonPath("$.registered").value(false));
        try(var scope=CredentialScope.use(alias)) {
            assertThatThrownBy(() -> gateway.getWithStoredKey(p.userId(),"https://external.invalid",NexonRequestMode.REALTIME)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.getCode()).isEqualTo("API_KEY_NOT_REGISTERED"));
        }
        assertThat(characters(second)).contains("캐릭터A");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys",Long.class)).isEqualTo(1);
    }

    @Test void legacyAliasSessionMarksRepresentativeCurrentAndWhitespaceDeletionRemovesOnlySameKey() throws Exception {
        Session first=direct("account-a-1"), other=direct("account-a-2");
        var actor=principal(first);
        long alias=legacyAlias(actor,"  account-a-1  ");
        Session oldSession=new Session(tokens.createAccessToken(new JwtAuthentication(actor.userId(),actor.identityUserId(),actor.contextId(),alias)).value(), first.refresh());

        JsonNode rows=dataContexts(oldSession);

        assertThat(rows.size()).isEqualTo(2);
        assertThat(rows.findValues("current")).filteredOn(JsonNode::asBoolean).hasSize(1);
        assertThat(rows.get(0).path("credentialId").asLong()).isEqualTo(actor.credentialId());
        assertThat(rows.get(0).path("current").asBoolean()).isTrue();
        mvc.perform(delete("/api/v1/api-key").header("Authorization","Bearer "+oldSession.access())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT id FROM user_api_keys",Long.class)).containsExactly(principal(other).credentialId());
        assertThat(characters(other)).contains("캐릭터A");
    }

    @Test void fingerprintedRepresentativeWinsEvenWhenLegacyAliasHasSmallerIdAndNullsChooseSmallestId() throws Exception {
        Session first=direct("account-a-1");
        var p=principal(first);
        long newer=legacyAlias(p,"account-a-1");
        jdbc.update("UPDATE user_api_keys SET key_fingerprint=NULL WHERE id=?",p.credentialId());
        jdbc.update("UPDATE user_api_keys SET key_fingerprint=? WHERE id=?",ApiKeyFingerprint.of("account-a-1"),newer);

        assertThat(dataContexts(first).get(0).path("credentialId").asLong()).isEqualTo(newer);
        assertThat(dataContexts(first).get(0).path("current").asBoolean()).isTrue();
        jdbc.update("UPDATE user_api_keys SET key_fingerprint=NULL");
        assertThat(dataContexts(first).get(0).path("credentialId").asLong()).isEqualTo(p.credentialId());
    }

    @Test void distinctFullKeysWithSameSuffixRemainSeparateAndRepeatedRegistrationReusesRepresentative() throws Exception {
        Session first=register(social("social-1"),"account-a-same");
        Session second=register(first,"account-a-other-same");
        Session repeated=register(second,"  account-a-same  ");

        assertThat(dataContexts(repeated).size()).isEqualTo(2);
        assertThat(dataContexts(repeated).findValuesAsText("keySuffix")).containsExactly("same","same");
        assertThat(principal(repeated).credentialId()).isEqualTo(principal(first).credentialId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys",Long.class)).isEqualTo(2);
    }

    @Test void selectionAfterLegacyFingerprintClearPersistsNewFingerprintThroughPersistenceContextReload() throws Exception {
        Session first=direct("account-a-1");
        jdbc.update("UPDATE user_api_keys SET key_fingerprint=NULL");

        var selected=contexts.select(principal(first).identityUserId(),principal(first).credentialId());

        assertThat(selected.credentialId()).isEqualTo(principal(first).credentialId());
        assertThat(jdbc.queryForObject("SELECT key_fingerprint FROM user_api_keys",String.class)).isEqualTo(ApiKeyFingerprint.of("account-a-1"));
    }

    @Test void sameNicknameOnDifferentVerifiedAccountsDoesNotAuthorizeDataMerge() throws Exception {
        when(nexon.verifyAccounts(anyLong(),eq("account-b-1"))).thenReturn(new NexonAccountCharacters(Set.of("B"),
            List.of(new NexonCharacterSummary("B-ocid","캐릭터A","루나","비숍",280))));
        Session first=direct("account-a-1"), different=direct("account-b-1");

        assertThat(principal(first).contextId()).isNotEqualTo(principal(different).contextId());
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT nickname) FROM users WHERE oauth_provider='NEXON_APIKEY'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_context_accounts",Long.class)).isEqualTo(2);
        assertThat(dataContexts(first).size()).isEqualTo(1);
    }

    @Test void savedSocialPreferencesSurviveDirectDefaultsExtraKeyMergeLogoutReloginAndRefresh() throws Exception {
        Session social=social("social-1");
        long id=contextCharacter(social,"A-ocid","캐릭터A","루나",280,false,1);
        mvc.perform(put("/api/v1/characters/preferences").header("Authorization","Bearer "+social.access())
            .contentType(MediaType.APPLICATION_JSON).content("{\"characters\":[{\"characterId\":"+id+",\"favorite\":true}]}"))
            .andExpect(status().isOk());
        selectWorld(social,"루나");
        Session direct=direct("account-a-1");
        Session linked=register(social,"account-a-1");
        Session extra=direct("account-a-2");

        assertThat(principal(linked).contextId()).isEqualTo(principal(direct).contextId()).isEqualTo(principal(extra).contextId());
        assertThat(jdbc.queryForObject("SELECT is_favorite FROM characters",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT source_user_id FROM data_preference_sources WHERE setting_type='FAVORITE'",Long.class))
            .isEqualTo(principal(social).identityUserId());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+linked.access())
            .cookie(new Cookie("refreshToken",linked.refresh()))).andExpect(status().isNoContent());
        Session again=social("social-1");
        var refreshResult=mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken",again.refresh())))
            .andExpect(status().isOk()).andReturn();
        Session refreshed=new Session(json.readTree(refreshResult.getResponse().getContentAsString()).path("accessToken").asText(),again.refresh());
        assertThat(characters(refreshed)).contains("캐릭터A", "\"favorite\":true");
        mvc.perform(get("/api/v1/characters/world-selection").header("Authorization","Bearer "+refreshed.access()))
            .andExpect(jsonPath("$.worldNames[0]").value("루나"));
    }

    @Test void explicitFalseAndEmptyWorldWrittenByKakaoRetainActorDespiteDirectDataOwner() throws Exception {
        Session direct=direct("account-a-1"), kakao=register(social("social-1"),"account-a-1");
        long id=jdbc.queryForObject("SELECT id FROM characters",Long.class);
        mvc.perform(put("/api/v1/characters/preferences").header("Authorization","Bearer "+kakao.access())
            .contentType(MediaType.APPLICATION_JSON).content("{\"characters\":[{\"characterId\":"+id+",\"favorite\":false}]}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/v1/characters/world-selection").header("Authorization","Bearer "+kakao.access())
            .contentType(MediaType.APPLICATION_JSON).content("{\"worldNames\":[]}"))
            .andExpect(status().isOk());

        assertThat(principal(direct).userId()).isNotEqualTo(principal(kakao).identityUserId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_preference_sources WHERE state='EXPLICIT' AND saved_at IS NOT NULL AND source_user_id=?",Long.class,principal(kakao).identityUserId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_selected_worlds",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_favorite FROM characters",Boolean.class)).isFalse();
    }

    @Test void historicalSeparateSocialAndDirectOwnersWithFourRowsConvergeToThreeUniqueKeys() throws Exception {
        Session kakao=social("social-1"), direct=direct("account-a-1");
        long socialCharacter=contextCharacter(kakao,"A-ocid","저장캐릭터","루나",280,true,1);
        long legacy=legacyAlias(principal(kakao),"account-a-1");
        jdbc.update("INSERT INTO data_preference_sources(setting_type,record_id,source_user_id,state,saved_at) VALUES ('FAVORITE',?,?,'EXPLICIT',CURRENT_TIMESTAMP)",socialCharacter,principal(kakao).identityUserId());

        Session linked=register(kakao,"account-a-1");
        direct("account-a-2");
        direct("account-a-3");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_api_keys",Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT context_id) FROM user_api_keys",Long.class)).isEqualTo(1);
        assertThat(dataContexts(linked).size()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_favorite FROM characters",Boolean.class)).isTrue();
        var p=principal(kakao);
        Session historical=new Session(tokens.createAccessToken(new JwtAuthentication(p.userId(),p.identityUserId(),p.contextId(),legacy)).value(),kakao.refresh());
        assertThat(dataContexts(historical).findValues("current")).filteredOn(JsonNode::asBoolean).hasSize(1);
        assertThat(characters(historical)).contains("\"favorite\":true");
        assertThat(principal(linked).credentialId()).isEqualTo(principal(direct).credentialId());
    }

    long legacyAlias(JwtAuthentication p,String plaintext) {
        return jdbc.queryForObject("INSERT INTO user_api_keys(user_id,context_id,encrypted_key,key_status,key_fingerprint) VALUES (?,?,?,'ACTIVE',NULL) RETURNING id",Long.class,
            p.identityUserId(),p.contextId(),crypto.encrypt(plaintext));
    }

    JsonNode dataContexts(Session session) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/auth/data-contexts")
                .header("Authorization", "Bearer " + session.access()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    void clearCharacters(Session session) {
        jdbc.update("DELETE FROM characters WHERE user_id=?", principal(session).userId());
    }

    long contextCharacter(Session session, String ocid, String name, String world,
                          Integer level, boolean favorite, int sortOrder) {
        return jdbc.queryForObject("""
                INSERT INTO characters(user_id,ocid,character_name,world_name,character_class,
                                       character_level,character_image,is_favorite,sort_order)
                VALUES (?,?,?,?,'비숍',?,?,?,?) RETURNING id
                """, Long.class, principal(session).userId(), ocid, name, world, level,
                "https://images.example/" + ocid, favorite, sortOrder);
    }

    @Test void accountLabelUsesFirstFavoriteByDisplayOrderAcrossWorlds() throws Exception {
        Session session = direct("account-a-1");
        clearCharacters(session);
        contextCharacter(session, "high", "최고레벨", "루나", 300, false, 0);
        contextCharacter(session, "later", "늦은즐겨찾기", "베라", 290, true, 8);
        contextCharacter(session, "first", "첫즐겨찾기", "스카니아", 210, true, 3);

        JsonNode row = dataContexts(session).get(0);

        assertThat(row.path("representativeCharacterName").asText()).isEqualTo("첫즐겨찾기");
        assertThat(row.path("worldName").asText()).isEqualTo("스카니아");
        assertThat(row.path("characterClass").asText()).isEqualTo("비숍");
        assertThat(row.path("characterLevel").asInt()).isEqualTo(210);
        assertThat(row.path("characterImage").asText()).isEqualTo("https://images.example/first");
    }

    @Test void favoriteDisplayOrderTieUsesLowerCharacterId() throws Exception {
        Session session = direct("account-a-1");
        clearCharacters(session);
        long firstId = contextCharacter(session, "z-first", "하늘", "베라", 200, true, 5);
        long laterId = contextCharacter(session, "a-later", "가람", "루나", 300, true, 5);
        assertThat(firstId).isLessThan(laterId);

        JsonNode row = dataContexts(session).get(0);

        assertThat(row.path("representativeCharacterName").asText()).isEqualTo("하늘");
    }

    @Test void noFavoriteLabelUsesHighestLevelAcrossAllWorldsRegardlessOfSavedOrder() throws Exception {
        Session session = direct("account-a-1");
        clearCharacters(session);
        contextCharacter(session, "luna", "루나캐릭터", "루나", 290, false, 0);
        contextCharacter(session, "bera", "베라캐릭터", "베라", 300, false, 90);
        selectWorld(session, "루나");

        JsonNode row = dataContexts(session).get(0);

        assertThat(row.path("representativeCharacterName").asText()).isEqualTo("베라캐릭터");
        assertThat(row.path("worldName").asText()).isEqualTo("베라");
    }

    @Test void highestLevelTieUsesKoreanNicknameOrder() throws Exception {
        Session session = direct("account-a-1");
        clearCharacters(session);
        contextCharacter(session, "z", "나무", "루나", 300, false, 0);
        contextCharacter(session, "a", "가람", "베라", 300, false, 9);

        JsonNode row = dataContexts(session).get(0);

        assertThat(row.path("representativeCharacterName").asText()).isEqualTo("가람");
    }

    @Test void sameLevelAndNicknameTieUsesOcidOrder() throws Exception {
        Session session = direct("account-a-1");
        clearCharacters(session);
        contextCharacter(session, "z-ocid", "동명이인", "루나", 300, false, 0);
        contextCharacter(session, "a-ocid", "동명이인", "베라", 300, false, 9);

        JsonNode row = dataContexts(session).get(0);

        assertThat(row.path("worldName").asText()).isEqualTo("베라");
        assertThat(row.path("characterImage").asText()).isEqualTo("https://images.example/a-ocid");
    }

    @Test void unknownLevelFollowsKnownLevelEvenWhenNicknameComesFirst() throws Exception {
        Session session = direct("account-a-1");
        clearCharacters(session);
        contextCharacter(session, "unknown", "가람", "베라", null, false, 0);
        contextCharacter(session, "known", "하늘", "루나", 1, false, 9);

        JsonNode row = dataContexts(session).get(0);

        assertThat(row.path("representativeCharacterName").asText()).isEqualTo("하늘");
        assertThat(row.path("characterLevel").asInt()).isEqualTo(1);
    }

    @Test void allUnknownLevelsStillSelectByNicknameAndReturnNullLevel() throws Exception {
        Session session = direct("account-a-1");
        clearCharacters(session);
        contextCharacter(session, "z", "하늘", "루나", null, false, 0);
        contextCharacter(session, "a", "가람", "베라", null, false, 9);

        JsonNode row = dataContexts(session).get(0);

        assertThat(row.path("representativeCharacterName").asText()).isEqualTo("가람");
        assertThat(row.get("characterLevel").isNull()).isTrue();
    }

    @Test void datasetWithoutCharactersReturnsExplicitNullRepresentativeFields() throws Exception {
        Session session = direct("account-a-1");
        clearCharacters(session);

        JsonNode row = dataContexts(session).get(0);

        for (String field : List.of("representativeCharacterName", "characterClass", "worldName", "characterLevel", "characterImage")) {
            assertThat(row.has(field)).as(field + " present").isTrue();
            assertThat(row.get(field).isNull()).as(field + " null").isTrue();
        }
        assertThat(row.path("credentialId").asLong()).isEqualTo(principal(session).credentialId());
    }

    @Test void credentialsSharingDatasetExposeSameRepresentativeAndOnlySelectedCredentialIsCurrent() throws Exception {
        Session first = direct("account-a-1");
        Session second = direct("account-a-2");
        clearCharacters(first);
        contextCharacter(first, "shared", "공유대표", "베라", 300, true, 0);

        JsonNode rows = dataContexts(second);

        assertThat(rows.size()).isEqualTo(2);
        for (JsonNode row : rows) {
            assertThat(row.path("contextId").asLong()).isEqualTo(principal(first).contextId());
            assertThat(row.path("ownerUserId").asLong()).isEqualTo(principal(first).userId());
            assertThat(row.path("representativeCharacterName").asText()).isEqualTo("공유대표");
            assertThat(row.path("current").asBoolean()).isEqualTo(row.path("credentialId").asLong() == principal(second).credentialId());
        }
    }

    @Test void explicitDatasetSwitchMovesCurrentCredentialAndRetainsEachAccountLabel() throws Exception {
        Session first = register(social("social-1"), "account-a-1");
        Session second = register(first, "account-b-1");
        JsonNode before = dataContexts(second);
        assertThat(before.size()).isEqualTo(2);
        for (JsonNode row : before) {
            assertThat(row.path("current").asBoolean()).isEqualTo(row.path("credentialId").asLong() == principal(second).credentialId());
        }

        Session switched = session(mvc.perform(post("/api/v1/auth/data-context")
                .header("Authorization", "Bearer " + second.access())
                .cookie(new Cookie("refreshToken", second.refresh()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credentialId\":" + principal(first).credentialId() + "}"))
            .andExpect(status().isOk()).andReturn(), false);
        JsonNode after = dataContexts(switched);

        assertThat(after.size()).isEqualTo(2);
        for (JsonNode row : after) {
            boolean firstCredential = row.path("credentialId").asLong() == principal(first).credentialId();
            assertThat(row.path("current").asBoolean()).isEqualTo(firstCredential);
            assertThat(row.path("representativeCharacterName").asText()).isEqualTo(firstCredential ? "캐릭터A" : "캐릭터B");
        }
    }

    @Test void verifiedAtIsCredentialSpecificAcrossSeoulMidnightAndAllowsNull() throws Exception {
        Session first = direct("account-a-1");
        Session second = direct("account-a-2");
        jdbc.update("UPDATE user_api_keys SET last_verified_at=TIMESTAMP '2026-10-13 23:59:59' WHERE id=?", principal(first).credentialId());
        jdbc.update("UPDATE user_api_keys SET last_verified_at=TIMESTAMP '2026-10-14 00:00:00' WHERE id=?", principal(second).credentialId());

        JsonNode rows = dataContexts(second);

        assertThat(rows.get(0).path("lastVerifiedAt").asText()).isEqualTo("2026-10-13T23:59:59");
        assertThat(rows.get(1).path("lastVerifiedAt").asText()).isEqualTo("2026-10-14T00:00:00");
        jdbc.update("UPDATE user_api_keys SET last_verified_at=NULL WHERE id=?", principal(first).credentialId());
        assertThat(dataContexts(second).get(0).get("lastVerifiedAt").isNull()).isTrue();
    }

    @Test void contextListHidesUnlinkedDatasetAndNeverExposesRawEncryptedOrFingerprintKeys() throws Exception {
        Session first = direct("account-a-1");
        Session unrelated = direct("account-b-1");
        String storedCipher = jdbc.queryForObject("SELECT encrypted_key FROM user_api_keys WHERE id=?", String.class, principal(first).credentialId());

        JsonNode rows = dataContexts(first);

        assertThat(rows.size()).isEqualTo(1);
        JsonNode row = rows.get(0);
        assertThat(row.path("contextId").asLong()).isEqualTo(principal(first).contextId()).isNotEqualTo(principal(unrelated).contextId());
        assertThat(row.path("keySuffix").asText()).isEqualTo("-a-1");
        assertThat(row.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(rows.toString()).doesNotContain("account-a-1", "account-b-1", storedCipher, ApiKeyFingerprint.of("account-a-1"));
        assertThat(row.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "contextId", "ownerUserId", "credentialId", "keySuffix", "status", "lastVerifiedAt", "linkedAt", "current",
                "representativeCharacterName", "characterClass", "worldName", "characterLevel", "characterImage");
    }

    @Test void representativeIsRecomputedWhenFavoritesOrLevelsChangeWithoutReorderingCharacters() throws Exception {
        Session session = direct("account-a-1");
        clearCharacters(session);
        contextCharacter(session, "first", "첫번째", "루나", 290, false, 0);
        contextCharacter(session, "second", "두번째", "베라", 300, false, 1);
        assertThat(dataContexts(session).get(0).path("representativeCharacterName").asText()).isEqualTo("두번째");

        jdbc.update("UPDATE characters SET is_favorite=true WHERE ocid='first'");
        assertThat(dataContexts(session).get(0).path("representativeCharacterName").asText()).isEqualTo("첫번째");
        jdbc.update("UPDATE characters SET is_favorite=false, character_level=310 WHERE ocid='first'");
        JsonNode row = dataContexts(session).get(0);

        assertThat(row.path("representativeCharacterName").asText()).isEqualTo("첫번째");
        assertThat(row.path("characterLevel").asInt()).isEqualTo(310);
        assertThat(jdbc.queryForList("SELECT ocid FROM characters WHERE user_id=? ORDER BY sort_order,id", String.class, principal(session).userId()))
                .containsExactly("first", "second");
    }
}
