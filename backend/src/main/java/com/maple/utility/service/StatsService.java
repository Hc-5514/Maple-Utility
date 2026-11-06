package com.maple.utility.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.dto.response.SchedulerBossResponse;
import com.maple.utility.dto.response.StatsBossItemResponse;
import com.maple.utility.dto.response.PageResponse;
import com.maple.utility.dto.response.StatsCompletionResponse;
import com.maple.utility.dto.response.StatsCrystalBossDetailResponse;
import com.maple.utility.dto.response.StatsCrystalSummaryResponse;
import com.maple.utility.dto.response.StatsCrystalWeeklyResponse;
import com.maple.utility.dto.response.StatsHuntingDailyResponse;
import com.maple.utility.dto.response.StatsHuntingSummaryResponse;
import com.maple.utility.entity.BossItemKind;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.StatsQueryRepository;

@Service
public class StatsService {

	private static final int DEFAULT_CRYSTAL_WEEKS = 12;
	private static final int MAX_CRYSTAL_WEEKS = 53;

	private final CharacterRepository characterRepository;
	private final StatsQueryRepository statsQueryRepository;
	private final SchedulerService schedulerService;
	private final Clock clock;

	public StatsService(
			CharacterRepository characterRepository,
			StatsQueryRepository statsQueryRepository,
			SchedulerService schedulerService,
			Clock clock
	) {
		this.characterRepository = characterRepository;
		this.statsQueryRepository = statsQueryRepository;
		this.schedulerService = schedulerService;
		this.clock = clock;
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
		LocalDate to = dateTo != null ? dateTo : LocalDate.now(clock);
		LocalDate from = dateFrom != null ? dateFrom : to.minusWeeks(DEFAULT_CRYSTAL_WEEKS);
		if (from.isAfter(to) || ChronoUnit.WEEKS.between(from, to) > MAX_CRYSTAL_WEEKS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "조회 기간 오류");
		}
		List<CrystalIncomeRow> rows = crystalIncomeRows(favoriteCharacters(userId, characterId), from, to);
		Map<LocalDate, WeeklyCrystalAccumulator> weekly = new LinkedHashMap<>();
		for (CrystalIncomeRow row : rows) {
			LocalDate weekStart = weekStartDate(row.recordDate());
			weekly.computeIfAbsent(weekStart, ignored -> new WeeklyCrystalAccumulator())
					.add(row.bossName(), row.difficulty(), row.income(), row.resetPeriod());
		}

		List<StatsCrystalWeeklyResponse> weeklyRecords = weekly.entrySet()
				.stream()
				.map(entry -> new StatsCrystalWeeklyResponse(
						entry.getKey(),
						entry.getValue().totalIncome(),
						entry.getValue().bossDetails(),
						entry.getValue().weeklyIncome,
						entry.getValue().monthlyIncome
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
	public PageResponse<StatsBossItemResponse> getBossItemStats(
			Long userId,
			Long characterId,
			String bossName,
			BossItemKind itemKind,
			LocalDate dateFrom,
			LocalDate dateTo,
			int page,
			int size
	) {
		validateDateRange(dateFrom, dateTo);
		if (page < 0 || (size != 10 && size != 20 && size != 30)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST", "페이지 요청 오류");
		}
		// LUCKY 물욕템과 기존 RANDOM 확률 보상 필터 허용
		if (itemKind != null && itemKind != BossItemKind.RANDOM && itemKind != BossItemKind.LUCKY) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ITEM_KIND", "아이템 종류 오류");
		}
		String normalizedBossName = bossName == null || bossName.isBlank() ? null : bossName.strip();
		return statsQueryRepository.findBossItemStats(
				characterIds(userId, characterId), normalizedBossName, itemKind, dateFrom, dateTo, page, size);
	}

	@Transactional(readOnly = true)
	public List<String> getBossNames() {
		return statsQueryRepository.findActiveBossNames();
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
		return favoriteCharacters(userId, characterId).stream()
				.map(MapleCharacter::getId)
				.toList();
	}

	private List<MapleCharacter> favoriteCharacters(Long userId, Long characterId) {
		if (characterId != null) {
			MapleCharacter character = characterRepository.findByIdAndUserId(characterId, userId)
					.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND", "캐릭터 없음"));
			if (!character.isFavorite()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_NOT_FAVORITE", "즐겨찾기 캐릭터 아님");
			}
			return List.of(character);
		}
		return characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(userId);
	}

	/**
	 * 클리어한 보스의 결정 수익. 보스 카드와 같은 규칙(미클리어 0, 기록 없음 1인 가격, 기록 있음 가격 / 인원, 획득 해제 0).
	 * 주간은 목요일 시작일, 월간은 1일이 조회 기간에 포함된 기간만 대상.
	 */
	private List<CrystalIncomeRow> crystalIncomeRows(List<MapleCharacter> characters, LocalDate from, LocalDate to) {
		List<CrystalIncomeRow> rows = new ArrayList<>();
		for (MapleCharacter character : characters) {
			for (LocalDate start = weekStartDate(from); !start.isAfter(to); start = start.plusWeeks(1)) {
				if (!start.isBefore(from)) {
					addCrystalRows(rows, character, start, ResetPeriod.WEEKLY);
				}
			}
			for (LocalDate start = from.withDayOfMonth(1); !start.isAfter(to); start = start.plusMonths(1)) {
				if (!start.isBefore(from)) {
					addCrystalRows(rows, character, start, ResetPeriod.MONTHLY);
				}
			}
		}
		rows.sort(Comparator.comparing(CrystalIncomeRow::recordDate));
		return rows;
	}

	private void addCrystalRows(List<CrystalIncomeRow> rows, MapleCharacter character, LocalDate start, ResetPeriod resetPeriod) {
		for (SchedulerBossResponse boss : schedulerService.getPeriodBosses(character, start, resetPeriod)) {
			long income = boss.acquiredCrystalMeso() == null ? 0 : boss.acquiredCrystalMeso();
			if (boss.isCompleted() && income > 0) {
				rows.add(new CrystalIncomeRow(start, boss.bossName(), Difficulty.valueOf(boss.difficulty()), income, resetPeriod));
			}
		}
	}

	private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
		if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "조회 기간 오류");
		}
	}

	private LocalDate weekStartDate(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
	}

	private static class WeeklyCrystalAccumulator {

		private final Map<CrystalBossKey, Long> incomes = new LinkedHashMap<>();
		private long weeklyIncome;
		private long monthlyIncome;

		void add(String bossName, Difficulty difficulty, long income, ResetPeriod resetPeriod) {
			if (resetPeriod == ResetPeriod.MONTHLY) {
				monthlyIncome += income;
			} else {
				weeklyIncome += income;
			}
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

	private record CrystalIncomeRow(LocalDate recordDate, String bossName, Difficulty difficulty, long income,
			ResetPeriod resetPeriod) {
	}

	private record CrystalBossKey(String bossName, Difficulty difficulty) {
	}
}
