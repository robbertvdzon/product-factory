# Product Factory v2 — Kwaliteitsbewaking

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt Kwaliteitsbewaking uit. De black-boxinterface in
[Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md) is leidend.

## Verantwoordelijkheid

Kwaliteitsbewaking test de werkende applicatie, verifieert losse opleveringen en controleert na de
laatste story of de volledige bevroren epic de bedoelde gebruikersverbetering heeft bereikt. Zij
publiceert reproduceerbare bugs en duurzame verificaties met bewijs. Ontbrekende epicdekking staat
in een verificatie en wordt via een command als nieuw planwerk aangevraagd.

De module is eigenaar van:

- teststrategie, testrotatie en dekkingsbeeld;
- testsessies, observaties en bewijs;
- bugs, ernst en herstelstatus;
- verificaties van stories, epics en gebruikerssignalen;
- structurele kwaliteitspatronen en hun bewijs;
- het eigen agent- en procesgeheugen.

Kwaliteitsbewaking maakt geen stories, wijzigt geen epicinhoud en bepaalt geen backlogvolgorde. Zij
kan alleen via publieke commands een bugfix, aanvullend planwerk, epicuitkomst of signaaluitkomst
doorgeven aan de module die de betreffende entiteit bezit.

## Publieke module-interface

De enige agentgestuurde ingang is:

```java
void runProcessSession();
```

Alleen de scheduler gebruikt deze functie. De module claimt zelf atomair één product en één
begrensde testopdracht. Een epic met status **Controleren** (`VERIFYING`) is zo'n opdracht; er staat
geen testqueue bij Productontwerp. Per product kan maximaal één sessie tegelijk actief zijn. Zonder
planbaar of periodiek testwerk eindigt de functie als succesvolle no-op.

Andere modules kunnen geen testagent of teststap starten. Een nieuwe oplevering, epic op
**Controleren** of gebruikerssignaal wordt tijdens een volgende sessie gelezen.

De deterministische publieke functies zijn beperkt tot read-only queries en lifecycle-commands voor
eigen bugs:

```java
BugDetails getBug(BugId bugId);
List<VerificationDetails> findVerifications(VerificationTarget target);
QualityOverview getQualityOverview(ProductId productId);
void linkBugfixStory(LinkBugfixStoryCommand command);
```

`linkBugfixStory(...)` laat Productplanning alleen een story-ID aan een bestaande uitvoerbare bug
koppelen. Het command controleert bugstatus, versie en idempotentie; Productplanning krijgt geen
toegang tot de bugrepository.

## Interface met andere modules en services

Kwaliteitsbewaking gebruikt publieke Spring Modulith-API's en read-only DTO's uit
`processcontracts`. DTO's zijn geen database-entiteiten. Browser-, log- en testclients zijn interne
adapters.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `StakeholderDetails` | product-/overlegmodule | identiteit, rol en beslissingsmandaat achter kwaliteitsgrenzen en risico's |
| `ProductAssignmentDetails` | productmodule | productgrenzen en publieke Git-URL van het product |
| `TestableProductDetails` | productmodule | omgevingen, routes, toegestane accounts, databereik en testgrenzen |
| `StakeholderDirectionDetails` | product-/overlegmodule | bindende productgrenzen en correcties die ook tijdens het testen gelden |
| `EpicDetails` | Productontwerp | bevroren scope, UX, succescriteria en status van de geclaimde versie |
| `StoryDetails` | Productplanning | type, storyversie, status, oplevergegevens, acceptatiecriteria en zelfstandige UX |
| `UserSignalDetails` | productmodule | oorspronkelijke melding plus actuele status en resultaatkoppelingen; categorie `QUALITY_CONCERN` vraagt extra onderzoek |

De module leest daarnaast eigen bugs en testhistorie. Iedere sessie legt de gebruikte contractversies
en exacte geteste omgeving vast.

