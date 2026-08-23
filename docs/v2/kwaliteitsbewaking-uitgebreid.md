# Product Factory v2 — Kwaliteitsbewaking uitgebreide implementatie

Status: doelontwerp voor een latere uitgebreide implementatie.

Deze implementatie gebruikt exact de publieke
[Kwaliteitsbewaking-API](kwaliteitsbewaking.md). Zij breidt de
[MVP](kwaliteitsbewaking-mvp.md) intern uit met gespecialiseerde rollen, parallelle testtaken,
risicogestuurde testrotatie, onafhankelijke kritiek en permanent geheugen per agentrol.

## Interne entiteiten

Naast de publieke module-entiteiten kan de uitgebreide implementatie gebruiken:

- `TestStrategy` — kwaliteitsdoelen en risicoprioriteiten per product;
- `TestRotation` — wanneer routes, apparaten en thema's voor het laatst zijn onderzocht;
- `TestAgenda` — doelen, omgeving en budget voor één sessie;
- `TestCase` — concrete controle of exploratieve opdracht;
- `TestObservation` — feitelijke ruwe waarneming;
- `EvidenceArtifact` — screenshot, log, trace of meetresultaat;
- `BugCandidate` — mogelijke afwijking vóór reproductie en publicatie;
- `EpicCoverageAssessment` — vergelijking van epic, UX, stories en geleverd gedrag;
- `VerificationDraft` — story-, epic- of signaalcontrole vóór publicatie;
- `QualityPattern` — clustering van verwante bevindingen;
- `AgentRun` — input, promptversie, output, fout en verbruik van één agenttaak.

Ruwe observaties en interne beoordelingen steken de modulegrens niet over. Alleen gevalideerde
`Bug`s, `Verification`s en `QualitySnapshot`s worden publieke productwaarheid.

## Agents

Een processessie kan vier vaste agentrollen gebruiken:

1. **Testcoördinator** — kiest agenda, omgeving, risico's en benodigde controles.
2. **Functionele tester** — controleert gebruikersroutes, stories, lege toestanden en foutpaden.
3. **Kwaliteitsspecialist** — rouleert tussen UX-samenhang, toegankelijkheid, responsiviteit,
   performance, beveiliging, privacy en betrouwbaarheid.
4. **Verificatiecriticus en bugtriager** — reproduceert bevindingen, classificeert bugs en
   dekkingsgaten, bepaalt ernst en keurt publieke resultaten goed.

Alleen `runProcessSession()` mag deze agents starten. Niet iedere kleine storycontrole gebruikt alle
rollen. Bij een complete epicverificatie zijn beide uitvoerende testrollen en de criticus verplicht.

Iedere rol heeft in [Agentgeheugen](agentgeheugen.md) haar eigen permanente geheugen. De runtime
leidt de vaste `AgentRoleKey` uit vertrouwde configuratie af en geeft een agent alleen actuele items
van die rol. De Testcoördinator kan dus niet het geheugen van de Functionele tester,
Kwaliteitsspecialist of Verificatiecriticus lezen. Rollen delen alleen de expliciete sessie-input,
agenda, observaties en handoffs.

## Soorten processessies

1. **Story verifiëren** — acceptatiecriteria en relevante regressie controleren.
2. **Bugfix hertesten** — oorspronkelijke reproductie en aangrenzend gedrag controleren.
3. **Epic verifiëren** — de complete bevroren gebruikersverbetering controleren.
4. **Dagelijkse kernroutes testen** — belangrijkste gebruikersresultaten bewaken.
5. **Kwaliteitsrotatie uitvoeren** — een onderbelicht thema, apparaat of productgebied onderzoeken.
6. **Gebruikerssignaal onderzoeken** — een gemeld probleem reproduceren.
7. **Patroon analyseren** — verwante bevindingen als kwaliteitspatroon beoordelen.

Gerichte P0/P1-opdrachten gaan voor periodieke rotatie. Een gequeue'de epicverificatie gaat voor
losse exploratieve tests wanneer alle benodigde omgevingen beschikbaar zijn.

## Verloop van één processessie

```text
claim product, opdrachten en omgeving
                  │
                  ▼
Testcoördinator maakt risicogestuurde agenda
                  │
         ┌────────┴────────┐
         ▼                 ▼
functionele tester   kwaliteitsspecialist
         └────────┬────────┘
                  ▼
Verificatiecriticus: reproductie, dekking en classificatie
                  │
                  ▼
bugs/verificaties atomair publiceren
                  │
                  ▼
vervolgcommands + QualitySnapshot + rotatie bijwerken
```

De functionele tester en kwaliteitsspecialist werken parallel op gescheiden opdrachten uit dezelfde
vaste inputmomentopname. De criticus werkt sequentieel nadat hun observaties beschikbaar zijn. Alleen
de publicatiestap schrijft publieke entiteiten.

### Stap 1 — claimen en agenda kiezen

Applicatiecode claimt de modulebrede run en vaste batch `QualityWorkItem`s. De Testcoördinator leest:

- exacte epic-, story-, bug-, opleverings- en signaalversies;
- testomgevingen, productversie, toegestane accounts en datagrenzen;
- actuele bugs, verificaties en kwaliteitshistorie;
- teststrategie, rotatie en bekende risico's;
- zo nodig read-only Git-code, tests en documentatie.

De coördinator maakt één begrensde `TestAgenda`. Een onbereikbare omgeving wordt een blokkade en
geen productbug.

### Stap 2 — parallel testen

De Functionele tester controleert:

- storyacceptatiecriteria, hoofdroute en relevante alternatieven;
- lege, laad-, fout- en uitzonderingssituaties;
- oorspronkelijke reproduceerstappen bij een bugfix;
- volledige route, storysamenhang, UX en succescriteria bij een epiccontrole;
- de concrete gemelde situatie bij een gebruikerssignaal.

De Kwaliteitsspecialist kiest risicogestuurd relevante dimensies. Een kleine storysessie hoeft niet
alles te testen; een epiccontrole dekt ten minste alle expliciete toegankelijkheids-, privacy-,
beveiligings-, performance-, responsiviteits- en betrouwbaarheidsgrenzen uit de epic.

Beide rollen bewaren alleen `TestObservation`s en `EvidenceArtifact`s. Zij publiceren geen bug of
verificatie.

### Stap 3 — onafhankelijk classificeren

De Verificatiecriticus en bugtriager:

- reproduceert mogelijke bugs onafhankelijk;
- controleert of ontbrekend gedrag een bouwfout, dekkingsgat, nieuwe wens of blokkade is;
- zoekt duplicaten en verwante patronen;
- vergelijkt epic, UX, alle stories en werkelijk gedrag in een `EpicCoverageAssessment`;
- bepaalt ernst en gebruikersimpact;
- controleert bewijs op secrets en persoonsgegevens;
- geeft het story-, epic- of signaaloordeel;
- keurt `BugCandidate`s en `VerificationDraft`s goed of af.

Bij herstelbare bewijsproblemen is één gerichte extra testopdracht toegestaan. Daarna publiceert de
criticus of blokkeert het resultaat.

### Stap 4 — publiceren en doorwerken

Goedgekeurde `Bug`s en `Verification`s worden atomair en geversioneerd opgeslagen. Daarna roept de
module de betekenisvolle vervolgcommands uit het publieke contract idempotent aan.

Na een sessie waarin daadwerkelijk is getest bouwt gewone code precies één `QualitySnapshot` uit de
gevalideerde publieke gegevens. Vervolgens werkt de module `TestRotation` bij. Een rol kan na
succesvolle validatie via gewone applicatiecode een geheugenactie voor haar eigen rol laten
uitvoeren. Rotatie of rolgeheugen verandert nooit oude snapshots.

## Testrotatie en intern leren

De `TestRotation` voorkomt dat alleen recent gewijzigde routes aandacht krijgen. Zij kan onder meer
rouleren over:

- kritieke gebruikersroutes;
- browsers, schermgroottes en invoermethoden;
- toegankelijkheid;
- privacy en beveiliging;
- performance en betrouwbaarheid;
- foutafhandeling en herstelgedrag.

Terugkerende bugs, dekkingsgaten en testblokkades kunnen een intern `QualityPattern` en een voorstel
voor toevoegen, vervangen of intrekken van geheugen voor de betrokken eigen rol opleveren. Gewone
code valideert dat voorstel en schrijft het via Agentgeheugen. Andere testrollen kunnen dit geheugen
niet lezen. Alleen wanneer een patroon productbetekenis heeft, registreert de module via de
productmodule een zichtbaar `UserSignal` van categorie `QUALITY_PATTERN`.

## Parallelisatie en publicatiegrens

- Parallelle agents werken altijd op dezelfde vaste publieke inputmomentopname en ieder met alleen
  het eigen rolgeheugen.
- Zij schrijven alleen ruwe observaties en bewijs, nooit publieke conclusies.
- De criticus is de enige interne rol die publicatie mag voorstellen.
- Gewone code valideert daarna nog contract, versies, privacy en idempotentie.
- Eén sessie publiceert per exact doel en omgeving maximaal één verificatie-uitkomst.

## Fouten en hervatten

- Gedeeltelijke observaties kunnen na een verlopen claim worden hervat.
- Een technische test- of toolfout blijft apart van productbevindingen.
- Een veranderde doel- of productversie blokkeert publicatie met de oude momentopname.
- Een extra bewijsronde behoudt dezelfde bronrelaties en auditgeschiedenis.
- Gedeeltelijk bewijs blijft intern tot reproductie en privacycontrole zijn afgerond.
- Nieuw queuewerk tijdens de run blijft voor de volgende sessie staan.

## Wanneer een sessie klaar is

Een uitgebreide kwaliteitssessie is klaar wanneer:

- ieder gekozen testgeval een resultaat of expliciete blokkade heeft;
- iedere publieke bug onafhankelijk reproduceerbaar en van bewijs voorzien is;
- ieder dekkingsgat aantoonbaar binnen de bevroren epic valt;
- iedere verificatie naar exacte bron-, opleverings- en omgevingsversies verwijst;
- ieder gebruikerssignaalonderzoek een zichtbare uitkomst of blokkade heeft;
- de criticus alle publieke concepten heeft beoordeeld;
- testrotatie en eventuele gevalideerde geheugenacties voor de betrokken eigen rollen zijn
  bijgewerkt;
- precies één nieuwe snapshot is opgeslagen na werkelijk testwerk;
- publicaties en vervolgcommands atomair of idempotent herstelbaar zijn.

## Gerelateerde documenten

- [Kwaliteitsbewaking-API](kwaliteitsbewaking.md)
- [Kwaliteitsbewaking — MVP](kwaliteitsbewaking-mvp.md)
- [Productontwerp-API](productontwerp.md)
- [Productplanning-API](productplanning.md)
- [Software Factory-dispatcher](software-factory-dispatcher.md)
- [Agentgeheugen](agentgeheugen.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
