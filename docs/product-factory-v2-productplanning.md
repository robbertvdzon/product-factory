# Product Factory v2 — Productplanning

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt Productplanning en de technische Software Factory-dispatcher uit. De
black-boxinterface in [Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md) is
leidend.

## Verantwoordelijkheid

Productplanning kiest uit de beschikbare epics de beste volgende gebruikersverbetering, claimt het
exacte versienummer bij Productontwerp, verdeelt die epic in kleine stories en ordent alle nog niet
afgeronde stories met een `sequenceNumber`. Voor HKH staan normaal ongeveer tien stories met status
`TODO` klaar. Een story is een productstory of een bugfix.

De module is eigenaar van:

- epicselectie en de opdrachten waarmee de epicstatus bij Productontwerp wordt bijgewerkt;
- stories, storytype, status en `sequenceNumber`;
- prioriteitsbeoordelingen, onderbouwingen en de gebruikte bronversies;
- de berekening van backlogvoorraad en `aanvullingNodig`;
- stories en hun koppeling met Software Factory;
- het eigen agent- en procesgeheugen.

Productplanning wijzigt geen epicinhoud, UX-ontwerp, bug of verificatieresultaat. Zij gebruikt voor
toegestane wijzigingen commands van de module die de betreffende entiteit bezit.

## Publieke module-interface

De enige intelligente uitvoerende ingang is:

```java
void runProcessSession();
```

Alleen de scheduler gebruikt deze functie. De module claimt zelf atomair één product waarvoor een
epic moet worden gekozen, stories moeten worden gemaakt, de backlog moet worden aangevuld of een
verificatie moet worden verwerkt. Zonder planbaar werk eindigt de functie als succesvolle no-op.

De functie verstuurt niets naar Software Factory. Verzending en statussynchronisatie zijn technisch
werk van de dispatcher en staan buiten de agentgestuurde processessie.

Daarnaast biedt Productplanning deterministische commands en read-only queries:

```java
StoryDetails getStory(StoryId storyId);
List<StoryDetails> getBacklog(ProductId productId);
BacklogSupply getBacklogSupply(ProductId productId);
void markStoryAsDispatched(MarkStoryAsDispatchedCommand command);
void markStoryAsDeveloped(MarkStoryAsDevelopedCommand command);
void recordDispatchFailure(RecordDispatchFailureCommand command);
void requestBugfix(RequestBugfixCommand command);
void requestCompletionWork(RequestCompletionWorkCommand command);
```

De dispatcher gebruikt de eerste drie schrijfcommands. Kwaliteitsbewaking kan met een bug- of
epicverificatie-ID nieuw planwerk aanvragen, maar kan geen storytekst, status of volgorde schrijven.
De module valideert iedere overgang, verwachte storyversie en idempotentiesleutel.

## Interface met andere modules en services

Productplanning gebruikt de publieke command- en query-API's van andere Spring Modulith-modules.
Read-only DTO's staan in `processcontracts` en zijn geen eigen database-entiteiten.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `StakeholderDetails` | product-/overlegmodule | identiteit, rol en beslissingsmandaat achter Stakeholderrichting |
| `ProductAssignmentDetails` | productmodule | productidentiteit, grenzen, backlogconfiguratie en publieke Git-URL |
| `StakeholderDirectionDetails` | product-/overlegmodule | expliciete epic- en prioriteitsgrenzen |
| `EpicDetails` | Productontwerp | beschikbare of geclaimde epicversie, status, gebruikerswaarde, scope, UX en succescriteria |
| `BugDetails` | Kwaliteitsbewaking | uitvoerbare bugfixkandidaat inclusief ernst en bewijs |
| `VerificationDetails` | Kwaliteitsbewaking | story- of epicuitkomst, bewijs en eventueel ontbrekende dekking |
| `SoftwareFactoryWork` | dispatcheradapter | actuele externe status van eerder verzonden werk; tijdelijk integratiegegeven |

