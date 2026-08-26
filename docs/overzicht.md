# Product Factory v2 — overzicht

Product Factory helpt één of meer producten steeds verder te verbeteren. Er is precies één
**Stakeholder** voor de hele Product Factory. Deze klant geeft voor alle producten het doel en de
richting aan. Product Factory onderzoekt, ontwerpt, plant en controleert het werk. Software Factory
bouwt de stories één voor één.

Dit document beschrijft de hele route in eenvoudige taal. De precieze module-interfaces, agents,
queues en interne werking staan in de documenten onderaan.

## De Stakeholder is de klant

Er is één globale Stakeholder: de klant voor wie we alle producten maken. De Stakeholder is een
actor en wordt niet als apart productobject opgeslagen. Dezelfde Stakeholder beheert dus ieder
product en mag ook Product Factory-brede instellingen wijzigen.

De Stakeholder communiceert met Product Factory via de gebruikersinterface. De UI vertaalt iedere
actie naar een publiek command op de module die het betrokken productobject bezit; de Stakeholder
schrijft nooit rechtstreeks in de database.

De Stakeholder:

- geeft het productdoel en de harde grenzen;
- kan feedback, zorgen, kansen en testtoegang leveren;
- kan de richting en prioriteit op ieder moment corrigeren;
- neemt grote beslissingen die langdurig richting geven;
- kan een Factory-besluit later aanpassen of intrekken;
- beheert de globale AI-provider- en modelinstellingen.

Agents mogen adviseren, doorvragen en gevolgen uitleggen. De expliciete wil van de Stakeholder is
uiteindelijk leidend. Binnen die richting mag Product Factory gewone, omkeerbare proceskeuzes zelf
maken. Inlog-, contact- en autorisatiegegevens horen bij technisch accountbeheer en niet bij de
productinterfaces.

## De publieke productbegrippen

Dit zijn de productobjecten die mensen zien en die modulegrenzen mogen oversteken. Interne
werkdocumenten, prompts en agentadministratie horen hier niet bij.

| Begrip | Eenvoudige betekenis |
|---|---|
| `Product` | Het product waaraan Product Factory werkt, inclusief of het actief is en dispatching aanstaat. |
| `ProductAssignment` | Het doel, de doelgroep, de harde grenzen en de publieke Git-URL van het product. |
| `UserSignal` | Feedback, een probleem, zorg, kans of observatie, met zichtbare verwerking en uitkomst. |
| `Decision` | Een grote, blijvende keuze die meerdere toekomstige sessies richting geeft. |
| `Epic` | Titel en korte samenvatting plus een concreet probleem met een duidelijke oplossing, richting, eventuele UX, testbare acceptatiecriteria en uitleg over behapbaarheid. |
| `Story` | Titel en korte samenvatting plus één volledig zelfstandig uitvoerbaar stuk productwerk of bugfixwerk voor Software Factory. |
| `Bug` | Titel en korte samenvatting plus een volledige reproduceerbare afwijking tussen verwacht en werkelijk gedrag. |
| `Verification` | Onveranderlijk bewijs en een oordeel over een story, epic of gebruikerssignaal. |
| `QualitySnapshot` | Een onveranderlijke momentopname van de aantoonbare productkwaliteit na een kwaliteitssessie. |
| `Meeting` | Een overleg met de Stakeholder, inclusief agenda, gesprek en gecontroleerde uitkomst. |
| `StakeholderQuestion` | Een tijdelijke vraag van één agentrol aan de Stakeholder, met een zichtbare antwoordstatus en bronoverleg. |
| `AgentMemoryItem` | Een permanente, versieerbare herinnering van precies één agentrol binnen dit product. |

De **backlog** is geen apart object. Het is de lijst van stories met status `TODO` of
`IN_PROGRESS`, geordend op `sequenceNumber`.

## De hele route

In gewone taal gebeurt het volgende:

1. De Stakeholder legt uit voor wie het product is, wat het moet bereiken en welke grenzen gelden.
2. Feedback, zorgen en kansen worden als gebruikerssignalen bewaard.
3. Productontwerp onderzoekt het product en maakt een complete epic; UX is onderdeel van de epic
   wanneer zichtbaar gedrag of interactie verandert.
4. Productplanning bevriest de gekozen epicversie en verdeelt de hele epic in zelfstandige stories.
5. Alle stories met status `TODO` of `IN_PROGRESS` vormen samen één geprioriteerde backlog.
6. De dispatcher stuurt de bovenste uitvoerbare story naar Software Factory.
7. Wanneer Software Factory de story heeft opgeleverd, wordt de story `DONE` en kan gericht
   testwerk worden klaargezet.
