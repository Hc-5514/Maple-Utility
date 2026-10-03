package com.maple.utility.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.maple.utility.dto.response.SyncJobResponse;

@Repository
public class SyncJobRepository {
	public record StartResult(SyncJobResponse job, boolean created) {
	}

	private static final RowMapper<SyncJobResponse> JOB_MAPPER = (rs, rowNum) -> mapJob(rs);
	private final JdbcTemplate jdbcTemplate;

	public SyncJobRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public StartResult start(Long userId, String type) {
		List<Long> ids = jdbcTemplate.query(
				"INSERT INTO sync_jobs (user_id, job_type, status) VALUES (?, ?, 'STARTED') "
						+ "ON CONFLICT (user_id, job_type) WHERE status = 'STARTED' DO NOTHING RETURNING id",
				(rs, rowNum) -> rs.getLong(1), userId, type);
		return ids.isEmpty()
				? new StartResult(active(userId, type).orElseThrow(), false)
				: new StartResult(get(userId, ids.getFirst()).orElseThrow(), true);
	}

	public Optional<SyncJobResponse> active(Long userId, String type) {
		return jdbcTemplate.query("SELECT * FROM sync_jobs WHERE user_id = ? AND job_type = ? AND status = 'STARTED'",
				JOB_MAPPER, userId, type).stream().findFirst();
	}

	public Optional<SyncJobResponse> latestCompleted(Long userId, String type) {
		return jdbcTemplate.query("SELECT * FROM sync_jobs WHERE user_id = ? AND job_type = ? AND status = 'COMPLETED' ORDER BY completed_at DESC LIMIT 1",
				JOB_MAPPER, userId, type).stream().findFirst();
	}

	public Optional<SyncJobResponse> get(Long userId, Long id) {
		return jdbcTemplate.query("SELECT * FROM sync_jobs WHERE user_id = ? AND id = ?", JOB_MAPPER, userId, id).stream().findFirst();
	}

	public void setTotal(Long id, int total) {
		jdbcTemplate.update("UPDATE sync_jobs SET total_count = ? WHERE id = ? AND status = 'STARTED'", total, id);
	}

	public void advance(Long id, boolean skipped) {
		jdbcTemplate.update("UPDATE sync_jobs SET completed_count = completed_count + 1, skipped_count = skipped_count + ? WHERE id = ? AND status = 'STARTED'",
				skipped ? 1 : 0, id);
	}

	public void complete(Long id, LocalDateTime time) {
		jdbcTemplate.update("UPDATE sync_jobs SET status = 'COMPLETED', completed_at = ? WHERE id = ? AND status = 'STARTED'", time, id);
	}

	public void fail(Long id, String message, LocalDateTime time) {
		jdbcTemplate.update("UPDATE sync_jobs SET status = 'FAILED', error_message = ?, completed_at = ? WHERE id = ? AND status = 'STARTED'", message, time, id);
	}

	public void failInterrupted(LocalDateTime time) {
		jdbcTemplate.update("UPDATE sync_jobs SET status = 'FAILED', error_message = '서버 재시작으로 동기화 중단', completed_at = ? WHERE status = 'STARTED'", time);
	}

	private static SyncJobResponse mapJob(ResultSet rs) throws SQLException {
		return new SyncJobResponse(
				rs.getLong("id"), rs.getString("job_type"), rs.getString("status"),
				rs.getInt("total_count"), rs.getInt("completed_count"), rs.getInt("skipped_count"),
				rs.getString("error_message"), rs.getTimestamp("started_at").toLocalDateTime(),
				rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime());
	}
}
