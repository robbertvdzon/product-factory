-- URL van de standing acceptatieomgeving van een product (aparte, altijd-beschikbare omgeving met
-- nepdata en uitgeschakelde authenticatie, zie deploy/overlays/acceptance/). Wordt door
-- ShadowIterationEngine.researchPrompt() gebruikt om de onderzoeksrol op te dragen de draaiende
-- applicatie te bekijken.
alter table product_definition add column acceptance_url varchar(1000);

update product_definition set acceptance_url = 'https://hkh-acceptance.vdzonsoftware.nl' where slug = 'hkh';
update product_definition set acceptance_url = 'https://hkh-autopilot-acceptance.vdzonsoftware.nl' where slug = 'hkh-autopilot';
