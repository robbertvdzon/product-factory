# 0003 - Aparte machinekoppeling met Software Factory

- Status: Accepted
- Datum: 2026-08-24

## Context

De Software Factory-dispatcher levert complete stories automatisch aan Software Factory en leest
later hun voortgang. Dit machineverkeer mag geen menselijke Google-sessie gebruiken en krijgt geen
toegang tot andere Product Factory-routes.

Een v2-story bevat alle inhoud, acceptatiecriteria, UX en assets die Software Factory nodig heeft.
Software Factory hoeft en mag daarom geen uitvoeringsvragen aan Product Factory stellen.

## Decision

- De dispatcher gebruikt een afzonderlijk, intrekbaar en minimaal gescopeerd machinecredential voor
  het geversioneerde Software Factory-contract.
- Het productiecontract heeft basis-URL
  `https://dashboard.vdzonsoftware.nl/api/integrations/v2`. Product Factory configureert die als
  `PF_SOFTWARE_FACTORY_URL` en leest het credential uit `PF_SOFTWARE_FACTORY_TOKEN`; Software
  Factory ontvangt dezelfde waarde als `SF_PRODUCT_FACTORY_TOKEN`.
- Het contract ondersteunt alleen:
  - verbinding en API-versie lezen via `GET /status`;
  - idempotent een compleet `StoryDeliveryPackage` aanmaken;
  - met externe storyreferentie, product-ID of idempotentiesleutel de status opvragen;
  - status `OPEN`, `DONE` of `CANCELLED` retourneren;
  - bij `DONE` verplicht de exacte `deliveredCommitSha` retourneren;
  - bij `CANCELLED` zo mogelijk een veilige reden retourneren.
- Er bestaat geen vraag-, antwoord- of inhoudelijke terugkoppelroute van Software Factory naar
  Product Factory.
- Een contractgeldig storypakket moet worden geaccepteerd. Weigering, een onbekende status, een
  vraagresponse of `DONE` zonder commit is een technische contractfout en nooit planfeedback.
- Alle retries gebruiken dezelfde idempotentiesleutel. De dispatcher zoekt vóór heraanmaak eerst of
  extern werk al bestaat.
- Het externe request gebruikt de kleine v2-vorm met `title`, één volledige `description` en alleen
  binaire `attachments`. Product Factory stuurt geen client-side pakkethash; Software Factory
  berekent die zelf. De integratie introduceert geen aantal-, grootte- of MIME-allowlist.

## Consequences

- De machinecredential staat uitsluitend in secretbeheer en geeft geen menselijke UI-toegang.
- Product Factory kan objectief onderscheiden tussen nog open, afgerond en geannuleerd extern werk.
- De oplevercommit kan worden vergeleken met de werkelijk gedeployde revision voordat testen begint.
- Software Factory kan geen Product Factory-proces, agent of menselijke vraagflow starten.
- `MockSoftwareFactory` implementeert exact dit beperkte contract en heeft geen answer-endpoint.
- Acceptatie gebruikt uitsluitend de mock en bevat geen productie-URL of productiecredential;
  productie activeert de echte adapter pas in dispatcher-stap 8.

## Gerelateerde documenten

- [Software Factory-dispatcher](../processen/software-factory-dispatcher.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
