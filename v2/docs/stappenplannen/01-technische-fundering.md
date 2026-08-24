# Stap 1 — Technische fundering

## Doel en status van dit document

Dit document is de zelfstandige uitvoeropdracht voor het vervangen van de huidige Product Factory
door een volledig nieuwe implementatie. Een uitvoerende agent moet dit document volledig lezen
voordat hij bestanden verwijdert of nieuwe code schrijft. Context uit een eerder gesprek is niet
nodig.

De eerste oplevering heet functioneel gewoon **Product Factory**. Gebruik `v2` niet in Maven-
artifactnamen, Java- of Kotlin-packages, URLs, schermteksten, documentnamen of modulenamen. Alleen
een technisch geïsoleerde overgangsdatabase of PVC mag tijdelijk `_v2` bevatten.

De eerste oplevering bevat nog geen Productontwerp, Productplanning, Kwaliteitsbewaking,
Software Factory-dispatcher of AI-proceslogica. Zij levert eerst de volledige technische fundering:

- nieuwe repository- en Maven-structuur;
- configuratie en secrets;
- productie- en acceptatiedatabase;
- backend- en frontendauthenticatie;
- een minimale maar bruikbare frontend;
- frontendcache- en versieregels;
- containers, builds, tests en deployments;
- lokale ontwikkeling, acceptatie en productie;
- health, logging, metrics, backups en herstel.

Functioneel onvolledige releases mogen rechtstreeks naar productie. Een release mag echter nooit
onveilig zijn, secrets lekken, een onbeschermde productie-API aanbieden of de oude database
destructief aanpassen.

## Normatieve architectuur voor later

De nieuwe technische basis moet aansluiten op de al beschreven architectuur. Deze documenten
blijven behouden en zijn normatief waar zij de technische fundering raken:

- [Overzicht](../overzicht.md)
- [Technische basis](../platform/technische-basis.md)
- [Deployment en operatie](../platform/deployment-en-operatie.md)
- [Maven en Spring Modulith](../platform/maven-en-spring-modulith.md)
- [Frontend](../stakeholder/frontend.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)

Tijdens de opschoning wordt de volledige inhoud van de huidige map `v2/docs` naar `docs` verplaatst.
Omdat alle documenten gezamenlijk verhuizen, moeten relatieve links tussen deze documenten blijven
werken. De technische overnamebestanden staan tijdelijk in `v2/files`. Na de verhuizing en het
onderbrengen van die bestanden op hun definitieve plek bestaat geen map `v2` meer.

## Hoofdregels voor de vervanging

1. Hergebruik geen v1-domeincode, v1-datamodel, v1-Flywaymigraties, v1-agentprompts,
   v1-productflows of v1-functionele documentatie.
2. Infra mag als bewezen patroon worden behouden of herschreven: secretbeheer, CI/CD, containers,
   OpenShift, caching, authenticatie, healthchecks en backups.
3. Houd `secrets.env` in de repositoryroot. Het bestand is gitignored en wordt geleidelijk
   aangepast; verwijder het niet tijdens de opschoning.
4. Gebruik nooit `git clean -fdx` of een brede verwijderopdracht die gitignored bestanden kan
   wissen. Verwijder uitsluitend expliciet gecontroleerde paden.
5. Toon of commit nooit waarden uit `secrets.env`.
6. De Git-geschiedenis bewaart v1-code en oude documentatie. Houd daarom geen tijdelijke kopie van
   v1 in een `temp`, `old`, `legacy` of `v1`-map.
7. Maak vóór de verwijdering een herkenbare tag `v1-final`, tenzij die al bestaat.
8. Maak vóór databasewerk een laatste gevalideerde v1-databasebackup. Git bewaart database-inhoud
   en niet-gecommitte secrets niet.
9. Stappen 0 tot en met 8 bouwen en verifiëren lokaal en in CI, maar deployen niets naar OpenShift.
   Stap 9 doet de eerste bewuste, handmatige deployment naar acceptatie en daarna productie. Pas
   stap 10 automatiseert deze doorstroom voor volgende pushes naar `main`.
10. Noem de nieuwe applicatie en de nieuwe modules overal gewoon `product-factory`.

## Uitvoeringsmandaat en stopvoorwaarden

Een expliciete opdracht om **dit volledige stappenplan uit te voeren** omvat de noodzakelijke
repositorywijzigingen, het maken en pushen van de bewaartag en commits, het aanmaken van de nieuwe
v2-database-infrastructuur en de handmatige deployments naar acceptatie en productie in stap 9.
Een opdracht voor slechts één stap geeft geen toestemming voor latere stappen.

