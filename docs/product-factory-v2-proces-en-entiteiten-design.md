# Product Factory v2 — processen en publieke entiteiten

Dit document legt de grenzen tussen de vier uitvoerende onderdelen vast. Het is de overkoepelende
kaart bij de afzonderlijke procesdocumenten en beantwoordt per publiek contract drie vragen:

1. wie maakt de entiteit voor het eerst aan;
2. wie mag de inhoud of status daarna schrijven;
3. wie mag de gepubliceerde versie lezen.

![Processen, eigenaren en gegevensstromen](product-factory-v2-proces-en-entiteiten.svg)

## Ontwerpregels

- Iedere duurzame productentiteit heeft precies één eigenaar.
- Alleen de eigenaar schrijft de bronentiteit. Andere modules lezen een geversioneerde,
  read-only projectie via `processcontracts`.
- Een contractnaam die eindigt op `View` is die publieke projectie. Het is geen tweede
  schrijfbaar object.
- Een lezer bewaart hoogstens de bron-ID en gebruikte bronversie bij zijn eigen entiteiten.
- Een nieuwe publicatie vervangt een eerdere versie niet stilzwijgend; versies en herkomst blijven
  herleidbaar.
- Procesmodules importeren elkaars interne code en tabellen niet.

In de tabel betekent **aanmaken**: de identiteit en eerste versie vormen. **Schrijven** betekent:
nieuwe versies, inhoud of toegestane statusvelden opslaan. Een module die alleen leest, kan het
bronobject nooit corrigeren; zij publiceert daarvoor eigen feedback.

## De vier uitvoerende onderdelen

| Onderdeel | Type | Enige geplande ingang | Eigen verantwoordelijkheid | Schrijft nooit |
|---|---|---|---|---|
| Productontwerp | intelligent proces | `runProcessSession()` | productrichting onderzoeken en complete, geversioneerde epicdefinities inclusief UX publiceren | stories, backlog, bugs of verificaties |
| Productplanning | intelligent proces | `runProcessSession()` | een exacte epicversie kiezen en bevriezen, stories maken en de backlog prioriteren | epicinhoud, bugs of kwaliteitsbewijzen |
| Kwaliteitsbewaking | intelligent proces | `runProcessSession()` | opgeleverd werk en de complete epic toetsen en bevindingen met bewijs publiceren | epics, stories of backlogprioriteit |
| Software Factory-dispatcher | technische adapter binnen Productplanning | `runDispatchSession()` | externe statussen synchroniseren en precies één bovenste verzendbare opdracht versturen wanneer geen werk openstaat | productinhoud, story-inhoud, epicselectie of prioriteit |

De scheduler mag deze ingangen starten, maar beslist niet over de inhoud. De processen roepen elkaar
niet rechtstreeks aan. Ze reageren op gepubliceerde gegevens en hun eigen planningsregels.

## Eigenaarschap per publieke entiteit

### Product-, overleg- en inboxcontracten

Deze contracten komen van ondersteunende modules buiten de drie intelligente processen.

| Publiek contract | Aanmaker | Schrijver/eigenaar | Lezers | Betekenis en schrijfgrens |
|---|---|---|---|---|
| `ProductAssignmentView` | productmodule na het aanmaken van een product | productmodule | Productontwerp, Productplanning | productidentiteit, doelgroep, doel, harde grenzen, repository, toegang en backlogconfiguratie |
| `StakeholderDirectionView` | Stakeholder via overleg of productbediening | product-/overlegmodule | Productontwerp, Productplanning, Kwaliteitsbewaking | bindende aanwijzing of correctie; processen verwerken haar maar veranderen haar niet |
| `UserSignalView` | gebruiker, Stakeholder of toegestane integratie via de inbox | inbox/productmodule | Productontwerp, Kwaliteitsbewaking | gemeld probleem, kans, observatie of risicogebied; beoordeling blijft bij het lezende proces |
| `TestableProductView` | productmodule bij het configureren van testtoegang | productmodule | Kwaliteitsbewaking | omgevingen, routes, accounts, databereik en testgrenzen |

### Contracten van Productontwerp

| Publiek contract | Aanmaker | Schrijver/eigenaar | Lezers | Betekenis en schrijfgrens |
|---|---|---|---|---|
| `DirectionSnapshot` | Productontwerp | Productontwerp | Productontwerp, Stakeholder en productbediening | geversioneerd droombeeld; geen uitvoerbare opdracht voor Productplanning |
| `EpicDefinitionView` | Productontwerp | Productontwerp | Productplanning, Kwaliteitsbewaking, Productontwerp en productbediening | complete gebruikersverbetering met scope, bewijs, UX en succescriteria; een door Productplanning gekozen versie blijft voor altijd bevroren |
| `LearningResultView` | Productontwerp | Productontwerp | Productontwerp, Stakeholder en productbediening | gevalideerde productkennis voor toekomstige keuzes; geen story of backlogitem |

