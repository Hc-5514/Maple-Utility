package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.maple.utility.entity.HuntingRecord;

public interface HuntingRecordRepository extends JpaRepository<HuntingRecord, Long>, JpaSpecificationExecutor<HuntingRecord> {

	Optional<HuntingRecord> findByIdAndCharacter_User_Id(Long id, Long userId);

	Optional<HuntingRecord> findFirstByCharacter_IdAndHuntingGroundCatalogIsNotNullOrderByRecordDateDescIdDesc(Long characterId);

	boolean existsByCharacter_IdAndRecordDate(Long characterId, LocalDate recordDate);

	boolean existsByCharacter_IdAndRecordDateAndIdNot(Long characterId, LocalDate recordDate, Long id);
}
