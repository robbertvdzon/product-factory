# product-factory-13 - Hergebruik de bestaande ClassificationBadge (met 'wat vs. waarom'-uitklap) in het detaildialoog in plaats van de kale rauwe-status-Chip

## Story

Hergebruik de bestaande ClassificationBadge (met 'wat vs. waarom'-uitklap) in het detaildialoog in plaats van de kale rauwe-status-Chip

<!-- refined-by-factory -->

## Samenvatting
In het overzicht van productcycli krijgt elke iteratie al een duidelijk gekleurd label (bijv. "richting-gekozen" of "guardrail-conflict") met een klikbare toelichting erbij. Klik je door naar de volledige details van een iteratie, dan zie je in plaats daarvan alleen de kale technische status (zoals "NEEDS_REVISION"), zonder die uitleg. Dat is verwarrend, want het lijkt andere informatie. Deze wijziging zorgt dat het detailscherm hetzelfde herkenbare label met toelichting toont als het overzicht, zodat een gebruiker overal dezelfde, begrijpelijke uitkomst ziet.

## Scope
- In `IterationSessionDialog` (`dashboard-frontend/lib/main.dart`, rond regel 836) wordt de bestaande `Chip(label: Text(status))` vervangen door de bestaande `ClassificationBadge`-widget uit `dashboard-frontend/lib/classification.dart`, gevoed met `classifyIterationOutcome(status: iteration['status'], criticVerdict: iteration['criticVerdict'], errorMessage: iteration['errorMessage'])` — exact dezelfde data die al in het dialoog beschikbaar is (geen nieuwe API-velden).
- Geen wijziging aan `classification.dart` zelf (geen nieuwe classificatielogica, geen nieuwe kleuren).
- De overige chips in dezelfde `Wrap` (delivery-mode, "bezig: rol") blijven ongewijzigd staan.
- De ruwe backend-statustekst (bv. "NEEDS_REVISION") mag niet meer los als `Chip` in de widget-tree van `IterationSessionDialog` staan; hij mag desgewenst nog decoratief in de subtitle/foutsectie voorkomen (dat blijft ongemoeid), maar niet meer als aparte statuschip.
- De `ClassificationBadge` moet het eerste interactieve (focusbare) element in het dialoog zijn na openen, vóór de secties "Voortgang" (agentstappen), agentresultaten en workspace-publicaties.

## Acceptance criteria
- Een Flutter widget-test mockt voor elk van de vijf classificatiewaarden (onderzoek-onvoldoende, guardrail-conflict, richting-gekozen, richting-verworpen, niet-classificeerbaar) een iteratie, rendert zowel de lijstkaart-rij als `IterationSessionDialog` voor dezelfde iteratiedata, en verifieert dat badge-tekst (`ClassificationBadge.classification`) en kleurenpaar (`kClassificationColors[...]`) op beide plekken identiek zijn.
- Een Flutter widget-test verifieert dat er in de widget-tree van `IterationSessionDialog` geen `Chip` meer voorkomt die de rauwe statuswaarde (bv. 'NEEDS_REVISION') als label toont.
- Een Flutter widget-test simuleert toetsenbordnavigatie (`tester.sendKeyEvent`, analoog aan het bestaande patroon in `classification_badge_disclosure_test.dart`) en toont aan dat de badge in het dialoog met Tab bereikbaar is en met Enter of Spatie de 'wat vs. waarom'-toelichting toggelt, net als op de lijstkaart.
  - *Vertaling t.o.v. issuetekst*: dit project heeft geen Playwright/axe-core-toolchain voor de Flutter-webbuild; de kandidaat-onderbouwing bevestigt dit al ("geautomatiseerde toetsenbordnavigatietest (bv. FocusTraversal-simulatie of Playwright...)" — de FocusTraversal/flutter_test-variant is hier het te implementeren pad).
- Een Flutter widget-test inspecteert via `tester.getSemantics(...)` de Semantics-boom van de badge in het dialoog en vergelijkt de aankondigingstekst (label + expanded-status) met die van de badge op de lijstkaart voor dezelfde classificatie — zelfde classificatienaam, zelfde hint dat activeren de uitleg toont.
  - *Vertaling t.o.v. issuetekst*: "ariaSnapshot/semantics-tree-inspectie op de web-build" wordt `tester.getSemantics()`-assertie in `flutter_test`, zoals al gedaan in `test/classification_badge_widget_test.dart`; er is geen aparte DOM/ARIA-laag in Flutter web.
