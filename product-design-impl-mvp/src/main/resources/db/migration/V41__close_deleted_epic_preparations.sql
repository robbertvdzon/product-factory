-- A draft can be published before the final request/conversation link is filled.
-- Include the epic's source request, not just the finished linked_epic_id path.
UPDATE pf_product_request SET status='CANCELLED',delivery_status='CANCELLED',updated_at=CURRENT_TIMESTAMP,version=version+1
WHERE status<>'CANCELLED' AND EXISTS (
    SELECT 1 FROM pf_epic e WHERE e.deleted_at IS NOT NULL
    AND (e.id=pf_product_request.linked_epic_id OR e.source_product_request_id=pf_product_request.request_id)
);

UPDATE pf_ai_task SET cancel_requested=TRUE,cancel_reason='De bijbehorende epic is verwijderd.',updated_at=CURRENT_TIMESTAMP
WHERE status NOT IN ('SUCCEEDED','FAILED','CANCELLED') AND id IN (
    SELECT s.current_ai_task_id FROM pf_design_process_session s
    JOIN pf_design_work_item w ON w.process_session_id=s.id
    JOIN pf_product_request r ON r.request_id=w.request_id
    JOIN pf_epic e ON e.id=r.linked_epic_id OR e.source_product_request_id=r.request_id
    WHERE e.deleted_at IS NOT NULL AND s.active_product_id IS NOT NULL
);

UPDATE pf_design_process_session SET status='CANCELLED',active_product_id=NULL,call_claimed_until=NULL,
    error_code='EPIC_DELETED',blocked_reason=NULL,result_summary='De bijbehorende epic is verwijderd.',
    updated_at=CURRENT_TIMESTAMP,finished_at=CURRENT_TIMESTAMP
WHERE active_product_id IS NOT NULL AND id IN (
    SELECT w.process_session_id FROM pf_design_work_item w
    JOIN pf_product_request r ON r.request_id=w.request_id
    JOIN pf_epic e ON e.id=r.linked_epic_id OR e.source_product_request_id=r.request_id
    WHERE e.deleted_at IS NOT NULL
);

UPDATE pf_design_work_item SET status='FAILED',updated_at=CURRENT_TIMESTAMP
WHERE status NOT IN ('DONE','FAILED') AND request_id IN (
    SELECT r.request_id FROM pf_product_request r
    JOIN pf_epic e ON e.id=r.linked_epic_id OR e.source_product_request_id=r.request_id
    WHERE e.deleted_at IS NOT NULL
);

UPDATE pf_product_request_route SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP
WHERE status NOT IN ('DONE','CANCELLED') AND request_id IN (
    SELECT r.request_id FROM pf_product_request r
    JOIN pf_epic e ON e.id=r.linked_epic_id OR e.source_product_request_id=r.request_id
    WHERE e.deleted_at IS NOT NULL
);

UPDATE pf_product_conversation SET deleted_at=CURRENT_TIMESTAMP,status='CLOSED',updated_at=CURRENT_TIMESTAMP,version=version+1
WHERE deleted_at IS NULL AND (
    epic_id IN (SELECT id FROM pf_epic WHERE deleted_at IS NOT NULL)
    OR conversation_id IN (
        SELECT r.conversation_id FROM pf_product_request r
        JOIN pf_epic e ON e.id=r.linked_epic_id OR e.source_product_request_id=r.request_id
        WHERE e.deleted_at IS NOT NULL
    )
);

UPDATE pf_product_advisor_turn SET status='BLOCKED',safe_error_code='CONVERSATION_DELETED',updated_at=CURRENT_TIMESTAMP
WHERE status IN ('PENDING','WAITING_FOR_AI')
AND conversation_id IN (SELECT conversation_id FROM pf_product_conversation WHERE deleted_at IS NOT NULL);

DELETE FROM pf_personal_notification WHERE target_type='CONVERSATION'
AND target_id IN (SELECT conversation_id FROM pf_product_conversation WHERE deleted_at IS NOT NULL);

UPDATE pf_stakeholder_question SET status='WITHDRAWN',withdrawal_reason='De bijbehorende epic is verwijderd.',
    withdrawn_at=CURRENT_TIMESTAMP,updated_by_type='SYSTEM',updated_by_id='epic-deletion',version=version+1
WHERE status='OPEN' AND (
    epic_link_id IN (SELECT id FROM pf_epic WHERE deleted_at IS NOT NULL)
    OR product_request_id IN (
        SELECT r.request_id FROM pf_product_request r
        JOIN pf_epic e ON e.id=r.linked_epic_id OR e.source_product_request_id=r.request_id
        WHERE e.deleted_at IS NOT NULL
    )
);