Kwaliteitsbewaking mag de publieke Git-URL uit de productopdracht uitchecken en code, tests en
documentatie read-only gebruiken voor testselectie, regressierisico en uitleg. Daarvoor is geen
aparte workspace of Git-service nodig. Zij commit en pusht nooit. Code is context en geen bewijs dat
gedrag werkt; de gedeployde applicatie en het verzamelde testbewijs blijven leidend. Waar bekend legt
de verificatie vast welke commit is bekeken en welke productversie werkelijk is getest.

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `BugDetails` | read-only weergave van een aantoonbare afwijking | werkelijk en verwacht gedrag, reproduceerstappen, omgeving, bewijs, impact, ernst, status en bron-signaal-ID's |
| `VerificationDetails` | read-only weergave van een story-, epic- of signaalcontrole | doeltype en -versie, uitkomst, omgeving, controles, bewijs, blokkade, ontbrekende dekking en vervolgkoppelingen |
| `QualityOverview` | berekend queryresultaat, geen duurzame entiteit | recente dekking, risico's, open bugs en onderbelichte gebieden |
| `ProcessSession` | operationele historie van de sessie | sessie-ID, product-ID, inputversies, publicatie-ID's en eindstatus |

Kwaliteitsbewaking schrijft `ProcessSession` uitsluitend voor zijn eigen sessies. De
scheduler roept alleen de procesfunctie aan; scheduler en frontend wijzigen het sessieresultaat niet.

Alleen Kwaliteitsbewaking schrijft `Bug` en `Verification`. Zij vraagt Productplanning via
`requestBugfix(...)` of `requestCompletionWork(...)` om vervolgwerk, Productontwerp via
`recordEpicVerification(...)` om een epicuitkomst vast te leggen en de productmodule via
`recordSignalInvestigation(...)` om een gebruikerssignaal bij te werken. Geen ontvangende module
kan de onderliggende verificatie of het bewijs veranderen.

## Een kwaliteitszorg uit een overleg

Wanneer de Stakeholder aangeeft dat een onderdeel mogelijk niet goed werkt of extra aandacht nodig
heeft, registreert de productmodule dit als `UserSignal`. De optionele categorie
`QUALITY_CONCERN` helpt Kwaliteitsbewaking bij de testagenda, maar maakt van de melding geen opdracht
met een vooraf bepaald resultaat.

De Stakeholder schrijft dit databaseobject niet rechtstreeks. De frontend of overlegmodule voert een
command uit op de productmodule; die bewaart de oorspronkelijke melding daarna onveranderlijk.
Kwaliteitsbewaking leest `UserSignalDetails`, bewaart het onderzoek als `Verification` en roept
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
testbewijs ieder hun eigen eigenaar houden. `StakeholderDirectionDetails` blijft alleen bedoeld voor echte bindende
productrichting, grenzen, correcties en stopbesluiten.

## Storyverificatie

Een story- of bugfixoplevering krijgt:

- **Geslaagd** — afgesproken gedrag werkt in de bedoelde omgeving;
- **Afgekeurd** — gedrag wijkt aantoonbaar af;
- **Geblokkeerd** — controle is door omgeving, toegang of ontbrekende informatie niet mogelijk.

Bij **Afgekeurd** publiceert Kwaliteitsbewaking zo nodig een bug. Het herschrijft de story niet.

## Epicverificatie

Alle stories van een epic op `DONE` is alleen het startsein voor de epiccontrole. De
controle gebruikt exact de door Productontwerp bevroren `EpicDetails` en beoordeelt:

- de volledige gebruikersroute, niet alleen losse schermen;
- alle relevante UX-toestanden en overgangen;
- de expliciete scope in en uit;
- toegankelijkheid, privacy en andere kwaliteitsgrenzen;
- samenhang tussen de geleverde stories;
- de succescriteria en de merkbare verbetering voor de gebruiker.

De uitkomst is:

- **Geslaagd** — de gebruikersverbetering is aantoonbaar bereikt;
- **Onvolledig** — gedrag binnen scope of UX ontbreekt;
- **Niet aantoonbaar** — de uitvoering lijkt compleet, maar bewijs of meting ontbreekt;
- **Geblokkeerd** — de controle kan niet verantwoord worden afgerond;
- **Niet geslaagd** — alles werkt zoals ontworpen, maar het bedoelde gebruikersresultaat is niet
  bereikt.

Kwaliteitsbewaking schrijft eerst een onveranderlijke `Verification` en roept daarna
`recordEpicVerification(...)` op Productontwerp aan. Productontwerp controleert de epicversie en is
de enige schrijver van de epicstatus.

Het vervolg per uitkomst is:

| Uitkomst | Epic bij Productontwerp | Bericht aan Productplanning |
|---|---|---|
| **Geslaagd** | **Geslaagd** (`COMPLETED`) | geen |
| **Onvolledig** | terug naar **Actief** | `requestCompletionWork(...)` met verificatie-ID |
| bouwfout in uitgevoerd storygedrag | terug naar of blijft **Actief** | `requestBugfix(...)` met bug- en verificatie-ID |
| **Niet aantoonbaar** | blijft **Controleren** | geen; Kwaliteitsbewaking plant later nieuw bewijswerk |
| **Geblokkeerd** | blijft **Controleren** | geen, tenzij aantoonbaar ontwikkelwerk nodig is |
| **Niet geslaagd** | **Niet geslaagd** (`NOT_SUCCESSFUL`) | geen; Productontwerp kan vanuit het resultaat een vervolgepic onderzoeken |

Productplanning krijgt dus geen generiek verificatieresultaat terug. Alleen een gericht plancommand
betekent dat zij nieuw ontwikkelwerk moet vormen. Gedrag binnen de bevroren scope wordt een
aanvullende story of bugfixstory binnen dezelfde epic. Alleen een nieuwe wens buiten scope of een
onjuiste productaanname kan later tot een nieuwe vervolgepic leiden.

## Bug, ontbrekende dekking of nieuwe productkans

Kwaliteitsbewaking classificeert een ontbrekend of onjuist resultaat vóór publicatie:

| Situatie | Publicatie | Vervolg |
|---|---|---|
| Gedrag stond in een uitgevoerde story maar is verkeerd gebouwd | `Bug` plus `Verification` | Kwaliteitsbewaking vraagt Productplanning om een bugfix |
| Gedrag viel duidelijk binnen de bevroren epic, maar er bestond nooit een story voor | `Verification` met ontbrekende dekking | Kwaliteitsbewaking vraagt Productplanning om aanvullend werk |
| Alles is geleverd, maar de gebruikersverbetering is nog niet bewezen | `Verification` met **Niet aantoonbaar** | epic blijft op **Controleren** |
| Alles werkt zoals ontworpen, maar de productaanname blijkt onjuist | `Verification` met **Niet geslaagd** en een `UserSignal` van categorie `QUALITY_PATTERN` | Productontwerp registreert de uitkomst en leert |
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
- herstelstatus.

De herstelstatus is:

- **Nieuw** — bevinding bestaat intern maar is nog niet reproduceerbaar;
- **Uitvoerbaar** — reproduceerbaar en compleet genoeg voor een bugfix;
- **Gepland** — Productplanning heeft er een bugfixstory met status `TODO` voor gepubliceerd;
- **In herstel** — de bugfixstory heeft status `IN_PROGRESS`;
- **Hertesten** — de bugfixstory heeft status `DONE` en de fix is opgeleverd;
- **Opgelost** — de fix is in de juiste omgeving goedgekeurd;
- **Heropend** — de afwijking bestaat nog of is teruggekomen;
- **Ongeldig** — geen productafwijking, met zichtbare reden.

Productplanning koppelt een bugfixstory via `linkBugfixStory(...)`. Kwaliteitsbewaking kan
**Gepland**, **In herstel** en **Hertesten** daarna uit `StoryDetails` afleiden en blijft zelf de enige
schrijver van de duurzame bugstatus.

