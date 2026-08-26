# 0008 - Test de echte applicatie met een gefakete buitenwereld

- Status: Accepted
- Datum: 2026-08-24

## Context

De e2e-strategie van `personal-news-feed-by-claude-code` start de volledige Spring-applicatie met
echte HTTP-security, echte Flywaymigraties en een geïsoleerde database. Alleen externe diensten
worden vervangen door deterministische fakes. Pure parsers en berekeningen krijgen kleine
unittests; modulegrenzen worden apart en snel gecontroleerd.

Software Factory gebruikt eveneens gerichte tests rond contracts, state machines, adapters en
modulegrenzen. Injecteerbare ports maken het mogelijk om bijvoorbeeld AI-, GitHub-, proces- en
deploymentgedrag zonder echte externe actie te bewijzen.

Product Factory v2 bevat juist in de samenwerking tussen modules, queues, retries, leases,
idempotentie en externe statusovergangen veel belangrijk gedrag. Tests die alle services mocken
bewijzen die keten niet.

## Decision

De teststrategie bestaat uit elkaar aanvullende lagen:

- snelle architectuurtests bewaken waar nodig Maven-, contract- en composition-rootgrenzen;
- unittests testen pure policies, parsers, state-overgangen en berekeningen zonder Spring-context;
- repository- en migratietests draaien tegen een tijdelijke echte PostgreSQL-instantie;
- integratietests starten de echte betrokken Product Factory-modules met echte configuratie,
  serialisatie, transacties, queues en migraties;
- alleen de buitenwereld wordt vervangen: AI en Software Factory door stateful Testbed-adapters,
  Git door een tijdelijke lokale repository en tijd door een bestuurbare geïnjecteerde `Clock`;
- HTTP- en UI-ketens worden via de publieke ingang getest en schrijven niet rechtstreeks in
  interne repositories;
- asynchrone tests wachten op waarneembare toestand met een begrensde deadline en gebruiken geen
  vaste sleeps;
- acceptatiescenario's gebruiken dezelfde publieke ports en protocollen als productie, maar nooit
  echte schrijfcredentials of productiegegevens.

Een mock van een interne application service is alleen passend in een kleine geïsoleerde test van
de directe consumer. Het is geen vervanging voor de integratietest van de capabilityketen.

## Consequences

- Tests bewijzen ook dependency wiring, transacties, mappings, beveiliging en herstelgedrag.
- Externe kosten en ongewenste schrijfacties blijven uitgesloten.
- Integratietests zijn trager dan volledig gemockte unittests; daarom blijven pure logica en
  architectuurcontroles in de snelle testlaag.
- Stateful simulators en vaste scenariofixtures moeten als echte testsoftware worden onderhouden.
- Productiecode moet testbaar zijn via ports, een injecteerbare klok en expliciete configuratie;
  verborgen globale toestand is niet toegestaan.
