# Product Factory v2 — Kwaliteitsbewaking-API

Status: eerste ontwerp van het publieke modulecontract.

Dit document beschrijft uitsluitend de buitenkant van Kwaliteitsbewaking. Andere modules mogen niet
afhankelijk zijn van agents, testorganisatie, interne observaties of de volgorde van teststappen. De
volgende implementaties gebruiken hetzelfde contract:

- [Kwaliteitsbewaking — MVP](mvp.md): één Tester-agent voert de volledige
  kwaliteitssessie uit;
- [Kwaliteitsbewaking — uitgebreide implementatie](uitgebreid.md): vier
  gespecialiseerde rollen, parallel testen, testrotatie en leren per agentrol.

Beide zijn afzonderlijke Maven-implementatiemodules van hetzelfde publieke `quality`-contract in
`product-factory-api`. De main-module
neemt bij build-time exact één implementatie op.

## Verantwoordelijkheid

Kwaliteitsbewaking test de werkende applicatie, verifieert losse opleveringen en controleert na de
laatste story of de volledige bevroren epic de bedoelde gebruikersverbetering heeft bereikt. Zij
publiceert reproduceerbare bugs en duurzame verificaties met bewijs. Ontbrekende epicdekking staat
in een verificatie en wordt via een command als nieuw planwerk aangevraagd.

De module is eigenaar en enige schrijver van `QualityWorkItem`, `Bug`, `Verification`,
`QualitySnapshot` en haar eigen `ProcessSession`. Hoe de module intern tot testbewijs en conclusies
komt, verschilt per implementatie.

Kwaliteitsbewaking maakt geen stories, wijzigt geen epicinhoud en bepaalt geen backlogvolgorde. Zij
kan alleen via publieke commands een bugfix, aanvullend planwerk, epicuitkomst of signaaluitkomst
doorgeven aan de module die de betreffende entiteit bezit.

## Publieke module-interface

De enige agentgestuurde ingang is:

```java
void runProcessSession(ProductId productId);
```

De scheduler of een handmatige UI-/REST-actie kan deze functie voor één product starten. Per product
kan maximaal één onafgeronde logische kwaliteitssessie bestaan, ook wanneer die `WAITING_FOR_AI` of
`BLOCKED` is; verschillende producten mogen parallel worden getest. Een handmatige aanroep hervat
zo'n niet-actief wachtende sessie. Alleen wanneer voor hetzelfde product op dat moment al een
functiecall uitvoert, volgt `ProcessAlreadyRunning`; een botsende geplande aanroep met zo'n actieve
call wordt als overgeslagen geregistreerd. Alleen deze functie mag voor Kwaliteitsbewaking nieuwe
taken bij [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md) aanvragen. Welke en hoeveel taken
een sessie gebruikt, is een implementatiedetail.

Aan het begin van een run zet gewone applicatiecode eerst retrybare `BLOCKED`- of `FAILED`-workitems
van dit product waarvan `retryAfter` is verstreken terug op `PENDING`. Daarna claimt de run atomair
een vaste momentopname van de `PENDING` `QualityWorkItem`s van dit product die klaarstaan. Nieuwe
verzoeken wachten op de volgende run.
Naast deze gerichte queueopdrachten mag de run zelf periodiek testwerk kiezen. Zonder queuewerk of
periodiek werk eindigt zij als succesvolle no-op.

De deterministische publieke functies zijn beperkt tot read-only queries en gerichte commands voor
de eigen queue en bugs:

```java
BugDetails getBug(BugId bugId);
List<BugDetails> findBugs(BugFilter filter);
List<VerificationDetails> findVerifications(VerificationFilter filter);
QualitySnapshotDetails getCurrentQuality(ProductId productId);
List<QualitySnapshotDetails> getQualityHistory(ProductId productId, TimeRange range);
List<QualityWorkItemDetails> findQualityWorkItems(ProductId productId, WorkItemStatus status);
List<QualityWorkItemDetails> findRetryableQualityWorkItems();
ProcessSessionDetails getProcessSession(ProcessSessionId processSessionId);
List<ProcessSessionDetails> findProcessSessions(ProcessSessionFilter filter);
QualityWorkItemId requestStoryVerification(RequestStoryVerificationCommand command);
QualityWorkItemId requestEpicVerification(RequestEpicVerificationCommand command);
QualityWorkItemId requestBugfixRetest(RequestBugfixRetestCommand command);
QualityWorkItemId requestSignalInvestigation(RequestSignalInvestigationCommand command);
void retryQualityWorkItem(QualityWorkItemId workItemId);
void linkBugfixStory(BugId bugId, StoryId storyId);
```

