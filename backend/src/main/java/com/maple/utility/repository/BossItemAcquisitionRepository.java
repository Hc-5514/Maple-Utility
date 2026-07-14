package com.maple.utility.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.BossItemAcquisition;

public interface BossItemAcquisitionRepository extends JpaRepository<BossItemAcquisition, Long> {

	List<BossItemAcquisition> findByCharacter_IdAndBossDropItem_IdInOrderByAcquiredDateDescIdDesc(
			Long characterId,
			List<Long> bossDropItemIds
	);

	Optional<BossItemAcquisition> findByIdAndCharacter_User_Id(Long id, Long userId);
}