8. Kwaliteitsbewaking controleert iedere opgeleverde story of bugfix. Pas wanneer alle actuele
   controles binnen een niet-geannuleerde epic geslaagd zijn, wordt de complete epiccontrole
   klaargezet.
9. Kwaliteitsbewaking controleert daarna of het product als geheel echt werkt en of de epic de
   bedoelde verbetering voor de gebruiker heeft bereikt.
10. Bij een bug komt er een bugfixverzoek. Bij ontbrekend werk binnen de epic komt er een
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

De normale route is bewust autonoom. Er zit geen verplichte menselijke goedkeuring tussen een
`AVAILABLE` epic, planning en dispatch. Product Factory wacht dus niet op akkoord voordat een
geldige story naar Software Factory gaat. De Stakeholder kan richting, prioriteit en dispatching wel
op ieder moment aanpassen of een epic annuleren; dat zijn bedieningsmogelijkheden en geen poort in
iedere cyclus.

## Vier uitvoerende onderdelen

Productontwerp, Productplanning en Kwaliteitsbewaking zijn de drie intelligente procesmodules. Alleen
hun geplande of handmatig gestarte `runProcessSession(productId)` mag voor dat proces een AI-taak
aanvragen. Per combinatie van module en product kan maximaal één onafgeronde logische sessie
bestaan, ook wanneer die `WAITING_FOR_AI` of `BLOCKED` is. Hetzelfde proces mag dus wel tegelijk
voor verschillende producten draaien. Alleen een werkelijk gelijktijdig uitvoerende handmatige
start voor hetzelfde product krijgt een fout; een schedulerbotsing met zo'n actieve call wordt
overgeslagen en geregistreerd.

De technische scheduler leest voor ieder actief product de `ProcessScheduleConfiguration` van de
vier uitvoerende onderdelen en roept alleen vervallen, ingeschakelde schedules aan. De Stakeholder
stelt via de UI één of meer menselijke dag/tijdregels of een vast interval en een expliciete
tijdzone in. Een regel kan meerdere dagen en meerdere tijden delen, terwijl een tweede regel andere
dagen en tijden kan hebben. Cronexpressies zijn geen onderdeel van de publieke bediening. De
scheduler bevat geen product- of agentlogica. Een handmatige UI-/REST-start kiest eveneens
expliciet één product en blijft ook bij een uitgeschakeld schedule beschikbaar.

Iedere geplande combinatie wordt hooguit eenmaal geclaimd. Na downtime wordt maximaal één gemiste
run ingehaald en daarna het eerstvolgende toekomstige tijdstip berekend; een lange storing speelt
dus geen hele rij oude runs af. Een wijziging geldt alleen voor toekomstige starts en annuleert geen
lopende of wachtende processessie.

Een AI-taak draait asynchroon. De processessie bewaart het taak-ID, krijgt status
`WAITING_FOR_AI` en houdt geen thread of technische lock vast. Een volgende schedule-run hervat
dezelfde sessie zodra het resultaat klaarstaat. Bij een handmatige start van een wachtende sessie
wordt diezelfde sessie veilig gecontroleerd en niet als tweede sessie gestart.

De Software Factory-dispatcher is het vierde uitvoerende onderdeel, maar geen intelligent proces. Hij
gebruikt geen agents en neemt geen productbesluiten. Net als ieder gepland onderdeel kan hij ook
bevoegd via UI of REST worden gestart.

Het Agentgeheugen is een ondersteunende module en geen vijfde proces. Iedere gewone procesagent
leest bij een taak uitsluitend haar eigen actuele geheugen. De Stakeholder kan alle rolgeheugens via
de UI bekijken en corrigeren. Alleen de Meeting Agent en notulenagent mogen via een vertrouwde
overlegcontext het actuele geheugen van alle rollen binnen precies het besproken product gebruiken.

AI-uitvoering is eveneens een ondersteunende module. Zij kent lokaal jobkeys, instellingen,
domeincorrelatie en agentrollen voor credentialgrants, maar de externe Agent Runtime kent alleen
complete `APPLICATION_WORK`-jobs met provider, model en één prompt. Product Factory bewaart een
lokale outbox en Runtime-job-ID; de gedeelde Runtime beheert de technische queue. Echte `CODEX`- en
`CLAUDE`-taken worden door een lokale Runtime-worker in een tijdelijke Dockercontainer uitgevoerd.
Buiten productie handelt Agent Runtime `MOCKED` server-side af, zodat tests geen laptop nodig
hebben.

