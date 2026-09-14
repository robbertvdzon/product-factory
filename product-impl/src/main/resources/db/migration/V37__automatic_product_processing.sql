-- One project-wide pause replaces the separate dispatch switch. Existing active
-- projects explicitly move to automatic processing; historical schedules are retained.
ALTER TABLE pf_product ALTER COLUMN dispatching_enabled SET DEFAULT TRUE;
UPDATE pf_product SET dispatching_enabled=TRUE,version=version+1 WHERE dispatching_enabled=FALSE;
CREATE TABLE pf_automation_state (
    product_id VARCHAR(100) PRIMARY KEY REFERENCES pf_product(product_id) ON DELETE CASCADE,
    checked_at TIMESTAMP WITH TIME ZONE,
    next_check_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    lease_owner VARCHAR(36)
);
CREATE TABLE pf_automation_process (
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id) ON DELETE CASCADE,
    process VARCHAR(50) NOT NULL,
    input_key VARCHAR(64),
    failure_count INTEGER NOT NULL DEFAULT 0,
    retry_after TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(160),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(product_id,process)
);
