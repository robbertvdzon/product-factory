# Product Factory v2 — Kwaliteitsbewaking MVP

Status: geïmplementeerd als de actieve MVP-provider in stap 7.

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
activeer verstreken retries en lees PENDING items van één product
                  │
                  ▼
controleer doelen en of de deployment alle oplevercommits bevat
                  │
                  ▼
claim gereed werk, registreer de testpoging en queue één Tester-AiTask
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

Applicatiecode zet eerst `BLOCKED`- of `FAILED`-workitems van het opgegeven product waarvan een
expliciete `retryAfter` is verstreken terug op `PENDING`. Alleen aantoonbaar tijdelijke technische
storingen krijgen zo’n tijdstip. `retryable=true` betekent dat bewust handmatig hervatten is toegestaan;
zonder `retryAfter` start er geen automatische herhaling. Daarna claimt of hervat zij de run voor dat
product en leest de gereedstaande `PENDING` workitems uit de vaste startmomentopname. Een ander
product kan gelijktijdig een eigen kwaliteitssessie uitvoeren.
Daarna leest zij per opdracht de exacte epic-, story-, bug-, opleverings- en signaalversies plus
`TestableProductDetails` en het actuele geheugen van `TESTER_MVP`. De processessie legt de exacte
bron- en geheugenversies vast.

Applicatiecode controleert vóór de AI-taak en vóór het registreren van een testpoging dat de
acceptatiedeployment alle vereiste oplevercommits bevat. Voor een epic zijn dat de commits van alle
`DONE`-stories in die epic; geannuleerde stories tellen niet mee. Voor storyverificatie of een
bugfixhertest geldt de eigen `Story.deliveredCommitSha`. Git-afstamming bepaalt dit: een latere
revision die alle commits bevat is geldig, alleen ongelijke SHA's zijn geen afwijzingsreden.
De gate en de AI-context gebruiken exact dezelfde bevroren stories en één bevroren deploymentrevision
per omgeving, zodat een gelijktijdige oplevering hun bewijs niet ongemerkt kan veranderen.

Een aantoonbaar achterlopende deployment houdt hetzelfde workitem `PENDING` met
`WAITING_FOR_DEPLOYMENT` en een concrete reden. De factory controleert hoogstens eens per minuut
opnieuw via `updated_at`, zonder AI-taak, `attemptCount`, nieuwe testpoging, bug of snapshot.
`retryAfter` blijft leeg: dit is wachten op oplevering, geen herhaling van een mislukte test. Zodra
de deployment gereed is start dezelfde opdracht automatisch en registreert zij pas haar testpoging.
Iedere uitgevoerde wachtcontrole verhoogt de werkversie zodat de automatische verwerking de
volgende controle kan oppakken. De wachttijd geldt ook bij een handmatig gestarte processessie.

Onbekende Git-afstamming, een ontbrekende oplevercommit, ontbrekende credentials, configuratie of
een ongeldige revisionresponse wordt handmatig hervatbaar `BLOCKED` zonder `retryAfter`. Zulke
startblokkades maken geen testpoging en overschrijven nooit historische pogingen. Alleen expliciete
tijdelijke transportfouten (timeout/connect) of HTTP 408/429/502/503/504 krijgen back-off. HTTP
401/403/404 en ongeclassificeerd falen krijgen geen automatische nieuwe test. De Tester controleert
daarna zelf ook bereikbaarheid, de werkelijk gedeployde revision en accountgrenzen vóór inhoudelijk
testen; gewone code bewaakt dat deze revision gedurende de test gelijk blijft.

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

Vanaf promptversie 4 krijgt de Tester een uitvoerbare browserstartcontrole: globale Node Playwright
via `NODE_PATH`, Chromium via `PLAYWRIGHT_BROWSERS_PATH` (standaard `/ms-playwright`) en headless
starten met `--no-sandbox`. Ontbrekende Python Playwright of een ontbrekend Chromium-commando in
`PATH` geldt niet als bewijs dat de browser ontbreekt. De startcontrole opent alleen `about:blank`;
vervolgens moet de Tester de echte schermhandelingen en vereiste viewports op acceptatie testen.
Zonder vereist schermbewijs én zonder bewezen defect blijft de verificatie `BLOCKED`.

De testlogincredential gaat uitsluitend naar het ingestelde relatieve loginendpoint op de
acceptatie-origin, zonder redirects en zonder globale browserheaders met de credential. De echte
PNG-screenshots gebruiken de gedeclareerde namen `/job/output/artifacts/evidence-01` tot en met
`evidence-50`, zonder extensie en met expliciet PNG-formaat. Een mislukte browserstart of nog niet
opgeleverde acceptatiefixture blijft een testblokkade; de Tester wijkt niet uit naar productie.