De vier `request...`-commands starten geen test en geen agent. Zij valideren de bron en voegen alleen
een idempotent `PENDING`-werkitem toe. `retryQualityWorkItem(...)` maakt alleen een retrybaar
workitem direct klaar; de normale UI-/REST-afhandeling start daarna zo nodig de gewone
`runProcessSession(productId)`. `linkBugfixStory(bugId, storyId)` laat Productplanning een bugfixstory aan een
bestaande uitvoerbare bug koppelen. Dezelfde koppeling is idempotent. Een bug mag na een afgeronde
of geannuleerde eerdere poging later opnieuw een bugfixstory krijgen, maar nooit twee tegelijk
actieve bugfixstories. Geen aanroeper krijgt toegang tot de kwaliteitsrepository.

De processessiequeries ondersteunen minimaal product, status en periode, leveren de nieuwste
sessies eerst en tonen de gebruikte input-, geheugen-, AI-taak- en publicatiereferenties. Ze zijn
read-only en staan los van de retrycommands voor `QualityWorkItem`s.

`findBugs(...)` ondersteunt minimaal filteren op product-ID, epic-ID en status. Productplanning
gebruikt deze query om vóór epicverificatie betrouwbaar vast te stellen dat geen relevante `OPEN`
bug resteert; het hoeft dat niet uit workitems of verificatielinks af te leiden.

`findVerifications(...)` ondersteunt minimaal filteren op product-ID, doeltype, doel-ID, uitkomst,
omgeving en periode. Daardoor kunnen processen exact bewijs voor één doel lezen en kan de frontend
ook recente verificaties en volledige historie tonen.

## QualityWorkItem: de queuegrens

Een `QualityWorkItem` bevat work-item-ID, product-ID, type, bron-ID en -versie, doelomgeving,
prioriteit, idempotentiesleutel, status, claim, foutinformatie, `attemptCount`, `lastAttemptAt`,
`retryable`, `retryAfter` en een eventuele `blockedReason`. De typen zijn:

| Type | Normale aanvrager | Betekenis |
|---|---|---|
| `VERIFY_STORY` | Productplanning na een relevante storyoplevering | toets storycriteria en regressierisico |
| `VERIFY_EPIC` | Productplanning nadat al het niet-geannuleerde werk is opgeleverd en geverifieerd, of nadat geannuleerd Software Factory-werk een nieuwe feitelijke beoordeling nodig maakt | toets de volledige bevroren epic tegen de werkende applicatie |
| `RETEST_BUGFIX` | Productplanning na oplevering van een bugfixstory | herhaal reproductie en aangrenzende controles |
| `INVESTIGATE_USER_SIGNAL` | product-/overlegmodule | onderzoek een gemeld kwaliteitsprobleem |

De statussen zijn `PENDING`, `IN_PROGRESS`, `DONE`, `BLOCKED` en `FAILED`. De commandhandler mag een
prioriteit overnemen, maar de latere kwaliteitsrun bepaalt zelf de testaanpak. De queue hoort bij
Kwaliteitsbewaking; Productontwerp bevat geen testqueue.

### Retry en back-off

Een tijdelijke blokkade verwijdert nooit het testwerk. Bij iedere mislukte poging verhoogt
Kwaliteitsbewaking `attemptCount`, bewaart zij de concrete reden en gebruikt zij dezelfde back-off.
Een tijdelijk ontbrekende testvoorwaarde wordt retrybaar `BLOCKED`; een uitgeputte technische
uitvoeringspoging kan retrybaar `FAILED` zijn. Een definitieve contractfout is niet retrybaar en
heeft daarom geen **Retry now**. De back-off is:

| Mislukte poging | Nieuwe `retryAfter` |
|---|---|
| 1 | 15 minuten later |
| 2 | 1 uur later |
| 3 | 4 uur later |
| 4 en verder | 24 uur later |

Er is geen maximaal aantal domeinretries. Vanaf vijf mislukte pogingen toont de UI het afgeleide
label **Aandacht nodig**, maar het workitem blijft dagelijks herstelbaar. Dit staat los van de
begrensde technische attempts van één `AiTask` binnen AI-uitvoering.

