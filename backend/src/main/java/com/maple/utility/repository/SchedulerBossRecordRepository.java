package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.entity.SchedulerBossRecord;

public interface SchedulerBossRecordRepository extends JpaRepository<SchedulerBossRecord, Long> {

	Optional<SchedulerBossRecord> findByCharacterIdAndBossIdAndRecordDate(Long characterId, Long bossId, LocalDate recordDate);

	List<SchedulerBossRecord> findByCharacterIdAndRecordDateOrderByBoss_SortOrderAscIdAsc(Long characterId, LocalDate recordDate);

	List<SchedulerBossRecord> findByCharacterIdAndRecordDateAndResetPeriodOrderByBoss_SortOrderAscIdAsc(
			Long characterId,
			LocalDate recordDate,
			ResetPeriod resetPeriod
	);

	List<SchedulerBossRecord> findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(
			List<Long> characterIds,
			LocalDate recordDate,
			ResetPeriod resetPeriod
	);
}
