package com.maple.utility.dto.response;

import java.io.Serializable;
import java.time.LocalDate;

public record StatsHuntingDailyResponse(
		LocalDate date,
		long mesoEarned,
		int solErdaEarned,
		Integer playDurationMin
) implements Serializable {
}
