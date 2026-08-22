# Product Factory v2 — Productontwerp

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt de module Productontwerp uit. De black-boxinterface in
[Product Factory v2 — eerste opzet](eerste-opzet.md) is leidend. Interne namen,
agentprompts en taakverdeling kunnen later veranderen zonder gevolgen voor andere modules.

## Verantwoordelijkheid

Productontwerp onderzoekt hoe een product zijn opdracht beter kan vervullen en zet de beste kansen
om in complete, behapbare epicdefinities. Iedere epic bevat een eenduidige gebruikersverbetering,
duidelijke scope, bewijs, een volledig actueel UX-ontwerp en succescriteria.

Productontwerp maakt geen stories en beheert geen backlog. De module is eigenaar van de complete
`Epic`: inhoud, UX, versie en levenscyclusstatus. Productplanning en Kwaliteitsbewaking kunnen de
epicstatus alleen via betekenisvolle commands van Productontwerp veranderen; zij krijgen nooit
schrijftoegang tot de epic of haar repository.

De module is eigenaar van:

- het actuele droombeeld en zijn versies;
- onderzoeksdossiers, bronnen, bewijsclaims en interne kansvoorstellen;
- epics, versies, scope, UX-ontwerp, succescriteria en epicstatus;
- leerresultaten over productrichting en gebruikerswaarde;
- het eigen agent- en procesgeheugen.

## Publieke module-interface

De enige agentgestuurde ingang is:

```java
void runProcessSession();
```

De scheduler of een handmatige UI-/REST-actie kan deze functie starten. De aanroep heeft geen
product-ID of epic-ID als argument: de module bepaalt zelf de belangrijkste planbare sessie. Er kan
modulebreed maximaal één ontwerpsessie tegelijk actief zijn. Een tweede handmatige aanroep krijgt
een `ProcessAlreadyRunning`-fout (bij REST bijvoorbeeld HTTP 409); een botsende scheduler-run wordt
als overgeslagen geregistreerd. Zonder planbaar werk eindigt de functie als succesvolle no-op.
Alleen `runProcessSession()` mag ontwerp- of onderzoeksagents starten.

Daarnaast heeft de module een kleine deterministische command- en query-interface:

```java
EpicDetails getEpic(EpicId epicId);
List<EpicDetails> findAvailableEpics(ProductId productId);
List<EpicDetails> findActiveEpics(ProductId productId);
void claimEpicForPlanning(ClaimEpicForPlanningCommand command);
void markEpicActive(MarkEpicActiveCommand command);
void markEpicReadyForVerification(MarkEpicReadyForVerificationCommand command);
void recordEpicVerification(RecordEpicVerificationCommand command);
void stopEpic(StopEpicCommand command);
```

Deze functies starten geen agents. Ze valideren bevoegdheid, verwachte versie en toegestane
statusovergang en schrijven de wijziging atomair op de eigen `Epic`. Andere modules kunnen geen
onderzoek of UX-stap starten en kunnen epicinhoud nooit via deze interface herschrijven.

## Interface met andere modules en services

Procesmodules gebruiken alleen elkaars publieke Spring Modulith-API. Queries leveren read-only DTO's
uit `processcontracts`; een DTO is geen tweede database-entiteit. Commands drukken een concrete
domeinovergang uit. Productontwerp schrijft uitsluitend zijn eigen tabellen.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `StakeholderDetails` | product-/overlegmodule | identiteit, contactwijze, rol en beslissingsmandaat van de Stakeholder |
| `ProductAssignmentDetails` | productmodule | doelgroep, productdoel, harde grenzen en publieke Git-URL van het product |
| `StakeholderDirectionDetails` | product-/overlegmodule | bindende richting en expliciete correcties |
| `UserSignalDetails` | productmodule | oorspronkelijke feedback plus actuele status, uitkomst en resultaatkoppelingen |
| `StoryDetails` | Productplanning-query | wat Software Factory heeft opgeleverd en welke story bij een epic hoort |
| `VerificationDetails` | Kwaliteitsbewaking-query | of de bedoelde gebruikersverbetering is bereikt en welk bewijs daarbij hoort |
| `QualityOverview` | Kwaliteitsbewaking-query | berekend beeld van productgezondheid en onderbelichte risicogebieden |