Het verwijderen van de oude v1-database, de oude database-PVC of de laatste bruikbare backup valt
nooit onder dit plan. Daarvoor blijft een afzonderlijke, expliciete opdracht nodig. Stop bovendien
vóór de eerste destructieve repositorywijziging wanneer geen veilige bestemming of
encryptieontvanger voor de reservekopie van `secrets.env` kan worden vastgesteld. Vraag dan alleen
die ontbrekende informatie; zet de uitvoering daarna vanaf hetzelfde controlepunt voort.

Ontbrekende toegang tot een extern systeem is eveneens een stopvoorwaarde wanneer die toegang niet
uit bestaande lokale configuratie, de repository of de actieve clustercontext kan worden afgeleid.
Toon bij zo'n controle nooit credentials of tokens in uitvoer.

## Technische v1-lessen die behouden blijven

De oude code is geen contract. Alleen deze technische lessen worden opnieuw geïmplementeerd:

- de configuratielagen, het lokale `secrets.env` en Sealed Secrets;
- onderling compatibele buildtoolchains, echte containerbuilds en immutable image-identiteit;
- een geïsoleerde productiedatabase, veilige Flywaymigraties, gevalideerde backups en restoretests;
- een eenvoudige deployment- en beheerbasis, plus productieauthenticatie, frontendcachegedrag en
  zichtbare versie-informatie.

De blijvende contracten staan in [Technische basis](../platform/technische-basis.md),
[Deployment en operatie](../platform/deployment-en-operatie.md) en [Frontend](../stakeholder/frontend.md). Het testbed was al
onderdeel van de nieuwe architectuur en staat in
[Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md).

Dit plan neemt uit v1 bewust geen revision- of worktree-attestatie, oude status- en
afhankelijkheidsmodellen, aanvullende agentbeveiligingsconstructies of oude frontendwidgets,
schermindelingen en testsuites over. Nieuwe functionele modules krijgen later hun eigen eisen.

## Te behouden bestanden en patronen

De map `v2/files` bevat gecontroleerde kopieën van technische v1-bestanden die als invoer voor de
nieuwbouw dienen. De actieve originelen blijven tijdens de voorbereiding op hun huidige plaats.
Gebruik vanaf de opschoning uitsluitend de kopieën hieronder als overnamebron. `Behouden` betekent
niet dat hun inhoud ongewijzigd correct blijft; de uitvoerende agent moet ze tijdens dit plan
actualiseren en naar hun definitieve plek verplaatsen.

| Pad | Actie |
|---|---|
| `secrets.env` | op zijn huidige plaats behouden; nooit tonen of committen |
| `v2/files/secrets.env.example` | naar de root verplaatsen en aanpassen aan het nieuwe secretcontract |
| `v2/files/properties.default.env` | naar de root verplaatsen en volledig herschrijven voor de nieuwe defaults |
| `v2/docs/**` | behouden en gezamenlijk naar `docs/**` verplaatsen |
| `v2/files/.gitignore`, `v2/files/.dockerignore` | naar de root verplaatsen en opschonen |
| `v2/files/.github/workflows/**` | structuur behouden; modulepaden, images en deploymentflow herschrijven |
| `v2/files/deploy/**` | bruikbare Kustomize-, OpenShift-, Sealed Secret- en backuppatronen behouden |
| `v2/files/deploy/seal-secrets.sh` | behouden en aanpassen aan de nieuwe verplichte sleutels |
| `v2/files/docker-compose.yml` | als lokale composition root volledig herschrijven |
| `v2/files/quality/detekt.yml` | behouden wanneer de nieuwe backend Kotlin gebruikt |
| `v2/files/product-factory` | als eenvoudige lokale CLI herschrijven |
| `v2/files/dashboard-frontend/nginx.conf` | cache-, security- en SPA-patronen behouden of gelijkwaardig opnieuw bouwen |
| `v2/files/tools/agent-worker-launchagent` | alleen het installatie-, restart- en logconcept behouden; workercontract herschrijven |

De volledige herkomst en bestemming staan in [`v2/files/README.md`](../../files/README.md). Breng
wijzigingen niet parallel aan in de actieve v1-bron en de overnamekopie. Alleen een bewust
vastgelegde actualisatie van de overnamekopie voorkomt onduidelijkheid over welke versie leidend is.

