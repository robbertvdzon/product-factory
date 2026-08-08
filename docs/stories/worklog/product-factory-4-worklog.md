# product-factory-4 - Worklog

Story-context bij eerste pickup:
Inline uitklappaneel met scope-disclaimer op de uitkomstbadge

Breid ClassificationBadge (dashboard-frontend/lib/classification.dart) uit met een toetsenbord- en muis-activeerbaar inline disclosure-paneel (geen AlertDialog/showDialog) direct onder de iteratierij in main.dart (_limitedSection('iterations', ...), rond regel 558-608). Documenteer in de PR-/commitbeschrijving dat AlertDialog al het bestaande herbruikbare modale patroon is (IterationSessionDialog, AddProductDialog, ProductSettingsDialog, _completeHumanAction) en waarom hier toch voor inline disclosure gekozen wordt. Implementeer: Semantics(expanded: false/true) op het badge dat toggelt bij activatie; Focus/FocusNode met onKeyEvent voor Enter/Space (activeren) en Escape (sluiten + focus terug op badge); het paneel toont voor alle vijf varianten (onderzoek-onvoldoende, guardrail-conflict, richting-gekozen, richting-verworpen, niet-classificeerbaar) exact de zin 'Dit toont wat de uitkomst was, niet waarom.' zonder link/verwijzing naar een externe iteratielog-route; elk badge behoudt een Semantics-label met de volledige categorienaam. Zorg dat het badge zijn eigen tap/toetsenbordactivatie afhandelt zonder de bestaande onTap van de rij (opent IterationSessionDialog via _showIteration) te triggeren. Schrijf bijbehorende flutter_test widgettests (toetsenbord-only navigatie/activatie, expanded-toggle zonder dialoogwidget in de boom, paneeltekst per variant, Escape+focusherstel, Semantics-label per variant, afwezigheid van externe link) naast de bestaande classification_test.dart en classification_badge_widget_test.dart, en verifieer dat bestaande tests en het klikgedrag van de rij (detaildialoog opent nog steeds bij klik buiten het badge) blijven werken.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Vooronderzoek/motivatie (conform Scope in .task.md): `AlertDialog` is al het bestaande
  herbruikbare modale patroon in dit project (`IterationSessionDialog`, `AddProductDialog`,
  `ProductSettingsDialog`, `_completeHumanAction`). Voor deze story is bewust gekozen voor een
  inline disclosure-paneel i.p.v. (weer) een `AlertDialog`, om het lichte scope-signaal niet te
  koppelen aan het zwaardere detaildialoog en om geen focus-trap/`role="dialog"`-semantiek te
  introduceren die hier niet nodig is. Deze motivatie staat ook als dartdoc-commentaar bij
  `ClassificationBadge` in `dashboard-frontend/lib/classification.dart`.
- `ClassificationBadge` (dashboard-frontend/lib/classification.dart) omgezet van `StatelessWidget`
  naar `StatefulWidget`: het widget beheert nu zelf een `FocusNode` en `_expanded`-state, en toont
  bij expansie een inline paneel direct onder de badge-chip binnen dezelfde widget (dus binnen
  dezelfde rij/Wrap in main.dart) — main.dart hoefde daardoor niet te wijzigen.
  - `Semantics(expanded:/label:/button:)` op het buitenste niveau van het widget, zodat de
    expanded-status en het label (dat al de volledige categorienaam bevatte, niet uitsluitend een
    kleur/slug) opvraagbaar blijven via `tester.getSemantics(find.byType(ClassificationBadge))`.
  - `Focus`/`FocusNode` met `onKeyEvent`: Enter/Space toggelen het paneel, Escape sluit het paneel
    en roept `_focusNode.requestFocus()` aan zodat de focus op het badge blijft/terugkeert.
  - `GestureDetector` (opaque hit-test) op de badge-chip consumeert de tap zodat de omringende
    `ListTile.onTap` (die `IterationSessionDialog` opent) niet mee triggert; een tap ernaast/
    erbuiten bereikt de rij ongewijzigd.
  - Paneeltekst is exact `kBadgeScopeDisclaimer` = "Dit toont wat de uitkomst was, niet waarom."
    voor alle vijf varianten; geen `showDialog`/`AlertDialog`/`Dialog`, geen link/route-verwijzing.
- Nieuwe widgettests in `dashboard-frontend/test/classification_badge_disclosure_test.dart` (19
  tests, toetsenbord-only waar vereist): Tab-bereikbaarheid + Enter/Space-activatie per variant,
  afwezigheid van `Dialog`/`AlertDialog` na activatie, paneeltekst per variant, Escape sluit +
  herstelt focus, Semantics-label per variant, afwezigheid van externe link/route in het paneel, en
  regressie op het rijklikgedrag (tap op badge triggert rij-onTap niet, tap ernaast wel).
- Bestaande tests ongewijzigd gebleven en blijven slagen (`classification_test.dart`,
  `classification_badge_widget_test.dart`, `widget_test.dart`,
  `iteration_progress_indicator_test.dart`, `limited_list_test.dart`,
  `iteration_workspace_publication_regression_test.dart`, `formatting_test.dart`).
- Vangnet gedraaid: `flutter analyze` (dashboard-frontend) → 0 issues; `flutter test`
  (dashboard-frontend) → alle 63 tests groen; `mvn -B --no-transfer-progress clean verify`
  (repo-root) → zie resultaat hieronder (backend ongewijzigd gelaten in deze story).
- `pubspec.lock` niet gewijzigd; geen nieuwe dependencies toegevoegd (alleen
  `package:flutter/services.dart` voor `LogicalKeyboardKey`/`KeyEvent`, onderdeel van de Flutter
  SDK zelf).
- `.factory/verification.yaml` ongewijzigd: geen nieuwe verificatiecommando's nodig voor deze
  story.