Welke provider en welk model een bepaald soort agentjob gebruikt, staat in de globale
AI-jobconfiguratie van de AI-uitvoeringscapability in de database. De Stakeholder bedient die op
dezelfde frontendpagina onder **Instellingen → AI-modellen**, duidelijk gemarkeerd als geldig voor
alle producten. Technisch blijft dit een afzonderlijk intern Spring Modulith-onderdeel van
AI-uitvoering; de Runtime-façade kiest of interpreteert het model niet. Een proces leest de
configuratie vóór het queueën. De gekozen waarden en configuratieversie worden op de taak bevroren,
zodat een latere instellingenwijziging geen lopende taak verandert.

Naast een processessie mogen modules snelle publieke commands en read-only queries aanbieden. Een
command zoals `requestEpicVerification(...)` start geen agent: het bewaart alleen werk in de queue
van de ontvangende module. Een latere `runProcessSession(productId)` pakt dat werk op.

## Productontwerp als black box

**Doel:** complete, duidelijke en behapbare epics ontwerpen. Productontwerp maakt geen stories.

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
| Huidige code en documentatie | Read-only checkout in de AI-taakcontainer vanaf `ProductAssignment.gitUrl` | Hoe het product er nu voorstaat. |
| Acceptatie- en eventueel productieomgeving | Read-only `TestableProductConfiguration` | Hoe het product nu werkelijk werkt en aanvoelt; productie alleen binnen veilige grenzen. |
| Eigen actueel rolgeheugen | Automatisch via Agentgeheugen en de vertrouwde agentrol | Permanente lessen van precies de uitgevoerde Productontwerp-rol; nooit geheugen van een andere rol. |

### Output

| Gegeven | Betekenis |
|---|---|
| `Epic` | Technische metadata, titel en samenvatting plus probleem, oplossing, relatie met productdoel of besluiten, eventueel UX-ontwerp, testbare acceptatiecriteria en uitleg over behapbaarheid. |

Productontwerp mag een beschikbare epic nog verbeteren. Zodra Productplanning een exacte epicversie
kiest, wordt die versie bevroren en niet meer aangepast.

Interne analyses, bronnen, concepten en agentuitvoer blijven binnen Productontwerp. Welke interne
werkwijze de module gebruikt, is niet zichtbaar voor de andere modules.

Wanneer Productontwerp AI nodig heeft, verzamelt het zelf de complete taakinput en alleen het
geheugen van de uit te voeren eigen rol. Het interne `settings`-onderdeel van AI-uitvoering levert
provider en model.
AI-uitvoering bewaart lokale correlatie/outbox en dient één complete opaque prompt bij Agent Runtime
in; de Runtime begrijpt niet dat de taak over een epic gaat.

## Productplanning als black box

**Doel:** epics en herstelverzoeken omzetten in volledige stories en alle open stories in één
uitlegbare volgorde zetten.

Een scheduler of bevoegde handmatige aanroep start `runProcessSession(productId)`. De run hervat
voor dat product eerst een nog niet afgeronde sessie of reeds geclaimde `IN_PLANNING` epic. Pas als
die niet bestaat, zoekt hij naar `AVAILABLE` epics en claimt hij gericht werk uit de eigen
`PlanningWorkItem`-queue. Als alles ontbreekt, is de run een geldige no-op.

### Input

| Gegeven | Hoe komt het binnen? | Betekenis |
|---|---|---|
| `PlanningWorkItem` | Via een snel, idempotent requestcommand | Een gericht verzoek voor een bugfix, ontbrekende dekking, prioriteit of handmatige herplanning. |
| Beschikbare `Epic`s | Read-only query op Productontwerp | De planner kiest en bevriest zelf een exacte epicversie. |
| `Bug` en `Verification` | Read-only queries op Kwaliteitsbewaking | Bewijs voor herstelwerk of ontbrekende epicdekking. |
| `ProductAssignment` en geldige `Decision`s | Read-only queries | Het productdoel, de grenzen en blijvende keuzes. |
| Bestaande `Story`s | Eigen database | Reeds gepland, verzonden en opgeleverd werk. |
| Huidige code en documentatie | Read-only checkout in de AI-taakcontainer vanaf de publieke Git-URL | Context voor een realistische storiesplitsing. |
| Acceptatie- en eventueel productieomgeving | Read-only `TestableProductConfiguration` | Bestaande gebruikersroutes, schermen en gedrag; productie alleen binnen veilige grenzen. |
| Eigen actueel rolgeheugen | Automatisch via Agentgeheugen en de vertrouwde agentrol | Permanente planningslessen van precies de uitgevoerde rol. |

### Output

