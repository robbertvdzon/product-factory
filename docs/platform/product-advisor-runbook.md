# Runbook — Product Advisor en Product Requests

Zie voor de nieuwe PO-/architectflow, rollen en migratie het
[runbook epic-samenwerking](epic-samenwerking-runbook.md).

## Operationele grens

`GET /api/foundation/implementations` toont `product-advisor-v1`. Product Advisor gebruikt Agent
Runtime v2 voor uitsluitend adviserende, strikt gestructureerde output. Externe mutaties volgen pas
na een geauthenticeerd, versiegebonden en idempotent backendcommand.

De gewone bugfixroute gebruikt het bestaande Software Factory-v2-token. De hotfixroute gebruikt een
afzonderlijk kortlevend dashboardtoken en geeft dat nooit aan Runtime door. Productie gebruikt
`PF_SOFTWARE_FACTORY_HOTFIX_ENABLED=true` met maximaal twee verzendpogingen per exacte
ProductRequest-versie.

## Dashboardtoken roteren

Voer vanuit de repositoryroot uit:

```bash
./tools/rotate-software-factory-dashboard-token.sh
```

Het script haalt ontbrekende bronwaarden uit de bestaande live secrets, toont geen credential,
genereert een token met de bestaande Software Factory-secret en allowlist en schrijft uitsluitend
`deploy/overlays/production/sealed-secret-product-factory.yaml`. Controleer daarna de diff op
sleutelnamen en laat de normale releaseworkflow dezelfde images promoveren. Een token is dertig
dagen geldig; roteer vóór verval en controleer als factory owner
`GET /api/operations/product-advisor/hotfix-token`. Alleen `configured`, `reachable`, `checkedAt` en
een veilige foutcode mogen zichtbaar zijn.

## Hotfixguard en at-least-once-risico

Een gecontroleerde productieprobe heeft bewezen dat status, create en detail werken:

1. `GET /api/v1/status` accepteert het doelgebonden token;
2. `POST /api/v1/stories` maakt met `hotfix=true`, `start=true` en een unieke
   `Product-Request: <uuid>:v<versie>`-marker precies één story;
3. geweigerd of verlopen token levert alleen een veilige foutcode op.

De lijstendpoint retourneerde de probestory niet. Daardoor is een timeout na een succesvolle POST
niet veilig te onderscheiden van een niet-uitgevoerde POST. Dit bekende risico is geaccepteerd:
Product Factory probeert per exacte requestversie maximaal twee keer. Er kan dus maximaal één
dubbele story ontstaan; na de tweede mislukte poging stopt automatische routering.

## Fout en herstel

- `HOTFIX_ROUTE_DISABLED`: verwacht zolang het externe herstelbewijs ontbreekt; geen externe call
  is gedaan. Laat het request zichtbaar op `ROUTING_FAILED` staan.
- `HOTFIX_TOKEN_MISSING` of `HOTFIX_TOKEN_REJECTED`: roteer/seal/deploy het token en controleer de
  statusroute; log of kopieer het token nooit.
- `HOTFIX_UNREACHABLE` of `HOTFIX_HTTP_5xx`: controleer beide mogelijke stories en herstel het
  request handmatig als de twee automatische pogingen zijn verbruikt. Zet de guard uit bij een
  aanhoudende storing.
- Een gewone bugfix herstelt via de v2-idempotentiesleutel; een epickandidaat hervat via hetzelfde
  `DesignWorkItem` en dezelfde gekoppelde gebruikersvraag.

Rollback zet de guard op `false` en kan daarna het vorige immutable image terugzetten. Flyway 21 is
additief en hoeft niet te worden teruggedraaid. Trek bij mogelijke tokenblootstelling het dashboard-
secret in Software Factory in en roteer beide kanten gecontroleerd.
