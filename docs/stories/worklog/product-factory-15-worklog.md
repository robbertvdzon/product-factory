# product-factory-15 - Worklog

Story-context bij eerste pickup:
'Toon technische details'-toggle per agentrol implementeren

Implementeer in dashboard-frontend/lib/main.dart (IterationSessionDialog, artifact-ExpansionTile-builder rond regel 990-1018) een geneste, standaard ingeklapte toggle met label 'Toon technische details' rond de bestaande ruwe-JSON-weergave (SelectableText(_prettyJson(...))), maar alleen wanneer readableFields.isNotEmpty. Gebruik een geneste ExpansionTile (initiallyExpanded: false) - die is standaard toetsenbordbedienbaar (Tab/Shift+Tab, Enter/Spatie) en levert zelf een Semantics-node met expanded-vlag als Flutter-web-equivalent van aria-expanded. De leesbare tekst blijft ongewijzigd direct zichtbaar. Bij readableFields.isEmpty (fallback-pad) blijft de ruwe JSON ongewijzigd direct zichtbaar zonder toggle. ClassificationBadge, het foutreden-blok bij FAILED en de 'Samenvatting voor jou'-kaart blijven ongewijzigd. Schrijf hierbij ook alle bijbehorende widgettests: toetsenbordnavigatie (tester.sendKeyEvent op de toggle), Semantics/expanded-verificatie, functionele gelijkheid van de uitgeklapte tekst met de originele contentJson, en een fallback-regressietest. Pas bestaande tests (iteration_readable_artifact_fields_test.dart, iteration_session_dialog_classification_badge_test.dart, iteration_session_error_message_test.dart) aan waar nodig zodat ze eerst de toggle activeren voordat ze de ruwe JSON verwachten. Formatteer gewijzigde code met dart format.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## product-85 - 'Toon technische details'-toggle per agentrol implementeren

Stappenplan:
[x]: dashboard-frontend/lib/main.dart - artefact-ExpansionTile-builder (rond regel 990-1018)
     wijzigen zodat de ruwe-JSON-SelectableText bij readableFields.isNotEmpty achter een nieuwe,
     standaard ingeklapte toggle staat; bij readableFields.isEmpty blijft de ruwe JSON ongewijzigd
     direct zichtbaar.
[x]: nieuwe herbruikbare widget `TechnicalDetailsToggle` toevoegen (StatefulWidget) met een
     `InkWell` (toetsenbordbereikbaar/-activeerbaar via Tab/Shift+Tab en Enter/Spatie) en een
     `MergeSemantics(child: Semantics(expanded: ..., button: true, label: 'Toon technische
     details', ...))`, zodat de in-/uitgeklapte status semantisch communiceert via een
     `expanded`-vlag (Flutter-web-equivalent van aria-expanded).
[x]: widgettests toegevoegd in test/iteration_technical_details_toggle_test.dart: AC1 (raw JSON
     standaard verborgen achter de toggle), AC2 (toetsenbordnavigatie: Tab bereikt de toggle,
     Enter/Spatie activeert/deactiveert), AC3 (Semantics-tree-inspectie op de expanded-vlag), AC4
     (functionele gelijkheid van gedecodeerde uitgeklapte tekst met de brondata) en AC5
     (fallback-pad readableFields.isEmpty toont ongewijzigd direct de ruwe JSON, zonder toggle).
[x]: bestaande tests in test/iteration_readable_artifact_fields_test.dart aangepast: de plekken
     die ruwe-JSON-tekst verwachtten bij readableFields.isNotEmpty klappen nu eerst de nieuwe
     toggle uit (met `tester.ensureVisible` + een expliciete `pump()`, omdat de dialoog een
     `SingleChildScrollView` gebruikt en de toggle anders buiten het scrollbare kijkvenster valt
     voor `tester.tap`).
[x]: `dart format` gedraaid op de gewijzigde bestanden (lib/main.dart bleef ongewijzigd door
     format, geen onnodige diff).
[x]: `flutter analyze` (geen issues) en `flutter test` (alle 104 tests groen) gedraaid vanuit
     dashboard-frontend/.
[x]: `mvn -B --no-transfer-progress clean verify` vanuit de repo-root gedraaid (BUILD SUCCESS, 0
     failures/errors) ter borging van het volledige vangnet, ook al raakt deze story alleen de
     Flutter-frontend.

Toelichting:
- `ExpansionTile` uit de Flutter Material-library zet zelf GEEN `Semantics(expanded: ...)` op de
  gerenderde node (geverifieerd in de Flutter-SDK-bron); daarom is voor AC3 een eigen
  `TechnicalDetailsToggle`-widget gebouwd i.p.v. een geneste `ExpansionTile` (zoals de
  implementatie-aanwijzing in de subtaak suggereerde), zodat het `expanded`-vlag daadwerkelijk in
  de Semantics-tree staat en testbaar is.
- `Semantics(..., mergeDescendantsIntoThisNode: true, ...)` bestaat niet in de Flutter-versie van
  dit project; `MergeSemantics(child: Semantics(...))` is het equivalent en samengevoegde
  focus-/knopsemantiek van de onderliggende `InkWell` komt zo in dezelfde node terecht als de
  `expanded`-vlag.
- `readableFields.isEmpty`-pad (fallback) blijft ongewijzigd: geen toggle, ruwe JSON direct
  zichtbaar, gedekt door een aparte AC5-test.

## product-86 - Story-brede test

Stappenplan:
[x]: .task.md, docs/factory en agent-tips gelezen.
[x]: diff geïnspecteerd (`git diff main...HEAD` op `dashboard-frontend/lib/main.dart`): matcht de
     scope-beschrijving (readableFields.isNotEmpty -> TechnicalDetailsToggle, isEmpty -> ongewijzigd
     direct zichtbaar).
[x]: `flutter analyze` in dashboard-frontend: "No issues found!".
[x]: `flutter test` in dashboard-frontend: alle 104 tests groen, exit code 0 (incl. de nieuwe
     `test/iteration_technical_details_toggle_test.dart` met AC1-AC5-dekking, keyboard-navigatie via
     `tester.sendKeyEvent`, Semantics-expanded-inspectie en jsonDecode-vergelijking tegen de
     brondata).
[x]: `mvn -B --no-transfer-progress clean verify` niet opnieuw gedraaid: diff raakt uitsluitend
     `dashboard-frontend/` en de worklog, wat geen van beide een `pathPrefixes`-match geeft voor de
     `repository-maven-verify`-command in `.factory/verification.yaml`.
[x]: preview-omgeving (`https://product-factory-pr-49.vdzonsoftware.nl`) gesmoketest: frontend en
     `/actuator/health` beide HTTP 200. Geen browsertool beschikbaar in de agentcontainer, dus
     interactieve/toetsenbord-/screenshotverificatie in de preview was niet mogelijk (bekende
     beperking, zie agent-tip `dashboard-frontend-preview-now-available`); leunt op de
     widgettests hierboven voor AC2/AC3.

Conclusie: alle acceptatiecriteria (AC1-AC7) zijn gedekt door geautomatiseerde tests die slagen; het
volledige voorgeschreven vangnet (flutter analyze + flutter test) gaf exitcode 0 zonder failures.
Goedgekeurd.
