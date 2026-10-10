package com.maple.utility.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.BossItemAcquisition;

public interface BossItemAcquisitionRepository extends JpaRepository<BossItemAcquisition, Long> {
	List<BossItemAcquisition> findByCharacter_IdAndBossDropItem_Boss_IdAndAcquiredDateBetween(
			Long characterId, Long bossId, LocalDate startDate, LocalDate endDate);

	List<BossItemAcquisition> findByCharacter_IdAndBossDropItem_IdInOrderByAcquiredDateDescIdDesc(
			Long characterId,
			List<Long> bossDropItemIds
	);

	Optional<BossItemAcquisition> findByIdAndCharacter_User_Id(Long id, Long userId);

	List<BossItemAcquisition> findByPeriodEntry_IdOrderByIdAsc(Long periodEntryId);

	List<BossItemAcquisition> findByCharacter_IdAndBossDropItem_Boss_IdOrderByAcquiredDateDescIdDesc(
			Long characterId, Long bossId
	);
}
