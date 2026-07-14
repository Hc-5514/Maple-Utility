package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.SchedulerWeeklyRecord;

public interface SchedulerWeeklyRecordRepository extends JpaRepository<SchedulerWeeklyRecord, Long> {

	Optional<SchedulerWeeklyRecord> findByCharacterIdAndWeekStartDateAndContentName(Long characterId, LocalDate weekStartDate, String contentName);

	List<SchedulerWeeklyRecord> findByCharacterIdAndWeekStartDateOrderByIdAsc(Long characterId, LocalDate weekStartDate);

	List<SchedulerWeeklyRecord> findByCharacterIdInAndWeekStartDateOrderByCharacterIdAscIdAsc(List<Long> characterIds, LocalDate weekStartDate);
}
