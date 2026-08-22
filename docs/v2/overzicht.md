# Product Factory v2 — overzicht

Product Factory helpt één product steeds verder te verbeteren. De klant voor wie het product wordt
gemaakt is de **Stakeholder**. Die geeft het doel en de richting aan. Product Factory onderzoekt,
ontwerpt, plant en controleert het werk. Software Factory bouwt de stories één voor één.

Dit document beschrijft de hele route in eenvoudige taal. De precieze module-interfaces, agents,
queues en interne werking staan in de documenten onderaan.

## De Stakeholder is de klant

Per product is er één Stakeholder: de klant voor wie we het product maken. De Stakeholder is een
actor en wordt niet als apart productobject opgeslagen.

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
| `StakeholderDirection` | Een expliciete aanwijzing, correctie of prioriteit van de Stakeholder. |
| `UserSignal` | Feedback, een probleem, zorg, kans of observatie, met zichtbare verwerking en uitkomst. |
| `Decision` | Een grote, blijvende keuze die meerdere toekomstige sessies richting geeft. |
| `Epic` | Een complete en behapbare gebruikersverbetering, inclusief scope, succescriteria en UX-ontwerp. |
| `Story` | Eén zelfstandig uitvoerbaar stuk productwerk of bugfixwerk voor Software Factory. |
| `Bug` | Een reproduceerbare afwijking tussen verwacht en werkelijk gedrag. |
| `Verification` | Onveranderlijk bewijs en een oordeel over een story, epic of gebruikerssignaal. |
| `Meeting` | Een overleg met de Stakeholder, inclusief agenda, gesprek en gecontroleerde uitkomst. |

De **backlog** is geen apart object. Het is de lijst van alle stories die nog niet `DONE` zijn,
geordend op `sequenceNumber`.

## De hele route

In gewone taal gebeurt het volgende:

1. De Stakeholder legt uit voor wie het product is, wat het moet bereiken en welke grenzen gelden.
2. Feedback, zorgen en kansen worden als gebruikerssignalen bewaard.
3. Productontwerp onderzoekt het product en maakt een complete epic met het benodigde UX-ontwerp.
4. Productplanning bevriest de gekozen epicversie en verdeelt de hele epic in zelfstandige stories.
5. Alle nog niet afgeronde stories vormen samen één geprioriteerde backlog.
6. De dispatcher stuurt de bovenste uitvoerbare story naar Software Factory.
7. Wanneer Software Factory de story heeft opgeleverd, wordt de story `DONE` en kan gericht
   testwerk worden klaargezet.
8. Wanneer alle stories van een epic zijn opgeleverd, wordt een controle van de complete epic
   klaargezet.
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
hun geplande of handmatig gestarte `runProcessSession()` mag agents starten. Per module kan maximaal
één run tegelijk actief zijn. Een handmatige start krijgt een fout als al een run loopt; een geplande
botsing wordt overgeslagen en geregistreerd.

De Software Factory-dispatcher is het vierde uitvoerende onderdeel, maar geen intelligent proces. Hij
gebruikt geen agents en neemt geen productbesluiten.

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
| `StakeholderDirection` | Read-only query op de product-/overlegmodule | De actuele wil en prioriteiten van de Stakeholder. |
| Geldige `Decision`s | Query op het Besluitenregister | Grote keuzes die nu gelden. |
| `UserSignal`s | Read-only query op de productmodule | Feedback, problemen, kansen en kwaliteitszorgen. |
| Stories en `Verification`s | Read-only queries op planning en kwaliteit | Wat eerder werkelijk is gebouwd en aangetoond. |
| Huidige code en documentatie | Read-only checkout van `ProductAssignment.gitUrl` | Hoe het product er nu voorstaat. |

### Output

| Gegeven | Betekenis |
|---|---|
| `Epic` | De gekozen gebruikersverbetering, met eenduidige scope, bewijs, risico's, succescriteria en compleet UX-ontwerp. |
| Planningrequest | `requestEpicPlanning(...)` zet duurzaam planwerk bij Productplanning klaar en start geen agent. |
| Epicstatus | Alleen Productontwerp voert geldige epicovergangen uit op verzoek van planning of kwaliteit. |

