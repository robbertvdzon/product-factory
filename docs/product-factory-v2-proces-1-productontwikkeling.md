# Product Factory v2 — proces 1: Productontwikkeling

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt de module Productontwikkeling uit. De black-boxinterface in
[Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md) is leidend. Interne namen,
agentprompts en taakverdeling kunnen later veranderen zonder gevolgen voor andere modules.

## Verantwoordelijkheid

Productontwikkeling onderzoekt hoe een product zijn opdracht beter kan vervullen en zet de beste
kansen om in epics en uitvoerbare productstories. Onderzoek en richting en epic- en storyvorming zijn
bewust onderdelen van dezelfde module. Zij delen bronnen, bewijs, hypotheses, UX-verkenningen,
technische risico's en het actuele droombeeld.

De module is eigenaar van:

- het actuele droombeeld en zijn versies;
- onderzoeksdossiers, bronnen, bewijsclaims en interne kansvoorstellen;
- epics, hun volgorde, actuele UX-richting en klaar-beoordeling;
- productstories en hun inhoudelijke status;
- het eigen agent- en procesgeheugen.

De module is niet de eigenaar van bugs, backlogvolgorde, uitvoeringsstatus of verificatieresultaten.

## Publieke procesfunctie

De enige uitvoerende ingang is:

```java
void runProcessSession();
```

Alleen de scheduler gebruikt deze functie. De aanroep heeft geen product-ID, epic-ID of opdracht als
argument. De module claimt zelf atomair de belangrijkste planbare sessie. Per product kan maximaal
één sessie van Productontwikkeling tegelijk actief zijn. Zonder planbaar werk eindigt de functie als
succesvolle no-op.

Andere modules kunnen geen onderzoek starten, geen epic activeren en geen story laten schrijven.
Zij publiceren alleen gegevens die Productontwikkeling tijdens een volgende sessie kan lezen.

## Interface met andere modules en services

De procesmodules importeren elkaar niet. Stabiele DTO's, read-only queryports en geversioneerde
databaseprojecties staan in de technische module `processcontracts`. Productontwikkeling schrijft
uitsluitend haar eigen publicaties en leest alleen publicaties van andere eigenaren.

### Input

| Contract | Eigenaar | Hoe verkregen | Gebruik |
|---|---|---|---|
| `ProductAssignmentView` | productmodule | read-only query bij sessiestart | doelgroep, productdoel, harde grenzen, repository en toegestane toegang |
| `StakeholderDirectionView` | product-/overlegmodule | actieve versies uit de database | bindende richting en expliciete correcties |
| `UserSignalView` | inbox/productmodule | onbewerkte signalen sinds vorige sessie | mogelijke problemen, kansen en observaties |
| `BacklogSupplyView` | proces 2 | gepubliceerde projectie | aantal verzendbare items, streefpeil en `aanvullingNodig` |
| `DeliveryResultView` | dispatcher/proces 2 | nieuwe of gewijzigde leveringen | wat Software Factory werkelijk heeft opgeleverd |
| `VerificationResultView` | proces 3 | nog niet verwerkte verificaties | of gedrag werkt en wat daarvan is geleerd |
| `QualitySignalView` | proces 3 | open structurele signalen | terugkerende problemen die een epic kunnen rechtvaardigen |
| `QualityOverviewView` | proces 3 | laatste gepubliceerde versie | productgezondheid en onderbelichte risicogebieden |

Voor iedere gelezen publicatie worden bron-ID en bronversie vastgelegd. Eenzelfde versie wordt niet
tweemaal als nieuwe input behandeld.

