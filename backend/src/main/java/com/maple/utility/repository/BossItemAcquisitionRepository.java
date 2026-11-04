package com.maple.utility.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.maple.utility.entity.BossItemAcquisition;

public interface BossItemAcquisitionRepository extends JpaRepository<BossItemAcquisition, Long> {

	/** 결정 획득 행이 있는 기간 기록 id */
	@Query("""
			select distinct acquisition.periodEntry.id
			from BossItemAcquisition acquisition
			where acquisition.periodEntry.id in :entryIds
			  and acquisition.bossDropItem.itemKind = com.maple.utility.entity.BossItemKind.CRYSTAL
			""")
	List<Long> findCrystalEntryIds(@Param("entryIds") Collection<Long> entryIds);

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
