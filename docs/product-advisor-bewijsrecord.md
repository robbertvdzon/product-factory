# Bewijsrecord — Product Advisor en Product Requests

Datum: 2026-09-10

Aanvulling: de hieronder beschreven oude PO/factory-ownerketen is vervangen door
[versiegebonden PO-/architectbeoordelingen](platform/epic-samenwerking-runbook.md).

## Opgeleverd

- duurzame identiteit, `FACTORY_OWNER` en geversioneerde product-ownerlidmaatschappen met
  grant/revokehistorie en onmiddellijke backendautorisatie;
- productgebonden gesprekken, append-only berichten, bookmarkbare gesprek-URL en persoonlijke
  acties/notificaties;
- `PRODUCT_ADVISOR.CONVERSE` via Agent Runtime v2 met bevroren Git-SHA, context, uitvoering,
  promptversie en strikt resultaatschema;
- onveranderlijke ProductRequest-versies met expliciete bevestiging, expected-version en
  idempotentie;
- gewone bugfix via Software Factory v2 met verloren-responseherstel;
- gerichte DesignWorkItem-route, exact hervatten na geadresseerde vraag en product-owner- gevolgd
  door factory-ownerapproval van dezelfde epicversie;
- Flyway 21, Testbeddataset `product-advisor-v1`, negen scenario's, contract- en integratietests;
- kortlevend dashboardtoken in de productie-SealedSecret en een reproduceerbaar rotatiescript.

## Productieprobe bestaande hotfixingang

Met een doelgebonden token antwoordde de live statusroute HTTP 200. Een gecontroleerde storyaanmaak
met `hotfix=true`, `start=true` en marker
`Product-Request: 00000000-0000-4000-8000-000000000016:v1` antwoordde HTTP 200 en leverde
`SF-2377`. De detailroute bevestigde marker, repository, automatische approval, vragen toegestaan en
de gevraagde notificatie-events.

De live lijstendpoint retourneerde `SF-2377` daarna niet. Daardoor kan de adapter de marker na een
verloren create-response niet altijd via de bestaande lijstingang herstellen. De productieroute is
op 2026-09-10 bewust geactiveerd met at-least-once-semantiek: per exacte ProductRequest-versie zijn
maximaal twee verzendpogingen toegestaan. In het zeldzame geval dat de eerste POST slaagt maar het
antwoord verloren gaat, kan daardoor maximaal één dubbele story ontstaan. Daarna blijft het
request zichtbaar op `ROUTING_FAILED` voor handmatig herstel.

## Verificatie

De lokale releasecontrole is afgerond met 148 backendtests, 35 frontendtests, statische
Flutter-analyse, een Flutter-webreleasebuild, rendercontroles voor acceptatie en productie en
shell-/whitespacecontroles. De geautomatiseerde releaseworkflow bewaart de exacte bronrevisie en
image-digests en promoveert hetzelfde gecontroleerde image eerst naar acceptatie en daarna naar
productie. Broncontroles bevestigen dat uitsluitend `product-factory` is gewijzigd; de repositories
`softwarefactory` en `pvdd` blijven schoon.