- Een geautomatiseerde test (Dart-unit of widget-test) berekent met de bestaande `contrastRatio`/`relativeLuminance`-helpers uit `classification.dart` het contrast van de kleurwaarden zoals gebruikt door de badge in het dialoog, en bevestigt minimaal 4.5:1 (dezelfde `kClassificationColors`-paren als op de lijstkaart, dus geen regressie mogelijk zolang er geen nieuwe kleuren worden geïntroduceerd).
- Een Flutter widget-test verifieert de focusvolgorde: na het openen van `IterationSessionDialog` is de `ClassificationBadge` het eerste focusbare element vóór de secties "Voortgang"/agentresultaten/workspace-publicaties (bv. via volgorde van `Focus`/`FocusNode`-widgets in de widget-tree, of door te tabben en te controleren welk element als eerste focus krijgt).

## Aannames
- "Web-build"-acceptatiecriteria uit de oorspronkelijke issuetekst (ariaSnapshot, Playwright) worden vertaald naar de bestaande `flutter_test`/`Semantics`-toolset van dit project, conform het precedent in `test/classification_badge_widget_test.dart` en `test/classification_badge_disclosure_test.dart`; er wordt geen nieuwe testtooling (Playwright, axe-core, integration_test) geïntroduceerd.
- "Dezelfde iteratiedata die al in het dialoog beschikbaar is" betekent: `iteration['status']`, `iteration['criticVerdict']`, `iteration['errorMessage']`, zoals die al uit de bestaande `session`-Future komen; er wordt geen nieuw API-veld of extra call toegevoegd.
- De overige, niet-status-gerelateerde chips (delivery-mode, "bezig: rol") in dezelfde `Wrap` blijven functioneel en visueel ongewijzigd.

## Eindsamenvatting

## Eindsamenvatting — product-factory-13

**Gebouwd:** In `IterationSessionDialog` (`dashboard-frontend/lib/main.dart`, rond regel 836) is de kale statuschip (`Chip(label: Text(status))`) vervangen door de bestaande `ClassificationBadge`-widget, gevoed met `classifyIterationOutcome(status, criticVerdict, errorMessage)` — exact dezelfde data die al beschikbaar was in het dialoog. Dit is identiek aan hoe de badge al op de lijstkaart werkte (main.dart:562–593). `classification.dart` en de kleurenset zijn niet aangeraakt; de overige chips in dezelfde `Wrap` (delivery-mode, "bezig: rol") zijn ongewijzigd gebleven.

**Keuzes:**
- Geen nieuwe classificatielogica of kleuren geïntroduceerd — hergebruik van bestaande, al goedgekeurde componenten.
- De Semantics-vergelijking tussen lijstkaart en dialoog is op classificatienaam + knop-/hint-gelijkheid gedaan i.p.v. exacte stringgelijkheid, omdat `getSemanticsData().label` de gemergde tekst van de hele rij teruggeeft.
- Eén ongerelateerde, pre-existing formatteringsafwijking in `main.dart` (buiten de gewijzigde sectie) is bewust niet meegenomen om de diff beperkt te houden.

**Getest:** Nieuw testbestand `dashboard-frontend/test/iteration_session_dialog_classification_badge_test.dart` met 14 testcases, dekt alle vijf acceptatiecriteria: (1) pariteit badge-tekst/kleuren tussen lijstkaart en dialoog voor alle vijf classificaties, (2) geen rauwe status-Chip meer in het dialoog, (3) toetsenbordnavigatie (Tab/Enter/Space) en toggle, (4) Semantics-aankondiging gelijk aan lijstkaart, (5) WCAG-contrast ≥4.5:1, (6) focusvolgorde: badge is eerste focusbare element vóór Voortgang/agentresultaten/workspace. Volledige vangnet groen: `flutter analyze` (geen issues), `flutter test` 93/93 geslaagd, `mvn clean verify` BUILD SUCCESS. Reviewer en tester hebben dit onafhankelijk herverifieerd (review-approved, test-approved) en het frontend-preview-endpoint gecontroleerd (200 OK).

**Bewust niet gedaan:** Geen aanpassing van het running/queued-gedrag — de badge in het dialoog toont ook tijdens QUEUED/RUNNING de classificatie (i.p.v. een aparte voortgangsindicator zoals op de lijstkaart). Dit valt buiten de scope van deze story (die vroeg om een 1-op-1 vervanging) en is door de reviewer als non-blocking suggestie genoteerd voor een eventuele vervolgstory. Interactieve verificatie in de preview-omgeving zelf kon niet worden gedaan (geen browsertool in de agentcontainer beschikbaar), maar wordt gedekt door de widget-tests die dit gedrag simuleren.

<!-- deploy-summary:start -->
Bij het bekijken van de details van een productiteratie zie je nu hetzelfde duidelijke, gekleurde label met uitleg dat je ook al in het overzicht zag, in plaats van een kale technische statustekst. Zo krijg je overal dezelfde, begrijpelijke uitkomst te zien, zonder verwarring.
<!-- deploy-summary:end -->
