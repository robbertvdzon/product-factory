# Product Factory v2 — processen en entiteiten

Dit document beschrijft de grenzen tussen de procesmodules, hun publieke functies en de duurzame
entiteiten. De module die een entiteit bezit, is de enige die haar repository en tabellen mag
schrijven. Andere modules kunnen wel een betekenisvol command aan de eigenaar geven en de uitkomst
via een read-only query bekijken.

![Processen, eigenaren en gegevensstromen](product-factory-v2-proces-en-entiteiten.svg)

Het diagram gebruikt UML-achtige moduleblokken: bovenaan staat de publieke interface en onderaan
staan de eigen entiteiten. De scheduler is niet als module afgebeeld; `«scheduled»` markeert zijn
aanroeppunten. De frontend gebruikt dezelfde commands en queries en staat daarom evenmin in het
diagram. De Stakeholder blijft zichtbaar als externe actor bij Product en overleg.

## Ontwerpregels

- Iedere duurzame entiteit heeft precies één schrijvende module.
- Iedere intelligente procesmodule heeft `runProcessSession()` als enige agentgestuurde functie.
- Een procesmodule mag daarnaast kleine deterministische command- en queryfuncties aanbieden.
- Een command benoemt een geldige domeinovergang, bijvoorbeeld `claimEpicForPlanning(...)`; een
  algemene setter als `setEpicStatus(...)` is niet toegestaan.
- De eigenaar controleert bevoegdheid, verwachte versie, huidige status en idempotentie en schrijft
  de wijziging in zijn eigen transactie.
- Een query retourneert een read-only DTO zoals `EpicDetails`. Zo'n DTO is geen tweede entiteit en
  kan niet worden teruggeschreven.
- Modules krijgen nooit elkaars repository of interne JPA-entiteit.
- Historisch bewijs blijft apart waar historie betekenis heeft: besluiten, verificaties,
  afleverpogingen en processessies worden niet in een actueel statusveld weggepoetst.
- De frontend leest via dezelfde query-API's en schrijft alleen via commands van de eigenaar.

## De vier uitvoerende onderdelen

| Onderdeel | Geplande ingang | Eigen entiteiten | Publieke commands naast de run | Schrijft nooit |
|---|---|---|---|---|
| Productontwerp | `runProcessSession()` | `Epic`, productrichting en intern ontwerpgeheugen | epic claimen, activeren, laten controleren, verificatie-uitkomst registreren of stoppen | stories, bugs, verificaties of signalen |
| Productplanning | `runProcessSession()` | `Story` en interne planningsverzoeken | story verzonden/ontwikkeld markeren, dispatchfout registreren, bugfix of aanvullend werk aanvragen | epicinhoud, bugs of verificatiebewijs |
| Kwaliteitsbewaking | `runProcessSession()` | `Bug` en `Verification` | een bugfixstory aan een bug koppelen | epics, stories, signalen of backlogvolgorde |
| Software Factory-dispatcher | `runDispatchSession()` | geen productentiteiten; alleen `DeliveryAttempt` binnen Productplanning | gebruikt storycommands van Productplanning | story-inhoud, status rechtstreeks, epicselectie of prioriteit |

De scheduler geeft geen product-ID of inhoudelijke opdracht mee. De `run...Session()` kiest zelf
hooguit één planbare sessie en eindigt als succesvolle no-op wanneer niets hoeft te gebeuren. De
publieke commands starten nooit agents.

## Publieke module-API's

De precieze Java-signatures kunnen bij implementatie worden verfijnd, maar de functionele grens is:

| Eigenaar | Commands | Read-only queries |
|---|---|---|
| product-/overlegmodule | `submitUserSignal`, `recordSignalInvestigation`, `linkSignalToEpic`, `recordStakeholderDirection` | `getUserSignal`, `findOpenUserSignals`, `getStakeholder`, `getProductAssignment`, `getTestableProduct` |
| Productontwerp | `claimEpicForPlanning`, `markEpicActive`, `markEpicReadyForVerification`, `recordEpicVerification`, `stopEpic` | `getEpic`, `findAvailableEpics`, `findActiveEpic`, `findEpicsAwaitingVerification` |
| Productplanning | `markStoryAsDispatched`, `markStoryAsDeveloped`, `recordDispatchFailure`, `requestBugfix`, `requestCompletionWork` | `getStory`, `getBacklog`, `getBacklogSupply` |
| Kwaliteitsbewaking | `linkBugfixStory` | `getBug`, `findVerifications`, `getQualityOverview` |
| Besluitenregister | `recordDecision`, `withdrawDecision` | `getDecision`, `findDecisions` |