Verwijder uiteindelijk alle oude domeinmodules, oude frontendimplementatie, oude backendproxy,
oude workspacecode, oude agentworkerimplementatie, oude Flywaymigraties, oude functionele tests,
`docs/stories`, `docs/stories/worklog`, `docs/factory`, oude architectuurplannen en alle overige
v1-documentatie. Verwijder ook de volledige map `.factory`, `tools/verify`, de v1-specifieke Docker
Engine-runner en gegenereerde mappen zoals `target`, `build`, `.dart_tool` en `work` uit de nieuwe
uitgangssituatie, maar raak `secrets.env` niet. Voeg later alleen een nieuwe centrale
verificatie-ingang toe wanneer daar een concrete behoefte voor bestaat.

## Beoogde eerste oplevering

De eerste technische release gebruikt versienummer `0.1.0` en heeft zichtbaar de volgende
eigenschappen:

- `https://product-factory.vdzonsoftware.nl` toont de nieuwe frontend;
- productie vereist Google-login;
- acceptatie is bereikbaar zonder login en toont op iedere pagina een duidelijke banner;
- de backend draait als één Spring Boot-applicatie;
- de frontend gebruikt alleen de publieke backend-API;
- productie gebruikt een nieuwe PostgreSQL-database;
- acceptatie gebruikt een resetbare in-memory database met vaste synthetische gegevens;
- het beheer-/informatiescherm toont frontend-, backend-, Git- en omgevingsidentiteit;
- er is nog geen functionele procesmodule actief.

## Uitvoeringsvolgorde

Voer onderstaande stappen in volgorde uit. Rond per stap de genoemde verificatie af voordat de
volgende stap begint. Houd `main` bij iedere push bouwbaar. Tijdens stappen 0 tot en met 8 blijft
automatische deployment uit en wordt de gedeeltelijk opgebouwde applicatie niet naar OpenShift
gestuurd.

### Stap 0 — Leg de uitgangssituatie vast

1. Controleer dat de worktree schoon is en gelijkloopt met `origin/main`.
2. Noteer de huidige commit en de concrete productie-images.
3. Maak en push, indien nog afwezig, de tag `v1-final`.
4. Maak een custom-format backup van de v1-productiedatabase.
5. Valideer de backup met `pg_restore --list` en maak een SHA-256-checksum.
6. Controleer dat `secrets.env` bestaat, gitignored is en bestandsrechten `0600` heeft.
7. Maak buiten de repository een versleutelde reservekopie van `secrets.env`; leg geen waarde vast
   in logs of documentatie. Gebruik alleen een ondubbelzinnig bestaande bestemming en
   encryptieontvanger. Als die niet veilig kunnen worden vastgesteld, stop dan hier en vraag de
   stakeholder om deze twee gegevens.
8. Inventariseer welke huidige Cloudflare-, Argo CD- en OpenShiftobjecten de productie- en
   acceptatiehostnamen bedienen.

**Verificatie:** tag is naar de bedoelde commit te herleiden, backup is leesbaar, secrets zijn niet
door Git gevolgd en de actieve routes en images zijn genoteerd zonder secretwaarden.

### Stap 1 — Maak een schone, definitieve repositorystructuur

1. Verplaats alle documenten uit `v2/docs` naar `docs` en herstel relatieve links.
2. Verwijder alle overige oude documentatie.
3. Verwijder de oude functionele broncode, tests, migraties en workspaceonderdelen.
4. Breng de in de bewaartabel genoemde kopieën uit `v2/files` naar hun definitieve paden, werk ze
   daar bij en verwijder daarna de tijdelijke map `v2`.
5. Maak een nieuwe root-Mavenreactor op Java 21, Kotlin en Spring Boot. Pin Java 21 expliciet in de
   lokale bouwinstructies en CI-toolchain; vertrouw niet op de standaard-Java van de machine. Laat
   de build vroeg en duidelijk falen wanneer Maven een andere Java-hoofdversie gebruikt.
6. Pas vanaf het begin het gedeelde-API-/implementatiemodulepatroon uit
   [Maven en Spring Modulith](../platform/maven-en-spring-modulith.md) toe.
7. Maak de ene publieke module `product-factory-api` en leg daarin per capabilitypackage de
   volledige command-, query-, DTO-, status- en enumcontracten vast. Voeg nog geen functionele
   implementaties van toekomstige capabilities toe.
8. Maak één main-module als enige Spring Boot composition root. De main-module vereist exact één
   implementatie per capability die in die release al geactiveerd is; een capabilitycontract zonder
   geactiveerde implementatie is in een tussenstap toegestaan.
9. Maak een nieuwe lege Flutter-webapp of een gelijkwaardig nieuw frontendproject; kopieer geen
   oude widgets of domeinschermen.
