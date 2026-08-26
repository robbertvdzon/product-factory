# Deployment- en operatierunbook

Product Factory heeft één Kustomize-base en twee overlays: `acceptance` en `production`.
Acceptatie gebruikt uitsluitend H2-geheugenopslag, vaste synthetische fixtures, uitgeschakelde
authenticatie en geblokkeerde externe mutaties. Productie gebruikt de afzonderlijke database en
PVC `productfactory_v2` en vereist Google-authenticatie.

## Bouwen en controleren

Gebruik Java 21 en Flutter 3.44.6. Leg voor één release één volledige Git-revisie en één UTC-tijd
vast en gebruik die voor beide images:

```bash
revision="$(git rev-parse HEAD)"
build_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
docker buildx build --platform linux/amd64 -f product-factory-app/Dockerfile \
  --build-arg SOURCE_REVISION="$revision" --build-arg BUILD_TIME="$build_time" .
docker buildx build --platform linux/amd64 -f product-factory-frontend/Dockerfile \
  --build-arg SOURCE_REVISION="$revision" --build-arg BUILD_TIME="$build_time" .
tools/verify-deployment.sh
```

Images worden gepubliceerd als `sha-<volledige-commit>` en in beide overlays op digest gepind.
Gebruik nooit uitsluitend `main` of een andere mutable tag voor een rollout.

Na een geslaagde `Repository verification` op `main` bouwt `release.yml` automatisch één set
images. De workflow pint en rooktest eerst acceptatie. Alleen daarna pint hij exact dezelfde
digests in productie en wacht hij op de productierooktest. Een nieuwere push naar `main` vervangt
een nog niet gepromoveerde oudere release; een mislukte acceptatierooktest bereikt productie niet.

## Secrets maken en roteren

Het plaintext bronbestand blijft uitsluitend `secrets.env` in de repositoryroot. Het is
gitignored en heeft modus `0600`. Het bestaande Sealed Secrets-proces blijft leidend:

```bash
PF_SEAL_NAMESPACE=product-factory \
PF_SEAL_OUTPUT=deploy/overlays/production/sealed-secret-product-factory.yaml \
deploy/seal-secrets.sh
kubeseal --validate < deploy/overlays/production/sealed-secret-product-factory.yaml
```

Commit alleen het versleutelde SealedSecret. Na rotatie: render, deploy naar productie, wacht op
een gezonde backendrollout en controleer een nieuwe login. Acceptatie krijgt geen productie-
SealedSecret of externe tokens.

## Handmatige fallback

Gebruik deze route alleen als de automatische GitOps-promotie bewust is uitgeschakeld. Render en
controleer eerst beide overlays. Deploy daarna acceptatie, voer de read-only rooktest uit en
promoveer pas daarna exact dezelfde imagedigests naar productie:

```bash
kustomize build deploy/overlays/acceptance | oc apply -f -
oc -n product-factory-acceptance rollout status deployment/product-factory-backend --timeout=5m
oc -n product-factory-acceptance rollout status deployment/product-factory-frontend --timeout=5m
tools/smoke-test.sh acceptance

kustomize build deploy/overlays/production | oc apply -f -
oc -n product-factory rollout status deployment/product-factory-postgres --timeout=5m
oc -n product-factory rollout status deployment/product-factory-backend --timeout=5m
oc -n product-factory rollout status deployment/product-factory-frontend --timeout=5m
tools/smoke-test.sh production
```

De bestaande routes `dashboard-frontend` en `dashboard-backend` worden bewust naar de nieuwe
services omgezet. De oude runtime-route vervalt. De oude v1-database en PVC `postgres-data` blijven
onaangeraakt totdat daar afzonderlijk opdracht voor wordt gegeven.

## Dagelijks beheer en diagnose

- Starten: schaal database, backend en frontend naar één replica, in die volgorde.
- Stoppen: schaal frontend en backend naar nul; stop de database pas nadat lopende backups klaar
  zijn. Verwijder geen PVC.
- Health: gebruik intern `/actuator/health/liveness` voor procesleven en
  `/actuator/health/readiness` voor applicatie plus database.
- Metrics: `/actuator/prometheus` is intern en in productie alleen met een geldige sessie
  bereikbaar. HTTP-duur, fouten, Hikari-pool, loginweigeringen en buildidentiteit zijn aanwezig.
- Logging: stdout bevat gestructureerde JSON. Zoek een fout via responseheader
  `X-Correlation-ID`; tokens, cookies en volledige profielen horen nooit in logs.
- Rollback: pin de laatst gevalideerde digests opnieuw of gebruik gecontroleerd `oc rollout undo`.
  Draai Flyway nooit terug en herstel nooit over de actieve database heen.
- Backup en restore: volg [database/README.md](database/README.md).

De productie-rooktest muteert geen data. De enige handmatige browsercontrole na een release is een
echte Google-login, gevolgd door logout; acceptatie moet zonder login openen en overal de vaste
gele banner tonen.
