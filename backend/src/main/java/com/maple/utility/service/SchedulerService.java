package com.maple.utility.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import com.maple.utility.dto.request.ManualBossRecordSaveRequest;
import com.maple.utility.dto.response.BossCandidateResponse;
import com.maple.utility.dto.response.SchedulerBossDetailResponse;
import com.maple.utility.dto.response.SchedulerBossResponse;
import com.maple.utility.dto.response.SchedulerCharacterSummaryResponse;
import com.maple.utility.dto.response.SchedulerDailyResponse;
import com.maple.utility.dto.response.SchedulerSummaryResponse;
import com.maple.utility.dto.response.SchedulerWeeklyResponse;
import com.maple.utility.dto.response.TaskSummary;
import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.BossPeriodSelection;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.SchedulerBossRecord;
import com.maple.utility.entity.SchedulerDailyRecord;
import com.maple.utility.entity.SchedulerWeeklyRecord;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.BossPeriodEntryRepository;
import com.maple.utility.repository.BossPeriodSelectionRepository;
import com.maple.utility.repository.BossItemAcquisitionRepository;
import com.maple.utility.repository.CharacterRepository;
import com.maple.utility.repository.SchedulerBossRecordRepository;
import com.maple.utility.repository.SchedulerDailyRecordRepository;
import com.maple.utility.repository.SchedulerWeeklyRecordRepository;
import com.maple.utility.repository.SyncJobRepository;

@Service
public class SchedulerService {

	private final CharacterRepository characterRepository;
	private final SchedulerDailyRecordRepository dailyRecordRepository;
	private final SchedulerWeeklyRecordRepository weeklyRecordRepository;
	private final SchedulerBossRecordRepository bossRecordRepository;
	private final BossMasterRepository bossMasterRepository;
	private final BossPeriodSelectionRepository bossPeriodSelectionRepository;
	private final BossPeriodEntryRepository bossPeriodEntryRepository;
	private final BossItemAcquisitionRepository bossItemAcquisitionRepository;
	private final SchedulerSyncService schedulerSyncService;
	private final SyncJobRepository syncJobRepository;
	private final Clock clock;

	public SchedulerService(
			CharacterRepository characterRepository,
			SchedulerDailyRecordRepository dailyRecordRepository,
			SchedulerWeeklyRecordRepository weeklyRecordRepository,
			SchedulerBossRecordRepository bossRecordRepository,
			BossMasterRepository bossMasterRepository,
			BossPeriodSelectionRepository bossPeriodSelectionRepository,
			BossPeriodEntryRepository bossPeriodEntryRepository,
			BossItemAcquisitionRepository bossItemAcquisitionRepository,
			SchedulerSyncService schedulerSyncService,
			SyncJobRepository syncJobRepository,
			Clock clock
	) {
		this.characterRepository = characterRepository;
		this.dailyRecordRepository = dailyRecordRepository;
		this.weeklyRecordRepository = weeklyRecordRepository;
		this.bossRecordRepository = bossRecordRepository;
		this.bossMasterRepository = bossMasterRepository;
		this.bossPeriodSelectionRepository = bossPeriodSelectionRepository;
		this.bossPeriodEntryRepository = bossPeriodEntryRepository;
		this.bossItemAcquisitionRepository = bossItemAcquisitionRepository;
		this.schedulerSyncService = schedulerSyncService;
		this.syncJobRepository = syncJobRepository;
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
			return new SchedulerSummaryResponse(List.of(), latestCompletedAt(userId));
		}

		List<SchedulerDailyRecord> dailyRecords = dailyRecordRepository.findByCharacterIdInAndRecordDateOrderByCharacterIdAscIdAsc(characterIds, targetDate);
		List<SchedulerWeeklyRecord> weeklyRecords = weeklyRecordRepository.findByCharacterIdInAndWeekStartDateOrderByCharacterIdAscIdAsc(characterIds, weekStartDate);
		List<SchedulerBossRecord> weeklyBossRecords = bossRecordRepository.findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(characterIds, targetDate, ResetPeriod.WEEKLY);
		List<SchedulerBossRecord> monthlyBossRecords = bossRecordRepository.findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(characterIds, targetDate, ResetPeriod.MONTHLY);

