CREATE TABLE pf_schedule_run (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES pf_product(product_id),
    process VARCHAR(50) NOT NULL CHECK (process IN ('PRODUCT_DESIGN','PRODUCT_PLANNING','QUALITY_ASSURANCE','SOFTWARE_FACTORY_DISPATCHER')),
    scheduled_for TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CLAIMED','SUCCEEDED','SKIPPED','FAILED')),
    result_summary VARCHAR(1000),
    error_code VARCHAR(160),
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(product_id,process,scheduled_for)
);
CREATE INDEX idx_pf_schedule_run_product_claimed ON pf_schedule_run(product_id,claimed_at);