Vanaf promptversie 5 onderscheidt de Tester dit van een defect in een beloofde, als `DONE`
opgeleverde testvoorziening. Bij een juiste acceptatierevision en werkende login is een
reproduceerbare 404 op de gedocumenteerde, toegestane scenarioroute een bug in de bestaande story.
De uitkomst is `NEEDS_WORK` met route, revision, story-id en herhaald waargenomen respons als bewijs.
Tests die daardoor niet uitvoerbaar waren staan in `missingCoverage`; dezelfde oorzaak wordt niet
ook als `testBlocker` of `implementationGap` gemeld. De gewone bugfixcyclus vraagt één gerichte
`PLAN_BUGFIX` aan, zonder nieuwe epicstories te bedenken.

Vanaf promptversie 6 controleert de Tester vóór het eindrapport de werkelijk gemaakte
artifactbestanden. Het rapport noemt uitsluitend bestaande bestanden, ieder precies één keer;
gaten in de nummerreeks worden niet aangevuld met veronderstelde screenshots. Vanaf promptversie 7
vergelijkt hij ook alle letterlijk overgenomen `workItemId`-waarden met de bevroren opdrachten.
Het resultaatschema begrenst die waarden per sessie met een `enum` van exact de geclaimde IDs.

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

Het testercontract vanaf promptversie 3 houdt implementatie en testbewijs gescheiden:

- `testBlockers` benoemt onvoldoende bewijs/testdata, ontbrekende toegang, ontbrekende browser of
  andere testcapaciteit, of een ongeschikte testomgeving. Een acceptatiemock met maar één vergadering
  bewijst geen defect in gedrag voor meerdere vergaderingen. Ook het ontbreken van een browser is
  geen productdefect. Iedere testblokkade maakt de verificatie handmatig hervatbaar `BLOCKED`; zij
  publiceert geen bugs, ontwikkelstories of kwaliteitssnapshot en laat een epic `VERIFYING`.
  `retryAfter` blijft leeg voor deze structurele oorzaken en de workitem vraagt direct aandacht.
- `TRANSIENT_TECHNICAL_FAILURE` is vanaf promptversie 5 uitsluitend voor aantoonbaar tijdelijke
  technische storing, zoals een netwerk-timeout, tijdelijke HTTP 503 of rate limit. Het bewijs
  vermeldt waarom een latere poging zonder wijziging zinvol is. Alleen als alle testblokkades dit
  type hebben krijgt hetzelfde workitem een `retryAfter`; één structurele blokkade heeft voorrang.
  Een reproduceerbare 404, ontbrekende configuratie of credential, 401/403, uitgeputte testtijd of
  onbekende oorzaak valt hier niet onder.
- `implementationGaps` bevat alleen bewezen ontbrekend gedrag voor een letterlijk overgenomen
  bevroren epiccriterium, met observatie, bewijs, de IDs van alle beoordeelde `DONE`-stories en een
  uitleg waarom die stories dit gedrag niet afdekken. Gewone code controleert criterium en IDs en
  weigert criteria die al letterlijk door een opgeleverde story zijn afgedekt. De planner vergelijkt
  ook anders geformuleerde dekking opnieuw met de opgeleverde stories.
- `missingCoverage` is rapportage van resterende of ongeteste dekking en triggert geen plancommand.
  Een negatieve uitslag zonder bewezen implementatiegat of reproduceerbare bug wordt `BLOCKED`.
  Een negatieve bugfixhertest kan met bewijs dezelfde open bug behouden zonder een nieuwe bug.

Nog lopende taken van vóór promptversie 3 kunnen hun positieve verificatie publiceren. Negatieve
resultaten van die oude taken worden veilig `BLOCKED` zonder automatische herhaling. Ze kunnen
bewust via hetzelfde workitem opnieuw worden getest met het gestructureerde contract; oude
ongetypeerde dekkingsgaten maken daardoor na de uitrol geen ontwikkelwerk meer. Resultaten vanaf
versie 3 blijven compatibel bij de browserinstructies van versie 4 en retryclassificatie van versie 5.

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
productresultaat weerlegt. Ontbrekende gebruiksdata of een onbereikbare meetbron geven `BLOCKED`. Een vooraf bewezen
achterlopende deployment blijft `PENDING` totdat de wijzigingen daadwerkelijk beschikbaar zijn.

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
- Een achterlopende deployment blijft `PENDING` met `WAITING_FOR_DEPLOYMENT`; iedere minuut volgt
  een lichte versiecontrole totdat de vereiste oplevercommits beschikbaar zijn, zonder AI-testpoging.
- Alleen expliciet tijdelijke technische storingen volgen 15 minuten, 1 uur, 4 uur en daarna
  maximaal 24 uur back-off zonder maximaal aantal domeinpogingen. `retryable=true` alleen is geen
  opdracht om opnieuw te proberen: `retryAfter` is daarvoor vereist.
