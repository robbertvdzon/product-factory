# 0002 - Google-SSO voor menselijke toegang tot Product Factory

- Status: Accepted
- Datum: 2026-08-24

## Context

Er is één globale Stakeholder die via de webinterface alle producten en algemene instellingen mag
beheren. Product Factory wil geen eigen wachtwoorden opslaan. De backend is de autoritatieve
beveiligingsgrens; frontendstatus of een verborgen knop mag nooit autorisatie vervangen.

Acceptatie gebruikt synthetische tijdelijke data en heeft authenticatie bewust uitgeschakeld. Die
modus mag in productie niet kunnen starten.

## Decision

- Productie gebruikt Google OpenID Connect voor menselijke login.
- De backend verifieert handtekening, issuer, audience, expiry en `email_verified` van het Google
  ID-token en controleert het e-mailadres tegen een expliciete allowlist.
- Na geldige login maakt Product Factory een eigen begrensde sessie, bij voorkeur in een `Secure`,
  `HttpOnly`, `SameSite` cookie.
- Muterende cookieverzoeken krijgen origin- en CSRF-bescherming.
- Alleen login, logout, health en beperkte versie-informatie mogen zonder productsessie bereikbaar
  zijn.
- Productie faalt bij startup wanneer authenticatie verplicht is maar client-ID, allowlist of
  sessiesleutel ontbreekt.
- Acceptatie mag authenticatie alleen via het expliciete acceptatieprofiel uitschakelen en toont dat
  zichtbaar op iedere pagina.

## Consequences

- Productie is afhankelijk van Google als identity provider voor menselijke login.
- Client-ID, allowlist en sessiesleutel worden als secrets/configuratie beheerd en nooit gelogd.
- Tests gebruiken een injecteerbare tokenverifier of lokale test-keyset en hebben geen Google-call
  nodig.
- Machinekoppelingen, waaronder Software Factory en AI-workers, gebruiken eigen gescopeerde
  credentials en nooit een Stakeholdersessie.

## Gerelateerde documenten

- [Technische basis](../platform/technische-basis.md)
- [Frontend](../stakeholder/frontend.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