Externe onderzoeksbronnen en repository-informatie worden binnen de toegestane productgrenzen door
de module zelf opgehaald. Ruwe bronnen steken de modulegrens niet over.

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `DirectionSnapshot` | zichtbaar, geversioneerd beeld van de gewenste verre toekomst | toekomstverhaal, kernervaringen, obstakels, aannames, bewijsbasis en wijzigingsreden |
| `EpicView` | gekozen samenhangende productverandering | probleem, doelgroep, uitkomst, bewijs, UX-richting, succescriteria, kleinste slice, status en positie |
| `ProductStoryView` | zelfstandig uitvoerbaar zichtbaar gedrag | epic-ID, gedrag, acceptatiecriteria, afhankelijkheden, relevante UX- en technische context, status en versie |
| `LearningResultView` | gevalideerde nieuwe productkennis | conclusie, bron, reikwijdte, geldigheid en effect op droombeeld, epic of story |
| `ProcessSessionPublication` | operationeel resultaat van de sessie | sessie-ID, product-ID, gebruikte inputversies, publicatie-ID's, eindstatus en blokkade |

Alleen Productontwikkeling schrijft deze publicaties. Proces 2 mag een uitvoerbare story in de
backlog opnemen, maar wijzigt de story niet. Proces 3 mag een epic of story gebruiken als
testverwachting, maar wijzigt die evenmin.

## Interne entiteiten

De volgende entiteiten blijven binnen de module:

- `ProductDevelopmentSession` — de geclaimde, begrensde uitvoering;
- `SessionAgenda` — de gekozen interne taak en het budget van een sessie;
- `ResearchDossier` — één onderzoeksvraag met voortgang en conclusie;
- `SourceRecord` — vindplaats, datum, brontype en toegangsvoorwaarden;
- `EvidenceClaim` — bewering met ondersteunende en tegensprekende bronnen;
- `Hypothesis` — nog onbewezen verklaring of kans;
- `OpportunityCandidate` — intern kansvoorstel vóór epicselectie;
- `DirectionDraft` — niet-gepubliceerde richtingvariant;
- `EpicDraft` — epic in onderzoek of uitwerking;
- `UxExploration` — gebruikersroute, toestanden, alternatieven en open vragen;
- `TechnicalExploration` — haalbaarheid, afhankelijkheden, risico's en mogelijke slices;
- `ReadinessAssessment` — controle of een epic of story publiceerbaar is;
- `StoryDraft` — story vóór de status **Uitvoerbaar**;
- `ProductDevelopmentMemory` — gedeelde lessen over onderzoek, productkeuzes en epicvorming;
- `AgentRun` — input, promptversie, output, fout en verbruik van één agenttaak.

Onderzoeksvragen en antwoorden tussen onderzoek en epicvorming zijn hier interne entiteiten. Ze
hebben geen contract in `processcontracts` nodig.

## Interne levenscycli

Een epic gebruikt deze statussen:

- **Onderzoeken** — probleem, bewijs en mogelijkheden worden onderzocht;
- **Uitwerken** — UX, techniek, succescriteria en eerste slice worden concreet;
- **Klaar** — verantwoord om te starten, maar nog niet actief;
- **Actief** — de module publiceert er stap voor stap productstories voor;
- **Controleren** — alle bedoelde stories zijn geleverd en de gewenste uitkomst wordt beoordeeld;
- **Geslaagd** — de gewenste uitkomst is voldoende bereikt;
- **Gestopt** — bewust niet verder, met reden en geldige bronversie.

Interne kansen die nog geen gekozen epic zijn, blijven `OpportunityCandidate` en krijgen geen
publieke epicstatus. Normaal zijn maximaal één epic **Actief** en één andere epic **Onderzoeken** of
**Uitwerken**.

Een productstory gebruikt inhoudelijk:

- **Concept** — alleen intern zichtbaar;
- **Uitvoerbaar** — als publieke bron beschikbaar voor proces 2;
- **Vervangen** — opgevolgd door een nieuwe versie vóór verzending;
- **Ingetrokken** — niet meer geldig, met een expliciete reden.

De uitvoering van een story staat niet hier maar op het backlogitem van proces 2.

## Agents

Een volledige processessie kan zeven vaste agentrollen gebruiken:

1. **Productontwikkelingsleider** — kiest de sessieagenda, bewaakt productdoel en neemt het interne
   integratiebesluit.
