package com.maple.utility.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class SyncJobFailedCountMigrationTest {

	@Test
	void upgradeBackfillsFailureCountWithoutChangingExistingJobResults() throws Exception {
		try (var database = new SyncJobRepositoryTest.Database("23")) {
			Long userId = database.createUser();
			for (String status : new String[] {"STARTED", "COMPLETED", "FAILED"}) {
				database.jdbc.update("""
						INSERT INTO sync_jobs (user_id, job_type, status, total_count, completed_count, skipped_count)
						VALUES (?, 'SCHEDULER', ?, 4, 3, 1)
						""", userId, status);
			}

			database.migrate(null);

			var rows = database.jdbc.queryForList("""
					SELECT status, total_count, completed_count, skipped_count, failed_count
					FROM sync_jobs WHERE user_id = ? ORDER BY id
					""", userId);
			assertThat(rows).extracting(row -> row.get("status"), row -> row.get("total_count"),
					row -> row.get("completed_count"), row -> row.get("skipped_count"), row -> row.get("failed_count"))
					.containsExactly(tuple("STARTED", 4, 3, 1, 0), tuple("COMPLETED", 4, 3, 1, 0), tuple("FAILED", 4, 3, 1, 0));
		}
	}
}