		Map<Long, List<SchedulerDailyRecord>> dailyByChar = dailyRecords.stream().collect(Collectors.groupingBy(r -> r.getCharacter().getId()));
		Map<Long, List<SchedulerWeeklyRecord>> weeklyByChar = weeklyRecords.stream().collect(Collectors.groupingBy(r -> r.getCharacter().getId()));

		List<SchedulerCharacterSummaryResponse> characters = favoriteCharacters.stream()
				.map(character -> {
					Long id = character.getId();
					List<SchedulerDailyRecord> daily = dailyByChar.getOrDefault(id, List.of());
					List<SchedulerWeeklyRecord> weekly = weeklyByChar.getOrDefault(id, List.of());
					List<SchedulerBossResponse> weeklyBoss = bossResponsesForPeriod(character, targetDate, ResetPeriod.WEEKLY);
					List<SchedulerBossResponse> monthlyBoss = bossResponsesForPeriod(character, targetDate, ResetPeriod.MONTHLY);
					return new SchedulerCharacterSummaryResponse(
							id,
							character.getCharacterName(),
							character.getCharacterLevel(),
							character.getCharacterClass(),
							character.getCharacterImage(),
							character.getWorldName(),
							new TaskSummary((int) daily.stream().filter(r -> r.getCompletedCount() == r.getTotalCount()).count(), daily.size()),
							new TaskSummary((int) weekly.stream().filter(SchedulerWeeklyRecord::isCompleted).count(), weekly.size()),
							new TaskSummary((int) weeklyBoss.stream().filter(SchedulerBossResponse::isCompleted).count(), weeklyBoss.size()),
							new TaskSummary((int) monthlyBoss.stream().filter(SchedulerBossResponse::isCompleted).count(), monthlyBoss.size())
					);
				})
				.toList();

		LocalDateTime recordSyncedAt = Stream.of(
						dailyRecords.stream().map(SchedulerDailyRecord::getSyncedAt),
						weeklyRecords.stream().map(SchedulerWeeklyRecord::getSyncedAt),
						weeklyBossRecords.stream().map(SchedulerBossRecord::getSyncedAt),
						monthlyBossRecords.stream().map(SchedulerBossRecord::getSyncedAt)
				).flatMap(s -> s)
				.filter(t -> t != null)
				.max(Comparator.naturalOrder())
				.orElse(null);
		LocalDateTime jobCompletedAt = latestCompletedAt(userId);
		LocalDateTime syncedAt = recordSyncedAt == null ? jobCompletedAt
				: jobCompletedAt == null || recordSyncedAt.isAfter(jobCompletedAt) ? recordSyncedAt : jobCompletedAt;

