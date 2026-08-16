# OpenShift-deployment

De Kustomize-basis maakt een eigen namespace, PostgreSQL-database, runtime en twee dashboard-
deployments. Het secret `product-factory-secrets` wordt door de Sealed Secrets-controller gemaakt
uit het commitbare `deploy/base/sealed-secret-product-factory.yaml`.

PostgreSQL gebruikt een 5Gi `local-path`-PVC op de SSD. De deploymentstrategie is `Recreate`, zodat
nooit twee databasepods tegelijk dezelfde `ReadWriteOnce`-data openen. Om 02:45
(Europe/Amsterdam) maakt de `postgres-backup` CronJob een custom-format dump, valideert die met
`pg_restore --list` en schrijft dump plus SHA-256-checksum naar
`/var/mnt/external-hdd/postgres-backups/product-factory`. Bestanden ouder dan dertig dagen worden
automatisch verwijderd.

Het gitignored rootbestand `secrets.env` is de enige lokale secretbron voor zowel ontwikkeling als
OpenShift. Het workspace-token daarin is een fine-grained GitHub-token met alleen Contents- en Pull
requests-schrijfrecht op `product-factory-workspace`. Versleutel de vier vereiste waarden met het
gedeelde clustercertificaat uit `robberts-infrastructure`:

```bash
./deploy/seal-secrets.sh
kubectl kustomize deploy/sno-local >/dev/null
```

Met `PF_SEAL_SOURCE=/pad/naar/alternatief.env` kan later bewust een afwijkende clusterbron worden
gebruikt zonder een tweede standaardbestand te introduceren. Commit het gewijzigde SealedSecret,
maar nooit het bronbestand. Een secretwijziging verandert de
podtemplate niet; herstart na de Argo CD-sync de deployments die environmentwaarden gebruiken:

```bash
oc rollout restart deployment/runtime deployment/dashboard-backend -n product-factory
```

`PF_AGENT_WORKER_TOKEN` beveiligt daarnaast de uitgaande Mac-agentworkerverbinding. Na het
toevoegen of roteren van deze waarde moet alleen `dashboard-backend` worden herstart; de lokale
LaunchAgent leest bij zijn volgende start hetzelfde `secrets.env`.

Cloudflare publiceert `product-factory.vdzonsoftware.nl` naar
`http://dashboard-frontend.product-factory.svc.cluster.local:8080` en
`product-factory-api.vdzonsoftware.nl` naar
`http://dashboard-backend.product-factory.svc.cluster.local:8081`.

## Buildgebonden dashboardidentiteit

De imageworkflow geeft de frontend bij het bouwen drie niet-gevoelige waarden mee:

- `BUILD_ENVIRONMENT`: `production` voor de productie-image, `acceptance` voor de afzonderlijke
  acceptance-image en `preview` voor een PR-preview;
- `SOURCE_REVISION`: de volledige uitgecheckte broncommit; bij een PR is dit de headcommit en niet
  de synthetische mergecommit;
- `DEPLOYED_AT`: één UTC-tijd in ISO-8601-formaat, aan het begin van de workflow vastgelegd en
  hergebruikt voor alle frontendvarianten uit die run.

De Dockerfile compileert deze waarden met `--dart-define` in de Flutter-webbundle. Het dashboard
toont de genormaliseerde waarden onder `Beheer` > `Omgevingsidentiteit`; terminale bewijsregels tonen
dezelfde omgeving en de eerste twaalf tekens van dezelfde revisie, maar niet de uitroltijd. De tijd
is dus vaste metadata van de gebouwde frontendvariant en geen live podstarttijd. De identiteit zegt
welke frontendbuild wordt geserveerd en doet geen uitspraak dat iedere backendcomponent dezelfde
revisie draait.

De frontend accepteert alleen de drie genoemde omgevingscodes, een volledige hexadecimale
bronrevisie en een geldige ISO-8601-tijd met tijdzone. Ieder ontbrekend of ongeldig veld wordt
afzonderlijk `Onbekend`. Lokale builds zonder build-args blijven daardoor bruikbaar. De draaiende
container leest hiervoor geen repositorybestand of secret en doet geen extra API-request.

## PR-previewdatabase herstellen

Een PR-preview gebruikt een wegwerpbare PostgreSQL-database in zijn eigen namespace. Omdat die
database meerdere branchrevisies kan overleven, kan een merge van `main` een Flyway-validatiefout
geven wanneer twee nog niet gemergde stories hetzelfde migratienummer gebruikten. De runtime
probeert migratie altijd eerst normaal en voert alleen na een echte `FlywayValidateException` een
schone herbouw uit wanneer alle volgende controles slagen:

- de gevalideerde synthetische dataset is exact `PR_PREVIEW`;
- de JDBC-URL van Flyways daadwerkelijke datasource is bytegelijk aan de vooraf gevalideerde
  in-namespace `PF_DB_URL`;
- zowel Flyways defaultschema als cleanschema is uitsluitend `public`.

