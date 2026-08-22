# Product Factory v2 — Kwaliteitsbewaking

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt Kwaliteitsbewaking uit. De black-boxinterface in
[Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md) is leidend.

## Verantwoordelijkheid

Kwaliteitsbewaking test de werkende applicatie, verifieert losse opleveringen en controleert na de
laatste story of de volledige bevroren epic de bedoelde gebruikersverbetering heeft bereikt. Zij
publiceert reproduceerbare bugs, ontbrekende epicdekking en bewijs over de uitkomst.

De module is eigenaar van:

- teststrategie, testrotatie en dekkingsbeeld;
- testsessies, observaties en bewijs;
- bugs, ernst en herstelstatus;
- story- en epicverificaties;
- epicgaten en structurele kwaliteitssignalen;
- het eigen agent- en procesgeheugen.

Kwaliteitsbewaking maakt geen stories, wijzigt geen epicdefinitie en bepaalt geen backlogvolgorde of
epicuitvoering.

## Publieke procesfunctie

De enige agentgestuurde ingang is:

```java
void runProcessSession();
```

Alleen de scheduler gebruikt deze functie. De module claimt zelf atomair één product en één
begrensde testopdracht. Per product kan maximaal één sessie tegelijk actief zijn. Zonder planbaar of
periodiek testwerk eindigt de functie als succesvolle no-op.

Andere modules kunnen geen testagent of teststap starten. Een nieuwe oplevering, epic op
**Controleren** of gebruikerssignaal wordt duurzaam gepubliceerd en tijdens een volgende sessie
gelezen.

## Interface met andere modules en services

Kwaliteitsbewaking gebruikt `processcontracts` voor stabiele DTO's en geversioneerde read-only
projecties. Zij importeert Productontwerp of Productplanning niet rechtstreeks. Browser-, log- en
testclients zijn interne adapters.

### Input

| Contract | Eigenaar | Gebruik |
|---|---|---|
| `StakeholderProfileView` | product-/overlegmodule | identiteit, rol en beslissingsmandaat achter kwaliteitsgrenzen en risico's |
| `TestableProductView` | productmodule | omgevingen, routes, toegestane accounts, databereik en testgrenzen |
| `StakeholderDirectionView` | product-/overlegmodule | expliciete kwaliteitsgrenzen en gemelde risico's |
| `EpicDefinitionView` | Productontwerp | bevroren scope, UX en succescriteria van de door Productplanning gekozen versie |
| `EpicExecutionView` | Productplanning | exacte versie, stories, voortgang en verzoek om volledige epiccontrole |
| `ProductStoryView` | Productplanning | acceptatiecriteria, verwacht gedrag en zelfstandige relevante UX per story |
| `BacklogItemView` | Productplanning | uitvoeringsstatus en bronkoppeling |
| `DeliveryResultView` | Software Factory-dispatcher | wat, waar en wanneer moet worden geverifieerd |
| `UserSignalView` | inbox/productmodule | oorspronkelijke melding met bron, context en bewijs |
| `UserSignalDispositionView` | inbox/productmodule | wat al met het signaal is gebeurd en aan welke resultaten het is gekoppeld |

De module leest daarnaast eigen bugs en testhistorie. Iedere sessie legt de gebruikte contractversies
en exacte geteste omgeving vast.

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `BugView` | aantoonbare bouw- of productafwijking | werkelijk en verwacht gedrag, reproduceerstappen, omgeving, bewijs, impact, ernst, status en bron-signaal-ID's |
| `StoryVerificationView` | oordeel over één concrete story of bugfix | backlogitem, bronversie, omgeving, resultaat, controles, bewijs en blokkade |
| `EpicVerificationView` | oordeel over de complete gebruikersverbetering | epic-ID en -versie, uitkomst, scope-/UX-dekking, succescriteria, bewijs, gaten en leerresultaat |
| `EpicCompletionGapView` | gedrag binnen de bevroren epic dat nooit in een story stond | scope- of UX-verwijzing, gebruikersimpact, ontbrekend gedrag en bewijs |
| `QualityOverviewView` | zichtbaar actueel kwaliteitsbeeld | recente dekking, risico's, open bugs en onderbelichte gebieden |
| `QualitySignalView` | groter productprobleem voor Productontwerp | patroon, betrokken bevindingen, impact, hypothese en gewenste onderzoeksvraag |
| `ProcessSessionPublication` | operationeel resultaat van de sessie | sessie-ID, product-ID, inputversies, publicatie-ID's en eindstatus |