Een command mag een ID, verwachte versie, bron-ID, actor en idempotentiesleutel aannemen, maar geeft
geen vrije velden door waarmee de aanroeper de state machine kan omzeilen.

## De Stakeholder

De Stakeholder is een actor en duurzame productrelatie, geen proces. De frontend vertaalt haar
invoer naar een command op de eigenaar.

| Levering door de Stakeholder | Vastlegging | Betekenis |
|---|---|---|
| identiteit, rol, contactwijze en beslissingsmandaat | `Stakeholder` | bepaalt wie bevoegd richting of antwoorden kan geven |
| productdoel en harde grenzen | `ProductAssignment` | vormt het vaste kader voor de processen |
| bindende richting, correctie of stopbesluit | `StakeholderDirection` en eventueel `DecisionRecord` | is binnen het mandaat verplichte context |
| feedback, observatie, probleem, kans, risico of kwaliteitszorg | `UserSignal`, eventueel categorie `QUALITY_CONCERN` | is onbeoordeelde input totdat een proces haar onderzoekt |
| testomgevingen en toegestane toegang | `TestableProductConfiguration` | maakt gecontroleerd testen mogelijk; secrets staan in een beveiligde voorziening |

De Stakeholder schrijft geen epic, story, bug, verificatie of backlogpositie.

## Duurzame entiteiten en eigenaarschap

In de tabel betekent **schrijven** altijd via de interne repository van de eigenaar. Genoemde andere
modules gebruiken uitsluitend het publieke command; zij schrijven de entiteit nooit rechtstreeks.

| Entiteit | Aanmaker en eigenaar | Wie mag een wijziging aanvragen | Lezers | Betekenis en belangrijke status |
|---|---|---|---|---|
| `Product` | productmodule | bevoegde productbediening | alle processen en frontend | productidentiteit en configuratie |
| `Stakeholder` | product-/overlegmodule | bevoegde Stakeholder of beheerder | alle processen en frontend | identiteit, contact en mandaat |
| `ProductAssignment` | productmodule | bevoegde Stakeholder | Productontwerp, Productplanning, Kwaliteitsbewaking en frontend | doelgroep, productdoel, harde grenzen en publieke Git-URL van het product |
| `StakeholderDirection` | product-/overlegmodule | bevoegde Stakeholder | alle processen en frontend | bindende richting met toepassingsgebied en geldigheid |
| `TestableProductConfiguration` | productmodule | bevoegde Stakeholder of beheerder | Kwaliteitsbewaking | omgeving, routes, accounts, databereik en testgrenzen |
| `UserSignal` | productmodule | gebruiker/Stakeholder mag indienen; Productontwerp en Kwaliteitsbewaking mogen een verwerkingsuitkomst registreren | Productontwerp, Kwaliteitsbewaking, Stakeholder en frontend | onveranderlijke broninhoud plus status `NEW`, `IN_REVIEW`, `PROCESSED`, `NEEDS_EVIDENCE`, `DUPLICATE`, `OUT_OF_SCOPE` of `DISMISSED`, en links naar verificatie, epic, bug of besluit |
| `DirectionSnapshot` | Productontwerp | niemand buiten Productontwerp | Productontwerp, Stakeholder en frontend | geversioneerde verre productrichting; geen uitvoerbaar werk |
| `Epic` | Productontwerp | Productplanning mag claimen/activeren/controleren; Kwaliteitsbewaking mag een verificatie-uitkomst registreren via Productontwerp | Productontwerp, Productplanning, Kwaliteitsbewaking en frontend | complete gebruikersverbetering met versie, scope, UX en status `AVAILABLE`, `IN_PLANNING`, `ACTIVE`, `VERIFYING`, `COMPLETED`, `NOT_SUCCESSFUL`, `STOPPED`, `SUPERSEDED` of `WITHDRAWN` |
| `Story` | Productplanning | dispatcher mag dispatch/oplevering melden; Kwaliteitsbewaking mag alleen nieuw planwerk aanvragen | Productplanning, dispatcher, Kwaliteitsbewaking en frontend | zelfstandige productstory of bugfix met UX, `sequenceNumber`, status `TODO`, `IN_PROGRESS` of `DONE`, externe referentie en oplevertijdstip |
| `Bug` | Kwaliteitsbewaking | Productplanning mag een bugfixstory koppelen | Kwaliteitsbewaking, Productplanning en frontend | reproduceerbare afwijking, bewijs, ernst en herstelstatus |
| `Verification` | Kwaliteitsbewaking | niemand; na publicatie onveranderlijk | Kwaliteitsbewaking, Productontwerp, Productplanning en frontend | controle van doeltype `STORY`, `EPIC` of `USER_SIGNAL`, met exacte doelversie, uitkomst, bewijs, dekkingsgaten en vervolgkoppelingen |
| `DecisionRecord` | Besluitenregister | bevoegde bronmodule mag registreren, intrekken of vervangen | alle processen, Stakeholder en frontend | betekenisvol besluit met geldigheid, onderbouwing en vervangingsrelaties |
| `ProcessSession` | het intelligente proces van die sessie | niemand buiten de eigenaar | scheduler, operations en frontend | inputversies, agentuitvoering, publicaties, eindstatus en blokkade |
| `DeliveryAttempt` | dispatcher binnen Productplanning | dispatcher via interne application service | Productplanning, operations en frontend | onveranderlijke poging, response, fout en retryhistorie; actuele leverstatus staat op `Story` |