`findRetryableQualityWorkItems()` geeft alle retrybare `BLOCKED`- en `FAILED`-items productoverstijgend
terug, primair gesorteerd op `attemptCount` aflopend en daarna op oudste laatste poging. De UI toont
voor ieder item minimaal product, type, doel, blokkadereden, aantal pogingen, laatste poging en
`retryAfter`.

`retryQualityWorkItem(...)` behoudt historie en `attemptCount`, maakt `retryAfter` leeg en zet het
item op `PENDING`. De UI-/REST-use-case roept daarna de normale
`runProcessSession(workItem.productId)` aan wanneer Kwaliteitsbewaking voor dat product niet al
draait. Een gelijktijdige `ProcessAlreadyRunning` voor hetzelfde product betekent alleen dat de
bestaande run doorgaat; het workitem blijft veilig `PENDING` voor een volgende vaste batch.

## Interface met andere modules en services

Kwaliteitsbewaking gebruikt publieke capabilitypackages uit `product-factory-api` en hun read-only
DTO's. DTO's zijn geen database-entiteiten. De module assembleert en valideert testtaken; bij echte
AI-uitvoering draaien de daadwerkelijke Git-checkout, browser, log- en testclients als tools in de
tijdelijke Dockeromgeving van de worker. Een server-side mock gebruikt alleen het voorbereide
resultaat. Spring Modulith structureert alleen de binnenkant van de gekozen
Kwaliteitsbewaking-implementatie.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `ProductAssignmentDetails` | productmodule | productgrenzen en publieke Git-URL van het product |
| `TestableProductDetails` | productmodule | omgevingen, routes, toegestane accounts, databereik en testgrenzen |
| `DecisionDto` | Besluitenregister-query voor het huidige tijdstip | grote blijvende privacy-, veiligheids- of productgrenzen die het testen beïnvloeden |
| `EpicDetails` | Productontwerp | bevroren scope, UX, succescriteria en status van de geclaimde versie |
| `StoryDetails` | Productplanning | type, storyversie, status, `deliveredCommitSha`, overige oplevergegevens, acceptatiecriteria en zelfstandige UX |
| `UserSignalDetails` | productmodule | oorspronkelijke melding plus actuele status en resultaatkoppelingen; categorie `QUALITY_CONCERN` vraagt extra onderzoek |
| `QualityWorkItem` | Kwaliteitsbewaking | duurzame gerichte testopdracht die de run claimt |
| `AgentMemoryItemDetails` | Agentgeheugen | alleen de actuele geheugenitems van de agentrol die op dat moment wordt uitgevoerd |
| `AiJobConfigurationDetails` | AI-uitvoering (`settings`) | actuele provider en model voor het soort kwaliteitsjob; bevroren op iedere nieuwe taak |
| `AiTaskResultDetails` | AI-uitvoering | opaque resultaat van een eerder door deze processessie aangevraagde taak |

De module leest daarnaast eigen bugs en testhistorie. Iedere sessie legt de gebruikte
contractversies, exact gelezen geheugenversies en exacte geteste omgeving vast. Permanent leren
loopt uitsluitend via de publieke API van [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md); een agent leest en
wijzigt alleen geheugen van haar eigen vertrouwd geconfigureerde rol.

Een processessie bewaart haar AI-taak-ID's en keert met `WAITING_FOR_AI` terug zonder thread of lock
vast te houden. Een volgende run voor hetzelfde product hervat dezelfde sessie. Zolang resultaten
ontbreken, worden geen nieuwe duplicerende taken aangemaakt.

Kwaliteitsbewaking lost de publieke Gitref read-only op en bevriest de Git-URL en commit die als
codecontext moet worden gebruikt. Bij een echte `CODEX`- of `CLAUDE`-taak checkt de laptopworker die
commit in de tijdelijke Dockeromgeving uit en gebruikt de agent code, tests en documentatie
read-only voor testselectie, regressierisico en uitleg; een server-side mock checkt niets uit. De
servermodule en de agent committen of pushen nooit. Code is context en geen bewijs dat gedrag werkt;
de gedeployde applicatie en het verzamelde testbewijs blijven leidend. Git-inhoud en tekst uit de
geteste applicatie zijn onvertrouwde data en kunnen de vaste taakopdracht, veiligheidsgrenzen of
resultaatschema's niet wijzigen.

