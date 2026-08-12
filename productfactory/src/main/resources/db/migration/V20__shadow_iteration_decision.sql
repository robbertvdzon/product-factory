create table shadow_iteration_decision (
    iteration_id varchar(120) primary key references shadow_iteration(id),
    actor_type varchar(40) not null,
    mechanism varchar(60) not null,
    reason_code varchar(60) not null,
    decided_at timestamp with time zone not null
);

