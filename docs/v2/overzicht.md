# Product Factory v2 — overzicht

Product Factory helpt één product steeds verder te verbeteren. De klant voor wie het product wordt
gemaakt is de **Stakeholder**. Die geeft het doel en de richting aan. Product Factory onderzoekt,
ontwerpt, plant en controleert het werk. Software Factory bouwt de stories één voor één.

Dit document beschrijft de hele route in eenvoudige taal. De precieze module-interfaces, agents,
queues en interne werking staan in de documenten onderaan.

## De Stakeholder is de klant

Per product is er één Stakeholder: de klant voor wie we het product maken. De Stakeholder is een
actor en wordt niet als apart productobject opgeslagen.

De Stakeholder communiceert met Product Factory via de gebruikersinterface. De UI vertaalt iedere
actie naar een publiek command op de module die het betrokken productobject bezit; de Stakeholder
schrijft nooit rechtstreeks in de database.

De Stakeholder:

- geeft het productdoel en de harde grenzen;
- kan feedback, zorgen, kansen en testtoegang leveren;
- kan de richting en prioriteit op ieder moment corrigeren;
- neemt grote beslissingen die langdurig richting geven;
- kan een Factory-besluit later aanpassen of intrekken.

Agents mogen adviseren, doorvragen en gevolgen uitleggen. De expliciete wil van de Stakeholder is
uiteindelijk leidend. Binnen die richting mag Product Factory gewone, omkeerbare proceskeuzes zelf
maken. Inlog-, contact- en autorisatiegegevens horen bij technisch accountbeheer en niet bij de
productinterfaces.

## De publieke productbegrippen

Dit zijn de productobjecten die mensen zien en die modulegrenzen mogen oversteken. Interne
werkdocumenten, prompts en agentadministratie horen hier niet bij.

| Begrip | Eenvoudige betekenis |
|---|---|
| `Product` | Het product waaraan Product Factory werkt. |
| `ProductAssignment` | Het doel, de doelgroep, de harde grenzen en de publieke Git-URL van het product. |
| `UserSignal` | Feedback, een probleem, zorg, kans of observatie, met zichtbare verwerking en uitkomst. |
| `Decision` | Een grote, blijvende keuze die meerdere toekomstige sessies richting geeft. |
| `Epic` | Een complete en behapbare gebruikersverbetering, inclusief scope, succescriteria en UX-ontwerp. |
| `Story` | Eén zelfstandig uitvoerbaar stuk productwerk of bugfixwerk voor Software Factory. |
| `Bug` | Een reproduceerbare afwijking tussen verwacht en werkelijk gedrag. |
| `Verification` | Onveranderlijk bewijs en een oordeel over een story, epic of gebruikerssignaal. |
| `QualitySnapshot` | Een onveranderlijke momentopname van de aantoonbare productkwaliteit na een kwaliteitssessie. |
| `Meeting` | Een overleg met de Stakeholder, inclusief agenda, gesprek en gecontroleerde uitkomst. |
| `AgentMemoryItem` | Een permanente, versieerbare herinnering van precies één agentrol binnen dit product. |

De **backlog** is geen apart object. Het is de lijst van stories met status `TODO` of
`IN_PROGRESS`, geordend op `sequenceNumber`.

## De hele route

In gewone taal gebeurt het volgende:

1. De Stakeholder legt uit voor wie het product is, wat het moet bereiken en welke grenzen gelden.
2. Feedback, zorgen en kansen worden als gebruikerssignalen bewaard.
3. Productontwerp onderzoekt het product en maakt een complete epic met het benodigde UX-ontwerp.
4. Productplanning bevriest de gekozen epicversie en verdeelt de hele epic in zelfstandige stories.
5. Alle stories met status `TODO` of `IN_PROGRESS` vormen samen één geprioriteerde backlog.
6. De dispatcher stuurt de bovenste uitvoerbare story naar Software Factory.
7. Wanneer Software Factory de story heeft opgeleverd, wordt de story `DONE` en kan gericht
   testwerk worden klaargezet.
8. Wanneer alle stories van een niet-geannuleerde epic zijn opgeleverd, wordt een controle van de
   complete epic klaargezet.
9. Kwaliteitsbewaking controleert of het product echt werkt en of de epic de bedoelde verbetering
   voor de gebruiker heeft bereikt.
