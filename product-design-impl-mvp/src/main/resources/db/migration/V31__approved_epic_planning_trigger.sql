CREATE TABLE pf_approved_epic_planning_trigger (
    epic_id VARCHAR(36) NOT NULL REFERENCES pf_epic(id),
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    content_version BIGINT NOT NULL,
    policy_version BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','STARTED')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (epic_id, content_version, policy_version)
);

CREATE INDEX pf_approved_epic_planning_trigger_pending_idx
    ON pf_approved_epic_planning_trigger(status, next_attempt_at, created_at);
