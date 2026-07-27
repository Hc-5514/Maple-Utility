package com.maple.utility.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.config.RedisCacheNames;
import com.maple.utility.dto.response.SchedulerBossDetailResponse;
import com.maple.utility.dto.response.SchedulerBossResponse;
import com.maple.utility.dto.response.SchedulerCharacterSummaryResponse;
import com.maple.utility.dto.response.SchedulerDailyResponse;
import com.maple.utility.dto.response.SchedulerSummaryResponse;
import com.maple.utility.dto.response.SchedulerWeeklyResponse;
import com.maple.utility.dto.response.TaskSummary;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.SchedulerBossRecord;
import com.maple.utility.entity.SchedulerDailyRecord;
import com.maple.utility.entity.SchedulerWeeklyRecord;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.SchedulerBossRecordRepository;
import com.maple.utility.repository.SchedulerDailyRecordRepository;
import com.maple.utility.repository.SchedulerWeeklyRecordRepository;

@Service
public class SchedulerService {

	private final CharacterRepository characterRepository;
	private final SchedulerDailyRecordRepository dailyRecordRepository;
	private final SchedulerWeeklyRecordRepository weeklyRecordRepository;
	private final SchedulerBossRecordRepository bossRecordRepository;
	private final SchedulerSyncService schedulerSyncService;
	private final Clock clock;

	public SchedulerService(
			CharacterRepository characterRepository,
			SchedulerDailyRecordRepository dailyRecordRepository,
			SchedulerWeeklyRecordRepository weeklyRecordRepository,
			SchedulerBossRecordRepository bossRecordRepository,
			SchedulerSyncService schedulerSyncService,
			Clock clock
	) {
		this.characterRepository = characterRepository;
		this.dailyRecordRepository = dailyRecordRepository;
		this.weeklyRecordRepository = weeklyRecordRepository;
		this.bossRecordRepository = bossRecordRepository;
		this.schedulerSyncService = schedulerSyncService;
		this.clock = clock;
	}

	@Cacheable(cacheNames = RedisCacheNames.SCHEDULER, key = "'summary:' + #userId + ':' + #date")
	@Transactional(readOnly = true)
	public SchedulerSummaryResponse getSummary(Long userId, LocalDate date) {
		LocalDate targetDate = dateOrToday(date);
		LocalDate weekStartDate = weekStartDate(targetDate);
		List<MapleCharacter> favoriteCharacters = characterRepository.findByUserIdAndFavoriteTrueOrderBySortOrderAscIdAsc(userId);
		List<Long> characterIds = favoriteCharacters.stream()
				.map(MapleCharacter::getId)
				.toList();
		if (characterIds.isEmpty()) {
			return new SchedulerSummaryResponse(List.of(), null);
		}

		List<SchedulerDailyRecord> dailyRecords = dailyRecordRepository.findByCharacterIdInAndRecordDateOrderByCharacterIdAscIdAsc(characterIds, targetDate);
		List<SchedulerWeeklyRecord> weeklyRecords = weeklyRecordRepository.findByCharacterIdInAndWeekStartDateOrderByCharacterIdAscIdAsc(characterIds, weekStartDate);
		List<SchedulerBossRecord> weeklyBossRecords = bossRecordRepository.findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(characterIds, targetDate, ResetPeriod.WEEKLY);
		List<SchedulerBossRecord> monthlyBossRecords = bossRecordRepository.findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(characterIds, targetDate, ResetPeriod.MONTHLY);

		Map<Long, List<SchedulerDailyRecord>> dailyByChar = dailyRecords.stream().collect(Collectors.groupingBy(r -> r.getCharacter().getId()));
		Map<Long, List<SchedulerWeeklyRecord>> weeklyByChar = weeklyRecords.stream().collect(Collectors.groupingBy(r -> r.getCharacter().getId()));
		Map<Long, List<SchedulerBossRecord>> weeklyBossByChar = weeklyBossRecords.stream().collect(Collectors.groupingBy(r -> r.getCharacter().getId()));
		Map<Long, List<SchedulerBossRecord>> monthlyBossByChar = monthlyBossRecords.stream().collect(Collectors.groupingBy(r -> r.getCharacter().getId()));

		List<SchedulerCharacterSummaryResponse> characters = favoriteCharacters.stream()
				.map(character -> {
					Long id = character.getId();
					List<SchedulerDailyRecord> daily = dailyByChar.getOrDefault(id, List.of());
					List<SchedulerWeeklyRecord> weekly = weeklyByChar.getOrDefault(id, List.of());
					List<SchedulerBossRecord> weeklyBoss = weeklyBossByChar.getOrDefault(id, List.of());
					List<SchedulerBossRecord> monthlyBoss = monthlyBossByChar.getOrDefault(id, List.of());
					return new SchedulerCharacterSummaryResponse(
							id,
							character.getCharacterName(),
							character.getCharacterLevel(),
							character.getCharacterClass(),
							character.getCharacterImage(),
							character.getWorldName(),
							new TaskSummary((int) daily.stream().filter(r -> r.getCompletedCount() == r.getTotalCount()).count(), daily.size()),
							new TaskSummary((int) weekly.stream().filter(SchedulerWeeklyRecord::isCompleted).count(), weekly.size()),
							new TaskSummary((int) weeklyBoss.stream().filter(SchedulerBossRecord::isCompleted).count(), weeklyBoss.size()),
							new TaskSummary((int) monthlyBoss.stream().filter(SchedulerBossRecord::isCompleted).count(), monthlyBoss.size())
					);
				})
				.toList();

		LocalDateTime syncedAt = Stream.of(
						dailyRecords.stream().map(SchedulerDailyRecord::getSyncedAt),
						weeklyRecords.stream().map(SchedulerWeeklyRecord::getSyncedAt),
						weeklyBossRecords.stream().map(SchedulerBossRecord::getSyncedAt),
						monthlyBossRecords.stream().map(SchedulerBossRecord::getSyncedAt)
				).flatMap(s -> s)
				.filter(t -> t != null)
				.max(Comparator.naturalOrder())
				.orElse(null);

		return new SchedulerSummaryResponse(characters, syncedAt);
	}

