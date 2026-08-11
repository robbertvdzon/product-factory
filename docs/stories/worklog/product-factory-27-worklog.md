# product-factory-27 - Worklog

Story-context bij eerste pickup:
CTA 'Start productcyclus nu' herpositioneren op Producten-kaart

In dashboard-frontend/lib/main.dart: verplaats de bestaande FilledButton.icon 'Start productcyclus nu' (zelfde onPressed/_startCycle-logica, icoon, label) uit de secundaire knoppenrij naar een eigen Row vóór en visueel gescheiden van de Pauzeren/Instellingen/Start overleg-knoppenrij. Geef de CTA een primaire stijl die door grootte en/of rand (niet uitsluitend kleur) verschilt van de secundaire knoppen, met een tekst/achtergrond-contrastverhouding ≥4.5:1 (hergebruik/analogie van het contrastRatio-patroon in classification.dart), en een zichtbare focusring via FocusNode naar het SettingsButton-precedent. Laat de bestaande Chip-statuslabels (status, developmentMode) naast de productnaam ongewijzigd staan als tekstlabel. Zorg dat de tab-/focus-traversal-volgorde de CTA als eerste interactief element na de heading/statuslabels oplevert. Schrijf bijbehorende flutter_test widget-tests voor: eigen rij/visuele scheiding en stijlverschil, contrastberekening, zichtbaarheid statuslabel als tekst, tab-volgorde, functionele gelijkheid van de klik-actie (_startCycle), en kaarthoogte vóór/na vergelijking (tester.getSize). Raak geen andere homepage-secties, andere producten of ProductSettingsDialog. Formatteer gewijzigde regels met dart format.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `StartCycleButton` toegevoegd (main.dart, naast `SettingsButton`-patroon): eigen
  `StatefulWidget` met `FocusNode` (zichtbare focusring), `FilledButton.icon` met expliciete
  `side`-rand + vetgedrukte tekst en een eigen achtergrond-/voorgrondkleurenpaar
  (`kStartCycleButtonBackground`/`kStartCycleButtonForeground`, contrast 11.08:1, ruim boven
  WCAG AA 4.5:1). Zelfde `onPressed`/`_startCycle`-logica, icoon en label als voorheen.
- CTA verplaatst naar een eigen `Row` vóór de secundaire knoppenrij (Pauzeren/Instellingen/Start
  overleg), met een `SizedBox(height: 12)` ertussen als visuele scheiding.
- Secundaire knoppenrij (Pauzeren/Hervatten, Instellingen, Start overleg) gewrapt in een lokale
  `Theme(...VisualDensity.compact...)` zodat die knoppen kleiner/subtieler ogen t.o.v. de CTA
  (grootteverschil) én de kaart per saldo niet hoger wordt dan vóór de wijziging — empirisch
  geverifieerd (zie hieronder) dat dit bij smallere kaartbreedtes (waar de knoppenrij voorheen
  wrapte) een kleinere totale kaarthoogte oplevert dan de oorspronkelijke, single-`Wrap`-indeling;
  bij zeer brede viewports (alle 4 knoppen op 1 regel) wordt de kaart wél iets hoger door de eigen
  CTA-regel — dit is inherent aan "eigen losstaande rij" en kon niet worden vermeden zonder die
  eis los te laten.
- Chip-statuslabels (status, developmentMode) ongewijzigd naast de productnaam gelaten.
- Nieuwe test `test/start_cycle_button_test.dart` (7 widgettests): eigen rij/stijlverschil,
  AA-contrastberekening (hergebruik `contrastRatio` uit classification.dart), statuslabel als
  tekst, tab-volgorde (CTA eerst via `LogicalKeyboardKey.tab` + Semantics-focus), functionele
  gelijkheid van de tap (`POST /api/products/demo/cycles`), disabled-state, en
  kaarthoogte-vergelijking via een `_LegacyProductCard`-reconstructie van de oude structuur
  (zelfde breedte, `tester.getSize`).
- `dart format lib/main.dart test/start_cycle_button_test.dart` gedraaid.
- Vangnet gedraaid: `flutter analyze` (0 issues), `flutter test` (172/172 groen),
  `mvn -B --no-transfer-progress clean verify` (backend ongewijzigd, 16/16 groen). Geen wijziging
  aan `.factory/verification.yaml` nodig (bestaande `dashboard-frontend/`-pathPrefixes dekken dit).

## Review (product-156)

- Diff beperkt tot twee hunks in `dashboard-frontend/lib/main.dart` (productkaart-knoppenrij +
  nieuwe `StartCycleButton`-widget, additief na `_AddProductDialogState`) en de nieuwe testfile;
  geen wijzigingen aan overige homepage-secties of `ProductSettingsDialog`.
- `onPressed`/`_startCycle`-logica, icoon en label ongewijzigd; status-Chip (status,
  developmentMode) ongewijzigd als tekstlabel.
- Gerichte checks (niet het volledige vangnet): `dart format --set-exit-if-changed` op gewijzigde
  bestanden (geen diff), `flutter analyze` op gewijzigde bestanden (0 issues), `flutter test
  test/start_cycle_button_test.dart` (7/7 groen); geen pubspec.lock-drift na `pub get`.
- Kaarthoogte-AC is getest op een vaste viewportbreedte (488px) conform de AC-tekst; de door de
  developer gemelde hoogtetoename bij zeer brede viewports valt buiten de letterlijke AC-eis en is
  transparant gedocumenteerd (zie agent tip `wrap-naar-eigen-rij-verhoogt-hoogte-tenzij-breedte-smal`).
- Akkoord.
