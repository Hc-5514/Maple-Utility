package com.maple.utility.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Transaction-scoped PostgreSQL lock shared by live and historical synchronization. */
@Component
public class SchedulerSyncLock {
	private static final long NAMESPACE = 209_000_000_000L;
	private final JdbcTemplate jdbc;

	public SchedulerSyncLock(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public boolean tryAcquire(Long userId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",
				Boolean.class, NAMESPACE + userId));
	}
}
