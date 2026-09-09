-- Expand-only migration for Agent Runtime v2. Legacy provider columns remain in
-- place so the previous Product Factory image can still start during rollback.

ALTER TABLE pf_ai_job_definition ADD COLUMN default_vendor_id VARCHAR(120);
ALTER TABLE pf_ai_job_definition ADD COLUMN default_execution_mode VARCHAR(20);

UPDATE pf_ai_job_definition
SET default_vendor_id = CASE default_provider
        WHEN 'CODEX' THEN 'openai'
        WHEN 'CLAUDE' THEN 'anthropic'
        WHEN 'MOCKED' THEN 'mock'
    END,
    default_execution_mode = CASE default_provider
        WHEN 'MOCKED' THEN 'MOCK'
        ELSE 'SUBSCRIPTION'
    END,
    default_model = CASE default_provider
        WHEN 'MOCKED' THEN 'mock'
        ELSE default_model
    END;

ALTER TABLE pf_ai_job_definition ALTER COLUMN default_vendor_id SET NOT NULL;
ALTER TABLE pf_ai_job_definition ALTER COLUMN default_execution_mode SET NOT NULL;
ALTER TABLE pf_ai_job_definition ALTER COLUMN default_vendor_id SET DEFAULT 'openai';
ALTER TABLE pf_ai_job_definition ALTER COLUMN default_execution_mode SET DEFAULT 'SUBSCRIPTION';
ALTER TABLE pf_ai_job_definition ADD CONSTRAINT chk_pf_ai_job_definition_mode
    CHECK (default_execution_mode IN ('SUBSCRIPTION', 'API', 'MOCK'));

ALTER TABLE pf_ai_job_configuration ADD COLUMN vendor_id VARCHAR(120);
ALTER TABLE pf_ai_job_configuration ADD COLUMN execution_mode VARCHAR(20);

UPDATE pf_ai_job_configuration
SET vendor_id = CASE provider
        WHEN 'CODEX' THEN 'openai'
        WHEN 'CLAUDE' THEN 'anthropic'
        WHEN 'MOCKED' THEN 'mock'
    END,
    execution_mode = CASE provider
        WHEN 'MOCKED' THEN 'MOCK'
        ELSE 'SUBSCRIPTION'
    END,
    model = CASE provider
        WHEN 'MOCKED' THEN 'mock'
        ELSE model
    END;

ALTER TABLE pf_ai_job_configuration ALTER COLUMN vendor_id SET NOT NULL;
ALTER TABLE pf_ai_job_configuration ALTER COLUMN execution_mode SET NOT NULL;
ALTER TABLE pf_ai_job_configuration ALTER COLUMN vendor_id SET DEFAULT 'openai';
ALTER TABLE pf_ai_job_configuration ALTER COLUMN execution_mode SET DEFAULT 'SUBSCRIPTION';
ALTER TABLE pf_ai_job_configuration ADD CONSTRAINT chk_pf_ai_job_configuration_mode
    CHECK (execution_mode IN ('SUBSCRIPTION', 'API', 'MOCK'));

ALTER TABLE pf_ai_task ADD COLUMN vendor_id VARCHAR(120);
ALTER TABLE pf_ai_task ADD COLUMN execution_mode VARCHAR(20);

UPDATE pf_ai_task
SET vendor_id = CASE provider
        WHEN 'CODEX' THEN 'openai'
        WHEN 'CLAUDE' THEN 'anthropic'
        WHEN 'MOCKED' THEN 'mock'
    END,
    execution_mode = CASE provider
        WHEN 'MOCKED' THEN 'MOCK'
        ELSE 'SUBSCRIPTION'
    END,
    model = CASE provider
        WHEN 'MOCKED' THEN 'mock'
        ELSE model
    END;

ALTER TABLE pf_ai_task ALTER COLUMN vendor_id SET NOT NULL;
ALTER TABLE pf_ai_task ALTER COLUMN execution_mode SET NOT NULL;
ALTER TABLE pf_ai_task ALTER COLUMN vendor_id SET DEFAULT 'openai';
ALTER TABLE pf_ai_task ALTER COLUMN execution_mode SET DEFAULT 'SUBSCRIPTION';
ALTER TABLE pf_ai_task ADD CONSTRAINT chk_pf_ai_task_execution_mode
    CHECK (execution_mode IN ('SUBSCRIPTION', 'API', 'MOCK'));
