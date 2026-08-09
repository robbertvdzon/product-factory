# product-factory-15 - Verplaats de ruwe JSON-weergave per agentrol achter een toegankelijke, standaard ingeklapte 'Toon technische details'-toggle

## Story

Verplaats de ruwe JSON-weergave per agentrol achter een toegankelijke, standaard ingeklapte 'Toon technische details'-toggle

<!-- refined-by-factory -->

## Samenvatting
In het detailscherm van een iteratie zie je per agentrol nu meteen de ruwe, technische JSON-data staan, ook al is daar al een leesbare tekstversie van. Dat maakt het scherm rommelig voor wie gewoon de resultaten wil lezen. Deze wijziging verbergt die ruwe JSON standaard achter een knop 'Toon technische details', die je met muis, toetsenbord en schermlezer kunt bedienen. Bij artefacten waar nog geen leesbare tekst voor bestaat, blijft de ruwe data gewoon meteen zichtbaar, zodat niemand data kwijtraakt.

## Scope
- Betreft uitsluitend `dashboard-frontend/lib/main.dart`, het `ExpansionTile`-blok per agentrol-artefact rond regel 990-1018 (binnen `IterationSessionDialog`), waar `readableFields` en de ruwe-JSON `SelectableText` (`_prettyJson`) naast elkaar getoond worden.
- Wanneer `readableFields.isNotEmpty` (leesbare tekst aanwezig): de bestaande ruwe-JSON-weergave (`SelectableText(_prettyJson(...))`) wordt verplaatst in een nieuwe, standaard ingeklapte, toetsenbord- en schermlezerbedienbare toggle-sectie met zichtbaar label 'Toon technische details'. De leesbare tekst (`readableFields`) blijft zoals nu direct zichtbaar, buiten de toggle.
- Wanneer `readableFields.isEmpty` (fallback-pad, geen herkende leesbare velden): de ruwe JSON blijft ongewijzigd direct zichtbaar, zonder toggle.
- Buiten scope: het "Volledig productdossier"-`ExpansionTile` (regel 970-983), de rest van de dialoog, en de backend/API blijven ongewijzigd.
- De toggle is een geneste, onafhankelijke component binnen de bestaande rol-`ExpansionTile` (die zelf al in-/uitklapt); beide staten (rol-tile en technische-details-toggle) moeten onafhankelijk van elkaar werken.

## Acceptance criteria
1. Binnen elke rol-`ExpansionTile` met leesbare tekst (`readableFields.isNotEmpty`) staat de ruwe JSON niet langer direct zichtbaar, maar in een nieuwe, standaard ingeklapte subsectie met zichtbaar label 'Toon technische details'.
2. De 'Toon technische details'-toggle is volledig bereikbaar en bedienbaar met alleen het toetsenbord (Tab/Shift+Tab bereikt de toggle, Enter of Spatie activeert/deactiveert), aangetoond met een geautomatiseerde `flutter_test`-widgettest die toetsenbordinteractie simuleert (`tester.sendKeyEvent`, focus-verplaatsing).
3. De in-/uitgeklapte status van de toggle wordt semantisch communiceert via een Flutter-`Semantics`-node met `expanded`-vlag (het Flutter-webequivalent van `aria-expanded`), aangetoond met een test die de `Semantics`-tree inspecteert.
4. Na het uitklappen is de getoonde technische-detailtekst, na JSON-decodering, functioneel gelijk aan de oorspronkelijke `contentJson` van dat artefact; een geautomatiseerde test vergelijkt de gedecodeerde weergave met de brondata uit de (gemockte) API-respons.
5. Voor artefacten zonder herkende leesbare velden (`readableFields.isEmpty`, bestaand fallback-pad) blijft de ruwe JSON ongewijzigd direct zichtbaar, zonder extra toggle-klik; een test dekt dit af zodat regressie hierop opvalt.
6. De bestaande classificatiebadge (`ClassificationBadge`), het foutreden-blok bij status FAILED, en de 'Samenvatting voor jou'-kaart blijven ongewijzigd zichtbaar en functioneel na deze wijziging; bestaande tests hiervoor (of een equivalente regressietest) blijven slagen.
7. Bestaande tests in `dashboard-frontend/test/iteration_readable_artifact_fields_test.dart` en andere iteratie-dialoogtests blijven slagen of worden aangepast aan de nieuwe structuur zonder functioneel gedrag te wijzigen buiten wat in deze story beschreven staat.