Tijdens een inhoudelijke sessie mag Productplanning de publieke Git-URL uit de productopdracht
uitchecken en broncode, tests en documentatie read-only bekijken. Zo kan zij bestaande componenten,
afhankelijkheden en technische grenzen herkennen zonder de implementatie voor te schrijven. Er is
geen aparte workspace of Git-service: de URL volstaat. Productplanning commit en pusht nooit en legt
de bekeken commit-SHA alleen als bronverwijzing bij de sessie of story vast. De story blijft
zelfstandig en bevat alle benodigde product- en UX-informatie.

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `StoryDetails` | read-only weergave van een zelfstandig uitvoerbare productstory of bugfix | type, bronrelaties, epicversie, gedrag, acceptatiecriteria, UX, `sequenceNumber`, status en externe referentie |
| `BacklogSupply` | berekend queryresultaat, geen duurzame entiteit | aantallen `TODO`, `IN_PROGRESS` en `DONE`, lage grens, streefpeil en `aanvullingNodig` |
| `StoryDeliveryPackage` | onveranderlijk pakket dat de dispatcher naar Software Factory stuurt | bron-ID's en versies, complete story of bugfix, acceptatiecriteria, UX, attachments, hashes en idempotentiesleutel |
| `ProcessSession` | operationele historie van de sessie | sessie-ID, product-ID, gebruikte inputversies, wijzigingen en eindstatus |

Productplanning schrijft `ProcessSession` uitsluitend voor zijn eigen intelligente
sessies. De scheduler en frontend schrijven dit record niet. De dispatcher bewaart afzonderlijke
technische `DispatchAttempt`-records binnen Productplanning.

Alleen Productplanning schrijft `Story`, story-inhoud en `sequenceNumber`. De dispatcher heeft geen
repositorytoegang en gebruikt uitsluitend `markStoryAsDispatched(...)`,
`markStoryAsDeveloped(...)` en `recordDispatchFailure(...)`. `BacklogSupply` wordt bij iedere query
uit stories berekend en heeft geen eigen tabel of schrijver.

Een betekenisvolle epicselectie, prioriteitsregel, afwijking van de normale volgorde of
epicafsluiting wordt als `DecisionRecord` door het centrale
[Besluitenregister](product-factory-v2-besluitenregister.md) vastgelegd. Productplanning levert het
registratieverzoek met keuze, alternatieven, criteria en bronversies; het Besluitenregister is
eigenaar van het publieke besluitrecord. Kleine mechanische verschuivingen door een afgerond item
zijn geen nieuw productbesluit.

## Een epic kiezen en bevriezen

Productplanning kiest alleen `EpicDetails` met status **Beschikbaar**. De keuze legt vast:

- exact epic-ID en versienummer;
- waarom deze epic voor alternatieven gaat;
- selectietijdstip en gebruikte productrichting;
- relevante afhankelijkheden en bekende risico's;
- wie of welke agentrol het besluit nam.

Productplanning roept `claimEpicForPlanning(...)` op Productontwerp aan. Dat command controleert en
bevriest de gekozen epicversie atomair en zet haar op **In planning**. Productplanning kopieert de
epic niet en alle stories en latere verificaties verwijzen naar exact dezelfde versie.

Normaal heeft een product maximaal één actieve epic. Een nieuwe epic wordt pas gekozen als
de vorige **Geslaagd**, **Niet geslaagd** of **Gestopt** is. Voorraad kan al worden voorbereid uit de
actieve epic, maar Productplanning schrijft niet ver vooruit voor nog niet gekozen epics.

## Storycontract en backlog

Een `Story` bevat minimaal:

- stabiel story-ID en product-ID;
- type `PRODUCT_STORY` of `BUGFIX`;
- epic-ID en bevroren epicversie voor een productstory en, indien relevant, voor een bugfix;
- bug-ID en bugversie voor een bugfix;
- het kleine zichtbare gebruikersgedrag;
- waarom dit deel waardevol of noodzakelijk is;
- duidelijke acceptatiecriteria;
- relevante hoofd-, lege, laad-, fout- en uitzonderingssituaties;
- een zelfstandige momentopname van het relevante deel van het bevroren UX-ontwerp;
- gebruikersflow, schermen, componenten, interacties en alle relevante toestanden;
- responsive gedrag, toegankelijkheid en privacygrenzen;
- benodigde tekstuele en binaire ontwerpassets met naam, MIME-type, grootte en hash;
- afhankelijkheden op andere stories of externe voorwaarden;
- bekende technische grenzen zonder de implementatie voor te schrijven;
- storyversie, begrijpelijke prioriteitsreden en `sequenceNumber`;
- status `TODO`, `IN_PROGRESS` of `DONE`;
- eventueel extern Software Factory-ID en verzend- en oplevertijdstip.

