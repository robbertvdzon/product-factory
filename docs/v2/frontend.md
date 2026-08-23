# Product Factory v2 — frontend

De frontend is de leesbare weergave van de productwaarheid in de database. Zij heeft geen eigen
productwaarheid. Acties lopen altijd via de publieke commands van de module die de betrokken
entiteit bezit.

Voor de Stakeholder is deze gebruikersinterface de normale ingang tot Product Factory: overleggen,
signalen, besluiten, prioriteitsacties en handmatige processessies beginnen hier.

## Ontwerpregels

- De gewone schermen gebruiken producttaal en geen agent-, prompt-, queue- of databasetaal.
- De frontend leest actuele gegevens en historie via publieke read-only queries.
- Zij schrijft nooit rechtstreeks in een moduletabel.
- Een commandfout wordt zichtbaar getoond en niet optimistisch als geslaagde wijziging bewaard.
- Technische sessies, queues en retries staan in een aparte operationele weergave.
- Productobjecten tonen hun bron, actuele status, versie en relevante koppelingen.

## Productoverzicht

Het hoofdscherm laat in één oogopslag zien:

- het productdoel en de harde grenzen;
- de actieve epics en eventuele handmatig urgente epic;
- de eerste `TODO`-story en de story die `IN_PROGRESS` is;
- de geordende backlog en open bugs;
- het huidige kwaliteitsbeeld en de ontwikkeling per kwaliteitsdimensie door de tijd;
- recente verificaties en belangrijke kwaliteitsrisico's;
- nieuwe en open gebruikerssignalen;
- geldige besluiten;
- aangevraagde of open overleggen.

Interne analyses, concepten, agentuitvoer en implementatiespecifiek geheugen staan hier niet. De
frontend blijft daardoor gelijk wanneer Productontwerp van de MVP naar de uitgebreide implementatie
overstapt.

## Inbox

De Inbox toont `UserSignal`s. Per signaal zijn zichtbaar:

- de oorspronkelijke tekst, bron, context en bijlagen;
- categorie en urgentie;
- status en onderzoeksuitkomst;
- koppelingen naar een verificatie, bug, epic of besluit;
- het bronoverleg wanneer de melding daar is ontstaan.

De oorspronkelijke melding blijft ongewijzigd. Alleen de productmodule past status en gecontroleerde
koppelingen aan. De tester doet dat via `recordSignalInvestigation(...)`, niet via directe
databasetoegang.

## Planning

Het planningsscherm toont:

- epics per actuele epicstatus;
- alle open stories op `sequenceNumber`;
- geannuleerde epics en stories apart van de backlog, met bron en reden;
- storytype `PRODUCT_STORY` of `BUGFIX`;
- de reden voor een handmatige prioriteitswijziging;
- de Software Factory-status van de verzonden story.

Er is geen afzonderlijke roadmapentiteit en geen tweede handmatige backlog. De epicstatussen en de
berekende storylijst zijn de enige bronnen.

## Detailpagina

Een epic, story, bug, verificatie, kwaliteitssnapshot, signaal of besluit heeft een rustige
detailpagina. Die toont alleen de velden die bij dat object horen, plus relaties naar bron- en
vervolgobjecten.

Een epic toont onder meer scope, gebruikersverbetering, succescriteria en het actuele UX-ontwerp.
Een story toont zelfstandig alle relevante UX en assets die ook naar Software Factory worden
verstuurd. Een verificatie toont omgeving, controles, oordeel en bewijs. Een besluit toont normaal
alleen de geldige tekst; een aparte archiefweergave toont eerdere versies, ingetrokken besluiten en
vervangingsrelaties.

De kwaliteitsweergave gebruikt `getCurrentQuality(...)` en `getQualityHistory(...)`. Zij toont geen
ondoorzichtige totaalscore, maar tijdlijnen voor onder meer kritieke bugs, onderzochte routes,
verificatie-uitkomsten, verouderde dekking en blokkades.

## Overleggen en richting geven

De Stakeholder kan vanuit het product of een detailpagina een overleg starten. Het overlegscherm
toont agenda, berichten, gekoppelde objecten, status, notulen en de expliciete doorwerking.

Snelle acties mogen ook rechtstreeks het juiste command aanbieden, bijvoorbeeld:

- productopdracht aanpassen;
- gebruikerssignaal indienen;
- een grote blijvende keuze via een overleg als besluit vastleggen;
- een urgente epic laten herprioriteren;
- een beschikbare epic intrekken of een actieve epic annuleren;
- een processessie handmatig starten.

Een handmatige `runProcessSession()` geeft een duidelijke fout als in die module al een run actief
is.

## Operationele weergave

Technische gebruikers kunnen apart zien:

- de eigen `ProcessSession`s van iedere intelligente module;
- `PlanningWorkItem`s en `QualityWorkItem`s met status en fout;
- `DeliveryAttempt`s en externe Software Factory-referenties;
- overgeslagen schedulerbotsingen en idempotente retries.

Deze informatie verklaart wat de automatisering doet, maar verandert nooit de inhoudelijke status
van een epic, story, bug of verificatie.

## Gerelateerde documenten

- [Overzicht](overzicht.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
- [Overleggen met de Stakeholder](overleggen.md)