2. **Product- en marktonderzoeker** — onderzoekt vergelijkbare producten en alternatieven.
3. **Gebruikers- en probleemonderzoeker** — onderzoekt signalen, taken en gewenste resultaten.
4. **Toekomstonderzoeker** — zoekt nieuwe techniek en oplossingen uit aangrenzende domeinen.
5. **UX-ontwerper** — werkt de belangrijkste gebruikersroute, toestanden en kleinste bruikbare
   ervaring uit.
6. **Technisch verkenner** — onderzoekt repository, haalbaarheid, afhankelijkheden en risico's zonder
   de oplossing voor Software Factory voor te schrijven.
7. **Productcriticus** — controleert bewijs, alternatieven, risico's, epic-klaarheid en
   story-uitvoerbaarheid.

Niet iedere sessie start alle zeven agents. De agenda bepaalt de minimale combinatie. Een eenvoudige
storyaanvulling kan bijvoorbeeld volstaan met de leider, UX-ontwerper, technisch verkenner en
criticus. Een nieuwe productrichting gebruikt alle rollen.

## Soorten processessies

De module kiest precies één hoofdsoort per aanroep:

1. **Richting onderzoeken** — nieuw bewijs verzamelen en het droombeeld herijken.
2. **Kans onderzoeken** — een signaal of hypothese tot een beslisbare kandidaat maken.
3. **Epic uitwerken** — bewijs, UX, techniek, kleinste slice en succescriteria uitwerken.
4. **Epic ordenen of activeren** — klaarliggende epics vergelijken en maximaal één epic actief maken.
5. **Stories aanvullen** — voor de actieve epic nieuwe uitvoerbare productstories maken.
6. **Resultaat verwerken** — oplevering, verificatie of kwaliteitssignaal in richting, epic of
   volgende stories verwerken.

Een sessie mag interne vervolgstappen uitvoeren die noodzakelijk zijn voor haar hoofdtaak, maar
begint niet onbeperkt aan een tweede groot onderwerp. Open werk wordt duurzaam bewaard voor een
volgende geplande sessie.

## Verloop van één processessie

```text
claim product en inputmomentopname
                 │
                 ▼
productontwikkelingsleider kiest hoofdtaak
                 │
       ┌─────────┼─────────┐
       ▼         ▼         ▼
  product-    gebruiker-  toekomst-
  onderzoek   onderzoek   onderzoek
       └─────────┼─────────┘
                 ▼
      synthese/kansselectie
                 │
       ┌─────────┴─────────┐
       ▼                   ▼
  UX-verkenning     technische verkenning
       └─────────┬─────────┘
                 ▼
       epic- en storyconcept
                 │
                 ▼
          productcriticus
                 │
          akkoord of herstel
                 │
                 ▼
  publiceer richting/epic/stories
```

De drie onderzoeksagents werken parallel op dezelfde vastgezette inputmomentopname. UX en techniek
werken daarna parallel zodra probleem, doelgroep en gewenste uitkomst voldoende stabiel zijn. De
productcriticus werkt sequentieel na de synthese. Bij herstelbare tekortkomingen is één gerichte
herstelronde toegestaan; daarna wordt het concept bewaard voor een volgende sessie of expliciet
geblokkeerd.

Bij een sessie die alleen stories aanvult, worden de onderzoeksstappen overgeslagen en gebruiken UX
en techniek het bestaande epicdossier. Bij tegenstrijdig nieuw bewijs gaat de sessie terug naar
onderzoek en publiceert zij nog geen story.

### Stap 1 — claimen en agenda kiezen

De module:

1. claimt één planbare sessie met een database-lock;
2. leest de exacte inputversies;
3. bepaalt urgentie in deze volgorde: blokkerende nieuwe kennis, actieve epic, lage backlogvoorraad,
   kansonderzoek en periodieke richtingherijking;
4. kiest één hoofdtaak en een begrensd tijd-/tokenbudget;
5. controleert toegangsrechten en harde productgrenzen.

### Stap 2 — onderzoeken en bewijs vormen

Alle belangrijke beweringen wijzen naar een `SourceRecord`, gebruikerssignaal, leveringsresultaat of
verificatieresultaat. Feiten, meningen en hypotheses blijven apart. Duplicaten worden samengevoegd en
tegenspraak blijft zichtbaar.