CREATE INDEX idx_pf_ai_task_execution ON pf_ai_task(vendor_id, model, execution_mode, created_at);

ALTER TABLE pf_ai_model_catalog ADD COLUMN vendor_id VARCHAR(120);
ALTER TABLE pf_ai_model_catalog ADD COLUMN execution_mode VARCHAR(20);

UPDATE pf_ai_model_catalog
SET vendor_id = CASE provider
        WHEN 'CODEX' THEN 'openai'
        WHEN 'CLAUDE' THEN 'anthropic'
        WHEN 'MOCKED' THEN 'mock'
    END,
    execution_mode = CASE provider
        WHEN 'MOCKED' THEN 'MOCK'
        ELSE 'SUBSCRIPTION'
    END,
    model = CASE provider
        WHEN 'MOCKED' THEN 'mock'
        ELSE model
    END;

ALTER TABLE pf_ai_model_catalog ALTER COLUMN vendor_id SET NOT NULL;
ALTER TABLE pf_ai_model_catalog ALTER COLUMN execution_mode SET NOT NULL;
ALTER TABLE pf_ai_model_catalog ALTER COLUMN vendor_id SET DEFAULT 'openai';
ALTER TABLE pf_ai_model_catalog ALTER COLUMN execution_mode SET DEFAULT 'SUBSCRIPTION';
ALTER TABLE pf_ai_model_catalog ADD CONSTRAINT chk_pf_ai_model_catalog_mode
    CHECK (execution_mode IN ('SUBSCRIPTION', 'API', 'MOCK'));
ALTER TABLE pf_ai_model_catalog ADD CONSTRAINT uq_pf_ai_model_catalog_execution
    UNIQUE (vendor_id, model, execution_mode);

ALTER TABLE pf_ai_runtime_outbox ALTER COLUMN frozen_request_json DROP NOT NULL;
ALTER TABLE pf_ai_runtime_outbox ADD COLUMN api_version VARCHAR(2) NOT NULL DEFAULT 'v1';
ALTER TABLE pf_ai_runtime_outbox ADD COLUMN request_fingerprint VARCHAR(64);
ALTER TABLE pf_ai_runtime_outbox ADD COLUMN dispatch_state VARCHAR(32) NOT NULL DEFAULT 'PENDING_UPLOAD';
ALTER TABLE pf_ai_runtime_outbox ADD COLUMN claimed_by VARCHAR(160);
ALTER TABLE pf_ai_runtime_outbox ADD COLUMN claimed_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE pf_ai_runtime_outbox ADD CONSTRAINT chk_pf_ai_runtime_outbox_api_version
    CHECK (api_version IN ('v1', 'v2'));
ALTER TABLE pf_ai_runtime_outbox ADD CONSTRAINT chk_pf_ai_runtime_outbox_state
    CHECK (dispatch_state IN ('PENDING_UPLOAD', 'REQUEST_FROZEN', 'DISPATCHED', 'CANCELLED'));
UPDATE pf_ai_runtime_outbox
SET request_fingerprint = (SELECT request_fingerprint FROM pf_ai_task WHERE pf_ai_task.id = pf_ai_runtime_outbox.task_id),
    dispatch_state = CASE WHEN dispatched_at IS NULL THEN 'REQUEST_FROZEN' ELSE 'DISPATCHED' END;

CREATE TABLE pf_ai_task_input (
    task_id VARCHAR(36) NOT NULL REFERENCES pf_ai_task(id),
    input_sequence INTEGER NOT NULL CHECK (input_sequence >= 0),
    logical_name VARCHAR(120) NOT NULL,
    filename VARCHAR(320) NOT NULL,
    mime_type VARCHAR(160) NOT NULL,
    input_role VARCHAR(20) NOT NULL CHECK (input_role IN ('SOURCE', 'CONTEXT', 'PROMPT', 'IMAGE', 'AUDIO', 'VIDEO', 'DOCUMENT')),
    content_bytes BYTEA,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 VARCHAR(64) NOT NULL,
    staged_at TIMESTAMP WITH TIME ZONE NOT NULL,
    content_cleared_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (task_id, input_sequence),
    UNIQUE (task_id, logical_name)
);

