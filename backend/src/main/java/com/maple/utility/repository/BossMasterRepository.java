package com.maple.utility.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.maple.utility.entity.BossMaster;
import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.ResetPeriod;

public interface BossMasterRepository extends JpaRepository<BossMaster, Long> {

	List<BossMaster> findByResetPeriodAndActiveTrueOrderBySortOrderAsc(ResetPeriod resetPeriod);

	/** 이름의 공백을 무시하고 조회. nameKey는 BossNames.lookupKey로 만든 값 */
	@Query("select boss from BossMaster boss where replace(boss.bossName, ' ', '') = :nameKey and boss.difficulty = :difficulty")
	Optional<BossMaster> findByNameKeyAndDifficulty(@Param("nameKey") String nameKey, @Param("difficulty") Difficulty difficulty);
}
