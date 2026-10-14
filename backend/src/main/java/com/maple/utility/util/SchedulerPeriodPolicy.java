package com.maple.utility.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import com.maple.utility.entity.ResetPeriod;

/** 서울 날짜 기준 이번 주·지난주 정책과 넥슨 과거 조회 범위. */
public final class SchedulerPeriodPolicy {

	private SchedulerPeriodPolicy() { }

	public static LocalDate weekStart(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
	}

	public static LocalDate previousWeekStart(LocalDate today) {
		return weekStart(today).minusWeeks(1);
	}

	public static boolean historyReady(LocalDateTime now) {
		return now.getDayOfWeek() != DayOfWeek.THURSDAY || !now.toLocalTime().isBefore(LocalTime.of(2, 0));
	}

	public static boolean withinHistoryWindow(LocalDate date, LocalDate today) {
		return date.isBefore(today) && !date.isBefore(today.minusDays(13));
	}

	public static boolean canFinalize(LocalDate recordDate, LocalDateTime now) {
		return historyReady(now) && withinHistoryWindow(recordDate, now.toLocalDate())
				&& recordDate.equals(previousWeekStart(now.toLocalDate()).plusDays(6));
	}

	public static LocalDate lastMonthStart(LocalDate today) {
		return today.withDayOfMonth(1).minusMonths(1);
	}

	public static boolean canFinalizeMonth(LocalDate recordDate, LocalDateTime now) {
		return recordDate.equals(recordDate.withDayOfMonth(1).plusMonths(1).minusDays(1))
				&& withinHistoryWindow(recordDate, now.toLocalDate())
				&& (now.toLocalDate().isAfter(recordDate.plusDays(1)) || !now.toLocalTime().isBefore(LocalTime.of(2, 0)));
	}

	public static boolean customizable(LocalDate start, ResetPeriod resetPeriod, LocalDate today) {
		LocalDate lockedFrom = previousWeekStart(today);
		return resetPeriod == ResetPeriod.WEEKLY
				? start.isBefore(lockedFrom)
				: start.plusMonths(1).minusDays(1).isBefore(lockedFrom);
	}
}
