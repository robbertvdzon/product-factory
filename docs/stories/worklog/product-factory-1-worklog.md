# product-factory-1 - Worklog

Story-context bij eerste pickup:
Classificatiebadge toevoegen aan iteratierij

Voeg in dashboard-frontend/lib/main.dart een pure mapping-functie toe die op basis van status/criticVerdict (en evt. errorMessage) van een iteratie exact één van de vier waarden bepaalt (onderzoek-onvoldoende, guardrail-conflict, richting-gekozen, richting-verworpen), met expliciet getest fallback-gedrag voor niet-ondubbelzinnige/lopende iteraties (verifieer de mapping tegen Contracts.kt/ShadowIterationEngine.kt/PreviewDataSeeder.kt). Bouw een badge-widget die deze tekst toont met WCAG 2.1 AA-contrast (≥4.5:1) per van de vier vaste kleurvarianten en de tekst als programmatisch leesbaar (Semantics) label blootstelt, niet uitsluitend via kleur. Integreer de badge additioneel in de bestaande ListTile van de iteratierij (sectie 'Productcycli en onderzoekssessies') zonder subtitle, trailing-icoon, onTap of de workspace-publicatie-weergave in het detaildialoog (_showIteration) te wijzigen. Schrijf als onderdeel van dit werk: unit tests voor de mapping-functie (alle vier gevallen + fallback), een widgettest die per iteratierij precies één toegestane badge-tekst bevestigt inclusief Semantics-inspectie, een unit test die het contrastratio van elk kleurpaar tegen de AA-drempel toetst, en een regressietest die de huidige inhoud/positie van de workspace-publicatie-weergave als baseline vastlegt. Zorg dat bestaande widget-/integratietests voor het overzicht ongewijzigd blijven slagen; formatteer gewijzigde code met dart format.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Mapping-logica en kleuren/badge-widget toegevoegd in nieuw bestand `dashboard-frontend/lib/classification.dart`
  (zelfde patroon als `formatting.dart`/`limited_list.dart`: pure logica in een losse library, apart
  unit-testbaar). `classifyIterationOutcome` mapt `status` (geverifieerd tegen
  `ShadowIterationApi.kt`/`ShadowIterationEngine.kt`/`PreviewDataSeeder.kt`) naar exact één van de vier
  vaste waarden: `ACCEPTED`→`richting-gekozen`, `NEEDS_REVISION`→`onderzoek-onvoldoende`,
  `REJECTED`→`richting-verworpen`, `FAILED`→`guardrail-conflict`; alle overige/onbekende statussen
  (o.a. `QUEUED`/`RUNNING`) vallen expliciet en getest terug op `onderzoek-onvoldoende`.
  `ClassificationBadge` toont de tekst zichtbaar én via een Semantics-label (`ExcludeSemantics` op de
  onderliggende `Text` voorkomt een dubbel accessibility-label).
- Kleurenparen per variant halen alle vier minimaal WCAG 2.1 AA (4.5:1); contrastberekening en -test
  zitten in `classification.dart`/`test/classification_test.dart` (ratio's liggen tussen 6.98:1 en
  8.88:1).
- Badge additief in de iteratierij van `main.dart` geïntegreerd: `title` werd een `Wrap` met de
  bestaande titeltekst plus de nieuwe badge; subtitle, trailing-icoon en onTap zijn ongewijzigd.
  Diff van `main.dart` beperkt tot de import en dit blok (bestaande niet-geformatteerde regels
  elders blijven met rust, conform de bekende `main.dart`-valkuil).
- Nieuwe tests: `test/classification_test.dart` (mapping alle vier gevallen + fallback, contrastratio
  per kleurpaar), `test/classification_badge_widget_test.dart` (widgettest die per rij precies één
  toegestane badge-tekst bevestigt + Semantics-inspectie), en
  `test/iteration_workspace_publication_regression_test.dart` (baseline-regressietest die de
  workspace-publicatie-weergave in het detaildialoog met een fake, niet-netwerkende `DashboardApi`-
  subclass rendert en zowel inhoud als positie (na 'Voortgang') vastlegt).
- Vangnet gedraaid en groen: `mvn -B --no-transfer-progress clean verify` (BUILD SUCCESS, alle 5
  modules), `flutter analyze` (geen issues) en `flutter test` (40/40 geslaagd, inclusief de 3 nieuwe
  testbestanden en de bestaande widget-/integratietests ongewijzigd groen).
- `.factory/verification.yaml` was al correct (geen wijziging nodig): alle vier verplichte commando's
  staan er al met stabiele id's, argv zonder shell, bestaande relatieve working directories en
  begrensde timeouts.