Voor iedere gelezen publicatie worden bron-ID en bronversie vastgelegd. Dezelfde versie wordt niet
tweemaal als nieuwe input behandeld.

Bij aanvang van een inhoudelijke sessie mag Productontwerp de publieke Git-URL uit de productopdracht
uitchecken en broncode, tests en documentatie read-only onderzoeken. Daarvoor is geen aparte
Product Factory-workspace of Git-module nodig. Productontwerp commit en pusht nooit; de gelezen
commit-SHA wordt alleen als bronverwijzing bij de sessie of epic vastgelegd. Ruwe bronnen steken de
modulegrens niet over.

Een `UserSignalDetails` is een aanwijzing en geen opdracht. De oorspronkelijke tekst blijft
onveranderlijk; status, verwerkingsuitkomst en koppelingen staan op dezelfde `UserSignal`. Als
Productontwerp een signaal verwerkt, roept het daarvoor een betekenisvol command op de productmodule
aan. Het krijgt nooit directe schrijftoegang tot het signaal.

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `DirectionSnapshot` | zichtbaar, geversioneerd droombeeld | toekomstverhaal, kernervaringen, obstakels, aannames, bewijsbasis en wijzigingsreden |
| `EpicDetails` | read-only weergave van één complete gebruikersverbetering | versie, status, probleem, doelgroep, uitkomst, scope in/uit, bewijs, UX, succescriteria, risico's, afhankelijkheden en bron-signaal-ID's |
| `ProcessSession` | operationele historie van de sessie | sessie-ID, product-ID, gebruikte inputversies, publicatie-ID's, eindstatus en blokkade |

Productontwerp schrijft `ProcessSession` uitsluitend voor zijn eigen sessies. De scheduler
roept de procesfunctie aan en de frontend leest het resultaat, maar geen van beide schrijft dit record.

De enige inhoudelijke overdracht naar Productplanning is `EpicDetails`. Het droombeeld is
zichtbare productrichting, maar geen uitvoerbaar werk. `LearningResult` blijft intern binnen
Productontwerp. Wanneer daar een concrete keuze uit volgt, laat Productontwerp die keuze als
`DecisionRecord` vastleggen door het
[Besluitenregister](besluitenregister.md); het volledige leerresultaat wordt niet
gepubliceerd.

Alleen Productontwerp schrijft de `Epic`. Productplanning claimt een exacte versie via
`claimEpicForPlanning(...)`; dat command bevriest die versie atomair. Kwaliteitsbewaking registreert
de afsluitende uitkomst via `recordEpicVerification(...)`. Geen van beide krijgt toegang tot de
epicrepository of kan inhoud en UX veranderen.

Na het atomair publiceren van een nieuwe beschikbare epic roept Productontwerp idempotent
`requestEpicPlanning(epicId, epicVersion, ...)` op Productplanning aan. Dat command start geen
planner en maakt geen stories: het zet alleen een duurzaam `PlanningWorkItem` met type `PLAN_EPIC`
in de planningsqueue. Een latere planningsrun pakt het werk op.

`markEpicReadyForVerification(...)` is uitsluitend een gevalideerde statusovergang naar
**Controleren** (`VERIFYING`). Productplanning roept daarna
`requestEpicVerification(epicId, epicVersion, ...)` op Kwaliteitsbewaking aan. Ook dat command start
geen agents, maar plaatst een `QualityWorkItem` in de kwaliteitsqueue.

## Epiccontract

Een beschikbare epicdefinitie bevat minimaal:

- stabiel epic-ID, product-ID en versienummer;
- voor welke gebruiker en situatie de epic bedoeld is;
- het aantoonbare probleem of de kans;
- de merkbare gewenste gebruikersverbetering;
- expliciete scope: wat hoort er wel en niet bij;
- relatie met productdoel en droombeeld;
- bewijs, bronnen, aannames en relevante tegenspraak;
- het volledige actuele UX-ontwerp;
- hoofdroute, alternatieve routes en schermtoestanden;
- lege, laad-, fout- en uitzonderingssituaties;
- toegankelijkheids-, privacy- en kwaliteitsgrenzen;
- bekende technische risico's en afhankelijkheden;
- succescriteria en hoe Kwaliteitsbewaking die later kan toetsen;
- waarom de epic behapbaar genoeg is om in kleine stories te verdelen.

