package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.BossPeriodEntry;

public interface BossPeriodEntryRepository extends JpaRepository<BossPeriodEntry, Long> {

	Optional<BossPeriodEntry> findByCharacter_IdAndBoss_IdAndPeriodStart(Long characterId, Long bossId, LocalDate periodStart);

	void deleteByCharacter_IdAndBoss_IdAndPeriodStart(Long characterId, Long bossId, LocalDate periodStart);
}
