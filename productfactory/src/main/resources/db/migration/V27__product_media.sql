-- Productbrede beeldbibliotheek. Berichten verwijzen naar assets, zodat hetzelfde beeld later ook
-- door roadmap- en productcycli kan worden geraadpleegd zonder het uit de overlegtranscriptie te kopieren.
create table product_media (
    id varchar(100) primary key,
    product_slug varchar(80) not null references product_definition(slug),
    filename varchar(255) not null,
    media_type varchar(100) not null,
    size_bytes bigint not null,
    alt_text varchar(1000),
    source varchar(20) not null,
    source_reference varchar(255),
    content bytea not null,
    created_at timestamp with time zone not null default current_timestamp
);

create index product_media_product_idx on product_media(product_slug, created_at desc);

create table meeting_message_media (
    message_id bigint not null references meeting_message(id) on delete cascade,
    media_id varchar(100) not null references product_media(id),
    position integer not null,
    primary key (message_id, media_id)
);

create index meeting_message_media_order_idx on meeting_message_media(message_id, position);
