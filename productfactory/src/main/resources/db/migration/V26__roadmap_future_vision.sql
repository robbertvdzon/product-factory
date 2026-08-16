-- Versioned, imaginative product horizon maintained by roadmap sessions.
create table roadmap_future_vision (
    id varchar(120) primary key,
    product_slug varchar(80) not null references product_definition(slug),
    version integer not null,
    content_json text not null,
    change_summary text not null,
    created_by_session_id varchar(120) not null references roadmap_session(id),
    created_at timestamp with time zone not null default current_timestamp,
    unique (product_slug, version),
    unique (created_by_session_id)
);

create index roadmap_future_vision_product_version_idx
    on roadmap_future_vision(product_slug, version desc);

-- Existing epics remain valid and are deliberately unplaced until the renewed roadmap process
-- connects them to a future capability.
alter table roadmap_theme add column horizon varchar(20) not null default 'UNPLACED';
alter table roadmap_theme add column kind varchar(20) not null default 'DELIVERY';
alter table roadmap_theme add column capability_key varchar(120);
