# product-factory-6 - Worklog

Story-context bij eerste pickup:
Documenteer status/conclusion-onderscheid in functional-spec.md

Voeg een nieuwe, statische sectie toe aan docs/factory/functional-spec.md die uitlegt: (1) status is alleen 'lopend' (QUEUED/RUNNING) of 'voltooid' (ACCEPTED/NEEDS_REVISION/REJECTED/FAILED), en conclusion is pas relevant/geldig zodra status voltooid is; (2) een tijdens uitvoering onderbroken iteratie wordt automatisch, zonder apart menselijk besluitmoment, geclassificeerd als 'niet-classificeerbaar' via de bestaande fallback-logica in classifyIterationOutcome (dashboard-frontend/lib/classification.dart); (3) de onvoorwaardelijke immutabiliteitszin - het eindoordeel van een iteratie wijzigt na vaststelling niet meer - toegestaan omdat kandidaat 0 (product-factory-5, write-once-guard, commit d8cd08d) is gemerged met bevestigde uitkomst 'guard-added' (markAccepted/markReviewed/markFailed in ShadowIterationApi.kt weigeren een tweede schrijfpoging op status/critic_verdict zodra status al terminaal is, en loggen dit met log.warn); (4) expliciete afwijkingsvermelding: er bestaat geen apart 'conclusion'-databaseveld, de conclusie is een afgeleide van status + criticVerdict (+ errorMessage bij FAILED) naar één van de vijf badges. Baseer alle veldnamen en waarden uitsluitend op de bevestigde code in Contracts.kt, classification.dart en ShadowIterationApi.kt. Voeg daarnaast een lichte geautomatiseerde check toe (klein testbestand, aansluitend bij bestaande testconventies) die verifieert dat het documentatiebestand bestaat en de drie vereiste kernzinnen bevat. Puur additief: geen wijziging aan routes, databaseschema, authenticatie of PR-goedkeuringsflow.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Vastgestelde uitkomst van kandidaat 0 (`product-factory-5`) bevestigd via de bestaande worklog:
  `guard-added` — `markAccepted`/`markReviewed`/`markFailed` in `ShadowIterationApi.kt` weigeren een
  tweede schrijfpoging op `status`/`critic_verdict` zodra een iteratie al terminaal is (`... where
  id = ? and status not in (TERMINAL_STATUSES_SQL)`) en loggen dit met `log.warn`. Daarom mag de
  immutabiliteitszin onvoorwaardelijk worden opgenomen.
- Veldnamen/waarden herbevestigd in code: `ShadowIterationView.status`
  (`productfactory-contracts/.../Contracts.kt`, waarden QUEUED/RUNNING/ACCEPTED/NEEDS_REVISION/
  REJECTED/FAILED), en de vijf badges + `classifyIterationOutcome`-fallbacklogica in
  `dashboard-frontend/lib/classification.dart` (geen apart `conclusion`-veld; `status` +
  `criticVerdict` + `errorMessage` bepalen de badge; onbekende/afgebroken status → altijd
  `niet-classificeerbaar`).
- Nieuwe, statische sectie "Status en conclusion van een productcyclus" toegevoegd aan
  `docs/factory/functional-spec.md`, direct vóór "Testerafspraken". Bevat de vier vereiste
  elementen: (1) status is alleen lopend of voltooid, conclusion pas geldig bij voltooid, (2) een
  onderbroken iteratie wordt automatisch en zonder apart besluitmoment `niet-classificeerbaar`, (3)
  onvoorwaardelijke immutabiliteitszin, (4) expliciete afwijkingsvermelding: geen apart
  `conclusion`-databaseveld.
- Geautomatiseerde check toegevoegd:
  `productfactory/src/test/kotlin/nl/vdzon/productfactory/FunctionalSpecStatusConclusionDocTest.kt`
  (plain JUnit5, geen Spring-context, naar analogie van `ModulithArchitectureTest.kt`). Verifieert
  dat het bestand bestaat en de drie vereiste kernzinnen bevat (na whitespace-normalisatie, zodat de
  check onafhankelijk is van markdown-regelbreedte).
- Puur additief: geen wijziging aan routes, databaseschema, authenticatie, PR-goedkeuringsflow of
  bestaande code-paden.
