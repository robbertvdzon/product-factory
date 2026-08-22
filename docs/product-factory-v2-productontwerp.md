# Product Factory v2 — Productontwerp

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt de module Productontwerp uit. De black-boxinterface in
[Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md) is leidend. Interne namen,
agentprompts en taakverdeling kunnen later veranderen zonder gevolgen voor andere modules.

## Verantwoordelijkheid

Productontwerp onderzoekt hoe een product zijn opdracht beter kan vervullen en zet de beste kansen
om in complete, behapbare epicdefinities. Iedere epic bevat een eenduidige gebruikersverbetering,
duidelijke scope, bewijs, een volledig actueel UX-ontwerp en succescriteria.

Productontwerp maakt geen stories, beheert geen backlog en verandert geen epicuitvoering. De module
levert de bouwtekening; Productplanning kiest een exacte versie en maakt daar uitvoerbaar werk van.

De module is eigenaar van:

- het actuele droombeeld en zijn versies;
- onderzoeksdossiers, bronnen, bewijsclaims en interne kansvoorstellen;
- epicdefinities, versies, scope, UX-ontwerp en succescriteria;
- leerresultaten over productrichting en gebruikerswaarde;
- het eigen agent- en procesgeheugen.

## Publieke procesfunctie

De enige agentgestuurde ingang is:

```java
void runProcessSession();
```

Alleen de scheduler gebruikt deze functie. De aanroep heeft geen product-ID of epic-ID als argument.
De module claimt zelf atomair de belangrijkste planbare sessie. Per product kan maximaal één sessie
van Productontwerp tegelijk actief zijn. Zonder planbaar werk eindigt de functie als succesvolle
no-op.

Andere modules kunnen geen onderzoek starten, geen UX-stap aanroepen en geen epic laten herschrijven.
Zij publiceren alleen gegevens die Productontwerp tijdens een volgende sessie kan lezen.

## Interface met andere modules en services

De procesmodules importeren elkaar niet. Stabiele DTO's, read-only queryports en geversioneerde
databaseprojecties staan in de technische module `processcontracts`. Productontwerp schrijft
uitsluitend zijn eigen publicaties en leest alleen publicaties van andere eigenaren.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `StakeholderProfileView` | product-/overlegmodule | identiteit, contactwijze, rol en beslissingsmandaat van de Stakeholder |
| `ProductAssignmentView` | productmodule | doelgroep, productdoel, harde grenzen, repository en toegestane toegang |
| `StakeholderDirectionView` | product-/overlegmodule | bindende richting en expliciete correcties |
| `UserSignalView` | productmodule | oorspronkelijke feedback, observatie of gebruiksgegeven met bron, context en bewijs |
| `UserSignalDispositionView` | productmodule | afgeleide status, bestaande koppelingen en wat al met het signaal is gebeurd |
| `BacklogSupplyView` | Productplanning | of nieuwe beschikbare epics extra urgent zijn |
| `EpicExecutionView` | Productplanning | welke exacte epicversies zijn gekozen en dus niet meer gewijzigd mogen worden |
| `DeliveryResultView` | Software Factory-dispatcher | wat Software Factory werkelijk heeft opgeleverd |
| `EpicVerificationView` | Kwaliteitsbewaking | of de bedoelde gebruikersverbetering is bereikt |
| `QualitySignalView` | Kwaliteitsbewaking | terugkerende problemen en onjuiste productaannames |
| `QualityOverviewView` | Kwaliteitsbewaking | productgezondheid en onderbelichte risicogebieden |

Voor iedere gelezen publicatie worden bron-ID en bronversie vastgelegd. Dezelfde versie wordt niet
tweemaal als nieuwe input behandeld.

Externe onderzoeksbronnen en repository-informatie worden binnen de toegestane productgrenzen door
de module zelf opgehaald. Ruwe bronnen steken de modulegrens niet over.

Een `UserSignalView` is een onbewerkte aanwijzing en geen opdracht. Productontwerp behoudt de
originele tekst, maakt eigen onderzoek of productoutput en neemt het signaal-ID daarin als bron op.
De productmodule leidt daaruit `UserSignalDispositionView` af; Productontwerp schrijft geen
status op het oorspronkelijke signaal.

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `DirectionSnapshot` | zichtbaar, geversioneerd droombeeld | toekomstverhaal, kernervaringen, obstakels, aannames, bewijsbasis en wijzigingsreden |
| `EpicDefinitionView` | complete bouwtekening voor één gebruikersverbetering | versie, probleem, doelgroep, uitkomst, scope in/uit, bewijs, UX, succescriteria, risico's, afhankelijkheden en bron-signaal-ID's |
| `LearningResultView` | gevalideerde nieuwe productkennis | conclusie, bron, reikwijdte, geldigheid, bron-signaal-ID's en effect op droombeeld of toekomstige epics |
| `ProcessSessionPublication` | operationeel resultaat van de sessie | sessie-ID, product-ID, gebruikte inputversies, publicatie-ID's, eindstatus en blokkade |

Productontwerp schrijft `ProcessSessionPublication` uitsluitend voor zijn eigen sessies. De scheduler
roept de procesfunctie aan en de frontend leest het resultaat, maar geen van beide schrijft dit record.

De enige inhoudelijke overdracht naar Productplanning is `EpicDefinitionView`. Het droombeeld en
leerresultaten zijn productkennis voor de Stakeholder en latere ontwerpsessies, geen uitvoerbaar werk.

Alleen Productontwerp schrijft de epicdefinitie. Productplanning kiest een exacte gepubliceerde
versie, maar wijzigt de inhoud niet. Kwaliteitsbewaking gebruikt diezelfde versie als contract voor
de latere epiccontrole.

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

