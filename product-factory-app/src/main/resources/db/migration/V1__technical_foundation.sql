CREATE TABLE environment_metadata (
    metadata_key VARCHAR(100) PRIMARY KEY,
    metadata_value VARCHAR(500) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE authentication_session (
    session_id VARCHAR(64) PRIMARY KEY,
    stakeholder_email VARCHAR(320) NOT NULL,
    csrf_token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX authentication_session_expires_at_idx
    ON authentication_session (expires_at);
