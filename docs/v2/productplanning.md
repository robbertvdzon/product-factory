# Product Factory v2 — Productplanning

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt Productplanning en de technische Software Factory-dispatcher uit. De
black-boxinterface in [Product Factory v2 — overzicht](overzicht.md) is
leidend.

## Verantwoordelijkheid

Productplanning zet een complete, bevroren epic om in zelfstandig uitvoerbare stories en ordent
alle nog niet afgeronde stories met een productbreed `sequenceNumber`. Een story is een
productstory of een bugfix. De epic bepaalt de inhoudelijke grens: zij kan twee stories opleveren,
maar ook dertig. Er is geen kunstmatig minimum, maximum of voorraadstreefgetal.

De module is eigenaar van:

- de duurzame queue met planningsopdrachten;
- stories, storytype, status en `sequenceNumber`;
- epicselectie voor een planningsopdracht en de opdrachten waarmee de epicstatus bij
  Productontwerp wordt bijgewerkt;
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
`PlanningWorkItem`s. Nieuwe verzoeken die tijdens de run binnenkomen blijven voor de volgende run
staan. Is de queue leeg, dan eindigt de run als succesvolle no-op.

Daarnaast biedt Productplanning deterministische commands en read-only queries:

```java
StoryDetails getStory(StoryId storyId);
List<StoryDetails> getBacklog(ProductId productId);
List<PlanningWorkItemDetails> findPlanningWorkItems(ProductId productId, WorkItemStatus status);

PlanningWorkItemId requestEpicPlanning(RequestEpicPlanningCommand command);
PlanningWorkItemId requestBugfix(RequestBugfixCommand command);
PlanningWorkItemId requestEpicGapPlanning(RequestEpicGapPlanningCommand command);
PlanningWorkItemId requestEpicReprioritization(RequestEpicReprioritizationCommand command);
PlanningWorkItemId requestManualReplan(RequestManualReplanCommand command);

void markStoryAsDispatched(MarkStoryAsDispatchedCommand command);
void markStoryAsDeveloped(MarkStoryAsDevelopedCommand command);
void recordDispatchFailure(RecordDispatchFailureCommand command);
```

De vijf `request...`-commands starten geen agents en voeren geen inhoudelijke planning uit. Zij
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
| `PLAN_EPIC` | Productontwerp via `requestEpicPlanning(...)` | verdeel deze beschikbare epic in stories |
| `PLAN_BUGFIX` | Kwaliteitsbewaking via `requestBugfix(...)` | maak een uitvoerbare bugfixstory voor deze bug |
| `PLAN_EPIC_GAP` | Kwaliteitsbewaking via `requestEpicGapPlanning(...)` | maak stories voor gedrag dat al binnen de bevroren epic viel, maar nooit in een story stond |
| `REPRIORITIZE_EPIC` | product-/overlegmodule via `requestEpicReprioritization(...)` | geef een door de Stakeholder aangewezen epic voorrang en herschik zo nodig `TODO`-stories |
| `MANUAL_REPLAN` | bevoegde productbediening via `requestManualReplan(...)` | vraag een expliciete herbeoordeling aan zonder zelf stories te schrijven |

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
| `StakeholderDirectionDetails` | product-/overlegmodule | bindende epic- en prioriteitsgrenzen |
| `DecisionDto` | Besluitenregister-query voor het huidige tijdstip | grote blijvende keuzes die de planning begrenzen; geen directe opdracht om een epic, bug of story te kiezen |
| `EpicDetails` | Productontwerp | exacte epicversie, gebruikerswaarde, scope, UX, succescriteria en status |
| `BugDetails` | Kwaliteitsbewaking | uitvoerbare bug inclusief ernst, bewijs en versie |
| `VerificationDetails` | Kwaliteitsbewaking | bewijs voor een ontbrekend stuk binnen een bevroren epic |
| `SoftwareFactoryWork` | dispatcheradapter | actuele externe status van eerder verzonden werk; tijdelijk integratiegegeven |

