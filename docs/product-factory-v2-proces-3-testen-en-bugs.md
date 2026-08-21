# Product Factory v2 — proces 3: Testen en bugs

Status: eerste ontwerp van modulegrens en interne werking.

Dit document werkt de module Testen en bugs uit. De black-boxinterface in
[Product Factory v2 — eerste opzet](product-factory-v2-eerste-opzet.md) is leidend.

## Verantwoordelijkheid

Proces 3 bewaakt voortdurend de kwaliteit van het werkende product. Het verifieert opleveringen,
onderzoekt belangrijke gebruikersroutes en publiceert reproduceerbare bugs. Wanneer meerdere
bevindingen één groter productprobleem laten zien, publiceert het een structureel kwaliteitssignaal
voor Productontwikkeling.

De module is eigenaar van:

- teststrategie, testrotatie en dekkingsbeeld;
- testsessies, testgevallen, observaties en bewijs;
- bugs, ernst en herstelstatus;
- verificatieresultaten voor productstories en bugfixes;
- structurele kwaliteitssignalen;
- het eigen agent- en procesgeheugen.

De module bepaalt niet welke bug of productstory als volgende wordt uitgevoerd en wijzigt geen epic,
story of backlogitem.

## Publieke procesfunctie

De enige uitvoerende ingang is:

```java
void runProcessSession();
```

Alleen de scheduler gebruikt deze functie. De module claimt zelf atomair één product en één
begrensde testopdracht. Per product kan maximaal één processessie tegelijk actief zijn. Zonder
planbaar of periodiek testwerk eindigt de functie als succesvolle no-op.

Andere modules kunnen geen specifieke testagent of teststap starten. Een nieuwe oplevering,
Stakeholdersignaal of backlogstatus wordt duurzaam gepubliceerd en tijdens de volgende sessie
gelezen.

## Interface met andere modules en services

Proces 3 gebruikt `processcontracts` voor stabiele DTO's en geversioneerde read-only projecties. Het
importeert proces 1 of 2 niet rechtstreeks. De concrete browser-, log- en testclients zijn interne
adapters.

### Input

| Contract | Eigenaar | Hoe verkregen | Gebruik |
|---|---|---|---|
| `TestableProductView` | productmodule | read-only query bij sessiestart | omgevingen, routes, toegestane accounts, databereik en testgrenzen |
| `StakeholderDirectionView` | product-/overlegmodule | actieve publicaties | expliciete kwaliteitsgrenzen en gemelde risico's |
| `EpicView` | proces 1 | gepubliceerde versie | gewenste uitkomst en succescriteria |
| `ProductStoryView` | proces 1 | bronversie van geleverd werk | acceptatiecriteria en verwacht gedrag |
| `BacklogItemView` | proces 2 | actuele projectie | lokale uitvoeringsstatus en bronkoppeling |
| `DeliveryResultView` | dispatcher/proces 2 | nieuwe of gewijzigde oplevering | wat, waar en wanneer moet worden geverifieerd |
| `UserSignalView` | inbox/productmodule | open gemelde problemen | input voor gerichte of exploratieve tests |

De module leest daarnaast eigen open bugs en testhistorie. Een testsessie legt alle gebruikte
contractversies en de exacte geteste omgeving vast.

### Output

| Contract | Betekenis | Minimale inhoud |
|---|---|---|
| `BugView` | aantoonbare afwijking die proces 2 kan prioriteren | werkelijk en verwacht gedrag, reproduceerstappen, omgeving, bewijs, impact, ernst, status en versie |
| `VerificationResultView` | oordeel over één concrete oplevering | backlogitem, bronversie, omgeving, resultaat, controles, bewijs en blokkade |
| `QualityOverviewView` | zichtbaar actueel kwaliteitsbeeld | laatste dekking, belangrijke risico's, open bugs en onderbelichte gebieden |
| `QualitySignalView` | structureel probleem voor proces 1 | patroon, betrokken bugs/tests, gebruikersimpact, hypothese en gewenste onderzoeksvraag |
| `ProcessSessionPublication` | operationeel resultaat van de sessie | sessie-ID, product-ID, gebruikte inputversies, publicatie-ID's en eindstatus |

Alleen proces 3 schrijft bugs, verificaties en kwaliteitssignalen. Proces 2 kan een bug in de backlog
opnemen, maar verandert de inhoud of ernst niet. Proces 1 kan een kwaliteitssignaal gebruiken voor
een epic, maar verandert het oorspronkelijke signaal niet.

## Bugcontract en levenscyclus

Een gepubliceerde bug bevat minimaal:

- stabiel bug-ID, product-ID en versie;
- korte titel en gebruikersimpact;
- werkelijk en verwacht gedrag;
- reproduceerstappen en vereiste uitgangssituatie;
- geteste omgeving, productversie en tijdstip;
- screenshot, log, netwerkspoor of ander bewijs;
- ernst P0, P1, P2 of P3 met reden;
- relatie met story, oplevering en eventueel soortgelijke bugs;
- herstelstatus.

De herstelstatus is:

- **Nieuw** — bevinding bestaat intern maar is nog niet reproduceerbaar;
- **Uitvoerbaar** — reproduceerbaar en compleet genoeg voor een bugfix;
- **Gepland** — proces 2 heeft er een backlogitem voor gepubliceerd;
- **In herstel** — de bijbehorende bugfix staat open in Software Factory;
- **Hertesten** — de fix is opgeleverd;
- **Opgelost** — proces 3 heeft de fix in de juiste omgeving goedgekeurd;
- **Heropend** — de afwijking bestaat nog of is teruggekomen;
- **Ongeldig** — geen productafwijking, met zichtbare reden.

Proces 3 leidt **Gepland**, **In herstel** en **Hertesten** af uit de gepubliceerde backlog- en
leveringsstatus, maar blijft zelf de enige schrijver van de bugstatus.

## Interne entiteiten

De volgende entiteiten blijven binnen proces 3:

- `QualitySession` — geclaimde en begrensde processessie;
- `TestStrategy` — vaste kwaliteitsdoelen en risicoprioriteiten per product;
- `TestRotation` — wanneer routes en thema's voor het laatst zijn onderzocht;
- `TestAgenda` — gekozen doelen, omgeving en budget voor één sessie;
- `TestCase` — concrete controle of exploratieve opdracht;
- `TestObservation` — feitelijke waarneming tijdens uitvoering;
- `EvidenceArtifact` — screenshot, log, trace of meetresultaat;
- `BugCandidate` — bevinding vóór reproduceerbaarheid en deduplicatie;
- `Bug` — duurzame afwijking en levenscyclus;
- `Verification` — interne controle die tot een publiek resultaat leidt;
- `QualityPattern` — clustering van verwante bugs en observaties;
- `QualityMemory` — lessen over risicogebieden, testaanpak en dekkingsgaten;
- `AgentRun` — input, promptversie, output, fout en verbruik van één agenttaak.

Testsessies en ruwe observaties zijn geen publieke interfacestatus. Alleen hun gevalideerde conclusie
wordt gepubliceerd.

## Agents

Een processessie gebruikt vier vaste agentrollen:

1. **Testcoördinator** — kiest agenda, omgeving, risico's en benodigde testers.
2. **Functionele tester** — controleert gebruikersroutes, acceptatiecriteria, lege toestanden en
   foutpaden.
3. **Kwaliteitsspecialist** — rouleert tussen toegankelijkheid, responsiviteit, performance,
   beveiliging, privacy en betrouwbaarheid.
4. **Bugtriager en verificatiecriticus** — reproduceert bevindingen, zoekt duplicaten, bepaalt ernst
   en keurt het publieke resultaat goed.

De functionele tester en kwaliteitsspecialist werken parallel op gescheiden testtaken en bij voorkeur
gescheiden testdata. De triager werkt sequentieel nadat hun observaties beschikbaar zijn. Bij een
gerichte opleveringscontrole kan de kwaliteitsspecialist worden overgeslagen als het risicoprofiel
dat verantwoordt; de triager blijft verplicht.

## Soorten processessies

De module kiest precies één hoofdsoort:

1. **Oplevering verifiëren** — acceptatiecriteria en relevante regressie controleren.
2. **Bugfix hertesten** — oorspronkelijke reproductie en aangrenzende regressie uitvoeren.
3. **Dagelijkse kernroutes testen** — belangrijkste gebruikersresultaten bewaken.
4. **Kwaliteitsrotatie uitvoeren** — een onderbelicht thema of apparaat onderzoeken.
5. **Stakeholdersignaal onderzoeken** — gemeld probleem gericht proberen te reproduceren.
6. **Bugpatroon analyseren** — verwante bugs tot een structureel kwaliteitssignaal vormen.

Nieuwe opleveringen en P0/P1-signalen gaan voor de periodieke rotatie. Een sessie blijft begrensd en
plant resterend werk voor een volgende aanroep.

## Verloop van één processessie

```text
claim product, opdracht en omgeving
                 │
                 ▼
testcoördinator maakt agenda
                 │
        ┌────────┴────────┐
        ▼                 ▼
functionele tester   kwaliteitsspecialist
        └────────┬────────┘
                 ▼
triage, reproductie en deduplicatie
                 │
                 ▼
verificatie + bugs + kwaliteitsbeeld
                 │
                 ▼
atomair publiceren en rotatie bijwerken
```

