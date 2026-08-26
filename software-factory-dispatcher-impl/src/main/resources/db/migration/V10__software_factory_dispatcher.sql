CREATE TABLE pf_dispatcher_process_session (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    active_product_id VARCHAR(100) UNIQUE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('RUNNING','BLOCKED','SUCCEEDED','FAILED','CANCELLED')),
    implementation_artifact VARCHAR(120) NOT NULL,
    implementation_variant VARCHAR(80) NOT NULL,
    implementation_version VARCHAR(80) NOT NULL,
    implementation_revision VARCHAR(80) NOT NULL,
    inputs_json TEXT NOT NULL,
    publications_json TEXT NOT NULL,
    result_summary VARCHAR(2000),
    blocked_reason VARCHAR(1000),
    error_code VARCHAR(160),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_pf_dispatcher_session_product_started ON pf_dispatcher_process_session(product_id,started_at);

CREATE TABLE pf_delivery_attempt (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    story_id VARCHAR(36) NOT NULL REFERENCES pf_story(id),
    story_version BIGINT NOT NULL,
    reservation_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    package_hash VARCHAR(64) NOT NULL,
    package_json TEXT NOT NULL,
    external_story_id VARCHAR(200),
    external_status VARCHAR(20) CHECK (external_status IN ('OPEN','DONE','CANCELLED')),
    delivered_commit_sha VARCHAR(64),
    status VARCHAR(40) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','RETRYABLE_FAILURE','CONFIGURATION_FAILURE','AUTHORIZATION_FAILURE','CONTRACT_FAILURE','COMPLETED','CANCELLED')),
    attempt_count INTEGER NOT NULL,
    retry_after TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(160),
    last_error_message VARCHAR(1000),
    local_command_status VARCHAR(30) NOT NULL CHECK (local_command_status IN ('NOT_REQUIRED','PENDING','APPLIED','FAILED')),
    last_session_id VARCHAR(36) REFERENCES pf_dispatcher_process_session(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(reservation_id),
    UNIQUE(idempotency_key),
    UNIQUE(external_story_id)
);
CREATE INDEX idx_pf_delivery_attempt_product_status ON pf_delivery_attempt(product_id,status,updated_at);

CREATE TABLE pf_dispatcher_product_state (
    product_id VARCHAR(100) PRIMARY KEY REFERENCES pf_product(product_id),
    blocked BOOLEAN NOT NULL,
    blocked_reason VARCHAR(1000),
    last_attempt_id VARCHAR(36),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
