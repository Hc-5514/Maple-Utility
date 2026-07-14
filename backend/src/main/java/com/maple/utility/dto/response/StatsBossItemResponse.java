package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDate;

import com.maple.utility.entity.Difficulty;

public record StatsBossItemResponse(
		LocalDate acquiredDate,
		String characterName,
		String bossName,
		Difficulty difficulty,
		String itemName
) implements Serializable {
}