10. Bij een bouwfout komt er een bugfixverzoek. Bij ontbrekend werk binnen de epic komt er een
    verzoek voor aanvullende stories. Daarna wordt opnieuw getest.
11. Bij een geslaagde epiccontrole sluit Productontwerp de epic af.

```text
Stakeholder + gebruikerssignalen
               │
               ▼
        Productontwerp
               │ complete Epic + UX
               ▼
        Productplanning
               │ geordende Story-lijst
               ▼
 Software Factory-dispatcher
               │ één complete Story
               ▼
        Software Factory
               │ oplevering
               ▼
      Kwaliteitsbewaking
               │ bewijs, Bug of ontbrekend werk
               └─────────────── terug naar de juiste eigenaar
```

De backlog mag leeg zijn en heeft geen kunstmatige maximumgrootte. Een epic kan twee stories of
dertig stories opleveren. Meerdere epics mogen tegelijk actief zijn. De Stakeholder kan een urgente
epic handmatig hoger laten plaatsen; een story die al `IN_PROGRESS` is loopt normaal door.

## Vier uitvoerende onderdelen

Productontwerp, Productplanning en Kwaliteitsbewaking zijn de drie intelligente procesmodules. Alleen
hun geplande of handmatig gestarte `runProcessSession()` mag voor dat proces een AI-taak aanvragen.
Per module kan maximaal één aanroep tegelijk uitvoeren. Een werkelijk botsende handmatige start
krijgt een fout en een schedulerbotsing wordt overgeslagen en geregistreerd.

Een AI-taak draait asynchroon. De processessie bewaart het taak-ID, krijgt status
`WAITING_FOR_AI` en houdt geen thread of technische lock vast. Een volgende schedule-run hervat
dezelfde sessie zodra het resultaat klaarstaat. Bij een handmatige start van een wachtende sessie
wordt diezelfde sessie veilig gecontroleerd en niet als tweede sessie gestart.

De Software Factory-dispatcher is het vierde uitvoerende onderdeel, maar geen intelligent proces. Hij
gebruikt geen agents en neemt geen productbesluiten.

Het Agentgeheugen is een ondersteunende module en geen vijfde proces. Iedere agentrol leest bij een
taak uitsluitend haar eigen actuele geheugen. De Stakeholder kan alle rolgeheugens via de UI
bekijken en corrigeren.

AI-uitvoering is eveneens een ondersteunende module. Zij kent geen agentrollen of productobjecten,
maar alleen complete taken met een expliciete provider en model. Iedere taak staat eerst duurzaam in
een databasequeue. De laptopworker haalt via HTTPS werk op en meldt heartbeat, veilige voortgang en
het eindresultaat terug.

Welke provider en welk model een bepaald soort agentjob gebruikt, staat in de algemene instellingen
in de database. Een proces leest die configuratie vóór het queueën. De gekozen waarden en
configuratieversie worden op de taak bevroren, zodat een latere instellingenwijziging geen lopende
taak verandert.

Naast een processessie mogen modules snelle publieke commands en read-only queries aanbieden. Een
command zoals `requestEpicVerification(...)` start geen agent: het bewaart alleen werk in de queue
van de ontvangende module. Een latere `runProcessSession()` pakt dat werk op.

## Productontwerp als black box

**Doel:** complete, duidelijke en behapbare epics met UX ontwerpen. Productontwerp maakt geen
stories.

Productontwerp wordt alleen door de scheduler of door een bevoegde handmatige UI-/REST-aanroep
gestart. Het heeft geen inkomende werkqueue en kiest tijdens een run zelf welk nuttig ontwerpwerk het
doet.

### Input

