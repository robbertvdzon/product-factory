# Product Factory v2 — integratie- en acceptatietesten

Status: eerste ontwerp van de testomgeving, testdata en externe simulators.

De integratie- en acceptatietesten moeten de echte modulegrenzen, databasebewerkingen, queues,
retries en gebruikersflows van Product Factory testen zonder echte AI-kosten, Software
Factory-stories of externe schrijfacties te veroorzaken.

Daarom gebruikt v2 een kleine eigen **Product Factory Testbed**. Dit is geen productmodule en draait
nooit in productie. Het Testbed simuleert externe systemen met toestand en tijdsverloop. Product
Factory gebruikt daarbij dezelfde publieke poorten en technische protocollen als in productie;
alleen de adapter aan de andere kant is vervangen.

WireMock mag in een gerichte contracttest worden gebruikt, maar is niet de centrale
acceptatievoorziening. Software Factory heeft een externe levenscyclus en krijgt daarom een
stateful simulator. AI-resultaten worden door de echte Agent Runtime-acceptatieserver server-side
voorbereid en gemockt, zodat productscenario's zonder laptop of Docker volledig via de UI
bestuurbaar blijven.

## Omgevingsgrens

| Afhankelijkheid | Integratietest | Acceptatie | Productie |
|---|---|---|---|
| Product Factory-modules | echte Maven-implementaties met hun interne Modulith-structuur | één echte appbuild met gekozen implementaties | één echte appbuild met gekozen implementaties |
| database | nieuwe in-memory database per test of testsuite | in-memory database, opnieuw te vullen via reset | duurzame ondersteunde productiedatabase |
| AI-uitvoering | echte Product Factory-façade/outbox tegen een Runtime v2-stub of testserver | echte façade tegen Agent Runtime-acceptatie | echte façade tegen Agent Runtime-productie |
| AI-provider | Runtime `MOCKED` met voorbereide antwoorden | Runtime `MOCKED` met voorbereide antwoorden | gedeelde Runtime-worker met `CODEX` of `CLAUDE` in Docker |
| Software Factory | `MockSoftwareFactory` uit Testbed | `MockSoftwareFactory` uit Testbed | echte Software Factory |
| Git | lokale tijdelijke testrepository | publieke repository read-only via HTTPS, zonder token | publieke repository read-only via HTTPS, zonder token |
| productomgeving | gecontroleerde lokale testsite indien nodig | synthetische testproductomgeving, nooit echte productie | geconfigureerde acceptatie en veilige productie-informatie |
| authenticatie | uitgeschakeld | uitgeschakeld | ingeschakeld |
| klok en schedules | bestuurbare testklok; geen achtergrondruns | automatische schedules standaard uit; handmatig via UI | echte klok en schedules |

De acceptatieomgeving krijgt geen Runtime-worker-, Runtime-admin-, productie-, Software Factory- of
Git-schrijftokens. Zij krijgt alleen een acceptatiegebonden Product Factory-consumentcredential en,
waar fixturebeheer dat vereist, een afzonderlijk gescopete Runtime-mockcredential. Egress is
standaard gesloten behalve HTTPS naar Agent Runtime-acceptatie en expliciet toegestane publieke
Git-hosts. Interne calls naar Testbed en de eigen Product Factory-services blijven toegestaan.

Bij het opstarten geldt een fail-closed controle. Met `environment = ACCEPTANCE`:

- moeten alle `AiJobConfiguration`s provider `MOCKED` gebruiken;
- moet `PF_AGENT_RUNTIME_URL` exact naar Agent Runtime-acceptatie wijzen;
- mag de Runtime-credential geen worker-, admin- of productiecredential zijn;
- moet het Software Factory-endpoint naar `MockSoftwareFactory` wijzen;
- moet authenticatie uitstaan;
- mogen geen externe schrijfcredentials aanwezig zijn;
- mogen Git-URL's alleen een toegestane publieke HTTPS-host gebruiken;
- mag geen echte productie-URL als testproductomgeving zijn ingesteld;
- moeten automatische processchedules uitstaan, tenzij een expliciete testsituatie ze tijdelijk
  inschakelt.