Een story is alleen **Uitvoerbaar** wanneer Software Factory haar zonder epicquery, intern
planningsdossier of Product Factory-call kan bouwen en Kwaliteitsbewaking haar
zelfstandig kan testen. Productplanning selecteert de relevante UX uit de bevroren epic zonder haar
inhoudelijk te herschrijven.

Een dekkingsgat uit een verificatie leidt tot aanvullende productstories binnen dezelfde bevroren epic. Productplanning verandert
daarvoor niet de scope of het UX-ontwerp. Ligt de gewenste verandering buiten die scope, dan maakt
Productplanning geen story maar wacht zij op een nieuwe epic van Productontwerp.

De backlog is geen afzonderlijke entiteit. Zij is de volgende databasequery:

```sql
select * from story
where product_id = :productId and status <> 'DONE'
order by sequence_number
```

`sequenceNumber` is per product uniek binnen de niet-afgeronde stories. Productplanning wijzigt de
volgorde van `TODO`-stories in één transactie. Een `IN_PROGRESS`-story wordt niet tussentijds naar een
andere plaats geschoven. Afgeronde stories bewaren hun laatste nummer alleen voor historie, maar
komen niet meer in de backlogquery voor.

Ook een bugfixstory is pas `TODO` wanneer de leveringsinhoud zelfstandig compleet is. Naast de
bug, verwacht gedrag en bewijs legt Productplanning daarom de relevante, bevroren UX-momentopname
vast wanneer de bug gebruikersgedrag raakt. Bij een bug die aan een eerdere story is gekoppeld komt
die UX zonder herontwerp uit de betreffende `StoryDetails`. De dispatcher kopieert dit alleen
naar het leveringspakket.

De drie storystatussen betekenen precies:

- `TODO` — compleet, geprioriteerd en nog niet in Software Factory aangemaakt;
- `IN_PROGRESS` — door de dispatcher naar Software Factory gestuurd en daar nog open;
- `DONE` — Software Factory heeft de story opgeleverd.

De actuele externe referentie en oplevering staan op `Story`; afzonderlijke `DeliveryAttempt`-records
bewaren fouten en retryhistorie. Een afgekeurde oplevering zet de oorspronkelijke story niet terug: zij blijft
`DONE` en Kwaliteitsbewaking publiceert een bug, waarna Productplanning een nieuwe bugfixstory met
status `TODO` kan maken.

## Epicstatus en afsluiting

De door Productontwerp beheerde `Epic` gebruikt:

- **Geselecteerd** — exact epic-ID en versienummer zijn gekozen en bevroren;
- **Stories maken** — de epic wordt in uitvoerbaar werk verdeeld;
- **Actief** — één of meer stories of bugfixes worden uitgevoerd;
- **Controleren** — alle geplande stories zijn afgerond en het geheel wordt beoordeeld;
- **Geslaagd** — de gebruikersverbetering is door Kwaliteitsbewaking bewezen;
- **Niet geslaagd** — alles is geleverd, maar het bedoelde gebruikersresultaat is niet bereikt;
- **Gestopt** — bewust niet verder, met reden.

Productplanning roept `markEpicReadyForVerification(...)` pas aan wanneer alle bekende stories
`DONE` zijn. Productontwerp zet de epic daarmee alleen op **Controleren** (`VERIFYING`);
Kwaliteitsbewaking vindt haar daarna zelf tijdens een geplande sessie. Een `VerificationDetails`
bepaalt daarna het vervolg:

| Uitkomst | Vervolg |
|---|---|
| **Geslaagd** | Productontwerp registreert de epic als **Geslaagd**; geen planwerk |
| **Onvolledig** met dekkingsgaten | Kwaliteitsbewaking roept `requestCompletionWork(...)` aan; Productplanning maakt aanvullende stories en de epic gaat terug naar **Actief** |
| bouwfout | Kwaliteitsbewaking maakt een bug en roept `requestBugfix(...)` aan; Productplanning maakt een bugfixstory en de epic blijft of gaat **Actief** |
| **Niet aantoonbaar** | epic blijft op **Controleren**; Kwaliteitsbewaking plant nieuw bewijswerk |
| **Geblokkeerd** | epic blijft op **Controleren** en de blokkade blijft zichtbaar; geen planwerk tenzij ontwikkeling nodig blijkt |
| **Niet geslaagd** | Productontwerp registreert **Niet geslaagd** en kan later een vervolgepic onderzoeken; geen automatisch planwerk |