Tijdens een inhoudelijke sessie mag Productplanning de publieke Git-URL uitchecken en broncode,
tests en documentatie read-only bekijken. Zij commit en pusht nooit. De bekeken commit-SHA kan als
bronverwijzing worden opgeslagen, maar iedere story blijft zelfstandig en bevat alle benodigde
product- en UX-informatie.

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `StoryDetails` | read-only weergave van een zelfstandig uitvoerbare productstory of bugfix | type, bronrelaties, epicversie, gedrag, acceptatiecriteria, UX, `sequenceNumber`, status en externe referentie |
| backlogquery | alle niet-afgeronde stories in uitvoervolgorde | `StoryDetails` met status `TODO` of `IN_PROGRESS`, geordend op `sequenceNumber` |
| `PlanningWorkItemDetails` | read-only inzicht in de planningsqueue | type, bron, status, claim, resultaat en fout; geen wijzigbaar requestobject |
| `StoryDeliveryPackage` | onveranderlijk pakket voor Software Factory | complete story of bugfix, acceptatiecriteria, UX, attachments, bronversies, hashes en idempotentiesleutel |
| `ProcessSession` | operationele historie van de intelligente run | geclaimde workitems, inputversies, agentruns, publicaties en eindstatus |

`PlanningWorkItem`, `Story`, `ProcessSession` en `DeliveryAttempt` zijn eigendom van
Productplanning. De backlog is geen entiteit. Epicselectie, storyvorming, backlogvolgorde en het
voorrang geven aan een epic of bug zijn gewone proceskeuzes en worden niet in het Besluitenregister
opgenomen.

## Een epic plannen

Productontwerp publiceert eerst een complete epic en roept daarna
`requestEpicPlanning(epicId, epicVersion, ...)` aan. Tijdens een latere processessie:

1. claimt Productplanning het bijbehorende `PlanningWorkItem`;
2. leest zij exact die `EpicDetails`;
3. roept zij `claimEpicForPlanning(...)` op Productontwerp aan;
4. bevriest Productontwerp de versie en zet de epic op `IN_PLANNING`;
5. maakt Productplanning een volledige, samenhangende set stories voor de epic;
6. ordent zij de nieuwe stories tussen alle andere `TODO`-stories;
7. publiceert zij de stories atomair en roept `markEpicActive(...)` aan;
8. markeert zij het workitem als `DONE`.

