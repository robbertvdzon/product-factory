# Stappenplannen voor de MVP

Deze map beschrijft de uitvoeringsvolgorde waarin Product Factory wordt opgebouwd. De
specificatiedocumenten blijven normatief: zij bepalen de contracten, invarianten en het bedoelde
gedrag. Ieder stappenplan vertaalt die eisen naar een zelfstandige uitvoeropdracht met een vaste
volgorde, concrete opleveringen, verificatie en een definitie van klaar. Bij verschil gaat het
gekoppelde specificatiedocument voor en wordt het stappenplan in dezelfde wijziging bijgewerkt.

Iedere stap levert een samenhangende versie op. Een push naar `main` doorloopt de in stap 1 gebouwde
releaseflow: verificatie, eenmaal bouwen, deployment van die immutable artifacts naar acceptatie,
rookcontrole en promotie van exact dezelfde digests naar productie. Een volgende stap begint pas
wanneer de voorgaande stap op beide omgevingen gezond staat. Functionele onvolledigheid is alleen
toegestaan waar het volgende stappenplan die ontbrekende capability expliciet overneemt; een
onveilige of technisch kapotte release nooit.

## Betekenis van "alle specificaties"

Na stap 9 zijn alle normatieve eisen uit de onderstaande **MVP-specificatieset** geïmplementeerd en
automatisch of operationeel aantoonbaar:

- `docs/overzicht.md`, `docs/ketenscenarios.md` en `docs/processen/processen-en-entiteiten.md`;
- alle documenten onder `docs/platform`;
- alle documenten onder `docs/stakeholder`;
- alle documenten onder `docs/gedeelde-modules`;
- de publieke API- en `mvp.md`-documenten van Productontwerp, Productplanning en
  Kwaliteitsbewaking;
- `docs/processen/software-factory-dispatcher.md`;
- alle geldende ADR's onder `docs/adr`.

De drie procesdocumenten met de naam `uitgebreid.md` zijn expliciet toekomstontwerp en dus geen
openstaande MVP-eisen. De eindcontrole in stap 9 mag alleen slagen wanneer iedere eis uit de
MVP-specificatieset is gekoppeld aan werkende code, configuratie, migratie, UI, test of bewust
operationeel bewijs. Een eis zonder eigenaar of bewijs blokkeert afronding.

Tijdens de ontwikkeling is [HKH Autopilot](../praktijkproducten/hkh-autopilot.md) het eerste echte
referentie- en praktijkproduct. De productspecifieke gegevens in dat document zijn geen generieke
MVP-eisen en worden niet in code of productieseed hardgecodeerd.

## Volgorde

| Stap | Plan | Resultaat |
|---|---|---|
| 1 | [Technische fundering](01-technische-fundering.md) | Een schoon, veilig en deploybaar technisch fundament zonder proceslogica. |
| 2 | [Product- en stakeholderbasis](02-product-en-stakeholderbasis.md) | Productopdracht, signalen, agentvragen, besluiten en overleggen zijn via de UI bruikbaar. |
| 3 | [Agentgeheugen en AI-instellingen](03-agentgeheugen-en-ai-instellingen.md) | Rollen hebben een catalogus en beheersbaar permanent geheugen; AI-jobs hebben centrale modelkeuzes. |
| 4 | [AI-uitvoering](04-ai-uitvoering.md) | AI-taken en rolgerichte overleggen werken duurzaam; mocks worden server-side uitgevoerd. |
| 5 | [Productontwerp MVP](05-productontwerp-mvp.md) | Eén ontwerperagent zet relevante input om in complete, behapbare epics met UX waar nodig. |
| 6 | [Productplanning MVP](06-productplanning-mvp.md) | Eén planneragent maakt uitvoerbare stories en een geordende backlog. |
| 7 | [Kwaliteitsbewaking MVP](07-kwaliteitsbewaking-mvp.md) | Eén testeragent levert verificaties, bugs en kwaliteitshistorie. |
| 8 | [Software Factory-dispatcher](08-software-factory-dispatcher.md) | Stories worden één voor één geleverd en opleveringen worden verwerkt. |
| 9 | [Volledige MVP-productflow](09-volledige-mvp-productflow.md) | De complete route van Stakeholder tot gebouwde en gecontroleerde verbetering werkt. |

