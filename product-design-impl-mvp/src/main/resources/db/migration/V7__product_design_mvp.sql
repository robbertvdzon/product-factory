CREATE TABLE pf_design_process_session (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    active_product_id VARCHAR(100) UNIQUE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('RUNNING','WAITING_FOR_AI','BLOCKED','SUCCEEDED','FAILED','CANCELLED')),
    implementation_artifact VARCHAR(120) NOT NULL,
    implementation_variant VARCHAR(80) NOT NULL,
    implementation_version VARCHAR(80) NOT NULL,
    implementation_revision VARCHAR(80) NOT NULL,
    input_fingerprint VARCHAR(64),
    inputs_json TEXT NOT NULL,
    snapshot_json TEXT,
    memory_version_ids_json TEXT NOT NULL,
    git_url VARCHAR(1000),
    git_commit_sha VARCHAR(40),
    ai_task_ids_json TEXT NOT NULL,
    current_ai_task_id VARCHAR(36),
    ai_attempt INTEGER NOT NULL DEFAULT 0,
    publications_json TEXT NOT NULL,
    result_summary VARCHAR(2000),
    blocked_reason VARCHAR(1000),
    error_code VARCHAR(160),
    call_claimed_until TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_pf_design_session_product_started ON pf_design_process_session(product_id, started_at);

CREATE TABLE pf_epic (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    current_version BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('AVAILABLE','IN_PLANNING','ACTIVE','VERIFYING','COMPLETED','NOT_SUCCESSFUL','SUPERSEDED','WITHDRAWN','CANCELLED')),
    verification_id VARCHAR(80),
    terminal_reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_pf_epic_product_status ON pf_epic(product_id, status);

CREATE TABLE pf_epic_version (
    epic_id VARCHAR(36) NOT NULL REFERENCES pf_epic(id),
    version BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(600) NOT NULL,
    problem TEXT NOT NULL,
    solution TEXT NOT NULL,
    direction_references_json TEXT NOT NULL,
    ux_design TEXT,
    acceptance_criteria_json TEXT NOT NULL,
    slicability_rationale TEXT NOT NULL,
    source_references_json TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    supersedes_version BIGINT,
    PRIMARY KEY (epic_id, version)
);

CREATE TABLE pf_design_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    epic_id VARCHAR(36) NOT NULL,
    result_version BIGINT NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_design_cancellation_operation (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    epic_id VARCHAR(36) NOT NULL REFERENCES pf_epic(id),
    expected_version BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_PLANNING','CONFIRMED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