10. Voeg een eenvoudige backendroute en een leeg maar herkenbaar frontendscherm toe.
11. Schakel de oude automatische imagepromotie en deployment uit voordat de eerste opschoningscommit
    wordt gepusht. Laat bestaande OpenShift-resources ongemoeid totdat stap 9 ze bewust vervangt.

Gebruik geen `v1`, `v2`, `legacy` of `new` in de definitieve modulenamen.

**Verificatie:** Maven bouwt de nieuwe reactor, de frontend analyseert en bouwt, er bestaan geen
oude domeinpackages of migraties meer en alle behouden documenten zijn bereikbaar.

### Stap 2 — Bouw configuratie en secretbeheer opnieuw

1. Implementeer en test de configuratieprioriteit:
   defaults, lokale overrides, lokale secrets en daarna procesenvironment.
2. Definieer één actuele lijst met geheime en niet-geheime instellingen.
3. Houd niet-geheime defaults in `properties.default.env`.
4. Houd lokale secretwaarden uitsluitend in `secrets.env`.
5. Actualiseer `secrets.env.example` met toelichting en veilige voorbeelden, nooit echte waarden.
6. Pas `deploy/seal-secrets.sh` aan:
   - gesloten lijst verplichte secretkeys;
   - fail-closed validatie op ontbrekende waarden;
   - tijdelijke bestanden via `mktemp` en rechten `0600`;
   - cleanup via trap;
   - alleen het Sealed Secret als output.
7. Gebruik afzonderlijke waarden voor lokaal, acceptatie en productie.
8. Laat productie bij ontbrekende auth- of databaseconfiguratie stoppen in plaats van onveilig
   terugvallen.

Begin minimaal met:

- database-URL, gebruiker en wachtwoord;
- Google client-id;
- toegestane stakeholder-e-mailadressen;
- sessieondertekeningssleutel;
- omgeving en publieke URLs.

AI-worker- en Software Factory-secrets worden pas actief wanneer die modules worden gebouwd, maar
mogen alvast als lege, niet-verplichte placeholders gedocumenteerd worden.

Migreer bestaande v1-instellingen doelbewust volgens deze tabel. Een bestaand veld behouden betekent
niet dat zijn waarde in logs, documentatie, een patch of Git mag verschijnen.

| Bestaande instelling | Actie | Reden |
|---|---|---|
| `PF_GOOGLE_CLIENT_ID` | Hergebruiken als de ingestelde productiehostname en Google-configuratie nog geldig zijn | Dit is de OAuth-clientidentiteit, geen gebruikers- of sessietoken |
| `PF_ADMIN_EMAILS` | Hergebruiken en naar de nieuwe gesloten stakeholder-allowlist migreren | Dezelfde stakeholder houdt toegang tot alle producten |
| `PF_DASHBOARD_REMEMBER_SECRET` | Niet hergebruiken; genereer een nieuwe sterke `PF_SESSION_SIGNING_SECRET` | Alle v1-sessies moeten bij de vervanging ongeldig worden |
| `PF_DB_PASSWORD` | Niet hergebruiken; genereer een nieuw wachtwoord voor gebruiker en database `productfactory_v2` | De nieuwe productiedatabase blijft technisch geïsoleerd van v1 |
| `PF_SOFTWARE_FACTORY_TOKEN` | In `secrets.env` behouden maar nog niet activeren | Deze technische fundering bevat nog geen dispatcher |
| `PF_AGENT_WORKER_TOKEN` | In `secrets.env` behouden maar nog niet activeren | Deze technische fundering bevat nog geen AI-uitvoering |
| `PF_WORKSPACE_GITHUB_TOKEN` | Na een repositorybrede gebruikscontrole uit het actuele secretcontract en `secrets.env` verwijderen | v2 heeft geen workspace en leest alleen publieke Git-repositories |

Google ID-tokens zijn kortlevende loginbewijzen en worden bij iedere login opnieuw verkregen. Neem
geen bestaand Google ID-token, Product Factory-bearertoken of andere v1-gebruikerssessie over. Andere
in v1 aangetroffen secrets worden niet automatisch hergebruikt: behoud ze tijdelijk buiten het
actieve contract en beoordeel ze pas wanneer de bijbehorende capability wordt geïmplementeerd.

**Verificatie:** configuratieprioriteit heeft tests, productie faalt bij ontbrekende verplichte
waarden, acceptatie bevat geen productiesecrets en een gerenderd manifest toont geen plaintext
secretwaarden.

### Stap 3 — Richt de databases in

#### Productie

