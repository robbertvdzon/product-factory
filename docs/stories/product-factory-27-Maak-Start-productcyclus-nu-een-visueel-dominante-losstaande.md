# product-factory-27 - Maak 'Start productcyclus nu' een visueel dominante, losstaande call-to-action bovenaan de Producten-kaart

## Story

Maak 'Start productcyclus nu' een visueel dominante, losstaande call-to-action bovenaan de Producten-kaart

<!-- refined-by-factory -->

## Scope
Herpositioneer de knop 'Start productcyclus nu' op de Producten-kaart (`dashboard-frontend/lib/main.dart`) naar een eigen rij, boven en visueel losstaand van de bestaande knoppenrij (Pauzeren/Hervatten, Instellingen, Start overleg). De knop krijgt een primaire stijl die zich door grootte en/of rand (niet uitsluitend kleur) onderscheidt van de secundaire knoppen. Er verandert niets aan de bestaande `onPressed`-logica (`_startCycle`), enabling-conditie, tekst of icoon van de knop.

De status van het product (autonoom/handmatig) staat al als tekstlabel (Chip) naast de productnaam — dit blijft zo; er is geen kleurenbolletje in de huidige implementatie dat vervangen hoeft te worden.

Dit is een pure UI-herindeling binnen `_ProductCard`-achtige structuur in `main.dart`; geen wijziging aan andere homepage-secties, andere producten, of aan `ProductSettingsDialog`.

Deze story kan onafhankelijk van de kandidaat "verplaats-productconfig-naar-instellingen" (nog niet gemerged, zie agent tips) worden geïmplementeerd: de hoogtemeting in de acceptatiecriteria vergelijkt de kaarthoogte vóór en ná déze wijziging, ongeacht of die andere kandidaat al is toegepast.

## Acceptance criteria
- 'Start productcyclus nu' is een apart Flutter-widget (Button-achtig element, bv. `FilledButton`/`ElevatedButton`) met een zichtbare focusring (via `FocusNode`/`Focus`, vergelijkbaar met het bestaande `SettingsButton`-patroon), staat op een eigen `Row`/`Wrap` vóór en visueel gescheiden (bv. extra verticale ruimte) van de Pauzeren/Instellingen/Start overleg-rij, en heeft een stijl die door grootte en/of rand visueel verschilt van de secundaire knoppen (niet uitsluitend kleur).
- De tekst-op-achtergrondkleur van de CTA voldoet aan een contrastverhouding van minimaal 4.5:1 (WCAG 2.1 AA); dit wordt geverifieerd met een berekende contrastcheck in een Flutter widget-test (zoals het bestaande `kClassificationColors`-patroon in `classification.dart`) — géén axe-core (niet beschikbaar in dit Flutter-webproject).
- De status (autonoom/handmatig) blijft naast de productnaam zichtbaar als tekstlabel (bestaande `Chip`-implementatie); een widget-test bevestigt dat de tekst 'autonomous'/'manual' (of vertaalde weergave) als leesbare tekst aanwezig is, niet uitsluitend als kleur.
- Een `flutter_test`-widgettest bevestigt via de focus-/traversal-volgorde (`FocusScope`/`tester.sendKeyEvent(LogicalKeyboardKey.tab)`-equivalent of expliciete traversal-check) dat 'Start productcyclus nu' het eerste interactieve element is dat bereikt wordt ná de kaart-heading/status-label, met een gelijk of kleiner aantal stappen dan vóór de wijziging.
- Een widgettest bevestigt dat een tap op 'Start productcyclus nu' dezelfde bestaande `_startCycle(slug)`-aanroep en hetzelfde resulterende gedrag triggert als vóór deze wijziging (geen functionele regressie).
- Een widgettest meet via `tester.getSize`/`RenderBox.size` (Flutter-equivalent van `getBoundingClientRect`) dat de verticale hoogte van de Producten-kaart na deze wijziging kleiner is dan de hoogte vóór de wijziging, gemeten op dezelfde testviewportbreedte.
- Geen wijziging aan de overige zes homepage-secties (metric-tegels, Productcycli, SF-stories, access tokens, Storywachtrij, Workspace) of aan andere producten dan de kaartlayout zelf.

