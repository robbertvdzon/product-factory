-- Vervangt de enkelvoudige iteration_schedule-cronstring door meerdere cyclustijden per product.
create table product_iteration_time (
    product_slug varchar(60) not null references product_definition(slug) on delete cascade,
    time_of_day time not null,
    primary key (product_slug, time_of_day)
);

-- Elk bestaand product had tot nu toe uitsluitend de vaste default '0 3 * * *' (er was nooit een manier om dit
-- te wijzigen), dus de eenmalige migratie zet dat rechtstreeks om naar 03:00 in plaats van de cronstring te parsen.
insert into product_iteration_time (product_slug, time_of_day)
select slug, time '03:00:00' from product_definition;

alter table product_definition drop column iteration_schedule;