Productontwerp beschrijft geen storylijst. Een mogelijke slice mag de behapbaarheid uitleggen, maar
is geen vooraf geschreven backlog.

## Versies en bevriezing

Iedere gepubliceerde epicversie is inhoudelijk onveranderlijk.

Zolang de epicstatus **Beschikbaar** is, mag Productontwerp:

- een nieuwe versie publiceren;
- de vorige versie als **Vervangen** markeren;
- een beschikbare epic intrekken met een zichtbare reden.

Zodra `claimEpicForPlanning(...)` een exact epic-ID en versienummer heeft gekozen:

- wordt die versie het vaste uitvoerings- en testcontract;
- mag Productontwerp geen nieuwe versie van dezelfde gekozen epic publiceren;
- blijven scope en UX van de gekozen versie ongewijzigd;
- wordt nieuwe kennis een vervolgepic of een voorstel om de uitvoering te stoppen;
- kan een vervangende richting alleen via een nieuw gekozen epic-ID of een vooraf gepubliceerde,
  nog niet gekozen epicversie lopen.

De module controleert deze regel en het verwachte versienummer in dezelfde transactie op de `Epic`.
Zo kan een langlopende ontwerpsessie niet alsnog een inmiddels geclaimde epic overschrijven.

## Duurzame en interne entiteiten

De volgende entiteiten blijven binnen de module:

- `Epic` — gepubliceerde gebruikersverbetering, inhoudelijke versie en levenscyclusstatus;
- `DirectionSnapshot` — geversioneerde productrichting;
- `ProcessSession` — de geclaimde, begrensde uitvoering en haar operationele historie;
- `SessionAgenda` — gekozen ontwerp- of onderzoekstaak en budget;
- `ResearchDossier` — één onderzoeksvraag met voortgang en conclusie;
- `SourceRecord` — vindplaats, datum, brontype en toegangsvoorwaarden;
- `EvidenceClaim` — bewering met ondersteunende en tegensprekende bronnen;
- `Hypothesis` — nog onbewezen verklaring of kans;
- `LearningResult` — intern gevalideerde conclusie met reikwijdte, geldigheid en gevolgen voor een volgende ontwerpsessie;
- `SignalAssessment` — interne beoordeling van een ongewijzigd gebruikerssignaal;
- `OpportunityCandidate` — intern kansvoorstel vóór epicvorming;
- `DirectionDraft` — niet-gepubliceerde droombeeldvariant;
- `EpicDraft` — epicdefinitie vóór publicatie;
- `UxDesign` — gebruikersroutes, toestanden, ontwerpassets en open vragen;
- `TechnicalExploration` — haalbaarheid, afhankelijkheden en risico's;
- `EpicReadinessAssessment` — controle of een epic overdraagbaar en behapbaar is;
- `ProductDesignMemory` — gedeelde lessen over onderzoek, UX en productkeuzes;
- `AgentRun` — input, promptversie, output, fout en verbruik van één agenttaak.

Onderzoeksvragen, kansvoorstellen, leerresultaten en conceptontwerpen zijn interne objecten. Alleen
het gevalideerde droombeeld en de `Epic` worden als inhoudelijke procesoutput gepubliceerd.
Een betekenisvolle keuze wordt daarnaast in het Besluitenregister vastgelegd zonder het interne
onderzoeksdossier te kopiëren.

## Levenscyclus van een epic

- **Concept** — alleen intern zichtbaar;
- **Onderzoeken** — probleem, bewijs en alternatieven worden onderzocht;
- **Ontwerpen** — scope, UX, techniek en succescriteria worden uitgewerkt;
- **Beschikbaar** — complete versie die Productplanning mag claimen;
- **In planning** — exact deze versie is geclaimd en wordt in stories verdeeld;
- **Actief** — één of meer stories of bugfixes worden uitgevoerd;
- **Controleren** — alle bekende stories zijn geleverd en de hele verbetering wordt getoetst;
- **Geslaagd**, **Niet geslaagd** of **Gestopt** — eindstatus met reden en eventuele verificatie-ID;
- **Vervangen** — er is vóór selectie een nieuwere versie gepubliceerd;
- **Ingetrokken** — bewust niet meer beschikbaar, met reden.

