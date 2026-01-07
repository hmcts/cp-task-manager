CREATE TABLE jobs (
    job_id UUID PRIMARY KEY,
    assigned_task_name TEXT,
    assigned_task_start_time TIMESTAMPTZ,
    job_data TEXT NOT NULL,
    priority INT NOT NULL DEFAULT 10,
    retry_attempts_remaining INT NOT NULL DEFAULT 0,
    worker_id UUID,
    worker_lock_time TIMESTAMPTZ
);

CREATE INDEX idx_jobs_unassigned
ON jobs (worker_id, assigned_task_start_time, priority);