1. Provision een nieuwe PostgreSQL-database `productfactory_v2` met gebruiker
   `productfactory_v2`, een nieuw wachtwoord en een nieuwe PVC.
2. Gebruik binnen deze aparte database het normale schema `public`; kopieer geen v1-schema of
   Flywayhistory.
3. Start de nieuwe Flywayreeks bij `V1`.
4. Maak alleen tabellen die de technische fundering werkelijk gebruikt. Voeg geen lege tabellen
   voor toekomstige processen toe.
5. Configureer een begrensde connectionpool en duidelijke connectie- en querytimeouts.
6. Voeg een database-healthindicator toe.
7. Houd de oude database en PVC tijdens deze fase onaangeraakt. Verwijder ze pas in een afzonderlijke,
   later expliciet goedgekeurde opruimactie.

#### Acceptatie en integratietests

1. Gebruik een nieuwe in-memory database per test of acceptatiestart.
2. Voer dezelfde relevante Flywaymigraties uit.
3. Seed vaste, versieerbare, synthetische data transactioneel en idempotent.
4. Maak seedbotsingen zichtbaar en overschrijf geen onverwachte bestaande records.
5. Voeg daarnaast een PostgreSQL-migratiesmoketest met Testcontainers toe, zodat H2-compatibiliteit
   geen vals vertrouwen geeft over productie.

#### Backup en restore

1. Pas de bestaande backup-CronJob aan naar de nieuwe database en een nieuwe backupdirectory.
2. Schrijf eerst tijdelijk, valideer de dump, maak checksum en rename daarna atomair.
3. Stel een gedocumenteerde bewaartermijn in.
4. Voer vóór afronding een echte restore uit naar een tijdelijke database en controleer inhoud en
   Flywaystatus.

**Verificatie:** een lege productieachtige database migreert vanaf nul, acceptatie start herhaalbaar,
de PostgreSQL-smoketest slaagt en een backup kan werkelijk worden teruggezet.

### Stap 4 — Implementeer authenticatie end-to-end

#### Backend

1. Ontvang een Google ID-token via `POST /api/auth/google`.
2. Valideer handtekening via de officiële Google JWK-set, issuer, audience, verloopdatum en
   `email_verified`.
3. Controleer het e-mailadres tegen een expliciete, gesloten allowlist.
4. Maak na geldige login een nieuwe, begrensde Product Factory-sessie met de nieuwe
   sessieondertekeningssleutel en een nieuwe cookienaam. Accepteer geen v1-sessievorm of v1-token.
5. Bewaar de sessie bij voorkeur in een `Secure`, `HttpOnly`, `SameSite` cookie. Sta de lokale
   niet-secure variant alleen in het expliciete lokale profiel toe.
6. Beveilig alle muterende en gegevensroutes standaard. Alleen login-, logout-, health- en beperkte
   versie-informatie zijn publiek.
7. Controleer bij cookieauthenticatie toegestane origins en bescherm mutaties tegen CSRF.
8. Log geen Google-token, sessietoken of volledig persoonlijk profiel.
9. Implementeer logout, sessieverloop en een uniforme `401`/`403`-response.

#### Frontend

1. Toon Google-login in productie.
2. Wissel het Google-token via de backend om voor de eigen sessie.
3. Bepaal de sessiestatus voordat beschermde schermen worden getoond.
4. Handel verlopen sessies en logout begrijpelijk af.
5. Laat de centrale API-client credentials en fouten consistent verwerken.
6. Zet authenticatie in acceptatie expliciet uit en toon daar op iedere pagina de banner
   **Acceptatie — synthetische tijdelijke data — authenticatie uit**.

**Verificatie:** tests dekken geldige login, verkeerde audience, verlopen token, niet-geverifieerde
e-mail, niet-toegestane e-mail, verlopen sessie, logout, CSRF/origin en productie die niet met
uitgeschakelde auth kan starten.

### Stap 5 — Bouw de minimale frontendfundering

Maak een nieuwe frontend met alleen:

- login en logout;
- applicatieschil en navigatie;
- een lege productpagina;
- een technisch informatie-/beheerscherm;
- centrale API-client;
- consistente loading-, empty- en errorstates;
- zichtbare omgevingsaanduiding;
- begrijpelijke foutmeldingen.

De frontend wordt vanaf nul ontworpen. Neem geen v1-widgets, schermindelingen of specifieke
presentatie- en testpatronen over. Voor deze eerste technische versie gelden alleen deze regels:

1. Bepaal de sessiestatus voordat een beschermd scherm wordt getoond.
2. Loading, fout en werkelijk lege data zijn verschillende presentatietoestanden.
3. UI-teksten zijn Nederlands; technische identifiers en ruwe JSON staan niet primair in beeld.
4. Toon omgeving en frontend- en backendversie op het beheerscherm.
5. Toon in acceptatie op iedere pagina de afgesproken banner.
6. Test de nieuw ontworpen schermlogica en interacties zonder een v1-testsuite na te bouwen.
7. De kern blijft bruikbaar op een viewport van 320 CSS-pixels en bij 200% tekstvergroting. Op
   bredere schermen gebruikt de applicatieschil de beschikbare schermruimte in plaats van de hele
   interface in een smalle mobiele kolom te houden.

**Verificatie:** frontendanalyse, gerichte tests en de releasebuild zijn groen zonder echte externe
calls.

### Stap 6 — Voorkom verouderde frontendversies

Implementeer het bewezen cachemodel opnieuw:

1. Bouw Flutter zonder PWA-service worker.
2. Serveer op het oude pad `/flutter_service_worker.js` tijdelijk een no-store kill-switch die oude
   caches verwijdert en de service worker uitschrijft.
3. Geef JavaScript- en CSS-assets een inhoudshash in de bestandsnaam.
4. Serveer gehashte assets met een lange immutable cacheheader.
5. Serveer `index.html`, bootstrapbestanden, manifest, serviceworkerpad en versiegegevens met
   `no-cache` of `no-store`, passend bij hun functie.
6. Behoud SPA-fallback zonder vaste bestanden abusievelijk als `index.html` te cachen.
7. Voeg passende securityheaders toe, waaronder minimaal content-type-, frame- en referrerbeleid;
   stem CSP expliciet af op Google-login en de eigen API.
8. Test twee opeenvolgende builds met verschillende inhoud: een browser die eerst build A bezocht
   moet zonder cachelegen build B kunnen laden.

**Verificatie:** Nginxconfiguratietest, containerbuild en echte browsertest bewijzen headers,
serviceworkeropruiming en het laden van de nieuwste build.

### Stap 7 — Voeg versie- en omgevingsidentiteit toe

Leg per build vast:

- `applicationVersion`, aanvankelijk `0.1.0`;
- volledige Git-revisie;
- UTC-buildtijd;
- omgeving `local`, `acceptance` of `production`;
- frontend- en backendbuildidentiteit;
- later het actieve implementatiemanifest.

1. Geef de backend een beperkte publieke `/api/version`-route.
2. Genereer voor de frontend versie-informatie tijdens de imagebuild; lees geen Gitrepository in de
   draaiende container.
3. Valideer buildwaarden gesloten en toon ontbrekende of ongeldige waarden als `Onbekend`.
4. Toon in Beheer frontendversie, backendversie, commit, buildtijd en omgeving.
5. Controleer periodiek een niet-gecachete versiebron. Toon bij verschil een melding
   **Nieuwe versie beschikbaar — vernieuwen**; voorkom reloadloops.
6. Toon een duidelijke fout als frontend en backend aantoonbaar incompatibele API-versies gebruiken.

**Verificatie:** tests dekken geldige en ongeldige buildmetadata, er worden geen secrets of
persoonsgegevens getoond en een gesimuleerde nieuwere build levert precies één vernieuwmelding.

### Stap 8 — Maak Product Factory Testbed voor acceptatie

Volg [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md).

1. Start acceptatie met in-memory database en een vaste synthetische catalogus.
2. Schakel automatische schedules standaard uit.
3. Schakel authenticatie expliciet uit en toon de acceptatiebanner overal.
4. Maak alleen het Testbed-raamwerk, de Test Control API, fixturecontributorgrens en veilige
   omgevingsblokkades. Reserveer de al bestaande publieke contracten voor de server-side
   AI-mockexecutor en `MockSoftwareFactory`, maar implementeer hun gedrag pas in respectievelijk
   stap 4 en stap 8.
5. Laat Testbed nooit rechtstreeks in moduletabellen schrijven.
6. Maak reset en scenariokeuze alleen in acceptatie beschikbaar; productie registreert deze
   endpoints en beans niet.
7. Blokkeer uitgaande mutaties naar echte AI- of Software Factory-services vanuit acceptatie.
8. Publieke Gitrepositories mogen later read-only worden gebruikt.
9. Voeg alleen funderingsscenario's toe voor reset, vaste data, omgevingisolatie en verboden echte
   uitgaande mutaties. Functionele AI- en Software Factory-scenario's volgen bij hun capability.

