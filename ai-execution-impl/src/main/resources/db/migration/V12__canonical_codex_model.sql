UPDATE pf_ai_job_definition
SET default_model = 'gpt-5.6-sol'
WHERE default_provider = 'CODEX'
  AND default_model = 'gpt-5.6'
  AND job_key IN (
      'MEETING.CONVERSE',
      'MEETING.SUMMARIZE',
      'PRODUCT_DESIGN.CREATE_EPIC',
      'PLANNING.SELECT_WORK',
      'PLANNING.SLICE_EPIC',
      'QUALITY.VERIFY_EPIC'
  );
