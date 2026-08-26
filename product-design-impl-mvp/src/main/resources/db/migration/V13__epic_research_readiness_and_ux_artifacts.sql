ALTER TABLE pf_epic_version ADD COLUMN research_sources_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE pf_epic_version ADD COLUMN readiness_json TEXT NOT NULL DEFAULT '{"readyForPlanning":false,"requiresExternalData":false,"unmetConditions":["Deze bestaande epic is nog niet opnieuw op gereedheid beoordeeld."],"openQuestions":[]}';
ALTER TABLE pf_epic_version ADD COLUMN ux_artifacts_json TEXT NOT NULL DEFAULT '[]';

UPDATE pf_epic_version
SET status = 'NEEDS_RESEARCH'
WHERE status = 'AVAILABLE'
  AND EXISTS (
      SELECT 1 FROM pf_epic e
      WHERE e.id = pf_epic_version.epic_id
        AND e.current_version = pf_epic_version.version
        AND e.status = 'AVAILABLE'
  );
