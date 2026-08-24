# Product Factory v2 — Productplanning-API

Status: eerste ontwerp van het publieke modulecontract.

Dit document beschrijft de buitenkant van Productplanning. Andere modules mogen niet afhankelijk
zijn van het aantal agents, interne concepten of de volgorde van planningsstappen. De volgende
implementaties gebruiken daarom hetzelfde contract:

- [Productplanning — MVP](mvp.md): één Planner-agent doet selectie, storyvorming en
  prioritering;
- [Productplanning — uitgebreide implementatie](uitgebreid.md): vier
  gespecialiseerde rollen met parallelle voorbereiding en een aparte criticus.

De technische [Software Factory-dispatcher](../software-factory-dispatcher.md) heeft een eigen
document en eigen Maven-API/implementatiegrens. Hij gebruikt `product-planning-api`, staat los van
de gekozen intelligente planningsimplementatie en gebruikt nooit agents.

## Verantwoordelijkheid

Productplanning zet complete, bevroren epics en gerichte herstelopdrachten om in zelfstandig
uitvoerbare stories. Zij ordent alle open stories met een productbreed `sequenceNumber`. Een story
is een productstory of een bugfixstory. Een epic kan twee stories opleveren, maar ook dertig; er is
geen kunstmatig minimum, maximum of voorraadstreefgetal.

Productplanning is eigenaar en enige schrijver van:

- `PlanningWorkItem`, de duurzame queue met gerichte planningsopdrachten;
- `Story`, inclusief type, inhoud, status, versie en `sequenceNumber`;
- `ProcessSession`, de operationele historie van een intelligente planningsrun.

De backlog is geen entiteit. Productplanning wijzigt geen epicinhoud, UX-ontwerp, bug of
verificatieresultaat. Zij gebruikt daarvoor uitsluitend publieke commands op de eigenaar.

## Publieke module-interface

De enige agentgestuurde ingang is:

```java
void runProcessSession();
```

De scheduler of een bevoegde handmatige UI-/REST-actie kan deze functie starten. Er kan modulebreed
maximaal één uitvoering tegelijk lopen. Een tweede handmatige aanroep krijgt een
`ProcessAlreadyRunning`-fout, bij REST bijvoorbeeld HTTP 409. Een botsende geplande aanroep wordt
als overgeslagen geregistreerd. Alleen `runProcessSession()` mag voor Productplanning nieuwe taken
bij [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md) aanvragen; hoeveel taken dat zijn is een
implementatiedetail.

Een run claimt atomair een vaste momentopname van de op dat moment `PENDING`
`PlanningWorkItem`s en leest de op dat moment `AVAILABLE` epics. Nieuwe verzoeken en epics blijven
voor de volgende run staan. Zijn de queue en de lijst beschikbare epics leeg, dan is de run een
succesvolle no-op.

Daarnaast biedt Productplanning deze deterministische commands en read-only queries:

```java
StoryDetails getStory(StoryId storyId);
List<StoryDetails> getBacklog(ProductId productId);
List<PlanningWorkItemDetails> findPlanningWorkItems(ProductId productId, WorkItemStatus status);

PlanningWorkItemId requestBugfix(RequestBugfixCommand command);
PlanningWorkItemId requestEpicGapPlanning(RequestEpicGapPlanningCommand command);
PlanningWorkItemId requestEpicReprioritization(RequestEpicReprioritizationCommand command);
PlanningWorkItemId requestManualReplan(RequestManualReplanCommand command);

StoryDispatchReservationDetails reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand command);
void markStoryAsDispatched(MarkStoryAsDispatchedCommand command);
void markStoryAsDeveloped(MarkStoryAsDevelopedCommand command);
void recordStoryVerification(RecordStoryVerificationCommand command);
void cancelStoriesForEpic(CancelStoriesForEpicCommand command);
```

De vier `request...`-commands starten geen agents en voeren geen inhoudelijke planning uit. Zij
valideren bron, versie en idempotentiesleutel en voegen alleen een duurzaam `PENDING`-werkitem toe.
De dispatch-, story- en lifecyclecommands zijn eveneens snel en deterministisch.
`reserveNextStoryForDispatch(...)` is uitsluitend voor de dispatcher en reserveert atomair hooguit
één uitvoerbare story. Andere modules krijgen nooit schrijftoegang tot `Story` of
`PlanningWorkItem`.