Productontwerp mag een beschikbare epic nog verbeteren. Zodra Productplanning een exacte epicversie
kiest, wordt die versie bevroren en niet meer aangepast.

Onderzoeksvragen, bronnen, tussenresultaten en concepten blijven intern binnen Productontwerp.

## Productplanning als black box

**Doel:** epics en herstelverzoeken omzetten in volledige stories en alle open stories in één
uitlegbare volgorde zetten.

Een scheduler of bevoegde handmatige aanroep start `runProcessSession()`. De run claimt werk uit de
eigen `PlanningWorkItem`-queue. Een lege queue is een geldige no-op.

### Input

| Gegeven | Hoe komt het binnen? | Betekenis |
|---|---|---|
| `PlanningWorkItem` | Via een snel, idempotent requestcommand | Een verzoek om een epic, bugfix, ontbrekende dekking of handmatige herplanning te verwerken. |
| `Epic` | Read-only query op Productontwerp | De exacte bron die wordt gekozen en bevroren. |
| `Bug` en `Verification` | Read-only queries op Kwaliteitsbewaking | Bewijs voor herstelwerk of ontbrekende epicdekking. |
| `ProductAssignment`, `StakeholderDirection` en geldige `Decision`s | Read-only queries | De grenzen en actuele prioriteitsrichting. |
| Bestaande `Story`s | Eigen database | Reeds gepland, verzonden en opgeleverd werk. |
| Huidige code en documentatie | Read-only checkout van de publieke Git-URL | Context voor een realistische storiesplitsing. |

### Output

| Gegeven | Betekenis |
|---|---|
| `Story` | Zelfstandige productstory of bugfixstory met acceptatiecriteria, relevante UX, assets, status en `sequenceNumber`. |
| Backlogquery | Alle stories die niet `DONE` zijn, geordend op `sequenceNumber`. |
| Planningstatus | Zichtbaar resultaat van ieder `PlanningWorkItem`. |
| Kwaliteitsrequest | Gerichte story-, bugfix- of epiccontrole die in de kwaliteitsqueue wordt gezet. |

Alleen Productplanning schrijft stories, story-inhoud, status en volgorde. De dispatcher meldt
leveringsgebeurtenissen via publieke planningcommands en verandert de story niet rechtstreeks.

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
| `StakeholderDirection` en geldige `Decision`s | Read-only queries | Geldende product-, privacy- en kwaliteitsgrenzen. |
| `Story` | Read-only query op Productplanning | Wat is gebouwd en waar de oplevering bij hoort. |
| Bevroren `Epic` met UX | Read-only query op Productontwerp | De volledige bedoeling die na alle stories moet worden gecontroleerd. |
| `UserSignal` | Read-only query op de productmodule | De oorspronkelijke zorg of observatie die onderzocht moet worden. |
| Huidige code en documentatie | Read-only checkout van de publieke Git-URL | Informatie over risico's en relevante tests; geen bewijs van werkend gedrag. |

### Output

| Gegeven | Betekenis |
|---|---|
| `Bug` | Reproduceerbare bouwfout met verwacht en werkelijk gedrag, bewijs en ernst. |
| `Verification` | Onveranderlijk oordeel en bewijs over een story, epic of gebruikerssignaal. |
| Kwaliteitsbeeld | Berekend read-only overzicht van dekking, risico's en recent onderzoek. |
| Herstelrequest | Verzoek aan Productplanning om een bugfix of ontbrekende epicdekking te plannen. |

Kwaliteitsbewaking maakt geen stories en wijzigt geen epic. Zij publiceert bewijs en vraagt de
eigenaar via een betekenisvol command om de geldige vervolgactie.

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
| Storycommands | Meldingen `markStoryAsDispatched(...)`, `markStoryAsDeveloped(...)` of `recordDispatchFailure(...)` aan Productplanning. |
| `DeliveryAttempt` | Technische historie binnen Productplanning van verzending, response, fout en retry. |

De dispatcher verstuurt niets zolang Software Factory voor dat product nog een openstaande story
heeft. Software Factory hoeft de productrepository niet te lezen: alle inhoud en UX staan in het
storypakket.

## Wanneer een epic klaar is

