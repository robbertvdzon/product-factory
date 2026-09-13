-- Een dispatcherrun die een al gekoppelde, nog open Software Factory-story alleen opnieuw bevestigt,
-- heet sinds deze release een no-op. Zet de historische herhalingen om; de eerste koppeling per story blijft staan.
UPDATE pf_dispatcher_process_session
SET result_summary = SUBSTRING(
        REPLACE(result_summary, ' is idempotent gekoppeld aan ', ' wacht bij Software Factory op '),
        1,
        LENGTH(REPLACE(result_summary, ' is idempotent gekoppeld aan ', ' wacht bij Software Factory op ')) - 1
    ) || ' (OPEN); succesvolle no-op.'
WHERE status = 'SUCCEEDED'
  AND result_summary LIKE 'Story % is idempotent gekoppeld aan %.'
  AND started_at > (
      SELECT MIN(first_link.started_at)
      FROM pf_dispatcher_process_session first_link
      WHERE first_link.result_summary = pf_dispatcher_process_session.result_summary
  );
