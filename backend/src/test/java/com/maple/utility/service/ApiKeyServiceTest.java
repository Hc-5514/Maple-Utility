package com.maple.utility.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import com.maple.utility.dto.response.*;
import com.maple.utility.entity.*;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.UserApiKeyRepository;
import com.maple.utility.security.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {
    @Mock UserApiKeyRepository keys;
    @Mock ApiKeyCryptoService crypto;
    @Mock DataContextService contexts;
    @Mock CharacterSyncService characters;
    @Mock AuthService auth;
    ApiKeyService service;
    JwtAuthentication principal = new JwtAuthentication(2L,1L,20L,30L);
    User user;
    UserApiKey key;
    @BeforeEach void setup() {
        service = new ApiKeyService(keys,crypto,contexts,characters,auth);
        user=User.create(OAuthProvider.KAKAO,"social",null,"이름");
        ReflectionTestUtils.setField(user,"id",1L);
        key=UserApiKey.create(user,"encrypted",LocalDateTime.of(2026,7,14,21,0));
        key.assignContext(20L,"fingerprint");
        ReflectionTestUtils.setField(key,"id",30L);
    }
    void selected() {
        when(keys.findById(30L)).thenReturn(Optional.of(key));
        when(contexts.canonical(20L)).thenReturn(new DataContextService.Context(20L,2L,1L,null));
    }
    @Test void registrationSyncsCanonicalOwnerAndReturnsNewSession() {
        when(contexts.user(1L)).thenReturn(user);
        when(contexts.user(2L)).thenReturn(user);
        var summary = new NexonCharacterSummary("ocid","닉네임","루나","비숍",280);
        when(contexts.connect(user,"raw-key")).thenReturn(new DataContextService.Connection(principal,key,new NexonAccountCharacters(Set.of("account"),List.of(summary))));
        var refresh=new JwtToken("refresh","refresh-id",Duration.ofDays(7));
        var response=AuthResponse.bearer("access",1800,AuthUserResponse.from(user,false));
        when(auth.switchSession(principal,"old-refresh")).thenReturn(new AuthService.LoginResult(response,refresh));
        when(crypto.decrypt("encrypted")).thenReturn("raw-key");
        var result=service.register(principal,"raw-key","old-refresh");
        assertThat(result.response().registered()).isTrue();
        assertThat(result.response().auth().accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo(refresh);
        verify(characters).syncSummaries(user,List.of(summary));
    }
    @Test void failedValidationDoesNotSyncOrReplaceSession() {
        when(contexts.user(1L)).thenReturn(user);
        when(contexts.connect(user,"invalid")).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"API_KEY_INVALID","무효 키"));
        assertThatThrownBy(() -> service.register(principal,"invalid",null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(keys,crypto,characters,auth);
    }
    @Test void statusReturnsUnregisteredWhenSessionHasNoCredential() {
        assertThat(service.status(new JwtAuthentication(1L)).registered()).isFalse();
        verifyNoInteractions(keys,crypto);
    }
    @Test void deletedCredentialRemainsUnregisteredRatherThanChoosingAnother() {
        when(keys.findById(30L)).thenReturn(Optional.empty());
        assertThat(service.status(principal).registered()).isFalse();
        verify(keys,never()).findByUserId(2L);
    }
    @Test void statusReturnsOnlySuffixAndKoreaTimestampForEveryStatus() throws Exception {
        selected();
        when(crypto.decrypt("encrypted")).thenReturn("sensitive-key-AB12");
        for (var status:ApiKeyStatus.values()) {
            ReflectionTestUtils.setField(key,"keyStatus",status);
            var result=service.status(principal);
            assertThat(result.keyStatus()).isEqualTo(status);
            assertThat(result.keySuffix()).isEqualTo("AB12");
            assertThat(result.lastVerifiedAt()).isEqualTo(LocalDateTime.of(2026,7,14,21,0));
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(result)).doesNotContain("sensitive-key","encrypted");
        }
    }
    @Test void shortKeyDoesNotExposeWholeLegacySecret() {
        selected();when(crypto.decrypt("encrypted")).thenReturn("AB12");
        assertThat(service.status(principal).keySuffix()).isNull();
    }
    @Test void mismatchedCredentialContextIsRejected() {
        when(keys.findById(30L)).thenReturn(Optional.of(key));
        when(contexts.canonical(20L)).thenReturn(new DataContextService.Context(99L,9L,null,null));
        assertThatThrownBy(() -> service.status(principal)).isInstanceOf(ApiException.class);
        verifyNoInteractions(crypto);
    }
    @Test void deleteRemovesEquivalentLegacyAliasesWhilePreservingOtherCredentials() {
        selected();
        var alias=UserApiKey.create(user,"alias-encrypted",null);
        var other=UserApiKey.create(user,"other-encrypted",null);
        when(crypto.decrypt("encrypted")).thenReturn("same-raw-key");
        when(crypto.decrypt("alias-encrypted")).thenReturn("same-raw-key");
        when(crypto.decrypt("other-encrypted")).thenReturn("different-raw-key");
        when(keys.findByContextIdOrderByIdAsc(20L)).thenReturn(List.of(key,alias,other));
        service.delete(principal);
        verify(contexts).lockConnections();verify(keys).deleteAll(List.of(key,alias));
        verify(keys,never()).deleteByUserId(2L);
    }
}
