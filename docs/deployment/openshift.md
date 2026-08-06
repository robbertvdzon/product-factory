# OpenShift en secrets

De deployment gebruikt namespace `product-factory`, een eigen PostgreSQL-PVC en losse deployments
voor runtime, dashboard-backend en dashboard-frontend. De agentimage is beschikbaar voor latere
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