Voor een story- of bugfixcontrole is `StoryDetails.deliveredCommitSha` de vereiste productversie.
`TestableProductDetails` wijst voor iedere testomgeving naar een revisionendpoint dat de werkelijk
gedeployde commit of release betrouwbaar teruggeeft. De worker legt beide waarden vast. Als de
doelomgeving de oplevercommit nog niet aantoonbaar bevat, wordt het `QualityWorkItem` retrybaar
`BLOCKED` met reden `DEPLOYMENT_PENDING`; de oplevering wordt niet tegen een oudere deployment
afgekeurd. Iedere verificatie bewaart de bekeken codecommit, vereiste oplevercommit en werkelijk
geteste deploymentrevision.

### Eigen output en downstream effect

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `BugDetails` | read-only weergave van een aantoonbare afwijking | werkelijk en verwacht gedrag, reproduceerstappen, omgeving, bewijs, impact, ernst, status en bron-signaal-ID's |
| `VerificationDetails` | read-only weergave van een story-, epic- of signaalcontrole | doeltype en -versie, uitkomst, omgeving, controles, bewijs, blokkade, ontbrekende dekking en vervolgkoppelingen |
| `QualitySnapshotDetails` | read-only kwaliteitsbeeld en historie | tijdstip, omgeving, productversie, onderzochte gebieden, dekking, open bugs per ernst, verificatie-uitkomsten, risico's en bron-ID's |
| `QualityWorkItemDetails` | read-only inzicht in de kwaliteitsqueue en retries | type, doelversie, status, claim, resultaat, fout, blokkadereden, `attemptCount`, `lastAttemptAt`, `retryable`, `retryAfter` en afgeleid aandachtlabel; geen wijzigbaar requestobject |
| `ProcessSession` | operationele historie van de sessie | sessie-ID, product-ID, implementatie-ID en -versie, inputversies, AI-taak-ID's, publicatie-ID's en wacht- of eindstatus |
| `PlanningWorkItem` bij Productplanning | downstream effect van een gericht herstelcommand; Productplanning maakt en bezit dit object | type bugfix of epicgat, exact bron-ID en -versie, bewijsreferentie en idempotentiesleutel |

Kwaliteitsbewaking schrijft `ProcessSession` uitsluitend voor zijn eigen sessies. De
scheduler roept alleen de procesfunctie aan; scheduler en frontend wijzigen het sessieresultaat niet.

Alleen Kwaliteitsbewaking schrijft `Bug` en `Verification`. Zij meldt een gerichte storyuitkomst via
`recordStoryVerification(...)` en vraagt Productplanning via `requestBugfix(...)` of
`requestEpicGapPlanning(...)` om vervolgwerk. Zij vraagt Productontwerp via
`recordEpicVerification(...)` om een epicuitkomst vast te leggen en de productmodule via
`recordSignalInvestigation(...)` om een gebruikerssignaal bij te werken. Geen ontvangende module
kan de onderliggende verificatie of het bewijs veranderen.

## Kwaliteitsbeeld en historie

Een `QualitySnapshot` is een onveranderlijke momentopname van wat op dat moment aantoonbaar bekend
is over de productkwaliteit. Iedere processessie hoort bij precies één product. Kwaliteitsbewaking
maakt na iedere afgeronde productgebonden processessie waarin daadwerkelijk is getest precies één
nieuwe snapshot voor dat product. Een no-op-sessie maakt geen duplicaat en een oude snapshot wordt
nooit bijgewerkt.

Een snapshot bevat geen verborgen totaalscore. Hij toont afzonderlijk:

- geteste omgeving en productversie;
- belangrijke routes en gebieden die recent zijn onderzocht;
- gebieden waarvan de controle verouderd is of ontbreekt;
- open bugs per ernst en relevante veranderingen sinds de vorige snapshot;
- aantallen en uitkomsten van story-, epic- en signaalverificaties;
- actuele risico's, blokkades en de bron-ID's waarop het beeld is gebaseerd.

`getCurrentQuality(...)` retourneert de nieuwste snapshot. `getQualityHistory(...)` retourneert de
snapshots in de gevraagde periode, zodat de frontend en Productontwerp per kwaliteitsdimensie kunnen
zien wat verbetert of verslechtert. Bugs en onveranderlijke verificaties blijven de inhoudelijke
bron; de snapshot is het controleerbare historische beeld daarvan op één moment.

## Een kwaliteitszorg uit een overleg

Wanneer de Stakeholder aangeeft dat een onderdeel mogelijk niet goed werkt of extra aandacht nodig
heeft, registreert de productmodule dit als `UserSignal`. De optionele categorie
`QUALITY_CONCERN` helpt Kwaliteitsbewaking bij de testagenda, maar maakt van de melding geen opdracht
met een vooraf bepaald resultaat.

