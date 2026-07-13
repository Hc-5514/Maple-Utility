package com.maple.utility.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.ResetPeriod;

public interface BossMasterRepository extends JpaRepository<BossMaster, Long> {

	List<BossMaster> findByResetPeriodAndActiveTrueOrderBySortOrderAsc(ResetPeriod resetPeriod);

	Optional<BossMaster> findByBossNameAndDifficulty(String bossName, Difficulty difficulty);
}
