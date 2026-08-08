# product-factory-4 - Voeg toegankelijk inline uitklappaneel met scope-disclaimer toe bij activeren van de uitkomstbadge

## Story

Voeg toegankelijk inline uitklappaneel met scope-disclaimer toe bij activeren van de uitkomstbadge

<!-- refined-by-factory -->

## Samenvatting
Elke uitkomstbadge in de productcyclus-lijst wordt klikbaar en met het toetsenbord bedienbaar. Bij activeren klapt er direct onder de rij een klein tekstpaneel open dat uitlegt dat de badge alleen "wat" toont en niet "waarom". Dit is geen nieuw pop-upvenster, maar een uitklapelement binnen de bestaande rij — eenvoudiger en zonder afhankelijkheid van een nieuwe, ongeteste dialoogcomponent. Nogmaals klikken (of Escape) klapt het weer dicht.

## Scope
- Uitbreiding van `ClassificationBadge` (dashboard-frontend/lib/classification.dart) en het gebruik ervan in de iteratierij (main.dart, `_limitedSection('iterations', ...)`).
- Geen nieuwe route, geen wijziging aan authenticatie of PR-flow, geen wijziging aan de bestaande detaildialoog (`IterationSessionDialog`).
- Vooronderzoek (te documenteren in PR-/commitbeschrijving): het project gebruikt al `AlertDialog` als herbruikbaar modaal patroon (o.a. `IterationSessionDialog`, `AddProductDialog`, `ProductSettingsDialog`, `_completeHumanAction`). Deze story kiest bewust toch voor een inline disclosure-paneel in plaats van (weer) een `AlertDialog`, om het scope-signaal niet te koppelen aan het zwaardere detaildialoog en om geen focus-trap/`role="dialog"`-semantiek te introduceren die hier niet nodig is.
- Vertaling van de gevraagde ARIA/DOM-termen naar deze Flutter-webapp (er is geen los HTML/DOM-laag om te toetsen, geen axe-core in het project):
  - "aria-expanded" → `Semantics(expanded: true/false, ...)` op het badge-element.
  - "role=status" / "aria-label" → bestaande `Semantics(label: ...)`-patroon, aangevuld met de volledige categorienaam.
  - "role=dialog"/"aria-modal" (afwezigheid) → het inline paneel gebruikt géén `showDialog`/`AlertDialog`/`Dialog`, maar een gewoon `Widget` in de bestaande widgetboom.
  - "Tab/Enter/Space" → een `Focus`-widget (of `FocusableActionDetector`) met `onKeyEvent` voor Enter/Space, plus standaard tab-traversal.
  - "Escape" en focusherstel → `Focus`-widget vangt Escape op, klapt paneel in en roept `.requestFocus()` aan op de eigen `FocusNode`.
  - "geautomatiseerde test" → `flutter_test` widgettests (`testWidgets`, `tester.sendKeyEvent`, Semantics-tree assertions), analoog aan het bestaande `test/classification_test.dart`.
- De rij (`ListTile`) heeft al een eigen `onTap` die het detaildialoog opent; het badge moet zijn eigen tap/keyboard-activatie afhandelen zonder dat de rij-`onTap` daarbij ook triggert (bv. via een eigen `GestureDetector`/`InkWell` op het badge, dat de tap consumeert).

## Acceptance criteria
- Vóór implementatie documenteert de developer in de PR-/commitbeschrijving dat `AlertDialog` al het bestaande herbruikbare modale patroon is in dit project, en motiveert waarom desondanks voor het inline disclosure-patroon wordt gekozen (conform Scope hierboven).
- Elk van de vijf badge-varianten (`onderzoek-onvoldoende`, `guardrail-conflict`, `richting-gekozen`, `richting-verworpen`, `niet-classificeerbaar`) is bereikbaar via toetsenbordfocus (Tab) en activeerbaar via Enter of Space; geverifieerd met een `flutter_test`-widgettest die uitsluitend toetsenbordinvoer simuleert (geen taps).
- Activeren van een badge zet `Semantics(expanded: ...)` van `false` naar `true` en toont het inline paneel binnen dezelfde rij (geen `showDialog`/`AlertDialog`/`Dialog`, geen focus-trap); een widgettest controleert deze expanded-toggle en dat er geen dialoogwidget in de boom verschijnt.
- De tekst van het geopende paneel bevat voor alle vijf varianten de exacte zin "Dit toont wat de uitkomst was, niet waarom."; een widgettest controleert deze tekst na activatie van elke variant.
- Nogmaals activeren van hetzelfde badge, of Escape, klapt het paneel weer in (`expanded` terug naar `false`) en de focus staat op/keert terug naar het badge-element; een widgettest controleert dat de focus na Escape op het badge-element ligt.
- Elk badge-element behoudt een Semantics-label met de volledige categorienaam (niet uitsluitend kleur/tekst-slug); een widgettest controleert dit label voor elk van de vijf varianten.
- Het inline paneel bevat geen link of verwijzing naar een externe iteratielog-route; een widgettest controleert de afwezigheid daarvan.
- Bestaande tests (o.a. `test/classification_test.dart`) en het klikgedrag van de rij (opent nog steeds het detaildialoog via een klik buiten het badge) blijven werken.

