package com.maple.utility.dto.response;

import com.maple.utility.entity.BossMaster;

public record BossCandidateResponse(
		Long id,
		String bossName,
		String difficulty,
		String bossImage,
		long crystalPrice,
		String resetPeriod
) {
	public static BossCandidateResponse from(BossMaster boss) {
		return new BossCandidateResponse(
				boss.getId(),
				boss.getBossName(),
				boss.getDifficulty().name(),
				boss.getBossImage(),
				boss.getCrystalPrice(),
				boss.getResetPeriod().name()
		);
	}
}
