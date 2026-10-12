package com.maple.utility.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SchedulerWeekFinalizationRepository {
	public record Target(long id, long userId, long characterId, LocalDate weekStartDate) {
		public LocalDate recordDate() { return weekStartDate.plusDays(6); }
	}

	private static final String RETRYABLE = "status IN ('PENDING', 'RETRY', 'NO_DATA')";
	private static final RowMapper<Target> MAPPER = (rs, row) -> new Target(rs.getLong("id"),
			rs.getLong("user_id"), rs.getLong("character_id"), rs.getDate("week_start_date").toLocalDate());
	private final JdbcTemplate jdbc;

	public SchedulerWeekFinalizationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	public void seed(Long userId, LocalDate weekStart) {
		jdbc.update("""
				INSERT INTO scheduler_week_finalizations (user_id, character_id, week_start_date)
				SELECT user_id, id, ? FROM characters WHERE user_id = ? AND is_favorite = true
				ON CONFLICT (character_id, week_start_date) DO NOTHING
				""", weekStart, userId);
	}

	public void expire(LocalDate today, LocalDateTime now) {
		jdbc.update("UPDATE scheduler_week_finalizations SET status = 'EXPIRED', last_error = 'NEXON_HISTORY_EXPIRED', updated_at = ? "
				+ "WHERE " + RETRYABLE + " AND week_start_date + 6 < ?", now, today.minusDays(14));
	}

	public List<Target> due(Long userId, LocalDate today) {
		return jdbc.query("SELECT * FROM scheduler_week_finalizations WHERE user_id = ? AND " + RETRYABLE
				+ " AND (last_attempt_at IS NULL OR last_attempt_at < ?) ORDER BY week_start_date, character_id",
				MAPPER, userId, today.atStartOfDay());
	}

	public Optional<Target> lockDue(long id, LocalDate today) {
		return jdbc.query("SELECT * FROM scheduler_week_finalizations WHERE id = ? AND " + RETRYABLE
				+ " AND (last_attempt_at IS NULL OR last_attempt_at < ?) FOR UPDATE", MAPPER,
				id, today.atStartOfDay()).stream().findFirst();
	}

	public void finish(long id, boolean hasData, LocalDateTime now) {
		jdbc.update("UPDATE scheduler_week_finalizations SET status = ?, attempts = attempts + 1, last_error = ?, "
				+ "last_attempt_at = ?, finalized_at = ?, updated_at = ? WHERE id = ? AND " + RETRYABLE,
				hasData ? "FINALIZED" : "NO_DATA", hasData ? null : "NEXON_SCHEDULER_NO_DATA",
				now, hasData ? now : null, now, id);
	}

	public void retry(long id, String error, boolean attempted, LocalDateTime now) {
		jdbc.update("UPDATE scheduler_week_finalizations SET status = 'RETRY', attempts = attempts + ?, "
				+ "last_error = ?, last_attempt_at = ?, updated_at = ? WHERE id = ? AND " + RETRYABLE,
				attempted ? 1 : 0, error, now, now, id);
	}
}