## Agents

Een volledige processessie kan zeven vaste agentrollen gebruiken:

1. **Productontwerpleider** — kiest de agenda, bewaakt het productdoel en integreert de uitkomst.
2. **Product- en marktonderzoeker** — onderzoekt vergelijkbare producten en alternatieven.
3. **Gebruikers- en probleemonderzoeker** — onderzoekt signalen, taken en gewenste resultaten.
4. **Toekomstonderzoeker** — zoekt nieuwe techniek en oplossingen uit aangrenzende domeinen.
5. **UX-ontwerper** — maakt de volledige gebruikersroute, toestanden en ontwerpassets.
6. **Technisch verkenner** — onderzoekt haalbaarheid, afhankelijkheden en risico's zonder de bouw
   voor Software Factory voor te schrijven.
7. **Epiccriticus** — controleert bewijs, scope, UX-volledigheid, gebruikerswaarde en behapbaarheid.

Niet iedere sessie start alle zeven agents. Een kleine herziening van een nog niet gekozen epic kan
volstaan met de leider, UX-ontwerper, technisch verkenner en criticus. Een nieuwe richting gebruikt
alle rollen.

## Soorten processessies

De module kiest precies één hoofdsoort per aanroep:

1. **Richting onderzoeken** — nieuw bewijs verzamelen en het droombeeld herijken.
2. **Kans onderzoeken** — een signaal of hypothese tot een beslisbare kandidaat maken.
3. **Epic ontwerpen** — probleem, scope, UX, techniek en succescriteria uitwerken.
4. **Epic herzien** — alleen een nog niet gekozen epic als nieuwe versie publiceren.
5. **Resultaat verwerken** — epicverificatie of kwaliteitspatroon in nieuwe productkennis of een
   vervolgepic verwerken.

Een sessie begint niet onbeperkt aan een tweede groot onderwerp. Open werk wordt duurzaam bewaard
voor een volgende geplande sessie.

## Verloop van één processessie

```text
claim product en inputmomentopname
                 │
                 ▼
Productontwerpleider kiest hoofdtaak
                 │
       ┌─────────┼─────────┐
       ▼         ▼         ▼
  product-    gebruiker-  toekomst-
  onderzoek   onderzoek   onderzoek
       └─────────┼─────────┘
                 ▼
      synthese en kansselectie
                 │
       ┌─────────┴─────────┐
       ▼                   ▼
    UX-ontwerp      technische verkenning
       └─────────┬─────────┘
                 ▼
          epicdefinitie
                 │
                 ▼
            Epiccriticus
                 │
          akkoord of herstel
                 │
                 ▼
       publiceer exacte versie
```

De drie onderzoeksagents werken parallel op dezelfde vastgezette inputmomentopname. UX en techniek
werken daarna parallel zodra probleem, doelgroep en gewenste uitkomst voldoende stabiel zijn. De
Epiccriticus werkt sequentieel na de synthese. Bij herstelbare tekortkomingen is één gerichte
herstelronde toegestaan.

### Stap 1 — claimen en agenda kiezen

De module:

1. claimt één planbare sessie met een database-lock;
2. leest de exacte inputversies;
3. controleert eerst of een betrokken epic inmiddels door Productplanning is gekozen;
4. bepaalt urgentie op basis van nieuwe kennis, kwaliteitssignalen, beschikbare epics en geldende
   Stakeholderrichting;
5. kiest één hoofdtaak en een begrensd tijd-/tokenbudget;
6. controleert toegangsrechten en harde productgrenzen.

### Stap 2 — onderzoeken en bewijs vormen

Alle belangrijke beweringen wijzen naar een bron, gebruikerssignaal, leveringsresultaat of
epicverificatie. Feiten, meningen en hypotheses blijven apart. Duplicaten worden gekoppeld zonder de
originele signalen samen te overschrijven en tegenspraak blijft zichtbaar.