Na `clean` voert Flyway alle migraties opnieuw uit en seedt de runtime de vaste PR-previewdata.
Ontbrekende of afwijkende targetmetadata stopt fail-closed met de oorspronkelijke validatiefout.
Productie en standing acceptatie komen nooit in dit herstelpad en hun schema wordt niet automatisch
opgeschoond.

## Standing acceptatieomgeving

`deploy/overlays/acceptance` is een blijvende, van productie en PR-previews gescheiden omgeving in
namespace `product-factory-acceptance`. De gebruikers- en API-routes zijn respectievelijk
`https://product-factory-acceptance.vdzonsoftware.nl` en
`https://product-factory-api-acceptance.vdzonsoftware.nl`.

De acceptance-overlay selecteert de vaste synthetische dataset met `PF_PREVIEW_ENABLED=true` en
marker `product-factory-acceptance`; `PF_PREVIEW_PR_NUMBER` blijft daar leeg. De runtime accepteert
deze combinatie alleen met de in-namespace PostgreSQL-URL en laadt dan seedversie
`acceptance-product-factory-cycles-v1`. Laat de previewmarker en de acceptance-marker nooit tussen
overlays uitwisselen: de previewmarker selecteert de afzonderlijke bestaande `hkh-autopilot`-seed,
terwijl productie geen synthetische seed activeert.

De overlay houdt autonomie en externe workspacepublicatie uitgeschakeld. De twee synthetische
leveringen zijn al voltooid, bevestigd en geëvalueerd en worden daarom niet naar externe systemen
gestuurd. De frontend gebruikt bovendien de aparte, door CI gepinde image
`sha-<commit>-acceptance`; alleen die build bevat `ACCEPTANCE_DATASET=true` en toont de melding
`Synthetische acceptatiedata`. Een restart is veilig en idempotent. Faalt startup met een melding
over een afwijkende gereserveerde acceptatiefixture, herstel dan niet door rijen te overschrijven:
onderzoek eerst de botsende id/seedversie; de transactie heeft geen gedeeltelijke catalogus
achtergelaten.

## Database verbinden

Er zijn twee volledig gescheiden PostgreSQL-databases: een lokale, lege ontwikkeldatabase via
docker-compose, en de productiedatabase in OpenShift. Beide gebruiken gebruikersnaam en database
`productfactory`; alleen host, poort en wachtwoord verschillen.

### Waar de credentials staan

Alle wachtwoorden staan uitsluitend in het gitignored rootbestand `secrets.env` (nooit in Git, ook
niet versleuteld — zie hierboven). Zoek daarin naar `PF_DB_PASSWORD`. Dat ene wachtwoord geldt voor
zowel de lokale als, na de laatste `./deploy/seal-secrets.sh`-run, de productiedatabase: het
bronbestand ís de bron voor het `SealedSecret` dat productie gebruikt. Staat `secrets.env` er niet
(nog), kopieer dan eerst `secrets.env.example` ernaartoe.

Er is geen andere plek om dit wachtwoord te vinden: `deploy/base/sealed-secret-product-factory.yaml`
is asymmetrisch versleuteld tegen het clustercertificaat en is alléén door de Sealed
Secrets-controller in de cluster zelf te ontsleutelen — ook niet door iemand met leestoegang tot
deze repository.

### Lokaal (ontwikkeldatabase, leeg, geen productiedata)

```bash
./product-factory up     # start docker-compose, inclusief postgres op localhost:5436
psql "postgresql://productfactory:$(grep PF_DB_PASSWORD ../secrets.env | cut -d= -f2)@localhost:5436/productfactory"
```

### Productie (echte cyclus-, story- en leveringsdata)

De `postgres`-Service in OpenShift is `NodePort` op `30432` (naast de gewone `ClusterIP`-toegang die
de runtime intern gebruikt). Een OpenShift `Route` werkt niet voor ruwe Postgres-TCP en er is geen
`LoadBalancer` (geen MetalLB) op deze cluster, dus dit is de LAN-only weg: alleen bereikbaar via het
vaste node-IP `192.168.178.64` binnen het thuisnetwerk, nooit van buitenaf (geen
routerportforwarding, los van de Cloudflare Tunnel voor de webapps). Geen `oc login` of
`oc port-forward` nodig — dit werkt direct vanaf elk toestel op het thuisnetwerk.

```bash
psql "postgresql://productfactory:$(grep PF_DB_PASSWORD secrets.env | cut -d= -f2)@192.168.178.64:30432/productfactory"
```

Relevante tabellen voor productcycli: `shadow_iteration` (cyclusstatus, rol, criticusoordeel),
`shadow_iteration_step` (per-rol agentoutput), `story_candidate` (voorgestelde stories, inclusief
afgewezen), `story_delivery` (levering aan de Software Factory, status, externe storysleutel),
`story_question` en `human_action` (vragen/escalaties tijdens uitvoering). Zie
[functioneel-overzicht.md](../docs/architecture/functioneel-overzicht.md) voor wat deze tabellen
functioneel betekenen.
