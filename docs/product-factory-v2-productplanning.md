# Product Factory v2 — Productplanning

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt Productplanning en de technische Software Factory-dispatcher uit. De
black-boxinterface in [Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md) is
leidend.

## Verantwoordelijkheid

Productplanning kiest uit de beschikbare epicdefinities de beste volgende gebruikersverbetering,
bevriest het exacte versienummer, verdeelt die epic in kleine productstories en onderhoudt één
uitlegbaar geprioriteerde backlog. Voor HKH staan normaal ongeveer tien verzendbare productstories
of bugfixes klaar.

De module is eigenaar van:

- epicselectie en epicuitvoering;
- productstories en hun inhoudelijke status;
- backlogitems en hun volgorde;
- prioriteitsbesluiten en de gebruikte bronversies;
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
| `EpicExecutionView` | uitvoering van één exact bevroren epiccontract | epic-ID, versie, selectiereden, bevriezingsmoment, status, story-ID's en einduitkomst |
| `ProductStoryView` | zelfstandig uitvoerbaar gedrag binnen de epic | epic-ID en -versie, gedrag, acceptatiecriteria, afhankelijkheden en relevante UX-verwijzingen |
| `PrioritizedBacklogView` | actuele geordende backlog | backlogversie, geordende item-ID's, redenen en aanmaakmoment |
| `BacklogItemView` | één verzendbare of reeds verzonden opdracht | type, bron-ID en -versie, positie, prioriteitsreden, status en externe referentie |
| `PriorityDecisionView` | uitlegbare keuze achter epicselectie of backlogvolgorde | keuze, alternatieven, criteria, bronversies en beslisser |
| `BacklogSupplyView` | voorraadstatus voor scheduler en Productontwerp | aantallen per status, lage grens, streefpeil en `aanvullingNodig` |
| `DeliveryResultView` | genormaliseerde terugmelding uit Software Factory | extern ID, backlogitem, status, opleverlocatie, tijdstippen en foutinformatie |
| `ProcessSessionPublication` | operationeel resultaat van de sessie | sessie-ID, product-ID, gebruikte inputversies, wijzigingen en eindstatus |

Alleen Productplanning schrijft epicuitvoering, productstories, backlogvolgorde en backlogstatus. De
dispatcher draait binnen dezelfde module en mag uitsluitend leveringsvelden en bijbehorende
statusovergangen schrijven.

## Een epic kiezen en bevriezen

Productplanning kiest alleen een `EpicDefinitionView` met status **Beschikbaar**. De keuze legt vast:

- exact epic-ID en versienummer;
- waarom deze epic voor alternatieven gaat;
- selectietijdstip en gebruikte productrichting;
- relevante afhankelijkheden en bekende risico's;
- wie of welke agentrol het besluit nam.

Het aanmaken van `EpicExecutionView` bevriest de gekozen epicversie. Productplanning kopieert de
epicdefinitie niet en wijzigt haar niet. Alle stories en latere verificaties verwijzen naar exact
dezelfde versie.

Normaal heeft een product maximaal één actieve epicuitvoering. Een nieuwe epic wordt pas gekozen als
de vorige **Geslaagd**, **Niet geslaagd** of **Gestopt** is. Voorraad kan al worden voorbereid uit de
actieve epic, maar Productplanning schrijft niet ver vooruit voor nog niet gekozen epics.

## Storycontract

Een productstory bevat minimaal:

- stabiel story-ID en product-ID;
- epic-ID en bevroren epicversie;
- het kleine zichtbare gebruikersgedrag;
- waarom dit deel waardevol of noodzakelijk is;
- duidelijke acceptatiecriteria;
- relevante hoofd-, lege, laad-, fout- en uitzonderingssituaties;
- verwijzingen naar het bijbehorende deel van het bevroren UX-ontwerp;
- afhankelijkheden op andere stories of externe voorwaarden;
- bekende technische grenzen zonder de implementatie voor te schrijven;
- inhoudelijke status en versie.

Een story is alleen **Uitvoerbaar** wanneer Software Factory haar zonder intern planningsdossier kan
bouwen en Kwaliteitsbewaking haar zelfstandig kan testen.

Een epicgat leidt tot aanvullende stories binnen dezelfde bevroren epic. Productplanning verandert
daarvoor niet de scope of het UX-ontwerp. Ligt de gewenste verandering buiten die scope, dan maakt
Productplanning geen story maar wacht zij op een nieuwe epic van Productontwerp.

## Backlogcontract

Een backlogitem heeft minimaal:

- stabiel backlogitem-ID en product-ID;
- type `PRODUCT_STORY` of `BUGFIX`;
- bron-ID, bronmodule en bronversie;
- titel en korte gewenste uitkomst uit de bron;
- prioriteitspositie en begrijpelijke reden;
- relevante afhankelijkheden;
- status;
- eventueel extern Software Factory-ID;
- aanmaak-, wijzigings-, verzend- en oplevertijd;
- laatste gesynchroniseerde externe status.

