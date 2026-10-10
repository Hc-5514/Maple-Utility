package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.BossPeriodSelection;
import com.maple.utility.entity.ResetPeriod;

public interface BossPeriodSelectionRepository extends JpaRepository<BossPeriodSelection, Long> {
	List<BossPeriodSelection> findByCharacter_IdAndPeriodStartAndResetPeriod(Long characterId, LocalDate periodStart, ResetPeriod resetPeriod);

	Optional<BossPeriodSelection> findByCharacter_IdAndBoss_IdAndPeriodStart(Long characterId, Long bossId, LocalDate periodStart);
}
