alter table shadow_iteration add column manual_start_origin varchar(32);

alter table shadow_iteration add constraint shadow_iteration_manual_start_origin_check
    check (manual_start_origin is null or manual_start_origin in ('AUTONOMOUS_DEFAULT', 'OWNER_INPUT'));
