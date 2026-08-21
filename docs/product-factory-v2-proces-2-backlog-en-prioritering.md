# Product Factory v2 — proces 2: Backlog en prioritering

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt de module Backlog en prioritering uit, inclusief de technische
`SoftwareFactoryDispatcher`. De black-boxinterface in
[Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md) is leidend.

## Verantwoordelijkheid

Proces 2 onderhoudt voor ieder actief product één uitlegbaar geprioriteerde backlog. Voor HKH staan
normaal ongeveer tien verzendbare items klaar. Een backlogitem verwijst naar precies één uitvoerbare
productstory uit proces 1 of één uitvoerbare bug uit proces 3.

De module is eigenaar van:

- backlogitems en hun volgorde;
- prioriteitsbesluiten en de gebruikte bronversies;
- de backlogvoorraad en de vlag `aanvullingNodig`;
- de koppeling tussen backlogitem en extern Software Factory-item;
- de uitvoeringsstatus van backlogitems;
- de technische dispatcher en synchronisatiehistorie;
- het eigen agent- en procesgeheugen.

De module wijzigt geen productstory, epic, bug of verificatieresultaat. Zij bewaart alleen een
verwijzing naar de bron en de versie waarop een besluit is gebaseerd.

## Publieke procesfunctie

De enige intelligente uitvoerende ingang is:

```java
void runProcessSession();
```

Alleen de scheduler gebruikt deze functie. De module claimt zelf atomair één product waarvoor de
backlog moet worden aangevuld, herordend of bijgewerkt. Per product kan maximaal één processessie
tegelijk actief zijn. Zonder planbaar werk eindigt de functie als succesvolle no-op.

De functie verstuurt niets naar Software Factory. Verzending en statussynchronisatie zijn technisch
werk van de dispatcher en staan buiten de agentgestuurde processessie.

## Interface met andere modules en services

Proces 2 gebruikt de technische module `processcontracts` voor geversioneerde read-only
projecties. Het importeert proces 1 of 3 niet rechtstreeks.

### Input

| Contract | Eigenaar | Hoe verkregen | Gebruik |
|---|---|---|---|
| `ProductAssignmentView` | productmodule | read-only query | productidentiteit, grenzen en backlogconfiguratie |
| `StakeholderDirectionView` | product-/overlegmodule | actieve publicaties | expliciete prioriteitsgrenzen en correcties |
| `EpicView` | proces 1 | nieuwste gepubliceerde versie | productwaarde, epicvolgorde, afhankelijkheden en gewenste uitkomst |
| `ProductStoryView` | proces 1 | stories met status **Uitvoerbaar** | kandidaat voor een productstory-backlogitem |
| `BugView` | proces 3 | bugs met status **Uitvoerbaar** | kandidaat voor een bugfix-backlogitem inclusief ernst en bewijs |
| `VerificationResultView` | proces 3 | resultaten sinds vorige sessie | bepaalt of opgeleverd werk afgerond, afgekeurd of opnieuw gepland wordt |
| `SoftwareFactoryWorkView` | dispatcher | gesynchroniseerde externe status | voorkomt dubbel verzenden en toont bezig, opgeleverd of geblokkeerd werk |

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `PrioritizedBacklogView` | actuele geordende lijst voor één product | backlogversie, geordende item-ID's, redenen en aanmaakmoment |
| `BacklogItemView` | één verzendbare of reeds verzonden opdracht | type, bron-ID en -versie, positie, prioriteitsreden, status en externe referentie |
| `PriorityDecisionView` | uitlegbare keuze achter de ordening | gekozen item, relevante alternatieven, criteria, bronversies en beslisser |
| `BacklogSupplyView` | voorraadstatus voor scheduler en proces 1 | aantallen per status, lage grens, streefpeil en `aanvullingNodig` |
| `DeliveryResultView` | genormaliseerde terugmelding uit Software Factory | extern ID, backlogitem, status, opleverlocatie, tijdstippen en foutinformatie |
| `ProcessSessionPublication` | operationeel resultaat van de processessie | sessie-ID, product-ID, gebruikte inputversies, wijzigingen en eindstatus |

Alleen proces 2 schrijft backlogvolgorde en backlogstatus. De dispatcher draait binnen dezelfde
module en mag uitsluitend de leveringsvelden en de bijbehorende statusovergangen schrijven.

## Backlogcontract

Een backlogitem heeft minimaal:

- een stabiel backlogitem-ID en product-ID;
- type `PRODUCT_STORY` of `BUGFIX`;
- bron-ID, bronmodule en bronversie;
- titel en korte gewenste uitkomst uit de gepubliceerde bron;
- prioriteitspositie en begrijpelijke reden;
- relevante afhankelijkheden;
- status;
- eventueel extern Software Factory-ID;
- aanmaak-, wijzigings-, verzend- en oplevertijd;
- laatste gesynchroniseerde externe status.

