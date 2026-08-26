CREATE TABLE pf_decision (
    decision_id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL,
    origin VARCHAR(20) NOT NULL CHECK (origin IN ('STAKEHOLDER', 'FACTORY')),
    state VARCHAR(20) NOT NULL CHECK (state IN ('ACTIVE', 'WITHDRAWN', 'SUPERSEDED')),
    superseded_by_decision_id VARCHAR(36) NULL,
    withdrawal_reason TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    CONSTRAINT pf_decision_successor_fk FOREIGN KEY (superseded_by_decision_id) REFERENCES pf_decision(decision_id)
);

CREATE INDEX pf_decision_product_state_idx
    ON pf_decision (product_id, state, created_at);

CREATE TABLE pf_decision_details (
    details_id VARCHAR(36) PRIMARY KEY,
    decision_id VARCHAR(36) NOT NULL REFERENCES pf_decision(decision_id),
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NULL,
    current_marker SMALLINT NULL CHECK (current_marker = 1),
    decision_text TEXT NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL
);

CREATE INDEX pf_decision_details_validity_idx
    ON pf_decision_details (decision_id, valid_from, valid_until);

CREATE UNIQUE INDEX pf_decision_details_one_current_idx
    ON pf_decision_details (decision_id, current_marker);

CREATE TABLE pf_decision_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    command_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_id VARCHAR(100) NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);
