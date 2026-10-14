package com.maple.utility.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SchedulerMonthlyFinalizationRepository {
	public record Target(long id, long userId, long characterId, LocalDate monthStartDate) {
		public LocalDate recordDate() { return monthStartDate.plusMonths(1).minusDays(1); }
	}
	public record BossSnapshot(long bossId, boolean completed) { }
	public record FinalSnapshot(LocalDate recordDate, LocalDateTime finalizedAt, List<BossSnapshot> bosses) { }
	private static final String RETRYABLE = "status IN ('PENDING','RETRY','NO_DATA')";
	private static final RowMapper<Target> MAPPER = (rs, row) -> new Target(rs.getLong("id"), rs.getLong("user_id"),
			rs.getLong("character_id"), rs.getDate("month_start_date").toLocalDate());
	private final JdbcTemplate jdbc;
	public SchedulerMonthlyFinalizationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	public void seed(Long userId, LocalDate monthStart) {
		jdbc.update("""
				INSERT INTO scheduler_monthly_finalizations(user_id, character_id, month_start_date)
				SELECT user_id, id, ? FROM characters WHERE user_id = ? AND is_favorite = true
				ON CONFLICT (character_id, month_start_date) DO NOTHING
				""", monthStart, userId);
	}
	public int expire(LocalDate today, LocalDateTime now) {
		return jdbc.update("UPDATE scheduler_monthly_finalizations SET status='EXPIRED', last_error='NEXON_MONTH_HISTORY_EXPIRED', updated_at=? WHERE "
				+ RETRYABLE + " AND month_start_date + INTERVAL '1 month' - INTERVAL '1 day' < ?", now, today.minusDays(13));
	}
	public List<Target> due(Long userId, LocalDate today) {
		return jdbc.query("SELECT * FROM scheduler_monthly_finalizations WHERE user_id=? AND " + RETRYABLE
				+ " AND month_start_date + INTERVAL '1 month' - INTERVAL '1 day' BETWEEN ? AND ?"
				+ " AND (last_attempt_at IS NULL OR last_attempt_at < ?) ORDER BY month_start_date, character_id",
				MAPPER, userId, today.minusDays(13), today.minusDays(1), today.atStartOfDay());
	}
	public Optional<Target> lockDue(long id, LocalDate today) {
		return jdbc.query("SELECT * FROM scheduler_monthly_finalizations WHERE id=? AND " + RETRYABLE
				+ " AND month_start_date + INTERVAL '1 month' - INTERVAL '1 day' BETWEEN ? AND ?"
				+ " AND (last_attempt_at IS NULL OR last_attempt_at < ?) FOR UPDATE",
				MAPPER, id, today.minusDays(13), today.minusDays(1), today.atStartOfDay()).stream().findFirst();
	}
	public boolean hasDueOrUnseeded(Long userId, LocalDate monthStart, LocalDate today) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM characters c LEFT JOIN scheduler_monthly_finalizations f
				ON f.character_id=c.id AND f.month_start_date=? WHERE c.user_id=? AND c.is_favorite=true
				AND (f.id IS NULL OR (f.status IN ('PENDING','RETRY','NO_DATA')
				AND (f.last_attempt_at IS NULL OR f.last_attempt_at < ?))))
				""", Boolean.class, monthStart, userId, today.atStartOfDay()));
	}
	public void finish(Target target, List<BossSnapshot> bosses, LocalDateTime now) {
		for (var boss : bosses) {
			jdbc.update("INSERT INTO scheduler_monthly_boss_snapshots(finalization_id,boss_id,is_completed) VALUES (?,?,?)",
					target.id(), boss.bossId(), boss.completed());
		}
		jdbc.update("UPDATE scheduler_monthly_finalizations SET status='FINALIZED', attempts=attempts+1, last_error=NULL, last_attempt_at=?, finalized_at=?, updated_at=? WHERE id=? AND "
				+ RETRYABLE, now, now, now, target.id());
	}
	public void noData(long id, LocalDateTime now) {
		jdbc.update("UPDATE scheduler_monthly_finalizations SET status='NO_DATA', attempts=attempts+1, last_error='NEXON_SCHEDULER_NO_DATA', last_attempt_at=?, updated_at=? WHERE id=? AND "
				+ RETRYABLE, now, now, id);
	}
	public void retry(long id, String code, boolean attempted, LocalDateTime now) {
		jdbc.update("UPDATE scheduler_monthly_finalizations SET status='RETRY', attempts=attempts+?, last_error=?, last_attempt_at=?, updated_at=? WHERE id=? AND "
				+ RETRYABLE, attempted ? 1 : 0, code, now, now, id);
	}
	public Optional<FinalSnapshot> findFinalized(Long characterId, LocalDate monthStart) {
		return jdbc.query("SELECT * FROM scheduler_monthly_finalizations WHERE character_id=? AND month_start_date=? AND status='FINALIZED'",
				(rs, row) -> new FinalSnapshot(monthStart.plusMonths(1).minusDays(1), rs.getTimestamp("finalized_at").toLocalDateTime(),
						jdbc.query("SELECT boss_id,is_completed FROM scheduler_monthly_boss_snapshots WHERE finalization_id=? ORDER BY boss_id",
								(brs, i) -> new BossSnapshot(brs.getLong("boss_id"), brs.getBoolean("is_completed")), rs.getLong("id"))),
				characterId, monthStart).stream().findFirst();
	}
}
