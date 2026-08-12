alter table shadow_iteration add column resume_from_iteration_id varchar(120) references shadow_iteration(id);
alter table shadow_iteration add column accepted_candidate_count integer not null default 0;
alter table shadow_iteration add column revision_rounds integer not null default 0;
alter table shadow_iteration add column outcome_reason varchar(80);

create index shadow_iteration_resume_idx on shadow_iteration(resume_from_iteration_id);