Er komt geen algemeen verificatieantwoord terug naar Productplanning. Alleen als nieuw ontwikkelwerk
nodig is, roept Kwaliteitsbewaking `requestBugfix(...)` of `requestCompletionWork(...)` aan. Dat
command maakt idempotent een `PlanningRequest`, waarna een volgende `runProcessSession()` de nieuwe
story vormt. Bij **Geslaagd**, **Niet aantoonbaar**, **Geblokkeerd** of **Niet geslaagd** hoeft
Productplanning zonder zo'n gericht planverzoek niets te doen.

Een bouwfout wordt door Kwaliteitsbewaking als bug gepubliceerd en wordt een bugfixstory in de
backlog. Een dekkingsgat wordt door Productplanning in stories vertaald. Kwaliteitsbewaking schrijft zelf
geen stories.
Hoort de bug bij de actieve epic, dan verwijst de bugfixstory ook naar die bevroren epicversie.
Productplanning roept `markEpicActive(...)` aan totdat de fix `DONE` en opnieuw gecontroleerd is.

## Interne entiteiten

- `ProcessSession` — de geclaimde intelligente processessie en haar operationele historie;
- `EpicCandidateSet` — beschikbare epicversies voor vergelijking;
- `EpicSelectionAssessment` — vergelijking en selectiereden;
- `StoryDraft` — story vóór kritiek en publicatie;
- `StoryUxSnapshot` — relevante, zelfstandige kopie uit het bevroren epicontwerp;
- `StoryCoverageMap` — koppeling tussen epicscope/UX en stories;
- `StoryCandidateSet` — uitvoerbare productstories en bugs die bugfixstories kunnen worden;
- `PlanningRequest` — idempotent, duurzaam verzoek voor een bugfix of ontbrekende epicdekking dat een volgende processessie planbaar maakt;
- `PriorityAssessment` — vergelijking per prioriteitscriterium;
- `StoryOrderDraft` — voorgestelde `sequenceNumber`-volgorde vóór kritiek;
- `Story` — inhoud, type, volgorde en de drie statussen;
- `DecisionDraft` — interne onderbouwing vóór registratie van een betekenisvolle keuze in het Besluitenregister;
- `SupplyPolicy` — lage grens en streefpeil; de actuele voorraad wordt berekend;
- `StoryDeliveryPackage`, `DeliveryAttempt` en `ExternalWorkLink` — technische koppeling en historie;
- `PlanningMemory` — lessen over slicing, balans en blokkades;
- `AgentRun` — input, promptversie, output en fout van één agenttaak.

## Agents

De intelligente processessie gebruikt vier vaste agentrollen:

1. **Epicplanner** — vergelijkt beschikbare epics en kiest de exacte volgende versie.
2. **Storymaker** — verdeelt de bevroren epic of bewezen ontbrekende dekking in kleine productstories.
3. **Backlogplanner** — combineert productstories en bugfixstories en bepaalt hun `sequenceNumber`.
4. **Planningscriticus** — controleert epicdekking, storygrootte, afhankelijkheden, balans en redenen.

De rollen werken grotendeels sequentieel: eerst epicselectie, daarna storyvorming, daarna
backlogordening en tot slot kritiek. Wanneer een actieve epic al is gekozen, kunnen onafhankelijke
storydelen parallel worden voorbereid, maar de Storymaker levert één samenhangende set op voordat de
Backlogplanner die ordent.

## Soorten processessies

1. **Epic kiezen** — beschikbare epicversies vergelijken en één versie bevriezen.
2. **Stories maken** — de gekozen epic of ontbrekende dekking in uitvoerbaar werk verdelen.
3. **Backlog aanvullen** — nieuwe productstories en bugfixstories op `TODO` opnemen tot het streefpeil.
4. **Herprioriteren** — de volgorde aanpassen op basis van nieuw bewijs of urgentie.
5. **Resultaat verwerken** — story- en epicverificaties in statuscommands en vervolgwerk verwerken.

Een sessie kiest één hoofdsoort. Een eerste sessie mag na epicselectie de minimaal nodige eerste
stories maken, maar schrijft niet automatisch de hele toekomst vooruit.

