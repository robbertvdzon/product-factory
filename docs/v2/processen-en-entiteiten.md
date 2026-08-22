# Product Factory v2 — processen en entiteiten

Dit document beschrijft de modulegrenzen, publieke functies en duurzame entiteiten. De module die
een entiteit bezit, is de enige die haar repository en tabellen mag schrijven. Andere modules
kunnen een betekenisvol command geven of een read-only DTO opvragen.

![Processen, eigenaren en gegevensstromen](processen-en-entiteiten.svg)

Het diagram gebruikt UML-achtige moduleblokken: bovenaan staan publieke functies en onderaan de
eigen entiteiten. De scheduler en frontend zijn geen procesmodules en staan daarom niet als blok in
het diagram. `«scheduled/manual»` markeert de aanroeppunten. De Stakeholder is een externe actor.

## Ontwerpregels

- Iedere duurzame entiteit heeft precies één schrijvende module.
- Iedere intelligente procesmodule heeft `runProcessSession()` als enige functie die agents start.
- Scheduler en bevoegde UI/REST-bediening mogen een run starten; per procesmodule draait maximaal
  één run tegelijk. Een botsende handmatige aanroep krijgt een fout en een schedulerbotsing wordt
  overgeslagen en geregistreerd.
- Een procesmodule mag daarnaast snelle, deterministische commands en read-only queries aanbieden.
- Een queuecommand start geen agents; het voegt alleen idempotent een werkitem bij de ontvangende
  module toe. Alleen een latere `runProcessSession()` claimt dat werk.
- Een domeincommand benoemt een geldige overgang; algemene setters zijn niet toegestaan.
- De eigenaar controleert bevoegdheid, bronversie, huidige status en idempotentie.
- Modules krijgen nooit elkaars repository of interne JPA-entiteit.
- De frontend gebruikt dezelfde publieke API's en krijgt geen repositorytoegang.

## De vier uitvoerende onderdelen

| Onderdeel | Uitvoerende ingang | Eigen publieke entiteiten | Deterministische verantwoordelijkheid |
|---|---|---|---|
| Productontwerp | `runProcessSession()` | `Epic`, `DirectionSnapshot` | epicstatuscommands uitvoeren en na publicatie planning aanvragen |
| Productplanning | `runProcessSession()` | `PlanningWorkItem`, `Story` | planverzoeken queueën, storylevering verwerken en zo nodig epicverificatie aanvragen |
| Kwaliteitsbewaking | `runProcessSession()` | `QualityWorkItem`, `Bug`, `Verification` | testverzoeken queueën en gevalideerde uitkomsten doorgeven |
| Software Factory-dispatcher | `runDispatchSession()` | geen productentiteit; `DeliveryAttempt` binnen Productplanning | externe status synchroniseren en steeds de eerste uitvoerbare `TODO`-story versturen |

De dispatcher gebruikt geen agents. Een lege backlog of lege processqueue is een geldige no-op.

## Publieke module-API's

| Eigenaar | Commands | Read-only queries |
|---|---|---|
| product-/overlegmodule | `submitUserSignal`, `markUserSignalInReview`, `recordSignalInvestigation`, `linkSignalToEpic`, `recordStakeholderDirection` | `getUserSignal`, `findOpenUserSignals`, `getStakeholder`, `getProductAssignment`, `getTestableProduct` |
| Productontwerp | `claimEpicForPlanning`, `markEpicActive`, `markEpicReadyForVerification`, `recordEpicVerification`, `stopEpic` | `getEpic`, `findAvailableEpics`, `findActiveEpics` |
| Productplanning | `requestEpicPlanning`, `requestBugfix`, `requestEpicGapPlanning`, `requestEpicReprioritization`, `requestManualReplan`, `markStoryAsDispatched`, `markStoryAsDeveloped`, `recordDispatchFailure` | `getStory`, `getBacklog`, `findPlanningWorkItems` |
| Kwaliteitsbewaking | `requestStoryVerification`, `requestEpicVerification`, `requestBugfixRetest`, `requestSignalInvestigation`, `linkBugfixStory` | `getBug`, `findVerifications`, `getQualityOverview`, `findQualityWorkItems` |
| Besluitenregister | `recordDecision`, `withdrawDecision` | `getDecision`, `findDecisions` |

