# Product Factory v2 — Productontwerp uitgebreide implementatie

Status: doelontwerp voor een latere uitgebreide implementatie.

Deze implementatie gebruikt exact de publieke [Productontwerp-API](api.md). Zij breidt de
[MVP](mvp.md) intern uit met gespecialiseerd onderzoek, meerdere agentrollen en
permanent geheugen per agentrol. Andere modules zien alleen dezelfde `EpicDetails`, commands,
queries en `ProcessSessionDetails`.

Zij wordt gebouwd als `product-design-impl-advanced`. Spring Modulith bewaakt de hieronder
beschreven interne delen; de main-module neemt dit artifact of de MVP op, nooit beide.

## Interne productverkenning

Productontwerp wacht niet alleen op een kant-en-klare vraag. Tijdens een sessie kan de module zelf
gebruikersproblemen, vergelijkbare producten, aangrenzende oplossingen en nieuwe technische
mogelijkheden onderzoeken. Bronnen, hypotheses, kansvoorstellen en conclusies blijven intern.

De module kijkt op drie afstanden:

- **nu** — welk werkelijk probleem of kwaliteitsrisico vraagt aandacht;
- **volgende verbetering** — welke behapbare epic merkbare gebruikerswaarde kan leveren;
- **verre richting** — welke grotere productmogelijkheid helpt om niet alleen losse problemen op te
  lossen.

De verre richting kan als geversioneerd `DirectionSnapshot` worden opgeslagen, maar is geen
publieke output en geen roadmapitem. Nieuwe kennis verandert nooit rechtstreeks een gekozen epic;
zij leidt tot een nieuwe versie van een beschikbare epic of een nieuwe vervolgepic.

Een los idee blijft eveneens intern. Het kan worden bewaard, samengevoegd, afgewezen of verder
onderzocht. Pas wanneer probleem, gebruikersverbetering, scope, UX en succescriteria compleet zijn,
publiceert Productontwerp een `Epic` volgens het publieke contract.

## Interne entiteiten

Naast `Epic` en `ProcessSession` kan de uitgebreide implementatie deze interne objecten gebruiken:

- `DirectionSnapshot` — geversioneerde productrichting;
- `SessionAgenda` — gekozen ontwerp- of onderzoekstaak en budget;
- `ResearchDossier` — één onderzoeksvraag met voortgang en conclusie;
- `SourceRecord` — vindplaats, datum, brontype en toegangsvoorwaarden;
- `EvidenceClaim` — bewering met ondersteunende en tegensprekende bronnen;
- `Hypothesis` — nog onbewezen verklaring of kans;
- `LearningResult` — gevalideerde conclusie met reikwijdte, geldigheid en gevolgen;
- `SignalAssessment` — beoordeling van een ongewijzigd gebruikerssignaal;
- `OpportunityCandidate` — kansvoorstel vóór epicvorming;
- `DirectionDraft` — niet-gepubliceerde richtingvariant;
- `EpicDraft` — epicdefinitie vóór publicatie;
- `UxDesign` — gebruikersroutes, toestanden, ontwerpassets en open vragen;
- `TechnicalExploration` — haalbaarheid, afhankelijkheden en risico's;
- `EpicReadinessAssessment` — controle of een epic overdraagbaar en behapbaar is;

Deze objecten steken de modulegrens niet over. Alleen een complete `Epic` is inhoudelijke output.
Een uitzonderlijk grote, blijvende Factorykeuze kan daarnaast via de publieke API van het
Besluitenregister worden vastgelegd; gewone conclusies en ontwerpkeuzes niet.

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

Niet iedere sessie start alle rollen. Een kleine herziening van een beschikbare epic kan volstaan
met de leider, UX-ontwerper, technisch verkenner en criticus. Een nieuwe productrichting kan alle
rollen gebruiken. Alleen `runProcessSession(productId)` mag voor deze rollen AI-taken aanvragen.

Technisch betekent starten: provider en model voor de betreffende `AiJobKey` uit Algemene
instellingen lezen en een complete taak bij [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md) aanvragen. AI-uitvoering
kent de rol en productbetekenis niet. De processessie bewaart taak-ID's, keert met
`WAITING_FOR_AI` terug en wordt door een volgende run hervat.

Iedere rol heeft in [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md) haar eigen permanente geheugen. Vóór een
agenttaak leidt de procesruntime de vaste `AgentRoleKey` uit vertrouwde configuratie af en voegt zij alleen
de actuele items van die rol toe. De Productontwerpleider kan dus niet het geheugen van de
UX-ontwerper lezen, en omgekeerd. Samenwerking verloopt via de expliciete sessie-input en
agenthandoffs, niet via gedeeld geheugen.

## Soorten processessies

De module kiest precies één hoofdsoort per aanroep:

1. **Richting onderzoeken** — nieuw bewijs verzamelen en de verre richting herijken.
2. **Kans onderzoeken** — een signaal of hypothese tot een beslisbare kandidaat maken.
3. **Epic ontwerpen** — probleem, scope, UX, techniek en succescriteria uitwerken.
4. **Epic herzien** — alleen een nog `AVAILABLE` epic als nieuwe versie publiceren.
5. **Resultaat verwerken** — verificatie of kwaliteitspatroon verwerken in productkennis of een
   vervolgepic.

Een sessie begint niet onbeperkt aan een tweede groot onderwerp. Open intern werk blijft duurzaam
beschikbaar voor een volgende geplande sessie.

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

