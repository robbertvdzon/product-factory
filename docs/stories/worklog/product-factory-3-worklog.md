# product-factory-3 - Worklog

Story-context bij eerste pickup:
Scheid voortgangsindicator van conclusion-badge op basis van bestaand status-veld

Bevinding (bevestigd via broncode-inspectie): ShadowIterationView.status (Contracts.kt) is het bestaande veld dat lopend (QUEUED/RUNNING) van afgerond (ACCEPTED/NEEDS_REVISION/REJECTED/FAILED) onderscheidt, al gebruikt in main.dart:767. Geen apart CANCELLED-veld; een tijdens uitvoering afgebroken iteratie valt via de bestaande fallback in classifyIterationOutcome op kNietClassificeerbaar. Implementeer in dashboard-frontend/lib/main.dart, iteratierij binnen _limitedSection('iterations', ...) (regel 558-603): bij status QUEUED/RUNNING toon een neutrale voortgangsindicator met Semantics(liveRegion: true) (aria-live="polite"-equivalent) in plaats van ClassificationBadge; bij elke andere status blijft exact één ClassificationBadge staan (ongewijzigd gedrag). Geen wijziging aan classification.dart's mapping-API of Contracts.kt/databaseschema, geen migratie. Schrijf als onderdeel van dit werk: unit test in classification_test.dart dat een onbekende, niet-QUEUED/RUNNING status (representatief voor 'geannuleerd tijdens uitvoering') deterministisch naar kNietClassificeerbaar mapt; widget-/snapshot-test die rijen gegroepeerd op status (QUEUED/RUNNING vs. de vier afgeronde statuswaarden) doorloopt en wederzijdse exclusiviteit tussen indicator en badge assert; test die de liveRegion/aria-live-eigenschap van de indicator verifieert. Documenteer de bevinding over het gebruikte bestaande signaal expliciet in de commit-/PR-beschrijving. Formatteer gewijzigde/nieuwe regels met dart format, laat ongerelateerde regels met rust.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Bevestigd (broncode-inspectie): `ShadowIterationView.status` (Contracts.kt) is het bestaande
  veld dat lopend (QUEUED/RUNNING) van afgerond (ACCEPTED/NEEDS_REVISION/REJECTED/FAILED)
  onderscheidt; dit patroon (`status == 'QUEUED' || status == 'RUNNING'`) staat al in
  `main.dart` rond regel 767 (detaildialoog). Er bestaat geen apart CANCELLED-statusveld in het
  datamodel; een tijdens uitvoering afgebroken iteratie is dus geen nieuw te bouwen geval en valt
  (net als elke andere onbekende status) via de bestaande fallback in `classifyIterationOutcome`
  op `niet-classificeerbaar`.
- `dashboard-frontend/lib/main.dart`: in de iteratierij binnen `_limitedSection('iterations', ...)`
  wordt nu, bij `status` QUEUED of RUNNING, een nieuwe `IterationProgressIndicator`-widget getoond
  in plaats van de `ClassificationBadge`; voor elke andere status blijft exact één
  `ClassificationBadge` staan (ongewijzigd gedrag). `IterationProgressIndicator` gebruikt
  `Semantics(liveRegion: true)` als Flutter-web-equivalent van `aria-live="polite"`.
- Geen wijziging aan `classification.dart`'s mapping-API of aan `Contracts.kt`/databaseschema,
  geen migratie; alleen het bestaande `status`-veld wordt gelezen.
- Tests toegevoegd:
  - `test/classification_test.dart`: unit test die een onbekende, niet-QUEUED/RUNNING statuswaarde
    (`CANCELLED`, representatief voor een tijdens uitvoering afgebroken iteratie) deterministisch
    naar `kNietClassificeerbaar` laat mappen.
  - `test/iteration_progress_indicator_test.dart` (nieuw): widgettest die rijen gegroepeerd op
    status (QUEUED/RUNNING vs. de vier afgeronde statuswaarden) doorloopt en wederzijdse
    exclusiviteit tussen `IterationProgressIndicator` en `ClassificationBadge` per rij assert, plus
    een test die `flagsCollection.isLiveRegion` op de indicator verifieert.
- Vangnet gedraaid: `mvn -B --no-transfer-progress clean verify` (BUILD SUCCESS, alle modules groen),
  `flutter analyze` (geen issues) en `flutter test` (alle 44 tests groen) in `dashboard-frontend`.
  `pubspec.lock` is ongewijzigd. `.factory/verification.yaml` dekte de gewijzigde paden al en is
  niet aangepast.
- Alleen nieuwe/gewijzigde regels geformatteerd met `dart format`; ongerelateerde, historisch
  niet-geformatteerde regels in `main.dart` blijven ongewijzigd (bevestigd via diff-review).