Een command mag ID's, verwachte versies, bron, actor en idempotentiesleutel aannemen, maar geen
vrije velden waarmee de aanroeper de state machine kan omzeilen.

## De Stakeholder

De Stakeholder is een actor en duurzame productrelatie, geen proces. De product-/overlegmodule
vertaalt invoer uit de UI naar commands op de juiste eigenaar.

| Levering door de Stakeholder | Vastlegging | Doorwerking |
|---|---|---|
| identiteit, rol, contactwijze en mandaat | `Stakeholder` | bepaalt wie richting en prioriteit mag geven |
| productdoel en harde grenzen | `ProductAssignment` | verplichte context voor alle processen |
| bindende richting, correctie of stopbesluit | `StakeholderDirection` en eventueel `DecisionRecord` | processen volgen de geldende richting |
| handmatige hoge prioriteit voor een epic | `StakeholderDirection` plus `DecisionRecord` | product-/overlegmodule roept `requestEpicReprioritization(...)` aan |
| feedback, probleem, kans, risico of kwaliteitszorg | `UserSignal` | ontwerp of kwaliteit onderzoekt dit later; een kwaliteitszorg kan een `QualityWorkItem` opleveren |
| testomgevingen en toegestane toegang | `TestableProductConfiguration` | maakt gecontroleerd testen mogelijk |

De Stakeholder schrijft geen epic, story, bug, verificatie of backlogpositie.

## Duurzame entiteiten en eigenaarschap

**Aanvragen** betekent altijd: een publiek command aan de eigenaar geven. De aanvrager schrijft
nooit rechtstreeks in de tabel.

| Entiteit | Aanmaker en enige schrijver | Wie mag een wijziging aanvragen | Lezers | Betekenis en status |
|---|---|---|---|---|
| `Product` | productmodule | productbediening | alle processen en frontend | productidentiteit en configuratie |
| `Stakeholder` | product-/overlegmodule | Stakeholder of beheerder | alle processen en frontend | identiteit, contact en mandaat |
| `ProductAssignment` | productmodule | Stakeholder | alle processen en frontend | doelgroep, doel, grenzen en publieke Git-URL |
| `StakeholderDirection` | product-/overlegmodule | Stakeholder | alle processen en frontend | bindende richting, scope, prioriteit en geldigheid |
| `TestableProductConfiguration` | productmodule | Stakeholder of beheerder | Kwaliteitsbewaking | omgeving, routes, accounts, data- en testgrenzen |
| `UserSignal` | productmodule | gebruiker/Stakeholder dient in; ontwerp of kwaliteit registreert een uitkomst via command | Productontwerp, Kwaliteitsbewaking, Stakeholder en frontend | onveranderlijke melding plus actuele verwerkingsstatus en resultaatlinks |
| `DirectionSnapshot` | Productontwerp | niemand buiten Productontwerp | Productontwerp, Stakeholder en frontend | geversioneerde verre productrichting; geen uitvoerbaar werk |
| `Epic` | Productontwerp | Productplanning vraagt planning/statusovergangen; Kwaliteitsbewaking registreert uitkomst | ontwerp, planning, kwaliteit en frontend | complete verbetering met scope, UX, versie en status `AVAILABLE`, `IN_PLANNING`, `ACTIVE`, `VERIFYING`, `COMPLETED`, `NOT_SUCCESSFUL`, `STOPPED`, `SUPERSEDED` of `WITHDRAWN` |
| `PlanningWorkItem` | Productplanning | Productontwerp, Kwaliteitsbewaking, product-/overlegmodule of bevoegde bediening | Productplanning, operations en frontend | duurzame planningsqueue; type `PLAN_EPIC`, `PLAN_BUGFIX`, `PLAN_EPIC_GAP`, `REPRIORITIZE_EPIC` of `MANUAL_REPLAN`; status `PENDING`, `IN_PROGRESS`, `DONE`, `BLOCKED` of `FAILED` |
| `Story` | Productplanning | dispatcher meldt verzending/oplevering; niemand schrijft inhoud of volgorde buiten planning | planning, dispatcher, kwaliteit en frontend | complete productstory of bugfix met UX, productbreed `sequenceNumber` en status `TODO`, `IN_PROGRESS` of `DONE` |
| `QualityWorkItem` | Kwaliteitsbewaking | Productplanning of product-/overlegmodule | Kwaliteitsbewaking, operations en frontend | duurzame testqueue; type `VERIFY_STORY`, `VERIFY_EPIC`, `RETEST_BUGFIX` of `INVESTIGATE_USER_SIGNAL`; dezelfde vijf werkstatussen |
| `Bug` | Kwaliteitsbewaking | Productplanning mag een bugfixstory koppelen | kwaliteit, planning en frontend | reproduceerbare afwijking, bewijs, ernst en herstelstatus |
| `Verification` | Kwaliteitsbewaking | niemand; na publicatie onveranderlijk | kwaliteit, ontwerp, planning en frontend | controle van `STORY`, `EPIC` of `USER_SIGNAL`, met doelversie, uitkomst, bewijs en eventuele dekkingsgaten |
| `DecisionRecord` | Besluitenregister | bevoegde bronmodule registreert, trekt in of vervangt | alle processen, Stakeholder en frontend | betekenisvol besluit met begin- en optionele einddatum en opvolgingsrelatie |
| `ProcessSession` | betreffende intelligente procesmodule | niemand buiten eigenaar | operations en frontend | geclaimde batch, inputversies, agentruns, publicaties, eindstatus en blokkade |
| `DeliveryAttempt` | dispatcher binnen Productplanning | dispatcher via interne service | planning, operations en frontend | onveranderlijke externe poging, response, fout en retryhistorie |