| Gegeven | Hoe komt het binnen? | Betekenis |
|---|---|---|
| `ProductAssignment` | Read-only query op de productmodule | Doel, doelgroep, grenzen en Git-URL. |
| Geldige `Decision`s | Query op het Besluitenregister | Grote keuzes die nu gelden. |
| `UserSignal`s | Read-only query op de productmodule | Feedback, problemen, kansen en kwaliteitszorgen. |
| Stories en `Verification`s | Read-only queries op planning en kwaliteit | Wat eerder werkelijk is gebouwd en aangetoond. |
| Huidig kwaliteitsbeeld en historie | Queries op `QualitySnapshot`s | Aantoonbare kwaliteit, risico's en ontwikkeling door de tijd. |
| Huidige code en documentatie | Read-only checkout van `ProductAssignment.gitUrl` | Hoe het product er nu voorstaat. |
| Acceptatie- en eventueel productieomgeving | Read-only `TestableProductConfiguration` | Hoe het product nu werkelijk werkt en aanvoelt; productie alleen binnen veilige grenzen. |
| Eigen actueel rolgeheugen | Automatisch via Agentgeheugen en de vertrouwde agentrol | Permanente lessen van precies de uitgevoerde Productontwerp-rol; nooit geheugen van een andere rol. |

### Output

| Gegeven | Betekenis |
|---|---|
| `Epic` | De gekozen gebruikersverbetering, inclusief status, eenduidige scope, bewijs, risico's, succescriteria en compleet UX-ontwerp. |

Productontwerp mag een beschikbare epic nog verbeteren. Zodra Productplanning een exacte epicversie
kiest, wordt die versie bevroren en niet meer aangepast.

Interne analyses, bronnen, concepten en agentuitvoer blijven binnen Productontwerp. Welke interne
werkwijze de module gebruikt, is niet zichtbaar voor de andere modules.

Wanneer Productontwerp AI nodig heeft, verzamelt het zelf de complete taakinput en alleen het
geheugen van de uit te voeren eigen rol. Algemene instellingen leveren provider en model.
AI-uitvoering bewaart en distribueert die opaque taak; het begrijpt niet dat de taak over een epic
gaat.

## Productplanning als black box

**Doel:** epics en herstelverzoeken omzetten in volledige stories en alle open stories in één
uitlegbare volgorde zetten.

Een scheduler of bevoegde handmatige aanroep start `runProcessSession()`. De run zoekt zelf naar
`AVAILABLE` epics en claimt daarnaast gericht werk uit de eigen `PlanningWorkItem`-queue. Als beide
ontbreken, is de run een geldige no-op.

### Input

| Gegeven | Hoe komt het binnen? | Betekenis |
|---|---|---|
| `PlanningWorkItem` | Via een snel, idempotent requestcommand | Een gericht verzoek voor een bugfix, ontbrekende dekking, storyreparatie, prioriteit of handmatige herplanning. |
| Beschikbare `Epic`s | Read-only query op Productontwerp | De planner kiest en bevriest zelf een exacte epicversie. |
| `Bug` en `Verification` | Read-only queries op Kwaliteitsbewaking | Bewijs voor herstelwerk of ontbrekende epicdekking. |
| `ProductAssignment` en geldige `Decision`s | Read-only queries | Het productdoel, de grenzen en blijvende keuzes. |
| Bestaande `Story`s | Eigen database | Reeds gepland, verzonden en opgeleverd werk. |
| Huidige code en documentatie | Read-only checkout van de publieke Git-URL | Context voor een realistische storiesplitsing. |
| Acceptatie- en eventueel productieomgeving | Read-only `TestableProductConfiguration` | Bestaande gebruikersroutes, schermen en gedrag; productie alleen binnen veilige grenzen. |
| Eigen actueel rolgeheugen | Automatisch via Agentgeheugen en de vertrouwde agentrol | Permanente planningslessen van precies de uitgevoerde rol. |

### Output

| Gegeven | Betekenis |
|---|---|
| `Story` | Zelfstandige productstory of bugfixstory met acceptatiecriteria, relevante UX, assets, status en `sequenceNumber`. |
| Backlogquery | Alle stories met status `TODO` of `IN_PROGRESS`, geordend op `sequenceNumber`. |
| Planningstatus | Zichtbaar resultaat van ieder `PlanningWorkItem`. |
| `QualityWorkItem` bij Kwaliteitsbewaking | Productplanning vraagt de eigenaar om een gerichte story-, bugfix- of epiccontrole klaar te zetten. |

Alleen Productplanning schrijft stories, story-inhoud, status en volgorde. De dispatcher meldt
leveringsgebeurtenissen via publieke planningcommands en verandert de story niet rechtstreeks.

Eventuele Planner-taken volgen dezelfde generieke AI-queue. AI-uitvoering kent geen Planner of
story, en Productplanning hervat haar wachtende processessie pas nadat het taakresultaat beschikbaar
is.

