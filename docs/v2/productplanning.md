# Product Factory v2 — Productplanning

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt Productplanning en de technische Software Factory-dispatcher uit. De
black-boxinterface in [Product Factory v2 — overzicht](overzicht.md) is
leidend.

## Verantwoordelijkheid

Productplanning zet een complete, bevroren epic om in zelfstandig uitvoerbare stories en ordent
alle open stories met een productbreed `sequenceNumber`. Een story is een
productstory of een bugfix. De epic bepaalt de inhoudelijke grens: zij kan twee stories opleveren,
maar ook dertig. Er is geen kunstmatig minimum, maximum of voorraadstreefgetal.

De module is eigenaar van:

- de duurzame queue met planningsopdrachten;
- stories, storytype, status en `sequenceNumber`;
- epicselectie tijdens de eigen processessie en de commands waarmee de epicstatus bij Productontwerp
  wordt bijgewerkt;
- prioriteitsbeoordelingen, onderbouwingen en gebruikte bronversies;
- stories en hun koppeling met Software Factory;
- het eigen agent- en procesgeheugen.

Productplanning wijzigt geen epicinhoud, UX-ontwerp, bug of verificatieresultaat. Zij gebruikt
hiervoor uitsluitend commands van de module die de betreffende entiteit bezit.

## Publieke module-interface

De enige agentgestuurde ingang is:

```java
void runProcessSession();
```

De scheduler of een handmatige UI-/REST-actie kan deze functie starten. Er kan modulebreed
maximaal één planningssessie tegelijk actief zijn. Een tweede handmatige aanroep krijgt een
`ProcessAlreadyRunning`-fout (bij REST bijvoorbeeld HTTP 409); een botsende geplande aanroep wordt
als overgeslagen geregistreerd. Alleen deze functie mag planningsagents starten.

Een run claimt atomair een vaste momentopname van alle op dat moment `PENDING`
`PlanningWorkItem`s en leest de op dat moment `AVAILABLE` epics. Nieuwe verzoeken en epics die
tijdens de run verschijnen blijven voor de volgende run staan. Zijn de queue en de lijst met
beschikbare epics leeg, dan eindigt de run als succesvolle no-op.

Daarnaast biedt Productplanning deterministische commands en read-only queries:

```java
StoryDetails getStory(StoryId storyId);
List<StoryDetails> getBacklog(ProductId productId);
List<PlanningWorkItemDetails> findPlanningWorkItems(ProductId productId, WorkItemStatus status);

PlanningWorkItemId requestBugfix(RequestBugfixCommand command);
PlanningWorkItemId requestEpicGapPlanning(RequestEpicGapPlanningCommand command);
PlanningWorkItemId requestEpicReprioritization(RequestEpicReprioritizationCommand command);
PlanningWorkItemId requestManualReplan(RequestManualReplanCommand command);

void markStoryAsDispatched(MarkStoryAsDispatchedCommand command);
void markStoryAsDeveloped(MarkStoryAsDevelopedCommand command);
void cancelStoriesForEpic(CancelStoriesForEpicCommand command);
```

De vier `request...`-commands starten geen agents en voeren geen inhoudelijke planning uit. Zij
valideren bron, versie en idempotentiesleutel en voegen alleen een duurzaam `PENDING`-werkitem toe.
De drie storycommands zijn eveneens snel en deterministisch. Andere modules krijgen geen
schrijftoegang tot `Story` of `PlanningWorkItem`.

## PlanningWorkItem: de queuegrens

Een `PlanningWorkItem` bevat minimaal:

- work-item-ID, product-ID, type en status;
- bronmodule, bron-ID en exacte bronversie;
- epic-ID en epicversie wanneer van toepassing;
- prioriteitsaanwijzing en reden, zonder dat de aanroeper de definitieve storyvolgorde voorschrijft;
- idempotentiesleutel, aanmaakmoment, claim en foutinformatie.

De typen zijn:

| Type | Aangevraagd door | Betekenis |
|---|---|---|
| `PLAN_BUGFIX` | Kwaliteitsbewaking via `requestBugfix(...)` | maak een uitvoerbare bugfixstory voor deze bug |
| `PLAN_EPIC_GAP` | Kwaliteitsbewaking via `requestEpicGapPlanning(...)` | maak stories voor gedrag dat al binnen de bevroren epic viel, maar nooit in een story stond |
| `REPRIORITIZE_EPIC` | product-/overlegmodule via `requestEpicReprioritization(...)` | geef een door de Stakeholder aangewezen epic voorrang en herschik zo nodig `TODO`-stories |
| `MANUAL_REPLAN` | bevoegde productbediening via `requestManualReplan(...)` | vraag een expliciete herbeoordeling aan zonder zelf stories te schrijven |
| `REPAIR_STORY` | dispatcher na een definitieve inhoudelijke afwijzing door Software Factory | herstel een nog niet verzonden story; geen retry van een transportfout |

De statussen zijn `PENDING`, `IN_PROGRESS`, `DONE`, `BLOCKED` en `FAILED`. Een mislukte run laat
zichtbaar welk werk niet is afgerond en kan veilig worden hervat. Eenzelfde bronversie en opdracht
maken door idempotentie nooit twee werkitems.

## Interface met andere modules en services

Productplanning gebruikt publieke Spring Modulith-API's. Read-only DTO's staan in
`processcontracts` en zijn geen eigen database-entiteiten.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `PlanningWorkItem` | Productplanning | duurzame opdracht die de run claimt; andere modules kunnen alleen een aanvraagcommand doen |
| `ProductAssignmentDetails` | productmodule | productidentiteit, grenzen en publieke Git-URL |
| `DecisionDto` | Besluitenregister-query voor het huidige tijdstip | grote blijvende keuzes die de planning begrenzen; geen directe opdracht om een epic, bug of story te kiezen |
| `EpicDetails` | Productontwerp | exacte epicversie, gebruikerswaarde, scope, UX, succescriteria en status |
| `BugDetails` | Kwaliteitsbewaking | uitvoerbare bug inclusief ernst, bewijs en versie |
| `VerificationDetails` | Kwaliteitsbewaking | bewijs voor een ontbrekend stuk binnen een bevroren epic |
| `TestableProductDetails` | productmodule | acceptatie- en eventueel productieomgeving, veilige routes, accounts en toegangsgrenzen |
| `SoftwareFactoryWork` | dispatcheradapter | actuele externe status van eerder verzonden werk; tijdelijk integratiegegeven |

Tijdens een inhoudelijke sessie mag Productplanning de publieke Git-URL uitchecken en broncode,
tests en documentatie read-only bekijken. Zij commit en pusht nooit. De bekeken commit-SHA kan als
bronverwijzing worden opgeslagen, maar iedere story blijft zelfstandig en bevat alle benodigde
product- en UX-informatie.

Productplanning mag daarnaast de werkende applicatie read-only bekijken. Acceptatie is de
voorkeursomgeving voor interactie; productie wordt alleen via veilige routes of expliciete
testaccounts gebruikt. De planner gebruikt dit als context voor bestaande gebruikersroutes en
schermen en voert er geen kwaliteitsoordeel uit.

### Eigen output en downstream effect

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `StoryDetails` | read-only weergave van een zelfstandig uitvoerbare productstory of bugfix | type, bronrelaties, epicversie, gedrag, acceptatiecriteria, UX, `sequenceNumber`, status en externe referentie |
| backlogquery | alle uitvoerbare of reeds verzonden stories in volgorde | `StoryDetails` met status `TODO` of `IN_PROGRESS`, geordend op `sequenceNumber` |
| `PlanningWorkItemDetails` | read-only inzicht in de planningsqueue | type, bron, status, claim, resultaat en fout; geen wijzigbaar requestobject |
| `StoryDeliveryPackage` | onveranderlijk pakket voor Software Factory | complete story of bugfix, acceptatiecriteria, UX, attachments, bronversies, hashes en idempotentiesleutel |
| `ProcessSession` | operationele historie van de intelligente run | geclaimde workitems, inputversies, agentruns, publicaties en eindstatus |
| `QualityWorkItem` bij Kwaliteitsbewaking | downstream effect van een verificatiecommand; Kwaliteitsbewaking maakt en bezit dit object | type, exact doel-ID en -versie, bron, prioriteit en idempotentiesleutel |

`PlanningWorkItem`, `Story`, `ProcessSession` en `DeliveryAttempt` zijn eigendom van
Productplanning. De backlog is geen entiteit. Epicselectie, storyvorming, backlogvolgorde en het
voorrang geven aan een epic of bug zijn gewone proceskeuzes en worden niet in het Besluitenregister
opgenomen.