| Gegeven | Betekenis |
|---|---|
| `Story` | Titel en samenvatting plus een zelfstandige productstory of bugfixstory met acceptatiecriteria, relevante UX, assets, status en `sequenceNumber`. |
| Backlogquery | Alle stories met status `TODO` of `IN_PROGRESS`, geordend op `sequenceNumber`. |
| Planningstatus | Zichtbaar resultaat van ieder `PlanningWorkItem`. |
| `QualityWorkItem` bij Kwaliteitsbewaking | Productplanning vraagt de eigenaar om een gerichte story-, bugfix- of epiccontrole klaar te zetten. |

Alleen Productplanning schrijft stories, story-inhoud, status en volgorde. De dispatcher meldt
leveringsgebeurtenissen via publieke planningcommands en verandert de story niet rechtstreeks.

Eventuele Planner-taken volgen dezelfde generieke Runtime-façade. De externe Runtime kent geen
Planner of story, en Productplanning hervat haar wachtende processessie pas nadat het taakresultaat
beschikbaar is.

## Kwaliteitsbewaking als black box

**Doel:** de werkende applicatie onderzoeken, opleveringen controleren en aantonen of een complete
epic de bedoelde gebruikersverbetering bereikt.

Een scheduler of bevoegde handmatige aanroep start `runProcessSession(productId)`. De run claimt
uitsluitend werk van dat product uit de eigen `QualityWorkItem`-queue. Een queuecommand start nooit
onmiddellijk een tester-agent. Aan het begin van een run worden retrybare geblokkeerde of mislukte
items van dat product waarvan `retryAfter` is verstreken weer `PENDING` gemaakt.

### Input

| Gegeven | Hoe komt het binnen? | Betekenis |
|---|---|---|
| `QualityWorkItem` | Via een snel, idempotent requestcommand | Verzoek om een story, epic, bugfix of gebruikerssignaal te onderzoeken. |
| `ProductAssignment` en testconfiguratie | Read-only query op de productmodule | Productgrenzen, omgeving, toegestane accounts en Git-URL. |
| Geldige `Decision`s | Read-only query | Grote product-, privacy- en kwaliteitskeuzes die nu gelden. |
| `Story` | Read-only query op Productplanning | Wat is gebouwd, in welke commit het is opgeleverd en waar de oplevering bij hoort. |
| Bevroren `Epic` met UX | Read-only query op Productontwerp | De volledige bedoeling die na alle stories moet worden gecontroleerd. |
| `UserSignal` | Read-only query op de productmodule | De oorspronkelijke zorg of observatie die onderzocht moet worden. |
| Huidige code en documentatie | Read-only checkout in de AI-taakcontainer vanaf de publieke Git-URL | Informatie over risico's en relevante tests; geen bewijs van werkend gedrag. |
| Eigen actueel rolgeheugen | Automatisch via Agentgeheugen en de vertrouwde agentrol | Permanente testlessen van precies de uitgevoerde kwaliteitsrol. |

### Output

| Gegeven | Betekenis |
|---|---|
| `Bug` | Titel en samenvatting plus een reproduceerbare afwijking met verwacht en werkelijk gedrag, bewijs en ernst. |
| `Verification` | Onveranderlijk oordeel en bewijs over een story, epic of gebruikerssignaal. |
| `QualitySnapshot` | Onveranderlijke momentopname voor het ene product van iedere afgeronde niet-lege kwaliteitssessie, met dekking, risico's, bugs en verificatie-uitkomsten. |
| Kwaliteitsqueuestatus | Read-only zicht op ieder `QualityWorkItem`, inclusief blokkade, pogingen en eerstvolgende retry. |
| `PlanningWorkItem` bij Productplanning | Kwaliteitsbewaking vraagt de eigenaar om een bugfix of ontbrekende epicdekking te plannen. |

Een tijdelijk geblokkeerd kwaliteitsitem blijft zichtbaar met reden, `attemptCount`, laatste poging
en `retryAfter`. De back-off is 15 minuten, 1 uur, 4 uur en daarna maximaal 24 uur. Er is geen
maximaal aantal domeinretries; vanaf vijf pogingen toont de UI **Aandacht nodig**. Met **Retry now**
maakt de Stakeholder het item direct `PENDING` en start de normale kwaliteitssessie als die nog niet
loopt. Een bestaande run wordt nooit verdubbeld.

Kwaliteitsbewaking maakt geen stories en wijzigt geen epic. Zij publiceert bewijs en vraagt de
eigenaar via een betekenisvol command om de geldige vervolgactie.