### Contracten van Productplanning

| Publiek contract | Aanmaker | Schrijver/eigenaar | Lezers | Betekenis en schrijfgrens |
|---|---|---|---|---|
| `EpicExecutionView` | Productplanning bij selectie van een epicversie | Productplanning | Productontwerp, Kwaliteitsbewaking, Productplanning en productbediening | koppelt de uitvoering aan exact één bevroren epicversie en bewaart voortgang en einduitkomst |
| `ProductStoryView` | Productplanning | Productplanning | Kwaliteitsbewaking, Software Factory-dispatcher, Productplanning en productbediening | zelfstandig uitvoerbaar gedrag binnen één bevroren epic; Productontwerp en Kwaliteitsbewaking maken of wijzigen geen stories |
| `PrioritizedBacklogView` | Productplanning | Productplanning | Software Factory-dispatcher, scheduler, Productplanning en productbediening | geversioneerde volgorde van verzendbare stories en bugfixes |
| `BacklogItemView` | Productplanning | Productplanning; dispatcher alleen voor verzendstatus, externe referentie en leveringstijdstippen | Software Factory-dispatcher, Kwaliteitsbewaking, Productplanning en productbediening | verwijst naar precies één story of bug; alleen Productplanning bepaalt inhoud, positie en prioriteitsreden |
| `PriorityDecisionView` | Productplanning | Productplanning | Productplanning, Stakeholder en productbediening | uitlegbaar bewijs van epicselectie of backlogvolgorde, inclusief alternatieven en gebruikte bronversies |
| `BacklogSupplyView` | Productplanning | Productplanning | Productontwerp, scheduler, Software Factory-dispatcher en productbediening | aantallen per backlogstatus, lage grens, streefpeil en `aanvullingNodig` |

### Contracten van de Software Factory-dispatcher

De dispatcher zit technisch in de module Productplanning, maar heeft hieronder een smalle,
afzonderlijke schrijfbevoegdheid. Hij verandert nooit de betekenis of prioriteit van werk.

| Publiek contract | Aanmaker | Schrijver/eigenaar | Lezers | Betekenis en schrijfgrens |
|---|---|---|---|---|
| `SoftwareFactoryWorkView` | Software Factory-dispatcher op basis van extern Software Factory-werk | Software Factory-dispatcher | Productplanning | genormaliseerde externe werkstatus; de externe Software Factory blijft bron van de ruwe status |
| `DeliveryResultView` | Software Factory-dispatcher zodra extern werk is opgeleverd of gewijzigd | Software Factory-dispatcher | Productplanning, Kwaliteitsbewaking en Productontwerp | genormaliseerde oplevering met extern ID, backlogitem, status, locatie, tijdstippen en foutinformatie |

### Contracten van Kwaliteitsbewaking

| Publiek contract | Aanmaker | Schrijver/eigenaar | Lezers | Betekenis en schrijfgrens |
|---|---|---|---|---|
| `BugView` | Kwaliteitsbewaking | Kwaliteitsbewaking | Productplanning, Kwaliteitsbewaking en productbediening | aantoonbare afwijking met verwacht en werkelijk gedrag, reproduceerstappen, bewijs, impact en ernst |
| `StoryVerificationView` | Kwaliteitsbewaking | Kwaliteitsbewaking | Productplanning, Kwaliteitsbewaking en productbediening | bewijs en oordeel over één story of bugfix; Productplanning verwerkt het oordeel maar wijzigt het niet |
| `EpicVerificationView` | Kwaliteitsbewaking | Kwaliteitsbewaking | Productplanning, Productontwerp, Kwaliteitsbewaking en productbediening | oordeel over de hele bevroren epic: geslaagd, onvolledig, niet aantoonbaar, geblokkeerd of niet geslaagd |
| `EpicCompletionGapView` | Kwaliteitsbewaking | Kwaliteitsbewaking | Productplanning, Kwaliteitsbewaking en productbediening | gedrag binnen de bevroren scope of UX waarvoor nooit een story bestond; Productplanning maakt zo nodig aanvullende stories |
| `QualityOverviewView` | Kwaliteitsbewaking | Kwaliteitsbewaking | Productontwerp, Kwaliteitsbewaking, Stakeholder en productbediening | actueel beeld van dekking, open bugs, risico's en onderbelichte gebieden |
| `QualitySignalView` | Kwaliteitsbewaking | Kwaliteitsbewaking | Productontwerp, Kwaliteitsbewaking en productbediening | terugkerend patroon of onjuiste productaanname dat nieuw onderzoek kan rechtvaardigen |