De combinatie van product, brontype, bron-ID en bronversie is uniek. Een nieuwere bronversie leidt
tot herbeoordeling, niet automatisch tot een tweede backlogitem.

Een backlogitem gebruikt deze statussen:

- **Verzendbaar** — compleet, geprioriteerd en nog niet extern aangemaakt;
- **Verstuurd** — door de dispatcher in Software Factory aangemaakt;
- **Bezig** — Software Factory meldt actieve uitvoering;
- **Opgeleverd** — Software Factory heeft resultaat teruggegeven;
- **Controleren** — proces 3 moet het resultaat nog verifiëren;
- **Afgerond** — proces 3 heeft het resultaat goedgekeurd;
- **Geblokkeerd** — kan niet verder, met zichtbare reden;
- **Gestopt** — bewust niet verder, met zichtbare reden.

## Interne entiteiten

De volgende entiteiten blijven binnen proces 2:

- `BacklogSession` — de geclaimde intelligente processessie;
- `CandidateSet` — uitvoerbare bronnen die nog niet of opnieuw moeten worden beoordeeld;
- `PriorityAssessment` — vergelijking per criterium;
- `BacklogDraft` — voorgestelde ordening vóór kritiek;
- `Backlog` — actuele duurzame backlog per product;
- `BacklogItem` — lokale verwijzing en uitvoeringsstatus;
- `PriorityDecision` — auditregel voor een plaatsings- of herordeningsbesluit;
- `SupplyState` — verzendbare voorraad, lage grens en streefpeil;
- `DispatchAttempt` — één idempotente verzendpoging;
- `ExternalWorkLink` — koppeling met Software Factory;
- `DeliverySync` — resultaat van een statuspoll;
- `BacklogMemory` — lessen over balans, blokkades en eerdere prioriteitscorrecties;
- `AgentRun` — input, promptversie, output en fout van één agenttaak.

## Agents

De intelligente processessie gebruikt twee agentrollen:

1. **Product Owner** — vergelijkt bronnen, vult de backlog en stelt de volledige volgorde voor.
2. **Prioriteitscriticus** — controleert urgentie, bewijs, afhankelijkheden, balans, verborgen
   score-effecten en de begrijpelijkheid van iedere reden.

De agents werken sequentieel. De Product Owner moet eerst één coherent backlogvoorstel maken; de
criticus beoordeelt daarna het voorstel als geheel. Bij herstelbare tekortkomingen krijgt de Product
Owner één gerichte herstelronde. De dispatcher gebruikt geen agent.

## Verloop van één processessie

```text
claim product en inputmomentopname
                 │
                 ▼
verwerk nieuwe stories, bugs en verificaties
                 │
                 ▼
Product Owner maakt backlogvoorstel
                 │
                 ▼
prioriteitscriticus controleert
                 │
          akkoord of herstel
                 │
                 ▼
publiceer backlog + voorraadstatus
```

### Stap 1 — claimen en invoer normaliseren

De module:

1. claimt één planbaar product met een database-lock;
2. zet de exacte versies van stories, bugs, epics, richtingen en verificaties vast;
3. verwijdert kandidaten die zijn ingetrokken, vervangen of niet langer uitvoerbaar zijn;
4. voorkomt duplicaten op basis van de bronidentiteit;
5. verwerkt verificaties in de lokale backlogstatus.

Een ingetrokken bron wordt niet stilletjes verwijderd wanneer het item al naar Software Factory is
verstuurd. Het item wordt gemarkeerd en vraagt zo nodig om een expliciet stopbesluit.

### Stap 2 — prioriteren

De Product Owner vergelijkt minimaal:

- P0–P3-ernst van bugs;
- gebruikerswaarde en urgentie;
- bijdrage aan actieve epic, productdoel en droombeeld;
- bewijssterkte en leerwaarde;
- afhankelijkheden en blokkades;
- risico en omkeerbaarheid;
- verwachte hoeveelheid werk;
- reeds openstaand werk in Software Factory;
- balans tussen vernieuwing en productgezondheid.

Een score mag informatie ordenen, maar de opgeslagen prioriteitsreden moet zelfstandig leesbaar zijn.
P0 staat bovenaan de eerstvolgende verzendbare positie. De dispatcher start geen tweede parallel item
zolang Software Factory nog openstaand werk voor hetzelfde product meldt.

### Stap 3 — vullen tot het streefpeil

Voor HKH gelden aanvankelijk:

- lage grens: vier verzendbare backlogitems;
- streefpeil: tien verzendbare backlogitems;
- maximaal één extern openstaand item.

Proces 2 neemt de beste beschikbare kandidaten op tot het streefpeil is bereikt of er geen voldoende
goede bron meer beschikbaar is. Het verzint geen stories en schrijft geen bugs om het getal tien te
halen. Als minder dan tien goede items bestaan, blijft de werkelijke voorraad zichtbaar.

`aanvullingNodig` wordt waar bij vier of minder verzendbare items en pas weer onwaar wanneer het
streefpeil van tien is bereikt. Deze hysterese voorkomt dat de scheduler rond de grens blijft
pendelen.

