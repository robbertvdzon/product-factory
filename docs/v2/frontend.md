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
starten. Het backendcontract en secretbeheer staan in [Technische basis](technische-basis.md).

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

Het hoofdscherm laat in één oogopslag zien:

- het productdoel en de harde grenzen;
- de actieve epics en eventuele handmatig urgente epic;
- de eerste `TODO`-story en de story die `IN_PROGRESS` is;
- de geordende backlog en open bugs;
- het huidige kwaliteitsbeeld en de ontwikkeling per kwaliteitsdimensie door de tijd;
- recente verificaties en belangrijke kwaliteitsrisico's;
- nieuwe en open gebruikerssignalen;
- geldige besluiten;
- agentrollen met geheugen dat aandacht of correctie nodig heeft;
- aangevraagde of open overleggen.

Interne analyses, concepten en agentuitvoer staan hier niet. Permanent rolgeheugen is wel zichtbaar
in het aparte beheerscherm, omdat de Stakeholder dit moet kunnen controleren en corrigeren.

## Agentgeheugen

Het scherm **Agentgeheugen** groepeert geheugen per proces en stabiele agentrol. Een rol ziet nooit
het geheugen van een andere rol, maar de Stakeholder mag binnen het eigen product alle rollen
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

## Algemene AI-instellingen

Het scherm **Algemene instellingen** bevat een tabel met alle geregistreerde `AiJobKey`s. Per job
kan een bevoegde Stakeholder of beheerder kiezen:

- provider `CODEX`, `CLAUDE` of, buiten productie, `MOCKED`;
- het model of mockprofiel;
- of de job momenteel ingeschakeld is.

De UI toont ook de configuratieversie en laatste wijziging. Een wijziging geldt alleen voor nieuwe
AI-taken. Een gequeue'de of lopende taak blijft zichtbaar met de provider, het model en de
configuratieversie waarmee zij is aangemaakt. Productie weigert `MOCKED` zowel bij opslaan als bij
taakaanvraag.

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
- een processessie handmatig starten;
- geheugen van een gekozen agentrol toevoegen, vervangen of intrekken.

Een handmatige `runProcessSession()` geeft een duidelijke fout als in die module al een run actief
is.

## Operationele weergave

Technische gebruikers kunnen apart zien:

- het `ImplementationManifest` van de actieve build met gekozen artifact, variant, versie en
  broncommit per capability;
- de eigen `ProcessSession`s van iedere intelligente module;
- `PlanningWorkItem`s en `QualityWorkItem`s met status en fout;
- `AiTask`s met aanvrager, provider, model, configuratieversie, status en attemptnummer;
- veilige AI-voortgang, laatste heartbeat, lease, hersteltermijn en retryreden;
- laptop- en mockworkers met capabilities, capaciteit en laatste aanwezigheid;
- `DeliveryAttempt`s en externe Software Factory-referenties;
- overgeslagen schedulerbotsingen en idempotente retries.

Deze informatie verklaart wat de automatisering doet, maar verandert nooit de inhoudelijke status
van een epic, story, bug of verificatie.

## Acceptatietesten

Alleen in de acceptatieomgeving toont de frontend het scherm **Acceptatietesten** en op iedere pagina
een herkenbare banner **synthetische tijdelijke data — authenticatie uit**. Het scherm bedient
Product Factory Testbed via een acceptance-only Test Control API; het schrijft niet rechtstreeks in
de Product Factory-database.

De tester kan hier:

- de actieve dataset-, scenario-, Testbed- en implementatieversies zien;
- de omgeving resetten naar vaste initiële testdata;
- een beschreven AI- of Software Factory-scenario activeren;
- expliciete externe stappen uitvoeren, zoals een mockworker laten slapen of hervatten, een externe
  story afronden of de volgende externe call laten mislukken;
- daarna via links de normale processessie, dispatcher, backlog, kwaliteit en operationele historie
  bekijken;
- per scenariostap de verwachte zichtbare uitkomst afvinken.

De UI biedt vaste, versieerbare scenario's in plaats van vrije scripts of willekeurige
mockresponse-JSON. Automatische schedules staan in acceptatie standaard uit. Processen en dispatcher
worden via hun bestaande handmatige acties gestart, zodat iedere overgang goed te volgen is. Reset
waarschuwt dat alle tijdelijke acceptatiewijzigingen verdwijnen en is technisch onmogelijk in
productie.

## Gerelateerde documenten

- [Overzicht](overzicht.md)
- [Technische basis](technische-basis.md)
- [Deployment en operatie](deployment-en-operatie.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
- [Overleggen met de Stakeholder](overleggen.md)
- [Agentgeheugen](agentgeheugen.md)
- [AI-uitvoering](ai-uitvoering.md)
- [Maven en Spring Modulith](maven-en-spring-modulith.md)
- [Integratie- en acceptatietesten](integratie-en-acceptatietesten.md)
