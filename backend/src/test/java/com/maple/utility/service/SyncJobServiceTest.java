package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import org.springframework.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;

import com.maple.utility.config.RedisCacheNames;
import com.maple.utility.dto.response.SyncJobResponse;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.exception.NexonApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.SyncJobRepository;
import com.maple.utility.repository.UserRepository;
import com.maple.utility.security.NexonCharacterSummary;
import com.maple.utility.security.NexonOpenApiClient;

@ExtendWith(MockitoExtension.class)
class SyncJobServiceTest {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T06:00:00Z"), ZoneId.of("Asia/Seoul"));
	@Mock SyncJobRepository jobs;
	@Mock UserRepository users;
	@Mock CharacterRepository characters;
	@Mock CharacterSyncService characterSync;
	@Mock SchedulerSyncService schedulerSync;
	@Mock NexonOpenApiClient nexon;
	@Mock CacheManager cacheManager;
	@Mock JdbcTemplate jdbcTemplate;
	private SyncJobService service;

	@BeforeEach
	void setUp() {
		service = new SyncJobService(jobs, users, characters, characterSync, schedulerSync,
				nexon, cacheManager, jdbcTemplate, Runnable::run, CLOCK);
	}

	@Test
	void characterJobPreservesSummaryBeforeDetailAndTracksSkippedCharacter() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		var summary = new NexonCharacterSummary("invalid-ocid", "캐릭터", "스카니아", "히어로", 280);
		var job = job(11L, "CHARACTER", "STARTED", 0, 0, null);
		when(jobs.start(1L, "CHARACTER")).thenReturn(new SyncJobRepository.StartResult(job, true));
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(nexon.getCharacters(1L)).thenReturn(List.of(summary));
		when(characterSync.syncCharacterBasic(1L, summary, 1)).thenReturn(false);

		service.startCharacters(1L);

		var order = org.mockito.Mockito.inOrder(characterSync, jobs);
		order.verify(characterSync).syncSummaries(user, List.of(summary));
		order.verify(jobs).setTotal(11L, 1);
		order.verify(characterSync).syncCharacterBasic(1L, summary, 1);
		order.verify(jobs).advance(11L, true);
		order.verify(jobs).complete(11L, LocalDateTime.now(CLOCK));
	}

	@Test
	void activeSchedulerJobIsReused() {
		var job = job(22L, "SCHEDULER", "STARTED", 2, 1, null);
		when(jobs.active(1L, "SCHEDULER")).thenReturn(Optional.of(job));

		assertThat(service.startScheduler(1L, true, false)).isEqualTo(job);
		verify(jobs, never()).start(1L, "SCHEDULER");
	}

	@Test
	void startReturnsBeforeSlowCharacterFetch() {
		List<Runnable> queued = new ArrayList<>();
		var deferredService = new SyncJobService(jobs, users, characters, characterSync, schedulerSync,
				nexon, cacheManager, jdbcTemplate, queued::add, CLOCK);
		var job = job(33L, "CHARACTER", "STARTED", 0, 0, null);
		when(jobs.start(1L, "CHARACTER")).thenReturn(new SyncJobRepository.StartResult(job, true));

		assertThat(deferredService.startCharacters(1L)).isEqualTo(job);
		assertThat(queued).hasSize(1);
		verify(nexon, never()).getCharacters(1L);
	}

	@Test
	void interruptedJobsAreFailedOnStartup() {
		service.failInterruptedJobs();
		verify(jobs).failInterrupted(LocalDateTime.now(CLOCK));
	}

	@Test
	void recentSchedulerJobWithUnchangedFavoritesIsReused() {
		var recent = job(22L, "SCHEDULER", "COMPLETED", 1, 1, LocalDateTime.now(CLOCK).minusMinutes(2));
		when(jobs.latestCompleted(1L, "SCHEDULER")).thenReturn(Optional.of(recent));
		when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.startsWith("SELECT COUNT"),
				org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(1);
		when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.startsWith("SELECT EXISTS"),
				org.mockito.ArgumentMatchers.eq(Boolean.class), org.mockito.ArgumentMatchers.eq(1L),
				org.mockito.ArgumentMatchers.eq(recent.completedAt()))).thenReturn(false);

		assertThat(service.startScheduler(1L, false, false)).isEqualTo(recent);
		verify(jobs, never()).start(1L, "SCHEDULER");
	}

	@Test
	void schedulerJobPreservesNexonForbiddenReason() {
		MapleCharacter character = character();
		when(jobs.start(1L, "SCHEDULER")).thenReturn(new SyncJobRepository.StartResult(job(44L, "SCHEDULER", "STARTED", 0, 0, null), true));
		when(characters.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		doThrow(new NexonApiException(HttpStatus.FORBIDDEN, "NEXON_ACCESS_DENIED", "접근 권한 없음", "OPENAPI00002", "Access Denied"))
				.when(schedulerSync).syncOneCharacter(1L, character, false, true);

		service.startScheduler(1L, true, false);

		verify(jobs).fail(44L, "NEXON_ACCESS_DENIED (OPENAPI00002)", LocalDateTime.now(CLOCK));
		verify(jobs, never()).complete(44L, LocalDateTime.now(CLOCK));
	}

	@Test
	void completedSchedulerJobEvictsSummaryAndDetailCaches() {
		MapleCharacter character = character();
		Cache cache = org.mockito.Mockito.mock(Cache.class);
		when(jobs.start(1L, "SCHEDULER")).thenReturn(new SyncJobRepository.StartResult(job(45L, "SCHEDULER", "STARTED", 0, 0, null), true));
		when(characters.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(cacheManager.getCache(RedisCacheNames.SCHEDULER)).thenReturn(cache);

		service.startScheduler(1L, true, false);

		verify(cache).evict("summary:1:null");
		verify(cache).evict("summary:1:2026-10-03");
		verify(cache).evict("daily:1:10:2026-10-03");
		verify(cache).evict("weekly:1:10:2026-09-28");
		verify(cache).evict("boss:1:10:null");
		verify(cache).evict("guild:1:10:2026-10-03");
		verify(jobs).complete(45L, LocalDateTime.now(CLOCK));
	}

	private MapleCharacter character() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		MapleCharacter character = MapleCharacter.create(user, "ocid", "캐릭터", "스카니아", "히어로", 280, 1);
		ReflectionTestUtils.setField(character, "id", 10L);
		return character;
	}

	private SyncJobResponse job(Long id, String type, String status, int total, int completed, LocalDateTime completedAt) {
		return new SyncJobResponse(id, type, status, total, completed, 0, null,
				LocalDateTime.now(CLOCK).minusMinutes(3), completedAt);
	}
}