## PlanningWorkItem: de queuegrens

Een `PlanningWorkItem` bevat minimaal:

- work-item-ID, product-ID, type en status;
- bronmodule, bron-ID en exacte bronversie;
- epic-ID en epicversie wanneer van toepassing;
- prioriteitsaanwijzing en reden, zonder de definitieve storyvolgorde voor te schrijven;
- idempotentiesleutel, aanmaakmoment, claim en foutinformatie.

| Type | Aangevraagd door | Betekenis |
|---|---|---|
| `PLAN_BUGFIX` | Kwaliteitsbewaking via `requestBugfix(...)` | maak een uitvoerbare bugfixstory voor deze bug |
| `PLAN_EPIC_GAP` | Kwaliteitsbewaking via `requestEpicGapPlanning(...)` | maak stories voor gedrag dat al binnen de bevroren epic viel, maar nooit in een story stond |
| `REPRIORITIZE_EPIC` | product-/overlegmodule via `requestEpicReprioritization(...)` | geef een door de Stakeholder aangewezen epic voorrang en herschik zo nodig `TODO`-stories |
| `MANUAL_REPLAN` | bevoegde productbediening via `requestManualReplan(...)` | vraag een expliciete herbeoordeling aan zonder zelf stories te schrijven |

De statussen zijn `PENDING`, `IN_PROGRESS`, `DONE`, `BLOCKED` en `FAILED`. Eenzelfde bronversie en
opdracht maken door idempotentie nooit twee workitems.

## Interface met andere modules en services

Productplanning gebruikt alleen publieke Maven-API-modules. Read-only DTO's staan in de
betreffende `*-api`-module en zijn geen eigen database-entiteiten. Spring Modulith structureert
alleen de binnenkant van de gekozen Productplanning-implementatie.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `PlanningWorkItem` | Productplanning | duurzame opdracht die de run claimt; andere modules kunnen alleen een aanvraagcommand doen |
| `ProductAssignmentDetails` | productmodule | productidentiteit, grenzen en publieke Git-URL |
| `DecisionDto` | Besluitenregister-query voor het huidige tijdstip | grote blijvende keuzes die de planning begrenzen; geen directe opdracht om een epic, bug of story te kiezen |
| `EpicDetails` | Productontwerp | exacte epicversie, gebruikerswaarde, scope, UX, succescriteria en status |
| `BugDetails` | Kwaliteitsbewaking | uitvoerbare bug inclusief ernst, bewijs en versie |
| `VerificationDetails` | Kwaliteitsbewaking | bewijs voor ontbrekend gedrag binnen een bevroren epic |
| `TestableProductDetails` | productmodule | acceptatie- en eventueel productieomgeving, veilige routes, accounts en toegangsgrenzen |
| `AgentMemoryItemDetails` | Agentgeheugen | alleen de actuele geheugenitems van de agentrol die op dat moment wordt uitgevoerd |
| `AiJobConfigurationDetails` | AI-uitvoering (`settings`) | actuele provider en model voor het soort planningsjob; bevroren op iedere nieuwe taak |
| `AiTaskResultDetails` | AI-uitvoering | opaque resultaat van een eerder door deze processessie aangevraagde taak |

Een processessie bewaart haar AI-taak-ID's en keert met `WAITING_FOR_AI` terug zonder thread of lock
vast te houden. Een volgende run hervat dezelfde sessie. Ontbreken de resultaten nog, dan maakt zij
geen duplicaten en blijft zij wachten.

Tijdens een inhoudelijke sessie mag Productplanning de publieke Git-URL uitchecken en broncode,
tests en documentatie read-only bekijken. Zij commit en pusht nooit. De bekeken commit-SHA kan als
bronverwijzing worden opgeslagen, maar iedere story bevat zelfstandig alle benodigde product- en
UX-informatie.

Productplanning mag ook de werkende applicatie read-only bekijken. Acceptatie is de
voorkeursomgeving voor interactie; productie wordt alleen via veilige routes of expliciete
testaccounts gebruikt. Dit geeft context over bestaande gebruikersroutes en schermen, maar is geen
kwaliteitsoordeel.

