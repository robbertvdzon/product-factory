-- Bijhouden welke previewtestdata-sets al zijn toegepast, zodat PreviewDataSeeder idempotent blijft.
-- Bestaat en wordt alleen gevuld in previewomgevingen (PF_PREVIEW_ENABLED=true); in productie blijft
-- de tabel altijd leeg.
create table preview_seed_history (
    seed_key varchar(80) primary key,
    applied_at timestamp with time zone not null default current_timestamp,
    pr_number integer not null
);