De combinatie van product, brontype, bron-ID en bronversie is uniek. Een nieuwere bronversie leidt
tot herbeoordeling, niet automatisch tot een tweede backlogitem.

Een backlogitem gebruikt:

- **Verzendbaar** — compleet, geprioriteerd en nog niet extern aangemaakt;
- **Verstuurd** — door de dispatcher in Software Factory aangemaakt;
- **Bezig** — Software Factory meldt actieve uitvoering;
- **Opgeleverd** — Software Factory heeft resultaat teruggegeven;
- **Controleren** — Kwaliteitsbewaking moet het resultaat nog verifiëren;
- **Afgerond** — Kwaliteitsbewaking heeft het resultaat goedgekeurd;
- **Geblokkeerd** — kan niet verder, met zichtbare reden;
- **Gestopt** — bewust niet verder, met zichtbare reden.

## Epicuitvoering en afsluiting

Een epicuitvoering gebruikt:

- **Geselecteerd** — exact epic-ID en versienummer zijn gekozen en bevroren;
- **Stories maken** — de epic wordt in uitvoerbaar werk verdeeld;
- **Actief** — één of meer stories of bugfixes worden uitgevoerd;
- **Controleren** — alle geplande stories zijn afgerond en het geheel wordt beoordeeld;
- **Geslaagd** — de gebruikersverbetering is door Kwaliteitsbewaking bewezen;
- **Niet geslaagd** — alles is geleverd, maar het bedoelde gebruikersresultaat is niet bereikt;
- **Gestopt** — bewust niet verder, met reden.

Productplanning zet een epic pas op **Controleren** wanneer alle bekende stories zijn afgerond. Een
`EpicVerificationView` bepaalt daarna het vervolg:

| Uitkomst | Actie van Productplanning |
|---|---|
| **Geslaagd** | epicuitvoering afsluiten als **Geslaagd** |
| **Onvolledig** met epicgaten | aanvullende stories maken en terug naar **Actief** |
| **Niet aantoonbaar** | op **Controleren** blijven en wachten op aanvullend bewijs |
| **Geblokkeerd** | blokkade zichtbaar opslaan en later opnieuw laten controleren |
| **Niet geslaagd** | afsluiten als **Niet geslaagd** en het leerresultaat beschikbaar maken voor Productontwerp |

Een bouwfout wordt door Kwaliteitsbewaking als bug gepubliceerd en komt als bugfix in de backlog. Een
epicgat wordt door Productplanning in stories vertaald. Kwaliteitsbewaking schrijft zelf geen stories.

## Interne entiteiten

- `PlanningSession` — de geclaimde intelligente processessie;
- `EpicCandidateSet` — beschikbare epicversies voor vergelijking;
- `EpicSelectionAssessment` — vergelijking en selectiereden;
- `EpicExecution` — bevroren versie, voortgang en einduitkomst;
- `StoryDraft` — story vóór kritiek en publicatie;
- `StoryCoverageMap` — koppeling tussen epicscope/UX en stories;
- `BacklogCandidateSet` — uitvoerbare stories en bugs;
- `PriorityAssessment` — vergelijking per prioriteitscriterium;
- `BacklogDraft` — voorgestelde ordening vóór kritiek;
- `Backlog` en `BacklogItem` — actuele uitvoeringstoestand;
- `PriorityDecision` — auditregel voor een keuze;
- `SupplyState` — voorraad, lage grens en streefpeil;
- `DispatchAttempt`, `ExternalWorkLink` en `DeliverySync` — technische koppeling;
- `PlanningMemory` — lessen over slicing, balans en blokkades;
- `AgentRun` — input, promptversie, output en fout van één agenttaak.

## Agents

De intelligente processessie gebruikt vier vaste agentrollen:

1. **Epicplanner** — vergelijkt beschikbare epics en kiest de exacte volgende versie.
2. **Storymaker** — verdeelt de bevroren epic of een epicgat in kleine productstories.
3. **Backlogplanner** — combineert productstories en bugs en maakt de volledige volgorde.
4. **Planningscriticus** — controleert epicdekking, storygrootte, afhankelijkheden, balans en redenen.

De rollen werken grotendeels sequentieel: eerst epicselectie, daarna storyvorming, daarna
backlogordening en tot slot kritiek. Wanneer een actieve epic al is gekozen, kunnen onafhankelijke
storydelen parallel worden voorbereid, maar de Storymaker levert één samenhangende set op voordat de
Backlogplanner die ordent.

## Soorten processessies

