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

## Buildgebonden omgevingsidentiteit

De frontend-imagebuild legt per workflowrun eenmaal drie niet-gevoelige Dart-defines vast:

- `BUILD_ENVIRONMENT`: `production` voor de productievariant, `acceptance` voor de afzonderlijke
  acceptance-variant en `preview` voor iedere PR-preview;
- `SOURCE_REVISION`: de volledige broncommit die door de workflow wordt uitgecheckt. Voor een PR is
  dit de headcommit en niet de synthetische mergecommit; voor main is dit de main-commit;
- `DEPLOYED_AT`: één aan het begin van de imageworkflow vastgelegde UTC-tijd in ISO-8601-formaat,
  hergebruikt door alle frontendvarianten uit die workflowrun.

`dashboard-frontend/Dockerfile` geeft deze waarden uitsluitend compile-time via `--dart-define` aan
de Flutter-webbuild door. De draaiende nginx-container leest daarom geen repositorybestanden en
heeft geen runtimeconfiguratie of extra API-call nodig. Zonder build-args blijven de Dockerfile- en
lokale builddefaults leeg; de UI normaliseert ieder veld dan onafhankelijk naar `Onbekend`.
Commitberichten, auteursinformatie, e-mailadressen, URLs en configuratie/secrets zijn geen invoer.
De identiteit beschrijft uitsluitend de geserveerde frontendbuild, niet noodzakelijk iedere
backendcomponent.

De preview-database is een wegwerpbare in-namespace Postgres (`deploy/overlays/preview`)
met een vaste, niet-gevoelige connectiestring — geen echt secret om op te halen, vandaar de
simpele recipe hierboven in plaats van een `oc get secret`-aanroep.

Een PR-previewdatabase kan meerdere branchrevisies overleven. Wanneer Flyway na integratie van
`main` een validatiefout vindt door een botsende, nog niet gemergde migratieversie, bouwt de runtime
uitsluitend deze gevalideerde per-PR-wegwerpdatabase schoon opnieuw op en seedt daarna de vaste
previewdata. Productie en de vaste acceptatieomgeving houden het normale fail-closed Flywaygedrag;
hun schema wordt nooit door dit herstelpad opgeschoond.

## Standing acceptatieomgeving

De afzonderlijke overlay `deploy/overlays/acceptance` draait in namespace
`product-factory-acceptance` en gebruikt vaste hostnamen:

- Frontend: `https://product-factory-acceptance.vdzonsoftware.nl`
- Dashboard-backend/API: `https://product-factory-api-acceptance.vdzonsoftware.nl`
- Runtime: `https://product-factory-runtime-acceptance.vdzonsoftware.nl`

De runtime laadt de versieerbare synthetische `product-factory`-catalogus alleen wanneer
`PF_PREVIEW_ENABLED=true`, `PF_PREVIEW_MARKER=product-factory-acceptance`, geen
`PF_PREVIEW_PR_NUMBER` is gezet en `PF_DB_URL` naar de in-namespace database wijst. De
PR-previewmarker blijft de bestaande `hkh-autopilot`-dataset selecteren; productie heeft de
synthetische modus en marker uitgeschakeld. Autonome uitvoering en externe workspacepublicatie
staan in de acceptance-overlay uit.

De acceptance-frontend is een afzonderlijke imagevariant met compile-time
`ACCEPTANCE_DATASET=true`, een acceptance-API-base-URL en uitgeschakelde login. CI tagt deze als
`sha-<commit>-acceptance` en pint alleen de acceptance-overlay op die variant. Productie en
PR-previews gebruiken de veilige Dockerfile-default `ACCEPTANCE_DATASET=false` en tonen de melding
dus niet. Herstarten mag de vaste fixtures niet wijzigen of dupliceren; een afwijkende botsing op
een gereserveerde fixture-identiteit laat startup transactioneel falen in plaats van data te
overschrijven.