### Stap 4 — kritiek en publiceren

De criticus controleert onder meer:

- of ieder item naar één geldige bronversie verwijst;
- of alle afhankelijkheden in een uitvoerbare volgorde staan;
- of bugs de vernieuwing niet zonder reden verdringen;
- of een actieve epic niet alle productgezondheid verdringt;
- of recente Stakeholderrichting correct is verwerkt;
- of de eerste tien posities ieder een concrete reden hebben;
- of geen intern detail uit proces 1 of 3 nodig is om de keuze te begrijpen.

De goedgekeurde backlog, besluiten en voorraadstatus worden in één transactie gepubliceerd.

## SoftwareFactoryDispatcher

De dispatcher is een geplande technische adapter binnen proces 2. Hij is geen productproces, heeft
geen geheugen en gebruikt geen agents. Zijn technische ingang is:

```java
void runDispatchSession();
```

Deze functie is niet beschikbaar voor andere procesmodules. Alleen de scheduler roept hem aan.

### Invoer van de dispatcher

- de actuele backlog en lokale uitvoeringsstatus uit proces 2;
- product- en Software Factory-configuratie;
- de via de Software Factory-client opgehaalde externe items en statussen.

### Uitvoer van de dispatcher

- bijgewerkte leveringsvelden op het lokale backlogitem;
- een `ExternalWorkLink` met het externe Software Factory-ID;
- een genormaliseerde `DeliveryResultView`;
- een `DispatchAttempt` met succes, fout of idempotente herhaling.

### Algoritme

Iedere dispatchersessie doet exact dit:

1. claim één product voor synchronisatie;
2. haal alle door Product Factory aangemaakte open of recent gewijzigde Software Factory-items op;
3. werk de lokale status en tijdstippen bij;
4. markeer een extern opgeleverd item lokaal als **Opgeleverd**, niet direct als **Afgerond**;
5. controleer of Software Factory nog een openstaand item voor dit product heeft;
6. zo ja: verstuur niets nieuws en sluit de sessie;
7. zo nee: selecteer het bovenste backlogitem met status **Verzendbaar** waarvan afhankelijkheden
   klaar zijn;
8. maak precies één story in Software Factory aan met een idempotentiesleutel;
9. bewaar het externe ID en zet het backlogitem op **Verstuurd**;
10. publiceer de nieuwe voorraad- en leveringsstatus.

Proces 3 verifieert een oplevering. Pas een geslaagd `VerificationResultView` laat de volgende
intelligente processessie het backlogitem **Afgerond** maken. Bij afkeuring blijft het item
**Controleren** of wordt een nieuwe bug gepubliceerd; de dispatcher neemt daarover geen besluit.

### Inhoud van de Software Factory-story

De dispatcher stuurt een volledige momentopname zodat Software Factory geen Product Factory-module
hoeft te raadplegen:

- backlogitem-ID en bronreferentie;
- type productstory of bugfix;
- titel, gewenste uitkomst en context;
- acceptatiecriteria of reproduceerstappen;
- afhankelijkheden;
- relevante UX-, bewijs- en technische verwijzingen;
- productrepository en toegestane uitvoeringsomgeving;
- callback- of correlatie-ID voor statuskoppeling.

## Planning

Een intelligente processessie wordt planbaar door:

- een nieuwe of gewijzigde uitvoerbare productstory;
- een nieuwe of gewijzigde uitvoerbare bug;
- een verificatieresultaat;
- gewijzigde Stakeholderrichting;
- een backlogvoorraad op of onder de lage grens;
- een periodieke herprioritering.

De dispatcher draait vaker en onafhankelijk van de intelligente processessie. Zijn interval is kort
genoeg om een volgend item snel te versturen nadat Software Factory vrij komt.

## Fouten, hervatten en idempotentie

- Backlogpublicatie is atomair en geversioneerd.
- Een sessie gebruikt één vastgezette inputmomentopname.
- De dispatcher gebruikt de backlogitem-ID en bronversie als idempotentiesleutel bij Software Factory.
- Een timeout na verzending leidt eerst tot opzoeken op idempotentiesleutel, nooit direct tot opnieuw
  aanmaken.
- Een synchronisatiefout verandert de laatst bekende externe status niet, maar markeert haar als
  mogelijk verouderd.
- Een geblokkeerde kandidaat blijft met reden zichtbaar en wordt niet telkens opnieuw toegevoegd.

## Wanneer een processessie klaar is

Een intelligente sessie is klaar wanneer:

- alle nieuwe of gewijzigde kandidaten zijn verwerkt of expliciet uitgesteld;
- de backlogvolgorde compleet en uitlegbaar is;
- iedere plaatsing naar exacte bronversies verwijst;
- de voorraadstatus en `aanvullingNodig` juist zijn;
- backlog, besluiten en publicaties atomair zijn opgeslagen;
- de operationele sessiestatus en volgende plandatum zijn vastgelegd.
