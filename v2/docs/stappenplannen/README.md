# Stappenplannen voor de MVP

Deze map beschrijft de volgorde waarin Product Factory wordt opgebouwd. De documenten zijn geen
tweede set specificaties. Zij benoemen per stap alleen het doel, de globale scope, de relevante
brondocumenten en het resultaat dat aantoonbaar moet werken.

Iedere stap levert een samenhangende versie op. Die versie wordt eerst op acceptatie gecontroleerd
en daarna naar productie gedeployed. Een volgende stap begint pas wanneer de voorgaande stap daar
werkend staat. Functionele onvolledigheid is toegestaan; een onveilige of technisch kapotte release
niet.

## Volgorde

| Stap | Plan | Resultaat |
|---|---|---|
| 1 | [Technische fundering](01-technische-fundering.md) | Een schoon, veilig en deploybaar technisch fundament zonder proceslogica. |
| 2 | [Product- en stakeholderbasis](02-product-en-stakeholderbasis.md) | Productopdracht, signalen, besluiten en overleggen zijn via de UI bruikbaar. |
| 3 | [Agentgeheugen en AI-instellingen](03-agentgeheugen-en-ai-instellingen.md) | Rollen hebben beheersbaar permanent geheugen en AI-jobs hebben centrale modelkeuzes. |
| 4 | [AI-uitvoering](04-ai-uitvoering.md) | Generieke AI-taken worden duurzaam door een laptop- of mockworker uitgevoerd. |
| 5 | [Productontwerp MVP](05-productontwerp-mvp.md) | Eén ontwerperagent zet alle relevante input om in complete epics met UX. |
| 6 | [Productplanning MVP](06-productplanning-mvp.md) | Eén planneragent maakt uitvoerbare stories en een geordende backlog. |
| 7 | [Kwaliteitsbewaking MVP](07-kwaliteitsbewaking-mvp.md) | Eén testeragent levert verificaties, bugs en kwaliteitshistorie. |
| 8 | [Software Factory-dispatcher](08-software-factory-dispatcher.md) | Stories worden één voor één geleverd en opleveringen worden verwerkt. |
| 9 | [Volledige MVP-productflow](09-volledige-mvp-productflow.md) | De complete route van Stakeholder tot gebouwde en gecontroleerde verbetering werkt. |

## Algemene regels voor iedere stap

- Stap 1 maakt alle publieke capability-API-modules en hun interfaces. Latere stappen voegen per
  capability de echte implementatie toe of maken haar volgende interne onderdeel functioneel en
  activeren dat bewust in `product-factory-app`.
- Bouw alleen wat voor die stap nodig is en loop niet vooruit op latere capabilities.
- Gebruik de gekoppelde API- en ontwerpdocumenten als specificatie; kopieer die inhoud niet naar het
  stappenplan.
- Werk backend, database, frontend, tests, beheerweergave en documentatie samenhangend bij voor zover
  de stap die raakt.
- Gebruik in acceptatie synthetische data en de voorgeschreven mocks. Productie gebruikt echte
  configuratie en nooit acceptance-only voorzieningen.
- Rond af met automatische tests, een deployment naar acceptatie, acceptatierooktests en daarna een
  deployment van exact dezelfde artifacts naar productie.
- Leg een afwijking eerst vast in het toepasselijke specificatiedocument en verwijs er daarna vanuit
  het stappenplan naar.

## Buiten deze route

De documenten `uitgebreid.md` beschrijven mogelijke latere implementaties van de intelligente
processen. Zij blijven beschikbaar voor verdere uitwerking, maar vallen volledig buiten deze
MVP-stappen. Stap 9 kiest dus voor Productontwerp, Productplanning en Kwaliteitsbewaking uitsluitend
de MVP-implementatie in de main-module.
