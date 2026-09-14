ALTER TABLE authentication_session ADD COLUMN viewed_user_id VARCHAR(36) REFERENCES pf_user_account(user_id);
ALTER TABLE authentication_session ADD COLUMN viewed_acting_role VARCHAR(30)
    CHECK (viewed_acting_role IS NULL OR viewed_acting_role IN ('FACTORY_OWNER','PRODUCT_OWNER','ARCHITECT'));

CREATE INDEX authentication_session_viewed_user_idx
    ON authentication_session(viewed_user_id, expires_at);