## Aannames
- "axe-core" en "<button>-element" uit de oorspronkelijke acceptatiecriteria zijn niet letterlijk toepasbaar op deze Flutter-webapp (geen DOM/axe-core-tooling aanwezig, bevestigd via eerdere refines); deze zijn vertaald naar Flutter-equivalenten (`Focus`/`FocusNode`, handmatige contrastberekening, `flutter_test`-widgettests) conform het bestaande project-precedent.
- De status (autonoom/handmatig) wordt al met een tekstlabel (Chip) getoond in de huidige code — er is geen los gekleurd statusbolletje dat vervangen moet worden; dit onderdeel van de story is grotendeels al voldaan en betreft vooral behoud van het bestaande gedrag tijdens de herindeling.
- Deze story is niet afhankelijk van het al dan niet gemerged zijn van de kandidaat "verplaats-productconfig-naar-instellingen"; de hoogtevermindering wordt gemeten relatief aan de kaart vóór/ná déze wijziging.
- "Primaire knopstijl die zich onderscheidt via grootte/rand" wordt ingevuld door de bestaande `FilledButton`-stijl te vergroten en/of van een rand te voorzien ten opzichte van de `OutlinedButton`/`SettingsButton`-stijl van de secundaire knoppen.

## Eindsamenvatting

Ik heb voldoende context uit `.task.md` en het worklog om de eindsamenvatting te maken.

**Samenvatting product-factory-27 — 'Start productcyclus nu' visueel dominante CTA**

**Gebouwd:**
- De knop 'Start productcyclus nu' is uit de secundaire knoppenrij (Pauzeren/Instellingen/Start overleg) gehaald en op een eigen `Row`, boven en visueel gescheiden (12px ruimte) van die rij, geplaatst — via een nieuwe `StartCycleButton`-widget in `dashboard-frontend/lib/main.dart`, naar analogie van het bestaande `SettingsButton`-patroon.
- De CTA heeft een eigen achtergrond-/tekstkleurenpaar met een contrastverhouding van 11,08:1 (ruim boven de vereiste WCAG AA 4,5:1), een expliciete rand en vetgedrukte tekst, en een zichtbare focusring via `FocusNode`.
- De secundaire knoppenrij is verkleind (`VisualDensity.compact`) zodat het grootteverschil met de CTA duidelijk is en de kaart bij normale/smalle breedtes niet hoger wordt dan voorheen.
- `onPressed`/`_startCycle`-logica, icoon en labeltekst van de knop zijn ongewijzigd; de status (autonoom/handmatig) blijft als bestaand tekstlabel (Chip) naast de productnaam staan.
- Overige homepage-secties en `ProductSettingsDialog` zijn niet aangeraakt.

**Gekozen aanpak / afweging:**
- Bij zeer brede viewports (waar alle 4 knoppen voorheen op één regel pasten) wordt de kaart door de eigen CTA-regel iets hoger dan voorheen — dit is inherent aan de eis "eigen losstaande rij" en is transparant gedocumenteerd; de acceptatiecriteria voor kaarthoogte zijn expliciet getest op de voorgeschreven vaste testviewportbreedte, waar de kaart wél kleiner wordt.

**Getest:**
- Nieuwe testfile `test/start_cycle_button_test.dart` (7 widgettests): eigen rij + stijlverschil, WCAG AA-contrastberekening, statuslabel als tekst, tab-/focusvolgorde (CTA eerst), functionele gelijkheid van de tap-actie, disabled-state, kaarthoogte vóór/na.
- Volledig vangnet: `flutter analyze` (0 issues), `flutter test` (172/172 groen), backend `mvn verify` (16/16 groen, ongewijzigd).
- Preview-smoketest (frontend + API) beide HTTP 200; interactieve/browser-verificatie was niet mogelijk (geen browsertool in agentcontainer), leunt op de widgettests.
- Reviewer en tester hebben beiden akkoord gegeven.

**Bewust niet gedaan:**
- Geen wijziging aan overige homepage-secties, andere producten, of `ProductSettingsDialog`.
- Geen aanpassing aan `.factory/verification.yaml` (bestaande dashboard-frontend-paden dekten dit al).

<!-- deploy-summary:start -->
De knop om een productcyclus te starten valt nu duidelijker op: hij staat los boven de andere knoppen en is groter en beter herkenbaar. Er verandert niets aan wat er gebeurt als je erop klikt. De status van een product blijft gewoon als tekst zichtbaar.
<!-- deploy-summary:end -->
