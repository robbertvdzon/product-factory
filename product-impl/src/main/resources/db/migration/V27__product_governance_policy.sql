CREATE TABLE pf_product_governance_policy (
    product_id VARCHAR(100) PRIMARY KEY REFERENCES pf_product(product_id),
    policy_json TEXT NOT NULL,
    version BIGINT NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE pf_product_governance_history (
    product_id VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL,
    policy_json TEXT NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    fingerprint VARCHAR(64) NOT NULL,
    PRIMARY KEY(product_id, version)
);