Na registratie roept de product-/overlegmodule bij zo'n signaal idempotent
`requestSignalInvestigation(...)` aan. Dat zet alleen een `INVESTIGATE_USER_SIGNAL`-workitem in de
kwaliteitsqueue. De melding bepaalt dus wel wat onderzocht moet worden, maar niet wat de uitkomst is.

De Stakeholder schrijft dit databaseobject niet rechtstreeks. De frontend of overlegmodule voert een
command uit op de productmodule; die bewaart de oorspronkelijke melding daarna onveranderlijk.
Tijdens een latere run leest Kwaliteitsbewaking `UserSignalDetails`, bewaart het onderzoek als `Verification` en roept
`recordSignalInvestigation(...)` op de productmodule aan. Alleen de productmodule wijzigt status en
resultaatkoppelingen op `UserSignal`.

Een signaalonderzoeksresultaat bevat minimaal:

- stabiel resultaat-ID, product-ID, signaal-ID en exact signaalversienummer;
- resultaat **Bevestigde bug**, **Geen probleem gevonden**, **Meer bewijs nodig**, **Duplicaat**,
  **Buiten testscope** of **Kwaliteitspatroon gevonden**;
- uitleg, uitgevoerde controles, geteste omgeving en bewijs;
- eventuele koppeling naar `Bug`, een nieuw `UserSignal` met categorie `QUALITY_PATTERN` of het duplicaatsignaal;
- processessie-ID en onderzoekstijdstip.

Het command zet de actuele signaalstatus en koppelt het verificatie-ID. Daardoor kan de frontend op
één `UserSignalDetails` tonen wat is onderzocht en wat daaruit kwam, terwijl bronmelding en
testbewijs ieder hun eigen eigenaar houden.

## Storyverificatie

Een story- of bugfixoplevering krijgt:

- **Geslaagd** — afgesproken gedrag werkt in de bedoelde omgeving;
- **Afgekeurd** — gedrag wijkt aantoonbaar af;
- **Geblokkeerd** — controle is door omgeving, toegang of ontbrekende informatie niet mogelijk.

Bij **Afgekeurd** publiceert Kwaliteitsbewaking zo nodig een bug. Het herschrijft de story niet.
Een story met leveringsstatus `CANCELLED` krijgt geen fictieve storyverificatie: er is immers geen
oplevering om aan de storycriteria te toetsen. Productplanning laat dan, zodra het overige actuele
werk van de epic klaar is, de complete epic opnieuw beoordelen op wat werkelijk in de applicatie
aanwezig is.

## Epicverificatie

Wanneer een storyverificatie of bugfixhertest is gepubliceerd, meldt Kwaliteitsbewaking de exacte
uitkomst via `recordStoryVerification(...)` aan Productplanning. Dat command start geen agent.
Productplanning vraagt normaal pas epicverificatie aan wanneer alle niet-geannuleerde stories en
bugfixes van de epic `DONE` zijn, iedere actuele controle is geslaagd en geen herstelwerk of open
bug resteert. Heeft Software Factory een `IN_PROGRESS` story `CANCELLED`, dan geldt een expliciete
herbeoordelingsroute: zodra alle overige niet-geannuleerde stories klaar en actueel geslaagd zijn,
mag Productplanning de epic ook met de nog open bevindingen naar `VERIFYING` brengen. Alleen de
complete test van de feitelijke applicatie bepaalt dan of het geannuleerde werk werkelijk nog nodig
is. Zo kan een handmatig aangebrachte oplossing slagen en blijft ontbrekend gedrag niet verborgen.
Een `CANCELLED` epic krijgt geen nieuwe epicverificatie. De controle gebruikt exact de door
Productontwerp bevroren `EpicDetails` en beoordeelt:

- de volledige gebruikersroute, niet alleen losse schermen;
- alle relevante UX-toestanden en overgangen;
- de expliciete scope in en uit;
- toegankelijkheid, privacy en andere kwaliteitsgrenzen;
- samenhang tussen de geleverde stories;
- de succescriteria en de merkbare verbetering voor de gebruiker.

De uitkomst is:

- `PASSED` — de gebruikersverbetering is aangetoond;
- `NEEDS_WORK` — er is minimaal één concrete bug of aantoonbaar dekkingsgat binnen de bevroren
  epic;
- `BLOCKED` — een verantwoord oordeel is tijdelijk niet mogelijk; reden en retry staan op het
  `QualityWorkItem`;
