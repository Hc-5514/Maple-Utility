package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.dto.response.SchedulerBossResponse;
import com.maple.utility.dto.response.StatsBossItemResponse;
import com.maple.utility.dto.response.PageResponse;
import com.maple.utility.dto.response.StatsCompletionDetailResponse;
import com.maple.utility.dto.response.StatsCompletionResponse;
import com.maple.utility.dto.response.StatsCrystalSummaryResponse;
import com.maple.utility.dto.response.StatsHuntingDailyResponse;
import com.maple.utility.dto.response.StatsHuntingSummaryResponse;
import com.maple.utility.entity.BossItemKind;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.StatsQueryRepository;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

	@Mock
	private CharacterRepository characterRepository;

	@Mock
	private StatsQueryRepository statsQueryRepository;

	@Mock
	private SchedulerService schedulerService;

	private StatsService statsService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		statsService = new StatsService(characterRepository, statsQueryRepository, schedulerService, clock);
	}

	private SchedulerBossResponse bossResponse(String bossName, boolean completed, Long crystalMeso) {
		return new SchedulerBossResponse(10L, "캐릭터", LocalDate.parse("2026-07-09"), 20L, bossName, "HARD", null,
				crystalMeso, "WEEKLY", completed, null, crystalMeso);
	}

	@Test
	void getHuntingStatsAggregatesDailyRecordsForAllCharacters() {
		MapleCharacter character = character(user(), 10L);
		List<StatsHuntingDailyResponse> dailyRecords = List.of(
				new StatsHuntingDailyResponse(LocalDate.parse("2026-07-14"), 100L, 2, 60),
				new StatsHuntingDailyResponse(LocalDate.parse("2026-07-15"), 300L, 4, 90)
		);
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(statsQueryRepository.findHuntingDailyStats(
				List.of(10L),
				LocalDate.parse("2026-07-01"),
				LocalDate.parse("2026-07-31")
		)).thenReturn(dailyRecords);

		StatsHuntingSummaryResponse response = statsService.getHuntingStats(
				1L,
				null,
				LocalDate.parse("2026-07-01"),
				LocalDate.parse("2026-07-31")
		);

		assertThat(response.totalMeso()).isEqualTo(400L);
		assertThat(response.totalSolErda()).isEqualTo(6);
		assertThat(response.avgDailyMeso()).isEqualTo(200L);
		assertThat(response.avgDailySolErda()).isEqualTo(3);
		assertThat(response.dailyRecords()).hasSize(2);
	}

	@Test
	void getHuntingStatsRejectsOtherUserCharacter() {
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> statsService.getHuntingStats(1L, 10L, null, null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("캐릭터 없음");
	}

	@Test
	void getHuntingStatsRejectsInvalidDateRange() {
		assertThatThrownBy(() -> statsService.getHuntingStats(
				1L,
				null,
				LocalDate.parse("2026-07-31"),
				LocalDate.parse("2026-07-01")
		))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("조회 기간 오류");
	}

	@Test
	void getCrystalStatsGroupsRowsByWeekAndBoss() {
		MapleCharacter character = character(user(), 10L);
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(schedulerService.getPeriodBosses(character, LocalDate.parse("2026-07-09"), ResetPeriod.WEEKLY)).thenReturn(List.of(
				bossResponse("스우", true, 100L),
				bossResponse("윌", true, 300L),
				bossResponse("루시드", false, 0L)
		));
		when(schedulerService.getPeriodBosses(character, LocalDate.parse("2026-07-16"), ResetPeriod.WEEKLY)).thenReturn(List.of(
				bossResponse("스우", true, 100L)
		));

		StatsCrystalSummaryResponse response = statsService.getCrystalStats(
				1L, null, LocalDate.parse("2026-07-09"), LocalDate.parse("2026-07-22"));

		assertThat(response.totalCrystalIncome()).isEqualTo(500L);
		assertThat(response.weeklyAverage()).isEqualTo(250L);
		assertThat(response.weeklyRecords()).hasSize(2);
		assertThat(response.weeklyRecords().getFirst().weekStart()).isEqualTo(LocalDate.parse("2026-07-09"));
		assertThat(response.weeklyRecords().getFirst().totalIncome()).isEqualTo(400L);
		assertThat(response.weeklyRecords().getFirst().weeklyIncome()).isEqualTo(400L);
		assertThat(response.weeklyRecords().getFirst().monthlyIncome()).isZero();
		assertThat(response.weeklyRecords().getFirst().bossDetails()).hasSize(2);
		assertThat(response.weeklyRecords().get(1).totalIncome()).isEqualTo(100L);
	}

	@Test
	void getCrystalStatsAddsMonthlyBossToWeekContainingFirstOfMonth() {
		MapleCharacter character = character(user(), 10L);
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		// 월간 기간 시작일 2026-11-01(일)은 목요일 시작 10-29 주에 합산
		when(schedulerService.getPeriodBosses(character, LocalDate.parse("2026-10-29"), ResetPeriod.WEEKLY))
				.thenReturn(List.of(bossResponse("스우", true, 100L)));
		when(schedulerService.getPeriodBosses(character, LocalDate.parse("2026-11-01"), ResetPeriod.MONTHLY))
				.thenReturn(List.of(bossResponse("검은 마법사", true, 700L)));

		StatsCrystalSummaryResponse response = statsService.getCrystalStats(
				1L, null, LocalDate.parse("2026-10-29"), LocalDate.parse("2026-11-04"));

		assertThat(response.weeklyRecords()).singleElement().satisfies(week -> {
			assertThat(week.weekStart()).isEqualTo(LocalDate.parse("2026-10-29"));
			assertThat(week.totalIncome()).isEqualTo(800L);
			assertThat(week.weeklyIncome()).isEqualTo(100L);
			assertThat(week.monthlyIncome()).isEqualTo(700L);
			assertThat(week.totalIncome()).isEqualTo(week.weeklyIncome() + week.monthlyIncome());
		});
	}

	@Test
	void getCrystalStatsKeepsMonthlyOnlyIncomeSeparateAndSkipsZeroOrUncompletedBosses() {
		MapleCharacter character = character(user(), 10L);
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(schedulerService.getPeriodBosses(character, LocalDate.parse("2026-10-29"), ResetPeriod.WEEKLY))
				.thenReturn(List.of());
		when(schedulerService.getPeriodBosses(character, LocalDate.parse("2026-11-01"), ResetPeriod.MONTHLY))
				.thenReturn(List.of(bossResponse("검은 마법사", true, 700L),
						bossResponse("미클리어", false, 100L), bossResponse("체크 해제", true, 0L),
						bossResponse("미입력", true, null)));

		StatsCrystalSummaryResponse response = statsService.getCrystalStats(
				1L, null, LocalDate.parse("2026-10-29"), LocalDate.parse("2026-11-04"));

		assertThat(response.totalCrystalIncome()).isEqualTo(700L);
		assertThat(response.weeklyRecords()).singleElement().satisfies(week -> {
			assertThat(week.weeklyIncome()).isZero();
			assertThat(week.monthlyIncome()).isEqualTo(700L);
			assertThat(week.totalIncome()).isEqualTo(week.weeklyIncome() + week.monthlyIncome());
			assertThat(week.bossDetails()).hasSize(1);
		});
	}

	@Test
	void getCrystalStatsSkipsMonthlyBossWhenFirstOfMonthIsOutsideRange() {
		MapleCharacter character = character(user(), 10L);
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(schedulerService.getPeriodBosses(character, LocalDate.parse("2026-11-05"), ResetPeriod.WEEKLY))
				.thenReturn(List.of());

		StatsCrystalSummaryResponse response = statsService.getCrystalStats(
				1L, null, LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-11"));

		assertThat(response.totalCrystalIncome()).isZero();
		verify(schedulerService, never()).getPeriodBosses(
				org.mockito.ArgumentMatchers.eq(character), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(ResetPeriod.MONTHLY));
	}

	@Test
	void getCrystalStatsDefaultsToTwelveWeeksAndRejectsTooLongRange() {
		MapleCharacter character = character(user(), 10L);
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));

		assertThat(statsService.getCrystalStats(1L, null, null, null).totalCrystalIncome()).isZero();
		assertThatThrownBy(() -> statsService.getCrystalStats(
				1L, null, LocalDate.parse("2025-01-01"), LocalDate.parse("2026-07-15")))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("조회 기간 오류");
	}

	@Test
	void getBossItemStatsReturnsQueryResult() {
		MapleCharacter character = character(user(), 10L);
		List<StatsBossItemResponse> rows = List.of(
				new StatsBossItemResponse(LocalDate.parse("2026-07-14"), "캐릭터", "스우", Difficulty.HARD, "아이템", LocalDate.parse("2026-07-09"), LocalDate.parse("2026-07-15"), null)
		);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		PageResponse<StatsBossItemResponse> expected = new PageResponse<>(rows, 21, 3, 1, 10);
		when(statsQueryRepository.findBossItemStats(List.of(10L), null, null, null, null, 1, 10)).thenReturn(expected);

		PageResponse<StatsBossItemResponse> response = statsService.getBossItemStats(1L, 10L, null, null, null, null, 1, 10);

		assertThat(response).isEqualTo(expected);
	}

	@Test
	void getBossItemStatsPassesTrimmedBossNameAndIgnoresBlank() {
		MapleCharacter character = character(user(), 10L);
		PageResponse<StatsBossItemResponse> empty = new PageResponse<>(List.of(), 0, 0, 0, 10);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(statsQueryRepository.findBossItemStats(List.of(10L), "스우", null, null, null, 0, 10)).thenReturn(empty);
		when(statsQueryRepository.findBossItemStats(List.of(10L), null, null, null, null, 0, 10)).thenReturn(empty);

		assertThat(statsService.getBossItemStats(1L, 10L, " 스우 ", null, null, null, 0, 10)).isEqualTo(empty);
		assertThat(statsService.getBossItemStats(1L, 10L, "  ", null, null, null, 0, 10)).isEqualTo(empty);
	}

	@Test
	void getBossNamesReturnsCatalogOrder() {
		when(statsQueryRepository.findActiveBossNames()).thenReturn(List.of("자쿰", "매그너스", "검은 마법사"));

		assertThat(statsService.getBossNames()).containsExactly("자쿰", "매그너스", "검은 마법사");
	}

	@Test
	void getBossItemStatsPassesRandomItemKindAndRejectsOthers() {
		MapleCharacter character = character(user(), 10L);
		PageResponse<StatsBossItemResponse> empty = new PageResponse<>(List.of(), 0, 0, 0, 10);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(statsQueryRepository.findBossItemStats(List.of(10L), null, BossItemKind.RANDOM, null, null, 0, 10))
				.thenReturn(empty);

		assertThat(statsService.getBossItemStats(1L, 10L, null, BossItemKind.RANDOM, null, null, 0, 10)).isEqualTo(empty);
		when(statsQueryRepository.findBossItemStats(List.of(10L), null, BossItemKind.LUCKY, null, null, 0, 10))
				.thenReturn(empty);
		assertThat(statsService.getBossItemStats(1L, 10L, null, BossItemKind.LUCKY, null, null, 0, 10)).isEqualTo(empty);
		for (BossItemKind rejected : List.of(BossItemKind.CRYSTAL, BossItemKind.FIXED)) {
			assertThatThrownBy(() -> statsService.getBossItemStats(1L, 10L, null, rejected, null, null, 0, 10))
					.isInstanceOf(ApiException.class)
					.hasMessageContaining("아이템 종류 오류");
		}
	}

	@Test
	void getBossItemStatsRejectsInvalidPagination() {
		assertThatThrownBy(() -> statsService.getBossItemStats(1L, null, null, null, null, null, -1, 10))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("페이지 요청 오류");
		assertThatThrownBy(() -> statsService.getBossItemStats(1L, null, null, null, null, null, 0, 11))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("페이지 요청 오류");
	}

	@Test
	void getCompletionStatsCombinesDailyAndBossCompletion() {
		MapleCharacter character = character(user(), 10L);
		StatsCompletionDetailResponse daily = new StatsCompletionDetailResponse(8, 10, 80);
		StatsCompletionDetailResponse boss = new StatsCompletionDetailResponse(3, 4, 75);
		when(characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(statsQueryRepository.findDailyCompletion(List.of(10L), null, null)).thenReturn(daily);
		when(statsQueryRepository.findBossCompletion(List.of(10L), null, null)).thenReturn(boss);

		StatsCompletionResponse response = statsService.getCompletionStats(1L, null, null, null);

		assertThat(response.daily()).isEqualTo(daily);
		assertThat(response.boss()).isEqualTo(boss);
	}

	private User user() {
		User user = User.create(OAuthProvider.KAKAO, "oauth-id", "user@example.com", "nickname");
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}

	private MapleCharacter character(User user, Long id) {
		MapleCharacter character = MapleCharacter.create(user, "ocid-" + id, "캐릭터", "스카니아", "히어로", 280, 1);
		ReflectionTestUtils.setField(character, "id", id);
		character.toggleFavorite();
		return character;
	}
}
