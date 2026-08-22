# Product Factory v2 — Productplanning

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt Productplanning en de technische Software Factory-dispatcher uit. De
black-boxinterface in [Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md) is
leidend.

## Verantwoordelijkheid

Productplanning kiest uit de beschikbare epicdefinities de beste volgende gebruikersverbetering,
bevriest het exacte versienummer, verdeelt die epic in kleine stories en ordent alle nog niet
afgeronde stories met een `sequenceNumber`. Voor HKH staan normaal ongeveer tien stories met status
`TODO` klaar. Een story is een productstory of een bugfix.

De module is eigenaar van:

- epicselectie en epicvoortgang;
- stories, storytype, status en `sequenceNumber`;
- prioriteitsbeoordelingen, onderbouwingen en de gebruikte bronversies;
- de backlogvoorraad en `aanvullingNodig`;
- de koppeling met Software Factory en de uitvoeringsstatus;
- het eigen agent- en procesgeheugen.

Productplanning wijzigt geen epicdefinitie, UX-ontwerp, bug of verificatieresultaat.

## Publieke procesfunctie

De enige intelligente uitvoerende ingang is:

```java
void runProcessSession();
```

Alleen de scheduler gebruikt deze functie. De module claimt zelf atomair één product waarvoor een
epic moet worden gekozen, stories moeten worden gemaakt, de backlog moet worden aangevuld of een
verificatie moet worden verwerkt. Zonder planbaar werk eindigt de functie als succesvolle no-op.

De functie verstuurt niets naar Software Factory. Verzending en statussynchronisatie zijn technisch
werk van de dispatcher en staan buiten de agentgestuurde processessie.

## Interface met andere modules en services

Productplanning gebruikt `processcontracts` voor geversioneerde read-only projecties. Het importeert
Productontwerp of Kwaliteitsbewaking niet rechtstreeks.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `StakeholderProfileView` | product-/overlegmodule | identiteit, rol en beslissingsmandaat achter Stakeholderrichting |
| `ProductAssignmentView` | productmodule | productidentiteit, grenzen en backlogconfiguratie |
| `StakeholderDirectionView` | product-/overlegmodule | expliciete epic- en prioriteitsgrenzen |
| `EpicDefinitionView` | Productontwerp | beschikbare epicversies, gebruikerswaarde, scope, UX en succescriteria |
| `BugView` | Kwaliteitsbewaking | uitvoerbare bugfixkandidaat inclusief ernst en bewijs |
| `EpicCompletionGapView` | Kwaliteitsbewaking | gedrag binnen de bevroren epic waarvoor aanvullende stories nodig zijn |
| `StoryVerificationView` | Kwaliteitsbewaking | of een productstory of bugfix is goedgekeurd |
| `EpicVerificationView` | Kwaliteitsbewaking | of de volledige epic geslaagd, onvolledig, niet aantoonbaar, geblokkeerd of niet geslaagd is |
| `SoftwareFactoryWorkView` | dispatcher | externe status van eerder verzonden werk |

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `EpicProgressView` | voortgang van één exact bevroren epiccontract | epic-ID, epicversie, selectiereden, bevriezingsmoment, status, verificatiemomenten en einduitkomst; bevat geen kopie van de epicinhoud |
| `StoryView` | zelfstandig uitvoerbare productstory of bugfix en tegelijk een geordend onderdeel van de berekende backlog | type, bronrelaties, epicversie indien van toepassing, gedrag, acceptatiecriteria, UX, `sequenceNumber`, status en externe referentie |
| `BacklogSupplyView` | berekende voorraadprojectie, geen duurzame entiteit | aantallen `TODO`, `IN_PROGRESS` en `DONE`, lage grens, streefpeil en `aanvullingNodig` |
| `StoryDeliveryPackage` | onveranderlijk pakket dat de dispatcher naar Software Factory stuurt | bron-ID's en versies, complete story of bugfix, acceptatiecriteria, UX, attachments, hashes en idempotentiesleutel |
| `DeliveryResultView` | genormaliseerde terugmelding uit Software Factory | extern ID, story-ID en -versie, status, opleverlocatie, tijdstippen en foutinformatie |
| `ProcessSessionPublication` | operationeel resultaat van de sessie | sessie-ID, product-ID, gebruikte inputversies, wijzigingen en eindstatus |

Productplanning schrijft `ProcessSessionPublication` uitsluitend voor zijn eigen intelligente
sessies. De scheduler en frontend schrijven dit record niet. De dispatcher bewaart afzonderlijke
technische `DispatchAttempt`-records binnen Productplanning.

Alleen Productplanning schrijft `EpicProgressView`, story-inhoud en `sequenceNumber`. De dispatcher
draait binnen dezelfde module en mag via een smalle application service uitsluitend de storyvelden
voor externe referentie, leveringstijdstippen en de overgangen `TODO` → `IN_PROGRESS` → `DONE`
schrijven. `BacklogSupplyView` wordt alleen uit stories berekend en heeft geen eigen tabel of
schrijver.