Een fout in één van deze controles voorkomt dat de omgeving start. Alleen een leeg token is dus
niet voldoende bescherming.

## Opbouw

```text
Acceptatie-UI
     │ scenario kiezen, resetten en stappen
     ▼
acceptance-only Test Control API
     ├───────────────> Testdatacoördinator ──> in-memory database
     ├───────────────> Agent Runtime Test Control ──> Runtime mockstore
     │
     └───────────────> Product Factory Testbed
Product Factory-processen ──> AI-façade/outbox ──> Agent Runtime-acceptatie
     │
     └── dispatcher ────────> MockSoftwareFactory
```

De Test Control API is een acceptance-only orchestratiefacade in Product Factory. Zij roept de
fixture-contributors van de eigen modules aan en bedient de interne scenario-interface van Testbed.
Testbed krijgt nooit databaseverbinding of modulecommand waarmee het producttoestand rechtstreeks
kan aanpassen. De Test Control API en alle reset- en scenariocommands bestaan alleen in
integratietests en in het acceptatieprofiel. De productieartifact bevat bij voorkeur de interfaces
maar registreert geen controllers, seeders of Testbed-adapters.

## Product Factory Testbed

Het Testbed is één kleine, zelfstandig te starten testapplicatie met `MockSoftwareFactory` en één
interne scenario-interface. De AI-mockstore hoort bij Agent Runtime-acceptatie en wordt door de
Product Factory Test Control-orchestratie via een afzonderlijk gescopete externe route
geconfigureerd. Testbed deelt geen Product Factory- of Runtime-database en schrijft nooit direct in
een productmodule.

### Test Control API voor de acceptatie-UI

```java
TestScenarioDetails getActiveScenario();
List<TestScenarioSummary> getAvailableScenarios();
void resetAcceptanceEnvironment(ResetAcceptanceEnvironmentCommand command);
void activateScenario(ActivateTestScenarioCommand command);
void advanceScenario(AdvanceTestScenarioCommand command);
void injectTestFault(InjectTestFaultCommand command);
```

`resetAcceptanceEnvironment(...)`:

1. stopt en annuleert uitsluitend acceptatietaken die nog lopen;
2. wist de in-memory Product Factory-data;
3. vult de vaste basisdataset opnieuw;
4. wist alle Testbed-stories, AI-responses, clocks en fouten;
5. activeert het gekozen scenario en de bijbehorende scenario-versie;
6. retourneert pas wanneer UI en backend dezelfde schone uitgangssituatie zien.

Reset is nooit beschikbaar in productie. In acceptatie krijgt de gebruiker vooraf een duidelijke
waarschuwing dat alle tijdelijke acceptatiewijzigingen verdwijnen.

De gedeelde acceptatieomgeving is één testlane. Reset of scenarioactivatie neemt een exclusieve,
zichtbare scenariolock. De UI toont wie of welke browsersessie de lock heeft en wanneer die verloopt.
Een tweede tester kan wel meekijken, maar niet tegelijk resetten of het scenario voortzetten.
Integratietests hebben ieder hun eigen geïsoleerde database en Testbed-instantie en kennen deze
onderlinge blokkade niet.

`advanceScenario(...)` voert precies één vooraf gedefinieerde externe stap uit, bijvoorbeeld een
Software Factory-story van `RUNNING` naar `DONE` zetten. Het command start niet stilzwijgend een
Product Factory-proces; de gebruiker start de volgende dispatcher- of processessie apart via de
normale UI-actie.

`injectTestFault(...)` is voor eenmalige, begrensde fouten zoals de eerstvolgende Software
Factory-call met HTTP 503 beantwoorden. Blijvende vrije scripts of willekeurige code zijn niet
toegestaan.

## Server-side Mock AI-uitvoering in Agent Runtime

