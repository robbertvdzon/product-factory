CREATE TABLE pf_quality_publication_repair (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    failed_session_id VARCHAR(80) NOT NULL UNIQUE REFERENCES pf_quality_process_session(id),
    source_task_id VARCHAR(80) NOT NULL,
    recovery_session_id VARCHAR(80) NOT NULL UNIQUE REFERENCES pf_quality_process_session(id),
    source_response_sha256 VARCHAR(64) NOT NULL,
    source_manifest_sha256 VARCHAR(64) NOT NULL,
    source_context_sha256 VARCHAR(64) NOT NULL,
    expected_work_item_versions_json TEXT NOT NULL,
    removed_artifact_names_json TEXT NOT NULL,
    corrected_response_json TEXT NOT NULL,
    corrected_response_sha256 VARCHAR(64) NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(320) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