Alleen Kwaliteitsbewaking schrijft bugs, verificaties, epicgaten en kwaliteitssignalen.
Productplanning verwerkt die output maar verandert haar niet.

## Storyverificatie

Een story- of bugfixoplevering krijgt:

- **Geslaagd** — afgesproken gedrag werkt in de bedoelde omgeving;
- **Afgekeurd** — gedrag wijkt aantoonbaar af;
- **Geblokkeerd** — controle is door omgeving, toegang of ontbrekende informatie niet mogelijk.

Bij **Afgekeurd** publiceert Kwaliteitsbewaking zo nodig een bug. Het herschrijft de story niet.

## Epicverificatie

Alle backlogitems van een epic op **Afgerond** is alleen het startsein voor de epiccontrole. De
controle gebruikt exact de door Productplanning bevroren `EpicDefinitionView` en beoordeelt:

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

Kwaliteitsbewaking sluit de epicuitvoering niet. Productplanning verwerkt deze uitkomst en is de
enige schrijver van de epicstatus.

## Bug, epicgat of nieuwe productkans

Kwaliteitsbewaking classificeert een ontbrekend of onjuist resultaat vóór publicatie:

| Situatie | Publicatie | Vervolg |
|---|---|---|
| Gedrag stond in een uitgevoerde story maar is verkeerd gebouwd | `BugView` | Productplanning neemt een bugfix op |
| Gedrag viel duidelijk binnen de bevroren epic, maar Productplanning maakte er nooit een story voor | `EpicCompletionGapView` | Productplanning maakt aanvullende stories |
| Alles is geleverd, maar de gebruikersverbetering is nog niet bewezen | `EpicVerificationView` met **Niet aantoonbaar** | epic blijft op **Controleren** |
| Alles werkt zoals ontworpen, maar de productaanname blijkt onjuist | `EpicVerificationView` met **Niet geslaagd** en `QualitySignalView` | Productplanning sluit met die uitkomst; Productontwerp leert |
| Gewenst gedrag valt buiten de bevroren scope | `QualitySignalView` of gebruikerssignaal | Productontwerp kan een vervolgepic maken |

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
- **Gepland** — Productplanning heeft er een backlogitem voor gepubliceerd;
- **In herstel** — de bugfix staat open in Software Factory;
- **Hertesten** — de fix is opgeleverd;
- **Opgelost** — de fix is in de juiste omgeving goedgekeurd;
- **Heropend** — de afwijking bestaat nog of is teruggekomen;
- **Ongeldig** — geen productafwijking, met zichtbare reden.

Kwaliteitsbewaking leidt **Gepland**, **In herstel** en **Hertesten** af uit gepubliceerde backlog- en
leveringsstatus, maar blijft zelf de enige schrijver van de bugstatus.

## Epicgatcontract

Een epicgat bevat minimaal:

- stabiel gat-ID, product-ID, epic-ID en exact epicversienummer;
- verwijzing naar de bevroren scope, UX-toestand of succesvoorwaarde;
- welk gedrag ontbreekt;
- welk gebruikersresultaat daardoor niet wordt bereikt;
- bewijs dat geen bestaande story dit gedrag afdekt;
- ernst of blokkerende werking voor epicafsluiting;
- status **Open**, **In stories verwerkt** of **Opgelost**.

