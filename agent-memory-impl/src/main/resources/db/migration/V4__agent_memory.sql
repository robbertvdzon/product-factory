CREATE TABLE pf_agent_role_definition (
    role_key VARCHAR(160) PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    capability VARCHAR(120) NOT NULL,
    implementation_variant VARCHAR(120) NOT NULL,
    purpose TEXT NOT NULL,
    responsibilities_text TEXT NOT NULL,
    boundaries_text TEXT NOT NULL,
    active BOOLEAN NOT NULL
);

CREATE TABLE pf_agent_memory_budget (
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    role_key VARCHAR(160) NOT NULL REFERENCES pf_agent_role_definition(role_key),
    maximum_active_items INTEGER NOT NULL CHECK (maximum_active_items > 0),
    maximum_item_characters INTEGER NOT NULL CHECK (maximum_item_characters > 0),
    maximum_total_characters INTEGER NOT NULL CHECK (maximum_total_characters > 0),
    PRIMARY KEY (product_id, role_key)
);

CREATE TABLE pf_agent_memory_item (
    memory_item_id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    role_key VARCHAR(160) NOT NULL REFERENCES pf_agent_role_definition(role_key),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX pf_agent_memory_item_scope_idx
    ON pf_agent_memory_item (product_id, role_key, created_at);

CREATE TABLE pf_agent_memory_version (
    memory_version_id VARCHAR(36) PRIMARY KEY,
    memory_item_id VARCHAR(36) NOT NULL REFERENCES pf_agent_memory_item(memory_item_id),
    supersedes_id VARCHAR(36) NULL REFERENCES pf_agent_memory_version(memory_version_id),
    title VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    change_reason TEXT NOT NULL,
    source_meeting_id VARCHAR(36) NULL,
    process_session_id VARCHAR(36) NULL,
    ai_task_id VARCHAR(36) NULL,
    UNIQUE (supersedes_id)
);

CREATE INDEX pf_agent_memory_version_item_idx
    ON pf_agent_memory_version (memory_item_id, created_at);

CREATE TABLE pf_agent_memory_retraction (
    retraction_id VARCHAR(36) PRIMARY KEY,
    memory_item_id VARCHAR(36) NOT NULL UNIQUE REFERENCES pf_agent_memory_item(memory_item_id),
    expected_version_id VARCHAR(36) NOT NULL REFERENCES pf_agent_memory_version(memory_version_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    reason TEXT NOT NULL,
    source_meeting_id VARCHAR(36) NULL,
    process_session_id VARCHAR(36) NULL,
    ai_task_id VARCHAR(36) NULL
);

CREATE TABLE pf_agent_memory_read_audit (
    read_id VARCHAR(36) PRIMARY KEY,
    memory_version_id VARCHAR(36) NOT NULL REFERENCES pf_agent_memory_version(memory_version_id),
    process_session_id VARCHAR(36) NULL,
    meeting_id VARCHAR(36) NULL,
    ai_task_id VARCHAR(36) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (memory_version_id, ai_task_id)
);

CREATE TABLE pf_agent_memory_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    command_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_ids TEXT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);
