# OpenShift en secrets

De deployment gebruikt namespace `product-factory` en losse deployments voor PostgreSQL, runtime,
dashboard-backend en dashboard-frontend. PostgreSQL gebruikt voorlopig een `emptyDir`: de lokale
SNO `local-path`-provisioner relabelt PVC's niet voor OpenShift SELinux.
Kies vóór productie een SELinux-compatibele StorageClass of beheerde PostgreSQL-dienst; een
podvervanging wist tot die tijd de dashboarddata. De agentimage blijft beschikbaar voor tests en
een latere API-keyruntime, maar de abonnementsworker draait op de Mac en niet in OpenShift.

Maak `product-factory-secrets` buiten Git. Het GitHub-token moet een fine-grained token zijn met
alleen Contents- en Pull request-schrijfrecht op `product-factory-workspace`. Hetzelfde token mag
niet als credential voor een productrepository of voor `product-factory` zelf worden
geconfigureerd. De runtime controleert bovendien de exacte workspace-repository vóór ieder
Git-commando.

`PF_AGENT_WORKER_TOKEN` is een afzonderlijk willekeurig token voor de WebSocket-hello en hoort in
dezelfde lokale `secrets.env`/SealedSecret-keten. De dashboardbackend weigert iedere worker zolang
dit token leeg is. Cloudflare routeert WebSocketpad `/agent-worker` via de bestaande
`product-factory-api.vdzonsoftware.nl`-route; er is geen aparte tunnel of publiek Mac-endpoint.

Voor productie zijn `PF_DASHBOARD_AUTH_REQUIRED=true`, een Google webclient in
`PF_GOOGLE_CLIENT_ID` en een expliciete `PF_ADMIN_EMAILS`-allowlist verplicht. Voeg de dashboard-
origin en callbackconfiguratie toe aan dezelfde Google OAuth-client als de andere beheerapps.

De twee publieke Cloudflare-routes staan in `deploy/README.md`. De interne runtime-route is alleen
voor operationele health/deploycontrole en hoort niet als gebruikersendpoint te worden gedeeld.

## Dashboardidentiteit bij uitrol

Productie, standing acceptatie en PR-previews krijgen elk hun eigen omgevingscode, volledige
bronrevisie en één UTC-tijd mee tijdens de frontend-imagebuild. Deze waarden zijn compile-time
metadata in de Flutter-bundle, geen secret of runtime-environmentvariabele. Beheer toont ze
veldgewijs gevalideerd; een gewone podrestart wijzigt de identiteit dus niet. De exacte bron,
betekenis en veilige fallbacks staan in
[deploy/README.md](../../deploy/README.md#buildgebonden-dashboardidentiteit).

## Standing acceptatie

Naast productie bestaat de geïsoleerde overlay `deploy/overlays/acceptance` in namespace
`product-factory-acceptance`. Deze combineert de geverifieerde marker
`product-factory-acceptance`, een in-namespace database en uitgeschakelde autonomie/externe
publicatie met een afzonderlijke frontendimage (`ACCEPTANCE_DATASET=true`). Alleen daar worden de
vaste `product-factory`-acceptatiefixtures en de zichtbare synthetische-datamelding geactiveerd;
productie en PR-previews blijven daarvan vrij. De routes, exacte activatievoorwaarden,
datasetscheiding en botsingsprocedure staan in [deploy/README.md](../../deploy/README.md#standing-acceptatieomgeving).

## PR-previewherstel

Alleen een aantoonbaar wegwerpbare PR-previewdatabase mag na een Flyway-validatiefout automatisch
opnieuw worden opgebouwd. De runtime bindt dit fail-closed aan dataset, daadwerkelijke JDBC-target
en uitsluitend schema `public`; productie en standing acceptatie worden nooit opgeschoond. Zie
[deploy/README.md](../../deploy/README.md#pr-previewdatabase-herstellen) voor de exacte controles.
