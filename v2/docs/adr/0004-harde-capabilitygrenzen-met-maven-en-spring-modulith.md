# 0004 - Harde capabilitygrenzen met Maven en Spring Modulith

- Status: Accepted
- Datum: 2026-08-24

## Context

De bestaande applicaties laten twee nuttige vormen van modulariteit zien. In
`personal-news-feed-by-claude-code` hebben functionele modules een publieke API op de package-root
en private `api`, `domain` en `infrastructure`-packages. `ModuleStructureTest` controleert met
Spring Modulith dat modules niet in elkaars interne packages grijpen en geen cycli vormen.

Software Factory maakt de toegestane afhankelijkheden per module expliciet met
`@ApplicationModule(allowedDependencies = ...)`. `ModulithArchitectureTest` weigert wildcards,
niet-publieke imports en koppelingen tussen transportmodules. Afzonderlijke Maven-artefacten
scheiden daarnaast wirecontracten, gedeelde code, de orchestrator en los uitvoerbare services.

Product Factory v2 heeft functionele capabilities met een eigen eigenaar en soms meerdere
verwisselbare implementaties. Alleen packageconventies zijn daarvoor niet hard genoeg.

## Decision

Product Factory v2 is één modulair monoliet met één uitvoerbare applicatie en twee niveaus van
modulariteit:

- iedere capability heeft een kleine publieke Maven-API-module;
- iedere concrete variant staat in een afzonderlijke implementatiemodule;
- alleen `product-factory-app` is composition root en kiest per geactiveerde capability exact één
  implementatie;
- een API-module bevat uitsluitend publieke serviceinterfaces, commands, resultaten, read-only
  DTO's, filters, ID's, statussen, enums, events en betekenisvolle fouten;
- een API-module bevat geen persistence, concrete Spring-beans, scheduling, interne state machines
  of implementatiedetails;
- een implementatiemodule gebruikt een andere capability uitsluitend via haar API-module en heeft
  nooit een dependency op een andere implementatiemodule;
- Spring Modulith mag binnen een implementatiemodule de interne functionele delen structureren,
  maar vervangt de Maven-grens tussen capabilities niet.

Maven Enforcer, composition-tests en module-lokale Modulith-tests bewaken deze regels. Nieuwe
afhankelijkheden worden expliciet toegestaan; wildcards en stilzwijgende uitzonderingslijsten zijn
niet toegestaan.

## Consequences

- Capability-eigenaarschap en toegestane afhankelijkheidsrichtingen zijn door de build afgedwongen.
- Een implementatie kan worden vervangen zonder consumers naar haar interne code te laten wijzen.
- De applicatie blijft als één geheel te bouwen, testen en deployen; er ontstaat geen operationele
  microservicecomplexiteit.
- Publieke API's moeten bewust klein en stabiel blijven, omdat een type in zo'n module deel wordt
  van het contract met andere capabilities.
- Er ontstaan meer Maven-modules en architectuurtests dan in een ongepartitioneerde applicatie.
  Dat is geaccepteerde structurele overhead.
