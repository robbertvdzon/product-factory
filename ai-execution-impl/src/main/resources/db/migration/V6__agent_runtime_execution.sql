CREATE TABLE pf_ai_task (
    id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    job_key VARCHAR(160) NOT NULL REFERENCES pf_ai_job_definition(job_key),
    product_id VARCHAR(80),
    requester_capability VARCHAR(160) NOT NULL,
    requester_session_id VARCHAR(80),
    agent_role VARCHAR(120) NOT NULL,
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('CODEX', 'CLAUDE', 'MOCKED')),
    model VARCHAR(200) NOT NULL,
    configuration_version BIGINT NOT NULL,
    prompt_template_version BIGINT NOT NULL,
    runtime_job_id VARCHAR(36) UNIQUE,
    runtime_phase VARCHAR(120),
    runtime_attempt_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL CHECK (status IN ('PENDING_SUBMISSION', 'QUEUED', 'WAITING_FOR_WORKER', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    safe_progress_percent INTEGER,
    safe_progress VARCHAR(1000),
    error_code VARCHAR(160),
    safe_error_message VARCHAR(1000),
    cancel_reason VARCHAR(500),
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_pf_ai_task_product_status ON pf_ai_task(product_id, status);
CREATE INDEX idx_pf_ai_task_runtime_status ON pf_ai_task(runtime_job_id, status);

CREATE TABLE pf_ai_runtime_outbox (
    task_id VARCHAR(36) PRIMARY KEY REFERENCES pf_ai_task(id),
    runtime_idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    frozen_request_json TEXT NOT NULL,
    dispatched_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(160),
    last_error_message VARCHAR(1000),
    retry_after TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_ai_task_result (
    task_id VARCHAR(36) PRIMARY KEY REFERENCES pf_ai_task(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED')),
    response_json TEXT,
    artifacts_json TEXT NOT NULL,
    error_code VARCHAR(160),
    safe_message VARCHAR(1000),
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_environment_key_catalog (
    name VARCHAR(200) PRIMARY KEY,
    project_prefix VARCHAR(80) NOT NULL,
    available BOOLEAN NOT NULL,
    matching_online_workers INTEGER NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_product_environment_key (
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    name VARCHAR(200) NOT NULL REFERENCES pf_environment_key_catalog(name),
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    PRIMARY KEY (product_id, name)
);

CREATE TABLE pf_agent_environment_grant (
    product_id VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    agent_role VARCHAR(120) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    PRIMARY KEY (product_id, name, agent_role),
    FOREIGN KEY (product_id, name) REFERENCES pf_product_environment_key(product_id, name)
);

CREATE TABLE pf_environment_access_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_meeting_ai_work (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    meeting_id VARCHAR(36) NOT NULL,
    work_type VARCHAR(20) NOT NULL CHECK (work_type IN ('CONVERSE', 'SUMMARIZE')),
    source_meeting_version BIGINT NOT NULL,
    target_agent_role VARCHAR(120) NOT NULL,
    task_id VARCHAR(36) NOT NULL UNIQUE REFERENCES pf_ai_task(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('WAITING_FOR_AI', 'APPLIED', 'FAILED')),
    safe_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_pf_meeting_ai_work_status ON pf_meeting_ai_work(status, created_at);
