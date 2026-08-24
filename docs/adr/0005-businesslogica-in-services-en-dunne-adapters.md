# 0005 - Businesslogica in services en dunne adapters

- Status: Accepted
- Datum: 2026-08-24

## Context

In `personal-news-feed-by-claude-code` delegeert bijvoorbeeld `FeedController` aan de publieke
`FeedService`; `FeedServiceImpl` bevat de use-cases en gebruikt een private
`FeedItemRepository`. Dezelfde indeling komt terug bij onder meer instellingen, authenticatie,
events, RSS en podcasts. Eerdere directe controller-naar-infrastructuurkoppelingen en grote
pipelineklassen zijn daar bewust weggewerkt omdat verantwoordelijkheden en tests anders snel
onoverzichtelijk worden.

Software Factory gebruikt hetzelfde principe met publieke application ports zoals
`DashboardCommands`, `DashboardQueries` en `OrchestratorApi`, concrete services voor de use-cases,
repositories voor opslag en controllers, pollers en bridgehandlers als technische ingangen.

Alle logica letterlijk in één soort `Service`-klasse stoppen zou echter ook pure domeinobjecten en
kleine beleidsobjecten onnodig uithollen. De relevante grens is waar businessbeslissingen en
use-case-orkestratie worden beheerd.

## Decision

Iedere implementatiemodule gebruikt de volgende verantwoordelijkheidsverdeling:

- **inbound adapters** zoals REST-controllers, schedulers, listeners en CLI-ingangen handelen het
  protocol af, bepalen de geauthenticeerde actor, parsen invoer, doen eenvoudige syntactische
  validatie en delegeren daarna aan een application service;
- **application services** voeren de volledige use-case uit en bezitten businessvalidatie,
  autorisatie op de use-case, orkestratie, idempotentie en de transactiegrens;
- **domeinobjecten en pure policies** mogen lokale invarianten en berekeningen bezitten wanneer dat
  de regel dichter bij de bijbehorende data houdt; zij doen zelf geen externe I/O;
- **repositories en technische clients** verzorgen uitsluitend persistence of externe communicatie
  en nemen geen productbeslissingen;
- services gebruiken constructor-injectie en hangen af van publieke capabilitycontracten uit
  `product-factory-api` of interne
  ports, niet van globale service locators of internals van andere capabilities.

Een controller, scheduler of listener leest of schrijft dus nooit rechtstreeks via een repository
en orkestreert geen volledige productflow. Een repository of HTTP-client bepaalt evenmin welke
domeinstatus of vervolgstap geldig is.

## Consequences

- Dezelfde use-case kan veilig worden aangeroepen vanuit REST, een schedule, een herstelpad of een
  test zonder businessregels te dupliceren.
- Controllers en andere adapters blijven klein en protocolgericht.
- Transactiegrenzen en invarianten zijn op één herkenbare plek te vinden.
- Grote services moeten worden opgesplitst in gerichte services of pure policy-objecten zodra zij
  meerdere onafhankelijke verantwoordelijkheden krijgen; deze ADR is geen rechtvaardiging voor
  god-classes.
- Kleine syntactische controles en transportmapping mogen bij de adapter blijven. Businessregels
  horen daar niet.