- `NOT_SUCCESSFUL` — alles werkt zoals afgesproken, maar beschikbaar bewijs weerlegt een vooraf
  toetsbaar succescriterium van de epic.

`NOT_SUCCESSFUL` vereist dus positief, herleidbaar bewijs. Ontbrekende gebruiksdata, een onbereikbare
meetbron of een nog niet gedeployde commit geven `BLOCKED`. Een abstract langetermijndoel zonder
beschikbare meetbron kan aanleiding zijn voor een later `UserSignal`, maar nooit voor een
willekeurig negatief epicoordeel.

Kwaliteitsbewaking schrijft eerst een onveranderlijke `Verification` en roept daarna
`recordEpicVerification(...)` op Productontwerp aan. Productontwerp controleert de epicversie en is
de enige schrijver van de epicstatus.

Het vervolg per uitkomst is:

| Uitkomst | Epic bij Productontwerp | Bericht aan Productplanning |
|---|---|---|
| `PASSED` | `COMPLETED` | geen |
| `NEEDS_WORK` met bug | terug naar `ACTIVE` | `requestBugfix(...)` per uitvoerbare bug |
| `NEEDS_WORK` met dekkingsgat | terug naar `ACTIVE` | `requestEpicGapPlanning(...)` met verificatie-ID |
| `BLOCKED` | blijft `VERIFYING` | geen; hetzelfde workitem volgt het retrybeleid |
| `NOT_SUCCESSFUL` | `NOT_SUCCESSFUL` | geen; Productontwerp kan vanuit het resultaat een vervolgepic onderzoeken |

Eén `NEEDS_WORK`-verificatie kan zowel bugs als dekkingsgaten bevatten. Productontwerp hoeft de
bevindingen niet te interpreteren: het registreert de uitkomst en zet de epic terug naar `ACTIVE`.
Kwaliteitsbewaking stuurt daarnaast per concrete bevinding alleen het gerichte command naar
Productplanning.

Productplanning krijgt dus geen generiek verificatieresultaat terug. Alleen een gericht plancommand
betekent dat zij nieuw ontwikkelwerk moet vormen. Gedrag binnen de bevroren scope wordt een
aanvullende story of bugfixstory binnen dezelfde epic. Alleen een nieuwe wens buiten scope of een
onjuiste productaanname kan later tot een nieuwe vervolgepic leiden.

## Bug, ontbrekende dekking of nieuwe productkans

Kwaliteitsbewaking classificeert een ontbrekend of onjuist resultaat vóór publicatie:

| Situatie | Publicatie | Vervolg |
|---|---|---|
| Gedrag stond in een uitgevoerde story maar werkt niet volgens de story | bestaande `OPEN` bug of nieuwe `Bug`, plus `Verification` met `NEEDS_WORK` bij een epiccontrole | Kwaliteitsbewaking vraagt Productplanning om een bugfix |
| Gedrag viel duidelijk binnen de bevroren epic, maar er bestond nooit een story voor | `Verification` met ontbrekende dekking | Kwaliteitsbewaking vraagt Productplanning om aanvullend werk |
| Alles werkt zoals ontworpen en toetsbaar bewijs weerlegt de productaanname | `Verification` met `NOT_SUCCESSFUL` en een `UserSignal` van categorie `QUALITY_PATTERN` | Productontwerp registreert de uitkomst en leert |
| Gewenst gedrag valt buiten de bevroren scope | `UserSignal` | Productontwerp kan een vervolgepic maken |

Kwaliteitsbewaking maakt in geen van deze gevallen zelf een story.

## Bugcontract en levenscyclus

Een gepubliceerde bug bevat minimaal:

- stabiel bug-ID, product-ID en versie;
- korte titel en gebruikersimpact;
- werkelijk en verwacht gedrag;
- reproduceerstappen en vereiste uitgangssituatie;
- geteste omgeving, productversie en tijdstip;
- screenshot, log, netwerkspoor of ander bewijs;
- ernst P0, P1, P2 of P3 met reden;
- relatie met story, epicversie, oplevering en soortgelijke bugs;
- eventuele bron-gebruikerssignalen;
- status `OPEN`, `RESOLVED` of `INVALID`.

De bugstatus is bewust klein:

- `OPEN` — de reproduceerbare afwijking is nog niet aantoonbaar verdwenen;
- `RESOLVED` — actueel testbewijs toont aan dat de afwijking niet meer bestaat;
- `INVALID` — de bevinding bleek geen productafwijking, met zichtbare reden.