In deze technische release mogen de domeinscenario's nog minimaal zijn. De Testbed-infrastructuur,
omgevingisolatie en productieblokkades moeten wel volledig bestaan.

**Verificatie:** acceptatie kan worden gereset, bevat herkenbare testdata, doet geen echte externe
mutaties en productie weigert mock-, reset- en seedfunctionaliteit.

### Stap 9 — Bouw containers en OpenShiftdeployments

1. Maak multi-stage Dockerfiles voor backend en frontend.
2. Pin basistoolchains op gecontroleerde versies of digests.
3. Draai containers als niet-root en drop onnodige Linux-capabilities.
4. Voeg startup-, readiness- en livenessprobes toe met verschillende betekenissen.
5. Voeg CPU- en geheugenrequests en -limieten toe.
6. Ondersteun graceful shutdown en een bruikbare termination grace period.
7. Maak een Kustomize-base en uitsluitend de eerste overlays `acceptance` en `production`.
8. Productie gebruikt de nieuwe PostgreSQL-PVC en Sealed Secrets.
9. Acceptatie gebruikt in-memory opslag en vaste niet-productieconfiguratie.
10. Behoud de bestaande gebruikers- en API-hostnamen waar mogelijk.
11. Verwijder de oude workspace-initcontainer en oude publieke runtime-route als zij niet meer nodig
    zijn.
12. Een PR-previewoverlay is geen onderdeel van release `0.1.0`; voeg die pas later gericht toe.
13. Deploy eerst handmatig naar acceptatie en voer daar de rooktests uit. Promoveer daarna bewust
    exact dezelfde image-digests handmatig naar productie. Activeer in deze stap nog geen
    automatische deployment vanaf `main`.
14. Laat de oude v1-database en database-PVC ook na een geslaagde productiepromotie bestaan. Alleen
    de nieuwe applicatie en routes worden actief; het verwijderen van oude data is geen onderdeel
    van deze stap.

**Verificatie:** `kubectl kustomize` rendert beide overlays, policychecks vinden geen plaintext
secrets of rootcontainer, probes worden gezond en beide omgevingen tonen de juiste identiteit.

### Stap 10 — Automatiseer CI/CD na de eerste deployment

1. Houd GitHub Actions-permissies minimaal.
2. Gebruik concurrencygroepen en annuleer verouderde verificatieruns.
3. Houd de workflow eenvoudig en maak in de eindstatus duidelijk welke verplichte controles zijn
   uitgevoerd en geslaagd.
4. Draai minimaal:
   - volledige Maven `verify`;
   - Spring Modulith-grenstests;
   - Detekt;
   - frontendanalyse en -tests;
   - frontend- en backendreleasebuild;
   - PostgreSQL-migratiesmoketest;
   - containerbuilds;
   - Kustomize-rendercontrole.
5. Publiceer images onder `sha-<volledige-commit>` en eventueel aanvullend een mutable gemakstag;
   deploy nooit uitsluitend op de mutable tag.
6. Deploy een geslaagde `main` eerst naar acceptatie, voer rooktests uit en promoveer daarna exact
   dezelfde image-digests naar productie.
7. Zorg dat automatische GitOps-imagepincommits niet een oneindige workflowlus veroorzaken.
8. Maak zichtbaar welke functionele commit een deploymentcommit promoveert.
9. Controleer na rollout concrete podimages, routes en health.

Activeer deze automatische deploymentflow pas nadat de handmatige deployments en rooktests uit
stap 9 zijn geslaagd. Vanaf dat moment doorloopt een geslaagde wijziging op `main` deze flow.

Functionele onvolledigheid blokkeert productie niet. Een mislukte build, test, authenticatiecheck,
securitycheck of deploymentrooktest blokkeert productie wel.

**Verificatie:** een gewone wijziging op `main` bouwt één set immutable images, zet die eerst op
acceptatie en daarna op productie, en beide omgevingen rapporteren exact dezelfde bronrevisie.

### Stap 11 — Voeg operationele basisvoorzieningen toe

1. Gebruik gestructureerde logging zonder secrets, tokens, prompts of volledige gevoelige payloads.
2. Geef ieder inkomend verzoek en ieder later asynchroon werk een correlation-id.
3. Gebruik `Instant` en UTC in backend en database; lokaliseer alleen in de gebruikersweergave.
4. Publiceer health, info en begrensde metrics voor intern gebruik.
5. Meet minimaal verzoekduur, fouten, databasepool, authenticatiefouten en deploymentidentiteit.
6. Geef externe HTTP-calls expliciete connectie-, request- en totale timeouts.
7. Gebruik consistente, veilige foutresponses met een traceerbare foutcode.
8. Documenteer starten, stoppen, configureren, secrets roteren, deployen, backup en restore.
9. Voeg een productie-rooktest toe die geen gegevens muteert.