## Ontbrekende epicdekking

Een dekkingsgat is geen afzonderlijke entiteit meer. Een epicverificatie kan één of meer gestructureerde
dekkingsgaten bevatten met scope- of UX-verwijzing, gebruikersimpact, ontbrekend gedrag en bewijs.
Kwaliteitsbewaking roept `requestCompletionWork(...)` aan met het verificatie-ID. Productplanning
maakt tijdens een processessie de aanvullende stories; een latere verificatie toont of het gat is
opgelost. Zo blijft het bewijs historisch intact zonder een tweede lifecycle naast epic en story.

## Interne entiteiten

- `ProcessSession` — geclaimde en begrensde processessie en haar operationele historie;
- `TestStrategy` — kwaliteitsdoelen en risicoprioriteiten per product;
- `TestRotation` — wanneer routes en thema's voor het laatst zijn onderzocht;
- `TestAgenda` — doelen, omgeving en budget voor één sessie;
- `TestCase` — concrete controle of exploratieve opdracht;
- `TestObservation` — feitelijke waarneming;
- `EvidenceArtifact` — screenshot, log, trace of meetresultaat;
- `BugCandidate` en `Bug` — bevinding en duurzame levenscyclus;
- `EpicCoverageAssessment` — vergelijking van epic, UX, stories en geleverd gedrag;
- `VerificationDraft` — story-, epic- of signaalcontrole vóór publicatie;
- `Verification` — duurzame, onveranderlijke controle met doeltype, uitkomst en bewijs;
- `QualityPattern` — clustering van verwante bevindingen;
- `QualityMemory` — lessen over risico's, testaanpak en dekkingsgaten;
- `AgentRun` — input, promptversie, output, fout en verbruik van één agenttaak.

Ruwe observaties zijn geen publieke interfacestatus. Alleen hun gevalideerde conclusie wordt
gepubliceerd.

## Agents

Een processessie gebruikt vier vaste agentrollen:

1. **Testcoördinator** — kiest agenda, omgeving, risico's en benodigde controles.
2. **Functionele tester** — controleert gebruikersroutes, stories, lege toestanden en foutpaden.
3. **Kwaliteitsspecialist** — rouleert tussen UX-samenhang, toegankelijkheid, responsiviteit,
   performance, beveiliging, privacy en betrouwbaarheid.
4. **Verificatiecriticus en bugtriager** — reproduceert bevindingen, classificeert bugs/dekkingsgaten,
   bepaalt ernst en keurt publieke resultaten goed.

De functionele tester en kwaliteitsspecialist werken parallel op gescheiden testtaken. De criticus
werkt sequentieel nadat hun observaties beschikbaar zijn. Bij een complete epiccontrole zijn beide
testrollen verplicht.

## Soorten processessies

1. **Story verifiëren** — acceptatiecriteria en relevante regressie controleren.
2. **Bugfix hertesten** — oorspronkelijke reproductie en aangrenzend gedrag controleren.
3. **Epic verifiëren** — de complete bevroren gebruikersverbetering controleren.
4. **Dagelijkse kernroutes testen** — belangrijkste gebruikersresultaten bewaken.
5. **Kwaliteitsrotatie uitvoeren** — een onderbelicht thema of apparaat onderzoeken.
6. **Gebruikerssignaal onderzoeken** — een gemeld probleem reproduceren.
7. **Patroon analyseren** — verwante bevindingen als `UserSignal` van categorie `QUALITY_PATTERN` registreren.

Nieuwe opleveringen en P0/P1-signalen gaan voor periodieke rotatie. Een epic op **Controleren** gaat
voor losse exploratieve tests wanneer alle benodigde omgevingen beschikbaar zijn.

## Verloop van één processessie

