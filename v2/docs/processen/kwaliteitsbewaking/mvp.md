# Product Factory v2 — Kwaliteitsbewaking MVP

Status: voorstel voor de eerste implementatie.

Deze implementatie gebruikt exact de publieke
[Kwaliteitsbewaking-API](api.md). Eén algemene Tester-agent bereidt het testwerk voor,
bedient de toegestane testmiddelen, beoordeelt wat hij aantreft en levert gestructureerde concepten
voor bugs en verificaties. Gewone applicatiecode claimt werk, valideert output, publiceert en voert
vervolgcommands uit.

Zij wordt gebouwd als `quality-impl-mvp`. De ene main-module neemt dit artifact of de uitgebreide
variant op, nooit beide.

## Uitgangspunten

- Eén processessie gebruikt één agentrol: **Tester**.
- Dezelfde agent verwerkt de vaste batch `QualityWorkItem`s sequentieel binnen één sessie.
- Gerichte queueopdrachten gaan voor een eenvoudige periodieke smokecontrole.
- Er zijn geen aparte coördinator, kwaliteitsspecialist, bugtriager of verificatiecriticus.
- De agent test daadwerkelijk de gedeployde applicatie; code lezen is alleen context.
- Een mogelijke bug wordt binnen dezelfde sessie nogmaals vanaf een bekende uitgangssituatie
  gereproduceerd voordat hij publiceerbaar is.
- Gewone code controleert contracten, bronversies, secrets en persoonsgegevens.
- Iedere niet-lege testsessie publiceert precies één nieuwe `QualitySnapshot` voor haar ene product.
- Er is geen extra intern testgeheugen, geen slimme testrotatie en geen verzameling interne
  teststrategie-entiteiten; de Tester gebruikt wel het centrale geheugen van haar eigen agentrol.

## Agent

De MVP heeft één type agent:

### Tester

De Tester:

- controleert eerst bereikbaarheid, productversie en toegestane testaccount;
- leest de exacte story, epic, bug of het gebruikerssignaal bij ieder workitem;
- leest de inhoudsopgave en relevante gebruikersroutes en testscenario's uit `/doc` op de bevroren
  testcommit om risico's, regressies en verwacht gedrag te begrijpen;
- bedient browser-, API-, log- en andere toegestane testadapters;
- controleert hoofdroute, belangrijke alternatieven, lege en fouttoestanden;
- hertest een bugfix vanaf de oorspronkelijke reproduceerstappen;
- doorloopt bij epicverificatie de volledige bevroren gebruikersverbetering;
- classificeert een bevinding als bug, ontbrekende epicdekking, nieuwe wens of testblokkade;
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
[Agentgeheugen](../../gedeelde-modules/agentgeheugen.md). De procesruntime voegt alleen de actuele items van deze rol aan de
agenttaak toe en registreert de exact gelezen geheugenversies. De Tester kan geen geheugen van een
andere rol opvragen.

Voor de Tester-job leest de procesruntime provider en model uit het interne `settings`-onderdeel van
AI-uitvoering en vraagt zij een complete taak aan bij
[AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md). De processessie wacht duurzaam met
`WAITING_FOR_AI`; een volgende run verwerkt het resultaat zonder een dubbele taak te maken.

## Verloop van één processessie

