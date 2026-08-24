# Product Factory v2 — frontend

De frontend is de leesbare weergave van de productwaarheid in de database. Zij heeft geen eigen
productwaarheid. Acties lopen altijd via de publieke commands van de module die de betrokken
entiteit bezit.

Voor de Stakeholder is deze gebruikersinterface de normale ingang tot Product Factory: overleggen,
signalen, besluiten, prioriteitsacties en handmatige processessies beginnen hier.

De frontend wordt volledig opnieuw gemaakt. De v1-code, widgets, schermindeling en specifieke
presentatiepatronen worden niet hergebruikt. Dit document beschrijft gewenst gedrag en publieke
gegevens, niet hoe het oude dashboard was opgebouwd.

## Ontwerpregels

- De gewone schermen gebruiken producttaal en geen agent-, prompt-, queue- of databasetaal.
- De frontend leest actuele gegevens en historie via publieke read-only queries.
- Zij schrijft nooit rechtstreeks in een moduletabel.
- Een commandfout wordt zichtbaar getoond en niet optimistisch als geslaagde wijziging bewaard.
- Technische sessies, queues en retries staan in een aparte operationele weergave.
- Productobjecten tonen hun bron, actuele status, versie en relevante koppelingen.
- De kern blijft bruikbaar op een viewport van 320 CSS-pixels en bij 200% tekstvergroting.
- Op bredere schermen gebruikt de applicatieschil de beschikbare ruimte; mobiel bruikbaar betekent
  niet dat de desktopweergave tot een smalle kolom wordt beperkt.

## Authenticatie

Productie toont Google-login. De frontend wisselt het Google ID-token via de backend om voor een
eigen Product Factory-sessie en gebruikt daarna uitsluitend die sessie. De backend blijft altijd de
autoritatieve beveiligingsgrens; een verborgen knop of lokale frontendstatus is geen autorisatie.

De frontend:

- bepaalt de sessiestatus voordat beschermde schermen worden getoond;
- stuurt de eigen sessie via de centrale API-client mee;
- handelt een verlopen of ingetrokken sessie als uitgelogd af;
- biedt een duidelijke logoutactie;
- maakt onderscheid tussen niet ingelogd, niet bevoegd en een technische fout;
- bewaart geen Google-token of applicatiesessie in leesbare productdata of logging.

Acceptatie schakelt authenticatie expliciet uit. Op iedere pagina staat daar de vaste banner
**Acceptatie — synthetische tijdelijke data — authenticatie uit**. Productie kan niet in deze modus
starten. Het backendcontract en secretbeheer staan in [Technische basis](../platform/technische-basis.md).

## Cache en nieuwe deployments

Een gebruiker moet na een deployment zonder handmatig cachelegen de nieuwe frontend kunnen laden.
Daarom gelden deze regels:

- de nieuwe webbuild gebruikt geen actieve PWA-service worker;
- het oude pad `/flutter_service_worker.js` blijft tijdens de overgang een `no-store`
  opruimrespons geven die oude Product Factory-service workers en hun caches verwijdert;
- JavaScript- en CSS-assets krijgen een inhoudshash in hun bestandsnaam;
- alleen gehashte assets krijgen een lange `immutable` cacheheader;
- `index.html`, bootstrapbestanden, manifest, serviceworkerpad en versiegegevens worden niet
  langdurig gecachet;
- de SPA-fallback serveert routes correct zonder een ontbrekend vast asset als blijvende
  `index.html`-response te cachen.

De cacheheaders en de echte productiecontainer worden als onderdeel van de normale build en
deployment gecontroleerd. De frontend vertrouwt niet op een gebruiker die browserdata verwijdert.

## Versie- en omgevingsinformatie

Iedere frontendbuild bevat alleen niet-geheime buildmetadata:

- applicatie- en frontendversie;
- volledige Git-revisie;
- UTC-buildtijd;
- omgeving `local`, `acceptance` of `production`.

De backend biedt haar eigen beperkte versie-informatie. Het beheerscherm toont frontendversie,
backendversie, omgeving, bronrevisies, buildtijden en later het `ImplementationManifest`. Ontbrekende
of ongeldige buildmetadata verschijnt als `Onbekend` en wordt niet uit een repository in de
draaiende container gelezen.

De frontend controleert periodiek een niet-langdurig gecachete versiebron. Is een nieuwere build
beschikbaar, dan verschijnt eenmaal de melding **Nieuwe versie beschikbaar — vernieuwen**. De
gebruiker houdt controle over het vernieuwen en de implementatie voorkomt reloadloops. Een bekende
incompatibiliteit tussen frontend- en backendcontract wordt als duidelijke technische fout getoond.

## Productoverzicht

Het hoofdscherm blijft bewust rustig. Het laat voor het gekozen product alleen zien:

- het productdoel;
- de belangrijkste actieve epic en de story die nu bij Software Factory wordt uitgevoerd;
- enkele concrete aandachtspunten, zoals een vastgelopen kwaliteitsretry of nieuw signaal;
- korte links naar de bijbehorende detailschermen.

De productstatus, dispatchinstelling, harde grenzen, volledige epic- en backloglijsten, bugs,
kwaliteitshistorie, signalen, besluiten, geheugen en overleggen blijven allemaal bereikbaar via hun
eigen scherm. Ze worden niet nogmaals als compacte dashboards op het hoofdscherm gepropt.

Interne analyses, concepten en agentuitvoer staan hier niet. Permanent rolgeheugen is wel zichtbaar
in het aparte beheerscherm, omdat de Stakeholder dit moet kunnen controleren en corrigeren.

## Agentgeheugen

Het scherm **Agentgeheugen** groepeert geheugen per proces en stabiele agentrol. Een rol ziet nooit
het geheugen van een andere rol, maar de globale Stakeholder mag binnen ieder product alle rollen
beheren.

Per rol toont de frontend:

- de weergavenaam en stabiele rolesleutel;
- alle actuele geheugenitems;
- gebruikt en beschikbaar contextbudget;
- toevoegen, vervangen en intrekken met een verplichte reden;
- een peildatum om de toen actieve set te reconstrueren;
- de volledige append-only versiegeschiedenis;
- actor, wijzigingsreden en geldigheidsperiode;
- welke processessies een exacte geheugenversie hebben gelezen.

Vervangen overschrijft de oude inhoud niet en intrekken verwijdert geen historie. Commands bevatten
de verwachte actuele versie, zodat een gelijktijdige wijziging als conflict zichtbaar wordt. De
frontend schrijft nooit rechtstreeks in de geheugentabellen.

## AI-modellen binnen Instellingen

De sectie **Instellingen → AI-modellen** bevat een tabel met alle geregistreerde `AiJobKey`s. Zij
staat omwille van vindbaarheid bij de product- en procesinstellingen, maar toont nadrukkelijk
**Geldt voor alle producten**. Per job kan de ene globale Stakeholder of een bevoegde beheerder
kiezen:

- provider `CODEX`, `CLAUDE` of, buiten productie, `MOCKED`;
- het model of mockprofiel;
- of de job momenteel ingeschakeld is.

De UI toont ook de configuratieversie en laatste wijziging. Een wijziging geldt alleen voor nieuwe
AI-taken. Een gequeue'de of lopende taak blijft zichtbaar met de provider, het model en de
configuratieversie waarmee zij is aangemaakt. Productie weigert `MOCKED` zowel bij opslaan als bij
taakaanvraag.

## Signalen

Het scherm **Signalen** toont `UserSignal`s en gebruikt geen afzonderlijke inboxentiteit. Open,
onderzochte en verwerkte signalen komen uit `findUserSignals(...)`. Per signaal zijn zichtbaar:

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

- alle open stories op `sequenceNumber`;
- geannuleerde stories apart van de backlog, met bron en reden;
- bij een door Software Factory geannuleerde story uitleg dat Product Factory de complete epic na
  het overige werk opnieuw op de feitelijke producttoestand beoordeelt;
- storytype `PRODUCT_STORY` of `BUGFIX`;
- een tijdelijke dispatchreservering als **Wordt verstuurd**, zonder een vijfde publieke
  storystatus te introduceren;
- de reden voor een handmatige prioriteitswijziging;
- de Software Factory-status van de verzonden story en een eventuele technische dispatchblokkade.
- bij een `DONE` story de `deliveredCommitSha`, de werkelijk geteste deploymentrevision en een
  zichtbare blokkade **Wacht op deployment** wanneer de doelomgeving nog achterloopt.

Er is geen afzonderlijke roadmapentiteit en geen tweede handmatige backlog. De epicstatussen en de
berekende storylijst zijn de enige bronnen.

Epics staan niet nogmaals als hoofdlijst bij Planning. Het scherm **Ontwerp** gebruikt
`findEpics(...)` en toont actuele en historische epics per lifecyclestatus. Op epicdetail kan de
Stakeholder, wanneer de status dat toestaat, de epic met reden laten herprioriteren, intrekken of
annuleren. Planning toont bij een handmatige prioriteitsactie de reden en de zichtbare doorwerking
op nog niet verstuurde stories.

## Detailpagina

Een epic, story, bug, verificatie, kwaliteitssnapshot, signaal, besluit, meeting of processessie heeft een rustige
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

### Kwaliteitsretries

