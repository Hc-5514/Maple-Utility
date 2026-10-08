package com.maple.utility.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.maple.utility.entity.ResetPeriod;

public record ManualBossRecordSaveRequest(
		@NotNull LocalDate periodStart,
		@NotNull ResetPeriod resetPeriod,
		@NotEmpty List<@NotNull Long> bossIds
) {
}