### Eigen output en downstream effect

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `StoryDetails` | read-only weergave van een zelfstandig uitvoerbare productstory of bugfix | type, bronrelaties, epicversie, gedrag, acceptatiecriteria, UX, `sequenceNumber`, leveringsstatus, eventuele dispatchreservering, externe referentie en actuele verificatiereferentie |
| backlogquery | alle uitvoerbare of reeds verzonden stories in volgorde | `StoryDetails` met status `TODO` of `IN_PROGRESS`, geordend op `sequenceNumber` |
| `PlanningWorkItemDetails` | read-only inzicht in de planningsqueue | type, bron, status, claim, resultaat en fout |
| `StoryDispatchReservationDetails` | tijdelijke read-only reservering voor de dispatcher | reserverings-ID en onveranderlijke momentopname van één uitvoerbare story; geen aparte productentiteit |
| `ProcessSession` | opgeslagen operationele historie van de intelligente run | implementatie-ID en -versie, geclaimde workitems, inputversies, AI-taak-ID's, publicaties, wacht- of eindstatus en blokkade |
| `QualityWorkItem` bij Kwaliteitsbewaking | downstream effect van een verificatiecommand; Kwaliteitsbewaking maakt en bezit dit object | type, exact doel-ID en -versie, bron, prioriteit en idempotentiesleutel |

Operations en frontend lezen de sessie via `ProcessSessionDetails`. Interne analyses, concepten en
agentuitvoer steken de modulegrens niet over. Permanent leren loopt uitsluitend via de publieke API
van [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md): een agent kan alleen geheugen van zijn eigen rol lezen en
wijzigen.

Na storyoplevering vraagt Productplanning via `requestStoryVerification(...)` of
`requestBugfixRetest(...)` aan Kwaliteitsbewaking om werk klaar te zetten. Kwaliteitsbewaking maakt
en bezit het resulterende `QualityWorkItem`. Na publicatie van de uitkomst meldt Kwaliteitsbewaking
de exacte verificatie idempotent via `recordStoryVerification(...)`. Pas wanneer alle stories en
bugfixes binnen de epic een geslaagde actuele verificatie hebben, vraagt Productplanning via
`requestEpicVerification(...)` de complete epiccontrole aan.

## Een epic plannen

Productontwerp publiceert een complete epic met status `AVAILABLE`. Tijdens een processessie:

1. vraagt Productplanning beschikbare epics read-only op;
2. kiest zij op basis van productdoel, geldige besluiten, gebruikerswaarde en bestaand werk;
3. roept zij `claimEpicForPlanning(...)` op Productontwerp aan;
4. bevriest Productontwerp de exacte versie en zet de epic op `IN_PLANNING`;
5. maakt Productplanning een volledige, samenhangende set stories voor de hele epic;
6. ordent zij nieuwe stories tussen alle andere `TODO`-stories;
7. controleert zij vlak voor publicatie dat geen duurzame annuleringsmarker voor deze epic bestaat;
8. publiceert zij stories atomair en roept `markEpicActive(...)` aan.

Meerdere epics mogen tegelijk `IN_PLANNING`, `ACTIVE` of `VERIFYING` zijn. De Stakeholder kan via de
UI `requestEpicReprioritization(...)` laten aanroepen. Een volgende planningsrun mag die epic
claimen, stories maken en alle `TODO`-stories opnieuw ordenen. Een `IN_PROGRESS` story loopt normaal
door.

De globale `sequenceNumber`-volgorde is de enige werkelijke dispatchvolgorde. Stories van
verschillende epics mogen door elkaar staan. Er is geen tweede roadmap- of backlogentiteit.
`sequenceNumber`s zijn uniek ten opzichte van alle open `TODO`- én `IN_PROGRESS`-stories; een
herordening verandert alleen de nummers van `TODO`-stories.

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

Een lege backlog is geldig en start geen proces. Nieuwe stories ontstaan alleen uit een zelf
gekozen `AVAILABLE` epic of gericht gequeue'd bugfix-, dekkings- of herplanningswerk.

De storystatussen zijn:

- `TODO` — compleet, geprioriteerd en nog niet extern aangemaakt;
- `IN_PROGRESS` — naar Software Factory gestuurd en daar nog open;
- `DONE` — door Software Factory ontwikkeld en opgeleverd; nog geen kwaliteitsoordeel;
- `CANCELLED` — bewust niet meer uitvoeren, met bron, tijdstip en reden.

Productplanning wijzigt alleen de volgorde van `TODO`-stories. Een `IN_PROGRESS` story wordt normaal
niet onderbroken.

`cancelStoriesForEpic(...)` bewaart altijd een interne duurzame annuleringsmarker voor epic-ID en
epicversie, ook als nog geen stories bestaan. In dezelfde transactie zet het alle niet-gereserveerde
`TODO`-stories op `CANCELLED`. Een latere of op AI wachtende planningssessie mag door de marker geen
stories meer voor die epic publiceren. Een `IN_PROGRESS` of al voor dispatch gereserveerde story
geldt als gestart en loopt normaal af.

## Atomaire dispatchreservering

De dispatcher verstuurt geen eerder los gelezen backlogstory. Na synchronisatie van bestaand extern
werk vraagt zij `reserveNextStoryForDispatch(...)` aan. Productplanning kiest en reserveert in één
transactie de afhankelijkheidsvrije `TODO`-story met het laagste `sequenceNumber` die compleet is,
waarvan de bugkoppeling zo nodig bevestigd is en waarvoor geen annuleringsmarker bestaat.

De reservering blijft intern onderdeel van de storyadministratie en levert een onveranderlijke
`StoryDispatchReservationDetails` op. Bij succesvolle externe aanmaak bevestigt
`markStoryAsDispatched(...)` dezelfde reservering, legt het externe ID vast en zet de story op
`IN_PROGRESS`. Bij een tijdelijke externe fout houdt de dispatcher dezelfde reservering en
idempotentiesleutel voor herstel. Een tweede sessie kan daardoor niet een andere story voor dat
product passeren.

Annulering en reservering gebruiken dezelfde Productplanning-transactiegrens. Wint annulering, dan
kan de story niet meer worden gereserveerd. Wint de reservering, dan geldt de story als reeds gestart
en mag de dispatcher de gereserveerde levering afmaken. Dit is de eenduidige grens voor gelijktijdige
UI-annulering en dispatch.

## Snelle opleverstatus en epicverificatie

Wanneer de dispatcher een Software Factory-oplevering ziet, roept hij
`markStoryAsDeveloped(...)` aan. Deze handler start geen agent en doet in milliseconden:

1. valideer idempotentiesleutel, externe referentie en verwachte storyversie;
2. zet de story van `IN_PROGRESS` naar `DONE` en bewaar de oplevervelden;
3. vraag `requestStoryVerification(...)` aan, of `requestBugfixRetest(...)` bij een bugfix;
4. laat de epic `ACTIVE`; oplevering alleen is nog geen toestemming voor epicverificatie.

Nadat Kwaliteitsbewaking een storyverificatie of bugfixhertest heeft gepubliceerd, roept zij
`recordStoryVerification(...)` aan. Deze snelle handler:

1. valideert storyversie, verificatie-ID, doelversie, uitkomst en idempotentiesleutel;
2. legt op de story alleen de actuele verificatiereferentie en uitkomst vast; de leveringsstatus
   blijft `DONE`;
3. laat de epic `ACTIVE` bij een afgekeurde of geblokkeerde controle;
4. controleert bij een geslaagde controle of alle niet-geannuleerde stories `DONE` zijn en iedere
   story of bugfix een geslaagde actuele verificatie heeft;
5. controleert dat geen open bug en geen `PENDING` of `IN_PROGRESS` bugfix- of dekkingsopdracht voor
   de epic bestaat;
6. roept alleen dan idempotent `markEpicReadyForVerification(...)` en daarna
   `requestEpicVerification(...)` aan.

Deze stappen zijn geen planning en hebben geen AI-agent of `PlanningWorkItem` nodig. De
qualitycommands starten evenmin agents; zij maken alleen `QualityWorkItem`s.

| Bevinding van Kwaliteitsbewaking | Snel command | Later intelligent planwerk |
|---|---|---|
| bug in afgesproken storygedrag | `requestBugfix(...)` | maak een bugfixstory |
| gedrag binnen de bevroren epic had nooit een story | `requestEpicGapPlanning(...)` | maak aanvullende productstories |
| epiccontrole geblokkeerd of productaanname niet geslaagd | geen | retry bij blokkade; alleen een nieuwe epic bij een latere expliciete productaanleiding |

