-- Publication, not completion of design, makes a conversation part of an epic.
-- Repair only an unambiguous live epic for the current request version.
UPDATE pf_product_request SET linked_epic_id=(
    SELECT e.id FROM pf_epic e WHERE e.source_product_request_id=pf_product_request.request_id
    AND e.source_product_request_version=pf_product_request.current_version AND e.deleted_at IS NULL
),updated_at=CURRENT_TIMESTAMP,version=version+1
WHERE linked_epic_id IS NULL AND status<>'CANCELLED' AND 1=(
    SELECT count(*) FROM pf_epic e WHERE e.source_product_request_id=pf_product_request.request_id
    AND e.source_product_request_version=pf_product_request.current_version AND e.deleted_at IS NULL
);

UPDATE pf_product_conversation SET epic_id=(
    SELECT r.linked_epic_id FROM pf_product_request r WHERE r.conversation_id=pf_product_conversation.conversation_id
),purpose='EPIC',updated_at=CURRENT_TIMESTAMP,version=version+1
WHERE epic_id IS NULL AND deleted_at IS NULL AND EXISTS (
    SELECT 1 FROM pf_product_request r JOIN pf_epic e ON e.id=r.linked_epic_id
    WHERE r.conversation_id=pf_product_conversation.conversation_id AND r.status<>'CANCELLED' AND e.deleted_at IS NULL
);

UPDATE pf_design_work_item SET epic_id=(
    SELECT r.linked_epic_id FROM pf_product_request r WHERE r.request_id=pf_design_work_item.request_id
)
WHERE epic_id IS NULL AND EXISTS (
    SELECT 1 FROM pf_product_request r JOIN pf_epic e ON e.id=r.linked_epic_id
    WHERE r.request_id=pf_design_work_item.request_id AND r.current_version=pf_design_work_item.request_version
    AND r.status<>'CANCELLED' AND e.deleted_at IS NULL
);
