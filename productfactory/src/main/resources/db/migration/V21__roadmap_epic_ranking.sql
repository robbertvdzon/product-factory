-- Roadmapthema's worden vanaf nu als epics behandeld. De bestaande tabel en ID's blijven
-- bewust staan zodat storykoppelingen en historische dossiers zonder risicovolle rename geldig
-- blijven. LOW/MEDIUM/HIGH wordt alleen gebruikt om de eerste twee rangordes te vullen.
alter table roadmap_theme add column customer_rank integer;
alter table roadmap_theme add column process_rank integer;

update roadmap_theme current_epic
set customer_rank = (
    select count(*)
    from roadmap_theme candidate
    where candidate.product_slug = current_epic.product_slug
      and (
        case candidate.priority when 'HIGH' then 1 when 'MEDIUM' then 2 when 'LOW' then 3 else 4 end
          < case current_epic.priority when 'HIGH' then 1 when 'MEDIUM' then 2 when 'LOW' then 3 else 4 end
        or (
          candidate.priority = current_epic.priority
          and candidate.sequence_number <= current_epic.sequence_number
        )
      )
);

update roadmap_theme set process_rank = customer_rank;

alter table roadmap_theme alter column customer_rank set not null;
alter table roadmap_theme alter column process_rank set not null;

create unique index roadmap_epic_customer_rank_idx on roadmap_theme(product_slug, customer_rank);
create unique index roadmap_epic_process_rank_idx on roadmap_theme(product_slug, process_rank);

create table roadmap_epic_dependency (
    epic_id varchar(120) not null references roadmap_theme(id) on delete cascade,
    dependency_id varchar(120) not null references roadmap_theme(id),
    primary key (epic_id, dependency_id),
    check (epic_id <> dependency_id)
);

create index roadmap_epic_dependency_reverse_idx on roadmap_epic_dependency(dependency_id, epic_id);
