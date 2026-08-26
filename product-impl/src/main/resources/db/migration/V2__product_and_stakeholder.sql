CREATE TABLE pf_product (
    product_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    dispatching_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_type VARCHAR(40) NOT NULL,
    updated_by_id VARCHAR(320) NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0)
);

CREATE TABLE pf_product_assignment (
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    version BIGINT NOT NULL CHECK (version > 0),
    audience TEXT NOT NULL,
    goal TEXT NOT NULL,
    hard_boundaries_json TEXT NOT NULL,
    public_git_url VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    PRIMARY KEY (product_id, version)
);

CREATE TABLE pf_testable_product_configuration (
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    version BIGINT NOT NULL CHECK (version > 0),
    acceptance_json TEXT NOT NULL,
    production_json TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    PRIMARY KEY (product_id, version)
);

CREATE TABLE pf_process_schedule (
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    process VARCHAR(60) NOT NULL CHECK (process IN ('PRODUCT_DESIGN', 'PRODUCT_PLANNING', 'QUALITY_ASSURANCE', 'SOFTWARE_FACTORY_DISPATCHER')),
    enabled BOOLEAN NOT NULL,
    timezone VARCHAR(100) NOT NULL,
    pattern_json TEXT NOT NULL,
    next_run_at TIMESTAMP WITH TIME ZONE NULL,
    last_scheduled_at TIMESTAMP WITH TIME ZONE NULL,
    last_skipped_at TIMESTAMP WITH TIME ZONE NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    PRIMARY KEY (product_id, process)
);

CREATE INDEX pf_process_schedule_due_idx
    ON pf_process_schedule (enabled, next_run_at);

CREATE TABLE pf_user_signal (
    signal_id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    category VARCHAR(40) NOT NULL CHECK (category IN ('FEEDBACK', 'PROBLEM', 'CONCERN', 'OPPORTUNITY', 'QUALITY_CONCERN', 'QUALITY_PATTERN')),
    urgency VARCHAR(20) NOT NULL CHECK (urgency IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    source VARCHAR(300) NOT NULL,
    signal_text TEXT NOT NULL,
    attachments_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'IN_REVIEW', 'PROCESSED')),
    verification_id VARCHAR(36) NULL,
    outcome TEXT NULL,
    epic_id VARCHAR(36) NULL,
    epic_version BIGINT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_type VARCHAR(40) NOT NULL,
    updated_by_id VARCHAR(320) NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0)
);

CREATE INDEX pf_user_signal_filter_idx
    ON pf_user_signal (product_id, status, category, urgency, created_at);

CREATE TABLE pf_stakeholder_question (
    question_id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    agent_role VARCHAR(160) NOT NULL,
    question TEXT NOT NULL,
    context TEXT NOT NULL,
    process_session_id VARCHAR(36) NOT NULL,
    linked_objects_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'ANSWERED', 'WITHDRAWN')),
    answer TEXT NULL,
    meeting_id VARCHAR(36) NULL,
    answer_message_id VARCHAR(36) NULL,
    withdrawal_reason TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    answered_at TIMESTAMP WITH TIME ZONE NULL,
    withdrawn_at TIMESTAMP WITH TIME ZONE NULL,
    updated_by_type VARCHAR(40) NOT NULL,
    updated_by_id VARCHAR(320) NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0)
);

CREATE INDEX pf_stakeholder_question_filter_idx
    ON pf_stakeholder_question (product_id, status, agent_role, created_at);

CREATE TABLE pf_meeting (
    meeting_id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    reason TEXT NOT NULL,
    agenda_json TEXT NOT NULL,
    linked_objects_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'OPEN', 'CLOSED')),
    minutes TEXT NULL,
    outcomes_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE NULL,
    updated_by_type VARCHAR(40) NOT NULL,
    updated_by_id VARCHAR(320) NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0)
);

CREATE INDEX pf_meeting_filter_idx
    ON pf_meeting (product_id, status, created_at);

CREATE TABLE pf_meeting_message (
    message_id VARCHAR(36) PRIMARY KEY,
    meeting_id VARCHAR(36) NOT NULL REFERENCES pf_meeting(meeting_id),
    sender_role VARCHAR(40) NOT NULL CHECK (sender_role IN ('STAKEHOLDER', 'MEETING_AGENT', 'SYSTEM')),
    represented_agent_role VARCHAR(160) NULL,
    message_text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    sequence_number BIGINT NOT NULL,
    UNIQUE (meeting_id, sequence_number)
);

CREATE TABLE pf_product_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    command_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_id VARCHAR(100) NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);
