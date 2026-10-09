package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

	@Query("""
			select record
			from SchedulerBossRecord record
			where record.character.id = :characterId
			  and record.recordDate between :startDate and :endDate
			  and record.resetPeriod = :resetPeriod
			  and record.recordDate = (
					select max(latest.recordDate)
					from SchedulerBossRecord latest
					where latest.character.id = record.character.id
					  and latest.boss.id = record.boss.id
					  and latest.resetPeriod = record.resetPeriod
					  and latest.recordDate between :startDate and :endDate
				  )
			order by record.boss.sortOrder asc, record.id asc
			""")
	List<SchedulerBossRecord> findLatestByCharacterIdAndRecordDateBetweenAndResetPeriod(
			Long characterId,
			LocalDate startDate,
			LocalDate endDate,
			ResetPeriod resetPeriod
	);

	List<SchedulerBossRecord> findByCharacterIdInAndRecordDateAndResetPeriodOrderByCharacterIdAscBoss_SortOrderAscIdAsc(
			List<Long> characterIds,
			LocalDate recordDate,
			ResetPeriod resetPeriod
	);
}
