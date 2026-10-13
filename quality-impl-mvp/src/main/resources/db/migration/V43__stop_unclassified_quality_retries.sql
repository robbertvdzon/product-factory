-- Older releases scheduled every blocker/failure again, without recording whether
-- it was a temporary technical failure. Preserve evidence and manual recovery,
-- but do not keep replaying these unclassified results automatically.
UPDATE pf_quality_work_item
SET retry_after = NULL,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE status IN ('BLOCKED', 'FAILED')
  AND retry_after IS NOT NULL;
