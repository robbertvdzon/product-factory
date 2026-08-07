# OpenShift-deployment

De Kustomize-basis maakt een eigen namespace, PostgreSQL-database, runtime en twee dashboard-
deployments. Het secret `product-factory-secrets` wordt door de Sealed Secrets-controller gemaakt
uit het commitbare `deploy/base/sealed-secret-product-factory.yaml`.

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