		return new SchedulerSummaryResponse(characters, syncedAt);
	}

	private LocalDateTime latestCompletedAt(Long userId) {
		return syncJobRepository.latestCompleted(userId, "SCHEDULER")
				.map(job -> job.completedAt()).orElse(null);
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

	@Cacheable(cacheNames = RedisCacheNames.SCHEDULER, key = "'boss:' + #userId + ':' + #characterId + ':' + #weeklyDate + ':' + #monthlyDate + ':' + #legacyDate")
	@Transactional(readOnly = true)
	public SchedulerBossDetailResponse getBoss(
			Long userId,
			Long characterId,
			LocalDate weeklyDate,
			LocalDate monthlyDate,
			LocalDate legacyDate
	) {
		MapleCharacter character = findCharacter(userId, characterId);
		LocalDate weeklyTargetDate = dateOrToday(weeklyDate != null ? weeklyDate : legacyDate);
		LocalDate monthlyTargetDate = dateOrToday(monthlyDate != null ? monthlyDate : legacyDate);
		return new SchedulerBossDetailResponse(
				bossResponsesForPeriod(character, weeklyTargetDate, ResetPeriod.WEEKLY),
				bossResponsesForPeriod(character, monthlyTargetDate, ResetPeriod.MONTHLY)
		);
	}

	@Transactional(readOnly = true)
	public List<BossCandidateResponse> getBossCandidates(Long userId, Long characterId, ResetPeriod resetPeriod) {
		findCharacter(userId, characterId);
		return bossMasterRepository.findByResetPeriodAndActiveTrueOrderBySortOrderAsc(resetPeriod).stream()
				.map(BossCandidateResponse::from)
				.toList();
	}

	@CacheEvict(cacheNames = RedisCacheNames.SCHEDULER, allEntries = true)
	@Transactional
	public List<SchedulerBossResponse> addBossesToPeriod(Long userId, Long characterId, ManualBossRecordSaveRequest request) {
		MapleCharacter character = findCharacter(userId, characterId);
		LocalDate start = periodStart(request.periodStart(), request.resetPeriod());
		if (request.bossIds().stream().distinct().count() != request.bossIds().size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_BOSS_SELECTION", "중복 보스 선택");
		}
		Map<Long, BossMaster> candidates = bossMasterRepository
				.findByResetPeriodAndActiveTrueOrderBySortOrderAsc(request.resetPeriod()).stream()
				.collect(Collectors.toMap(BossMaster::getId, boss -> boss));
		if (request.bossIds().stream().anyMatch(id -> !candidates.containsKey(id))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOSS_SELECTION", "활성 보스 후보 아님");
		}
		Map<Long, BossPeriodSelection> selections = bossPeriodSelectionRepository
				.findByCharacter_IdAndPeriodStartAndResetPeriod(characterId, start, request.resetPeriod()).stream()
				.collect(Collectors.toMap(selection -> selection.getBoss().getId(), selection -> selection));
		List<SchedulerBossResponse> existing = bossResponsesForPeriod(character, start, request.resetPeriod());
		var existingBossIds = existing.stream().map(SchedulerBossResponse::bossId).collect(Collectors.toSet());
		long newCount = request.bossIds().stream()
				.filter(id -> !existingBossIds.contains(id))
				.count();
		if (request.resetPeriod() == ResetPeriod.WEEKLY
				&& existing.size() + newCount > 12) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "WEEKLY_BOSS_LIMIT_EXCEEDED", "주간 보스 선택 한도 초과");
		}
		for (Long bossId : request.bossIds()) {
			BossPeriodSelection selection = selections.get(bossId);
			if (selection == null) {
				bossPeriodSelectionRepository.save(BossPeriodSelection.create(character, candidates.get(bossId), start, true));
			} else {
				selection.setVisible(true);
			}
		}
		return bossResponsesForPeriod(character, start, request.resetPeriod());
	}

	@CacheEvict(cacheNames = RedisCacheNames.SCHEDULER, allEntries = true)
	@Transactional
	public void hideBossFromPeriod(Long userId, Long characterId, Long bossId, LocalDate date, ResetPeriod resetPeriod) {
		MapleCharacter character = findCharacter(userId, characterId);
		BossMaster boss = bossMasterRepository.findById(bossId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOSS_NOT_FOUND", "보스 없음"));
		if (boss.getResetPeriod() != resetPeriod) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOSS_SELECTION", "보스 주기 불일치");
		}
		LocalDate start = periodStart(date, resetPeriod);
		LocalDate end = resetPeriod == ResetPeriod.WEEKLY ? start.plusDays(6) : start.plusMonths(1).minusDays(1);
		BossPeriodSelection selection = bossPeriodSelectionRepository
				.findByCharacter_IdAndBoss_IdAndPeriodStart(characterId, bossId, start)
				.orElseGet(() -> bossPeriodSelectionRepository.save(BossPeriodSelection.create(character, boss, start, false)));
		selection.setVisible(false);
		bossItemAcquisitionRepository.deleteAll(bossItemAcquisitionRepository
				.findByCharacter_IdAndBossDropItem_Boss_IdAndAcquiredDateBetween(characterId, bossId, start, end));
		bossPeriodEntryRepository.deleteByCharacter_IdAndBoss_IdAndPeriodStart(characterId, bossId, start);
	}

	@CacheEvict(cacheNames = RedisCacheNames.SCHEDULER, allEntries = true)
	@Transactional
	public List<SchedulerBossResponse> saveManualBossRecords(
			Long userId,
			Long characterId,
			ManualBossRecordSaveRequest request
	) {
		MapleCharacter character = findCharacter(userId, characterId);
		LocalDate periodStart = periodStart(request.periodStart(), request.resetPeriod());
		if (request.resetPeriod() == ResetPeriod.WEEKLY && request.bossIds().size() > 12) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "WEEKLY_BOSS_LIMIT_EXCEEDED", "주간 보스 선택 한도 초과");
		}
		if (request.bossIds().stream().distinct().count() != request.bossIds().size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_BOSS_SELECTION", "중복 보스 선택");
		}
		if (!bossRecordsForPeriod(character.getId(), periodStart, request.resetPeriod()).isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "BOSS_RECORD_EXISTS", "선택 기간 보스 기록 존재");
		}

		Map<Long, BossMaster> bossesById = bossMasterRepository
				.findByResetPeriodAndActiveTrueOrderBySortOrderAsc(request.resetPeriod())
				.stream()
				.collect(Collectors.toMap(BossMaster::getId, boss -> boss));
		List<BossMaster> selected = request.bossIds().stream().map(bossesById::get).toList();
		if (selected.stream().anyMatch(boss -> boss == null)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOSS_SELECTION", "활성 보스 후보 아님");
		}

		return bossRecordRepository.saveAll(selected.stream()
				.map(boss -> SchedulerBossRecord.create(character, boss, periodStart, request.resetPeriod(), true, null))
				.toList()).stream()
				.sorted(Comparator.comparing(record -> record.getBoss().getSortOrder()))
				.map(SchedulerBossResponse::from)
				.toList();
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
		MapleCharacter character = characterRepository.findByIdAndUserId(characterId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND", "캐릭터 없음"));
		if (!character.isFavorite()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_NOT_FAVORITE", "즐겨찾기 캐릭터 아님");
		}
		return character;
	}

	private LocalDate dateOrToday(LocalDate date) {
		return date == null ? LocalDate.now(clock) : date;
	}

	private LocalDate weekStartDate(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
	}

	private List<SchedulerBossRecord> bossRecordsForPeriod(Long characterId, LocalDate date, ResetPeriod resetPeriod) {
		LocalDate start = periodStart(date, resetPeriod);
		LocalDate end = resetPeriod == ResetPeriod.WEEKLY ? start.plusDays(6) : start.plusMonths(1).minusDays(1);
		return bossRecordRepository.findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(
				characterId, start, end, resetPeriod);
	}

	private List<SchedulerBossResponse> bossResponsesForPeriod(MapleCharacter character, LocalDate date, ResetPeriod resetPeriod) {
		LocalDate start = periodStart(date, resetPeriod);
		Map<Long, SchedulerBossResponse> records = new LinkedHashMap<>();
		Map<Long, BossMaster> bosses = new LinkedHashMap<>();
		for (SchedulerBossRecord record : bossRecordsForPeriod(character.getId(), date, resetPeriod)) {
			records.put(record.getBoss().getId(), SchedulerBossResponse.from(record));
			bosses.put(record.getBoss().getId(), record.getBoss());
		}
		for (BossPeriodSelection selection : bossPeriodSelectionRepository
				.findByCharacter_IdAndPeriodStartAndResetPeriod(character.getId(), start, resetPeriod)) {
			Long bossId = selection.getBoss().getId();
			if (!selection.isVisible()) {
				records.remove(bossId);
				continue;
			}
			BossMaster boss = selection.getBoss();
			bosses.put(bossId, boss);
			records.put(bossId, new SchedulerBossResponse(character.getId(), character.getCharacterName(),
					start, bossId, boss.getBossName(), boss.getDifficulty().name(), boss.getBossImage(),
					boss.getCrystalPrice(), resetPeriod.name(), selection.isCompleted(), null));
		}
		return records.values().stream()
				.sorted(Comparator.comparingInt((SchedulerBossResponse record) -> bosses.get(record.bossId()).getSortOrder())
					.thenComparing(SchedulerBossResponse::bossId))
				.toList();
	}

	private LocalDate periodStart(LocalDate date, ResetPeriod resetPeriod) {
		return resetPeriod == ResetPeriod.WEEKLY ? weekStartDate(date) : date.withDayOfMonth(1);
	}
}