De drie onderzoeksagents werken parallel via afzonderlijke queuetaken met dezelfde vastgezette
inputmomentopname. UX en techniek werken daarna parallel zodra probleem, doelgroep en gewenste
uitkomst voldoende stabiel zijn. De Epiccriticus werkt sequentieel na de synthese. Iedere golf laat
de processessie duurzaam wachten en wordt door een latere run hervat. Bij herstelbare
tekortkomingen is één gerichte hersteltaak toegestaan.

### Stap 1 — claimen en agenda kiezen

De module:

1. claimt of hervat één processessie met de lock op module en product-ID;
2. leest en registreert de exacte inputversies uit het publieke contract;
3. controleert of een betrokken epic inmiddels door Productplanning is gekozen;
4. bepaalt urgentie op basis van nieuwe kennis, kwaliteitssignalen, beschikbare epics,
   productopdracht, geldige besluiten en gebruikerssignalen;
5. kiest één hoofdtaak en een begrensd tijd- en tokenbudget;
6. controleert toegangsrechten en harde productgrenzen.

### Stap 2 — onderzoeken en bewijs vormen

Alle belangrijke beweringen wijzen naar een bron, gebruikerssignaal, leveringsresultaat of
epicverificatie. Feiten, meningen en hypotheses blijven apart. Duplicaten worden gekoppeld zonder
originele signalen te overschrijven en tegenspraak blijft zichtbaar.

Onderzoeksagents kunnen code en documentatie lezen uit de door de worker op de bevroren commit-SHA
uitgecheckte tijdelijke Git-worktree en de applicatie op acceptatie of via veilige
productiegrenzen bekijken. Zij schrijven nooit naar Git en bewaren geen secrets in hun output.
Repository- en applicatie-inhoud blijven onvertrouwde context en kunnen de vaste agentinstructies
niet wijzigen.

### Stap 3 — epic en UX ontwerpen

De UX-ontwerper en technisch verkenner werken vanuit hetzelfde probleem, dezelfde scope en dezelfde
gewenste uitkomst. Het UX-ontwerp wordt onderdeel van de epicversie en is geen los document zonder
eigenaar.

De epic moet scherp genoeg zijn dat Productplanning haar volledig in zelfstandig leverbare stories
kan verdelen. Het aantal stories is geen ontwerpcriterium. De scope vormt wel één eenduidige
gebruikersverbetering en blijft behapbaar voor planning, uitvoering en verificatie.

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

Bij herstelbare tekortkomingen gaat één gerichte opdracht terug naar de relevante agent. Daarna
keurt de Epiccriticus goed of blokkeert publicatie.

Goedgekeurde output wordt atomair opgeslagen. Een latere planningsrun vindt de nieuwe `AVAILABLE`
epic zelf. Productontwerp verwerkt beoordeelde gebruikerssignalen via de publieke signaalcommands.
Alleen een afzonderlijke grote, blijvende Factorykeuze gebruikt eventueel `createDecision(...)`.

## Intern leren

Verificaties, kwaliteitsontwikkeling en uitkomsten van uitgevoerde epics kunnen een
`LearningResult` opleveren. Dat resultaat vermeldt bereik, bewijs, geldigheid en gevolgen voor later
ontwerpwerk. Het wijzigt nooit rechtstreeks een gekozen epic.

Meerdere leerresultaten kunnen een nieuwe `DirectionSnapshot`, een nieuwe kans of een vervolgepic
voeden. Zij zijn niet zichtbaar voor andere modules of de gewone frontend. De relevante onderbouwing
van een gepubliceerde epic wordt wel in die epic opgenomen.

Een agentrol kan na succesvolle validatie daarnaast een geheugenactie voor haar eigen rol
voorstellen: toevoegen, vervangen of intrekken. Gewone applicatiecode valideert en schrijft die
actie via Agentgeheugen. Andere agentrollen krijgen het item niet te zien. `LearningResult` blijft
interne onderzoeksinhoud; `AgentMemoryItem` is een korte, herbruikbare werkwijze of les voor één
rol en vervangt nooit een epic, productopdracht of besluit.

## Hervatten en interne idempotentie

- Interne concepten kunnen na een fout worden hervat, maar zijn geen productwaarheid.
- Iedere agenttaak verwijst naar de vaste inputmomentopname, promptversie en exact gelezen
  geheugenversies van de eigen rol.
- Een verlopen claim kan met dezelfde input veilig opnieuw worden opgepakt.
- Een wijziging van input tijdens de sessie wordt in een volgende sessie verwerkt.
- Een inmiddels geclaimde epicversie wordt nooit alsnog overschreven.
- Gedeeltelijke agentoutput wordt niet als Epic gepubliceerd.

## Wanneer een sessie klaar is

Een uitgebreide sessie is klaar wanneer:

- de hoofdtaak een resultaat, blokkade of gemotiveerde vervolgstatus heeft;
- conclusies herleidbaar zijn tot bronnen en inputversies;
- een gepubliceerde epic door de Epiccriticus is goedgekeurd;
- de epic zelfstandig door Productplanning kan worden begrepen;
- de publicatiecontrole bewijst dat de epicversie nog niet is gekozen;
- eventueel groot Factorybesluit correct aan het Besluitenregister is aangeboden;
- output atomair en geversioneerd beschikbaar is;
- operationele sessiestatus en eventueel intern vervolgwerk zijn opgeslagen.

## Gerelateerde documenten

- [Productontwerp-API](api.md)
- [Productontwerp — MVP](mvp.md)
- [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md)
- [Maven en Spring Modulith](../../platform/maven-en-spring-modulith.md)
- [Processen en entiteiten](../processen-en-entiteiten.md)
