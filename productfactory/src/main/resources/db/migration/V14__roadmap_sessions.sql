-- Eén sessie van de Product Manager-rol (zie RoadmapSessionEngine): bekijkt periodiek (of op
-- handmatig verzoek) de huidige roadmap plus wat er sinds de vorige sessie is gebeurd, en werkt de
-- roadmap bij. Eén rol, geen keten van agentstappen zoals shadow_iteration, dus geen aparte
-- stappentabel nodig.
create table roadmap_session (
    id varchar(120) primary key,
    product_slug varchar(80) not null references product_definition(slug),
    sequence_number integer not null,
    status varchar(20) not null default 'QUEUED',
    summary text,
    error_message text,
    created_at timestamp with time zone not null default current_timestamp,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    workspace_run_id varchar(120),
    workspace_pull_request_url varchar(1000),
    workspace_commit_sha varchar(64),
    unique (product_slug, sequence_number)
);

create index roadmap_session_product_status_idx on roadmap_session(product_slug, status);
