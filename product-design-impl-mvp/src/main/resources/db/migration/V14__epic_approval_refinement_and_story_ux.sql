ALTER TABLE pf_product
    ADD COLUMN epic_approval_mode VARCHAR(20) NOT NULL DEFAULT 'AUTOMATIC'
        CHECK (epic_approval_mode IN ('AUTOMATIC','MANUAL'));

ALTER TABLE pf_epic DROP CONSTRAINT IF EXISTS pf_epic_status_check;
ALTER TABLE pf_epic ADD CONSTRAINT pf_epic_status_check CHECK (status IN (
    'NEEDS_RESEARCH','NEEDS_REFINEMENT','AWAITING_APPROVAL','AVAILABLE','IN_PLANNING','ACTIVE','VERIFYING',
    'COMPLETED','NOT_SUCCESSFUL','SUPERSEDED','WITHDRAWN','CANCELLED'
));
ALTER TABLE pf_epic ADD COLUMN refinement_reason TEXT;
ALTER TABLE pf_epic_version ADD COLUMN refinement_reason TEXT;

ALTER TABLE pf_story_version ADD COLUMN ux_artifacts_json TEXT NOT NULL DEFAULT '[]';
UPDATE pf_story_version
SET ux_artifacts_json = COALESCE((
    SELECT ev.ux_artifacts_json
    FROM pf_story s
    JOIN pf_epic_version ev ON ev.epic_id=s.epic_id AND ev.version=s.epic_version
    WHERE s.id=pf_story_version.story_id
), '[]')
WHERE ux_artifacts_json='[]';

ALTER TABLE pf_story ADD COLUMN refinement_cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pf_story ADD COLUMN refinement_cancel_sent BOOLEAN NOT NULL DEFAULT FALSE;
