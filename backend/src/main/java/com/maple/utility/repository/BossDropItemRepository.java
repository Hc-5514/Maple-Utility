package com.maple.utility.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.BossDropItem;

public interface BossDropItemRepository extends JpaRepository<BossDropItem, Long> {

	List<BossDropItem> findByBossIdOrderByIdAsc(Long bossId);
}
