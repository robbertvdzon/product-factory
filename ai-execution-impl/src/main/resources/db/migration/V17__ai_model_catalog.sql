CREATE TABLE pf_ai_model_catalog (
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('CODEX', 'CLAUDE', 'MOCKED')),
    model VARCHAR(200) NOT NULL,
    available BOOLEAN NOT NULL,
    matching_online_workers INTEGER NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (provider, model)
);
