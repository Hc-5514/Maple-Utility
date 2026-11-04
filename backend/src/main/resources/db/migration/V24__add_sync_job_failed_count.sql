ALTER TABLE sync_jobs
    ADD COLUMN failed_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_sync_jobs_failed_count CHECK (failed_count >= 0),
    ADD CONSTRAINT chk_sync_jobs_processed_counts CHECK (failed_count + skipped_count <= completed_count);
