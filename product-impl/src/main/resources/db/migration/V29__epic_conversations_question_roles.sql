ALTER TABLE pf_product_conversation ADD COLUMN epic_id VARCHAR(36);
ALTER TABLE pf_product_conversation ADD COLUMN audience_role VARCHAR(30) NOT NULL DEFAULT 'PRODUCT_OWNER';
CREATE INDEX pf_product_conversation_epic_idx ON pf_product_conversation(epic_id);
ALTER TABLE pf_stakeholder_question ADD COLUMN requested_role VARCHAR(30) NOT NULL DEFAULT 'PRODUCT_OWNER';
