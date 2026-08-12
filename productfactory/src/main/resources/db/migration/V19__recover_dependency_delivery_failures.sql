alter table shadow_iteration add column generated_candidate_count integer not null default 0;

-- Het dependson_resolution-artefact bevat exact één top-level candidateKey per gegenereerde
-- kandidaat. Deze draagbare telling herstelt ook de diagnostiek van reeds afgeronde cycli.
update shadow_iteration
set generated_candidate_count = coalesce((
    select (length(a.content_json) - length(replace(a.content_json, '"candidateKey"', ''))) / length('"candidateKey"')
    from shadow_iteration_artifact a
    where a.iteration_id = shadow_iteration.id and a.artifact_type = 'dependson_resolution'
), generated_candidate_count);

-- Een door de criticus geaccepteerde richting die pas in de dependency-resolutie uitviel was
-- technisch mislukt en is geen productafwijzing. Maak deze historische gevallen herstelbaar.
update shadow_iteration
set status = 'FAILED',
    outcome_reason = 'DELIVERY_DEPENDENCY_UNRESOLVED',
    error_message = 'Geaccepteerde storykandidaat kon niet worden geleverd doordat een afhankelijkheid niet werd herkend.'
where status = 'REJECTED'
  and critic_verdict = 'ACCEPT'
  and outcome_reason = 'NO_DELIVERABLE_CANDIDATE'
  and exists (
      select 1 from shadow_iteration_artifact a
      where a.iteration_id = shadow_iteration.id
        and a.artifact_type = 'dependson_resolution'
        and a.content_json like '%"blocked":true%'
  );