Bij provider `MOCKED` maakt de Product Factory-façade een lokale correlatie/outbox en exact één
externe Runtime-job. De Agent Runtime-server handelt die job vóór de workergrens af. Product Factory
maakt geen eigen queueattempt, `AiWorkerSession`, lease, heartbeat of Dockercontainer.
Productprocessen gebruiken exact hetzelfde `requestAiTask(...)`-contract en verwerken het resultaat
tijdens hun volgende gewone run.

De Product Factory Test Control API biedt alleen in integratie en acceptatie een orchestratiefaçade
naar de gescopete Runtime test-controlroutes:

```text
GET    /api/test-control/ai/mock-responses
POST   /api/test-control/ai/mock-responses
DELETE /api/test-control/ai/mock-responses/{responseId}
DELETE /api/test-control/ai/mock-responses
```

De tester kan hiermee vóór een processessie een antwoord klaarzetten en de resterende antwoorden
bekijken of wissen. Product Factory vertaalt lokale `jobKey`, product-ID, scenario en stap naar een
opaque Runtime-testcorrelatie; deze domeinvelden horen niet in het productiejobcontract. Exacte
matches gaan voor algemene matches en gelijke matches volgen FIFO.
Het bevat `SUCCEEDED` met syntactisch geldige JSON en optionele artifacts, of `FAILED` met foutcode
en veilige melding. Bij gebruik valideert AI-uitvoering een succesvol antwoord opnieuw tegen het
responseschema van de concrete taak. Ontbreekt een match, dan faalt de taak expliciet met
`NO_MOCK_RESPONSE_CONFIGURED`; er bestaat geen stil succesvolle standaardrespons.

Ondersteunde productgerichte AI-situaties bevatten minimaal:

| Situatie | Te bewijzen gedrag |
|---|---|
| normaal succes | taak en resultaat worden duurzaam bewaard; processessie hervat |
| technisch ongeldig resultaat | AI-uitvoering weigert schemafout volgens retrybeleid |
| inhoudelijk ongeldig resultaat | procesmodule vraagt eventueel gericht herstel in een nieuwe taak |
| voorbereide technische fout | taak eindigt zichtbaar `FAILED` zonder domeinpublicatie |
| geen voorbereid antwoord | taak faalt fail-closed met `NO_MOCK_RESPONSE_CONFIGURED` |
| annulering | een geannuleerde taak accepteert geen later mockresultaat |
| job uitgeschakeld | de productsessie wordt `BLOCKED` met `AI_JOB_DISABLED`, maakt geen taak en hervat na inschakelen |
| taak geannuleerd maar domeinwerk geldig | de productsessie blijft zichtbaar `BLOCKED` en maakt later een vervangende taak |

Fixtures zijn gekoppeld aan een stabiele `scenarioKey`, `scenarioVersion`, `jobKey` en optionele
stap. Zij bevatten geen vrije productieprompt. Iedere fixture wordt in CI gevalideerd tegen het
response-schema van de bijbehorende job.

### Aparte Agent Runtime-contracttests

De normale Product Factory-acceptatieflow test geen laptopstoringen. Agent Runtime test in zijn
eigen repository claims, heartbeat, slaap, workerrestart, reconciliatie, retry, fencing, harde
attemptdeadline, inputattachments, outputartifactcollectie en een laat oud resultaat. Product
Factory test alleen zijn v2-clientcontract, outbox/idempotentie, statusvertaling en domeinhervatting.
Een credentialcontracttest bewijst gezamenlijk dat alleen toegestane environmentkeynamen in de job
staan en dat Product Factory of Runtime nooit waarden bewaart; de bewust geselecteerde waarden zijn
alleen in de tijdelijke agentcontainer leesbaar.

## MockSoftwareFactory

`MockSoftwareFactory` implementeert het echte externe contract dat de dispatcher gebruikt. Hij
heeft een eigen in-memory storyadministratie en ondersteunt dezelfde idempotentiesleutel als de
echte Software Factory.