Interne objecten zoals `LearningResult`, drafts, agentruns, onderzoeksdossiers en testobservaties
steken de modulegrens niet over. Een betekenisvolle conclusie kan wel een `DecisionRecord` worden.

## Read-only en transportcontracten

Deze contracten zijn momentopnamen en hebben geen eigen tabel of schrijver.

| Contract | Producent | Lezers/ontvangers | Betekenis |
|---|---|---|---|
| `StakeholderDetails` | product-/overlegmodule | alle processen en frontend | identiteit en mandaat |
| `ProductAssignmentDetails` | productmodule | alle processen en frontend | productdoel, grenzen en publieke Git-URL |
| `StakeholderDirectionDetails` | product-/overlegmodule | alle processen en frontend | geldende bindende richting en prioriteit |
| `TestableProductDetails` | productmodule | Kwaliteitsbewaking | veilige testconfiguratie zonder secrets |
| `UserSignalDetails` | productmodule | Productontwerp, Kwaliteitsbewaking, Stakeholder en frontend | bronmelding, status, uitkomst en koppelingen |
| `EpicDetails` | Productontwerp | Productplanning, Kwaliteitsbewaking en frontend | epicinhoud, UX, versie en status; read-only |
| `StoryDetails` | Productplanning | dispatcher, Kwaliteitsbewaking en frontend | storyinhoud, UX, volgorde en leveringsstatus; read-only |
| backlogquery | Productplanning uit `Story` | dispatcher en frontend | alle stories die niet `DONE` zijn, geordend op `sequenceNumber` |
| `PlanningWorkItemDetails` | Productplanning uit `PlanningWorkItem` | operations en frontend | planningsopdracht, bron, status, claim, resultaat en fout |
| `BugDetails` | Kwaliteitsbewaking | Productplanning en frontend | bug, bewijs, ernst en herstelstatus |
| `VerificationDetails` | Kwaliteitsbewaking | Productontwerp, Productplanning en frontend | doel, uitkomst, bewijs en dekkingsgaten |
| `QualityOverview` | Kwaliteitsbewaking uit bugs en verificaties | Productontwerp, Stakeholder en frontend | berekend actueel kwaliteitsbeeld |
| `QualityWorkItemDetails` | Kwaliteitsbewaking uit `QualityWorkItem` | operations en frontend | testopdracht, doelversie, status, claim, resultaat en fout |
| `DecisionDetails` | Besluitenregister | alle processen, Stakeholder en frontend | actueel of historisch besluit met geldigheid |
| `ProcessSessionDetails` | betreffende procesmodule | operations en frontend | operationele sessiestatus en historie |
| `SoftwareFactoryWork` | externe adapter | dispatcher | tijdelijk extern integratieantwoord |
| `StoryDeliveryPackage` | dispatcher uit één `StoryDetails` | Software Factory | volledige, onveranderlijke story met UX, assets, hashes en idempotentiesleutel |