De kwaliteitsweergave bevat een vaste lijst **Opnieuw te proberen testwerk** uit
`findRetryableQualityWorkItems()`. Ieder retrybaar workitem is zichtbaar; de items met het hoogste
`attemptCount` staan bovenaan en bij een gelijke waarde staat de oudste laatste poging eerst. Per
item toont de UI minimaal product, type, doel, blokkadereden, aantal pogingen, laatste poging,
`retryAfter` en vanaf vijf pogingen **Aandacht nodig**.

Ieder retrybaar item heeft de actie **Retry now**. De UI:

1. roept `retryQualityWorkItem(...)` aan; historie en `attemptCount` blijven staan, `retryAfter`
   wordt leeggemaakt en het item wordt `PENDING`;
2. start daarna de normale `runProcessSession(workItem.productId)` van Kwaliteitsbewaking als die
   voor dit product nog niet draait;
3. behandelt een gelijktijdige `ProcessAlreadyRunning` voor dit product als bevestiging dat de
   kwaliteitsrun al bezig is, niet als verloren retry.

Een workitem dat na de vaste batchselectie `PENDING` is geworden, wacht zichtbaar op de volgende
run. De knop start nooit rechtstreeks een agent en maakt nooit een tweede gelijktijdige
kwaliteitssessie.

## Overleggen en richting geven

De Stakeholder kan vanuit het product of een detailpagina een overleg starten. Het overlegscherm
toont agenda, berichten, gekoppelde objecten, status, notulen en de expliciete doorwerking.

Snelle acties mogen ook rechtstreeks het juiste command aanbieden, bijvoorbeeld:

- productopdracht aanpassen;
- dispatching voor een product bewust in- of uitschakelen;
- automatische schedules per proces instellen of uitschakelen;
- gebruikerssignaal indienen;
- een grote blijvende keuze via een overleg als besluit vastleggen;
- een urgente epic laten herprioriteren;
- een beschikbare epic intrekken of een actieve epic annuleren;
- iedere normaal geplande proces- of dispatchersessie handmatig starten;
- retrybaar testwerk met **Retry now** direct klaarzetten en zo nodig de kwaliteitsrun starten;
- geheugen van een gekozen agentrol toevoegen, vervangen of intrekken.

Een handmatige start kiest altijd een product en roept `runProcessSession(productId)` of
`runDispatchSession(productId)` aan. Zij geeft een duidelijke fout als in dezelfde module voor dat
product al een run actief is. De REST-ingang retourneert voor deze botsing bijvoorbeeld HTTP 409.
Een run voor een ander product blijft wel toegestaan.

De overlegweergave is meer dan een lijst. Een open overleg toont agenda, berichten, gekoppelde
objecten en open acties. De Stakeholder kan berichten toevoegen en het overleg afsluiten. Daarna
toont de UI de notulen en per expliciete uitkomst of het bijbehorende command is uitgevoerd of nog
aandacht nodig heeft. Vanuit een product-, epic-, signaal-, bug- of besluitdetail kan een overleg
met dat object als bron worden gestart.

## Beheer

Om de dagelijkse navigatie rustig te houden groepeert **Beheer** de minder vaak gebruikte onderdelen:

- **Instellingen** — product aanmaken, productopdracht en testconfiguratie aanpassen, dispatching
  bewust aan- of uitzetten, de schedules van de vier uitvoerende onderdelen beheren en de globale
  provider-, model- en `enabled`-keuze per `AiJobKey` bekijken of wijzigen;
- **Besluiten** — actuele besluiten, peildatum, archief, versies, intrekkingsreden en opvolgers;
- **Agentgeheugen** — actueel rolgeheugen, contextbudget, historie en add/replace/retract-acties.

**Operatie** blijft binnen Beheer een afzonderlijk technisch onderdeel. De frontend mag deze
informatie anders groeperen op mobiel, zolang iedere functie rechtstreeks bereikbaar blijft.
De pagina **Instellingen** heeft boven de titel een expliciete actie **Terug naar Beheer** en een
compacte inhoudsnavigatie naar Product, Omgevingen, Automatisering en AI-modellen.

### Automatisering per product

Onder **Instellingen → Automatisering** toont de frontend per uitvoerend onderdeel:

- of automatische starts zijn ingeschakeld;
- het volledige schema in gewone taal, bijvoorbeeld **Dagelijks om 07:00 en 20:00** of
  **Maandag 09:00 · vrijdag 21:00**;
- de tijdzone, standaard `Europe/Amsterdam`;
- de eerstvolgende geplande start;
- **Bewerken** en de bestaande actie **Nu starten**.

De bewerkweergave laat bij dag/tijdplanning meerdere regels toevoegen en verwijderen. Iedere regel
heeft één of meer gekozen weekdagen en één of meer tijden. De andere modus bevat één vast interval
in hele minuten; een interval en dag/tijdregels kunnen niet worden gemengd. De UI toont geen
cronexpressie. Opslaan roept `updateProcessSchedule(...)` aan met de verwachte configuratieversie.
Een wijzigingsconflict, dubbele combinatie en ongeldige tijd worden zichtbaar getoond; de frontend
rekent `nextRunAt` niet zelf uit.

