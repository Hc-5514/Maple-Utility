package com.maple.utility.service;

import com.maple.utility.util.SchedulerPeriodPolicy;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.MapleCharacter;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.SchedulerBossRecord;
import com.maple.utility.entity.SchedulerDailyRecord;
import com.maple.utility.entity.SchedulerWeeklyRecord;
import com.maple.utility.exception.ApiException;
import com.maple.utility.repository.BossMasterRepository;
import com.maple.utility.repository.SchedulerBossRecordRepository;
import com.maple.utility.repository.SchedulerDailyRecordRepository;
import com.maple.utility.repository.SchedulerWeeklyRecordRepository;
import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.security.NexonRequestMode;
import com.maple.utility.security.NexonSchedulerResponse;

@Service
public class SchedulerSyncService {

	private final NexonOpenApiClient nexonOpenApiClient;
	private final BossMasterRepository bossMasterRepository;
	private final SchedulerDailyRecordRepository dailyRecordRepository;
	private final SchedulerWeeklyRecordRepository weeklyRecordRepository;
	private final SchedulerBossRecordRepository bossRecordRepository;
	private final Clock clock;
	private final SchedulerSyncLock syncLock;

	public SchedulerSyncService(
			NexonOpenApiClient nexonOpenApiClient,
			BossMasterRepository bossMasterRepository,
			SchedulerDailyRecordRepository dailyRecordRepository,
			SchedulerWeeklyRecordRepository weeklyRecordRepository,
			SchedulerBossRecordRepository bossRecordRepository,
			Clock clock,
			SchedulerSyncLock syncLock
	) {
		this.nexonOpenApiClient = nexonOpenApiClient;
		this.bossMasterRepository = bossMasterRepository;
		this.dailyRecordRepository = dailyRecordRepository;
		this.weeklyRecordRepository = weeklyRecordRepository;
		this.bossRecordRepository = bossRecordRepository;
		this.clock = clock;
		this.syncLock = syncLock;
	}

	@Transactional
	public void syncCharacters(Long userId, List<MapleCharacter> characters) {
		for (MapleCharacter character : characters) {
			syncOneCharacter(userId, character, false, false);
		}
	}

	@Transactional
	public void syncCharactersForBatch(Long userId, List<MapleCharacter> characters) {
		for (MapleCharacter character : characters) {
			syncOneCharacter(userId, character, true, false);
		}
	}

	@Transactional
	public void syncOneCharacter(Long userId, MapleCharacter character, boolean batch, boolean force) {
		acquireLock(userId);
		NexonSchedulerResponse scheduler = batch
				? nexonOpenApiClient.getCharacterSchedulerForBatch(userId, character.getOcid())
				: nexonOpenApiClient.getCharacterScheduler(userId, character.getOcid(), force);
		persist(character, scheduler);
	}

	@Transactional
	public boolean syncHistoricalCharacter(Long userId, MapleCharacter character, LocalDate recordDate) {
		return syncHistoricalCharacter(userId, character, recordDate, NexonRequestMode.BATCH);
	}

	@Transactional
	public boolean syncHistoricalCharacter(Long userId, MapleCharacter character, LocalDate recordDate, NexonRequestMode mode) {
		return syncHistoricalCharacter(userId, character, recordDate, mode, null);
	}