Een story gebruikt drie eenvoudige statussen:

- `TODO` — klaar voor uitvoering maar nog niet verstuurd;
- `IN_PROGRESS` — naar Software Factory gestuurd en daar nog open;
- `DONE` — door Software Factory opgeleverd; dit is nog geen kwaliteitsoordeel.

Een epic gebruikt:

- `AVAILABLE` — complete versie die Productplanning mag kiezen;
- `IN_PLANNING` — exacte versie is gekozen en bevroren;
- `ACTIVE` — één of meer stories worden uitgevoerd of hersteld;
- `VERIFYING` — alle geplande stories zijn klaar en de complete epic wordt gecontroleerd;
- `COMPLETED` — de bedoelde gebruikersverbetering is aangetoond;
- `NOT_SUCCESSFUL` — alles is geleverd, maar het gebruikersresultaat is niet bereikt;
- `STOPPED` — bewust gestopt met een zichtbare reden;
- `SUPERSEDED` — een nog niet gekozen epicversie is door een nieuwere versie vervangen;
- `WITHDRAWN` — een nog niet gekozen epic is bewust ingetrokken.

Alle stories `DONE` betekent dus nog niet automatisch dat de epic is geslaagd. Productplanning zet
de epic zonder agent op `VERIFYING` en roept `requestEpicVerification(...)` aan. Dat command zet
alleen een `QualityWorkItem` in de queue. Tijdens een latere kwaliteitsrun wordt de hele epic getest.

Bij een bouwfout vraagt Kwaliteitsbewaking bugfixwerk aan. Bij ontbrekend gedrag binnen de bevroren
scope vraagt zij aanvullende stories voor dezelfde epic aan. Na herstel volgt opnieuw controle.
Alleen Productontwerp verwerkt het verificatieresultaat en sluit de epic af.

## Overleg en richting

De Stakeholder kan vanuit de UI een overleg starten. Een proces kan om overleg vragen wanneer
menselijke richting nodig is. Bij afsluiting maakt een notulenagent leesbare notulen en verwerkt hij
alleen expliciete uitkomsten:

- een concrete aanwijzing of correctie wordt `StakeholderDirection`;
- feedback of een kwaliteitszorg wordt `UserSignal`;
- alleen een grote, blijvende keuze wordt `Decision`;
- een gewone epic-, story-, bug- of prioriteitsactie blijft bij het betreffende productobject.

Een transcript verandert niet vanzelf het product. Iedere doorwerking gebeurt via een zichtbaar
command aan de module die het betreffende object bezit.

## Database, frontend en Git

De database is de productwaarheid. Iedere duurzame entiteit heeft precies één schrijvende module.
Andere modules en de frontend lezen via publieke queries en vragen wijzigingen alleen aan via
betekenisvolle commands. Niemand schrijft rechtstreeks in de tabellen van een andere module.

De frontend maakt dezelfde databasegegevens begrijpelijk voor mensen.

De publieke productrepository blijft wel de waarheid over de huidige code, tests en
productdocumentatie. Productontwerp, Productplanning en Kwaliteitsbewaking mogen de Git-URL uit de
`ProductAssignment` tijdens een sessie read-only uitchecken. Zij committen of pushen niets.

## Zes hoofdregels

1. De Stakeholder is de klant en diens expliciete richting is leidend.
2. Productontwerp maakt complete epics met UX, maar geen stories.
3. Productplanning maakt en ordent alle stories; de backlog is alleen een query op open stories.
4. Kwaliteitsbewaking levert bewijs en bugs, maar maakt geen stories en wijzigt geen epics.
5. Alleen `runProcessSession()` mag agents starten; requests zetten alleen duurzaam queuewerk klaar.
6. Iedere entiteit heeft één eigenaar en andere modules wijzigen haar alleen via publieke commands.

## Detaildocumenten

- [Processen, publieke interfaces en entiteiten](processen-en-entiteiten.md)
- [Besluitenregister](besluitenregister.md)
- [Overleggen met de Stakeholder](overleggen.md)
- [Frontend](frontend.md)
- [Productontwerp](productontwerp.md)
- [Productplanning](productplanning.md)
- [Kwaliteitsbewaking](kwaliteitsbewaking.md)
