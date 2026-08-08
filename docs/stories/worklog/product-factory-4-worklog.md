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

## Reviewer-notities (product-19)

- Diff tegen `main` beperkt tot `dashboard-frontend/lib/classification.dart`,
  nieuwe testfile `dashboard-frontend/test/classification_badge_disclosure_test.dart`
  en de worklog zelf — conform scope uit `.task.md`, `main.dart` inderdaad ongewijzigd.
- Geverifieerd: `AlertDialog`/`showDialog`/`Dialog` worden nergens gebruikt in het nieuwe
  paneel; motivatie voor inline disclosure staat zowel als dartdoc bij `ClassificationBadge`
  als in de worklog, conform AC.
- Alle vijf varianten (`kIterationClassifications`) hebben eigen toetsenbord-only tests
  (Tab-bereikbaarheid, Enter/Space-activatie, exacte disclaimerzin, Semantics-label).
  Escape-test bevestigt zowel `expanded -> false` als focus terug op het badge.
  Test op afwezigheid van externe link/route aanwezig.
- Bestaande `classification_badge_widget_test.dart` blijft functioneel compatibel: die
  test raakt alleen `label`/`find.text`, niet de nieuwe expanded/button-flags.
- `main.dart` (regel 578-589) bevestigt dat de echte iteratierij dezelfde
  `Wrap`-in-`ListTile.title`-structuur heeft als de testharness — geen mismatch tussen test
  en productiecode.
- `.factory/verification.yaml`-commando's `dashboard-flutter-analyze` en
  `dashboard-flutter-test` dekken de gewijzigde paden (`dashboard-frontend/`); geen
  backend-, dependency- of Dockerfile-wijzigingen die buiten het vangnet vallen.
- [suggestie] Het uitklappaneel zit binnen dezelfde `Wrap` als de rij-titel in `main.dart`
  (regel 578-589); bij expansie groeit de rijhoogte binnen die Wrap. Functioneel geen
  probleem en niet in scope van deze story, maar visueel iets om in een latere UX-pass
  naar te kijken.
- Conclusie: geen blockers gevonden; akkoord.

## Tester-notities (product-20)

- Vangnet gedraaid (agent-runnable subset uit `.factory/verification.yaml`):
  - `flutter analyze` (dashboard-frontend) → "No issues found!" (0 issues).
  - `flutter test` (dashboard-frontend) → 63/63 tests groen, exit 0 ("All tests passed!").
    Zowel default (concurrent) als `-j 1` (serieel) gedraaid; serieel geeft de
    betrouwbaarste per-testnaam-log (concurrente compact-reporter-output toont soms
    dezelfde regel meerdere keren door interleaving van shards — cosmetisch, de
    +N-teller en het eindtotaal (63) kloppen in beide runs). Alle 8 testfiles zijn
    hiermee bevestigd inclusief de nieuwe `classification_badge_disclosure_test.dart`
    (19 tests) en de bestaande `classification_test.dart`, `formatting_test.dart`,
    `widget_test.dart`, `classification_badge_widget_test.dart`,
    `iteration_progress_indicator_test.dart`,
    `iteration_workspace_publication_regression_test.dart`, `limited_list_test.dart`.
  - `mvn -B --no-transfer-progress clean verify` (repo-root) → BUILD SUCCESS, alle
    backend-modules groen (35 + 17 + 7 tests, 0 failures/errors).
  - De twee docker-image-buildcommando's in `.factory/verification.yaml` staan op
    `agentRunnable: false` en zijn CI-only (geen docker in de agentcontainer);
    conform eerdere agent-tip niet zelf gedraaid.
- Codeverificatie tegen de acceptance criteria in `.task.md`:
  - Diff blijft beperkt tot `dashboard-frontend/lib/classification.dart`, de nieuwe
    testfile en de worklog — `main.dart` ongewijzigd bevestigd (`grep ClassificationBadge
    lib/main.dart` toont alleen het bestaande gebruik op regel 587).
  - Alle vijf badge-varianten hebben eigen toetsenbord-only tests (Tab, Enter/Space),
    expanded-toggle zonder `Dialog`/`AlertDialog` in de boom, de exacte disclaimerzin
    per variant, Escape+focusherstel, Semantics-label per variant, en afwezigheid van
    een externe iteratielog-link — allemaal aanwezig en groen in
    `classification_badge_disclosure_test.dart`.
  - Rijklikgedrag-regressietest bevestigt: tap op badge triggert `ListTile.onTap` niet,
    tap ernaast wel.
- Preview: `SF_PREVIEW_URL` (https://product-factory-pr-38.vdzonsoftware.nl) en de
  API-health-endpoint beantwoorden beide met HTTP 200 (curl-smoketest); er is geen
  browsertool in deze agentcontainer beschikbaar voor interactieve
  toetsenbord-/screenshotverificatie in de preview zelf, dus die verificatie steunt op
  de widgettests hierboven (conform eerdere agent-tip over ontbrekende browser).
- Geen bugs gevonden; geen tijdelijke testdata aangemaakt (geen cleanup nodig).