1. **Epic kiezen** — beschikbare epicversies vergelijken en één versie bevriezen.
2. **Stories maken** — de gekozen epic of een epicgat in uitvoerbaar werk verdelen.
3. **Backlog aanvullen** — nieuwe stories en bugs opnemen tot het streefpeil.
4. **Herprioriteren** — de volgorde aanpassen op basis van nieuw bewijs of urgentie.
5. **Resultaat verwerken** — story- en epicverificaties in status en vervolgwerk verwerken.

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
Backlogplanner ordent stories en bugs
                 │
                 ▼
Planningscriticus controleert geheel
                 │
          akkoord of herstel
                 │
                 ▼
publiceer uitvoering + stories + backlog
```

### Stap 1 — invoer en selectie

De module claimt één product, zet alle bronversies vast, verwerkt intrekkingen en verificaties en
controleert of al een epicuitvoering actief is. Zonder actieve epic vergelijkt de Epicplanner de
beschikbare epicdefinities op gebruikerswaarde, productdoel, bewijs, risico, afhankelijkheden,
behapbaarheid en productgezondheid.

### Stap 2 — stories maken

De Storymaker dekt de bevroren scope en UX stap voor stap af. Niet ieder detail hoeft vooraf een
story te krijgen, maar de eerste bruikbare slice en voldoende vervolgwerk voor een gezonde backlog
moeten duidelijk zijn. `StoryCoverageMap` maakt zichtbaar welke delen al wel en nog niet door stories
worden afgedekt.

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

- lage grens: vier verzendbare backlogitems;
- streefpeil: tien verzendbare backlogitems;
- maximaal één extern openstaand item.

`aanvullingNodig` wordt waar bij vier of minder items en pas weer onwaar bij tien. Productplanning
verzint geen werk om het getal tien te halen.

### Stap 4 — kritiek en atomair publiceren

De Planningscriticus controleert:

- dat exact één onveranderlijke epicversie is gebruikt;
- dat Productplanning de epicdefinitie of UX niet heeft herschreven;
- dat stories klein, zelfstandig en testbaar zijn;
- dat storydekking geen belangrijk deel van scope of UX vergeet;
- dat bugs de vernieuwing niet zonder reden verdringen;
- dat alle posities een begrijpelijke reden hebben;
- dat epic-, story- en backlogstatus consistent zijn.

Goedgekeurde uitvoering, stories, besluiten en backlog worden atomair gepubliceerd.

## Software Factory-dispatcher

De Software Factory-dispatcher is een geplande technische adapter binnen Productplanning. Hij is
geen productproces, heeft geen geheugen en gebruikt geen agents. Zijn technische ingang is:

```java
void runDispatchSession();
```

Iedere dispatchersessie:

1. claimt één product voor synchronisatie;
2. haalt open of recent gewijzigde Software Factory-items op;
3. werkt lokale status en tijdstippen bij;
4. markeert extern afgerond werk lokaal als **Opgeleverd**, niet direct als **Afgerond**;
5. verstuurt niets als Software Factory nog openstaand werk voor het product heeft;
6. selecteert anders het bovenste afhankelijke-vrije item met status **Verzendbaar**;
7. maakt precies één Software Factory-story aan met een idempotentiesleutel;
8. bewaart het externe ID en zet het backlogitem op **Verstuurd**;
9. publiceert leverings- en voorraadstatus.

De dispatcher kan geen item overslaan, story schrijven, epic kiezen of prioriteit veranderen.

## Planning

Een intelligente sessie wordt planbaar door:

- een nieuwe of gewijzigde beschikbare epicdefinitie;
- een lage backlogvoorraad;
- een nieuwe uitvoerbare bug of een epicgat;
- een story- of epicverificatie;
- gewijzigde Stakeholderrichting;
- een periodieke herprioritering.

De dispatcher draait vaker en onafhankelijk van de intelligente sessie.

## Fouten, hervatten en idempotentie

- Selectie, stories en backlogpublicatie zijn atomair en geversioneerd.
- Een sessie gebruikt één vastgezette inputmomentopname.
- Een gekozen epicversie kan niet door een nieuwere ontwerpversie worden vervangen.
- Storybron en epicversie zijn onderdeel van alle idempotentiesleutels.
- De dispatcher zoekt na een timeout eerst op idempotentiesleutel en maakt nooit blind een duplicaat.
- Een synchronisatiefout verandert de laatst bekende externe status niet, maar markeert haar als
  mogelijk verouderd.
- Een geblokkeerde kandidaat blijft met reden zichtbaar.

## Wanneer een processessie klaar is

Een intelligente sessie is klaar wanneer:

- selectie of vervolgstatus expliciet is vastgelegd;
- alle nieuwe stories, bugs, epicgaten en verificaties zijn verwerkt of uitgesteld;
- iedere story naar exact één bevroren epicversie verwijst;
- backlogvolgorde en epicstatus uitlegbaar en consistent zijn;
- voorraadstatus en `aanvullingNodig` juist zijn;
- output atomair is opgeslagen;
- de operationele sessiestatus en volgende plandatum zijn vastgelegd.
