# Deployment en operatie

Dit document beschrijft hoe de nieuwe Product Factory wordt gebouwd, gedeployed en beheerd. Het
gaat alleen over de technische omgevingen en niet over de interne werking van de productprocessen.
Configuratie, databasekeuzes en authenticatie staan in [Technische basis](technische-basis.md).

Functioneel onvolledige versies mogen naar productie. Een versie die niet bouwt, niet veilig start,
authenticatie openzet, secrets lekt of de productiedatabase niet veilig kan gebruiken, wordt niet
gedeployed.

## Omgevingen

De eerste implementatie ondersteunt drie omgevingen:

| Omgeving | Database | Authenticatie | Externe diensten | Doel |
|---|---|---|---|---|
| lokaal | lokale PostgreSQL of expliciet lokaal profiel | standaard uit | lokaal of mocks | ontwikkelen en debuggen |
| acceptatie | resetbare in-memory database | uit, met vaste banner | stateful Testbed-mocks | integratie- en UI-tests |
| productie | duurzame PostgreSQL-database | verplichte Google-login | echte toegestane diensten | de echte Product Factory |

Een PR-previewomgeving hoort niet bij de eerste technische release. Zij kan later worden toegevoegd
als daar een concrete behoefte voor bestaat.

Acceptatie en productie hebben gescheiden configuratie, secrets, routes en data. Acceptatie kan
geen productiecredentials lezen en kan geen echte schrijvende AI- of Software Factory-dienst
aanroepen. De precieze Testbedgrens staat in
[Integratie- en acceptatietesten](integratie-en-acceptatietesten.md).

## Buildstraat

Een wijziging op `main` doorloopt een eenvoudige, betrouwbare buildstraat:

1. bouw de volledige Maven-reactor en voer de backendtests uit;
2. controleer de Spring Modulith-grenzen binnen implementatiemodules;
3. voer Kotlinanalyse uit;
4. analyseer en test de nieuwe frontend;
5. maak een releasebuild van backend en frontend;
6. bouw de echte backend- en frontendcontainerimages;
7. render de Kustomize-overlays voor acceptatie en productie;
8. publiceer de images met een immutable Git-SHA-tag of digest;
9. deploy eerst naar acceptatie en voer een korte rooktest uit;
10. promoveer daarna dezelfde image-inhoud naar productie.

Dit is gewone CI/CD. Het zware v1-systeem met apart worktreebewijs, door agents gemeten
revisioncontracten en uitgebreide Factory-attestatie wordt niet overgenomen.

De Java-, Maven-, Flutter- en Dartversies in CI moeten compatibel zijn met de lokaal vastgelegde
toolchain, Dockerbuilds en lockfiles. Een frontendwijziging is pas bouwbaar als ook de echte
productieachtige frontendimage kan worden gemaakt.

GitHub Actions gebruikt minimale rechten en voorkomt dat twee verouderde builds onnodig tegelijk
doorlopen. Een automatische GitOps-commit mag geen oneindige workflowlus veroorzaken.

## Images en buildidentiteit

Backend en frontend worden als afzonderlijke, multi-stage images gebouwd. Runtimecontainers:

- draaien niet als root;
- gebruiken een vaste gebruiker;
- krijgen geen onnodige Linux-capabilities;
- staan privilege escalation niet toe;
- bevatten alleen runtimebenodigdheden;
- gebruiken gecontroleerde basisimageversies of digests.

Iedere image heeft minimaal:

- `applicationVersion`;
- de volledige Git-revisie;
- UTC-buildtijd;
- omgeving of imagevariant;
- naam van de component, bijvoorbeeld frontend of backend.

Een deployment verwijst naar de immutable SHA-tag of digest en nooit uitsluitend naar een mutable
tag zoals `main`. Na een rollout wordt behalve de GitOps-/Argo CD-status ook de concrete image van
de draaiende pod gecontroleerd. Een `Synced` melding alleen bewijst niet dat de bedoelde build al
actief is.

De frontend- en backendidentiteit worden via het beheerscherm zichtbaar. Cache- en
vernieuwingsgedrag staat in [Frontend](frontend.md).

## OpenShift en Kustomize

De repository bevat:

- één Kustomize-base voor gedeelde objecten;
- een acceptance-overlay;
- een production-overlay.

De base bevat alleen gedeelde structuur. Omgevingsspecifieke hostnamen, databasekeuzes,
authenticatie en Testbedconfiguratie staan in de juiste overlay. Gerenderde manifests bevatten geen
plaintext productiesecrets.

Productie bevat minimaal:

- één backenddeployment en service;
- één frontenddeployment en service;
- publieke routes voor frontend en API;
- een PostgreSQL-deployment of ondersteunde databasevoorziening;
- een afzonderlijke persistente volumeclaim;
- het door Sealed Secrets geleverde secret;
- de databasebackup-CronJob.

Acceptatie bevat geen duurzame PostgreSQL-deployment. De backend gebruikt daar de in-memory
database en Testbedconfiguratie. Routes en visuele omgevingsidentiteit zijn duidelijk van productie
te onderscheiden.