De simulator kan:

- een `StoryDeliveryPackage` aannemen en een stabiel extern story-ID teruggeven;
- hetzelfde antwoord teruggeven bij herhaling van dezelfde idempotentiesleutel;
- open werk per product tonen;
- een story handmatig of automatisch door statusfasen laten lopen;
- een open story als geannuleerd of verwijderd teruggeven;
- oplevergegevens inclusief een geldige `deliveredCommitSha` teruggeven;
- als expliciet contractfoutscenario een geldig pakket ten onrechte weigeren;
- een tijdelijke timeout, HTTP 429 of HTTP 5xx simuleren;
- een succesvolle externe aanmaak gevolgd door een verloren response simuleren;
- een fout of ongeldig contractantwoord teruggeven.

Minimaal worden deze situaties als vaste scenario's meegeleverd:

| Situatie | Te bewijzen gedrag |
|---|---|
| story geaccepteerd | story wordt eerst atomair gereserveerd, één keer extern gemaakt en lokaal `IN_PROGRESS` |
| nog open werk | dispatcher verstuurt geen volgende backlogstory |
| story opgeleverd | dispatcher bewaart `deliveredCommitSha`, markeert lokaal `DONE` en het juiste kwaliteitswerk ontstaat |
| story extern geannuleerd | dispatcher markeert lokaal `CANCELLED`; na het overige werk volgt een feitelijke epicbeoordeling zonder storystatus **mislukt** |
| tijdelijke storing | dezelfde dispatchreservering, `DeliveryAttempt`, externe aanwezigheidscontrole en begrensde retry zonder dubbel extern werk |
| response verloren | idempotentie vindt de eerder aangemaakte story terug |
| contractbreuk of ongeldig antwoord | dispatcher blokkeert het product, meldt de softwarefout operationeel en maakt geen domein- of planningswerk |
| extern foutstadium | operationele fout blijft bij dispatcher en verandert geen storyinhoud |

De mock biedt bewust geen uitvoeringsvraag of answer-endpoint. Het v2-contract vereist dat Software
Factory ieder geldig, compleet storypakket accepteert en uitsluitend status `OPEN`, `DONE` of
`CANCELLED` teruggeeft. Een andere response is een contractbreuk die alleen de dispatcher blokkeert.

De simulator mag geen Productplanning-command aanroepen. Alleen de echte dispatcher vertaalt het
externe mockantwoord naar Product Factory-statussen. Daarmee test acceptatie de echte grens.

## Database en initiële testdata

De acceptatieomgeving gebruikt één in-memory database. Data hoeft een restart niet te overleven;
een herstart of reset levert juist dezelfde bekende uitgangssituatie op. De UI toont daarom altijd:

- dat de omgeving synthetische, tijdelijke data gebruikt;
- de actieve datasetversie en scenarioversie;
- het tijdstip van de laatste reset.

De basisdataset bevat minimaal:

- één actief synthetisch product met `ProductAssignment`, read-only publieke Git-URL en
  `TestableProductConfiguration` met een bestuurbaar revisionendpoint;
- actuele en historische `Decision`s;
- nieuwe, verwerkte en afgesloten `UserSignal`s;
- epics in relevante statussen;
- geprioriteerde productstories en bugfixstories in `TODO`, `IN_PROGRESS`, `DONE` en `CANCELLED`;
- `PlanningWorkItem`s en `QualityWorkItem`s in relevante statussen;
- retrybare `QualityWorkItem`s met verschillende `attemptCount`s, blokkaderedenen en
  `retryAfter`-tijdstippen, waaronder minimaal één item met **Aandacht nodig**;
- bugs, verificaties en meerdere `QualitySnapshot`s voor een zichtbare tijdlijn;
- een overleg met algemene en rolgerichte berichten, open en beantwoorde Stakeholdervragen,
  notulen en doorwerking;