De Tester zet iedere benodigde agenttaak als lokale `AiTask` in de generieke Runtime-outbox. Dat is
een andere queue dan `QualityWorkItem`: een qualityworkitem zegt wat Kwaliteitsbewaking moet
onderzoeken; een AI-taak correleert één technische Runtime-job binnen die processessie.

## Software Factory-dispatcher als black box

**Doel:** voor ieder product steeds precies één geschikte story tegelijk naar Software Factory
sturen en de leveringsstatus terugmelden aan Productplanning.

De scheduler of een bevoegde handmatige UI-/REST-actie start `runDispatchSession(productId)`. De
dispatcher gebruikt geen agents, heeft geen productlogica en beheert geen eigen productentiteiten.
Eén sessie verwerkt precies één product en kan daarvoor maximaal één nieuwe story versturen.

### Input

| Gegeven | Hoe komt het binnen? | Betekenis |
|---|---|---|
| Productconfiguratie | Read-only query op de productmodule | Of het gekozen product actief is en dispatching aanstaat. |
| Dispatchreservering | Atomair command op Productplanning | De eerste uitvoerbare `TODO`-story op `sequenceNumber`; bij een retry wordt de reservering opnieuw tegen annulering gevalideerd. |
| Externe Software Factory-status | API van Software Factory | `OPEN`, `DONE` of `CANCELLED`; bij `DONE` ook de exacte oplevercommit. |

### Output

| Gegeven | Betekenis |
|---|---|
| `StoryDeliveryPackage` | Volledige interne storymomentopname die voor Software Factory v2 deterministisch wordt gemapt naar titel, één zelfstandige Markdownomschrijving, binaire attachments en een idempotentieheader. |
| Storycommands | Reservering en meldingen `markStoryAsDispatched(...)`, `markStoryAsDeveloped(...)` en `markStoryAsCancelled(...)` aan Productplanning. |
| `DeliveryAttempt` | Technische historie van de Software Factory-dispatcher over verzending, response, fout en retry. |

De dispatcher verstuurt niets zolang Software Factory voor dat product nog een openstaande story
heeft. Software Factory hoeft de productrepository niet te lezen: alle inhoud en UX staan in het
storypakket. Software Factory accepteert en bouwt het pakket, maar kan Product Factory nooit een
uitvoeringsvraag stellen.

Een tijdelijke dispatchfout handelt de dispatcher zelf af met een `DeliveryAttempt`, gecontroleerde
retry en dezelfde idempotentiesleutel. Een configuratie- of autorisatiefout blokkeert de levering en
wordt operationeel zichtbaar. Software Factory moet ieder contractgeldig storypakket accepteren.
Een weigering is een technische contractfout die levering voor dat product blokkeert; zij verandert
de story niet en maakt geen planningswerk.

Vóór iedere retry vraagt de dispatcher eerst met dezelfde idempotentiesleutel of de externe story al
bestaat. Alleen als Software Factory aantoonbaar nog niets heeft aangemaakt, laat hij
Productplanning de reservering opnieuw tegen de actuele epicanulering controleren. Een inmiddels
geannuleerde epic maakt de story dan `CANCELLED`; een onbekende externe toestand leidt nooit tot een
blinde verzending.

Wanneer Software Factory meldt dat extern werk is geannuleerd of verwijderd, roept de dispatcher
`markStoryAsCancelled(...)` aan. Productplanning zet de story op `CANCELLED`; dit is geen technische
fout en geen mislukte story. De complete epic wordt later opnieuw getest zodra het overige actuele
werk klaar is, tenzij de Stakeholder de epic zelf heeft geannuleerd.

## Wanneer een epic klaar is

Een story gebruikt vier eenvoudige statussen:

- `TODO` — klaar voor uitvoering maar nog niet verstuurd;
- `IN_PROGRESS` — naar Software Factory gestuurd en daar nog open;
- `DONE` — door Software Factory opgeleverd; dit is nog geen kwaliteitsoordeel;
- `CANCELLED` — niet meer uitvoeren, met een zichtbare bron en reden; bijvoorbeeld doordat
  Software Factory de externe story verwijdert of bewust niet uitvoert.

`DONE` betekent hier *finished*. Een story of bugfixstory krijgt nooit `FAILED` of **mislukt** als
leveringsstatus. Een ontoereikende oplevering blijft `DONE` en krijgt een afgekeurde verificatie.
Een niet-uitgevoerde story wordt `CANCELLED`.

Bij `DONE` bewaart Productplanning de exacte `deliveredCommitSha` die Software Factory teruggeeft.
Een gerichte verificatie bevat die commit als vereiste productversie. Kwaliteitsbewaking vraagt via
de testconfiguratie op welke commit of release de doelomgeving draait en zet de controle op
`BLOCKED` zolang de oplevercommit daar nog niet aantoonbaar in zit. Zo wordt een nieuwe story nooit
tegen een oudere deployment afgekeurd.