	@Cacheable(cacheNames = RedisCacheNames.SCHEDULER, key = "'daily:' + #userId + ':' + #characterId + ':' + #date")
	@Transactional(readOnly = true)
	public List<SchedulerDailyResponse> getDaily(Long userId, Long characterId, LocalDate date) {
		MapleCharacter character = findCharacter(userId, characterId);
		return dailyRecordRepository.findByCharacterIdAndRecordDateOrderByIdAsc(character.getId(), dateOrToday(date)).stream()
				.map(SchedulerDailyResponse::from)
				.toList();
	}

	@Cacheable(cacheNames = RedisCacheNames.SCHEDULER, key = "'weekly:' + #userId + ':' + #characterId + ':' + #date")
	@Transactional(readOnly = true)
	public List<SchedulerWeeklyResponse> getWeekly(Long userId, Long characterId, LocalDate date) {
		MapleCharacter character = findCharacter(userId, characterId);
		return weeklyRecordRepository.findByCharacterIdAndWeekStartDateOrderByIdAsc(character.getId(), weekStartDate(dateOrToday(date))).stream()
				.map(SchedulerWeeklyResponse::from)
				.toList();
	}

	@Cacheable(cacheNames = RedisCacheNames.SCHEDULER, key = "'boss:' + #userId + ':' + #characterId + ':' + #date")
	@Transactional(readOnly = true)
	public SchedulerBossDetailResponse getBoss(Long userId, Long characterId, LocalDate date) {
		MapleCharacter character = findCharacter(userId, characterId);
		LocalDate targetDate = dateOrToday(date);
		return new SchedulerBossDetailResponse(
				bossRecordRepository
						.findByCharacterIdAndRecordDateAndResetPeriodOrderByBoss_SortOrderAscIdAsc(character.getId(), targetDate, ResetPeriod.WEEKLY)
						.stream()
						.map(SchedulerBossResponse::from)
						.toList(),
				bossRecordRepository
						.findByCharacterIdAndRecordDateAndResetPeriodOrderByBoss_SortOrderAscIdAsc(character.getId(), targetDate, ResetPeriod.MONTHLY)
						.stream()
						.map(SchedulerBossResponse::from)
						.toList()
		);
	}

	@Cacheable(cacheNames = RedisCacheNames.SCHEDULER, key = "'guild:' + #userId + ':' + #characterId + ':' + #date")
	@Transactional(readOnly = true)
	public List<SchedulerWeeklyResponse> getGuild(Long userId, Long characterId, LocalDate date) {
		return getWeekly(userId, characterId, date).stream()
				.filter(response -> response.contentName().contains("길드") || response.contentName().toLowerCase().contains("guild"))
				.toList();
	}

	@CacheEvict(cacheNames = RedisCacheNames.SCHEDULER, allEntries = true)
	@Transactional
	public SchedulerSummaryResponse sync(Long userId) {
		List<MapleCharacter> characters = characterRepository.findByUserIdOrderBySortOrderAscIdAsc(userId);
		schedulerSyncService.syncCharacters(userId, characters);
		return getSummary(userId, LocalDate.now(clock));
	}

	private MapleCharacter findCharacter(Long userId, Long characterId) {
		return characterRepository.findByIdAndUserId(characterId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND", "캐릭터 없음"));
	}

	private LocalDate dateOrToday(LocalDate date) {
		return date == null ? LocalDate.now(clock) : date;
	}

	private LocalDate weekStartDate(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}
}