- versieerbaar geheugen voor meerdere agentrollen;
- een actieve rolcatalogus en aan een overleg gekoppelde geheugenwijzigingen voor meerdere rollen;
- alle `AiJobConfiguration`s op provider `MOCKED` en voorbereide mockantwoorden voor de vaste
  beginscenario's;
- voorbeeldhistorie voor `ProcessSession`, `AiTask`, attempts en `DeliveryAttempt`.

Iedere module blijft eigenaar van haar eigen tabellen en levert een testfixture-contributor. Een
acceptance-only testdatacoördinator roept die contributors in vaste volgorde aan. Eén algemene
seeder schrijft dus niet buiten de modulegrenzen om in alle tabellen.

De dataset heeft een expliciete versie. Een codewijziging die publieke statussen, contracts of
flows verandert, past de fixtureversie en de bijbehorende assertions samen aan.

### Databaseverschil met productie

De in-memory database is geschikt om productflows via de UI snel en reproduceerbaar te testen, maar
bewijst niet automatisch dat alle productie-SQL en migraties op de productiedatabase werken.
Daarom bestaat daarnaast een kleine databasecompatibiliteitssuite die repositories en migraties
tegen een tijdelijke echte instantie van het gekozen productiedatabasesysteem uitvoert. Die
instantie leeft alleen tijdens CI, bevat uitsluitend synthetische data en is geen gedeelde externe
testservice.

## Git als echte read-only bron

Publieke productrepositories mogen in acceptatie echt worden gelezen via HTTPS. Daarvoor is geen
token nodig en er wordt ook geen token geconfigureerd. De procesmodule mag de bedoelde commit-SHA
vastzetten en in de `AiTask` bewaren. Runtime `MOCKED` checkt de repository niet uit; de
voorbereide fixture staat voor het modelresultaat. De echte Runtime-worker voert in productie clone,
fetch, detached checkout, log en bestandlezing zelf uit in de tijdelijke taakcontainer. Commit,
push, tag, merge en pull-requestacties zijn niet beschikbaar.

Voor reproduceerbaarheid legt iedere processessie de opgeloste commit-SHA vast. Een branch mag bij
de start naar de nieuwste commit wijzen, maar alle taken in die sessie gebruiken daarna dezelfde
SHA.

Integratietests gebruiken geen internet. Zij maken een tijdelijke lokale bare repository met
bekende commits, code en documentatie en testen dezelfde read-only Git-adapter daartegen.

Een afzonderlijke storingstest gebruikt een onbereikbare test-URL of foutgevende testadapter om te
bewijzen dat Git-fouten veilig zichtbaar worden. De normale acceptatieomgeving hoeft GitHub niet te
mocken, maar mag er nooit naartoe schrijven.

## Acceptatiescherm in de UI

Alleen wanneer de backend het acceptatieprofiel bevestigt, toont de frontend het scherm
**Acceptatietesten**. Een banner op iedere pagina vermeldt duidelijk dat het om tijdelijke
synthetische data zonder authenticatie gaat.

Het scherm bevat:

- actieve dataset-, scenario-, Testbed- en `ImplementationManifest`-versie;
- knop **Reset naar beginsituatie**;
- lijst met beschreven vaste scenario's en hun verwachte resultaat;
- knop om een scenario te activeren;
- een formulier en lijst om het volgende AI-mockantwoord per job en eventueel product klaar te
  zetten, te bekijken en te verwijderen;
- expliciete knoppen voor toegestane vervolgstappen, zoals **Software Factory-story afronden**,
  **Software Factory-story annuleren** en **Volgende externe call laten mislukken**;
- links naar de gewone proces-, backlog-, kwaliteit-, AI-task- en dispatcherweergaven;
- een tijdlijn van Testbed-interacties met requesttype, status en tijdstip, zonder secrets of
  volledige prompts;
- een checklist met de verwachte zichtbare uitkomst per stap.

Vaste kwaliteitsscenario's bewijzen daarnaast dat:

- verstreken `retryAfter` een retrybaar workitem bij de volgende kwaliteitsrun opnieuw `PENDING`
  maakt;
- de lijst met retrybaar testwerk op hoogste `attemptCount` staat;
- **Retry now** historie en telling behoudt, `retryAfter` leegmaakt en één normale kwaliteitsrun
  start of een al actieve run hergebruikt;
- een `NEEDS_WORK`-epiccontrole bugs, dekkingsgaten of beide naar het juiste plancommand vertaalt;
- een afgekeurde bugfixhertest de bug `OPEN` en de opgeleverde story `DONE` laat, waarna voor
  dezelfde bug een volgende gewone bugfixstory kan worden gepland;
- een door Software Factory geannuleerde story lokaal `CANCELLED` wordt en na afronding van het
  overige werk tot een complete feitelijke epicbeoordeling leidt;
- annulering vóór dispatchreservering geen externe story oplevert;
- een lang bestaande reservering na herstel eerst extern wordt opgezocht en bij aantoonbare
  afwezigheid opnieuw tegen de annuleringsmarker wordt gevalideerd;
- alleen een daadwerkelijk bestaande externe story na epicannulering als gestart doorloopt;
- een storyverificatie `DEPLOYMENT_PENDING` blijft zolang de synthetische doelomgeving nog een
  revision vóór `deliveredCommitSha` meldt en daarna zonder onterechte afkeuring hervat.

Vaste, versieerbare presets blijven de eenvoudige normale route. Voor gericht onderzoek mag de
tester via een schema-ondersteund formulier ook zelf de JSON-output van het volgende mockantwoord
invoeren; de backend valideert die vóór opslag en opnieuw tegen het taakresponseschema bij gebruik.
Vrije scripts zijn nooit toegestaan. Een technische beheerweergave mag voor diagnose de veilige
request- en response-envelop tonen.

Automatische schedules staan standaard uit. De tester kiest een product en start
`runProcessSession(productId)` en `runDispatchSession(productId)` bewust via de bestaande handmatige
UI-acties. Een apart schedulerscenario
kan duurzame `ProcessScheduleConfiguration`s laden, een bestuurbare klok vooruitzetten en precies
één tick uitvoeren om scheduling zelf te testen. De scenario's controleren minimaal meerdere
tijden op dezelfde dag, meerdere regels met verschillende dagen en tijden, een interval, een
uitgeschakeld schema, dubbele combinaties, ongeldige tijden, een schedulerbotsing, tijdzonegedrag en
dat na downtime hooguit één gemiste run wordt ingehaald. De acceptance-only omgevingsschakelaar
blijft leidend: gewone achtergrondpolling staat uit, ook als synthetische productdata een schema als
ingeschakeld toont.

## Integratietestpatroon

Een integratietest gebruikt dezelfde scenariofixtures en volgt steeds deze vorm:

1. start de echte betrokken Product Factory-modules met een lege in-memory database;
2. laad de kleinste passende versie van de basisdataset;
3. start Testbed met één vast scenario;
4. roep het publieke command of de REST-interface aan die een gebruiker of scheduler ook gebruikt;
5. laat Testbed zo nodig één of meer externe stappen uitvoeren;
6. draai een volgende processessie of dispatchersessie;
7. controleer publieke queries, duurzame status, idempotentie en zichtbare historie;
8. controleer dat geen onverwachte externe call is gedaan.

Tests mogen nooit rechtstreeks een gewenste eindstatus in een moduletabel zetten wanneer juist de
flow naar die status wordt getest. Testbed verandert alleen zijn eigen externe toestand; Product
Factory moet zelf reageren.

## MVP en uitgebreid na elkaar vergelijken

Acceptatie draait maar één `product-factory-app` tegelijk. Een variantvergelijking gebeurt daarom
sequentieel en niet met twee schrijvers op dezelfde database:

1. deploy de appbuild met de MVP-implementatie;
2. reset naar een vastgelegde dataset- en scenarioversie;
3. voer de gekozen UI-scenario's uit en exporteer resultaten en operationele metingen;
4. deploy de appbuild met de uitgebreide implementatie;
5. reset naar exact dezelfde dataset- en scenarioversie;
6. voer dezelfde scenario's uit en vergelijk de exports.

De UI toont bij iedere run het `ImplementationManifest` en iedere `ProcessSession` bewaart haar
implementatie-ID en -versie. Daardoor kan een resultaat nooit per ongeluk aan de verkeerde variant
worden toegeschreven. De uitgebreide implementatie wordt niet automatisch definitief: een volgende
build kan opnieuw de MVP selecteren wanneer die aantoonbaar beter werkt.

## Contract- en scenariobeheer

- De echte adapter en simulator implementeren hetzelfde versioned contract.
- Contracttests draaien tegen zowel de echte adapterclient als de simulator.
- Scenario's en fixtures staan als leesbare versieerbare bestanden bij de testcode.
- Iedere scenariofixture heeft een stabiele sleutel, beschrijving, beginvoorwaarden, stappen en
  verwachte publieke uitkomsten.
- Een schema- of contractwijziging laat CI falen wanneer simulator en productieadapter uiteenlopen.
- De acceptatie-UI toont uitsluitend scenario's die bij de gedeployde Testbed- en contractversie
  horen.
- Nieuwe externe integraties worden pas compleet genoemd wanneer zij een integratietestadapter,
  acceptatiesimulator en fail-closed omgevingscontrole hebben.

## Wat deze omgeving wel en niet bewijst

De productgerichte integratie- en acceptatieomgeving bewijst onder meer:

- modulecontracten en databaseovergangen;
- duurzame lokale AI-correlatie/outbox en resultaatverwerking met bestuurbare Runtime-mockoutput;
- dispatchercontract, idempotentie en foutafhandeling;
- agentvraag, automatische overlegagenda, rolgericht antwoord, beantwoording en atomaire
  geheugenwijzigingen voor meerdere rollen zonder dat gewone procesagents elkaars geheugen zien;
- end-to-end UI-flows met voorspelbare externe reacties;
- historie, resetbaarheid en operationele zichtbaarheid.

De omgeving bewijst niet dat Codex of Claude inhoudelijk goede productkeuzes maakt, dat de echte
Runtime-worker na een echte OS- of Dockerstoring herstelt, dat GitHub altijd beschikbaar is of dat de
echte Software Factory exact dezelfde implementatiefouten heeft. Agent Runtime-contracttests bewijzen de
technische queue-, lease-, heartbeat-, harde deadline-, restart-, retry- en fencinglogica. Aanvullende bewust gestarte
smoke-tests mogen de echte Runtime-worker gebruiken, maar gebruiken geen acceptatiedata en zijn nooit
een voorwaarde voor de veilige acceptatieomgeving.

## Invarianten

- Acceptatie gebruikt uitsluitend synthetische, resetbare data.
- Acceptatie heeft geen externe schrijfcredentials.
- AI wordt vóór de workergrens in Agent Runtime server-side gemockt via dezelfde externe job- en
  resultaatgrens;
  Software Factory wordt stateful gesimuleerd via haar echte externe contract.
- Auth is uit en dit is overal zichtbaar.
- Git is uitsluitend publiek, HTTPS en read-only; integratietests gebruiken een lokale repository.
- Automatische schedules staan standaard uit en tijd kan gecontroleerd worden voortgezet.
- Testbed schrijft nooit rechtstreeks in een Product Factory-module.
- Dezelfde scenariofixtures ondersteunen automatische integratietests en handmatige UI-acceptatie.
- Productie weigert Testbed-, reset-, seed- en mockfunctionaliteit.
- Iedere toekomstige externe service krijgt dezelfde test- en fail-closed grens.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Frontend](../stakeholder/frontend.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Software Factory-dispatcher](../processen/software-factory-dispatcher.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Maven en Spring Modulith](maven-en-spring-modulith.md)