```text
claim product, opdracht en omgeving
                 │
                 ▼
Testcoördinator maakt agenda
                 │
        ┌────────┴────────┐
        ▼                 ▼
functionele tester   kwaliteitsspecialist
        └────────┬────────┘
                 ▼
reproductie, dekking en classificatie
                 │
                 ▼
story-/epicverificatie + bug/gat
                 │
                 ▼
atomair publiceren en rotatie bijwerken
```

### Stap 1 — claimen en omgeving controleren

De module claimt één planbare opdracht en leest exacte versies van epic, stories,
stories, oplevering en eventueel gebruikerssignaal, controleert de omgeving en registreert
productversie en testaccount. Een onbereikbare omgeving leidt tot **Geblokkeerd**, niet tot een
productbug.

### Stap 2 — testen

Bij een story test de functionele tester acceptatiecriteria, hoofdroute en relevante fout- en lege
toestanden. Bij een bugfix begint hij met de oorspronkelijke reproduceerstappen. Bij een epiccontrole
doorloopt hij de volledige route en vergelijkt hij alle storyresultaten met scope, UX en
succescriteria van de bevroren epicversie.

De kwaliteitsspecialist kiest relevante kwaliteitsdimensies. Niet iedere storysessie test alles,
maar een epiccontrole dekt minimaal de expliciete grenzen uit de epic.

### Stap 3 — classificeren en publiceren

De criticus:

- reproduceert mogelijke bugs onafhankelijk;
- controleert of ontbrekend gedrag een bouwfout, dekkingsgat of nieuwe wens is;
- zoekt duplicaten;
- bepaalt ernst en gebruikersimpact;
- controleert bewijs op geheimen en persoonsgegevens;
- geeft het story- of epicoordeel;
- geeft ieder onderzocht gebruikerssignaal een expliciet onderzoeksresultaat;
- publiceert eigen `Bug`- en `Verification`-entiteiten atomair en voert vervolgcommands idempotent uit.

## Planning en de HKH-backlog

Een sessie wordt planbaar door:

- een nieuwe Software Factory-oplevering;
- een bugfix op **Hertesten**;
- een epic met status **Controleren**;
- een nieuw of heropend gebruikerssignaal, of nieuw bewijs bij een eerder onbeslist signaal;
- een P0/P1-risico of verouderd kwaliteitsbeeld;
- de dagelijkse kernrouteplanning of testrotatie;
- lage backlogvoorraad, zodat bekende bevindingen tijdig worden gereproduceerd.

Kwaliteitsbewaking maakt geen bugs of dekkingsgaten om de backlog kunstmatig tot tien te vullen.

## Fouten, hervatten en idempotentie

- Een storyverificatie is uniek voor story-ID, storyversie, oplevering en omgeving.
- Een epicverificatie is uniek voor epic-ID, epicversie en geteste productversie.
- Herhaling werkt bewijs bij maar maakt geen duplicaat.
- Een technische testfout wordt apart geregistreerd en niet als productbug gepubliceerd.
- Een sessie kan na een verlopen claim worden hervat met dezelfde inputmomentopname.
- Parallelle testers schrijven alleen observaties; de criticus publiceert het definitieve resultaat.
- Gedeeltelijk bewijs blijft intern tot reproductie en privacycontrole zijn afgerond.

## Wanneer een sessie klaar is

Een sessie is klaar wanneer:

- ieder gekozen testgeval een resultaat of expliciete blokkade heeft;
- iedere publieke bug reproduceerbaar en van bewijs voorzien is;
- ieder dekkingsgat aantoonbaar binnen de bevroren epic valt en niet door een story wordt gedekt;
- iedere verificatie naar exacte epic-, story-, opleverings- en omgevingsversies verwijst;
- ieder onderzocht gebruikerssignaal naar exact signaal-ID en -versie verwijst en een zichtbaar
  onderzoeksresultaat of expliciete blokkade heeft;
- testrotatie en kwaliteitsbeeld zijn bijgewerkt;
- publicaties atomair en geversioneerd beschikbaar zijn;
- de operationele sessiestatus en volgende plandatum zijn opgeslagen.