Productplanning maakt de stories. Kwaliteitsbewaking markeert het epicgat pas **Opgelost** nadat het
ontbrekende gedrag is geleverd en opnieuw gecontroleerd.

## Interne entiteiten

- `QualitySession` — geclaimde en begrensde processessie;
- `TestStrategy` — kwaliteitsdoelen en risicoprioriteiten per product;
- `TestRotation` — wanneer routes en thema's voor het laatst zijn onderzocht;
- `TestAgenda` — doelen, omgeving en budget voor één sessie;
- `TestCase` — concrete controle of exploratieve opdracht;
- `TestObservation` — feitelijke waarneming;
- `EvidenceArtifact` — screenshot, log, trace of meetresultaat;
- `BugCandidate` en `Bug` — bevinding en duurzame levenscyclus;
- `EpicCoverageAssessment` — vergelijking van epic, UX, stories en geleverd gedrag;
- `EpicCompletionGap` — ontbrekende dekking binnen een bevroren epic;
- `StoryVerification` en `EpicVerification` — interne controles vóór publicatie;
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
4. **Verificatiecriticus en bugtriager** — reproduceert bevindingen, classificeert bugs/epicgaten,
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
7. **Patroon analyseren** — verwante bevindingen tot een kwaliteitssignaal vormen.

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

De module claimt één planbare opdracht, leest exacte versies van epic, uitvoering, stories,
backlogitems en oplevering, controleert de omgeving en registreert productversie en testaccount. Een
onbereikbare omgeving leidt tot **Geblokkeerd**, niet tot een productbug.

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
- controleert of ontbrekend gedrag een bouwfout, epicgat of nieuwe wens is;
- zoekt duplicaten;
- bepaalt ernst en gebruikersimpact;
- controleert bewijs op geheimen en persoonsgegevens;
- geeft het story- of epicoordeel;
- publiceert eigen objecten en projecties atomair.

## Planning en de HKH-backlog

Een sessie wordt planbaar door:

- een nieuwe Software Factory-oplevering;
- een bugfix op **Hertesten**;
- een epicuitvoering op **Controleren**;
- een nieuw of gewijzigd gebruikerssignaal of zijn afhandelstatus;
- een P0/P1-risico of verouderd kwaliteitsbeeld;
- de dagelijkse kernrouteplanning of testrotatie;
- lage backlogvoorraad, zodat bekende bevindingen tijdig worden gereproduceerd.

Kwaliteitsbewaking maakt geen bugs of epicgaten om de backlog kunstmatig tot tien te vullen.

## Fouten, hervatten en idempotentie

- Een storyverificatie is uniek voor backlogitem, bronversie, oplevering en omgeving.
- Een epicverificatie is uniek voor epicuitvoering, epicversie en geteste productversie.
- Herhaling werkt bewijs bij maar maakt geen duplicaat.
- Een technische testfout wordt apart geregistreerd en niet als productbug gepubliceerd.
- Een sessie kan na een verlopen claim worden hervat met dezelfde inputmomentopname.
- Parallelle testers schrijven alleen observaties; de criticus publiceert het definitieve resultaat.
- Gedeeltelijk bewijs blijft intern tot reproductie en privacycontrole zijn afgerond.

## Wanneer een sessie klaar is

Een sessie is klaar wanneer:

- ieder gekozen testgeval een resultaat of expliciete blokkade heeft;
- iedere publieke bug reproduceerbaar en van bewijs voorzien is;
- ieder epicgat aantoonbaar binnen de bevroren epic valt en niet door een story wordt gedekt;
- iedere verificatie naar exacte epic-, story-, opleverings- en omgevingsversies verwijst;
- testrotatie en kwaliteitsbeeld zijn bijgewerkt;
- publicaties atomair en geversioneerd beschikbaar zijn;
- de operationele sessiestatus en volgende plandatum zijn opgeslagen.
