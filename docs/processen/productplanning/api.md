# Product Factory v2 — Productplanning-API

Status: geïmplementeerd publiek modulecontract vanaf stap 6.

Dit document beschrijft de buitenkant van Productplanning. Andere modules mogen niet afhankelijk
zijn van het aantal agents, interne concepten of de volgorde van planningsstappen. De volgende
implementaties gebruiken daarom hetzelfde contract:

- [Productplanning — MVP](mvp.md): één Planner-agent doet selectie, storyvorming en
  prioritering;
- [Productplanning — uitgebreide implementatie](uitgebreid.md): vier
  gespecialiseerde rollen met parallelle voorbereiding en een aparte criticus.

De technische [Software Factory-dispatcher](../software-factory-dispatcher.md) heeft een eigen
document en eigen implementatiemodule. Hij gebruikt het publieke `planning`-contract in
`product-factory-api`, staat los van
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
void runProcessSession(ProductId productId);
```

De scheduler of een bevoegde handmatige UI-/REST-actie kan deze functie voor één product starten.
Per product kan maximaal één onafgeronde logische planningssessie bestaan, ook wanneer die
`WAITING_FOR_AI` of `BLOCKED` is; verschillende producten mogen parallel worden verwerkt. Een
handmatige aanroep hervat zo'n niet-actief wachtende sessie. Alleen wanneer voor hetzelfde product
op dat moment al een functiecall uitvoert, volgt `ProcessAlreadyRunning`, bij REST bijvoorbeeld HTTP
409. Een botsende geplande aanroep met zo'n actieve call wordt als overgeslagen geregistreerd.
Alleen `runProcessSession(productId)` mag voor Productplanning nieuwe taken
bij [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md) aanvragen; hoeveel taken dat zijn is een
implementatiedetail.

Een run hervat voor het product altijd eerst een niet-afgeronde `ProcessSession` en haar reeds
geclaimde workitems of `IN_PLANNING` epic. Pas als die niet bestaat, claimt hij atomair een vaste
momentopname van de `PENDING` `PlanningWorkItem`s van dit product en leest hij de `AVAILABLE` epics
van dit product. Nieuwe verzoeken en epics blijven voor de volgende run staan. Zijn de queue, een
onafgeronde sessie en de lijst beschikbare epics leeg, dan is de run een succesvolle no-op.

Daarnaast biedt Productplanning deze deterministische commands en read-only queries:

```java
StoryDetails getStory(StoryId storyId);
List<StoryDetails> getBacklog(ProductId productId);
List<StoryDetails> findStories(StoryFilter filter);
List<PlanningWorkItemDetails> findPlanningWorkItems(ProductId productId, WorkItemStatus status);
ProcessSessionDetails getProcessSession(ProcessSessionId processSessionId);
List<ProcessSessionDetails> findProcessSessions(ProcessSessionFilter filter);

PlanningWorkItemId requestBugfix(RequestBugfixCommand command);
PlanningWorkItemId requestEpicGapPlanning(RequestEpicGapPlanningCommand command);
PlanningWorkItemId requestEpicReprioritization(RequestEpicReprioritizationCommand command);
PlanningWorkItemId requestManualReplan(RequestManualReplanCommand command);