- Structurele of ongeclassificeerde blokkades en ongeldig testerresultaat krijgen geen `retryAfter`.
  Ze vragen direct aandacht; een herhaling zonder herstel zou dezelfde uitkomst geven.
- De Runtime beheert eigen begrensde uitvoeringspogingen. Na een definitieve taakfout plant kwaliteit
  alleen bij `PROVIDER_RATE_LIMIT` of `WORKER_LEASE_EXPIRED` een nieuwe test. Onbekende fouten,
  `ENGINE_FAILED` en uitgeputte uitvoerings-/testbudgetten blijven wachten op bewust herstel.
- Historische geplande retries zonder expliciet bewijs van tijdelijk falen worden bij deze wijziging
  stilgezet; eerdere pogingen, resultaten en handmatig hervatten blijven behouden.
- `retryQualityWorkItem(...)` behoudt `attemptCount` en historie, maakt het item direct `PENDING` en
  laat de UI daarna zo nodig de normale processessie starten.
- Een verlopen claim kan met dezelfde inputmomentopname worden hervat.
- Een gewijzigde doel- of omgevingsversie blokkeert publicatie en wacht op een bewuste herstart.
- Een technisch mislukte uitvoering krijgt binnen dezelfde `AiTask` een begrensde nieuwe attempt
  van AI-uitvoering.
- Een inhoudelijk ongeldig concept wordt niet gepubliceerd; er start geen tweede critic-agent.
- Reeds geldige resultaten uit een batch kunnen worden gepubliceerd wanneer een ander workitem
  aantoonbaar geïsoleerd faalt.

### Herstel van bewijsverwijzingen na een geslaagde test

Een rapport met verwijzingen naar niet-bestaande extra artifacts mag na beoordeling worden
hersteld via `repairQualityPublication(...)`. Dit is een expliciete beheerhandeling, geen automatische
retry. Alleen de opgegeven ontbrekende verwijzingen worden verwijderd; checks, uitkomsten, bugs en
`missingCoverage` blijven ongewijzigd. Ontbrekend noodzakelijk testbewijs vraagt aanvullende tests
en mag niet langs deze route worden goedgekeurd.

Een verkeerd gerapporteerd opdracht-ID kan alleen expliciet worden gecorrigeerd wanneer de
bronsessie precies één epicverificatie claimde en het rapport precies één resultaat bevat.
`replaceReportedWorkItemId` noemt exact de foutieve bronwaarde; het correcte ID komt uitsluitend uit
de oorspronkelijke claim. De foutieve waarde mag geen bestaand ander workitem zijn. De herstelreden
noemt beide IDs. Het onveranderde bronrapport en het gecorrigeerde rapport maken de mapping
controleerbaar. Deze uitzondering mag nooit resultaten uit een gemengde batch herverdelen.

Het command controleert de oorspronkelijke mislukte sessie, exacte werkversies en SHA-256 van het
opgeslagen rapport, artifactmanifest en bevroren context. Epic, opgeleverde stories en deployment
moeten nog overeenkomen met de uitgevoerde test. Alle behouden artifacts moeten duurzaam aanwezig
zijn, met de juiste inhoud, SHA-256 en MIME-type, en precies één keer in het rapport voorkomen.
Daarna doorloopt het gecorrigeerde rapport de normale verificatie- en domeinpublicatie.

Een aparte herstelsessie en auditregistratie bewaren bronverwijzingen, correctie, actor, reden en
idempotentiesleutel. Het oorspronkelijke AI-rapport, de mislukte processessie en afgesloten
testpogingen blijven intact. Het herstel start geen AI-taak en verhoogt het aantal testpogingen niet.
Een gewijzigd rapport, gewijzigde deployment of domeinversie blokkeert herstel.

De expliciete CLI-entrypoint is
`nl.vdzon.productfactory.operations.QualityPublicationRepairMain`. Voer deze alleen na beoordeling
uit in een eenmalige onderhoudsjob met dezelfde uitgerolde backendimage, configuratie en
artifactopslag, en een gemount command-JSON-bestand:

```sh
java -Dloader.main=nl.vdzon.productfactory.operations.QualityPublicationRepairMain \
  -cp /app/product-factory.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  /maintenance/command.json
```

Deze entrypoint schakelt alle drie achtergrondverwerkers en Flyway uit, bindt alleen lokaal op een
willekeurige poort, voert één command uit en sluit daarna de applicatie. Er is geen HTTP-endpoint.
Controleer daarna de audit, verificatie, bugstatus en epicstatus; een voltooid onderhoudsproces
alleen bewijst niet dat de epic is afgerond.

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