Na een storyoplevering vraagt Productplanning via `requestStoryVerification(...)`,
`requestBugfixRetest(...)` of `requestEpicVerification(...)` aan Kwaliteitsbewaking om gericht werk
klaar te zetten. Kwaliteitsbewaking maakt en bezit het resulterende `QualityWorkItem`; het is geen
entiteit van Productplanning.

## Een epic plannen

Productontwerp publiceert een complete epic met status `AVAILABLE`. Tijdens een geplande of
handmatig gestarte processessie:

1. vraagt Productplanning de beschikbare epics read-only op;
2. kiest zij op basis van productdoel, geldige besluiten, gebruikerswaarde en bestaand werk een epic;
3. roept zij `claimEpicForPlanning(...)` op Productontwerp aan;
4. bevriest Productontwerp de versie en zet de epic op `IN_PLANNING`;
5. maakt Productplanning een volledige, samenhangende set stories voor de epic;
6. ordent zij de nieuwe stories tussen alle andere `TODO`-stories;
7. publiceert zij de stories atomair en roept `markEpicActive(...)` aan.

Meerdere epics mogen tegelijk `IN_PLANNING`, `ACTIVE` of `VERIFYING` zijn. Normaal houdt de planner
één hoofdepic aan, maar dit is geen technische invariant. De Stakeholder kan via de UI direct
`requestEpicReprioritization(...)` laten aanroepen. Dat maakt een `REPRIORITIZE_EPIC`-workitem. Een
volgende planningsrun mag die epic claimen, stories maken en alle
`TODO`-stories productbreed opnieuw ordenen. Een al `IN_PROGRESS` zijnde story loopt normaal door.

Iedere epic wordt onafhankelijk voltooid en geverifieerd. De globale `sequenceNumber`-volgorde is
de enige werkelijke dispatchvolgorde; stories van verschillende epics mogen door elkaar staan.

Er is geen aparte duurzame roadmapentiteit met vakken als Nu, Hierna en Later. De actuele
epicstatussen laten zien welke epics beschikbaar, in planning, actief of in controle zijn. De
werkelijke uitvoeringsprioriteit staat uitsluitend in de `sequenceNumber`s van open stories. Zo
ontstaan geen twee concurrerende bronnen voor de volgorde.

## Storycontract en backlog

Een `Story` bevat minimaal:

- stabiel story-ID en product-ID;
- type `PRODUCT_STORY` of `BUGFIX`;
- epic-ID en bevroren epicversie voor een productstory en waar relevant voor een bugfix;
- bug-ID en bugversie voor een bugfix;
- klein zichtbaar gebruikersgedrag, waarde en duidelijke acceptatiecriteria;
- hoofd-, lege, laad-, fout- en uitzonderingssituaties;
- een zelfstandige momentopname van het relevante deel van het bevroren UX-ontwerp;
- gebruikersflow, schermen, componenten, interacties, responsive gedrag en toegankelijkheid;
- benodigde tekstuele en binaire ontwerpassets met naam, MIME-type, grootte en hash;
- afhankelijkheden en bekende technische grenzen zonder implementatie voor te schrijven;
- storyversie, prioriteitsreden, `sequenceNumber` en status;
- eventueel extern Software Factory-ID en verzend- en oplevertijdstip.

Een story is pas uitvoerbaar als Software Factory haar zonder epicquery of Product Factory-call kan
bouwen. Tekst, Markdown, JSON en SVG blijven gewone UTF-8-tekst. Alleen binaire inhoud gebruikt bij
een JSON-only transport Base64; de database bewaart het oorspronkelijke object en metadata.

De backlog is exact deze query en heeft geen eigen tabel, schrijver of voorraadstatus:

```sql
select * from story
where product_id = :productId and status in ('TODO', 'IN_PROGRESS')
order by sequence_number
```

Een lege backlog is geldig en start geen proces. Productplanning verzint geen stories om een aantal
te halen. Nieuwe stories ontstaan uit een zelf gekozen `AVAILABLE` epic of uit gericht gequeue'd
herstel- of herplanningswerk.

De vier statussen betekenen:

- `TODO` — compleet, geprioriteerd en nog niet extern aangemaakt;
- `IN_PROGRESS` — door de dispatcher naar Software Factory gestuurd en daar nog open;
- `DONE` — door Software Factory ontwikkeld en opgeleverd; dit is nog geen kwaliteitsoordeel;
- `CANCELLED` — bewust niet meer uitvoeren, met bron, tijdstip en reden.

Productplanning wijzigt de volgorde van `TODO`-stories in één transactie. Een `IN_PROGRESS`-story
wordt normaal niet onderbroken. Afgeronde en geannuleerde stories bewaren hun nummer alleen voor
historie.

`cancelStoriesForEpic(...)` zet alle `TODO`-stories van een door Productontwerp geannuleerde epic
atomair op `CANCELLED`. De dispatcher controleert ook de epicstatus en verstuurt daarna niets meer
voor die epic. Een reeds `IN_PROGRESS` story loopt normaal af en wordt bij oplevering `DONE`.

## Snelle opleverstatus en epicverificatie

Wanneer de dispatcher ziet dat Software Factory een story heeft opgeleverd, roept hij
`markStoryAsDeveloped(...)` aan. Deze commandhandler start geen agent en doet in milliseconden:

1. controleer idempotentiesleutel, externe referentie en verwachte storyversie;
2. zet de story van `IN_PROGRESS` naar `DONE` en sla de oplevervelden op;
3. vraag idempotent `requestStoryVerification(...)` aan, of `requestBugfixRetest(...)` voor een
   bugfixstory; deze quality-commands queueën alleen werk;
4. controleer met een gewone databasequery of voor precies deze niet-geannuleerde epic nog stories
   met status `TODO` of `IN_PROGRESS` bestaan;
5. als dat niet zo is en de epic niet `CANCELLED` is, roep idempotent
   `markEpicReadyForVerification(...)` op Productontwerp aan;
6. roep in dat geval daarna idempotent `requestEpicVerification(...)` op Kwaliteitsbewaking aan.

Stap 4 is geen planning en heeft geen AI-agent of `PlanningWorkItem` nodig. Het kwaliteitscommand
start evenmin een agent: Kwaliteitsbewaking zet alleen een `QualityWorkItem` in haar eigen queue.
Een latere kwaliteitsrun voert de echte epiccontrole uit.

Het vervolg na die controle is:

| Bevinding | Snel command van Kwaliteitsbewaking | Later intelligent werk |
|---|---|---|
| fout in gedrag dat door een story was afgesproken | `requestBugfix(...)` | planner maakt een bugfixstory |
| gedrag binnen de bevroren epic had nooit een story | `requestEpicGapPlanning(...)` | planner maakt ontbrekende productstories |
| epic geslaagd, niet aantoonbaar, geblokkeerd of als productaanname niet geslaagd | geen planningscommand | geen nieuw planwerk, tenzij later een expliciete nieuwe aanleiding ontstaat |

`requestEpicGapPlanning(...)` zegt precies dat de tester een gat in de dekking van de bestaande
epic heeft gevonden. Het command zelf maakt geen story; het zet alleen een idempotent
`PLAN_EPIC_GAP`-werkitem klaar.

## Interne entiteiten

- `PlanningWorkItem` — duurzame queueopdracht met type, bron, status, claim en idempotentiesleutel;
- `ProcessSession` — één geclaimde intelligente run en haar operationele historie;
- `EpicCandidateSet` en `EpicSelectionAssessment` — vergelijking bij prioritering;
- `StoryDraft`, `StoryUxSnapshot` en `StoryCoverageMap` — concept, UX-kopie en dekking vóór publicatie;
- `StoryCandidateSet`, `PriorityAssessment` en `StoryOrderDraft` — kandidaatwerk en ordening;
- `Story` — inhoud, type, productbrede volgorde en de vier statussen;
- `StoryDeliveryPackage`, `DeliveryAttempt` en `ExternalWorkLink` — technische levering en historie;
- `PlanningMemory` — lessen over slicing, balans en blokkades;
- `AgentRun` — input, promptversie, output en fout van één agenttaak.

## Agents en verloop van een run

Een inhoudelijke run gebruikt vier rollen:

1. **Epicplanner** — beoordeelt beschikbare epics, geldige besluiten en gerichte workitems.
2. **Storymaker** — verdeelt iedere bevroren epic of bewezen ontbrekende dekking volledig in
   zelfstandige stories.
3. **Backlogplanner** — combineert productstories en bugfixstories en bepaalt productbreed de
   `sequenceNumber`s.
4. **Planningscriticus** — controleert dekking, storygrootte, afhankelijkheden, UX en redenen.

De run verwerkt de bij de start geclaimde batch als één consistente momentopname. Onafhankelijke
epics of storydelen mogen parallel worden voorbereid. Publicatie en de definitieve globale ordening
zijn sequentieel en atomair.

```text
claim PENDING workitems, beschikbare epics en inputversies
                      │
                      ▼
       beoordeel epics, bugs en besluiten
                      │
             ┌────────┴────────┐
             ▼                 ▼
       stories per epic   bugfix/gat-stories
             └────────┬────────┘
                      ▼
       één productbrede TODO-volgorde
                      │
                      ▼
           kritiek en atomair publiceren
```

Bij een zelf gekozen `AVAILABLE` epic maakt de run de hele epic behapbaar in stories; hij stopt niet
bij een voorraadgetal. Bij `REPRIORITIZE_EPIC` mag hij ook uitsluitend de volgorde wijzigen als er
al geschikte stories bestaan. Ontbreken beschikbare epics en workitems, dan is de run een no-op.

## Software Factory-dispatcher

De dispatcher is een eenvoudige geplande adapter binnen Productplanning. Hij gebruikt geen agents
en heeft als technische ingang:

```java
void runDispatchSession();
```

Iedere dispatchersessie:

1. synchroniseert externe statussen en registreert responses als `DeliveryAttempt`;
2. roept voor een opgeleverde story `markStoryAsDeveloped(...)` aan;
3. verstuurt niets zolang Software Factory nog open werk voor het product heeft;
4. kiest anders de afhankelijke-vrije `TODO`-story met het laagste `sequenceNumber` waarvan de epic
   niet `CANCELLED` is;
5. vormt zonder inhoudelijke beslissing één compleet `StoryDeliveryPackage`;
6. maakt idempotent één Software Factory-story aan;
7. roept `markStoryAsDispatched(...)` aan om extern ID en `IN_PROGRESS` te laten opslaan.

Als de backlog leeg is, doet de dispatcher niets. Dat is een normale toestand en geen aanleiding om
Productontwerp of Productplanning te starten. De dispatcher kan geen story overslaan, inhoud of UX
wijzigen, epic kiezen of prioriteit veranderen.

Een mislukte dispatch is intern werk van de dispatcher en geen publiek planningcommand:

- bij een timeout of tijdelijke netwerkfout bewaart hij een `DeliveryAttempt`, controleert eerst op
  dezelfde idempotentiesleutel of de story toch is aangemaakt en probeert later met backoff opnieuw;
- bij een configuratie- of autorisatiefout blokkeert hij de levering en maakt hij een operationele
  melding; er start geen planningsagent;
- alleen bij een definitieve inhoudelijke afwijzing van het `StoryDeliveryPackage` maakt
  Productplanning intern een `REPAIR_STORY`-workitem. De dispatcher verandert zelf geen story-inhoud.

## Fouten, hervatten en klaarcriteria

- Maximaal één `runProcessSession()` bewaakt de globale publicatie- en ordeningsinvariant.
- Een run gebruikt één vastgezette batch en inputmomentopname; nieuw queuewerk wacht.
- Workitems en moduleoverschrijdende commands zijn idempotent en herstelbaar.
- Een gekozen epicversie kan niet door een nieuwere ontwerpversie worden vervangen.
- De dispatcher zoekt na een timeout eerst op idempotentiesleutel en maakt nooit blind een duplicaat.
- Een fout vóór externe aanmaak laat de story `TODO`; een fout bij reeds bestaand extern werk laat
  haar `IN_PROGRESS`. In beide gevallen blijft de technische blokkade zichtbaar.

Een run is klaar wanneer ieder geclaimd workitem `DONE`, `BLOCKED` of `FAILED` is, iedere nieuwe
story zelfstandig uitvoerbaar is, de epic- en bugbronversies exact vastliggen, de productbrede
`sequenceNumber`s consistent zijn en alle output atomair en geversioneerd is opgeslagen.