## Kwaliteitsbewaking als black box

**Doel:** de werkende applicatie onderzoeken, opleveringen controleren en aantonen of een complete
epic de bedoelde gebruikersverbetering bereikt.

Een scheduler of bevoegde handmatige aanroep start `runProcessSession()`. De run claimt werk uit de
eigen `QualityWorkItem`-queue. Een queuecommand start nooit onmiddellijk een tester-agent.

### Input

| Gegeven | Hoe komt het binnen? | Betekenis |
|---|---|---|
| `QualityWorkItem` | Via een snel, idempotent requestcommand | Verzoek om een story, epic, bugfix of gebruikerssignaal te onderzoeken. |
| `ProductAssignment` en testconfiguratie | Read-only query op de productmodule | Productgrenzen, omgeving, toegestane accounts en Git-URL. |
| Geldige `Decision`s | Read-only query | Grote product-, privacy- en kwaliteitskeuzes die nu gelden. |
| `Story` | Read-only query op Productplanning | Wat is gebouwd en waar de oplevering bij hoort. |
| Bevroren `Epic` met UX | Read-only query op Productontwerp | De volledige bedoeling die na alle stories moet worden gecontroleerd. |
| `UserSignal` | Read-only query op de productmodule | De oorspronkelijke zorg of observatie die onderzocht moet worden. |
| Huidige code en documentatie | Read-only checkout van de publieke Git-URL | Informatie over risico's en relevante tests; geen bewijs van werkend gedrag. |
| Eigen actueel rolgeheugen | Automatisch via Agentgeheugen en de vertrouwde agentrol | Permanente testlessen van precies de uitgevoerde kwaliteitsrol. |

### Output

| Gegeven | Betekenis |
|---|---|
| `Bug` | Reproduceerbare bouwfout met verwacht en werkelijk gedrag, bewijs en ernst. |
| `Verification` | Onveranderlijk oordeel en bewijs over een story, epic of gebruikerssignaal. |
| `QualitySnapshot` | Onveranderlijke momentopname na iedere afgeronde niet-lege kwaliteitssessie, met dekking, risico's, bugs en verificatie-uitkomsten. |
| `PlanningWorkItem` bij Productplanning | Kwaliteitsbewaking vraagt de eigenaar om een bugfix of ontbrekende epicdekking te plannen. |

Kwaliteitsbewaking maakt geen stories en wijzigt geen epic. Zij publiceert bewijs en vraagt de
eigenaar via een betekenisvol command om de geldige vervolgactie.

De Tester zet iedere benodigde agenttaak als complete `AiTask` in de generieke AI-queue. Dat is een
andere queue dan `QualityWorkItem`: een qualityworkitem zegt wat Kwaliteitsbewaking moet onderzoeken;
een AI-taak is alleen de technische uitvoering van één stap binnen die processessie.

## Software Factory-dispatcher als black box

**Doel:** steeds precies één geschikte story naar Software Factory sturen en de leveringsstatus
terugmelden aan Productplanning.

De scheduler start `runDispatchSession()`. De dispatcher gebruikt geen agents, heeft geen
productlogica en beheert geen eigen productentiteiten.

### Input

| Gegeven | Hoe komt het binnen? | Betekenis |
|---|---|---|
| Backlogquery | Read-only query op Productplanning | De eerste uitvoerbare `TODO`-story op `sequenceNumber`. |
| Externe Software Factory-status | API van Software Factory | Of voor het product nog werk openstaat en of een eerdere story is opgeleverd. |

### Output

| Gegeven | Betekenis |
|---|---|
| `StoryDeliveryPackage` | Volledige momentopname van de story met alle benodigde UX en assets. |
| Storycommands | Meldingen `markStoryAsDispatched(...)` en `markStoryAsDeveloped(...)` aan Productplanning. |
| `DeliveryAttempt` | Technische historie binnen Productplanning van verzending, response, fout en retry. |

De dispatcher verstuurt niets zolang Software Factory voor dat product nog een openstaande story
heeft. Software Factory hoeft de productrepository niet te lezen: alle inhoud en UX staan in het
storypakket.

