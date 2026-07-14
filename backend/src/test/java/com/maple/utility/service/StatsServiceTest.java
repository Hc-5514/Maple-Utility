package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.maple.utility.dto.response.StatsBossItemResponse;
import com.maple.utility.dto.response.StatsCompletionDetailResponse;
import com.maple.utility.dto.response.StatsCompletionResponse;
import com.maple.utility.dto.response.StatsCrystalSummaryResponse;
import com.maple.utility.dto.response.StatsHuntingDailyResponse;
import com.maple.utility.dto.response.StatsHuntingSummaryResponse;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.OAuthProvider;
import com.maple.utility.entity.User;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.StatsQueryRepository;
import com.maple.utility.repository.StatsQueryRepository.CrystalIncomeRow;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

	@Mock
	private CharacterRepository characterRepository;

	@Mock
	private StatsQueryRepository statsQueryRepository;

	private StatsService statsService;

	@BeforeEach
	void setUp() {
		statsService = new StatsService(characterRepository, statsQueryRepository);
	}

	@Test
	void getHuntingStatsAggregatesDailyRecordsForAllCharacters() {
		MapleCharacter character = character(user(), 10L);
		List<StatsHuntingDailyResponse> dailyRecords = List.of(
				new StatsHuntingDailyResponse(LocalDate.parse("2026-07-14"), 100L, 2, 60),
				new StatsHuntingDailyResponse(LocalDate.parse("2026-07-15"), 300L, 4, 90)
		);
		when(characterRepository.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
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
		when(characterRepository.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
		when(statsQueryRepository.findCompletedBossCrystalRows(List.of(10L), null, null)).thenReturn(List.of(
				new CrystalIncomeRow(LocalDate.parse("2026-07-14"), "스우", Difficulty.HARD, 100L),
				new CrystalIncomeRow(LocalDate.parse("2026-07-15"), "스우", Difficulty.HARD, 100L),
				new CrystalIncomeRow(LocalDate.parse("2026-07-21"), "윌", Difficulty.HARD, 300L)
		));

		StatsCrystalSummaryResponse response = statsService.getCrystalStats(1L, null, null, null);

		assertThat(response.totalCrystalIncome()).isEqualTo(500L);
		assertThat(response.weeklyAverage()).isEqualTo(250L);
		assertThat(response.weeklyRecords()).hasSize(2);
		assertThat(response.weeklyRecords().getFirst().weekStart()).isEqualTo(LocalDate.parse("2026-07-13"));
		assertThat(response.weeklyRecords().getFirst().totalIncome()).isEqualTo(200L);
		assertThat(response.weeklyRecords().getFirst().bossDetails().getFirst().income()).isEqualTo(200L);
	}

	@Test
	void getBossItemStatsReturnsQueryResult() {
		MapleCharacter character = character(user(), 10L);
		List<StatsBossItemResponse> rows = List.of(
				new StatsBossItemResponse(LocalDate.parse("2026-07-14"), "캐릭터", "스우", Difficulty.HARD, "아이템")
		);
		when(characterRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(character));
		when(statsQueryRepository.findBossItemStats(List.of(10L), null, null)).thenReturn(rows);

		List<StatsBossItemResponse> response = statsService.getBossItemStats(1L, 10L, null, null);

		assertThat(response).isEqualTo(rows);
	}

	@Test
	void getCompletionStatsCombinesDailyAndBossCompletion() {
		MapleCharacter character = character(user(), 10L);
		StatsCompletionDetailResponse daily = new StatsCompletionDetailResponse(8, 10, 80);
		StatsCompletionDetailResponse boss = new StatsCompletionDetailResponse(3, 4, 75);
		when(characterRepository.findByUserIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(character));
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
		return character;
	}
}
