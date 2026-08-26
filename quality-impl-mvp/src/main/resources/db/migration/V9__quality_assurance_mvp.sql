CREATE TABLE pf_quality_process_session (
    id VARCHAR(80) PRIMARY KEY,
    product_id VARCHAR(120) NOT NULL,
    active_product_id VARCHAR(120),
    status VARCHAR(40) NOT NULL,
    implementation_artifact VARCHAR(160) NOT NULL,
    implementation_variant VARCHAR(160) NOT NULL,
    implementation_version VARCHAR(120) NOT NULL,
    implementation_revision VARCHAR(120) NOT NULL,
    inputs_json TEXT NOT NULL,
    claimed_work_items_json TEXT NOT NULL,
    memory_version_ids_json TEXT NOT NULL,
    ai_task_ids_json TEXT NOT NULL,
    publications_json TEXT NOT NULL,
    frozen_context_json TEXT,
    git_url VARCHAR(2000),
    git_commit_sha VARCHAR(40),
    deployed_revision VARCHAR(160),
    current_ai_task_id VARCHAR(80),
    call_claimed_until TIMESTAMP WITH TIME ZONE,
    result_summary VARCHAR(2000),
    blocked_reason VARCHAR(1000),
    error_code VARCHAR(160),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX uq_pf_quality_active_product ON pf_quality_process_session(active_product_id);

CREATE TABLE pf_quality_work_item (
    id VARCHAR(80) PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    product_id VARCHAR(120) NOT NULL,
    type VARCHAR(60) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_id VARCHAR(120) NOT NULL,
    source_version BIGINT NOT NULL,
    request_json TEXT NOT NULL,
    target_environment VARCHAR(120) NOT NULL,
    priority INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    claimed_by_session_id VARCHAR(80),
    result_summary VARCHAR(2000),
    error_code VARCHAR(160),
    blocked_reason VARCHAR(1000),
    attempt_count INTEGER NOT NULL,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    retryable BOOLEAN NOT NULL,
    retry_after TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);
CREATE INDEX ix_pf_quality_work_queue ON pf_quality_work_item(product_id,status,retry_after,priority);

CREATE TABLE pf_quality_attempt (
    work_item_id VARCHAR(80) NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    error_code VARCHAR(160),
    reason VARCHAR(1000),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY(work_item_id,attempt_number),
    FOREIGN KEY(work_item_id) REFERENCES pf_quality_work_item(id)
);

CREATE TABLE pf_verification (
    id VARCHAR(80) PRIMARY KEY,
    publication_key VARCHAR(200) NOT NULL UNIQUE,
    product_id VARCHAR(120) NOT NULL,
    work_item_id VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(120) NOT NULL,
    target_version BIGINT NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    environment VARCHAR(120) NOT NULL,
    checks_json TEXT NOT NULL,
    evidence_json TEXT NOT NULL,
    blocked_reason VARCHAR(1000),
    missing_coverage_json TEXT NOT NULL,
    required_commit_sha VARCHAR(40),
    tested_revision VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_pf_verification_target ON pf_verification(product_id,target_type,target_id,created_at);

CREATE TABLE pf_bug (
    id VARCHAR(80) PRIMARY KEY,
    product_id VARCHAR(120) NOT NULL,
    epic_id VARCHAR(80),
    source_story_id VARCHAR(80),
    source_verification_id VARCHAR(80),
    status VARCHAR(40) NOT NULL,
    current_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_pf_bug_product_status ON pf_bug(product_id,status);

CREATE TABLE pf_bug_version (
    bug_id VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(600) NOT NULL,
    actual_behaviour TEXT NOT NULL,
    expected_behaviour TEXT NOT NULL,
    reproduction_steps_json TEXT NOT NULL,
    environment VARCHAR(120) NOT NULL,
    evidence_json TEXT NOT NULL,
    impact VARCHAR(2000) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    source_signal_ids_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(bug_id,version),
    FOREIGN KEY(bug_id) REFERENCES pf_bug(id)
);

CREATE TABLE pf_bug_story (
    bug_id VARCHAR(80) NOT NULL,
    story_id VARCHAR(80) NOT NULL,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(bug_id,story_id),
    FOREIGN KEY(bug_id) REFERENCES pf_bug(id)
);

CREATE TABLE pf_quality_snapshot (
    id VARCHAR(80) PRIMARY KEY,
    session_id VARCHAR(80) NOT NULL UNIQUE,
    product_id VARCHAR(120) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    environment VARCHAR(120) NOT NULL,
    product_revision VARCHAR(160) NOT NULL,
    investigated_areas_json TEXT NOT NULL,
    stale_or_missing_coverage_json TEXT NOT NULL,
    open_bugs_by_severity_json TEXT NOT NULL,
    verification_outcomes_json TEXT NOT NULL,
    risks_json TEXT NOT NULL,
    sources_json TEXT NOT NULL
);
CREATE INDEX ix_pf_quality_snapshot_product ON pf_quality_snapshot(product_id,captured_at);

CREATE TABLE pf_quality_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_reference VARCHAR(120),
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);
