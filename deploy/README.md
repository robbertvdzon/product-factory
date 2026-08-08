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

## Rechtstreekse databasetoegang vanaf het thuisnetwerk

De `postgres`-Service is `NodePort` op `30432` (naast de gewone `ClusterIP`-toegang die de runtime
intern gebruikt). Een OpenShift `Route` werkt niet voor ruwe Postgres-TCP en er is geen
`LoadBalancer` (geen MetalLB) op deze cluster, dus dit is de LAN-only weg: alleen bereikbaar via het
vaste node-IP `192.168.178.64` binnen het thuisnetwerk, nooit van buitenaf (geen
routerportforwarding, los van de Cloudflare Tunnel voor de webapps).

```bash
psql "postgresql://productfactory:<wachtwoord>@192.168.178.64:30432/productfactory"
```