- Volledig vangnet gedraaid: `mvn -B --no-transfer-progress clean verify` vanuit repo-root, groen
  (`BUILD SUCCESS`, exitcode 0). `FunctionalSpecStatusConclusionDocTest` liep mee: 4 tests, 0
  failures, 0 errors. `flutter analyze`/`flutter test` niet opnieuw gedraaid — geen frontendcode
  gewijzigd in deze subtaak.
- `.factory/verification.yaml` niet gewijzigd: de nieuwe testklasse valt al onder de bestaande
  `repository-maven-verify`-entry (pathPrefix `productfactory/`), en er is geen nieuw command nodig.

## Review (product-31)

- [info] Alle vier vereiste elementen in `docs/factory/functional-spec.md` geverifieerd tegen de
  bevestigde code: `ShadowIterationView.status` (Contracts.kt, `status: String`, geen enum) met
  QUEUED/RUNNING (lopend) en ACCEPTED/NEEDS_REVISION/REJECTED/FAILED (voltooid); geen apart
  `conclusion`-veld, badges + fallback naar `niet-classificeerbaar` kloppen met
  `classification.dart` (`kBekendeStatuswaardenPerCategorie`, `classifyIterationOutcome`); de
  onvoorwaardelijke immutabiliteitszin klopt met de `TERMINAL_STATUSES_SQL`-guard + `log.warn` in
  `ShadowIterationApi.kt` (regels 323-393) en met de bevestigde `guard-added`-uitkomst in
  `product-factory-5-worklog.md`.
- [info] `FunctionalSpecStatusConclusionDocTest.kt` dekt de drie vereiste kernzinnen met
  whitespace-genormaliseerde matches; valt onder bestaande `repository-maven-verify` (pathPrefix
  `productfactory/`), geen wijziging aan `.factory/verification.yaml` nodig.
- [info] Wijziging is puur additief: geen routes, schema, auth of PR-flow geraakt. Geen
  bugs/regressies/scope-afwijkingen gevonden.
- Oordeel: akkoord.

## Test (product-32)

- Documentatie-inhoud van `docs/factory/functional-spec.md` (sectie "Status en conclusion van een
  productcyclus") geverifieerd tegen de daadwerkelijke broncode:
  `ShadowIterationView.status` (Contracts.kt: `status: String`, geen enum); QUEUED/RUNNING/ACCEPTED/
  NEEDS_REVISION/REJECTED/FAILED (ShadowIterationApi.kt); geen apart `conclusion`-veld; vijf badges
  + fallback naar `niet-classificeerbaar` kloppen 1-op-1 met `kBekendeStatuswaardenPerCategorie` en
  `classifyIterationOutcome` in `dashboard-frontend/lib/classification.dart`; de onvoorwaardelijke
  immutabiliteitszin klopt met de `TERMINAL_STATUSES_SQL`-guard (`'ACCEPTED', 'NEEDS_REVISION',
  'REJECTED', 'FAILED'`) + `log.warn` in `markAccepted`/`markReviewed`/`markFailed`
  (ShadowIterationApi.kt, regels 323-393). Alle vier acceptance-criteria-zinnen letterlijk aanwezig.
- Volledig vangnet gedraaid en groen:
  - `mvn -B --no-transfer-progress clean verify` (repo-root): `BUILD SUCCESS`, exitcode 0.
    `FunctionalSpecStatusConclusionDocTest`: 4 tests, 0 failures, 0 errors. Alle overige modules
    (productfactory, agentworker, dashboard-backend, productfactory-common) eveneens 0
    failures/errors.
  - `flutter analyze` (dashboard-frontend): "No issues found!", exitcode 0.
  - `flutter test` (dashboard-frontend): "All tests passed!", 63/63, exitcode 0 (herhaalde testregels
    in de log zijn het bekende shard-interleaving-weergaveartefact, geen echte herhalingen — zie
    agent-tips).
- Preview-smoketest: `https://product-factory-pr-40.vdzonsoftware.nl` → 200,
  `https://product-factory-api-pr-40.vdzonsoftware.nl/actuator/health` → 200. Geen browsertool
  beschikbaar in de agentcontainer; interactieve verificatie was niet nodig omdat deze story puur
  documentatie + een backend-doctest betreft, geen UI-wijziging.
- Geen bugs of afwijkingen gevonden. Geen code/tests gewijzigd.
- Oordeel: `tested`.
