CREATE TABLE task_status (
    id UUID PRIMARY KEY,
    job_data TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);