De oude workspace-initcontainer en een afzonderlijke publieke runtime-route worden niet
overgenomen. De nieuwe backend is de ene Spring Boot-applicatie en de frontend communiceert alleen
met haar publieke API.

## Probes, resources en stoppen

Backend, frontend en database krijgen passende controles met verschillende betekenissen:

- een startupcontrole bepaalt of de component volledig kon initialiseren;
- een readinesscontrole bepaalt of nieuw verkeer veilig ontvangen kan worden;
- een livenesscontrole detecteert een werkelijk vastgelopen proces zonder een trage startup af te
  straffen.

Containers hebben realistische CPU- en geheugenrequests en begrensde limieten. De backend
ondersteunt graceful shutdown en krijgt voldoende termination grace time om lopende HTTP-requests
af te ronden. Een rollout start pas nieuw verkeer naar een pod nadat readiness groen is.

De PostgreSQL-workload gebruikt bij een enkel `ReadWriteOnce`-volume een strategie die voorkomt dat
twee pods gelijktijdig dezelfde data openen.

## ConfigMaps, secrets en rotatie

Niet-geheime omgevingsconfiguratie staat in ConfigMaps of expliciete buildconfiguratie. Secrets
komen uitsluitend uit het omgevingsspecifieke Secret. De bron- en sealregels staan in
[Technische basis](technische-basis.md).

Bij secretrotatie:

1. wijzig de lokale geheime bron zonder de waarde te loggen;
2. genereer en commit het nieuwe Sealed Secret;
3. laat GitOps synchroniseren;
4. herstart gecontroleerd alleen de deployments die deze waarde lezen;
5. controleer login, databaseverbinding of externe koppeling waarvoor de secret geldt;
6. trek waar relevant de oude credential in.

Een nieuwe sessieondertekeningssleutel mag bestaande dashboardsessies bewust ongeldig maken. Dat
wordt bij de rollout vermeld.

## Databasebackup

De productiedatabase krijgt een periodieke custom-format PostgreSQL-backup. De backupjob:

1. schrijft eerst naar een tijdelijk bestand in de bedoelde backupdirectory;
2. beëindigt de run bij een fout van `pg_dump`;
3. valideert de dump met `pg_restore --list`;
4. maakt een SHA-256-checksum;
5. maakt dump en checksum pas daarna definitief zichtbaar;
6. verwijdert backups ouder dan de afgesproken bewaartermijn.

Een mislukte of onvolledige tijdelijke dump wordt niet als geldige backup gepresenteerd. De job
draait als niet-rootgebruiker, heeft begrensde resources en toegang tot precies de benodigde
databasecredential en backupopslag.

## Restoreprocedure

Een backup geldt pas als bruikbaar nadat herstel daadwerkelijk is getest. Een restoretest:

- gebruikt een nieuwe tijdelijke database;
- controleert eerst de checksum;
- voert `pg_restore` uit zonder de actieve productiedatabase te raken;
- controleert dat Flyway-history en representatieve rijen leesbaar zijn;
- legt datum, gebruikte backup en uitkomst vast zonder data of credentials te kopiëren;
- ruimt de tijdelijke database na controle op.

De restoreprocedure beschrijft daarnaast hoe productie bij echt herstel wordt stilgezet, hoe de
doeldatabase exact wordt vastgesteld en hoe na herstel backendhealth en Flywaystatus worden
gecontroleerd. Een destructief productieherstel wordt nooit automatisch door applicatiestart
uitgevoerd.

## Health en operationeel inzicht

De backend biedt interne health-, info- en begrensde metricsinformatie. Minimaal zichtbaar zijn:

- applicatie- en buildidentiteit;
- startup- en readinessstatus;
- databasebereikbaarheid en poolstatus;
- aantallen en duur van HTTP-fouten;
- authenticatiefouten zonder tokens of persoonsgegevens;
- JVM- en procesgezondheid.

Logs bevatten geen secrets, sessietokens of volledige gevoelige payloads. Foutresponses geven een
veilige foutcode waarmee een technisch gebruiker de bijbehorende logregel kan vinden. Backend en
database slaan tijden in UTC op; alleen de frontend lokaliseert tijden voor de gebruiker.

## Deploymentcontrole

Na iedere omgevingsrollout wordt minimaal gecontroleerd:

- de route antwoordt;
- frontend en backend rapporteren de bedoelde omgeving en Git-revisie;
- de concrete podimages hebben de bedoelde SHA of digest;
- startup en readiness zijn gezond;
- productieauthenticatie is actief;
- acceptatieauthenticatie is uit en de banner zichtbaar;
- acceptatie gebruikt in-memory data en productie PostgreSQL;
- er staan geen plaintext secrets in gerenderde manifests;
- een frontenddeployment toont zonder handmatig cachelegen de nieuwe build.

## Gerelateerde documenten

- [Overzicht](overzicht.md)
- [Technische basis](technische-basis.md)
- [Implementatieplan voor de nieuwe technische fundering](implementatieplan-technische-fundering.md)
- [Frontend](frontend.md)
- [Maven en Spring Modulith](maven-en-spring-modulith.md)
- [Integratie- en acceptatietesten](integratie-en-acceptatietesten.md)