Interne objecten zoals `LearningResult`, drafts, agentruns, onderzoeksdossiers en testobservaties
steken de modulegrens niet over, behalve als bronverwijzing vanuit een publieke entiteit.

## Read-only en transportcontracten

Deze contracten steken de modulegrens over, maar zijn geen entiteit en hebben geen tabel of
schrijver. De producent bouwt bij een query of overdracht een momentopname uit zijn eigen gegevens.

| Contract | Producent | Lezers/ontvangers | Betekenis |
|---|---|---|---|
| `StakeholderDetails` | product-/overlegmodule uit `Stakeholder` | alle processen en frontend | identiteit en mandaat |
| `ProductAssignmentDetails` | productmodule uit `ProductAssignment` | Productontwerp, Productplanning, Kwaliteitsbewaking en frontend | productdoel, grenzen en publieke Git-URL |
| `StakeholderDirectionDetails` | product-/overlegmodule uit `StakeholderDirection` | alle processen en frontend | geldende bindende richting |
| `TestableProductDetails` | productmodule uit `TestableProductConfiguration` | Kwaliteitsbewaking | veilige testconfiguratie zonder secrets |
| `UserSignalDetails` | productmodule uit `UserSignal` | Productontwerp, Kwaliteitsbewaking, Stakeholder en frontend | bronmelding, actuele status, uitkomst en koppelingen |
| `EpicDetails` | Productontwerp uit `Epic` | Productplanning, Kwaliteitsbewaking en frontend | inhoud, UX, versie en status; read-only |
| `StoryDetails` | Productplanning uit `Story` | dispatcher, Kwaliteitsbewaking en frontend | story-inhoud, volgorde, status en oplevervelden; read-only |
| `BugDetails` | Kwaliteitsbewaking uit `Bug` | Productplanning en frontend | bug, bewijs, ernst en herstelstatus |
| `VerificationDetails` | Kwaliteitsbewaking uit `Verification` | Productontwerp, Productplanning en frontend | doel, uitkomst, bewijs en dekkingsgaten |
| `BacklogSupply` | Productplanning-query uit `Story` | Productontwerp, Productplanning, schedulerbediening en frontend | berekende aantallen, lage grens, streefpeil en `aanvullingNodig` |
| `QualityOverview` | Kwaliteitsbewaking-query uit bugs en verificaties | Productontwerp, Stakeholder en frontend | berekend actueel kwaliteitsbeeld |
| `DecisionDetails` | Besluitenregister uit `DecisionRecord` | alle processen, Stakeholder en frontend | actueel of historisch besluit met geldigheid |
| `ProcessSessionDetails` | betreffende procesmodule uit `ProcessSession` | scheduler, operations en frontend | operationele sessiestatus en historie |
| `SoftwareFactoryWork` | dispatcheradapter uit externe Software Factory-status | dispatcher | tijdelijk integratieantwoord; niet duurzaam in Product Factory |
| `StoryDeliveryPackage` | dispatcher uit één exacte `StoryDetails` | Software Factory | volledige, onveranderlijke story met UX, assets, hashes en idempotentiesleutel |

