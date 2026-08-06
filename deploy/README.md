# OpenShift-deployment

De Kustomize-basis maakt een eigen namespace, PostgreSQL-database, runtime en twee dashboard-
deployments. Maak vóór synchronisatie het secret `product-factory-secrets` uit een lokale,
gitignored `deploy/secrets-cluster.env`. Het workspace-token is een fijnmazig GitHub-token dat
alleen `product-factory-workspace` als repositoryresource heeft.

Cloudflare publiceert `product-factory.vdzonsoftware.nl` naar
`http://dashboard-frontend.product-factory.svc.cluster.local:8080` en
`product-factory-api.vdzonsoftware.nl` naar
`http://dashboard-backend.product-factory.svc.cluster.local:8081`.
