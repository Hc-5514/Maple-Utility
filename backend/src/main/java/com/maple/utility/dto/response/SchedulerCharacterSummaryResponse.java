package com.maple.utility.dto.response;

import java.io.Serializable;

public record SchedulerCharacterSummaryResponse(
		Long characterId,
		String characterName,
		Integer characterLevel,
		String characterClass,
		String characterImage,
		String worldName,
		TaskSummary daily,
		TaskSummary weekly,
		TaskSummary weeklyBoss,
		TaskSummary monthlyBoss
) implements Serializable {
}