## Publieke productrepository als leesbron

`ProductAssignment.gitUrl` wijst naar de publiek leesbare GitHub-repository van het product. Dit is
geen Product Factory-entiteit en er komt geen aparte workspace of Git-module. Productontwerp,
Productplanning en Kwaliteitsbewaking mogen de URL bij een inhoudelijke sessie uitchecken en code,
tests en documentatie lezen. Zij hebben geen commit- of pushfunctie. De gevonden commit-SHA kan als
bronverwijzing worden vastgelegd; de repository zelf wordt niet naar de productdatabase gekopieerd.

De drie processen gebruiken dezelfde bron verschillend: Productontwerp begrijpt de huidige
productwerking, Productplanning herkent afhankelijkheden en Kwaliteitsbewaking selecteert risico's
en tests. Voor Kwaliteitsbewaking blijft gedeployed gedrag plus testbewijs leidend. De Software
Factory-story blijft zelfstandig en verwijst niet naar Git als enige drager van product- of UX-keuzes.

## Wat uit het oude model verdwijnt

| Oude constructie | Nieuwe vorm |
|---|---|
| `EpicDefinitionView` + `EpicProgressView` | één `Epic`; `EpicDetails` is alleen het read-only query-DTO |
| `UserSignalView` + `UserSignalDispositionView` | één `UserSignal` met onveranderlijke melding, actuele status en resultaatlinks |
| `StoryView` als publieke entiteit | één `Story`; `StoryDetails` is alleen het query-DTO |
| `StoryVerificationView`, `EpicVerificationView`, `SignalInvestigationResultView` | één `Verification` met een doeltype |
| `EpicCompletionGapView` | gestructureerd dekkingsgat binnen een epicverificatie plus `requestCompletionWork(...)` |
| `QualitySignalView` | nieuw `UserSignal` met categorie `QUALITY_PATTERN` en links naar bewijs |
| `DeliveryResultView` | actuele velden op `Story`; historie in `DeliveryAttempt` |
| `SoftwareFactoryWorkView` | tijdelijk antwoord van de externe adapter; geen duurzame Product Factory-entiteit |
| `BacklogSupplyView` en `QualityOverviewView` | berekende queryresultaten `BacklogSupply` en `QualityOverview` |

`StoryDeliveryPackage`, `EpicDetails`, `StoryDetails`, `BugDetails`, `VerificationDetails` en andere
`...Details`-objecten zijn overdrachtsobjecten. Ze hebben geen eigen tabel en geen schrijver.

## Backlog en levering

De backlog is geen entiteit. Zij is de query op alle stories die nog niet `DONE` zijn:

```sql
select * from story
where product_id = :productId and status <> 'DONE'
order by sequence_number
```

Wanneer Software Factory geen open werk heeft, vraagt de dispatcher de eerste `TODO`-story op. Na
succesvolle externe creatie roept hij `markStoryAsDispatched(...)` aan; Productplanning controleert
de storyversie en zet `TODO` naar `IN_PROGRESS`. Als Software Factory oplevering meldt, gebruikt de
dispatcher `markStoryAsDeveloped(...)`; Productplanning zet `IN_PROGRESS` naar `DONE` en bewaart
externe referentie, locatie en tijdstip. Iedere poging wordt als `DeliveryAttempt` vastgelegd.

