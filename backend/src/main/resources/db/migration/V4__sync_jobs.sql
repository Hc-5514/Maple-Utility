CREATE TABLE sync_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_count INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT chk_sync_jobs_type CHECK (job_type IN ('CHARACTER', 'SCHEDULER')),
    CONSTRAINT chk_sync_jobs_status CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_sync_jobs_progress CHECK (total_count >= 0 AND completed_count >= 0 AND skipped_count >= 0)
);

CREATE UNIQUE INDEX uk_sync_jobs_active ON sync_jobs (user_id, job_type) WHERE status = 'STARTED';
CREATE INDEX idx_sync_jobs_latest ON sync_jobs (user_id, job_type, started_at DESC);
