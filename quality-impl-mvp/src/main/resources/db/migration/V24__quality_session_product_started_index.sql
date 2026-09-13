-- Sessielijsten filteren en begrenzen in SQL (product_id + started_at DESC + LIMIT).
-- Ontwerp, planning en dispatcher hebben deze index al sinds V7/V8/V10; kwaliteit nog niet.
CREATE INDEX idx_pf_quality_session_product_started ON pf_quality_process_session(product_id,started_at);