### Stap 3 — epic en UX ontwerpen

De UX-ontwerper en technisch verkenner werken vanuit hetzelfde probleem, dezelfde scope en dezelfde
gewenste uitkomst. Het UX-ontwerp is onderdeel van de epicversie en geen los document zonder eigenaar.

De epic moet scherp genoeg zijn dat Productplanning haar volledig in zelfstandig leverbare stories
kan verdelen. Het aantal stories is geen ontwerpcriterium: een heldere epic kan twee stories vragen
of dertig. De scope moet wel één eenduidige gebruikersverbetering vormen en behapbaar blijven voor
planning, uitvoering en verificatie.

### Stap 4 — kritiek en atomair publiceren

De Epiccriticus controleert minimaal:

- één duidelijke gebruikersverbetering;
- eenduidige scope in en uit;
- voldoende bewijs en zichtbare onzekerheid;
- complete hoofdroute en belangrijke UX-toestanden;
- testbare succescriteria;
- toegankelijkheid, privacy, veiligheid en onderhoudslast;
- aannemelijke technische uitvoerbaarheid;
- behapbaarheid voor Productplanning;
- afwezigheid van vooraf geschreven stories;
- dat de epic nog niet door Productplanning is gekozen.

Goedgekeurde output wordt atomair in de eigen entiteiten opgeslagen en daarna wordt de sessiestatus
afgerond. Query-DTO's worden uit die entiteiten opgebouwd en hebben geen eigen opslag. Na publicatie
vraagt Productontwerp via `requestEpicPlanning(...)` idempotent planning voor exact die epicversie
aan. Betekenisvolle keuzes leveren een idempotent registratieverzoek aan het Besluitenregister. Als
een gebruikerssignaal is beoordeeld zonder dat een epic ontstaat, bevat dat besluit het signaal-ID
en de uitkomst. Productontwerp roept daarna het passende signaalcommand op de productmodule aan.

## Wanneer Productontwerp draait

Een sessie wordt planbaar door:

- een gewijzigd Stakeholderprofiel of een nieuwe of gewijzigde Stakeholderrichting,
  gebruikerssignaal of signaalstatus;
- een nieuwe epicverificatie of nieuw intern leerresultaat dat nog om een ontwerpkeuze vraagt;
- een structureel kwaliteitspatroon als gebruikerssignaal;
- een periodieke onderzoeks- of epiccontrole.

Productontwerp heeft bewust geen inkomende werkqueue. De scheduler of een bevoegde handmatige
aanroep start een sessie; de module kiest daarna zelf haar ontwerpwerk uit bovenstaande input. De
toestand van de backlog is geen startsein. Productontwerp maakt uitsluitend complete epics en nooit
stories.

## Fouten, hervatten en idempotentie

- Publieke output verschijnt alleen na complete validatie.
- Interne concepten kunnen na een fout worden hervat, maar zijn geen productwaarheid.
- Dezelfde bronversie en sessiedoelstelling leveren niet tweemaal dezelfde publicatie op.
- Een verlopen claim kan veilig opnieuw worden opgepakt met dezelfde inputmomentopname.
- Een epic die tijdens de sessie wordt gekozen, faalt bij publicatie gesloten en wordt niet herzien.
- Een wijziging van input tijdens een sessie wordt in een volgende sessie verwerkt.

## Wanneer een sessie klaar is

Een sessie is klaar wanneer:

- de gekozen hoofdtaak een resultaat, expliciete blokkade of gemotiveerde vervolgstatus heeft;
- alle conclusies herleidbaar zijn tot bronnen en inputversies;
- iedere gepubliceerde epic door de Epiccriticus is goedgekeurd;
- iedere epic zelfstandig door Productplanning kan worden begrepen;
- de publicatiecontrole bewijst dat de epicversie nog niet is gekozen;
- ieder betekenisvol product- of signaalbesluit idempotent aan het Besluitenregister is aangeboden;
- output atomair en geversioneerd beschikbaar is;
- de operationele sessiestatus en volgende plandatum zijn opgeslagen.