Een epic gebruikt:

- `AVAILABLE` — complete versie die Productplanning mag kiezen;
- `IN_PLANNING` — exacte versie is gekozen en bevroren;
- `ACTIVE` — één of meer stories worden uitgevoerd of hersteld;
- `VERIFYING` — al het niet-geannuleerde werk is opgeleverd en actueel geslaagd geverifieerd, of
  geannuleerd extern werk vraagt om een feitelijke herbeoordeling; de complete epic wordt
  gecontroleerd;
- `COMPLETED` — de bedoelde gebruikersverbetering is aangetoond;
- `NOT_SUCCESSFUL` — alles is geleverd, maar het gebruikersresultaat is niet bereikt;
- `CANCELLED` — een reeds gekozen of actieve epic is bewust gestopt;
- `SUPERSEDED` — een nog niet gekozen epicversie is door een nieuwere versie vervangen;
- `WITHDRAWN` — een nog niet gekozen epic is bewust ingetrokken.

Alle stories `DONE` betekent dus nog niet dat de epic klaar is voor epicverificatie. Eerst controleert
Kwaliteitsbewaking iedere story of bugfix. Zij meldt iedere uitkomst via een snel command aan
Productplanning. Normaal zet Productplanning de epic alleen op `VERIFYING` als alle actuele
controles geslaagd zijn en geen open bug of herstelwerk resteert. Wanneer Software Factory een
`IN_PROGRESS` story `CANCELLED`, volgt na afronding van het overige werk juist een complete
herbeoordeling van de feitelijke applicatie. Die bepaalt of het geannuleerde werk nog nodig was,
bijvoorbeeld omdat iemand de wijziging handmatig heeft gedaan. `requestEpicVerification(...)` zet
alleen een `QualityWorkItem` in de queue; de latere kwaliteitsrun voert de echte controle uit.

Een epiccontrole gebruikt alleen `PASSED`, `NEEDS_WORK`, `BLOCKED` of `NOT_SUCCESSFUL`. Bij
`NEEDS_WORK` zet Productontwerp de epic terug naar `ACTIVE`: bugs leveren gerichte
bugfixverzoeken op en ontbrekende dekking aanvullende stories. Bij `BLOCKED` blijft de epic
`VERIFYING` en volgt dezelfde controle het retrybeleid. Na herstel volgen eerst de gerichte
storycontroles en daarna opnieuw de complete epiccontrole. Alleen Productontwerp verwerkt het
epicverificatieresultaat en sluit de epic af.

Een `NOT_SUCCESSFUL` epic blijft als historisch eindresultaat bestaan en wordt niet heropend.
Productontwerp kan tijdens een latere geplande run op basis van de verificatie een nieuwe vervolgepic
maken. Een `WITHDRAWN` epic was nog niet gekozen en heeft daarom geen stories. Bij een `CANCELLED`
epic bewaart Productplanning een duurzame annuleringsmarker en zet alle niet-gereserveerde `TODO`-
stories op `CANCELLED`. Daardoor kan een wachtende Planner later niets meer publiceren. Een
`IN_PROGRESS` story loopt normaal af. Een alleen lokaal gereserveerde story wordt bij een latere
dispatchretry eveneens `CANCELLED` als Software Factory aantoonbaar nog geen extern werk heeft; er
wordt geen nieuwe epiccontrole gestart.

`NOT_SUCCESSFUL` is alleen toegestaan wanneer de functionaliteit volgens afspraak werkt én
beschikbaar bewijs een testbaar acceptatiecriterium van de epic aantoonbaar weerlegt. Ontbrekende
gegevens of een nog niet gedeployde versie geven `BLOCKED`, geen productoordeel. Langdurige gebruiksdoelen die
niet tijdens een test te meten zijn worden later als gebruikerssignaal gevolgd en houden de
technische epicafronding niet willekeurig tegen.

## Overleg en richting

De Stakeholder kan vanuit de UI een overleg starten. Een procesagent die uitleg nodig heeft, maakt
via vertrouwde procescode een `StakeholderQuestion`. De vraag staat niet in permanent geheugen,
blijft zichtbaar als `OPEN`, `ANSWERED` of `WITHDRAWN` en komt automatisch op de agenda van een
bestaand of volgend overleg voor dat product.