**Verificatie:** een fout is via correlation-id terug te vinden, gevoelige waarden ontbreken in
logs, health onderscheidt startup/readiness/liveness en het runbook is door een nieuwe agent zonder
chatcontext uitvoerbaar.

### Stap 12 — Eindcontrole en opruiming

1. Zoek repositorybreed naar oude packages, modulenamen, workspaceverwijzingen, v1-agentrollen,
   oude databaseobjecten en oude URLs.
2. Zoek buiten de technisch toegestane database-/PVC-naam naar resterende teksten `v1`, `v2`,
   `legacy`, `shadow` en `workspace` en beoordeel iedere treffer.
3. Controleer dat geen oud document meer normatief of bereikbaar vanuit de nieuwe documentatie is.
4. Controleer dat `secrets.env` nog bestaat, ongewijzigd gitignored is en niet in een diff voorkomt.
5. Draai het volledige lokale en CI-vangnet.
6. Voer acceptatie- en productie-rooktests uit.
7. Controleer een echte login op productie en uitgeschakelde auth plus banner op acceptatie.
8. Voer de tweebuild-cachetest en database-restoretest uit.
9. Controleer concrete images, Git-revisies, Flywayversies, routes en health.
10. Werk dit document bij met feitelijke afwijkingen en bewijs, maar zet implementatiedetails niet in
    het overzichtsdocument wanneer zij in een gericht technisch document horen.

## Aanbevolen commitgrenzen

Gebruik kleine, samenhangende en steeds bouwbare commits. Een logische indeling is:

1. `docs: add executable technical foundation plan`
2. `chore: replace v1 repository structure`
3. `feat: add configuration and database foundation`
4. `feat: add secure authentication`
5. `feat: add frontend shell caching and build identity`
6. `test: add acceptance testbed foundation`
7. `deploy: add acceptance and production environments`
8. `ci: verify build and promote immutable images`
9. `ops: add health metrics backup and restore runbook`

Een uitvoerende agent mag grenzen combineren wanneer dat nodig is om `main` bouwbaar en productie
veilig te houden. Push nooit een commit die de oude beveiliging verwijdert terwijl de vervangende
beveiliging nog ontbreekt, tenzij de publieke route op infrastructuurniveau aantoonbaar afgesloten
blijft.

## Definitie van klaar voor release 0.1.0

De technische fundering is pas klaar wanneer al het volgende aantoonbaar waar is:

- v1-code, v1-migraties en v1-documentatie zijn uit de actuele branch verwijderd;
- `secrets.env` is behouden, gitignored en niet gelekt;
- de nieuwe applicatie gebruikt nergens functionele `v2`-namen;
- alle nieuwe documenten staan rechtstreeks onder `docs`;
- Maven-, Modulith-, Kotlin- en frontendchecks zijn groen;
- backend- en frontendcontainers zijn immutable gebouwd en draaien als niet-root;
- productie gebruikt de nieuwe PostgreSQL-database en een nieuwe credential;
- acceptatie gebruikt uitsluitend resetbare synthetische in-memory data;
- productieauthenticatie werkt en faalt gesloten;
- acceptatieauthenticatie staat zichtbaar en uitsluitend daar uit;
- frontendcachegedrag toont na deployment zonder handmatig cachelegen de nieuwste versie;
- de kern van de frontend werkt op 320 CSS-pixels en bij 200% tekstvergroting en gebruikt op brede
  schermen de beschikbare ruimte;
- frontend en backend tonen omgeving, applicatieversie, commit en buildtijd;
- acceptatie en productie draaien exact de bedoelde image-digests;
- healthchecks, logs, metrics, correlation-ids en timeouts werken;
- een productiebackup is gevalideerd en succesvol teruggezet in een tijdelijke database;
- productie registreert geen Testbed-, mock-, reset- of seedvoorzieningen;
- er bestaan nog geen functionele procesimplementaties of half afgemaakte v1-compatibiliteitslagen;
- een volgende agent kan vanuit de nieuwe documentatie de eerste functionele module bouwen zonder
  oude documentatie of chatcontext nodig te hebben.

## Vervolg

Na deze technische release volgt de incrementele MVP-route uit het
[overzicht van de stappenplannen](README.md). Deze stap implementeert geen functionele capability
vooruit. Iedere volgende stap wordt afzonderlijk afgerond, getest en naar acceptatie en productie
gedeployed.
