# product-factory-5 - Worklog

Story-context bij eerste pickup:
Write-once-guard op conclusion-veld (status/critic_verdict) van shadow_iteration

Bevestig zelf, geautomatiseerd, het huidige schrijfpad van het conclusion-veld (status/critic_verdict op shadow_iteration, geschreven door markAccepted/markReviewed/markFailed in productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowIterationApi.kt) en registreer het resultaat als exact één van: precondities-niet-vervuld, confirmed-immutable-native, guard-added, of unconfirmed-partial-coverage. Ga uit van de reeds gevonden bevinding dat alle drie de methoden unconditionele 'update ... where id = ?' queries zijn zonder guard tegen een reeds-terminale staat (ACCEPTED/NEEDS_REVISION/REJECTED/FAILED). Bij verwachte uitkomst guard-added: voeg een write-once-guard toe op de bestaande status/critic_verdict-kolommen (bv. via een WHERE-conditie die alleen niet-terminale iteraties update), zonder nieuw schemaveld. Log een afgewezen tweede schrijfpoging traceerbaar (iteratie-id) via een toe te voegen SLF4J-logger. Schrijf een geautomatiseerde test die twee opeenvolgende iteratie-afrondingen simuleert en verifieert dat de conclusion-waarde van de eerste, al afgeronde iteratie ongewijzigd blijft. Volg bij een andere uitkomst dan guard-added de bijbehorende AC-tak (geen codewijziging, of alleen documentatie). Leg de vastgestelde uitkomst machineleesbaar vast.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Precondities herbevestigd: `ShadowIterationView` (productfactory-contracts/.../Contracts.kt) heeft
  géén apart `conclusion`-veld; `status` en `criticVerdict` zijn twee bestaande, losse kolommen op
  `shadow_iteration` (zie ook `dashboard-frontend/lib/classification.dart`, dat puur UI-afleiding op
  `status`/`criticVerdict` doet). Conform de Aannames in de story wordt "het bestaande
  conclusion-veld" geïnterpreteerd als deze bestaande `status`/`critic_verdict`-kolommen.
- `markAccepted`, `markReviewed` en `markFailed` in `ShadowIterationApi.kt`
  (`ShadowIterationRepository`) bleken inderdaad unconditionele
  `update shadow_iteration set ... where id = ?`-queries, zónder guard tegen een iteratie die al in
  een terminale staat staat (ACCEPTED/NEEDS_REVISION/REJECTED/FAILED). Het risico bestaat dus
  daadwerkelijk voor dit schrijfpad.
- **Vastgestelde uitkomst: `guard-added`.**
- Guard toegevoegd: alle drie de methoden krijgen een extra
  `and status not in ('ACCEPTED', 'NEEDS_REVISION', 'REJECTED', 'FAILED')` in hun WHERE-clausule
  (constante `TERMINAL_STATUSES_SQL`), zodat een tweede schrijfpoging op een al terminale iteratie
  0 rijen raakt in plaats van de bestaande `status`/`critic_verdict`-waarde te overschrijven. Geen
  nieuw schemaveld/kolom/migratie.
- Een genegeerde tweede schrijfpoging wordt traceerbaar gelogd via een nieuwe SLF4J-logger op
  `ShadowIterationRepository` (`log.warn(...)`, met iteratie-id), per methode met een eigen
  boodschap.
- Test toegevoegd: `productfactory/src/test/kotlin/nl/vdzon/productfactory/iteration/ShadowIterationRepositoryWriteOnceGuardTest.kt`.
  Simuleert twee opeenvolgende afrondingen van dezelfde iteratie (`markAccepted` gevolgd door een
  tweede `markAccepted`, en `markReviewed` gevolgd door `markFailed`) en verifieert dat de
  conclusion-waarde (status/critic_verdict, plus de bijbehorende workspace-/foutvelden) van de
  eerste, al afgeronde iteratie ongewijzigd blijft.
- Geen wijziging aan Git-, GitHub-, OpenShift-, database-schema- of PR-goedkeuringsflow buiten de
  guard-logica in `ShadowIterationRepository`.
- Volledig vangnet (`mvn -B --no-transfer-progress clean verify`) gedraaid; groen (zie run-log).
- Niet gedaan: `flutter analyze`/`flutter test` niet opnieuw gedraaid — er is geen frontendcode
  gewijzigd in deze subtaak (backend-only guard).

Vastgestelde uitkomst (machineleesbaar): `{"outcome":"guard-added"}`