Productplanning koppelt een bugfixstory via `linkBugfixStory(bugId, storyId)`. De handler accepteert
dezelfde koppeling idempotent en valideert dat de bug `OPEN` is. Productplanning is de vertrouwde
aanroeper en garandeert vóór het command dat de story type `BUGFIX` heeft, hetzelfde bug- en
product-ID bevat en dat er geen andere bugfixstory voor deze bug `TODO` of `IN_PROGRESS` is;
Kwaliteitsbewaking roept tijdens deze commandhandler niet synchroon terug naar Productplanning. Een
eerdere `DONE`- of `CANCELLED`-story blijft historie en blokkeert een volgende herstelstory niet.
**Gepland**, **In herstel** en **Hertesten** zijn daardoor afgeleide UI-labels uit later gelezen
`StoryDetails`, geen extra bugstatussen.

Als een `DONE` bugfixstory bij de hertest de afwijking niet heeft weggenomen, blijft de bug gewoon
`OPEN`. De bugfixstory blijft `DONE`: Software Factory heeft haar immers wel opgeleverd. De
afgekeurde `Verification` legt vast dat die oplevering het probleem niet verhielp en voegt het
actuele bewijs als een nieuwe bugversie aan dezelfde bug toe. Kwaliteitsbewaking vraagt voor die
nieuwe bronversie idempotent opnieuw `requestBugfix(...)` aan. Daardoor ontstaat precies één nieuw
`PlanningWorkItem` en wordt een eerder afgerond bugfixverzoek niet hergebruikt. Een latere
planningsrun mag daarna een volgende gewone bugfixstory koppelen. Er bestaat geen aparte
mislukstatus, extra bugconstructie of repair-storytype.

Wanneer Software Factory een bugfixstory `CANCELLED`, wordt de bug evenmin als mislukt of opgelost
gemarkeerd. Productplanning vraagt geen hertest van een niet-opgeleverde story aan, maar neemt de
annulering mee in de herbeoordelingsroute van de complete epic. Blijkt de afwijking daar nog te
bestaan, dan blijft de bug `OPEN`, krijgt dezelfde bug een nieuwe bewijsversie en kan opnieuw een
gewone bugfixstory worden gepland. Blijkt zij bijvoorbeeld door een handmatige wijziging verdwenen,
dan mag actueel bewijs de bug `RESOLVED` maken.

Na een geslaagde bugfixhertest zet Kwaliteitsbewaking de actuele bug op `RESOLVED` en
maakt zij intern een nieuw idempotent `VERIFY_STORY`-workitem voor de oorspronkelijke story tegen de
nieuwe productversie. Daardoor vervangt een oude afgekeurde controle niet stilzwijgend het bewijs:
de oorspronkelijke storycriteria worden na de fix opnieuw actueel aangetoond voordat de epic naar
`VERIFYING` kan.

## Ontbrekende epicdekking

Een dekkingsgat is geen afzonderlijke entiteit meer. Een epicverificatie kan één of meer gestructureerde
dekkingsgaten bevatten met scope- of UX-verwijzing, gebruikersimpact, ontbrekend gedrag en bewijs.
Kwaliteitsbewaking roept `requestEpicGapPlanning(...)` aan met het verificatie-ID. Productplanning
maakt tijdens een processessie de aanvullende stories; een latere verificatie toont of het gat is
opgelost. Zo blijft het bewijs historisch intact zonder een tweede lifecycle naast epic en story.

## Wanneer Kwaliteitsbewaking draait

Gericht werk wordt gequeue'd door:

- Productplanning na een story-, bugfix- of volledige epicoplevering;
- de product-/overlegmodule na een kwaliteitszorg of relevant gebruikerssignaal.

Daarnaast kan de scheduler een run starten voor dagelijkse kernroutes, een verouderd kwaliteitsbeeld
of een periodieke kwaliteitscontrole. Een queuecommand is een snelle databasebewerking; alleen de latere
`runProcessSession(productId)` mag nieuwe AI-taken aanvragen. De backloggrootte speelt hierbij geen
rol.

## Fouten, hervatten en idempotentie

- Eén verificatiepoging is uniek voor workitem-ID en `attemptCount`. Een retry mag voor hetzelfde
  story- of epicdoel een nieuwe onveranderlijke verificatie publiceren; de historie blijft staan en
  de actuele referentie wijst naar de nieuwste poging.
