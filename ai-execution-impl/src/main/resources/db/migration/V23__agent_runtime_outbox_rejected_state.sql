-- Een aanvraag die de Agent Runtime definitief afwijst (bijv. RESPONSE_SCHEMA_UNSUPPORTED) markeert de taak
-- FAILED. De outboxrij bleef tot nu toe echter oppakbaar en werd elke paar seconden opnieuw verstuurd.
-- REJECTED is een eindtoestand die de dispatcher niet meer oppakt.
ALTER TABLE pf_ai_runtime_outbox DROP CONSTRAINT chk_pf_ai_runtime_outbox_state;
ALTER TABLE pf_ai_runtime_outbox ADD CONSTRAINT chk_pf_ai_runtime_outbox_state
    CHECK (dispatch_state IN ('PENDING_UPLOAD', 'REQUEST_FROZEN', 'DISPATCHED', 'CANCELLED', 'REJECTED'));

UPDATE pf_ai_runtime_outbox
SET dispatch_state = 'REJECTED', retry_after = NULL
WHERE dispatched_at IS NULL
  AND EXISTS (SELECT 1 FROM pf_ai_task t WHERE t.id = pf_ai_runtime_outbox.task_id AND t.status IN ('FAILED', 'CANCELLED'));