Een tijdelijke dispatchfout handelt de dispatcher zelf af met een `DeliveryAttempt`, gecontroleerde
retry en dezelfde idempotentiesleutel. Een configuratie- of autorisatiefout blokkeert de levering en
wordt operationeel zichtbaar. Alleen wanneer Software Factory de story-inhoud definitief afwijst,
maakt Productplanning intern een `REPAIR_STORY`-workitem; de dispatcher verzint nooit zelf inhoud.

## Wanneer een epic klaar is

Een story gebruikt vier eenvoudige statussen:

- `TODO` — klaar voor uitvoering maar nog niet verstuurd;
- `IN_PROGRESS` — naar Software Factory gestuurd en daar nog open;
- `DONE` — door Software Factory opgeleverd; dit is nog geen kwaliteitsoordeel;
- `CANCELLED` — bewust niet meer uitvoeren, met een zichtbare bron en reden.

Een epic gebruikt:

- `AVAILABLE` — complete versie die Productplanning mag kiezen;
- `IN_PLANNING` — exacte versie is gekozen en bevroren;
- `ACTIVE` — één of meer stories worden uitgevoerd of hersteld;
- `VERIFYING` — alle geplande stories zijn klaar en de complete epic wordt gecontroleerd;
- `COMPLETED` — de bedoelde gebruikersverbetering is aangetoond;
- `NOT_SUCCESSFUL` — alles is geleverd, maar het gebruikersresultaat is niet bereikt;
- `CANCELLED` — een reeds gekozen of actieve epic is bewust gestopt;
- `SUPERSEDED` — een nog niet gekozen epicversie is door een nieuwere versie vervangen;
- `WITHDRAWN` — een nog niet gekozen epic is bewust ingetrokken.

Alle stories `DONE` betekent dus nog niet automatisch dat de epic is geslaagd. Productplanning zet
de epic zonder agent op `VERIFYING` en roept `requestEpicVerification(...)` aan. Dat command zet
alleen een `QualityWorkItem` in de queue. Tijdens een latere kwaliteitsrun wordt de hele epic getest.

Bij een bouwfout vraagt Kwaliteitsbewaking bugfixwerk aan. Bij ontbrekend gedrag binnen de bevroren
scope vraagt zij aanvullende stories voor dezelfde epic aan. Na herstel volgt opnieuw controle.
Alleen Productontwerp verwerkt het verificatieresultaat en sluit de epic af.

Een `NOT_SUCCESSFUL` epic blijft als historisch eindresultaat bestaan en wordt niet heropend.
Productontwerp kan tijdens een latere geplande run op basis van de verificatie een nieuwe vervolgepic
maken. Een `WITHDRAWN` epic was nog niet gekozen en heeft daarom geen stories. Bij een `CANCELLED`
epic zet Productplanning alle nog niet verstuurde stories direct op `CANCELLED`; een reeds
`IN_PROGRESS` story loopt normaal af en er wordt geen nieuwe epiccontrole gestart.

## Overleg en richting

De Stakeholder kan vanuit de UI een overleg starten. Een proces kan om overleg vragen wanneer
menselijke richting nodig is. Bij afsluiting maakt een notulenagent leesbare notulen en verwerkt hij
alleen expliciete uitkomsten:

- feedback, een correctie, wens of kwaliteitszorg wordt `UserSignal`;
- alleen een grote, blijvende keuze wordt `Decision`;
- een wijziging van doel of harde grens past `ProductAssignment` aan;
- een gewone epic-, story-, bug-, annulerings- of prioriteitsactie wordt een direct command op de
  eigenaarsmodule.

Een transcript verandert niet vanzelf het product. Iedere doorwerking gebeurt via een zichtbaar
command aan de module die het betreffende object bezit.

## Database, frontend en Git

De database is de productwaarheid. Iedere duurzame entiteit heeft precies één schrijvende module.
Andere modules en de frontend lezen via publieke queries en vragen wijzigingen alleen aan via
betekenisvolle commands. Niemand schrijft rechtstreeks in de tabellen van een andere module.

De frontend maakt dezelfde databasegegevens begrijpelijk voor mensen.

Agentgeheugen staat eveneens in de database, maar is context voor één agentrol en geen alternatieve
productwaarheid. Iedere processessie legt vast welke exacte geheugenversies haar taken hebben
gebruikt. De Stakeholder kan actuele items via de UI toevoegen, vervangen en intrekken en kan met een peildatum
zien wat een rol vroeger onthield.

