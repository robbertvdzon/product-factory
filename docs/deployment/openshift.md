# OpenShift en secrets

De deployment gebruikt namespace `product-factory` en losse deployments voor PostgreSQL, runtime,
dashboard-backend en dashboard-frontend. Net als de HKH-fase-1-basis gebruikt PostgreSQL voorlopig
een `emptyDir`: de lokale SNO `local-path`-provisioner relabelt PVC's niet voor OpenShift SELinux.
Kies vóór productie een SELinux-compatibele StorageClass of beheerde PostgreSQL-dienst; een
podvervanging wist tot die tijd de dashboarddata. De agentimage is beschikbaar voor latere
run-jobs, maar fase 2 plant nog geen autonome runs.

Maak `product-factory-secrets` buiten Git. Het GitHub-token moet een fine-grained token zijn met
alleen Contents- en Pull request-schrijfrecht op `product-factory-workspace`. Hetzelfde token mag
niet als credential voor `hkh`, `hkh-autopilot` of `product-factory` worden geconfigureerd. De
runtime controleert bovendien de exacte workspace-repository vóór ieder Git-commando.

Voor productie zijn `PF_DASHBOARD_AUTH_REQUIRED=true`, een Google webclient in
`PF_GOOGLE_CLIENT_ID` en een expliciete `PF_ADMIN_EMAILS`-allowlist verplicht. Voeg de dashboard-
origin en callbackconfiguratie toe aan dezelfde Google OAuth-client als de andere beheerapps.

De twee publieke Cloudflare-routes staan in `deploy/README.md`. De interne runtime-route is alleen
voor operationele health/deploycontrole en hoort niet als gebruikersendpoint te worden gedeeld.