Wanneer Productplanning een bugfixstory publiceert, bewaart zij in dezelfde transactie een
herstelbaar uitgaand effect voor `linkBugfixStory(bugId, storyId)`. De story wordt pas uitvoerbaar en
het `PlanningWorkItem` pas `DONE` nadat Kwaliteitsbewaking de koppeling idempotent heeft bevestigd.
Een afgekeurde bugfixhertest levert een nieuwe bug en daarmee eventueel een nieuwe bugfixstory op;
de storytypen blijven uitsluitend `PRODUCT_STORY` en `BUGFIX`.

## Grens met de Software Factory-dispatcher

De dispatcher leest status en open werk via Productplanning, reserveert de volgende story via
`reserveNextStoryForDispatch(...)` en meldt verzending en oplevering via
`markStoryAsDispatched(...)` en `markStoryAsDeveloped(...)`. De intelligente planner
kent geen extern Software Factory-protocol. Pakketvorming, externe statussynchronisatie,
`DeliveryAttempt`s, retry en idempotentie staan volledig in het
[dispatcherdocument](../software-factory-dispatcher.md).

Software Factory accepteert ieder contractgeldig `StoryDeliveryPackage`. Een weigering is daarom
een fout in de integratie of Software Factory en levert nooit planwerk of een aangepaste story op.
Ook een tijdelijke transport-, configuratie- of autorisatiefout start geen planningsagent.

## Fouten en idempotentie

- Maximaal één `runProcessSession()` bewaakt globale publicatie en ordening.
- Een run gebruikt één vastgezette batch en inputmomentopname; nieuw queuewerk wacht.
- Workitems en moduleoverschrijdende commands zijn idempotent en herstelbaar.
- Een gekozen epicversie kan niet door een nieuwere ontwerpversie worden vervangen.
- Een mislukte run laat per workitem een zichtbare status en fout achter.

## Eisen aan iedere implementatie

De MVP en iedere latere implementatie moeten garanderen dat:

- zij dezelfde `product-planning-api` implementeert en andere capabilities alleen via hun
  API-module gebruikt;
- iedere nieuwe `ProcessSession` de exacte `implementationId` en `implementationVersion` vastlegt;
- alleen `runProcessSession()` voor Productplanning nieuwe AI-taken aanvraagt;
- maximaal één uitvoering tegelijk loopt en een wachtende sessie geen technische lock vasthoudt;
- ieder geclaimd workitem eindigt als `DONE`, `BLOCKED` of `FAILED`;
- een epic volledig wordt afgedekt door zelfstandig uitvoerbare stories;
- iedere story het volledige Storycontract volgt;
- epic- en bugbronversies exact vastliggen;
- `sequenceNumber`s productbreed consistent en uniek zijn;
- bugfixstories pas na de bevestigde natuurlijke koppeling `linkBugfixStory(bugId, storyId)`
  uitvoerbaar worden;
- een annuleringsmarker latere storypublicatie verhindert en dispatchreservering de gelijktijdige
  grens met annuleren atomair maakt;
- de eigen procesruntime iedere agent alleen het actuele geheugen van haar vertrouwd geconfigureerde eigen rol
  geeft en de exact gelezen geheugenversies vastlegt;
- iedere AI-taak een vaste provider, model en configuratieversie heeft en via AI-uitvoering loopt;
- publicatie en definitieve ordening atomair gebeuren;
- de dispatcher via de beschreven commands kan leveren zonder interne planningskennis.

## Gerelateerde documenten

- [Productplanning — MVP](mvp.md)
- [Productplanning — uitgebreide implementatie](uitgebreid.md)
- [Software Factory-dispatcher](../software-factory-dispatcher.md)
- [Productontwerp-API](../productontwerp/api.md)
- [Kwaliteitsbewaking-API](../kwaliteitsbewaking/api.md)
- [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md)
- [Maven en Spring Modulith](../../platform/maven-en-spring-modulith.md)
- [Processen en entiteiten](../processen-en-entiteiten.md)