Ook algemene `AiJobConfiguration`s, `AiTask`s, attempts en resultaten staan in de database. De
laptopworker leest nooit rechtstreeks uit die database: hij claimt taken via de publieke worker-API.
De worker draait niet in een `product-factory-workspace`; iedere taak bevat zelf alle benodigde data
en gebruikt een tijdelijke werkdirectory.

De publieke productrepository blijft wel de waarheid over de huidige code, tests en
productdocumentatie. Productontwerp, Productplanning en Kwaliteitsbewaking mogen de Git-URL uit de
`ProductAssignment` tijdens een sessie read-only uitchecken. Zij committen of pushen niets.

## Integratie- en acceptatietesten

De acceptatieomgeving gebruikt echte Product Factory-modules, contracts, queues en statusmachines,
maar geen echte externe schrijvende diensten. Een aparte Product Factory Testbed simuleert AI en
Software Factory stateful via dezelfde interfaces als productie. De in-memory database wordt bij
start of reset gevuld met vaste synthetische data, authenticatie staat uit en automatische
schedules staan standaard stil zodat een tester iedere stap bewust via de UI kan uitvoeren.

Publieke Git-repositories mogen zonder token via HTTPS worden gelezen. Iedere sessie bevriest de
gevonden commit-SHA en geen acceptatiecomponent bezit Git-schrijfrechten. Integratietests gebruiken
dezelfde Testbed-scenario's, maar vervangen internet-Git door een tijdelijke lokale repository.

Een acceptance-only UI-scherm laat scenario's resetten, kiezen en stap voor stap voortzetten. Het
toont daarna via de gewone product- en operationele schermen wat Product Factory werkelijk heeft
gedaan. Opstartcontroles weigeren in acceptatie echte AI-providers, een echt Software
Factory-endpoint, externe schrijftokens, productie-URL's en ingeschakelde achtergrondschedules.
Details staan in [Integratie- en acceptatietesten](integratie-en-acceptatietesten.md).

## Negen hoofdregels

1. De Stakeholder is de klant en diens expliciete richting is leidend.
2. Productontwerp maakt complete epics met UX, maar geen stories.
3. Productplanning maakt en ordent alle stories; de backlog is alleen een query op open stories.
4. Kwaliteitsbewaking levert bewijs en bugs, maar maakt geen stories en wijzigt geen epics.
5. Alleen `runProcessSession()` mag voor een intelligent proces AI-taken aanvragen; de laptopworker
   voert uitsluitend bestaande queuetaken uit.
6. Iedere entiteit heeft één eigenaar en andere modules wijzigen haar alleen via publieke commands.
7. Iedere agentrol leest uitsluitend haar eigen actuele, versieerbare geheugen; de Stakeholder mag
   dat geheugen via de UI corrigeren.
8. AI-uitvoering kent geen rollen of productbetekenis en krijgt altijd een complete taak met
   bevroren provider en model.
9. Gemiste worker-heartbeats leiden eerst tot een hersteltermijn; retries zijn met leases en fencing
   beschermd tegen oude workers.

## Detaildocumenten

- [Processen, publieke interfaces en entiteiten](processen-en-entiteiten.md)
- [Besluitenregister](besluitenregister.md)
- [Overleggen met de Stakeholder](overleggen.md)
- [Frontend](frontend.md)
- [Agentgeheugen](agentgeheugen.md)
- [AI-uitvoering](ai-uitvoering.md)
- [Integratie- en acceptatietesten](integratie-en-acceptatietesten.md)
- [Productontwerp-API](productontwerp.md)
- [Productontwerp — MVP](productontwerp-mvp.md)
- [Productontwerp — uitgebreide implementatie](productontwerp-uitgebreid.md)
- [Productplanning-API](productplanning.md)
- [Productplanning — MVP](productplanning-mvp.md)
- [Productplanning — uitgebreide implementatie](productplanning-uitgebreid.md)
- [Software Factory-dispatcher](software-factory-dispatcher.md)
- [Kwaliteitsbewaking-API](kwaliteitsbewaking.md)
- [Kwaliteitsbewaking — MVP](kwaliteitsbewaking-mvp.md)
- [Kwaliteitsbewaking — uitgebreide implementatie](kwaliteitsbewaking-uitgebreid.md)
