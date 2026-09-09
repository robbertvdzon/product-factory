CREATE TABLE pf_ai_task_specification (
    task_id VARCHAR(36) PRIMARY KEY REFERENCES pf_ai_task(id),
    instruction VARCHAR(1000) NOT NULL,
    response_schema TEXT NOT NULL,
    repository_url VARCHAR(1000),
    repository_commit_sha VARCHAR(40),
    environment_keys_json TEXT NOT NULL,
    output_artifacts_json TEXT NOT NULL,
    execution_timeout_seconds INTEGER NOT NULL CHECK (execution_timeout_seconds BETWEEN 30 AND 86400),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((repository_url IS NULL AND repository_commit_sha IS NULL) OR
           (repository_url IS NOT NULL AND repository_commit_sha IS NOT NULL))
);

UPDATE pf_ai_runtime_outbox SET api_version = 'v1' WHERE api_version IS NULL;
