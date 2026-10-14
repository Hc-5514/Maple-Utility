package com.maple.utility.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import com.maple.utility.entity.ResetPeriod;
import com.maple.utility.util.SchedulerPeriodPolicy;

class SchedulerPeriodPolicyTest {
	@Test
	void everyWeekdayHasExactlyOneFinalizablePreviousWednesday() {
		for (int day = 0; day < 7; day++) {
			LocalDate today = LocalDate.of(2026, 10, 15).plusDays(day);
			LocalDateTime now = today.atTime(3, 0);
			assertThat(SchedulerPeriodPolicy.previousWeekStart(today)).isEqualTo("2026-10-08");
			assertThat(SchedulerPeriodPolicy.canFinalize(LocalDate.of(2026, 10, 14), now)).isTrue();
			assertThat(SchedulerPeriodPolicy.canFinalize(LocalDate.of(2026, 10, 7), now)).isFalse();
		}
	}

	@Test
	void thursdayBeforeTwoDoesNotConsumeTheFirstAttempt() {
		LocalDate recordDate = LocalDate.of(2026, 10, 14);
		assertThat(SchedulerPeriodPolicy.canFinalize(recordDate, LocalDateTime.parse("2026-10-15T01:59:59"))).isFalse();
		assertThat(SchedulerPeriodPolicy.canFinalize(recordDate, LocalDateTime.parse("2026-10-15T02:00:00"))).isTrue();
	}

	@Test
	void providerWindowAndTwoWeekBusinessScopeAreSeparate() {
		LocalDate today = LocalDate.of(2026, 10, 15);
		assertThat(SchedulerPeriodPolicy.withinHistoryWindow(today.minusDays(13), today)).isTrue();
		assertThat(SchedulerPeriodPolicy.withinHistoryWindow(today.minusDays(14), today)).isFalse();
		assertThat(SchedulerPeriodPolicy.withinHistoryWindow(today, today)).isFalse();
		assertThat(SchedulerPeriodPolicy.withinHistoryWindow(today.plusDays(1), today)).isFalse();
		assertThat(SchedulerPeriodPolicy.withinHistoryWindow(LocalDate.of(2026, 10, 7), today)).isTrue();
		assertThat(SchedulerPeriodPolicy.canFinalize(LocalDate.of(2026, 10, 7), today.atTime(3, 0))).isFalse();
	}

	@Test
	void weeklyLocksMoveAtThursdayMidnight() {
		LocalDate start = LocalDate.of(2026, 10, 1);
		assertThat(SchedulerPeriodPolicy.customizable(start, ResetPeriod.WEEKLY, LocalDate.of(2026, 10, 14))).isFalse();
		assertThat(SchedulerPeriodPolicy.customizable(start, ResetPeriod.WEEKLY, LocalDate.of(2026, 10, 15))).isTrue();
		assertThat(SchedulerPeriodPolicy.customizable(LocalDate.of(2026, 10, 8), ResetPeriod.WEEKLY, LocalDate.of(2026, 10, 15))).isFalse();
	}

	@Test
	void monthlyLocksFollowOverlappingCalendarWeeksIncludingLeapYearAndYearEnd() {
		LocalDate september = LocalDate.of(2026, 9, 1);
		assertThat(SchedulerPeriodPolicy.customizable(september, ResetPeriod.MONTHLY, LocalDate.of(2026, 10, 1))).isFalse();
		assertThat(SchedulerPeriodPolicy.customizable(september, ResetPeriod.MONTHLY, LocalDate.of(2026, 10, 8))).isTrue();
		assertThat(SchedulerPeriodPolicy.customizable(LocalDate.of(2025, 12, 1), ResetPeriod.MONTHLY, LocalDate.of(2026, 1, 1))).isFalse();
		assertThat(SchedulerPeriodPolicy.customizable(LocalDate.of(2024, 2, 1), ResetPeriod.MONTHLY, LocalDate.of(2024, 3, 7))).isFalse();
		assertThat(SchedulerPeriodPolicy.customizable(LocalDate.of(2024, 2, 1), ResetPeriod.MONTHLY, LocalDate.of(2024, 3, 14))).isTrue();
	}
}
