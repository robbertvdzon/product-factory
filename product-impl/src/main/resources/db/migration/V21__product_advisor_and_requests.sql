CREATE TABLE pf_user_account (
    user_id VARCHAR(36) PRIMARY KEY,
    normalized_email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_user_global_role (
    user_id VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    role VARCHAR(40) NOT NULL CHECK (role IN ('FACTORY_OWNER')),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    granted_by VARCHAR(36),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE pf_product_membership (
    user_id VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    role VARCHAR(40) NOT NULL CHECK (role IN ('PRODUCT_OWNER')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    granted_by VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by VARCHAR(36) REFERENCES pf_user_account(user_id),
    revoke_reason TEXT,
    version BIGINT NOT NULL CHECK (version > 0),
    PRIMARY KEY (user_id, product_id, role)
);
CREATE INDEX pf_product_membership_product_idx ON pf_product_membership(product_id, status);

CREATE TABLE pf_product_membership_history (
    history_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    role VARCHAR(40) NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('GRANTED','REVOKED')),
    reason TEXT,
    actor_user_id VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_user_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    command_type VARCHAR(80) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_id VARCHAR(100) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE authentication_session ADD COLUMN user_id VARCHAR(36) REFERENCES pf_user_account(user_id);
CREATE INDEX authentication_session_user_idx ON authentication_session(user_id, expires_at);

CREATE TABLE pf_product_conversation (
    conversation_id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    title VARCHAR(200) NOT NULL,
    created_by VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('OPEN','PROCESSING','WAITING_FOR_USER','PROPOSAL_READY','BLOCKED','CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0)
);
CREATE INDEX pf_product_conversation_filter_idx ON pf_product_conversation(product_id, updated_at);

CREATE TABLE pf_product_conversation_message (
    message_id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES pf_product_conversation(conversation_id),
    sequence_number BIGINT NOT NULL,
    sender VARCHAR(30) NOT NULL CHECK (sender IN ('USER','PRODUCT_ADVISOR','SYSTEM')),
    message_text TEXT NOT NULL,
    created_by VARCHAR(36) REFERENCES pf_user_account(user_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    UNIQUE (conversation_id, sequence_number),
    UNIQUE (conversation_id, idempotency_key)
);

CREATE TABLE pf_product_advisor_turn (
    turn_id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES pf_product_conversation(conversation_id),
    source_message_id VARCHAR(36) NOT NULL REFERENCES pf_product_conversation_message(message_id),
    source_conversation_version BIGINT NOT NULL,
    ai_task_id VARCHAR(36),
    context_snapshot_json TEXT,
    prompt_text TEXT,
    git_url TEXT,
    git_commit_sha VARCHAR(40),
    execution_vendor VARCHAR(100),
    execution_model VARCHAR(200),
    execution_mode VARCHAR(30),
    configuration_version BIGINT,
    prompt_template_version BIGINT,
    memory_version_ids_json TEXT,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','WAITING_FOR_AI','APPLIED','BLOCKED')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    safe_error_code VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    UNIQUE (source_message_id)
);

CREATE TABLE pf_advisor_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    command_type VARCHAR(80) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_id VARCHAR(100) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pf_product_request (
    request_id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    conversation_id VARCHAR(36) NOT NULL REFERENCES pf_product_conversation(conversation_id),
    requested_by VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    request_type VARCHAR(30) NOT NULL CHECK (request_type IN ('HOTFIX','BUGFIX','EPIC_CANDIDATE')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PROPOSED','APPROVED','ROUTING','ROUTED','ROUTING_FAILED','CANCELLED')),
    current_version BIGINT NOT NULL,
    linked_epic_id VARCHAR(36),
    external_story_key VARCHAR(200),
    delivery_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED' CHECK (delivery_status IN ('NOT_STARTED','OPEN','DONE','CANCELLED','FAILED')),
    delivered_commit_sha VARCHAR(40),
    safe_error_code VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    UNIQUE (conversation_id)
);
CREATE INDEX pf_product_request_filter_idx ON pf_product_request(product_id, status, updated_at);

CREATE TABLE pf_product_request_version (
    request_id VARCHAR(36) NOT NULL REFERENCES pf_product_request(request_id),
    version BIGINT NOT NULL,
    request_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    problem TEXT NOT NULL,
    user_impact TEXT NOT NULL,
    current_behavior TEXT NOT NULL,
    desired_behavior TEXT NOT NULL,
    evidence_json TEXT NOT NULL,
    git_commit_sha VARCHAR(40) NOT NULL,
    acceptance_criteria_json TEXT NOT NULL,
    scope_json TEXT NOT NULL,
    boundaries_json TEXT NOT NULL,
    excluded_hotfix_categories_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (request_id, version)
);

CREATE TABLE pf_product_request_approval (
    request_id VARCHAR(36) NOT NULL REFERENCES pf_product_request(request_id),
    request_version BIGINT NOT NULL,
    user_id VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    PRIMARY KEY (request_id, request_version, user_id)
);

CREATE TABLE pf_product_request_route (
    request_id VARCHAR(36) NOT NULL REFERENCES pf_product_request(request_id),
    request_version BIGINT NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    route_type VARCHAR(30) NOT NULL,
    package_json TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    external_key VARCHAR(200),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','OPEN','DONE','CANCELLED','FAILED','RETRY','BLOCKED')),
    retry_after TIMESTAMP WITH TIME ZONE,
    safe_error_code VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (request_id, request_version)
);

CREATE TABLE pf_design_work_item (
    work_item_id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    request_id VARCHAR(36) NOT NULL REFERENCES pf_product_request(request_id),
    request_version BIGINT NOT NULL,
    purpose VARCHAR(60) NOT NULL CHECK (purpose IN ('CREATE_EPIC_FROM_PRODUCT_REQUEST')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','WAITING_FOR_USER','DONE','BLOCKED','FAILED')),
    process_session_id VARCHAR(36),
    epic_id VARCHAR(36),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    claimed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    UNIQUE (request_id, request_version)
);

CREATE TABLE pf_epic_approval_record (
    epic_id VARCHAR(36) NOT NULL REFERENCES pf_epic(id),
    epic_version BIGINT NOT NULL,
    approval_role VARCHAR(30) NOT NULL CHECK (approval_role IN ('PRODUCT_OWNER','FACTORY_OWNER')),
    user_id VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    PRIMARY KEY (epic_id, epic_version, approval_role)
);

CREATE TABLE pf_personal_notification (
    notification_id VARCHAR(36) PRIMARY KEY,
    recipient_user_id VARCHAR(36) NOT NULL REFERENCES pf_user_account(user_id),
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    event_key VARCHAR(200) NOT NULL,
    kind VARCHAR(60) NOT NULL,
    title VARCHAR(300) NOT NULL,
    target_type VARCHAR(60) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    UNIQUE (recipient_user_id, event_key)
);

ALTER TABLE pf_stakeholder_question ADD COLUMN requested_respondent_user_id VARCHAR(36) REFERENCES pf_user_account(user_id);
ALTER TABLE pf_stakeholder_question ADD COLUMN product_request_id VARCHAR(36) REFERENCES pf_product_request(request_id);
ALTER TABLE pf_stakeholder_question ADD COLUMN epic_link_id VARCHAR(36);
ALTER TABLE pf_stakeholder_question ADD COLUMN story_link_id VARCHAR(36);

ALTER TABLE pf_epic DROP CONSTRAINT IF EXISTS pf_epic_status_check;
ALTER TABLE pf_epic ALTER COLUMN status TYPE VARCHAR(40);
ALTER TABLE pf_epic_version ALTER COLUMN status TYPE VARCHAR(40);
ALTER TABLE pf_epic ADD CONSTRAINT pf_epic_status_check CHECK (status IN (
    'NEEDS_RESEARCH','NEEDS_REFINEMENT','AWAITING_APPROVAL','AWAITING_PRODUCT_OWNER_APPROVAL',
    'AWAITING_FACTORY_OWNER_APPROVAL','AVAILABLE','IN_PLANNING','ACTIVE','VERIFYING',
    'COMPLETED','NOT_SUCCESSFUL','SUPERSEDED','WITHDRAWN','CANCELLED'
));
ALTER TABLE pf_epic ADD COLUMN source_product_request_id VARCHAR(36) REFERENCES pf_product_request(request_id);
ALTER TABLE pf_epic ADD COLUMN source_product_request_version BIGINT;