Een betekenisvolle epicselectie, prioriteitsregel, afwijking van de normale volgorde of
epicafsluiting wordt als `DecisionRecordView` door het centrale
[Besluitenregister](product-factory-v2-besluitenregister.md) vastgelegd. Productplanning levert het
registratieverzoek met keuze, alternatieven, criteria en bronversies; het Besluitenregister is
eigenaar van het publieke besluitrecord. Kleine mechanische verschuivingen door een afgerond item
zijn geen nieuw productbesluit.

## Een epic kiezen en bevriezen

Productplanning kiest alleen een `EpicDefinitionView` met status **Beschikbaar**. De keuze legt vast:

- exact epic-ID en versienummer;
- waarom deze epic voor alternatieven gaat;
- selectietijdstip en gebruikte productrichting;
- relevante afhankelijkheden en bekende risico's;
- wie of welke agentrol het besluit nam.

Het aanmaken van `EpicProgressView` bevriest de gekozen epicversie. Productplanning kopieert de
epicdefinitie niet en wijzigt haar niet. Alle stories en latere verificaties verwijzen naar exact
dezelfde versie.

Normaal heeft een product maximaal één actieve epicvoortgang. Een nieuwe epic wordt pas gekozen als
de vorige **Geslaagd**, **Niet geslaagd** of **Gestopt** is. Voorraad kan al worden voorbereid uit de
actieve epic, maar Productplanning schrijft niet ver vooruit voor nog niet gekozen epics.

## Storycontract en backlog

Een `StoryView` bevat minimaal:

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

Een epicgat leidt tot aanvullende productstories binnen dezelfde bevroren epic. Productplanning verandert
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
die UX zonder herontwerp uit de betreffende `StoryView`. De dispatcher kopieert dit alleen
naar het leveringspakket.

De drie storystatussen betekenen precies:

- `TODO` — compleet, geprioriteerd en nog niet in Software Factory aangemaakt;
- `IN_PROGRESS` — door de dispatcher naar Software Factory gestuurd en daar nog open;
- `DONE` — Software Factory heeft de story opgeleverd.

`SoftwareFactoryWorkView` en `DeliveryResultView` bewaren de fijnere externe status, blokkades en
foutinformatie. Een afgekeurde oplevering zet de oorspronkelijke story niet terug: zij blijft
`DONE` en Kwaliteitsbewaking publiceert een bug, waarna Productplanning een nieuwe bugfixstory met
status `TODO` kan maken.

## Epicvoortgang en afsluiting

Een `EpicProgressView` gebruikt:

- **Geselecteerd** — exact epic-ID en versienummer zijn gekozen en bevroren;
- **Stories maken** — de epic wordt in uitvoerbaar werk verdeeld;
- **Actief** — één of meer stories of bugfixes worden uitgevoerd;
- **Controleren** — alle geplande stories zijn afgerond en het geheel wordt beoordeeld;
- **Geslaagd** — de gebruikersverbetering is door Kwaliteitsbewaking bewezen;
- **Niet geslaagd** — alles is geleverd, maar het bedoelde gebruikersresultaat is niet bereikt;
- **Gestopt** — bewust niet verder, met reden.

Productplanning zet een epic pas op **Controleren** wanneer alle bekende stories `DONE` zijn. Een
`EpicVerificationView` bepaalt daarna het vervolg:

| Uitkomst | Actie van Productplanning |
|---|---|
| **Geslaagd** | epicvoortgang afsluiten als **Geslaagd** |
| **Onvolledig** met epicgaten | aanvullende stories maken en terug naar **Actief** |
| **Niet aantoonbaar** | op **Controleren** blijven en wachten op aanvullend bewijs |
| **Geblokkeerd** | blokkade zichtbaar opslaan en later opnieuw laten controleren |
| **Niet geslaagd** | afsluiten als **Niet geslaagd**; Productontwerp leest de epicverificatie en verwerkt de conclusie intern |

Een bouwfout wordt door Kwaliteitsbewaking als bug gepubliceerd en wordt een bugfixstory in de
backlog. Een epicgat wordt door Productplanning in stories vertaald. Kwaliteitsbewaking schrijft zelf
geen stories.
Hoort de bug bij de actieve epic, dan verwijst de bugfixstory ook naar die bevroren epicversie en
blijft of gaat `EpicProgressView` terug naar **Actief** totdat de fix `DONE` en opnieuw gecontroleerd
is.

## Interne entiteiten

