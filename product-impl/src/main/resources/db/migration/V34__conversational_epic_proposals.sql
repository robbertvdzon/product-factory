ALTER TABLE pf_product_advisor_turn ADD COLUMN expected_epic_content_version BIGINT;
ALTER TABLE pf_product_advisor_turn ADD COLUMN result_content_version BIGINT;
ALTER TABLE pf_product_advisor_turn ADD COLUMN refinement_reverted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pf_product_advisor_turn ADD COLUMN refinement_summary TEXT;