Meerdere epics mogen tegelijk `IN_PLANNING`, `ACTIVE` of `VERIFYING` zijn. Normaal houdt de planner
één hoofdepic aan, maar dit is geen technische invariant. De Stakeholder kan via
`recordStakeholderDirection(...)` een andere epic urgent maken. De product-/overlegmodule vraagt
dan `REPRIORITIZE_EPIC` aan. Een volgende planningsrun mag die epic claimen, stories maken en alle
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
where product_id = :productId and status <> 'DONE'
order by sequence_number
```

Een lege backlog is geldig en start geen proces. Productplanning verzint geen stories om een aantal
te halen. Nieuwe stories ontstaan alleen uit gequeue'd werk, bijvoorbeeld nadat Productontwerp een
epic beschikbaar heeft gemaakt.

De statussen betekenen:

- `TODO` — compleet, geprioriteerd en nog niet extern aangemaakt;
- `IN_PROGRESS` — door de dispatcher naar Software Factory gestuurd en daar nog open;
- `DONE` — door Software Factory ontwikkeld en opgeleverd; dit is nog geen kwaliteitsoordeel.

Productplanning wijzigt de volgorde van `TODO`-stories in één transactie. Een `IN_PROGRESS`-story
wordt normaal niet onderbroken. Afgeronde stories bewaren hun nummer alleen voor historie.

## Snelle opleverstatus en epicverificatie

Wanneer de dispatcher ziet dat Software Factory een story heeft opgeleverd, roept hij
`markStoryAsDeveloped(...)` aan. Deze commandhandler start geen agent en doet in milliseconden:

1. controleer idempotentiesleutel, externe referentie en verwachte storyversie;
2. zet de story van `IN_PROGRESS` naar `DONE` en sla de oplevervelden op;
3. vraag idempotent `requestStoryVerification(...)` aan, of `requestBugfixRetest(...)` voor een
   bugfixstory; deze quality-commands queueën alleen werk;
4. controleer met een gewone databasequery of voor precies deze epic nog stories bestaan met een
   andere status dan `DONE`;
5. als dat niet zo is, roep idempotent `markEpicReadyForVerification(...)` op Productontwerp aan;
6. roep daarna idempotent `requestEpicVerification(...)` op Kwaliteitsbewaking aan.

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
- `Story` — inhoud, type, productbrede volgorde en de drie statussen;
- `StoryDeliveryPackage`, `DeliveryAttempt` en `ExternalWorkLink` — technische levering en historie;
- `PlanningMemory` — lessen over slicing, balans en blokkades;
- `AgentRun` — input, promptversie, output en fout van één agenttaak.

## Agents en verloop van een run

Een inhoudelijke run gebruikt vier rollen:

1. **Epicplanner** — beoordeelt de gequeue'de epics en geldende prioriteitsrichting.
2. **Storymaker** — verdeelt iedere bevroren epic of bewezen ontbrekende dekking volledig in
   zelfstandige stories.
3. **Backlogplanner** — combineert productstories en bugfixstories en bepaalt productbreed de
   `sequenceNumber`s.
4. **Planningscriticus** — controleert dekking, storygrootte, afhankelijkheden, UX en redenen.

De run verwerkt de bij de start geclaimde batch als één consistente momentopname. Onafhankelijke
epics of storydelen mogen parallel worden voorbereid. Publicatie en de definitieve globale ordening
zijn sequentieel en atomair.

```text
claim PENDING PlanningWorkItems en inputversies
                      │
                      ▼
       beoordeel epics, bugs en richtingen
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

Bij een `PLAN_EPIC` maakt de run de hele epic behapbaar in stories; hij stopt niet bij een
voorraadgetal. Bij `REPRIORITIZE_EPIC` mag hij ook uitsluitend de volgorde wijzigen als er al
geschikte stories bestaan. Een lege batch is een no-op.

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
4. kiest anders de afhankelijke-vrije `TODO`-story met het laagste `sequenceNumber`;
5. vormt zonder inhoudelijke beslissing één compleet `StoryDeliveryPackage`;
6. maakt idempotent één Software Factory-story aan;
7. roept `markStoryAsDispatched(...)` aan om extern ID en `IN_PROGRESS` te laten opslaan.

Als de backlog leeg is, doet de dispatcher niets. Dat is een normale toestand en geen aanleiding om
Productontwerp of Productplanning te starten. De dispatcher kan geen story overslaan, inhoud of UX
wijzigen, epic kiezen of prioriteit veranderen.

## Fouten, hervatten en klaarcriteria

- Maximaal één `runProcessSession()` bewaakt de globale publicatie- en ordeningsinvariant.
- Een run gebruikt één vastgezette batch en inputmomentopname; nieuw queuewerk wacht.
- Workitems en moduleoverschrijdende commands zijn idempotent en herstelbaar.
- Een gekozen epicversie kan niet door een nieuwere ontwerpversie worden vervangen.
- De dispatcher zoekt na een timeout eerst op idempotentiesleutel en maakt nooit blind een duplicaat.
- Een extern geblokkeerde story blijft `IN_PROGRESS`; de blokkade blijft zichtbaar.

Een run is klaar wanneer ieder geclaimd workitem `DONE`, `BLOCKED` of `FAILED` is, iedere nieuwe
story zelfstandig uitvoerbaar is, de epic- en bugbronversies exact vastliggen, de productbrede
`sequenceNumber`s consistent zijn en alle output atomair en geversioneerd is opgeslagen.
