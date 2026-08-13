-- Roadmap-sessies krijgen een eigen, optionele weekplanning per product. Een lege planning
-- betekent bewust: alleen handmatig starten. Bestaande producten krijgen daarom geen impliciete
-- automatische sessie na deze migratie.
create table product_roadmap_schedule (
    product_slug varchar(80) not null references product_definition(slug) on delete cascade,
    day_of_week varchar(9) not null,
    time_of_day time not null,
    primary key (product_slug, day_of_week, time_of_day),
    constraint product_roadmap_schedule_day_check check (
        day_of_week in ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    )
);