Tijdens het overleg praat de Stakeholder met één Meeting Agent. Deze super-agent kent via de
rolcatalogus de verantwoordelijkheden en grenzen van alle actieve agentrollen en mag het actuele
geheugen van al die rollen voor dit product inzien. De Stakeholder kan een bericht aan Product
Factory als geheel of specifiek aan één rol richten. De Meeting Agent noemt dan expliciet vanuit
welke rol hij antwoordt; de echte procesagent wordt niet gestart.

Bij afsluiting maakt een notulenagent leesbare notulen en verwerkt hij gecontroleerde uitkomsten:

- feedback, een correctie, wens of kwaliteitszorg wordt `UserSignal`;
- alleen een grote, blijvende keuze wordt `Decision`;
- een wijziging van doel of harde grens past `ProductAssignment` aan;
- een gewone epic-, story-, bug-, annulerings- of prioriteitsactie wordt een direct command op de
  eigenaarsmodule;
- beantwoorde agentvragen krijgen antwoord, meeting en bericht als bron;
- blijvende, herbruikbare lessen kunnen via een gevalideerde append-only batch in het geheugen van
  een of meer betrokken agentrollen worden toegevoegd, vervangen of ingetrokken.

Voor deze productbrede geheugenbatch is geen extra menselijke goedkeuring nodig. Iedere wijziging
is aan het overleg gekoppeld, wordt bij de notulen getoond en kan later door de Stakeholder worden
gecorrigeerd. Een losse vraag, antwoord of actie wordt niet automatisch permanent geheugen.

Een transcript verandert niet vanzelf het product. Iedere doorwerking gebeurt via een zichtbaar
command aan de module die het betreffende object bezit.

Een overleg is geen verplichte goedkeuring in de ontwerp-, plan-, dispatch- of kwaliteitsroute. Als
de bestaande productopdracht en besluiten voldoende richting geven, werkt de Factory zonder
menselijke tussenkomst verder.

## Database, frontend en Git

De database is de productwaarheid. Iedere duurzame entiteit heeft precies één schrijvende module.
Andere modules en de frontend lezen via publieke queries en vragen wijzigingen alleen aan via
betekenisvolle commands. Niemand schrijft rechtstreeks in de tabellen van een andere module.

De frontend maakt dezelfde databasegegevens begrijpelijk voor mensen.

Agentgeheugen staat eveneens in de database, maar is context voor agentrollen en geen alternatieve
productwaarheid. Iedere processessie en ieder overleg legt vast welke exacte geheugenversies haar
taken hebben gebruikt. De Stakeholder kan actuele items via de UI toevoegen, vervangen en intrekken
en kan met een peildatum zien wat een rol vroeger onthield.

Ook algemene `AiJobConfiguration`s en lokale `AiTask`-correlaties staan in de Product Factory-
database. Attempts, leases, fencing, technische resultaten en artifactbytes staan uitsluitend bij
Agent Runtime. Product Factory dient via HTTPS in en leest status/resultaat; de lokale worker heeft
nooit toegang tot de Product Factory-database. Iedere job bevat één complete prompt en gebruikt een
tijdelijke Runtime-werkdirectory. `MOCKED` blijft vóór de workergrens en wordt alleen in integratie
en acceptatie door Agent Runtime uitgevoerd met vooraf ingestelde antwoorden.

Ook iedere `ProcessScheduleConfiguration` staat duurzaam en geversioneerd in de database bij het
betreffende product. De technische scheduler gebruikt uitsluitend deze publieke configuratie en een
eigen idempotente claim op het geplande tijdstip; omgevingsconfiguratie kan automatische polling,
zoals op acceptatie, volledig uitschakelen zonder de productinstelling te overschrijven.

De publieke productrepository blijft wel de waarheid over de huidige code, tests en
productdocumentatie. Productontwerp, Productplanning en Kwaliteitsbewaking bevriezen de Git-URL en
commit-SHA in hun taakinput. Bij echte AI-uitvoering checkt de agent die commit in zijn tijdelijke
Dockercontainer read-only uit; de servermodules en de agent committen of pushen niets.

## Technische moduleopbouw

Maven vormt de harde grens tussen het publieke contract en de capability-implementaties. Alle
publieke interfaces en DTO's staan per capabilitypackage in één module `product-factory-api`.
Iedere implementatie gebruikt die gedeelde API-module, maar nooit een andere implementatiemodule.
Alleen de ene uitvoerbare `product-factory-app` kent implementatie-artifacts. Door het ene publieke
API-artifact kunnen publieke contracten elkaar niet via cyclische Maven-dependencies vastzetten.