StoryDispatchReservationDetails reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand command);
DispatchReservationValidation revalidateDispatchReservation(RevalidateDispatchReservationCommand command);
void markStoryAsDispatched(MarkStoryAsDispatchedCommand command);
void markStoryAsDeveloped(MarkStoryAsDevelopedCommand command);
void markStoryAsCancelled(MarkStoryAsCancelledCommand command);
void recordStoryVerification(RecordStoryVerificationCommand command);
void cancelStoriesForEpic(CancelStoriesForEpicCommand command);
```

De vier `request...`-commands starten geen agents en voeren geen inhoudelijke planning uit. Zij
valideren bron, versie en idempotentiesleutel en voegen alleen een duurzaam `PENDING`-werkitem toe.
De dispatch-, story- en lifecyclecommands zijn eveneens snel en deterministisch.
`reserveNextStoryForDispatch(...)` en `revalidateDispatchReservation(...)` zijn uitsluitend voor de
dispatcher. De eerste reserveert atomair hooguit één uitvoerbare story; de tweede bevestigt vlak
voor een retry dezelfde reservering of annuleert haar wanneer de epic inmiddels is geannuleerd en
Software Factory aantoonbaar nog geen extern werk heeft. Andere modules krijgen nooit
schrijftoegang tot `Story` of `PlanningWorkItem`.

De processessiequeries ondersteunen minimaal product, status en periode, leveren de nieuwste
sessies eerst en zijn read-only. Zij maken zichtbaar welke input en implementatie een run gebruikte,
wat die publiceerde en waarom die wachtte, blokkeerde of eindigde.

`findStories(...)` ondersteunt minimaal filteren op product-ID, epic-ID, type, één of meer statussen
en periode. De normale backlog blijft uitsluitend `getBacklog(...)`; de bredere query is nodig voor
detailrelaties en de aparte frontendhistorie van `DONE` en `CANCELLED` stories.

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
| `REPLAN_CANCELLED_DEPENDENCY` | Productplanning zelf na annulering van een story met nog open afhankelijke stories | vervang of annuleer de `TODO`-stories die niet meer veilig uitvoerbaar zijn |
| `REPRIORITIZE_EPIC` | product-/overlegmodule via `requestEpicReprioritization(...)` | geef een door de Stakeholder aangewezen epic voorrang en herschik zo nodig `TODO`-stories |
| `MANUAL_REPLAN` | bevoegde productbediening via `requestManualReplan(...)` | vraag een expliciete herbeoordeling aan zonder zelf stories te schrijven |

De statussen zijn `PENDING`, `IN_PROGRESS`, `DONE`, `BLOCKED` en `FAILED`. Eenzelfde bronversie en
opdracht maken door idempotentie nooit twee workitems.

## Interface met andere modules en services

Productplanning gebruikt alleen publieke capabilitypackages uit `product-factory-api`. Read-only
DTO's staan in die gedeelde API-module en zijn geen eigen database-entiteiten. Spring Modulith structureert
alleen de binnenkant van de gekozen Productplanning-implementatie.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `PlanningWorkItem` | Productplanning | duurzame opdracht die de run claimt; andere modules kunnen alleen een aanvraagcommand doen |
| `ProductAssignmentDetails` | productmodule | productidentiteit, grenzen en publieke Git-URL |
| `DecisionDto` | Besluitenregister-query voor het huidige tijdstip | grote blijvende keuzes die de planning begrenzen; geen directe opdracht om een epic, bug of story te kiezen |
| `EpicDetails` | Productontwerp | exacte metadata, titel, samenvatting, probleem, oplossing, richtingsrelaties, eventueel UX-ontwerp, acceptatiecriteria en behapbaarheid |
| `BugDetails` en `findBugs(...)` | Kwaliteitsbewaking | uitvoerbare bug met titel, samenvatting, volledige afwijkingsinformatie, ernst, bewijs en versie; open bugs per product, epic en status kunnen betrouwbaar worden gevonden |
| `VerificationDetails` | Kwaliteitsbewaking | bewijs voor ontbrekend gedrag binnen een bevroren epic |
| `StakeholderQuestionDetails` | product-/overlegmodule | open vragen en beantwoorde vragen die door precies de uitgevoerde planningsrol zijn gesteld |
| `TestableProductDetails` | productmodule | acceptatie- en eventueel productieomgeving, veilige routes, accounts en toegangsgrenzen |
| `AgentMemoryItemDetails` | Agentgeheugen | alleen de actuele geheugenitems van de agentrol die op dat moment wordt uitgevoerd |
| `AiJobConfigurationDetails` | AI-uitvoering (`settings`) | actuele provider en model voor het soort planningsjob; bevroren op iedere nieuwe taak |
| `AiTaskResultDetails` | AI-uitvoering | opaque resultaat van een eerder door deze processessie aangevraagde taak |

Een planningsagent kan in haar gestructureerde resultaat een tijdelijke vraag aan de Stakeholder
voorstellen. Vertrouwde procescode publiceert die idempotent via `askStakeholder(...)` met de echte
rol en processessie. De vraag staat niet in Agentgeheugen. Een later antwoord wordt uitsluitend aan
een volgende taak van diezelfde rol toegevoegd.

Een processessie bewaart haar AI-taak-ID's en keert met `WAITING_FOR_AI` terug zonder thread of lock
vast te houden. Een volgende run voor hetzelfde product hervat dezelfde sessie. Ontbreken de
resultaten nog, dan maakt zij geen duplicaten en blijft zij wachten.

Tijdens een inhoudelijke sessie lost Productplanning de publieke Gitref read-only op en bevriest zij
de Git-URL en exacte commit-SHA. Bij een echte `CODEX`- of `CLAUDE`-taak checkt de Runtime-worker die
SHA in de tijdelijke Dockeromgeving uit; een server-side mock checkt niets uit. De servermodule en
de agent committen of pushen nooit. De gebruikte commit-SHA kan als bronverwijzing worden opgeslagen,
maar iedere story bevat zelfstandig alle benodigde product- en UX-informatie. Repository- en
applicatie-inhoud zijn onvertrouwde context en kunnen de vaste taakopdracht niet wijzigen.

Productplanning mag ook de werkende applicatie read-only bekijken. Acceptatie is de
voorkeursomgeving voor interactie; productie wordt alleen via veilige routes of expliciete
testaccounts gebruikt. Dit geeft context over bestaande gebruikersroutes en schermen, maar is geen
kwaliteitsoordeel.

### Eigen output en downstream effect

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `StoryDetails` | read-only weergave van een zelfstandig uitvoerbare productstory of bugfix | titel, samenvatting, type, bronrelaties, epicversie, gedrag, acceptatiecriteria, eventuele UX, afhankelijkheden, `sequenceNumber`, leveringsstatus, eventuele dispatchreservering, externe referentie, `deliveredCommitSha` en actuele verificatiereferentie |
| backlogquery | alle uitvoerbare of reeds verzonden stories in volgorde | `StoryDetails` met status `TODO` of `IN_PROGRESS`, geordend op `sequenceNumber` |
| `PlanningWorkItemDetails` | read-only inzicht in de planningsqueue | type, bron, status, claim, resultaat en fout |
| `StoryDispatchReservationDetails` | tijdelijke read-only reservering voor de dispatcher | reserverings-ID, geldigheid en onveranderlijke momentopname van één uitvoerbare story; geen aparte productentiteit |
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

1. hervat Productplanning eerst een onafgeronde sessie of reeds door haar geclaimde
   `IN_PLANNING` epic van dit product;
2. vraagt zij alleen wanneer die niet bestaat beschikbare epics read-only op;
3. kiest zij op basis van productdoel, geldige besluiten, gebruikerswaarde en bestaand werk;
4. roept zij `claimEpicForPlanning(...)` op Productontwerp aan;
5. bevriest Productontwerp de exacte versie en zet de epic op `IN_PLANNING`;
6. maakt Productplanning een volledige, samenhangende set stories voor de hele epic;
7. ordent zij nieuwe stories tussen alle andere `TODO`-stories;
8. controleert zij vlak voor publicatie dat geen duurzame annuleringsmarker voor deze epic bestaat;
9. publiceert zij stories atomair en roept `markEpicActive(...)` aan.

De claim en processessie vormen één duurzame herstelrelatie. Eindigt een `AiTask` na haar technische
`maxAttempts` als `FAILED`, dan wordt de processessie zichtbaar `BLOCKED` met fout en
`retryAfter`; de epic blijft bewust `IN_PLANNING`. Een latere run voor hetzelfde product maakt na
de back-off een nieuwe taak voor dezelfde sessie, inputmomentopname en epicclaim. Hij kiest geen
andere epic en zet de geclaimde epic niet stilzwijgend terug naar `AVAILABLE`. Is de epic inmiddels
door de Stakeholder geannuleerd, dan annuleert Productplanning open AI-taken, sluit zij de sessie als
`CANCELLED` en publiceert zij niets.

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

- stabiel story-ID als canonieke UUID-string en een stabiel product-ID;
- `title`: één korte regel van enkele woorden voor backlog- en andere lijstweergaven;
- `summary`: maximaal twee korte zinnen die onder de titel de kern van het werk uitleggen;
- type `PRODUCT_STORY` of `BUGFIX`;
- epic-ID en bevroren epicversie voor een productstory en waar relevant voor een bugfix;
- bug-ID en bugversie voor een bugfix;
- klein zichtbaar gebruikersgedrag, waarde en duidelijke acceptatiecriteria;
- hoofd-, lege, laad-, fout- en uitzonderingssituaties;
- wanneer de story zichtbaar gedrag of interactie verandert: een zelfstandige momentopname van het
  relevante deel van het bevroren UX-ontwerp;
- waar UX van toepassing is: gebruikersflow, schermen, componenten, interacties, responsive gedrag
  en toegankelijkheid;
- benodigde tekstuele en binaire ontwerpassets met naam, MIME-type, grootte en hash;
- expliciete afhankelijkheden als story-ID's en bekende technische grenzen zonder implementatie
  voor te schrijven;
- storyversie, prioriteitsreden, `sequenceNumber` en status;
- eventueel extern Software Factory-ID, verzend- en oplevertijdstip en na oplevering de verplichte
  `deliveredCommitSha`.

Een story is pas uitvoerbaar als Software Factory haar zonder epicquery of Product Factory-call kan
bouwen. Tekst, Markdown, JSON en SVG blijven gewone UTF-8-tekst. Alleen binaire inhoud gebruikt bij
een JSON-only transport Base64; de database bewaart het oorspronkelijke object en metadata.

`title` en `summary` worden met de storyversie opgeslagen en veranderen niet doordat de frontend de
story toont. Ze helpen bij herkenning, maar vervangen nooit het volledige zelfstandige
Storycontract en mogen daarmee niet in tegenspraak zijn.

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
- `CANCELLED` — niet meer uitvoeren, met bron, tijdstip en reden; bijvoorbeeld doordat de
  Stakeholder een epic stopte of Software Factory het externe werk verwijderde of annuleerde.

`DONE` is hier de technische betekenis van *finished*: Software Factory heeft de story opgeleverd.
Een story heeft geen status `FAILED` of **mislukt**. Een functioneel ontoereikende oplevering blijft
`DONE` en krijgt een afgekeurde verificatie; een niet uitgevoerde externe story wordt `CANCELLED`.

Productplanning wijzigt alleen de volgorde van `TODO`-stories. Een `IN_PROGRESS` story wordt normaal
niet onderbroken.

Een storyafhankelijkheid is voldaan zodra de afhankelijke bronstory `DONE` is; kwaliteitsverificatie
is geen dispatchvoorwaarde. Daardoor mag de volgende technische slice al worden gebouwd terwijl de
eerdere oplevering nog wordt getest. `TODO` en `IN_PROGRESS` voldoen een afhankelijkheid niet. Een
`CANCELLED` dependency voldoet haar evenmin. Als zo'n annulering nog open afhankelijke
`TODO`-stories raakt, maakt Productplanning idempotent één `REPLAN_CANCELLED_DEPENDENCY`-workitem.
Een latere planningsrun vervangt of annuleert die stories voordat ze weer uitvoerbaar kunnen zijn.
Een `IN_PROGRESS` afhankelijke story wordt nooit met terugwerkende kracht onderbroken.

De Stakeholder kan geen losse machinegemaakte story annuleren of inhoudelijk wijzigen. De
Stakeholder kan de epic stoppen of `TODO`-werk via epicprioriteit laten herordenen. Alleen
Productplanning annuleert een `TODO`-story als gevolg van een geldige epicannulering of herplanning;
Software Factory kan haar eigen reeds verstuurde story extern `CANCELLED` melden.

`cancelStoriesForEpic(...)` bewaart altijd een interne duurzame annuleringsmarker voor epic-ID en
epicversie, ook als nog geen stories bestaan. In dezelfde transactie zet het alle niet-gereserveerde
`TODO`-stories op `CANCELLED`. Een latere of op AI wachtende planningssessie mag door de marker geen
stories meer voor die epic publiceren. Een `IN_PROGRESS` story loopt normaal af. Een alleen lokaal
gereserveerde story wacht op de hieronder beschreven externe aanwezigheidscontrole.

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

Vóór iedere externe retry zoekt de dispatcher eerst met dezelfde idempotentiesleutel bij Software
Factory. Bestaat het werk al, dan geldt het werkelijk als gestart en wordt de lokale koppeling
hersteld. Bestaat het aantoonbaar niet, dan roept de dispatcher
`revalidateDispatchReservation(...)` aan. Productplanning controleert in één transactie opnieuw de
epicmarker: zonder annulering blijft exact dezelfde reservering geldig; met annulering vervalt zij
en wordt de story `CANCELLED`. Is de externe toestand onbekend, dan volgt geen externe aanmaak en
geen lokale annulering. Hiermee blijft alleen het kleine racevenster tussen de laatste atomaire
herbevestiging en externe aanmaak over; een dagenoude reservering wint niet onbeperkt van een latere
epicannulering.

## Snelle opleverstatus en epicverificatie

Wanneer de dispatcher een Software Factory-oplevering ziet, roept hij
`markStoryAsDeveloped(...)` aan. Deze handler start geen agent en doet in milliseconden:

1. valideer idempotentiesleutel, externe referentie, verwachte storyversie en de verplichte
   `deliveredCommitSha`;
2. zet de story van `IN_PROGRESS` naar `DONE` en bewaar de oplevervelden en exacte commit;
3. vraag `requestStoryVerification(...)` aan, of `requestBugfixRetest(...)` bij een bugfix, met die
   commit als vereiste testversie;
4. laat de epic `ACTIVE`; oplevering alleen is nog geen toestemming voor epicverificatie.

Wanneer de dispatcher ziet dat Software Factory een externe story heeft verwijderd of bewust niet
uitvoert, roept hij `markStoryAsCancelled(...)` aan. Deze handler start evenmin een agent en:

1. valideert idempotentiesleutel, externe referentie en verwachte storyversie;
2. zet `IN_PROGRESS` op `CANCELLED` en bewaart bron, reden en tijdstip;
3. vraagt geen storyverificatie of bugfixhertest aan, omdat niets is opgeleverd;
4. controleert of de epic zelf niet `CANCELLED` is en alle overige niet-geannuleerde stories van de
   epic `DONE` en actueel geslaagd geverifieerd zijn;
5. brengt de epic dan via `markEpicReadyForVerification(...)` naar `VERIFYING` en vraagt een
   complete `requestEpicVerification(...)` aan, ook wanneer de geannuleerde story bij een nog open
   bug hoorde.

Bij een epic die de Stakeholder zelf `CANCELLED` heeft, stopt de handler na stap 3 en ontstaat geen
nieuwe epicverificatie. Anders beoordeelt de complete epiccontrole de feitelijke applicatie.
Daardoor kan een handmatig gemaakte oplossing worden geaccepteerd. Bestaat het ontbrekende of
foutieve gedrag nog, dan zet
Kwaliteitsbewaking de epic terug naar `ACTIVE` en vraagt zij gewoon nieuw bugfix- of
dekkingswerk aan.

Nadat Kwaliteitsbewaking een storyverificatie of bugfixhertest heeft gepubliceerd, roept zij
`recordStoryVerification(...)` aan. Deze snelle handler:

1. valideert storyversie, verificatie-ID, doelversie, uitkomst en idempotentiesleutel;
2. legt op de story alleen de actuele verificatiereferentie en uitkomst vast; de leveringsstatus
   blijft `DONE`;
3. laat de epic `ACTIVE` bij een afgekeurde of geblokkeerde controle;
4. controleert bij een geslaagde controle of alle niet-geannuleerde stories `DONE` zijn en iedere
   story of bugfix een geslaagde actuele verificatie heeft;
5. controleert via `findBugs(...)` dat geen open bug en geen `PENDING` of `IN_PROGRESS` bugfix- of
   dekkingsopdracht voor de epic bestaat; als een externe story `CANCELLED` is, mag in plaats
   daarvan de hierboven beschreven feitelijke herbeoordeling plaatsvinden;
6. roept alleen dan idempotent `markEpicReadyForVerification(...)` en daarna
   `requestEpicVerification(...)` aan.

Deze stappen zijn geen planning en hebben geen AI-agent of `PlanningWorkItem` nodig. De
qualitycommands starten evenmin agents; zij maken alleen `QualityWorkItem`s.

| Bevinding van Kwaliteitsbewaking | Snel command | Later intelligent planwerk |
|---|---|---|
| bug in afgesproken storygedrag | `requestBugfix(...)` | maak een bugfixstory |
| gedrag uit de bevroren oplossing, UX of acceptatiecriteria had nooit een story | `requestEpicGapPlanning(...)` | maak aanvullende productstories |
| epiccontrole geblokkeerd of productaanname niet geslaagd | geen | retry bij blokkade; alleen een nieuwe epic bij een latere expliciete productaanleiding |

Wanneer Productplanning een bugfixstory publiceert, bewaart zij in dezelfde transactie een
herstelbaar uitgaand effect voor `linkBugfixStory(bugId, storyId)`. De story wordt pas uitvoerbaar en
het `PlanningWorkItem` pas `DONE` nadat Kwaliteitsbewaking de koppeling idempotent heeft bevestigd.
Per bug mag maximaal één gekoppelde story tegelijk `TODO` of `IN_PROGRESS` zijn. Een eerdere
`DONE`- of `CANCELLED`-poging blijft historie en blokkeert een volgende gewone bugfixstory niet. Een
afgekeurde hertest houdt dezelfde bug `OPEN` en kan opnieuw een bugfixverzoek opleveren; de
storytypen blijven uitsluitend `PRODUCT_STORY` en `BUGFIX`.

## Grens met de Software Factory-dispatcher

De dispatcher leest status en open werk via Productplanning, reserveert de volgende story via
`reserveNextStoryForDispatch(...)` en meldt verzending en oplevering via
`markStoryAsDispatched(...)`, `markStoryAsDeveloped(...)` en `markStoryAsCancelled(...)`. De intelligente planner
kent geen extern Software Factory-protocol. Pakketvorming, externe statussynchronisatie,
`DeliveryAttempt`s, retry en idempotentie staan volledig in het
[dispatcherdocument](../software-factory-dispatcher.md).

Software Factory accepteert ieder contractgeldig `StoryDeliveryPackage`. Een weigering is daarom
een fout in de integratie of Software Factory en levert nooit planwerk of een aangepaste story op.
Ook een tijdelijke transport-, configuratie- of autorisatiefout start geen planningsagent.

## Fouten en idempotentie

- Maximaal één `runProcessSession(productId)` bewaakt per product publicatie en ordening;
  verschillende producten mogen parallel lopen.
- Een run gebruikt één vastgezette batch en inputmomentopname; nieuw queuewerk wacht.
- Workitems en moduleoverschrijdende commands zijn idempotent en herstelbaar.
- Een gekozen epicversie kan niet door een nieuwere ontwerpversie worden vervangen.
- Een technisch mislukte AI-taak laat sessie, workitems en epicclaim zichtbaar en herstelbaar achter;
  een volgende productrun hervat dezelfde claim.

## Eisen aan iedere implementatie

De MVP en iedere latere implementatie moeten garanderen dat:

- zij hetzelfde publieke `planning`-contract implementeert en andere capabilities alleen via
  `product-factory-api` gebruikt;
- iedere nieuwe `ProcessSession` de exacte `implementationId` en `implementationVersion` vastlegt;
- alleen `runProcessSession(productId)` voor Productplanning nieuwe AI-taken aanvraagt;
- maximaal één onafgeronde logische sessie per product bestaat, verschillende producten parallel
  mogen lopen en een wachtende sessie geen technische lock vasthoudt;
- ieder geclaimd workitem eindigt als `DONE`, `BLOCKED` of `FAILED`;
- een epic volledig wordt afgedekt door zelfstandig uitvoerbare stories;
- iedere story het volledige Storycontract volgt;
- epic- en bugbronversies exact vastliggen;
- `sequenceNumber`s productbreed consistent en uniek zijn;
- bugfixstories pas na de bevestigde natuurlijke koppeling `linkBugfixStory(bugId, storyId)`
  uitvoerbaar worden en per bug maximaal één story tegelijk `TODO` of `IN_PROGRESS` is;
- een annuleringsmarker latere storypublicatie verhindert en dispatchreservering de gelijktijdige
  grens met annuleren atomair maakt, ook bij een late retry na een externe storing;
- `DONE` iedere dependency vervult, `CANCELLED` haar blokkeert en dan automatisch gericht
  herplanningswerk voor nog open afhankelijke stories ontstaat;
- iedere opgeleverde story de exacte `deliveredCommitSha` bewaart en die commit meegaat naar het
  verificatiewerk;
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
