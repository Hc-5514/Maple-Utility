package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.maple.utility.dto.request.CharacterPreferencesRequest.Preference;
import com.maple.utility.dto.response.CharacterResponse;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.security.NexonCharacterBasic;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.service.CharacterService;
import com.maple.utility.service.CharacterSyncService;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({CharacterService.class, CharacterSyncService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CharacterPreferencesPersistenceTest {
	@org.springframework.test.context.bean.override.mockito.MockitoBean
	com.maple.utility.service.DataPreferenceSourceService preferenceSources;
	@Autowired EntityManager entityManager;
	@Autowired CharacterService service;
	@Autowired CharacterSyncService sync;
	@Autowired UserRepository users;
	@Autowired PlatformTransactionManager transactionManager;
	@MockitoBean NexonOpenApiClient nexon;
	@MockitoBean CacheManager cacheManager;
	@MockitoBean Clock clock;
	private TransactionTemplate transaction;
	private Long userId;
	private Long firstId;
	private Long secondId;

	@BeforeEach
	void setUp() {
		transaction = new TransactionTemplate(transactionManager);
		transaction.executeWithoutResult(status -> {
			for (String table : List.of("users", "characters")) {
				for (String column : List.of("created_at", "updated_at")) {
					entityManager.createNativeQuery("ALTER TABLE " + table + " ALTER COLUMN " + column
							+ " SET DEFAULT CURRENT_TIMESTAMP").executeUpdate();
				}
			}
			User user = User.create(OAuthProvider.KAKAO, "preferences-user", null, "사용자");
			entityManager.persist(user);
			MapleCharacter first = MapleCharacter.create(user, "first", "첫째", "루나", "히어로", 280, 4);
			MapleCharacter second = MapleCharacter.create(user, "second", "둘째", "베라", "히어로", 260, 7);
			entityManager.persist(first);
			entityManager.persist(second);
			entityManager.flush();
			userId = user.getId();
			firstId = first.getId();
			secondId = second.getId();
		});
	}

	@AfterEach
	void cleanUp() {
		transaction.executeWithoutResult(status -> {
			entityManager.createQuery("delete from MapleCharacter c where c.user.id = :id").setParameter("id", userId).executeUpdate();
			entityManager.createQuery("delete from User u where u.id = :id").setParameter("id", userId).executeUpdate();
		});
	}

	@Test
	void savedPreferencesPersistAndFavoritesFollowGlobalOrder() {
		service.updatePreferences(userId, List.of(new Preference(secondId, true), new Preference(firstId, false)));
		assertThat(service.getCharacters(userId)).extracting(CharacterResponse::id).containsExactly(secondId, firstId);
		assertThat(service.getCharacters(userId)).extracting(CharacterResponse::sortOrder).containsExactly(1, 2);
		assertThat(service.getFavoriteCharacters(userId)).extracting(CharacterResponse::id).containsExactly(secondId);
	}

	@Test
	void failedTransactionRollsBackAllPreferencesWithoutEvictingCache() {
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			service.updatePreferences(userId, List.of(new Preference(secondId, true), new Preference(firstId, true)));
			entityManager.flush();
			throw new IllegalStateException("저장 실패");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(service.getCharacters(userId)).extracting(CharacterResponse::sortOrder).containsExactly(4, 7);
		assertThat(service.getFavoriteCharacters(userId)).isEmpty();
		verifyNoInteractions(cacheManager);
	}

	@Test
	void concurrentBasicSyncKeepsCommittedPreferences() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch externalFetched = new CountDownLatch(1);
		when(nexon.getCharacterBasic(userId, "first")).thenAnswer(invocation -> {
			externalFetched.countDown();
			return new NexonCharacterBasic("first", "변경", "루나", "히어로", 281, "image", "길드");
		});
		CompletableFuture<Void> save = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
			users.findByIdForUpdate(userId).orElseThrow();
			entityManager.find(MapleCharacter.class, firstId).updatePreferences(true, 9);
			locked.countDown();
			try {
				if (!externalFetched.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("조회 대기 초과");
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
		}));
		assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<Boolean> refresh = CompletableFuture.supplyAsync(() -> sync.syncCharacterBasic(userId,
				new NexonCharacterSummary("first", "변경", "루나", "히어로", 281)));
		save.get(5, TimeUnit.SECONDS);
		assertThat(refresh.get(5, TimeUnit.SECONDS)).isTrue();
		CharacterResponse first = service.getCharacters(userId).stream().filter(item -> item.id().equals(firstId)).findFirst().orElseThrow();
		assertThat(first.favorite()).isTrue();
		assertThat(first.sortOrder()).isEqualTo(9);
		assertThat(first.characterName()).isEqualTo("변경");
	}

	@Test
	void concurrentFavoriteAddsCannotExceedEighteenAcrossWorlds() throws Exception {
		transaction.executeWithoutResult(status -> {
			User user = entityManager.find(User.class, userId);
			for (int index = 0; index < 17; index++) {
				MapleCharacter favorite = MapleCharacter.create(user, "favorite-" + index, "선택" + index,
						index % 2 == 0 ? "루나" : "베라", "히어로", 280, index + 10);
				favorite.toggleFavorite();
				entityManager.persist(favorite);
			}
		});
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
			users.findByIdForUpdate(userId).orElseThrow();
			locked.countDown();
			try {
				if (!secondStarted.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("추가 요청 대기 초과");
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			return service.toggleFavorite(userId, firstId).favorite();
		}));
		assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> {
			secondStarted.countDown();
			try {
				service.toggleFavorite(userId, secondId);
				return "ADDED";
			} catch (ApiException exception) {
				return exception.getCode();
			}
		});
		assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
		assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("FAVORITE_LIMIT_EXCEEDED");
		assertThat(service.getFavoriteCharacters(userId)).hasSize(18);
		assertThat(service.getCharacters(userId)).filteredOn(item -> item.id().equals(secondId))
				.extracting(CharacterResponse::favorite).containsExactly(false);
	}
}
