-- Grondwaarheid voor "echt opgeleverd": Software Factory's deployRolloutStage bevestigt pas DEPLOYED
-- zodra alle geraakte deploy-doelen (backend, frontend, ...) onafhankelijk live zijn bevestigd, in
-- tegenstelling tot alleen een goedgekeurde deploy-subtaak (zie AutonomousDelivery.reconcileStory).
alter table story_delivery add column confirmed_deployed boolean not null default false;
alter table story_delivery add column deployed_at timestamp with time zone;

-- Rapport van de opleverchecker-agentrol (zie roadmap/DeliveryVerificationEngine): bezoekt de
-- draaiende applicatie om te verifiëren of een bevestigd opgeleverde story ook echt voldoet aan zijn
-- acceptatiecriteria en de bedoeling van het gekoppelde roadmapthema. De Product Manager-rol gebruikt
-- dit rapport om een thema te sluiten, in plaats van zelf te moeten testen.
create table delivery_verification (
    id varchar(120) primary key,
    product_slug varchar(80) not null references product_definition(slug),
    theme_id varchar(120) not null references roadmap_theme(id),
    candidate_id bigint not null unique references story_candidate(id),
    status varchar(20) not null default 'RUNNING',
    verdict varchar(20),
    report text,
    created_at timestamp with time zone not null default current_timestamp,
    completed_at timestamp with time zone
);

create index delivery_verification_product_theme_idx on delivery_verification(product_slug, theme_id);
