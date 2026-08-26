CREATE TABLE pf_planning_work_item (
    id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    type VARCHAR(40) NOT NULL CHECK (type IN ('PLAN_BUGFIX','PLAN_EPIC_GAP','REPLAN_CANCELLED_DEPENDENCY','REPRIORITIZE_EPIC','MANUAL_REPLAN')),
    source_type VARCHAR(80) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    source_version BIGINT NOT NULL,
    explanation TEXT NOT NULL,
    priority INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','DONE','BLOCKED','FAILED')),
    claimed_by_session_id VARCHAR(36),
    result_summary VARCHAR(2000),
    error_code VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);
CREATE INDEX idx_pf_planning_work_product_status ON pf_planning_work_item(product_id,status,priority,created_at);

CREATE TABLE pf_planning_process_session (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    active_product_id VARCHAR(100) UNIQUE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('RUNNING','WAITING_FOR_AI','BLOCKED','SUCCEEDED','FAILED','CANCELLED')),
    phase VARCHAR(30) NOT NULL CHECK (phase IN ('SELECTING','PLANNING','COMPLETED')),
    implementation_artifact VARCHAR(120) NOT NULL,
    implementation_variant VARCHAR(80) NOT NULL,
    implementation_version VARCHAR(80) NOT NULL,
    implementation_revision VARCHAR(80) NOT NULL,
    inputs_json TEXT NOT NULL,
    snapshot_json TEXT,
    selected_epics_json TEXT NOT NULL,
    claimed_work_items_json TEXT NOT NULL,
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
CREATE INDEX idx_pf_planning_session_product_started ON pf_planning_process_session(product_id,started_at);

CREATE TABLE pf_story (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    epic_id VARCHAR(36) NOT NULL,
    epic_version BIGINT NOT NULL,
    bug_id VARCHAR(80),
    bug_version BIGINT,
    type VARCHAR(30) NOT NULL CHECK (type IN ('PRODUCT_STORY','BUGFIX')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('TODO','IN_PROGRESS','DONE','CANCELLED')),
    current_version BIGINT NOT NULL,
    sequence_number BIGINT NOT NULL,
    priority_reason VARCHAR(1000),
    external_story_id VARCHAR(200),
    delivered_commit_sha VARCHAR(40),
    cancellation_reason VARCHAR(1000),
    verification_id VARCHAR(80),
    verification_passed BOOLEAN,
    bug_link_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(product_id,sequence_number)
);
CREATE INDEX idx_pf_story_product_status_sequence ON pf_story(product_id,status,sequence_number);
CREATE INDEX idx_pf_story_epic ON pf_story(epic_id,epic_version,status);

CREATE TABLE pf_story_version (
    story_id VARCHAR(36) NOT NULL REFERENCES pf_story(id),
    version BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(600) NOT NULL,
    content TEXT NOT NULL,
    acceptance_criteria_json TEXT NOT NULL,
    ux_design TEXT,
    dependencies_json TEXT NOT NULL,
    source_references_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(story_id,version)
);

CREATE TABLE pf_epic_cancellation_marker (
    epic_id VARCHAR(36) NOT NULL,
    epic_version BIGINT NOT NULL,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    reason VARCHAR(1000) NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(epic_id,epic_version)
);

CREATE TABLE pf_story_dispatch_reservation (
    id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    active_product_id VARCHAR(100) UNIQUE,
    story_id VARCHAR(36) NOT NULL REFERENCES pf_story(id),
    story_version BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('RESERVED','DISPATCHED','RELEASED','CANCELLED')),
    external_story_id VARCHAR(200),
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_planning_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_reference VARCHAR(100),
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_planning_quality_effect (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    effect_type VARCHAR(40) NOT NULL,
    payload_json TEXT NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