	@Transactional
	public boolean syncHistoricalCharacter(Long userId, MapleCharacter character, LocalDate recordDate, NexonRequestMode mode, SchedulerHistoryFetch fetch) {
		if (!SchedulerPeriodPolicy.canFinalize(recordDate, LocalDateTime.now(clock))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NEXON_HISTORY_DATE_INVALID", "지난주 최종 조회 날짜 오류");
		}
		acquireLock(userId);
		NexonSchedulerResponse scheduler = fetch == null
				? nexonOpenApiClient.getCharacterSchedulerForDate(userId, character.getOcid(), recordDate, mode)
				: fetch.get(userId, character.getOcid(), recordDate, mode);
		if (scheduler == null) {
			return false;
		}
		LocalDate weekStart = recordDate.minusDays(6);
		if (!recordDate.equals(scheduler.date())
				|| scheduler.daily().stream().anyMatch(row -> !recordDate.equals(row.recordDate()))
				|| scheduler.weekly().stream().anyMatch(row -> !weekStart.equals(row.weekStartDate()))
				|| scheduler.boss().stream().anyMatch(row -> !recordDate.equals(row.recordDate()))) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "NEXON_HISTORY_DATE_MISMATCH", "Nexon 과거 스케줄러 조회 날짜 불일치");
		}
		if (!SchedulerPeriodPolicy.canFinalize(recordDate, LocalDateTime.now(clock))) {
			throw new ApiException(HttpStatus.CONFLICT, "NEXON_HISTORY_SCOPE_CHANGED", "동기화 중 조회 기간 변경");
		}
		persist(character, scheduler);
		return true;
	}

	private void acquireLock(Long userId) {
		if (!syncLock.tryAcquire(userId)) {
			throw new ApiException(HttpStatus.CONFLICT, "SCHEDULER_SYNC_IN_PROGRESS", "스케줄러 동기화 진행 중");
		}
	}

	private void persist(MapleCharacter character, NexonSchedulerResponse scheduler) {
		LocalDateTime syncedAt = LocalDateTime.now(clock);
		syncDailyRecords(character, scheduler.date(), scheduler.daily(), syncedAt);
		syncWeeklyRecords(character, scheduler.date(), scheduler.weekly(), syncedAt);
		syncBossRecords(character, scheduler.date(), scheduler.boss(), syncedAt);
	}

	private void syncDailyRecords(
			MapleCharacter character,
			LocalDate recordDate,
			List<NexonSchedulerResponse.Daily> dailyRecords,
			LocalDateTime syncedAt
	) {
		for (NexonSchedulerResponse.Daily source : dailyRecords) {
			if (source.recordDate() == null) {
				continue;
			}
			SchedulerDailyRecord record = dailyRecordRepository
					.findByCharacterIdAndRecordDateAndContentName(character.getId(), source.recordDate(), source.contentName())
					.orElseGet(() -> dailyRecordRepository.save(SchedulerDailyRecord.create(
							character,
							source.recordDate(),
							source.contentName(),
							source.completedCount(),
							source.totalCount(),
							syncedAt
					)));
			record.updateProgress(source.completedCount(), source.totalCount(), syncedAt);
		}
		Set<String> registeredNames = dailyRecords.stream().map(NexonSchedulerResponse.Daily::contentName).collect(Collectors.toSet());
		dailyRecordRepository.deleteAll(dailyRecordRepository.findByCharacterIdAndRecordDateOrderByIdAsc(character.getId(), recordDate)
				.stream().filter(record -> !registeredNames.contains(record.getContentName())).toList());
	}

	private void syncWeeklyRecords(
			MapleCharacter character,
			LocalDate recordDate,
			List<NexonSchedulerResponse.Weekly> weeklyRecords,
			LocalDateTime syncedAt
	) {
		for (NexonSchedulerResponse.Weekly source : weeklyRecords) {
			if (source.weekStartDate() == null) {
				continue;
			}
			SchedulerWeeklyRecord record = weeklyRecordRepository
					.findByCharacterIdAndWeekStartDateAndContentName(character.getId(), source.weekStartDate(), source.contentName())
					.orElseGet(() -> weeklyRecordRepository.save(SchedulerWeeklyRecord.create(
							character,
							source.weekStartDate(),
							source.contentName(),
							source.completed(),
							source.score(),
							syncedAt
					)));
			record.updateProgress(source.completed(), source.score(), syncedAt);
		}
		LocalDate weekStartDate = recordDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
		Set<String> registeredNames = weeklyRecords.stream().map(NexonSchedulerResponse.Weekly::contentName).collect(Collectors.toSet());
		weeklyRecordRepository.deleteAll(weeklyRecordRepository.findByCharacterIdAndWeekStartDateOrderByIdAsc(character.getId(), weekStartDate)
				.stream().filter(record -> !registeredNames.contains(record.getContentName())).toList());
	}

	private void syncBossRecords(
			MapleCharacter character,
			LocalDate recordDate,
			List<NexonSchedulerResponse.Boss> bossRecords,
			LocalDateTime syncedAt
	) {
		Set<Long> registeredBossIds = new HashSet<>();
		for (NexonSchedulerResponse.Boss source : bossRecords) {
			if (source.recordDate() == null) {
				continue;
			}
			BossMaster boss = bossMasterRepository
					.findByBossNameAndDifficulty(source.bossName(), source.difficulty())
					.orElse(null);
			if (boss == null) {
				continue;
			}
			registeredBossIds.add(boss.getId());
			SchedulerBossRecord record = bossRecordRepository
					.findByCharacterIdAndBossIdAndRecordDate(character.getId(), boss.getId(), source.recordDate())
					.orElseGet(() -> bossRecordRepository.save(SchedulerBossRecord.create(
							character,
							boss,
							source.recordDate(),
							source.resetPeriod(),
							source.completed(),
							syncedAt
					)));
			if (record.getResetPeriod() != ResetPeriod.MONTHLY || record.getSyncedAt() != null) {
				record.updateProgress(source.completed(), syncedAt);
			}
		}
		bossRecordRepository.deleteAll(bossRecordRepository.findByCharacterIdAndRecordDateOrderByBoss_SortOrderAscIdAsc(character.getId(), recordDate)
				.stream().filter(record -> record.getResetPeriod() != ResetPeriod.MONTHLY || record.getSyncedAt() != null)
				.filter(record -> !registeredBossIds.contains(record.getBoss().getId())).toList());
	}
}