Productontwerp, Productplanning en Kwaliteitsbewaking kunnen een MVP- en uitgebreide implementatie
hebben. De main-module kiest tijdens de build exact één implementatie per geactiveerde capability;
een nog niet geactiveerde capability kan al wel haar publieke contract in `product-factory-api`
hebben. Er bestaat
geen runtime-toggle en twee varianten schrijven nooit tegelijk dezelfde productdata. Spring
Modulith wordt uitsluitend binnen een implementatiemodule gebruikt om haar interne functionele
delen te structureren en te testen. `product-factory-api` gebruikt geen Spring Modulith.

Iedere processessie bewaart haar `implementationId` en `implementationVersion`. Zolang terugkeer
naar de MVP ondersteund wordt, blijven publieke objecten en het duurzame schema compatibel en zijn
migraties additief. De volledige keuze staat in
[Maven en Spring Modulith](platform/maven-en-spring-modulith.md).

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
Details staan in [Integratie- en acceptatietesten](platform/integratie-en-acceptatietesten.md).

## Tien hoofdregels

1. De Stakeholder is de klant en diens expliciete richting is leidend.
2. Productontwerp maakt complete epics, met UX wanneer die nodig is, maar geen stories.
3. Productplanning maakt en ordent alle stories; de backlog is alleen een query op open stories.
4. Kwaliteitsbewaking levert bewijs en bugs, maar maakt geen stories en wijzigt geen epics.
5. Alleen `runProcessSession(productId)` mag voor een intelligent proces AI-taken aanvragen; de
   technische uitvoerders verwerken uitsluitend bestaande queuetaken en starten nooit zelf een
   proces.
6. Iedere entiteit heeft één eigenaar en andere modules wijzigen haar alleen via publieke commands.
7. Iedere gewone procesagent leest uitsluitend haar eigen actuele, versieerbare geheugen. De
   Stakeholder mag alle rollen via de UI corrigeren; alleen Meeting Agent en notulenagent hebben
   binnen een vertrouwd productoverleg gecontroleerde toegang tot alle rolgeheugens van dat product.
8. De externe Agent Runtime kent geen rollen of productbetekenis en krijgt altijd één complete
   prompt met bevroren provider en model; Product Factory gebruikt agentrollen alleen om lokaal
   geheugen en environmentkeygrants te begrenzen.
9. Agent Runtime beheert worker-heartbeats, harde deadlines, herstel, retries, leases en fencing;
   Product Factory projecteert alleen de stabiele jobstatus.
10. Maven bewaakt de harde grens tussen de ene gedeelde API-module en alle implementaties; alle
    publieke capabilitycontracten bestaan vanaf het begin en de ene main-build kiest exact één
    implementatie per geactiveerde capability. Spring Modulith blijft binnen die implementatie.

## Detaildocumenten

### Platform

- [Technische basis](platform/technische-basis.md)
- [Maven en Spring Modulith](platform/maven-en-spring-modulith.md)
- [Deployment en operatie](platform/deployment-en-operatie.md)
- [Integratie- en acceptatietesten](platform/integratie-en-acceptatietesten.md)

### Stappenplannen

- [Overzicht van de implementatiestappen](stappenplannen/README.md)
- [Stap 1 — Technische fundering](stappenplannen/01-technische-fundering.md)

### Gedeelde modules

- [Besluitenregister](gedeelde-modules/besluitenregister.md)
- [Agentgeheugen](gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](gedeelde-modules/ai-uitvoering.md)
- [Agent Runtime-integratie en taakcontainer](gedeelde-modules/ai-worker.md)

### Stakeholderbediening

- [Product- en overleg-API](stakeholder/product-en-overleg-api.md)
- [Frontend](stakeholder/frontend.md)
- [Overleggen met de Stakeholder](stakeholder/overleggen.md)

### Processen

- [Belangrijkste functionele ketenscenario's](ketenscenarios.md)
- [Processen, publieke interfaces en entiteiten](processen/processen-en-entiteiten.md)
- [Productontwerp-API](processen/productontwerp/api.md)
- [Productontwerp — MVP](processen/productontwerp/mvp.md)
- [Productontwerp — uitgebreide implementatie](processen/productontwerp/uitgebreid.md)
- [Productplanning-API](processen/productplanning/api.md)
- [Productplanning — MVP](processen/productplanning/mvp.md)
- [Productplanning — uitgebreide implementatie](processen/productplanning/uitgebreid.md)
- [Software Factory-dispatcher](processen/software-factory-dispatcher.md)
- [Kwaliteitsbewaking-API](processen/kwaliteitsbewaking/api.md)
- [Kwaliteitsbewaking — MVP](processen/kwaliteitsbewaking/mvp.md)
- [Kwaliteitsbewaking — uitgebreide implementatie](processen/kwaliteitsbewaking/uitgebreid.md)