- `PlanningSession` — de geclaimde intelligente processessie;
- `EpicCandidateSet` — beschikbare epicversies voor vergelijking;
- `EpicSelectionAssessment` — vergelijking en selectiereden;
- `EpicProgress` — verwijzing naar de bevroren versie, voortgang en einduitkomst;
- `StoryDraft` — story vóór kritiek en publicatie;
- `StoryUxSnapshot` — relevante, zelfstandige kopie uit het bevroren epicontwerp;
- `StoryCoverageMap` — koppeling tussen epicscope/UX en stories;
- `StoryCandidateSet` — uitvoerbare productstories en bugs die bugfixstories kunnen worden;
- `PriorityAssessment` — vergelijking per prioriteitscriterium;
- `StoryOrderDraft` — voorgestelde `sequenceNumber`-volgorde vóór kritiek;
- `Story` — inhoud, type, volgorde en de drie statussen;
- `DecisionDraft` — interne onderbouwing vóór registratie van een betekenisvolle keuze in het Besluitenregister;
- `SupplyState` — voorraad, lage grens en streefpeil;
- `StoryDeliveryPackage`, `DispatchAttempt`, `ExternalWorkLink` en `DeliverySync` — technische koppeling;
- `PlanningMemory` — lessen over slicing, balans en blokkades;
- `AgentRun` — input, promptversie, output en fout van één agenttaak.

## Agents

De intelligente processessie gebruikt vier vaste agentrollen:

1. **Epicplanner** — vergelijkt beschikbare epics en kiest de exacte volgende versie.
2. **Storymaker** — verdeelt de bevroren epic of een epicgat in kleine productstories.
3. **Backlogplanner** — combineert productstories en bugfixstories en bepaalt hun `sequenceNumber`.
4. **Planningscriticus** — controleert epicdekking, storygrootte, afhankelijkheden, balans en redenen.

De rollen werken grotendeels sequentieel: eerst epicselectie, daarna storyvorming, daarna
backlogordening en tot slot kritiek. Wanneer een actieve epic al is gekozen, kunnen onafhankelijke
storydelen parallel worden voorbereid, maar de Storymaker levert één samenhangende set op voordat de
Backlogplanner die ordent.

## Soorten processessies

1. **Epic kiezen** — beschikbare epicversies vergelijken en één versie bevriezen.
2. **Stories maken** — de gekozen epic of een epicgat in uitvoerbaar werk verdelen.
3. **Backlog aanvullen** — nieuwe productstories en bugfixstories op `TODO` opnemen tot het streefpeil.
4. **Herprioriteren** — de volgorde aanpassen op basis van nieuw bewijs of urgentie.
5. **Resultaat verwerken** — story- en epicverificaties in epicvoortgang en vervolgwerk verwerken.

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
publiceer epicvoortgang + stories
```

### Stap 1 — invoer en selectie

De module claimt één product, zet alle bronversies vast, verwerkt intrekkingen en verificaties en
controleert of al een epicvoortgang actief is. Zonder actieve epic vergelijkt de Epicplanner de
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

De berekende `BacklogSupplyView.aanvullingNodig` wordt waar bij vier of minder `TODO`-stories en pas
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

Goedgekeurde epicvoortgang en stories worden atomair gepubliceerd. Betekenisvolle keuzes
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
3. werkt externe status en tijdstippen bij;
4. zet een extern opgeleverde story lokaal van `IN_PROGRESS` op `DONE` en publiceert
   `DeliveryResultView` voor Kwaliteitsbewaking;
5. verstuurt niets als Software Factory nog openstaand werk voor het product heeft;
6. selecteert anders de afhankelijke-vrije `TODO`-story met het laagste `sequenceNumber`;
7. vormt zonder inhoudelijke beslissing één onveranderlijk `StoryDeliveryPackage` uit die story;
8. verstuurt tekst, Markdown, JSON en SVG als tekst en binaire assets als begrensde attachments;
9. maakt precies één Software Factory-story aan met een idempotentiesleutel;
10. laat Software Factory het complete pakket in de eigen storystorage vastleggen;
11. bewaart het externe ID en zet de story op `IN_PROGRESS`;
12. publiceert leveringsstatus en de opnieuw berekende voorraadprojectie.

De dispatcher kan geen story overslaan, story-inhoud of UX schrijven, epic kiezen of prioriteit
veranderen.
Voor een JSON-only transport mag een binair attachment Base64 gebruiken, maar de database bewaart
het oorspronkelijke binaire object en Base64 is geen domein- of opslagformaat.

## Planning

Een intelligente sessie wordt planbaar door:

- een nieuwe of gewijzigde beschikbare epicdefinitie;
- een lage backlogvoorraad;
- een nieuwe uitvoerbare bug of een epicgat;
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
  `SoftwareFactoryWorkView` totdat Software Factory haar afrondt of de koppeling expliciet wordt
  hersteld.

## Wanneer een processessie klaar is

Een intelligente sessie is klaar wanneer:

- selectie of vervolgstatus expliciet is vastgelegd;
- alle nieuwe stories, bugs, epicgaten en verificaties zijn verwerkt of uitgesteld;
- iedere productstory naar exact één bevroren epicversie verwijst en iedere bugfixstory naar exact
  één bugversie;
- iedere story zelfstandig alle relevante UX-inhoud en assets bevat;
- `sequenceNumber`, storystatus en epicstatus uitlegbaar en consistent zijn;
- voorraadstatus en `aanvullingNodig` juist zijn;
- output atomair is opgeslagen;
- de operationele sessiestatus en volgende plandatum zijn vastgelegd.
