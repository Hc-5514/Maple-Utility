package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.SchedulerDailyRecord;

public interface SchedulerDailyRecordRepository extends JpaRepository<SchedulerDailyRecord, Long> {

	Optional<SchedulerDailyRecord> findByCharacterIdAndRecordDateAndContentName(Long characterId, LocalDate recordDate, String contentName);

	List<SchedulerDailyRecord> findByCharacterIdAndRecordDateOrderByIdAsc(Long characterId, LocalDate recordDate);

	List<SchedulerDailyRecord> findByCharacterIdInAndRecordDateOrderByCharacterIdAscIdAsc(List<Long> characterIds, LocalDate recordDate);
}
