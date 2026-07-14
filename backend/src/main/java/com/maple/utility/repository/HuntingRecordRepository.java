package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.maple.utility.entity.HuntingRecord;

public interface HuntingRecordRepository extends JpaRepository<HuntingRecord, Long> {

	@Query("""
			select record
			from HuntingRecord record
			where record.character.id = :characterId
			  and (:from is null or record.recordDate >= :from)
			  and (:to is null or record.recordDate <= :to)
			order by record.recordDate desc, record.id desc
			""")
	List<HuntingRecord> findByCharacterIdAndRecordDateRange(Long characterId, LocalDate from, LocalDate to);

	Optional<HuntingRecord> findByIdAndCharacter_User_Id(Long id, Long userId);

	boolean existsByCharacter_IdAndRecordDate(Long characterId, LocalDate recordDate);

	boolean existsByCharacter_IdAndRecordDateAndIdNot(Long characterId, LocalDate recordDate, Long id);
}
