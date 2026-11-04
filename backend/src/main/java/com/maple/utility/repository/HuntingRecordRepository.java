package com.maple.utility.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.maple.utility.entity.HuntingRecord;

public interface HuntingRecordRepository extends JpaRepository<HuntingRecord, Long>, JpaSpecificationExecutor<HuntingRecord> {

	Optional<HuntingRecord> findByIdAndCharacter_User_Id(Long id, Long userId);

	Optional<HuntingRecord> findFirstByCharacter_IdAndHuntingGroundCatalogIsNotNullOrderByRecordDateDescIdDesc(Long characterId);
}