### Gedeeld operationeel contract

| Publiek contract | Aanmaker | Schrijver/eigenaar | Lezers | Betekenis en schrijfgrens |
|---|---|---|---|---|
| `ProcessSessionPublication` | Productontwerp, Productplanning of Kwaliteitsbewaking voor de eigen sessie | het proces dat de betreffende sessie uitvoerde; na publicatie onveranderlijk | eigen proces, scheduler, operations en productbediening | operationele vastlegging van sessie-ID, product-ID, inputversies, publicaties, eindstatus en blokkade; geen productwaarheid |

`ProcessSessionPublication` is één technisch contract, maar ieder record heeft precies één
proces als eigenaar. Een proces mag nooit de sessieregistratie van een ander proces schrijven.

## Gegevensstromen per onderdeel

| Producent | Gepubliceerde gegevens | Consument |
|---|---|---|
| product-/overleg-/inboxmodule | productopdracht, Stakeholderrichting en gebruikerssignalen | Productontwerp |
| product-/overlegmodule | productopdracht en Stakeholderrichting | Productplanning |
| product-/overleg-/inboxmodule | testconfiguratie, Stakeholderrichting en gebruikerssignalen | Kwaliteitsbewaking |
| Productontwerp | epicdefinitie | Productplanning en Kwaliteitsbewaking |
| Productplanning | epicuitvoering en backlogvoorraad | Productontwerp |
| Productplanning | epicuitvoering, productstories en backlogitems | Kwaliteitsbewaking |
| Productplanning | geprioriteerde backlog, backlogitems en productstories | Software Factory-dispatcher |
| Software Factory-dispatcher | externe werkstatus en opleverresultaat | Productplanning |
| Software Factory-dispatcher | opleverresultaat | Kwaliteitsbewaking en Productontwerp |
| Kwaliteitsbewaking | bugs, storyverificaties, epicverificaties en epicgaten | Productplanning |
| Kwaliteitsbewaking | epicverificaties, kwaliteitsbeeld en kwaliteitssignalen | Productontwerp |
| ieder intelligent proces | eigen sessiepublicatie | scheduler, operations en productbediening |

## Belangrijke levenscyclusregels

1. Productontwerp kan een nog niet gekozen epicdefinitie als een nieuwe versie publiceren.
2. Productplanning kiest exact één versie en maakt daarvoor `EpicExecutionView` aan. Vanaf dat
   moment verandert niemand die epicversie.
3. Alleen Productplanning deelt de epic op in `ProductStoryView`-objecten en zet stories of bugs als
   `BacklogItemView` in de geprioriteerde backlog.
4. De dispatcher leest de bovenste verzendbare opdracht. Alleen als Software Factory geen open werk
   voor het product heeft, stuurt hij precies één opdracht en slaat hij het externe ID op.
5. Kwaliteitsbewaking publiceert bevindingen. Zij repareert geen bronobjecten en maakt geen stories.
6. Productplanning verwerkt verificaties: een echt bouwdefect wordt een bugfix; een gemist onderdeel
   uit de bevroren epic wordt een aanvullende story op basis van een epicgat.
7. Pas een geslaagde `EpicVerificationView` laat Productplanning de epicuitvoering als **Geslaagd**
   afsluiten. Alle stories opgeleverd hebben is op zichzelf niet genoeg.

## Technische vertaling naar Spring Modulith

- `processcontracts` bevat uitsluitend stabiele DTO's, contractversies en read-only queryports.
- Iedere eigenaar beheert zijn eigen aggregates, repositories, transacties en publicaties.
- Een procesmodule krijgt geen repository van een andere procesmodule geïnjecteerd.
- De database mag fysiek gedeeld zijn, maar tabellen en schrijftransacties zijn logisch per module
  afgeschermd.
- De uitzondering voor dispatcher-velden op het backlogitem wordt als expliciete application service
  binnen Productplanning aangeboden; de dispatcher krijgt geen vrije repositorytoegang.
- Overdrachten zijn idempotent op bron-ID plus bronversie. Een consument mag dezelfde versie niet
  tweemaal als nieuw werk behandelen.

## Gerelateerde documenten

- [Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md)
- [Productontwerp](product-factory-v2-productontwerp.md)
- [Productplanning](product-factory-v2-productplanning.md)
- [Kwaliteitsbewaking](product-factory-v2-kwaliteitsbewaking.md)
