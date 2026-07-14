package com.maple.utility.security;

import java.time.LocalDate;
import java.util.List;

import com.maple.utility.entity.Difficulty;
import com.maple.utility.entity.ResetPeriod;

public record NexonSchedulerResponse(
		List<Daily> daily,
		List<Weekly> weekly,
		List<Boss> boss
) {

	public record Daily(
			LocalDate recordDate,
			String contentName,
			int completedCount,
			int totalCount
	) {
	}

	public record Weekly(
			LocalDate weekStartDate,
			String contentName,
			boolean completed,
			Integer score
	) {
	}

	public record Boss(
			LocalDate recordDate,
			String bossName,
			Difficulty difficulty,
			ResetPeriod resetPeriod,
			boolean completed
	) {
	}
}