Uitschakelen voorkomt alleen toekomstige automatische starts. Handmatig starten blijft mogelijk en
een bestaande lopende of `WAITING_FOR_AI`-sessie wordt niet geannuleerd. In acceptatie staan
automatische starts door de omgevingsgrens standaard uit, ook wanneer de bewaarde
productconfiguratie `enabled = true` is; de vaste banner en het acceptatiescherm maken dit zichtbaar.
Bij een nieuw product tonen de vier regels eerst **Niet ingesteld** en geen volgende start; de
Stakeholder moet een geldig patroon kiezen voordat inschakelen kan worden opgeslagen.

## Operationele weergave

Technische gebruikers kunnen apart zien:

- het `ImplementationManifest` van de actieve build met gekozen artifact, variant, versie en
  broncommit per capability;
- de eigen `ProcessSession`s van iedere intelligente module en de dispatcher;
- het ingestelde schema en `nextRunAt` van ieder uitvoerend onderdeel;
- `PlanningWorkItem`s en `QualityWorkItem`s met status, fout, blokkadereden, `attemptCount` en
  `retryAfter`;
- `AiTask`s met aanvrager, provider, model, configuratieversie, status en attemptnummer;
- geblokkeerde processessies door `AI_JOB_DISABLED`, een terminale taakfout of een geannuleerde
  technische taak, inclusief product, volgende retry en behouden domeinclaim;
- veilige AI-voortgang, laatste heartbeat, lease, hersteltermijn en retryreden;
- laptopworkers met capabilities, capaciteit en laatste aanwezigheid;

De MVP toont operationele aandachtspunten alleen in deze UI. E-mail, Telegram of andere externe
notificaties vallen buiten de MVP en kunnen later als aparte uitgaande adapter worden toegevoegd.
- `DeliveryAttempt`s en externe Software Factory-referenties;
- overgeslagen schedulerbotsingen en idempotente retries.

De operationele weergave heeft minimaal de deelweergaven **Processessies**, **Werkqueues**,
**AI-uitvoering**, **Dispatcher** en **Versies**. `findProcessSessions(...)` of de gelijkwaardige
dispatcherquery levert per module en product de lijst, nieuwste eerst. Een sessiedetail toont
start- en eindtijd, status, leesbare uitkomst, blokkade of fout, implementatie, gebruikte inputs,
AI-taken en publicaties. Ook een succesvolle no-op en een overgeslagen schedulerbotsing blijven
zichtbaar, zodat een handmatige run altijd verklaarbaar is.

Deze informatie verklaart wat de automatisering doet, maar verandert nooit de inhoudelijke status
van een epic, story, bug of verificatie.

## Acceptatietesten

Alleen in de acceptatieomgeving toont de frontend het scherm **Acceptatietesten** en op iedere pagina
de vaste banner **Acceptatie — synthetische tijdelijke data — authenticatie uit**. Het scherm bedient
Product Factory Testbed via een acceptance-only Test Control API; het schrijft niet rechtstreeks in
de Product Factory-database.

De tester kan hier:

- de actieve dataset-, scenario-, Testbed- en implementatieversies zien;
- de omgeving resetten naar vaste initiële testdata;
- een beschreven AI- of Software Factory-scenario activeren;
- server-side AI-mockantwoorden klaarzetten, inzien, verwijderen of resetten, inclusief de gewenste
  uitkomst en veilige JSON- of artifactinhoud;
- expliciete externe Software Factory-stappen uitvoeren, zoals een story afronden of annuleren, of
  de volgende externe call laten mislukken;
- daarna via links de normale processessie, dispatcher, backlog, kwaliteit en operationele historie
  bekijken;
- per scenariostap de verwachte zichtbare uitkomst afvinken.

De UI biedt vaste, versieerbare scenario's voor normale productflows. Voor gerichte AI-tests mag de
tester daarnaast via een schema-ondersteund formulier een mockantwoord klaarzetten; vrije scripts
zijn niet toegestaan. Automatische schedules staan in acceptatie standaard uit. Alle functies die
normaal gepland draaien, inclusief processen en dispatcher, worden via hun gewone handmatige acties
gestart, zodat iedere overgang goed te volgen is. Reset
waarschuwt dat alle tijdelijke acceptatiewijzigingen verdwijnen en is technisch onmogelijk in
productie.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Product- en overleg-API](product-en-overleg-api.md)
- [Technische basis](../platform/technische-basis.md)
- [Deployment en operatie](../platform/deployment-en-operatie.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Overleggen met de Stakeholder](overleggen.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Maven en Spring Modulith](../platform/maven-en-spring-modulith.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