## Verloop van één processessie

```text
claim product en inputmomentopname
                 │
                 ▼
Epicplanner kiest of bevestigt epicversie
                 │
                 ▼
Storymaker maakt/aanvult stories
                 │
                 ▼
Backlogplanner geeft stories sequenceNumbers
                 │
                 ▼
Planningscriticus controleert geheel
                 │
          akkoord of herstel
                 │
                 ▼
claim epic + publiceer stories
```

### Stap 1 — invoer en selectie

De module claimt één product, zet alle bronversies vast, verwerkt intrekkingen en verificaties en
controleert via Productontwerp of al een epic actief is. Zonder actieve epic vergelijkt de Epicplanner de
beschikbare epicdefinities op gebruikerswaarde, productdoel, bewijs, risico, afhankelijkheden,
behapbaarheid en productgezondheid.

### Stap 2 — stories maken

De Storymaker dekt de bevroren scope en UX stap voor stap af. Iedere gemaakte story krijgt een
zelfstandige `StoryUxSnapshot`; een verwijzing naar alleen het epicontwerp is niet voldoende. Niet
ieder detail hoeft vooraf een story te krijgen, maar de eerste bruikbare slice en voldoende
vervolgwerk voor een gezonde backlog moeten duidelijk zijn. `StoryCoverageMap` maakt zichtbaar welke
delen al wel en nog niet door stories worden afgedekt.

### Stap 3 — prioriteren en vullen

De Backlogplanner weegt minimaal:

- P0–P3-ernst van bugs;
- gebruikerswaarde en urgentie;
- bijdrage aan de actieve epic;
- afhankelijkheden en blokkades;
- risico, omkeerbaarheid en leerwaarde;
- reeds openstaand werk in Software Factory;
- balans tussen vernieuwing en productgezondheid.

Voor HKH gelden aanvankelijk:

- lage grens: vier `TODO`-stories;
- streefpeil: tien `TODO`-stories;
- maximaal één extern openstaand item.

De berekende `BacklogSupply.aanvullingNodig` wordt waar bij vier of minder `TODO`-stories en pas
weer onwaar bij tien. `IN_PROGRESS` telt niet mee als klaarliggende voorraad. Productplanning
verzint geen werk om het getal tien te halen.

### Stap 4 — kritiek en atomair publiceren

De Planningscriticus controleert:

- dat exact één onveranderlijke epicversie is gebruikt;
- dat Productplanning de epicdefinitie of UX niet heeft herschreven;
- dat stories klein, zelfstandig en testbaar zijn;
- dat iedere story alle benodigde UX-inhoud en assets bevat zonder concurrerend ontwerp te maken;
- dat storydekking geen belangrijk deel van scope of UX vergeet;
- dat bugs de vernieuwing niet zonder reden verdringen;
- dat alle posities een begrijpelijke reden hebben;
- dat epicstatus, storystatus en `sequenceNumber` consistent zijn.

De epicclaim en goedgekeurde stories worden idempotent gepubliceerd. De eigen storytransactie is
atomair; het epiccommand gebruikt een idempotentiesleutel zodat herstel na een tussenfout veilig is. Betekenisvolle keuzes
worden met een idempotent registratieverzoek aan het Besluitenregister aangeboden.

## StoryDeliveryPackage-contract

Het `StoryDeliveryPackage` is de complete, onveranderlijke grens naar Software Factory. Het wordt in
de database opgeslagen vóór verzending en bevat minimaal:

- contractversie, pakket-ID, product-ID en idempotentiesleutel;
- story-ID, storyversie en type `PRODUCT_STORY` of `BUGFIX`;
- epic-ID en bevroren epicversie wanneer die van toepassing zijn;
- titel, gebruikersdoel, gewenst gedrag, context en acceptatiecriteria;
- bekende grenzen en afhankelijkheden zonder de technische implementatie voor te schrijven;
- voor een bugfix: werkelijk gedrag, verwacht gedrag, reproduceerstappen, omgeving, impact en bewijs;
- de volledige relevante `StoryUxSnapshot` met flow, schermen, toestanden, interacties, responsive
  gedrag, toegankelijkheid en privacygrenzen;
- attachments met naam, MIME-type, oorspronkelijke bytegrootte, SHA-256-hash, transportcodering en
  inhoud;
