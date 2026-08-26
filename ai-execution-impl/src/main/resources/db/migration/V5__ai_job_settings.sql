CREATE TABLE pf_ai_job_definition (
    job_key VARCHAR(160) PRIMARY KEY,
    display_name VARCHAR(240) NOT NULL,
    default_provider VARCHAR(20) NOT NULL CHECK (default_provider IN ('CODEX', 'CLAUDE', 'MOCKED')),
    default_model VARCHAR(200) NOT NULL,
    default_enabled BOOLEAN NOT NULL
);

CREATE TABLE pf_ai_job_configuration (
    job_key VARCHAR(160) NOT NULL REFERENCES pf_ai_job_definition(job_key),
    version BIGINT NOT NULL CHECK (version > 0),
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('CODEX', 'CLAUDE', 'MOCKED')),
    model VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    PRIMARY KEY (job_key, version)
);

CREATE TABLE pf_ai_settings_command (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    job_key VARCHAR(160) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_version BIGINT NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);
