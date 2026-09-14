CREATE TABLE pf_advisor_image (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES pf_product_conversation(conversation_id) ON DELETE CASCADE,
    message_id VARCHAR(36) NOT NULL REFERENCES pf_product_conversation_message(message_id) ON DELETE CASCADE,
    task_id VARCHAR(80) NOT NULL,
    artifact_id VARCHAR(80) NOT NULL,
    filename VARCHAR(100) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    caption VARCHAR(500) NOT NULL,
    source_url TEXT,
    environment VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(message_id, artifact_id)
);
CREATE INDEX pf_advisor_image_message_idx ON pf_advisor_image(message_id);
CREATE INDEX pf_advisor_image_conversation_idx ON pf_advisor_image(conversation_id, created_at);
