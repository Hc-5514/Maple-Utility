package com.maple.utility.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.response.StatsBossItemResponse;
import com.maple.utility.dto.response.StatsCompletionResponse;
import com.maple.utility.dto.response.StatsCrystalBossDetailResponse;
import com.maple.utility.dto.response.StatsCrystalSummaryResponse;
import com.maple.utility.dto.response.StatsCrystalWeeklyResponse;
import com.maple.utility.dto.response.StatsHuntingDailyResponse;
import com.maple.utility.dto.response.StatsHuntingSummaryResponse;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.StatsQueryRepository;
import com.maple.utility.repository.StatsQueryRepository.CrystalIncomeRow;

@Service
public class StatsService {

	private final CharacterRepository characterRepository;
	private final StatsQueryRepository statsQueryRepository;

	public StatsService(
			CharacterRepository characterRepository,
			StatsQueryRepository statsQueryRepository
	) {
		this.characterRepository = characterRepository;
		this.statsQueryRepository = statsQueryRepository;
	}

	@Transactional(readOnly = true)
	public StatsHuntingSummaryResponse getHuntingStats(
			Long userId,
			Long characterId,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		validateDateRange(dateFrom, dateTo);
		List<Long> characterIds = characterIds(userId, characterId);
		List<StatsHuntingDailyResponse> dailyRecords = statsQueryRepository.findHuntingDailyStats(
				characterIds,
				dateFrom,
				dateTo
		);
		long totalMeso = dailyRecords.stream()
				.mapToLong(StatsHuntingDailyResponse::mesoEarned)
				.sum();
		int totalSolErda = dailyRecords.stream()
				.mapToInt(StatsHuntingDailyResponse::solErdaEarned)
				.sum();
		int recordDays = dailyRecords.size();
		return new StatsHuntingSummaryResponse(
				totalMeso,
				totalSolErda,
				recordDays == 0 ? 0 : totalMeso / recordDays,
				recordDays == 0 ? 0 : totalSolErda / recordDays,
				dailyRecords
		);
	}

	@Transactional(readOnly = true)
	public StatsCrystalSummaryResponse getCrystalStats(
			Long userId,
			Long characterId,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		validateDateRange(dateFrom, dateTo);
		List<Long> characterIds = characterIds(userId, characterId);
		List<CrystalIncomeRow> rows = statsQueryRepository.findCompletedBossCrystalRows(
				characterIds,
				dateFrom,
				dateTo
		);
		Map<LocalDate, WeeklyCrystalAccumulator> weekly = new LinkedHashMap<>();
		for (CrystalIncomeRow row : rows) {
			LocalDate weekStart = weekStartDate(row.recordDate());
			weekly.computeIfAbsent(weekStart, ignored -> new WeeklyCrystalAccumulator())
					.add(row.bossName(), row.difficulty(), row.income());
		}

		List<StatsCrystalWeeklyResponse> weeklyRecords = weekly.entrySet()
				.stream()
				.map(entry -> new StatsCrystalWeeklyResponse(
						entry.getKey(),
						entry.getValue().totalIncome(),
						entry.getValue().bossDetails()
				))
				.toList();
		long totalCrystalIncome = weeklyRecords.stream()
				.mapToLong(StatsCrystalWeeklyResponse::totalIncome)
				.sum();
		return new StatsCrystalSummaryResponse(
				totalCrystalIncome,
				weeklyRecords.isEmpty() ? 0 : totalCrystalIncome / weeklyRecords.size(),
				weeklyRecords
		);
	}

	@Transactional(readOnly = true)
	public List<StatsBossItemResponse> getBossItemStats(
			Long userId,
			Long characterId,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		validateDateRange(dateFrom, dateTo);
		return statsQueryRepository.findBossItemStats(characterIds(userId, characterId), dateFrom, dateTo);
	}

	@Transactional(readOnly = true)
	public StatsCompletionResponse getCompletionStats(
			Long userId,
			Long characterId,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		validateDateRange(dateFrom, dateTo);
		List<Long> characterIds = characterIds(userId, characterId);
		return new StatsCompletionResponse(
				statsQueryRepository.findDailyCompletion(characterIds, dateFrom, dateTo),
				statsQueryRepository.findBossCompletion(characterIds, dateFrom, dateTo)
		);
	}

	private List<Long> characterIds(Long userId, Long characterId) {
		if (characterId != null) {
			MapleCharacter character = characterRepository.findByIdAndUserId(characterId, userId)
					.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND", "캐릭터 없음"));
			return List.of(character.getId());
		}
		return characterRepository.findByUserIdOrderBySortOrderAscIdAsc(userId).stream()
				.map(MapleCharacter::getId)
				.toList();
	}

	private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
		if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "조회 기간 오류");
		}
	}

	private LocalDate weekStartDate(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	private static class WeeklyCrystalAccumulator {

		private final Map<CrystalBossKey, Long> incomes = new LinkedHashMap<>();

		void add(String bossName, Difficulty difficulty, long income) {
			CrystalBossKey key = new CrystalBossKey(bossName, difficulty);
			incomes.merge(key, income, Long::sum);
		}

		long totalIncome() {
			return incomes.values()
					.stream()
					.mapToLong(Long::longValue)
					.sum();
		}

		List<StatsCrystalBossDetailResponse> bossDetails() {
			List<StatsCrystalBossDetailResponse> details = new ArrayList<>();
			incomes.forEach((key, income) -> details.add(new StatsCrystalBossDetailResponse(
					key.bossName(),
					key.difficulty(),
					income
			)));
			return details;
		}
	}

	private record CrystalBossKey(String bossName, Difficulty difficulty) {
	}
}