De leider kan uit dit werk intern een `OpportunityCandidate` maken. Die kandidaat is nog geen epic
en hoeft nooit buiten de module gepubliceerd te worden.

### Stap 3 — epic en eerste slice vormen

Een epic is pas klaar wanneer duidelijk is:

- welk probleem of welke kans wordt aangepakt en voor wie;
- welke gewenste uitkomst en succescriteria gelden;
- hoe de epic bij productdoel en droombeeld past;
- welk bewijs en welke onzekerheid bestaan;
- wat de actuele UX-richting is;
- welke technische risico's en afhankelijkheden bekend zijn;
- wat de kleinste bruikbare of leerzame slice is;
- welke eerste productstory zelfstandig uitvoerbaar is.

Normaal zijn maximaal één epic actief en één andere epic uitgebreid in onderzoek of uitwerking.

### Stap 4 — stories publiceren

Een productstory wordt alleen **Uitvoerbaar** als zij:

- bij precies één epic hoort;
- zichtbaar gedrag en waarde beschrijft;
- duidelijke acceptatiecriteria heeft;
- klein genoeg is voor één Software Factory-opdracht;
- relevante afhankelijkheden en UX-toestanden vermeldt;
- testbaar is zonder intern Product Factory-dossier;
- niet al als gelijke of overlappende story bestaat.

Bij lage backlogvoorraad probeert een storiesessie genoeg nieuwe stories te publiceren om proces 2
weer richting het streefpeil van tien te laten groeien. Kwaliteit wordt niet verlaagd om dat aantal
kunstmatig te halen.

### Stap 5 — kritiek en atomair publiceren

De productcriticus controleert bronkwaliteit, gebruikerswaarde, alternatieven, privacy, veiligheid,
toegankelijkheid, onderhoudslast, technische aannames, epic-klaarheid en storygrootte.

Goedgekeurde output wordt atomair opgeslagen: eerst de eigen entiteiten, daarna de geversioneerde
projecties in `processcontracts`, en als laatste de sessiestatus. Andere modules zien nooit een halve
epic of story zonder bijbehorende versie.

## Planning en de HKH-backlog

Een sessie wordt planbaar door:

- een nieuw of gewijzigd Stakeholder- of gebruikerssignaal;
- een nieuwe oplevering, verificatie of geldig leerresultaat;
- een structureel kwaliteitssignaal;
- een periodieke onderzoeks- of epiccontrole;
- `BacklogSupplyView.aanvullingNodig = true`.

Voor HKH betekent een voorraad van vier of minder verzendbare items dat een storiesessie hoge
prioriteit krijgt. Als de actieve epic onvoldoende goede stories kan leveren, onderzoekt de module
de eerstvolgende epic. Er wordt geen losse story zonder epic gemaakt om de backlog te vullen.

## Fouten, hervatten en idempotentie

- Publieke output verschijnt alleen na een complete validatie.
- Interne concepten kunnen na een fout worden hervat, maar zijn niet zichtbaar als productwaarheid.
- Dezelfde bronversie en sessiedoelstelling leveren niet tweemaal dezelfde publicatie op.
- Een verlopen claim kan veilig opnieuw worden opgepakt met dezelfde inputmomentopname.
- Een mislukte agenttaak blokkeert alleen de betrokken interne taak; onafhankelijke resultaten mogen
  worden bewaard voor een volgende sessie.
- Een wijziging van input tijdens een sessie wordt in een volgende sessie verwerkt; de lopende sessie
  blijft reproduceerbaar op haar vastgezette versies.

## Wanneer een sessie klaar is

Een sessie is klaar wanneer:

- de gekozen hoofdtaak een resultaat, expliciete blokkade of gemotiveerde vervolgstatus heeft;
- alle conclusies herleidbaar zijn tot bronnen en inputversies;
- iedere gepubliceerde epic en story door de productcriticus is goedgekeurd;
- iedere story zelfstandig door proces 2 en Software Factory kan worden begrepen;
- output atomair en geversioneerd beschikbaar is;
- de operationele sessiestatus en volgende plandatum zijn opgeslagen.