## Publieke productrepository als leesbron

`ProductAssignment.gitUrl` wijst naar de publiek leesbare GitHub-repository. Er is geen aparte
workspace of Git-module. Productontwerp, Productplanning en Kwaliteitsbewaking mogen de repository
bij een inhoudelijke sessie uitchecken en code, tests en documentatie lezen. Zij committen en pushen
niet. De Software Factory-story blijft zelfstandig en gebruikt Git nooit als enige drager van
product- of UX-keuzes.

## Backlog, queues en levering

De backlog is geen entiteit maar deze query:

```sql
select * from story
where product_id = :productId and status <> 'DONE'
order by sequence_number
```

Er is geen voorraadgrens en leeg is geldig. De planner verwerkt een hele epic in zo veel stories als
nodig. Meerdere epics mogen tegelijk actief zijn en hun `TODO`-stories mogen productbreed door elkaar
worden geordend. Een Stakeholder kan een andere epic handmatig voorrang geven; een `IN_PROGRESS`
story loopt normaal door.

De twee procesqueues zijn wel duurzame entiteiten:

- `PlanningWorkItem` vertelt Productplanning welk inhoudelijk planwerk een latere run moet doen;
- `QualityWorkItem` vertelt Kwaliteitsbewaking welk gericht testwerk een latere run moet doen.

Een queuecommand retourneert zodra het idempotente record is opgeslagen. Het start geen agents.
Iedere run claimt een stabiele batch; nieuw werk wacht tot de volgende run.

## Belangrijkste levenscyclus

1. Productontwerp publiceert een complete `AVAILABLE` epic en roept `requestEpicPlanning(...)` aan.
2. Een planningsrun claimt het `PLAN_EPIC`-workitem, bevriest de epic via
   `claimEpicForPlanning(...)`, maakt alle benodigde stories en zet de epic `ACTIVE`.
3. De dispatcher verstuurt telkens de eerste uitvoerbare `TODO`-story en meldt status via
   `markStoryAsDispatched(...)` en `markStoryAsDeveloped(...)`.
4. `markStoryAsDeveloped(...)` zet snel `IN_PROGRESS` naar `DONE`, queue't storyverificatie of een
   bugfix-hertest en controleert zonder agent of voor die epic alle stories klaar zijn.
5. Zo ja, roept Productplanning `markEpicReadyForVerification(...)` en daarna
   `requestEpicVerification(...)` aan. Dit laatste maakt alleen een `VERIFY_EPIC`-workitem.
6. Een latere kwaliteitsrun test de epic, bewaart een onveranderlijke `Verification` en roept
   `recordEpicVerification(...)` op Productontwerp aan.
7. Alleen bij nieuw ontwikkelwerk roept Kwaliteitsbewaking `requestBugfix(...)` of
   `requestEpicGapPlanning(...)` aan; deze commands zetten werk in de planningsqueue.
8. Productontwerp blijft enige schrijver van de uiteindelijke epicstatus. Iedere epic doorloopt dit
   onafhankelijk van andere actieve epics.

## Technische vertaling naar Spring Modulith

- Iedere eigenaar implementeert een application port met alleen de genoemde commands en queries.
- Stabiele portinterfaces, command-DTO's en read-only DTO's staan in `processcontracts`; logica en
  JPA-entiteiten blijven in de eigenaarsmodule.
- Iedere eigenaar beheert eigen aggregates, repositories en transacties, ook in één fysieke database.
- Queue-inserts en commandketens over modules zijn idempotent en herstelbaar; ze doen niet alsof één
  transactie meerdere module-aggregates bezit.
- Een unieke actieve-run-constraint per procesmodule voorkomt gelijktijdige agentsessies.
- Tekst, Markdown, JSON en SVG blijven tekst in `StoryDeliveryPackage`; binaire assets krijgen
  begrensde attachments met MIME-type, grootte en hash en mogen alleen voor transport Base64 zijn.

## Gerelateerde documenten

- [Product Factory v2 — eerste opzet](eerste-opzet.md)
- [Besluitenregister](besluitenregister.md)
- [Productontwerp](productontwerp.md)
- [Productplanning](productplanning.md)
- [Kwaliteitsbewaking](kwaliteitsbewaking.md)