Iedere `EpicDefinitionView` is na publicatie onveranderlijk.

Zolang er geen `EpicExecutionView` naar een epic verwijst, mag Productontwerp:

- een nieuwe versie publiceren;
- de vorige versie als **Vervangen** markeren;
- een beschikbare epic intrekken met een zichtbare reden.

Zodra Productplanning een exact epic-ID en versienummer heeft gekozen:

- wordt die versie het vaste uitvoerings- en testcontract;
- mag Productontwerp geen nieuwe versie van dezelfde gekozen epic publiceren;
- blijven scope en UX van de gekozen versie ongewijzigd;
- wordt nieuwe kennis een vervolgepic of een voorstel om de uitvoering te stoppen;
- kan een vervangende richting alleen via een nieuw gekozen epic-ID of een vooraf gepubliceerde,
  nog niet gekozen epicversie lopen.

De module controleert deze regel vóór iedere publicatie tegen `EpicExecutionView`. Zo kan een
langlopende ontwerpsessie niet alsnog een inmiddels gekozen epic overschrijven.

## Interne entiteiten

De volgende entiteiten blijven binnen de module:

- `ProductDesignSession` — de geclaimde, begrensde uitvoering;
- `SessionAgenda` — gekozen ontwerp- of onderzoekstaak en budget;
- `ResearchDossier` — één onderzoeksvraag met voortgang en conclusie;
- `SourceRecord` — vindplaats, datum, brontype en toegangsvoorwaarden;
- `EvidenceClaim` — bewering met ondersteunende en tegensprekende bronnen;
- `Hypothesis` — nog onbewezen verklaring of kans;
- `SignalAssessment` — interne beoordeling van een ongewijzigd gebruikerssignaal;
- `OpportunityCandidate` — intern kansvoorstel vóór epicvorming;
- `DirectionDraft` — niet-gepubliceerde droombeeldvariant;
- `EpicDraft` — epicdefinitie vóór publicatie;
- `UxDesign` — gebruikersroutes, toestanden, ontwerpassets en open vragen;
- `TechnicalExploration` — haalbaarheid, afhankelijkheden en risico's;
- `EpicReadinessAssessment` — controle of een epic overdraagbaar en behapbaar is;
- `ProductDesignMemory` — gedeelde lessen over onderzoek, UX en productkeuzes;
- `AgentRun` — input, promptversie, output, fout en verbruik van één agenttaak.

Onderzoeksvragen, kansvoorstellen en conceptontwerpen zijn interne objecten. Alleen het gevalideerde
droombeeld, de epicdefinitie en leerresultaten worden gepubliceerd.

## Interne levenscyclus van een epicdefinitie

- **Concept** — alleen intern zichtbaar;
- **Onderzoeken** — probleem, bewijs en alternatieven worden onderzocht;
- **Ontwerpen** — scope, UX, techniek en succescriteria worden uitgewerkt;
- **Beschikbaar** — complete versie die Productplanning mag kiezen;
- **Vervangen** — er is vóór selectie een nieuwere versie gepubliceerd;
- **Ingetrokken** — bewust niet meer beschikbaar, met reden.

**Geselecteerd**, **Actief**, **Controleren** en eindstatussen horen niet bij de epicdefinitie. Die
staan op de epicuitvoering van Productplanning.

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
5. **Resultaat verwerken** — epicverificatie of kwaliteitssignaal in nieuwe productkennis of een
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
4. bepaalt urgentie op basis van nieuwe kennis, kwaliteitssignalen, beschikbare epics en
   backlogvoorraad;
5. kiest één hoofdtaak en een begrensd tijd-/tokenbudget;
6. controleert toegangsrechten en harde productgrenzen.

### Stap 2 — onderzoeken en bewijs vormen

Alle belangrijke beweringen wijzen naar een bron, gebruikerssignaal, leveringsresultaat of
epicverificatie. Feiten, meningen en hypotheses blijven apart. Duplicaten worden gekoppeld zonder de
originele signalen samen te overschrijven en tegenspraak blijft zichtbaar.

### Stap 3 — epic en UX ontwerpen

De UX-ontwerper en technisch verkenner werken vanuit hetzelfde probleem, dezelfde scope en dezelfde
gewenste uitkomst. Het UX-ontwerp is onderdeel van de epicversie en geen los document zonder eigenaar.

De epic moet klein genoeg zijn om door Productplanning in een beperkt aantal zelfstandig leverbare
stories te worden verdeeld. Als grove controle verwacht Productontwerp meestal drie tot acht stories.
Dat is geen harde productregel, maar een waarschuwing wanneer de scope waarschijnlijk te groot of te
klein is.

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

Goedgekeurde output wordt atomair opgeslagen: eerst de eigen entiteiten, daarna de geversioneerde
projecties in `processcontracts`, en als laatste de sessiestatus.

## Planning en de HKH-backlog

Een sessie wordt planbaar door:

- een gewijzigd Stakeholderprofiel of een nieuwe of gewijzigde Stakeholderrichting,
  gebruikerssignaal of signaalafhandeling;
- een nieuwe epicverificatie of geldig leerresultaat;
- een structureel kwaliteitssignaal;
- een periodieke onderzoeks- of epiccontrole;
- `BacklogSupplyView.aanvullingNodig = true` terwijl onvoldoende beschikbare epics bestaan.

Bij lage backlogvoorraad geeft Productontwerp voorrang aan complete, kansrijke epics die tijdig door
Productplanning kunnen worden opgepakt. Het maakt geen stories en verlaagt de epickwaliteit niet om
het getal tien kunstmatig te halen.

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
- output atomair en geversioneerd beschikbaar is;
- de operationele sessiestatus en volgende plandatum zijn opgeslagen.