### Stap 1 — claimen en omgeving controleren

De module:

1. claimt één planbare testopdracht met een database-lock;
2. leest de exacte versies van oplevering, story, epic en backlogitem;
3. controleert of de testomgeving bereikbaar en toegestaan is;
4. registreert productversie, omgeving en testaccount;
5. kiest testgevallen op basis van risico en nog ontbrekende dekking.

Een onbereikbare omgeving leidt tot een geblokkeerd verificatieresultaat, niet tot een productbug.

### Stap 2 — testen

Bij een productstory test de functionele tester minimaal de acceptatiecriteria, de belangrijkste
gebruikersroute en relevante fout- en lege toestanden. Bij een bugfix begint hij met de oorspronkelijke
reproduceerstappen en controleert daarna het aangrenzende gedrag.

De kwaliteitsspecialist kiest het meest relevante rotatiethema. Niet elke sessie probeert alle
kwaliteitsdimensies volledig te testen.

### Stap 3 — triage en verificatie

De triager:

- reproduceert iedere mogelijke bug onafhankelijk;
- vergelijkt met bestaande bugs en voegt bewijs toe aan een duplicaat;
- onderscheidt productfout, omgevingsfout, testdatafout en onduidelijke verwachting;
- bepaalt ernst op basis van impact, bereik, omweg, veiligheid en datarisico;
- controleert dat bewijs geen geheimen of onnodige persoonsgegevens bevat;
- geeft een oplevering het resultaat **Geslaagd**, **Afgekeurd** of **Geblokkeerd**.

Een afgekeurde productstory levert een verificatieresultaat en zo nodig één of meer bugs op. De
triager herschrijft de oorspronkelijke acceptatiecriteria niet.

### Stap 4 — patronen en kwaliteitssignalen

Wanneer meerdere bugs of tests hetzelfde onderliggende probleem suggereren, maakt de module een
`QualityPattern`. Een publiek kwaliteitssignaal bevat geen oplossing als voldongen besluit, maar wel:

- het waargenomen patroon;
- betrokken bug- en testsessie-ID's;
- geraakte gebruikers en ernst;
- mogelijke onderliggende oorzaak als hypothese;
- waarom Productontwikkeling dit als groter productprobleem moet onderzoeken.

### Stap 5 — atomair publiceren

De module schrijft eerst eigen bugs, verificaties en testhistorie, daarna hun geversioneerde
projecties in `processcontracts`, en als laatste sessiestatus en testrotatie. Een screenshot of log is
pas via een publieke verwijzing zichtbaar nadat de inhoud is gecontroleerd.

## Planning en de HKH-backlog

Een sessie wordt planbaar door:

- een nieuwe Software Factory-oplevering;
- een bugfix met status **Hertesten**;
- een nieuw Stakeholdersignaal;
- een P0/P1-risico of verouderd kwaliteitsbeeld;
- de dagelijkse kernrouteplanning;
- een open plek in de testrotatie;
- een lage backlogvoorraad, zodat bekende maar nog niet uitgewerkte bevindingen tijdig worden
  gereproduceerd en gepubliceerd.

Proces 3 maakt geen bugs om de backlog kunstmatig tot tien te vullen. Alleen een aantoonbare,
reproduceerbare afwijking krijgt de status **Uitvoerbaar**.

## Fouten, hervatten en idempotentie

- Iedere verificatie is uniek voor backlogitem, bronversie, oplevering en omgeving.
- Herhaling met dezelfde combinatie werkt het bewijs bij maar maakt geen tweede verificatie.
- Een technische testfout wordt apart geregistreerd en wordt niet als productbug gepubliceerd.
- Een sessie kan na een verlopen claim worden hervat met dezelfde inputmomentopname.
- Parallelle testers schrijven alleen observaties; de triager publiceert het definitieve resultaat.
- Gedeeltelijk bewijs blijft intern tot reproductie en privacycontrole zijn afgerond.

## Wanneer een sessie klaar is

Een sessie is klaar wanneer:

- ieder gekozen testgeval een resultaat of expliciete blokkade heeft;
- iedere publieke bug reproduceerbaar, gededupliceerd en van bewijs voorzien is;
- iedere verificatie naar de exacte oplevering en bronversie verwijst;
- testrotatie en kwaliteitsbeeld zijn bijgewerkt;
- publicaties atomair en geversioneerd beschikbaar zijn;
- de operationele sessiestatus en volgende plandatum zijn opgeslagen.