## Aannames
- "aria-expanded", "role=status/dialog", "aria-modal", "document.activeElement" uit de oorspronkelijke acceptatiecriteria zijn Flutter-web-vertalingen zoals hierboven beschreven (`Semantics(expanded/label)`, `Focus`/`FocusNode`), omdat dit project geen raw-DOM/ARIA-testtooling (bv. axe-core) heeft.
- Het reeds bestaande `AlertDialog`-patroon telt als "herbruikbare modale dialoogcomponent"; de keuze voor inline disclosure blijft staan, zoals in de oorspronkelijke story gemotiveerd.
- De scope-disclaimerzin is exact "Dit toont wat de uitkomst was, niet waarom." voor alle vijf varianten (geen variatie per categorie).
- Alleen de vijf badge-varianten in de iteratielijst worden aangepast; de `IterationProgressIndicator` (voor lopende iteraties, geen badge) blijft ongewijzigd.

## Eindsamenvatting

I have enough context to write the final summary now.

## Eindsamenvatting — product-factory-4: Inline uitklappaneel met scope-disclaimer bij uitkomstbadge

**Gebouwd:** Elke uitkomstbadge in de iteratielijst (`ClassificationBadge`, `dashboard-frontend/lib/classification.dart`) is uitgebreid met een toetsenbord- en muis-bedienbaar inline uitklappaneel. Bij activeren (klik, of Enter/Space na Tab-focus) klapt direct onder de badge een tekstpaneel open met de vaste zin "Dit toont wat de uitkomst was, niet waarom." voor alle vijf varianten (onderzoek-onvoldoende, guardrail-conflict, richting-gekozen, richting-verworpen, niet-classificeerbaar). Nogmaals activeren of Escape klapt het weer dicht en herstelt de focus op de badge. Het badge bleef een gewoon widget binnen de bestaande rij (`main.dart` is ongewijzigd) — er is dus geen `AlertDialog`/`showDialog`/modaal dialoogvenster gebruikt.

**Belangrijkste keuze:** hoewel `AlertDialog` al het bestaande herbruikbare modale patroon in het project is (o.a. `IterationSessionDialog`), is bewust gekozen voor een lichter inline disclosure-paneel binnen dezelfde rij, om het scope-signaal niet te koppelen aan een zwaarder detaildialoog en geen onnodige focus-trap/dialoog-semantiek te introduceren. Deze motivatie staat vastgelegd als dartdoc bij `ClassificationBadge`.

**Getest:** 19 nieuwe widgettests (`classification_badge_disclosure_test.dart`) dekken per variant: Tab-bereikbaarheid en toetsenbord-only activatie, expanded-toggle zonder dialoogwidget in de boom, exacte paneeltekst, Escape met focusherstel, Semantics-label met volledige categorienaam, en afwezigheid van een externe link/route. Ook een regressietest dat een klik op de badge de rij-klik (die het detaildialoog opent) niet meer triggert, terwijl een klik ernaast dat nog wel doet. Alle 63 Flutter-tests (incl. bestaande suites) en `flutter analyze` (0 issues) slagen; backend-`mvn verify` slaagt eveneens (ongewijzigd door deze story). De preview-omgeving (PR #38) is bereikbaar (HTTP 200), maar interactieve toetsenbord-/visuele verificatie in de browser zelf kon niet — er is geen browsertool in de agentcontainer; dit steunt volledig op de widgettests.

**Bewust niet gedaan:** geen wijziging aan `main.dart`, geen wijziging aan `IterationSessionDialog` of andere bestaande modale dialogen, geen nieuwe dependencies of `pubspec.lock`-wijziging. Een klein visueel aandachtspunt (rijhoogte groeit binnen dezelfde `Wrap` bij expansie) is genoteerd door de reviewer als niet-blokkerend en buiten scope voor een latere UX-pass.

<!-- deploy-summary:start -->
Als je op een uitkomstbadge (bijvoorbeeld "richting-gekozen" of "guardrail-conflict") klikt of er met het toetsenbord naartoe navigeert en op Enter/Spatie drukt, verschijnt er nu direct een kort tekstje dat uitlegt dat de badge alleen laat zien wát er is gebeurd, niet waarom. Klik nogmaals of druk op Escape om dit tekstje weer te sluiten. Er verandert verder niets aan hoe je de rest van de pagina gebruikt.
<!-- deploy-summary:end -->
