package com.maple.utility.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import com.maple.utility.entity.*;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock OAuthClient oauth;
    @Mock UserRepository users;
    @Mock JwtTokenProvider tokens;
    @Mock RefreshTokenRedisService redis;
    @Mock DataContextService contexts;
    @Mock CharacterSyncService characters;
    AuthService service;
    User identity;
    JwtAuthentication principal = new JwtAuthentication(2L, 1L, 20L, 30L);
    JwtToken access = new JwtToken("access", "access-id", Duration.ofMinutes(30));
    JwtToken refresh = new JwtToken("refresh", "refresh-id", Duration.ofDays(7));
    @BeforeEach void setup() {
        when(oauth.provider()).thenReturn(OAuthProvider.KAKAO);
        service = new AuthService(List.of(oauth), users, tokens, redis, contexts, characters);
        identity = User.create(OAuthProvider.KAKAO, "social", null, "이름");
        ReflectionTestUtils.setField(identity, "id", 1L);
    }
    void issuance() {
        when(tokens.createAccessToken(principal)).thenReturn(access);
        when(tokens.createRefreshToken(principal)).thenReturn(refresh);
    }
    @Test void socialLoginCreatesIdentityAndUsesResolvedDataOwner() {
        when(oauth.getUserInfo("code")).thenReturn(new OAuthUserInfo(OAuthProvider.KAKAO,"social",null,"이름"));
        when(users.findByOauthProviderAndOauthId(OAuthProvider.KAKAO,"social")).thenReturn(Optional.empty());
        when(users.save(any())).thenReturn(identity);
        when(contexts.defaultPrincipal(identity)).thenReturn(principal);
        issuance();
        var result = service.login(OAuthProvider.KAKAO,"code");
        assertThat(result.response().user().isNewUser()).isTrue();
        assertThat(result.response().user().id()).isEqualTo(1L);
        assertThat(result.response().expiresIn()).isEqualTo(1800);
        verify(redis).save(1L,"refresh-id","refresh",Duration.ofDays(7));
    }
    @Test void socialLoginRejectsMissingOauthIdentityBeforeDatabaseMutation() {
        when(oauth.getUserInfo("code")).thenReturn(new OAuthUserInfo(OAuthProvider.KAKAO,null,null,null));
        assertThatThrownBy(() -> service.login(OAuthProvider.KAKAO,"code")).isInstanceOf(ApiException.class);
        verifyNoInteractions(users, contexts, redis);
    }
    @Test void unsupportedProviderIsRejected() {
        assertThatThrownBy(() -> service.login(OAuthProvider.NEXON_APIKEY,"code")).isInstanceOf(ApiException.class);
        verifyNoInteractions(users, redis);
    }
    @Test void directLoginUsesVerifiedContextAndSyncsSummaryToDataOwner() {
        when(users.findByOauthProviderAndOauthId(OAuthProvider.NEXON_APIKEY,ApiKeyFingerprint.of("key"))).thenReturn(Optional.of(identity));
        User owner = User.create(OAuthProvider.NEXON_APIKEY,"owner",null,null);
        when(contexts.user(2L)).thenReturn(owner);
        var summary = new NexonCharacterSummary("ocid","닉네임","루나","비숍",280);
        when(contexts.connect(identity,"key")).thenReturn(new DataContextService.Connection(principal,null,new NexonAccountCharacters(Set.of("account"),List.of(summary))));
        when(users.save(identity)).thenReturn(identity);
        issuance();
        var result = service.loginWithNexonApiKey("  key  ");
        assertThat(result.response().user().nickname()).isEqualTo("닉네임");
        assertThat(result.response().user().isNewUser()).isFalse();
        verify(characters).syncSummaries(owner,List.of(summary));
    }
    @Test void invalidDirectLoginDoesNotIssueTokens() {
        when(users.findByOauthProviderAndOauthId(OAuthProvider.NEXON_APIKEY,ApiKeyFingerprint.of("key"))).thenReturn(Optional.of(identity));
        when(contexts.connect(identity,"key")).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"API_KEY_INVALID","무효 키"));
        assertThatThrownBy(() -> service.loginWithNexonApiKey("key")).isInstanceOf(ApiException.class);
        verifyNoInteractions(tokens,redis,characters);
    }
    @Test void blankDirectKeyIsRejectedBeforeIdentityCreation() {
        assertThatThrownBy(() -> service.loginWithNexonApiKey(" ")).isInstanceOf(ApiException.class);
        verifyNoInteractions(users,contexts,tokens);
    }
    @Test void refreshKeepsSessionSelectedDataContext() {
        when(tokens.parseRefreshToken("refresh")).thenReturn(new JwtTokenProvider.RefreshTokenClaims(1L,"refresh-id",principal));
        when(redis.find(1L,"refresh-id")).thenReturn(Optional.of("refresh"));
        when(users.findById(1L)).thenReturn(Optional.of(identity));
        when(contexts.resolve(principal)).thenReturn(principal);
        when(tokens.createAccessToken(principal)).thenReturn(access);
        assertThat(service.refresh("refresh").accessToken()).isEqualTo("access");
        verify(tokens,never()).createRefreshToken(any(JwtAuthentication.class));
    }
    @Test void refreshRejectsMismatchedStoredToken() {
        when(tokens.parseRefreshToken("refresh")).thenReturn(new JwtTokenProvider.RefreshTokenClaims(1L,"refresh-id",principal));
        when(redis.find(1L,"refresh-id")).thenReturn(Optional.of("other"));
        assertThatThrownBy(() -> service.refresh("refresh")).isInstanceOf(ApiException.class);
        verify(contexts).lockBusinessRequest();
        verify(contexts,never()).resolve(any());
    }
    @Test void switchingDeletesOnlyPreviousSessionsRefreshToken() {
        when(tokens.parseRefreshToken("previous")).thenReturn(new JwtTokenProvider.RefreshTokenClaims(1L,"previous-id",principal));
        when(users.findById(1L)).thenReturn(Optional.of(identity));
        issuance();
        service.switchSession(principal,"previous");
        verify(redis).delete(1L,"previous-id");
        verify(redis).save(1L,"refresh-id","refresh",Duration.ofDays(7));
        verifyNoMoreInteractions(redis);
    }
    @Test void logoutDeletesSpecificRefreshToken() {
        when(tokens.parseRefreshToken("refresh")).thenReturn(new JwtTokenProvider.RefreshTokenClaims(1L,"refresh-id",principal));
        service.logout("refresh");
        verify(redis).delete(1L,"refresh-id");
    }
}
