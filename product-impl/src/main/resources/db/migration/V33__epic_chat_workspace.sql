ALTER TABLE pf_product_conversation ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE pf_product_conversation_message ADD COLUMN author_role VARCHAR(30);
ALTER TABLE pf_product_advisor_turn ADD COLUMN intent VARCHAR(30) NOT NULL DEFAULT 'DISCUSS';
ALTER TABLE pf_product_advisor_turn ADD COLUMN expected_epic_version BIGINT;
UPDATE pf_product_conversation SET epic_id=(SELECT r.linked_epic_id FROM pf_product_request r WHERE r.conversation_id=pf_product_conversation.conversation_id)
WHERE epic_id IS NULL AND EXISTS (SELECT 1 FROM pf_product_request r WHERE r.conversation_id=pf_product_conversation.conversation_id AND r.linked_epic_id IS NOT NULL);
UPDATE pf_product_conversation SET purpose='EPIC' WHERE epic_id IS NOT NULL OR EXISTS (SELECT 1 FROM pf_product_request r WHERE r.conversation_id=pf_product_conversation.conversation_id AND r.request_type='EPIC_CANDIDATE');
CREATE TABLE pf_conversation_attachment (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES pf_product_conversation(conversation_id) ON DELETE CASCADE,
    message_id VARCHAR(36) NOT NULL REFERENCES pf_product_conversation_message(message_id) ON DELETE CASCADE,
    filename VARCHAR(200) NOT NULL,
    media_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_base64 TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX pf_conversation_attachment_conversation_idx ON pf_conversation_attachment(conversation_id);

ALTER TABLE pf_product_advisor_turn ADD COLUMN refinement_content_version BIGINT;
ALTER TABLE pf_product_advisor_turn ADD COLUMN refinement_completed BOOLEAN NOT NULL DEFAULT FALSE;