De stappen 1 tot en met 4 zijn uitgebracht. Stap 5 wordt door de actieve
`product-design-impl-mvp`-provider ingevuld; stappen 6 tot en met 9 volgen in deze volgorde.

## Algemene regels voor iedere stap

- Lees vóór implementatie het volledige stappenplan en alle gekoppelde normatieve bronnen.
- Controleer eerst de actuele code en contracten. Een al aanwezige implementatie wordt bewezen en
  zo nodig aangevuld; zij wordt niet blind opnieuw gebouwd.
- Stap 1 maakt één `product-factory-api` met alle publieke capabilitypackages en interfaces. Latere
  stappen voegen per capability de echte implementatie toe of maken haar volgende interne
  onderdeel functioneel en activeren dat bewust in `product-factory-app`.
- Bouw alleen wat voor die stap nodig is en loop niet vooruit op latere capabilities.
- Publieke modulecommunicatie loopt uitsluitend via `product-factory-api`; geen module leest of
  schrijft tabellen of repositories van een andere eigenaar.
- Iedere duurzame wijziging krijgt een voorwaartse Flywaymigratie, idempotentie waar het contract
  dat vereist en een PostgreSQL-migratiesmoketest.
- Werk backend, database, frontend, tests, beheerweergave en documentatie samenhangend bij voor zover
  de stap die raakt.
- Gebruik in acceptatie synthetische data en de voorgeschreven mocks. Productie gebruikt echte
  configuratie en nooit acceptance-only voorzieningen.
- Voeg per stap contracttests, domeintests, integratietests en gerichte frontendtests toe. Voeg een
  Testbedscenario toe wanneer de stap een externe of AI-grens introduceert.
- Push pas naar `main` wanneer de lokale en CI-verificatie groen zijn. Controleer daarna dat de
  automatische release exact dezelfde artifactdigests op acceptatie en productie heeft gezet en
  dat beide omgevingen gezond zijn.
- Leg een afwijking eerst vast in het toepasselijke specificatiedocument en verwijs er daarna vanuit
  het stappenplan naar.

Handmatige browsercontrole, een handmatige productiebackup, controle op 320px/200%-weergave en een
volledige menselijke eindcontrole zijn acties van de Stakeholder. Zij mogen worden uitgevoerd, maar
zijn geen automatische klaarvoorwaarde van stappen 2 tot en met 9.

## Vaste afronding per stap

Iedere stap sluit af met dezelfde bewijsset:

1. de reactorbuild, backendtests, frontendanalyse/-tests en productiebuild slagen;
2. nieuwe migraties slagen vanaf een lege PostgreSQL-database én boven op de vorige release;
3. de gerichte API-, domein-, integratie- en Testbedscenario's slagen;
4. de actieve implementatie en bronrevisie zijn zichtbaar in `ImplementationManifest` en Operatie;
5. documentatie, voorbeeldconfiguratie en runbooks beschrijven de werkelijk gebouwde situatie;
6. de releaseworkflow heeft dezelfde digests gezond op acceptatie en productie gezet.

Een volgende stap mag ontbrekend bewijs uit een eerdere stap niet overnemen.

## Buiten deze route

De documenten `uitgebreid.md` beschrijven mogelijke latere implementaties van de intelligente
processen. Zij blijven beschikbaar voor verdere uitwerking, maar vallen volledig buiten deze
MVP-stappen. Stap 9 kiest dus voor Productontwerp, Productplanning en Kwaliteitsbewaking uitsluitend
de MVP-implementatie in de main-module.