- aanmaaktijdstip en een hash over het complete pakket.

Tekst, Markdown, JSON en SVG blijven gewone UTF-8-tekst. Alleen binaire inhoud gebruikt bij een
JSON-only API `base64`; een aparte begrensde attachment-upload mag later hetzelfde contract
efficiënter transporteren. Software Factory bevestigt pas acceptatie nadat het complete pakket in de
eigen storystorage staat. Een URL naar Product Factory is nooit de enige drager van vereiste inhoud.

## Software Factory-dispatcher

De Software Factory-dispatcher is een geplande technische adapter binnen Productplanning. Hij is
geen productproces, heeft geen geheugen en gebruikt geen agents. Zijn technische ingang is:

```java
void runDispatchSession();
```

Iedere dispatchersessie:

1. claimt één product voor synchronisatie;
2. haalt open of recent gewijzigde Software Factory-items op;
3. registreert iedere externe response als `DeliveryAttempt`;
4. roept voor een extern opgeleverde story `markStoryAsDeveloped(...)` aan, waarna Productplanning
   `IN_PROGRESS` naar `DONE` zet en oplevervelden op `Story` bewaart;
5. verstuurt niets als Software Factory nog openstaand werk voor het product heeft;
6. selecteert anders de afhankelijke-vrije `TODO`-story met het laagste `sequenceNumber`;
7. vormt zonder inhoudelijke beslissing één onveranderlijk `StoryDeliveryPackage` uit die story;
8. verstuurt tekst, Markdown, JSON en SVG als tekst en binaire assets als begrensde attachments;
9. maakt precies één Software Factory-story aan met een idempotentiesleutel;
10. laat Software Factory het complete pakket in de eigen storystorage vastleggen;
11. roept `markStoryAsDispatched(...)` aan om extern ID en `IN_PROGRESS` door Productplanning te laten opslaan;
12. stelt leveringsstatus en berekende `BacklogSupply` via queries beschikbaar.

De dispatcher kan geen story overslaan, story-inhoud of UX schrijven, epic kiezen of prioriteit
veranderen.
Voor een JSON-only transport mag een binair attachment Base64 gebruiken, maar de database bewaart
het oorspronkelijke binaire object en Base64 is geen domein- of opslagformaat.

## Planning

Een intelligente sessie wordt planbaar door:

- een nieuwe of gewijzigde beschikbare epicdefinitie;
- een lage backlogvoorraad;
- een nieuwe uitvoerbare bug of een verificatie met ontbrekende dekking;
- een story- of epicverificatie;
- een gewijzigd Stakeholderprofiel of gewijzigde Stakeholderrichting;
- een periodieke herprioritering.

De dispatcher draait vaker en onafhankelijk van de intelligente sessie.

## Fouten, hervatten en idempotentie

- Epicselectie, story-inhoud en storyvolgorde worden atomair en geversioneerd opgeslagen.
- Een sessie gebruikt één vastgezette inputmomentopname.
- Een gekozen epicversie kan niet door een nieuwere ontwerpversie worden vervangen.
- Storybron en epicversie zijn onderdeel van alle idempotentiesleutels.
- De dispatcher zoekt na een timeout eerst op idempotentiesleutel en maakt nooit blind een duplicaat.
- Een synchronisatiefout verandert de laatst bekende externe status niet, maar markeert haar als
  mogelijk verouderd.
- Een extern geblokkeerde story blijft `IN_PROGRESS`; de blokkade staat zichtbaar in
  de actuele `SoftwareFactoryWork` totdat Software Factory haar afrondt of de koppeling expliciet wordt
  hersteld.

## Wanneer een processessie klaar is

Een intelligente sessie is klaar wanneer:

- selectie of vervolgstatus expliciet is vastgelegd;
- alle nieuwe stories, bugs, dekkingsgaten en verificaties zijn verwerkt of uitgesteld;
- iedere productstory naar exact één bevroren epicversie verwijst en iedere bugfixstory naar exact
  één bugversie;
- iedere story zelfstandig alle relevante UX-inhoud en assets bevat;
- `sequenceNumber`, storystatus en epicstatus uitlegbaar en consistent zijn;
- voorraadstatus en `aanvullingNodig` juist zijn;
- output atomair is opgeslagen;
- de operationele sessiestatus en volgende plandatum zijn vastgelegd.
