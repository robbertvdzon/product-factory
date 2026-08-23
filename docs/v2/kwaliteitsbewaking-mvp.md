# Product Factory v2 — Kwaliteitsbewaking MVP

Status: voorstel voor de eerste implementatie.

Deze implementatie gebruikt exact de publieke
[Kwaliteitsbewaking-API](kwaliteitsbewaking.md). Eén algemene Tester-agent bereidt het testwerk voor,
bedient de toegestane testmiddelen, beoordeelt wat hij aantreft en levert gestructureerde concepten
voor bugs en verificaties. Gewone applicatiecode claimt werk, valideert output, publiceert en voert
vervolgcommands uit.

## Uitgangspunten

- Eén processessie gebruikt één agentrol: **Tester**.
- Dezelfde agent verwerkt de vaste batch `QualityWorkItem`s sequentieel binnen één sessie.
- Gerichte queueopdrachten gaan voor een eenvoudige periodieke smokecontrole.
- Er zijn geen aparte coördinator, kwaliteitsspecialist, bugtriager of verificatiecriticus.
- De agent test daadwerkelijk de gedeployde applicatie; code lezen is alleen context.
- Een mogelijke bug wordt binnen dezelfde sessie nogmaals vanaf een bekende uitgangssituatie
  gereproduceerd voordat hij publiceerbaar is.
- Gewone code controleert contracten, bronversies, secrets en persoonsgegevens.
- Iedere niet-lege testsessie publiceert precies één nieuwe `QualitySnapshot`.
- Er is geen extra intern testgeheugen, geen slimme testrotatie en geen verzameling interne
  teststrategie-entiteiten; de Tester gebruikt wel het centrale geheugen van haar eigen agentrol.

## Agent

De MVP heeft één type agent:

### Tester

De Tester:

- controleert eerst bereikbaarheid, productversie en toegestane testaccount;
- leest de exacte story, epic, bug of het gebruikerssignaal bij ieder workitem;
- gebruikt Git-code, tests en documentatie alleen om risico's en verwacht gedrag te begrijpen;
- bedient browser-, API-, log- en andere toegestane testadapters;
- controleert hoofdroute, belangrijke alternatieven, lege en fouttoestanden;
- hertest een bugfix vanaf de oorspronkelijke reproduceerstappen;
- doorloopt bij epicverificatie de volledige bevroren gebruikersverbetering;
- classificeert een bevinding als bouwfout, ontbrekende epicdekking, nieuwe wens of testblokkade;
- maakt reproduceerbare bug- en verificatieconcepten met bewijs;
- geeft een gebruikerssignaalonderzoek altijd een expliciete uitkomst;
- levert één gestructureerd `QualitySessionDraft` terug.

`QualitySessionDraft`, tijdelijke teststappen en ruwe observaties bestaan alleen binnen de sessie en
krijgen in de MVP geen eigen tabellen.

## Minimale duurzame gegevens

De MVP bewaart binnen Kwaliteitsbewaking alleen:

- `QualityWorkItem`;
- `Bug` en haar lifecycle;
- onveranderlijke `Verification`s;
- onveranderlijke `QualitySnapshot`s;
- `ProcessSession`;
- minimale technische lock- en idempotentiegegevens.

Screenshots, logs, traces en andere bewijsassets worden met veilige metadata aan de betreffende bug
of verificatie gekoppeld. Zij vormen geen afzonderlijk publiek productobject.

Het permanente geheugen van de rol `TESTER_MVP` staat in
[Agentgeheugen](agentgeheugen.md). De runtime voegt alleen de actuele items van deze rol aan de
agenttaak toe en registreert de exact gelezen geheugenversies. De Tester kan geen geheugen van een
andere rol opvragen.

## Verloop van één processessie

```text
claim run en PENDING QualityWorkItems
                  │
                  ▼
lees exacte doelen, omgeving en productversie
                  │
                  ▼
één Tester voert opdrachten sequentieel uit
                  │
                  ▼
reproduceren, classificeren en conceptresultaten maken
                  │
                  ▼
deterministische contract-, privacy- en versiecontrole
                  │
                  ▼
atomair publiceren + vervolgcommands + QualitySnapshot
```

### Stap 1 — claimen en omgeving controleren

Applicatiecode claimt de modulebrede run en alle `PENDING` workitems uit de vaste startmomentopname.
Daarna leest zij per opdracht de exacte epic-, story-, bug-, opleverings- en signaalversies plus
`TestableProductDetails` en het actuele geheugen van `TESTER_MVP`. De processessie legt de exacte
bron- en geheugenversies vast.

De Tester controleert bereikbaarheid, geteste productversie en accountgrenzen vóór inhoudelijk
testen. Een onbereikbare omgeving of ontbrekende toegang maakt het workitem `BLOCKED` en wordt nooit
een productbug.

### Stap 2 — opdrachten ordenen

De Tester verwerkt normaal in deze volgorde:

1. P0/P1-bugfixhertests en andere urgente gerichte opdrachten;
2. gequeue'de epicverificaties;
3. storyverificaties en overige bugfixhertests;
4. onderzoeken van gebruikerssignalen;
5. een vaste periodieke smokecontrole wanneer daar in de sessie ruimte voor is.

Dit is geen tweede duurzame prioriteitsentiteit. De geclaimde workitems en hun resultaat blijven de
enige zichtbare werkstatus.