CREATE TABLE pf_ai_runtime_upload (
    task_id VARCHAR(36) NOT NULL,
    input_sequence INTEGER NOT NULL,
    upload_id VARCHAR(36),
    object_id VARCHAR(36),
    upload_url VARCHAR(1000),
    chunk_size_bytes INTEGER,
    confirmed_offset BIGINT NOT NULL DEFAULT 0 CHECK (confirmed_offset >= 0),
    upload_state VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED'
        CHECK (upload_state IN ('NOT_STARTED', 'UPLOADING', 'READY', 'EXPIRED', 'DELETED')),
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (task_id, input_sequence),
    FOREIGN KEY (task_id, input_sequence) REFERENCES pf_ai_task_input(task_id, input_sequence)
);

CREATE TABLE pf_ai_runtime_event_cursor (
    task_id VARCHAR(36) PRIMARY KEY REFERENCES pf_ai_task(id),
    runtime_job_id VARCHAR(36) NOT NULL,
    last_sequence BIGINT NOT NULL DEFAULT 0 CHECK (last_sequence >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_ai_runtime_event (
    task_id VARCHAR(36) NOT NULL REFERENCES pf_ai_task(id),
    runtime_job_id VARCHAR(36) NOT NULL,
    event_sequence BIGINT NOT NULL CHECK (event_sequence > 0),
    event_type VARCHAR(80) NOT NULL,
    safe_message VARCHAR(2000),
    progress_percent INTEGER,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    stored_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (runtime_job_id, event_sequence)
);
CREATE INDEX idx_pf_ai_runtime_event_task ON pf_ai_runtime_event(task_id, event_sequence);

CREATE TABLE pf_ai_runtime_usage (
    task_id VARCHAR(36) PRIMARY KEY REFERENCES pf_ai_task(id),
    runtime_job_id VARCHAR(36) NOT NULL,
    task_type VARCHAR(80) NOT NULL,
    vendor_id VARCHAR(120) NOT NULL,
    model VARCHAR(200) NOT NULL,
    execution_mode VARCHAR(20) NOT NULL CHECK (execution_mode IN ('SUBSCRIPTION', 'API', 'MOCK')),
    attempt_count INTEGER NOT NULL CHECK (attempt_count >= 0),
    usage_quality VARCHAR(40) NOT NULL,
    usage_json TEXT NOT NULL,
    cost_json TEXT NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_ai_runtime_attempt_usage (
    task_id VARCHAR(36) NOT NULL REFERENCES pf_ai_task(id),
    runtime_attempt_id VARCHAR(36) NOT NULL,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    status VARCHAR(40) NOT NULL,
    usage_quality VARCHAR(40) NOT NULL,
    usage_json TEXT NOT NULL,
    cost_json TEXT NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (task_id, runtime_attempt_id)
);

CREATE TABLE pf_ai_artifact (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL REFERENCES pf_ai_task(id),
    runtime_job_id VARCHAR(36) NOT NULL,
    runtime_object_id VARCHAR(36) NOT NULL,
    logical_name VARCHAR(120) NOT NULL,
    filename VARCHAR(320) NOT NULL,
    mime_type VARCHAR(160) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(1000) NOT NULL UNIQUE,
    artifact_status VARCHAR(32) NOT NULL CHECK (artifact_status IN ('COPYING', 'READY', 'FAILED', 'DELETING', 'DELETED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ready_at TIMESTAMP WITH TIME ZONE,
    retention_until TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (task_id, logical_name)
);

CREATE TABLE pf_ai_artifact_domain_reference (
    artifact_id VARCHAR(36) NOT NULL REFERENCES pf_ai_artifact(id),
    domain_type VARCHAR(80) NOT NULL,
    domain_id VARCHAR(160) NOT NULL,
    domain_version BIGINT NOT NULL,
    reference_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (artifact_id, domain_type, domain_id, domain_version, reference_name)
);
CREATE INDEX idx_pf_ai_artifact_domain_reference_lookup
    ON pf_ai_artifact_domain_reference(domain_type, domain_id, released_at);