- Een idempotente herhaling binnen dezelfde poging maakt geen duplicaat en wijzigt een reeds
  gepubliceerde verificatie niet.
- Een technische testfout wordt apart geregistreerd en niet als productbug gepubliceerd.
- Een sessie kan na een verlopen claim worden hervat met dezelfde inputmomentopname.
- Gedeeltelijk bewijs blijft intern tot reproductie en privacycontrole zijn afgerond.

## Eisen aan iedere implementatie

De MVP en iedere latere implementatie moeten garanderen dat:

- zij hetzelfde publieke `quality`-contract implementeert en andere capabilities alleen via
  `product-factory-api` gebruikt;
- iedere nieuwe `ProcessSession` de exacte `implementationId` en `implementationVersion` vastlegt;
- alleen `runProcessSession(productId)` voor Kwaliteitsbewaking nieuwe AI-taken aanvraagt;
- maximaal één onafgeronde logische sessie per product bestaat, verschillende producten parallel
  mogen lopen en een wachtende sessie geen technische lock vasthoudt;
- ieder geclaimd workitem `DONE`, `BLOCKED` of `FAILED` wordt;
- retrybare workitems zonder maximum volgens de vaste begrensde back-off opnieuw beschikbaar komen;
- een handmatige retry historie en `attemptCount` behoudt en nooit een tweede processessie afdwingt;
- een onbereikbare of kapotte testomgeving niet als productbug wordt gepubliceerd;
- iedere bug reproduceerbaar is en controleerbaar bewijs bevat;
- een bugfixstory alleen `TODO`, `IN_PROGRESS`, `DONE` of `CANCELLED` kan zijn en een afgekeurde of
  geannuleerde poging nooit een aparte mislukstatus maakt;
- per bug maximaal één gekoppelde bugfixstory tegelijk `TODO` of `IN_PROGRESS` is;
- iedere story- of bugfixverificatie de vereiste `deliveredCommitSha` vergelijkt met de werkelijk
  gedeployde revision en bij achterlopende deployment `BLOCKED` blijft;
- iedere verificatie exacte doel-, opleverings-, omgevings- en bronversies bevat;
- ontbrekende epicdekking binnen de bevroren scope wordt bewezen;
- ieder gebruikerssignaalonderzoek een expliciete uitkomst of blokkade krijgt;
- de eigen procesruntime iedere agent alleen het actuele geheugen van haar eigen rol geeft en de exact gelezen
  geheugenversies vastlegt;
- iedere AI-taak een vaste provider, model en configuratieversie heeft en via AI-uitvoering loopt;
- publieke output pas na contract-, privacy- en geheimencontrole verschijnt;
- iedere productgebonden sessie waarin daadwerkelijk is getest precies één nieuwe onveranderlijke
  `QualitySnapshot` voor dat product maakt;
- publicaties en vervolgcommands atomair of idempotent herstelbaar zijn.

## Wanneer een sessie klaar is

Een inhoudelijke sessie is klaar wanneer:

- iedere geclaimde opdracht en gekozen periodieke controle een resultaat of expliciete blokkade heeft;
- iedere publieke bug reproduceerbaar en van bewijs voorzien is;
- ieder dekkingsgat aantoonbaar binnen de bevroren epic valt en niet door een story wordt gedekt;
- iedere verificatie naar exacte epic-, story-, opleverings- en omgevingsversies verwijst;
- ieder onderzocht gebruikerssignaal naar exact signaal-ID en -versie verwijst en een zichtbaar
  onderzoeksresultaat of expliciete blokkade heeft;
- het kwaliteitsbeeld uit de gevalideerde resultaten is opgebouwd;
- na daadwerkelijk testwerk precies één nieuwe `QualitySnapshot` voor het product is opgeslagen;
- publicaties atomair en geversioneerd beschikbaar zijn;
- de operationele sessiestatus en volgende plandatum zijn opgeslagen.

## Gerelateerde documenten

- [Kwaliteitsbewaking — MVP](mvp.md)
- [Kwaliteitsbewaking — uitgebreide implementatie](uitgebreid.md)
- [Productontwerp-API](../productontwerp/api.md)
- [Productplanning-API](../productplanning/api.md)
- [Software Factory-dispatcher](../software-factory-dispatcher.md)
- [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md)
- [AI-worker en taakcontainer](../../gedeelde-modules/ai-worker.md)
- [Maven en Spring Modulith](../../platform/maven-en-spring-modulith.md)
- [Processen en entiteiten](../processen-en-entiteiten.md)