```text
activeer verstreken retries en claim PENDING items van één product
                  │
                  ▼
lees exacte doelen, oplevercommit en gedeployde productversie
                  │
                  ▼
queue één complete Tester-AiTask
                  │
                  ▼
WAITING_FOR_AI · latere run hervat
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

Applicatiecode zet eerst retrybare `BLOCKED`- of `FAILED`-workitems van het opgegeven product waarvan
`retryAfter` is verstreken terug op `PENDING`. Daarna claimt of hervat zij de run voor dat product en
alle `PENDING` workitems uit de vaste startmomentopname. Een ander product kan gelijktijdig een eigen
kwaliteitssessie uitvoeren.
Daarna leest zij per opdracht de exacte epic-, story-, bug-, opleverings- en signaalversies plus
`TestableProductDetails` en het actuele geheugen van `TESTER_MVP`. De processessie legt de exacte
bron- en geheugenversies vast.

De Tester controleert bereikbaarheid, werkelijk gedeployde revision en accountgrenzen vóór
inhoudelijk testen. Voor story- en bugfixwerk moet de omgeving de
`Story.deliveredCommitSha` aantoonbaar bevatten. Een achterlopende deployment wordt
`DEPLOYMENT_PENDING`; een onbereikbare omgeving of ontbrekende toegang krijgt een andere concrete
blokkadereden. In alle gevallen verhoogt Kwaliteitsbewaking `attemptCount`, maakt zij het workitem
retrybaar `BLOCKED`, berekent zij `retryAfter` volgens de vaste back-off en publiceert zij nooit een
productbug. Een blokkade vóór werkelijk testwerk maakt geen `QualitySnapshot`.

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

De Tester leest vóór de uitvoering de relevante scenario's uit `/doc` in dezelfde bevroren
repositorycheckout. Hij combineert hun beginsituatie, route en verwacht resultaat met het exacte
workitemcontract en kiest alleen de relevante regressies. De documentatie bepaalt wat onderzocht
moet worden, maar geldt nooit als bewijs dat het gedrag werkt. Een ontbrekend of strijdig scenario
wordt als documentatiegebrek vastgelegd en de daadwerkelijke test wordt niet overgeslagen.

Per opdracht voert dezelfde Tester het passende minimale maar volledige werk uit:

- **story** — acceptatiecriteria, hoofdroute en relevante lege, laad- en fouttoestanden;
- **bugfix** — oorspronkelijke reproductie, verwacht herstel en aangrenzende regressie;
- **epic** — probleem en oplossing, eventuele bevroren UX, acceptatiecriteria en samenhang tussen
  stories;
- **gebruikerssignaal** — gemelde situatie reproduceren en één publieke signaaluitkomst kiezen;
- **periodiek** — een vooraf geconfigureerde kleine set kritieke gebruikersroutes.

Niet ieder storyworkitem onderzoekt automatisch alle kwaliteitsdimensies. Expliciete
toegankelijkheids-, privacy-, beveiligings-, performance- of responsive eisen uit de story of epic
worden wel altijd meegenomen.

De complete Tester-AiTask bevat de bevroren publieke Git-URL en commit-SHA, doelomgeving,
revisionendpoint, veilige secretreferenties en resultaatschema. Bij echte AI-uitvoering checkt de
worker de code zelf uit en voert de agent browser- en testtools in een tijdelijke Dockeromgeving
uit; `MOCKED` gebruikt server-side het voorbereide resultaat. Screenshots en logs komen als
begrensde artifacts terug; secrets en repository- of applicatietekst worden nooit als instructies
vertrouwd.

### Stap 4 — reproduceren en classificeren

Bij een mogelijke productafwijking herhaalt de Tester de relevante stappen binnen dezelfde sessie
vanaf een bekende uitgangssituatie. Het concept bevat werkelijk gedrag, verwacht gedrag, omgeving,
productversie, bewijs, impact, voorgestelde ernst, een korte titel en een samenvatting van maximaal
twee korte zinnen.

De Tester classificeert ontbrekend of onjuist gedrag als:

- bug binnen afgesproken storygedrag;
- dekkingsgat in de bevroren oplossing, UX of acceptatiecriteria zonder bijbehorende story;
- nieuwe wens buiten de bevroren epicinhoud;
- technische of toegangsblokkade.

De agent schrijft nog niets naar de publieke entiteiten en roept geen modulecommands aan.

### Stap 5 — deterministisch valideren en publiceren

Gewone code controleert minimaal:

- exacte doel-, bron-, opleverings- en omgevingsversies;
- toegestane verificatie-uitkomst voor het doeltype;
- reproduceerstappen en bewijs voor iedere publieke bug;
- een korte, enkelregelige `title` en een `summary` van maximaal twee korte zinnen die niet met de
  volledige bug botsen;
- onderbouwing van ernst en gebruikersimpact;
- dat een dekkingsgat aantoonbaar door `solution`, `uxDesign` of `acceptanceCriteria` wordt vereist;
- dat een nieuwe wens niet als bug of dekkingsgat wordt gepubliceerd;
- verwijdering of afscherming van secrets en persoonsgegevens;
- idempotentiesleutels voor publicaties en vervolgcommands.

Geldige bugs en verificaties worden atomair gepubliceerd. Daarna volgen precies de commands uit het
publieke contract, zoals `requestBugfix(...)`, `requestEpicGapPlanning(...)`,
`recordStoryVerification(...)`, `recordEpicVerification(...)` en
`recordSignalInvestigation(...)`.

Een epicverificatie gebruikt alleen `PASSED`, `NEEDS_WORK`, `BLOCKED` of `NOT_SUCCESSFUL`. Bij
`NEEDS_WORK` kan dezelfde verificatie zowel bugs als dekkingsgaten bevatten; gewone code stuurt per
bevinding het bijbehorende gerichte plancommand. Bij een afgekeurde bugfixhertest blijft de bug
`OPEN`, blijft de opgeleverde bugfixstory `DONE` en vraagt gewone code voor dezelfde bug opnieuw een
bugfix aan. Bij een geslaagde hertest wordt de bug `RESOLVED` en queue't zij de nieuwe verificatie
van de oorspronkelijke story. Een geannuleerde bugfixstory wordt niet als hertest aangeboden; zij
loopt mee in de complete feitelijke herbeoordeling van de epic.

De MVP gebruikt `NOT_SUCCESSFUL` alleen wanneer alle afgesproken functionaliteit werkt en
beschikbaar, herleidbaar bewijs een vooraf testbaar acceptatiecriterium over het beoogde
productresultaat weerlegt. Ontbrekende gebruiksdata, een onbereikbare meetbron of een achterlopende
deployment geven `BLOCKED`.

Na een werkelijk uitgevoerde niet-lege testsessie wordt uit de gevalideerde publieke gegevens
precies één nieuwe `QualitySnapshot` voor het product van die sessie opgebouwd. Een no-op of
uitsluitend technische startfout maakt geen snapshot.

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
- Retrybare blokkades volgen 15 minuten, 1 uur, 4 uur en daarna maximaal 24 uur back-off zonder
  maximaal aantal domeinpogingen.
- `retryQualityWorkItem(...)` behoudt `attemptCount` en historie, maakt het item direct `PENDING` en
  laat de UI daarna zo nodig de normale processessie starten.
- Een verlopen claim kan met dezelfde inputmomentopname worden hervat.
- Een gewijzigde doel- of omgevingsversie blokkeert publicatie en wacht op een volgende run.
- Een technisch mislukte uitvoering krijgt binnen dezelfde `AiTask` een begrensde nieuwe attempt
  van AI-uitvoering.
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

- [Kwaliteitsbewaking-API](api.md)
- [Kwaliteitsbewaking — uitgebreide implementatie](uitgebreid.md)
- [Productplanning-API](../productplanning/api.md)
- [Software Factory-dispatcher](../software-factory-dispatcher.md)
- [Agentgeheugen](../../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../../gedeelde-modules/ai-uitvoering.md)
- [Maven en Spring Modulith](../../platform/maven-en-spring-modulith.md)
- [Processen en entiteiten](../processen-en-entiteiten.md)