## Aannames
- 'Toon technische details' is een label dat niet verandert bij in-/uitklappen (analoog aan het bestaande `ExpansionTile`-patroon in dit bestand, waar de titel ook statisch blijft); alleen de expanded-status verandert semantisch (aria-expanded-equivalent).
- Als Flutter-widget voor de toggle wordt een geneste `ExpansionTile` (of vergelijkbaar toggle-patroon met `Semantics(expanded: ...)`, zie precedent `IterationProgressIndicator` in main.dart) gebruikt, consistent met de rest van het bestand; de exacte widgetkeuze is een implementatiedetail van de developer.
- "Geautomatiseerde AX-tree-inspectie van de Flutter-webbuild" wordt ingevuld met `flutter_test`-widgettests die de `Semantics`-tree van de gerenderde widget inspecteren (zoals ook elders in dit project gebeurt), niet met browser-DOM-/axe-core-tooling, aangezien dat laatste niet beschikbaar is in dit project (bevestigd in agent-tips).
- Deze story wijzigt geen backend- of API-gedrag; alle benodigde data (`contentJson`, `readableFields`) is al beschikbaar via de bestaande implementatie uit de vorige story.

## Eindsamenvatting

Ik heb genoeg context. Ik schrijf nu de eindsamenvatting.

## Eindsamenvatting — product-factory-15: 'Toon technische details'-toggle

**Gebouwd:**
In `dashboard-frontend/lib/main.dart` (`IterationSessionDialog`, artefact-blok per agentrol) is de ruwe JSON-weergave verplaatst achter een nieuwe, standaard ingeklapte toggle met label "Toon technische details" — maar alleen bij artefacten waar al leesbare tekst (`readableFields`) bestaat. Bij artefacten zonder leesbare velden (fallback-pad) blijft de ruwe JSON zoals voorheen direct zichtbaar, zonder toggle.

**Belangrijke keuze:**
De ontwikkelaar week af van het gesuggereerde patroon (geneste `ExpansionTile`) omdat Flutters standaard `ExpansionTile` geen `Semantics(expanded: ...)`-node op de gerenderde widget zet — daardoor zou aria-expanded-gedrag niet echt testbaar/aanwezig zijn. In plaats daarvan is een eigen herbruikbare widget `TechnicalDetailsToggle` gebouwd (`InkWell` + `MergeSemantics(child: Semantics(expanded: ..., button: true, label: 'Toon technische details'))`), die wél toetsenbordbedienbaar is (Tab/Shift+Tab, Enter/Spatie) en de expanded-status correct in de Semantics-tree communiceert.

**Getest:**
- Nieuwe testfile `test/iteration_technical_details_toggle_test.dart` (216 regels) dekt AC1 t/m AC5: standaard verborgen ruwe JSON, toetsenbordnavigatie via `tester.sendKeyEvent`, Semantics-expanded-inspectie, functionele gelijkheid van uitgeklapte tekst met de originele `contentJson` (via `jsonDecode`-vergelijking), en het ongewijzigde fallback-pad.
- Bestaande test `iteration_readable_artifact_fields_test.dart` aangepast zodat deze eerst de toggle uitklapt voordat ruwe JSON wordt verwacht.
- `flutter analyze`: geen issues. `flutter test`: alle 104 tests groen.
- `mvn clean verify` is door de developer eenmalig volledig gedraaid (BUILD SUCCESS); de tester heeft dit bewust niet herhaald omdat de diff uitsluitend `dashboard-frontend/` en de worklog raakt, wat niet matcht met de Maven-verify-padprefixes in `.factory/verification.yaml`.
- Preview-omgeving gesmoketest (frontend + `/actuator/health` beide HTTP 200); interactieve/toetsenbord-verificatie in de browser was niet mogelijk (geen browsertool in de agentcontainer), maar wordt gedekt door de widgettests.

**Bewust niet gedaan:**
Geen wijzigingen aan het "Volledig productdossier"-blok, de rest van de dialoog, of backend/API — zoals afgebakend in de story-scope.

**Resultaat:** alle acceptatiecriteria (AC1–AC7) zijn gedekt door geslaagde geautomatiseerde tests. Goedgekeurd door de tester.

<!-- deploy-summary:start -->
In het detailscherm van een productiestap zie je nu standaard alleen nog de leesbare uitleg per onderdeel, in plaats van rommelige technische ruwe data. Wil je toch de technische details bekijken, dan klap je dat eenvoudig open met een knop, ook volledig met het toetsenbord te bedienen. Bij onderdelen waar nog geen leesbare uitleg voor bestaat, blijft de technische data gewoon meteen zichtbaar zodat je niets mist.
<!-- deploy-summary:end -->
