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

## Review (product-13)
- Diff (main...HEAD) beperkt tot: main.dart (rij QUEUED/RUNNING → IterationProgressIndicator,
  nieuwe widget), classification_test.dart (CANCELLED → niet-classificeerbaar), nieuwe
  iteration_progress_indicator_test.dart (wederzijdse exclusiviteit + liveRegion), worklog.
- Geverifieerd: `kBekendeStatuswaardenPerCategorie` mapte QUEUED/RUNNING al op
  kOnderzoekOnvoldoende — de nieuwe indicator vervangt terecht die potentieel misleidende badge
  voor lopende iteraties; detaildialoog (regel ~767) had al eigen, ongewijzigde progress-UI en
  gebruikt geen ClassificationBadge, dus terecht buiten scope gelaten.
- `dart format --set-exit-if-changed lib/main.dart` toont afwijkingen, maar diff tegen een lokaal
  geformatteerde kopie bevestigt dat deze uitsluitend pre-existing blokken raken (snackbar/
  dropdown/helperText rond regels 290-1458), niet het gewijzigde blok van deze story (557-603,
  1501-1548). Geen regressie.
- Geen wijziging aan Contracts.kt/schema/migraties; alleen bestaand `status`-veld gelezen.
  Bevinding over status als bestaand onderscheidend signaal staat expliciet in de commit-
  beschrijving/worklog. Akkoord.

## Test (product-14)
- Geen preview-omgeving/browser beschikbaar in de agentcontainer (bevestigd via `docs/factory/deployment.md`
  en het ontbreken van SF_PREVIEW_* in `.task.md` bij pickup); geverifieerd via codeverificatie +
  het volledige agent-runnable vangnet.
- Diff-check: alleen `dashboard-frontend/lib/main.dart`, `dashboard-frontend/test/classification_test.dart`,
  `dashboard-frontend/test/iteration_progress_indicator_test.dart` en deze worklog gewijzigd. Geen
  wijziging aan `Contracts.kt`, databaseschema of migraties; geen nieuw datamodelveld.
- Code-review: iteratierij in `main.dart` (regel ~558-590) toont voor `status` QUEUED/RUNNING nu
  `IterationProgressIndicator` i.p.v. `ClassificationBadge`; voor elke andere status blijft exact
  één `ClassificationBadge` staan, ongewijzigd. `IterationProgressIndicator` gebruikt
  `Semantics(liveRegion: true)` (aria-live="polite"-equivalent). Komt overeen met de acceptance
  criteria en de eerder vastgestelde bevinding dat `status` (QUEUED/RUNNING vs. overig) het enige
  gebruikte onderscheidende signaal is; geen apart CANCELLED-veld.
- Vangnet gedraaid (allemaal groen, geen failures/errors):
  - `mvn -B --no-transfer-progress clean verify` (root): BUILD SUCCESS, alle modules groen
    (7 backend-tests, 0 failures/errors).
  - `flutter analyze` (dashboard-frontend): "No issues found!".
  - `flutter test` (dashboard-frontend): alle 44 tests groen, incl. de nieuwe
    `classification_test.dart`-case (onbekende status `CANCELLED` → `kNietClassificeerbaar`,
    deterministisch dubbel geverifieerd) en `iteration_progress_indicator_test.dart` (wederzijdse
    exclusiviteit tussen indicator en badge per statusgroep + liveRegion-assertie).
  - `flutter build web` (rooktest, niet in verification.yaml maar goedkoop en build/ is gitignored):
    slaagt, geen compile-issues.
- Conclusie: implementatie voldoet aan alle acceptance criteria van product-factory-3; vangnet
  volledig groen. Akkoord — door naar `tested`.