`DONE` betekent ontwikkeld en opgeleverd, niet door Kwaliteitsbewaking goedgekeurd. Een afkeuring
laat de oorspronkelijke story `DONE`; Kwaliteitsbewaking maakt een `Bug` en vraagt via
`requestBugfix(...)` een nieuwe bugfixstory aan.

## Belangrijke levenscycli

1. Productontwerp publiceert een complete epic met status `AVAILABLE`.
2. Productplanning kiest een versie en roept `claimEpicForPlanning(...)` aan. Productontwerp zet de
   epic atomair op `IN_PLANNING`; inhoud en UX van die versie zijn daarna bevroren.
3. Productplanning maakt en ordent stories en roept `markEpicActive(...)` aan.
4. De dispatcher verwerkt steeds de eerste `TODO`-story via storycommands van Productplanning.
5. Kwaliteitsbewaking maakt onveranderlijke verificaties. Voor een bug of ontbrekende dekking vraagt
   zij via Productplanning nieuw planwerk aan.
6. Als alle bekende epicstories `DONE` zijn, zet Productplanning de epic via
   `markEpicReadyForVerification(...)` op `VERIFYING`. Dit start geen testwerk. Kwaliteitsbewaking
   vindt de epic tijdens `runProcessSession()`, registreert de verificatie en geeft haar ID en uitkomst via
   `recordEpicVerification(...)` aan Productontwerp.
7. Productontwerp is de enige schrijver van de uiteindelijke epicstatus.
8. Een signaalonderzoek wordt een `Verification`; via `recordSignalInvestigation(...)` actualiseert
   de productmodule status en koppelingen op hetzelfde `UserSignal`.
9. Een ingetrokken of vervangen besluit behoudt zijn inhoud en krijgt een einddatum en relatie naar
   de opvolger.

## Scheduler en frontend

De scheduler roept alleen `runProcessSession()` en `runDispatchSession()` aan. Hij schrijft geen
productstatus en gebruikt `ProcessSession` uitsluitend voor monitoring en retry.

De frontend toont Product, Stakeholder, signalen met actuele status, droombeeld, epics met status en
UX, stories en berekende backlog, bugs, verificaties, kwaliteitsoverzicht, besluiten,
processessies en afleverpogingen. Een gebruikersactie wordt een command op de eigenaar; de frontend
krijgt geen repositorytoegang. **Inbox** is alleen de schermnaam voor signalen en hun afhandeling.

## Technische vertaling naar Spring Modulith

- Iedere eigenaar implementeert een application port met alleen de genoemde commands en queries.
- De stabiele owner-specifieke portinterfaces, command-DTO's en read-only `...Details`-DTO's staan in
  het neutrale `processcontracts`; implementaties en productlogica blijven in de eigenaarsmodule.
- Waar geen direct antwoord nodig is, mag dezelfde overgang via een duurzaam application event lopen.
- Iedere eigenaar beheert eigen aggregates, repositories en transacties, ook in één fysieke database.
- Geen procesmodule importeert de implementatie, interne packages of JPA-entiteiten van een andere
  procesmodule; daardoor ontstaan geen cyclische Spring Modulith-codeafhankelijkheden.
- Iedere commandhandler controleert actor/bronmodule, verwachte versie, huidige status en
  idempotentiesleutel.
- Overgangen binnen één aggregate zijn atomair. Een keten over modules is idempotent en herstelbaar;
  hij doet niet alsof één database-transactie meerdere module-aggregates bezit.
- Tekst, Markdown, JSON en SVG blijven tekst in `StoryDeliveryPackage`; binaire assets krijgen een
  begrensd attachment met MIME-type, grootte en hash en mogen alleen voor JSON-transport Base64 zijn.
- De Software Factory bewaart bij acceptatie het complete leveringspakket in haar eigen storystorage.

## Gerelateerde documenten

- [Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md)
- [Besluitenregister](product-factory-v2-besluitenregister.md)
- [Productontwerp](product-factory-v2-productontwerp.md)
- [Productplanning](product-factory-v2-productplanning.md)
- [Kwaliteitsbewaking](product-factory-v2-kwaliteitsbewaking.md)
