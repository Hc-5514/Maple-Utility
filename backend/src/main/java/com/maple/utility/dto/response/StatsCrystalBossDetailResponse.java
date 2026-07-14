package com.maple.utility.dto.response;

import java.io.Serializable;

import com.maple.utility.entity.Difficulty;

public record StatsCrystalBossDetailResponse(
		String bossName,
		Difficulty difficulty,
		long income
) implements Serializable {
}