### Stap 3 — testen

Per opdracht voert dezelfde Tester het passende minimale maar volledige werk uit:

- **story** — acceptatiecriteria, hoofdroute en relevante lege, laad- en fouttoestanden;
- **bugfix** — oorspronkelijke reproductie, verwacht herstel en aangrenzende regressie;
- **epic** — complete route, bevroren UX, samenhang tussen stories, grenzen en succescriteria;
- **gebruikerssignaal** — gemelde situatie reproduceren en één publieke signaaluitkomst kiezen;
- **periodiek** — een vooraf geconfigureerde kleine set kritieke gebruikersroutes.

Niet ieder storyworkitem onderzoekt automatisch alle kwaliteitsdimensies. Expliciete
toegankelijkheids-, privacy-, beveiligings-, performance- of responsive eisen uit de story of epic
worden wel altijd meegenomen.

### Stap 4 — reproduceren en classificeren

Bij een mogelijke productafwijking herhaalt de Tester de relevante stappen binnen dezelfde sessie
vanaf een bekende uitgangssituatie. Het concept bevat werkelijk gedrag, verwacht gedrag, omgeving,
productversie, bewijs, impact en voorgestelde ernst.

De Tester classificeert ontbrekend gedrag als:

- bouwfout binnen afgesproken storygedrag;
- dekkingsgat binnen de bevroren epic zonder bijbehorende story;
- nieuwe wens buiten de bevroren scope;
- niet aantoonbaar resultaat;
- technische of toegangsblokkade.

De agent schrijft nog niets naar de publieke entiteiten en roept geen modulecommands aan.

### Stap 5 — deterministisch valideren en publiceren

Gewone code controleert minimaal:

- exacte doel-, bron-, opleverings- en omgevingsversies;
- toegestane verificatie-uitkomst voor het doeltype;
- reproduceerstappen en bewijs voor iedere publieke bug;
- onderbouwing van ernst en gebruikersimpact;
- dat een dekkingsgat aantoonbaar binnen de bevroren epic valt;
- dat een nieuwe wens niet als bug of dekkingsgat wordt gepubliceerd;
- verwijdering of afscherming van secrets en persoonsgegevens;
- idempotentiesleutels voor publicaties en vervolgcommands.

Geldige bugs en verificaties worden atomair gepubliceerd. Daarna volgen precies de commands uit het
publieke contract, zoals `requestBugfix(...)`, `requestEpicGapPlanning(...)`,
`recordEpicVerification(...)` en `recordSignalInvestigation(...)`.

Na een werkelijk uitgevoerde niet-lege testsessie wordt uit de gevalideerde publieke gegevens
precies één nieuwe `QualitySnapshot` opgebouwd. Een no-op of uitsluitend technische startfout maakt
geen snapshot.

## Wat bewust niet in de MVP zit

- geen Testcoördinator, Functionele tester, Kwaliteitsspecialist of Verificatiecriticus;
- geen parallelle testagents;
- geen onafhankelijk tweede-agentonderzoek van een mogelijke bug;
- geen duurzame `TestStrategy`, `TestRotation`, `TestAgenda` of `TestCase`;
- geen duurzame ruwe `TestObservation`s, coverage assessments of verification drafts;
- geen extra interne geheugenentiteit naast het centrale geheugen van de eigen agentrol;
- geen intelligente kwaliteitsrotatie over thema's, apparaten en productgebieden;
- geen aparte patroonanalyse-agent.

Een eenvoudige vaste smokeconfiguratie en de bestaande kwaliteitshistorie zijn voor de MVP genoeg.
De publieke eisen aan bewijs, verificaties, bugs en epicoordelen blijven ongewijzigd.

## Fouten en hervatten

- Een technische toolfout wordt geen productbevinding.
- Een verlopen claim kan met dezelfde inputmomentopname worden hervat.
- Een gewijzigde doel- of omgevingsversie blokkeert publicatie en wacht op een volgende run.
- Een technisch mislukte modelaanroep mag idempotent met dezelfde input worden herhaald.
- Een inhoudelijk ongeldig concept wordt niet gepubliceerd; er start geen tweede critic-agent.
- Reeds geldige resultaten uit een batch kunnen worden gepubliceerd wanneer een ander workitem
  aantoonbaar geïsoleerd faalt.

## Wanneer de MVP voldoende is

De MVP is bruikbaar zolang één Tester consequent:

- gerichte opdrachten volledig uitvoert;
- bugs reproduceerbaar en goed geclassificeerd publiceert;
- story- en epicresultaten correct onderscheidt;
- dekkingsgaten en nieuwe wensen niet verwart;
- belangrijke expliciete kwaliteitsgrenzen niet mist;
- controleerbare kwaliteitssnapshots oplevert.

Meetbare problemen met testdekking, specialistische risico's, onafhankelijkheid of contextgrootte
bepalen welke uitgebreide rol als eerste nodig is.

## Gerelateerde documenten

- [Kwaliteitsbewaking-API](kwaliteitsbewaking.md)
- [Kwaliteitsbewaking — uitgebreide implementatie](kwaliteitsbewaking-uitgebreid.md)
- [Productplanning-API](productplanning.md)
- [Software Factory-dispatcher](software-factory-dispatcher.md)
- [Agentgeheugen](agentgeheugen.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
