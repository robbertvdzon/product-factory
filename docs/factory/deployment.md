---
default_base_branch: main
branch_prefix: ai/
preview_url_template: "https://product-factory-pr-{pr_num}.vdzonsoftware.nl"
preview_namespace_template: "product-factory-pr-{pr_num}"
preview_db_secret_recipe: |
  echo "jdbc:postgresql://postgres:5432/productfactory"
---

# Deployment

Elke pull request tegen `product-factory` krijgt automatisch een eigen preview-omgeving
via de ArgoCD ApplicationSet `product-factory-previews`
(`robberts-infrastructure/manifests/root-app/apps/product-factory-applicationset.yaml`).
Die genereert per PR-nummer een namespace en drie Routes op basis van `deploy/overlays/preview`:

- Frontend (dashboard): `https://product-factory-pr-{pr_num}.vdzonsoftware.nl`
- Dashboard-backend/API: `https://product-factory-api-pr-{pr_num}.vdzonsoftware.nl`
- Runtime (agent-bridge): `https://product-factory-runtime-pr-{pr_num}.vdzonsoftware.nl`

Testers gebruiken de frontend-URL hierboven om de preview in de browser te bekijken.

De preview-database is een wegwerpbare in-namespace Postgres (`deploy/overlays/preview`)
met een vaste, niet-gevoelige connectiestring — geen echt secret om op te halen, vandaar de
simpele recipe hierboven in plaats van een `oc get secret`-aanroep.
