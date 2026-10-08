package com.maple.utility.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.HuntingGround;

public interface HuntingGroundRepository extends JpaRepository<HuntingGround, Long> {
	List<HuntingGround> findAllByOrderByRegionNameAscMaxMonsterLevelDescMapNameDesc();
}
