# product-factory-26 - Worklog

Story-context bij eerste pickup:
Verplaats missie/repo/workspace/max-stories/WIP/AI-model/cyclustijden van productkaart naar Instellingen-paneel

Verwijder in dashboard-frontend/lib/main.dart de directe weergave op de productkaart (regels ~564, 570-584) van missie, softwareFactoryProjectKey, targetRepositoryName, workspaceOwnership, maxStoriesPerCycle, wipLimit, AI-provider/model en cyclustijden. Documenteer eerst (in commit-omschrijving) de bevinding dat ProductSettingsDialog (main.dart:2363-2478) al developmentMode/maxStoriesPerCycle/wipLimit/AiProviderModelFields/IterationTimesField bevat, maar geen targetRepositoryName-veld en geen sectie voor mission/softwareFactoryProjectKey/workspaceOwnership. Breid ProductSettingsDialog uit met: (1) een alleen-lezen sectie met missie, softwareFactoryProjectKey en workspaceOwnership, elk gelabeld met een korte toelichting dat deze velden gekoppeld zijn aan de Software Factory-integratie en niet bewerkbaar zijn; (2) een nieuw bewerkbaar TextField voor targetRepositoryName, opgenomen in de _submit()-payload zodat het via de bestaande, ongewijzigde api.updateProductSettings-call (die UpdateProductSettingsRequest in ProductCatalog.kt al ondersteunt) wordt opgeslagen. Laat maxStoriesPerCycle/wipLimit/AiProviderModelFields/IterationTimesField functioneel ongewijzigd. Borg en waar nodig verbeter toetsenbordbediening van de AlertDialog: opent met focus binnen de dialoog, Escape sluit met focus terug naar de Instellingen-knop, Tab-focus blijft binnen de dialoog (focus-trap). Raak de kaartknoppen 'Pauzeren'/'Hervatten' en 'Start overleg', en de backend/API (UpdateProductSettingsRequest, database) niet aan. Schrijf als onderdeel van dit werk nieuwe flutter_test-widgettests in dashboard-frontend/test/ die verifiëren: de zes velden staan niet meer op de kaart; elk bewerkbaar veld (inclusief targetRepositoryName) wijzigt, slaat op en komt met de juiste sleutel in de opslag-payload terecht; de alleen-lezen sectie toont missie/project-key/workspace met label en toelichting; het toetsenbordgedrag (Escape, focus-terugkeer, focus-trap) werkt zoals vereist. Formatteer alleen gewijzigde regels met dart format.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## Bevindingen vóór wijziging (inspectie `ProductSettingsDialog`, main.dart)

- `ProductSettingsDialog` (vóór wijziging: main.dart:2363-2478) bevatte al bewerkbare velden voor
  `developmentMode` (dropdown), `maxStoriesPerCycle` (TextField), `wipLimit` (TextField),
  `aiProvider`/`aiModel` (`AiProviderModelFields`) en `iterationTimes` (`IterationTimesField`),
  elk met label/helperText en al opgenomen in `_submit()`.
- Ontbrekend in de dialoog: (1) een `targetRepositoryName`-veld — hoewel
  `UpdateProductSettingsRequest` (productfactory/.../product/api/ProductCatalog.kt:49-58) dit veld
  al ondersteunt — en (2) een sectie voor `mission`, `softwareFactoryProjectKey` en
  `workspaceOwnership`, die alléén op de productkaart zelf stonden (main.dart, was ~regel 564,
  570-584).

## Uitgevoerde wijzigingen

- Productkaart (`dashboard-frontend/lib/main.dart`, `_limitedSection('products', ...)`): de
  `Text('${product['mission']}')`-regel en de `Wrap` met project/repo/workspace/max-stories/wip/
  AI/cyclustijden zijn volledig verwijderd. De kaart toont nu alleen naam, status, ontwikkelmodus-
  chip, actieve/gepauzeerd-uitleg en de knoppenrij.
- `ProductSettingsDialog`: nieuwe alleen-lezen sectie bovenaan met 3 `TextFormField(readOnly: true)`
  voor Missie / Software Factory-project / Workspace, elk met helperText "Gekoppeld aan de Software
  Factory-integratie; hier niet bewerkbaar.". Nieuw bewerkbaar `TextField` "Doelrepository"
  (`targetRepositoryName`), meegenomen in `_submit()`'s payload — de API-call
  (`api.updateProductSettings` → `UpdateProductSettingsRequest`) is niet gewijzigd, want die
  ondersteunde dit veld al.
- Toetsenbordbediening: `_editProductSettings` roept `showDialog` nu aan met
  `traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop` (anders laat Flutters standaard
  `_ModalScope`-gedrag Tab bij het laatste/eerste focusbare veld de dialoogscope verlaten — geen
  focus-trap zonder deze parameter). De "Ontwikkelmodus"-dropdown kreeg `autofocus: true` (focus
  opent binnen de dialoog). De Instellingen-knop is vervangen door een nieuwe publieke
  `SettingsButton`-widget die zijn eigen `FocusNode` bezit en die na het sluiten van de dialoog
  (Opslaan, Annuleren, barrier-tap óf Escape — Escape sluit al standaard via Flutters ingebouwde
  `DismissIntent`-binding op modal routes) expliciet weer opvraagt, zodat de focus deterministisch
  teruggaat naar de knop.
- Boyscout-fix: `_OverviewPageState._reload()` was een expressie-body
  (`void _reload() => data = Future.wait(...)`) die bij aanroep via `setState(_reload)` (o.a. in
  `_changeStatus`, gebruikt door de Pauzeren/Hervatten-knop) de assertion "setState() callback
  argument returned a Future" veroorzaakte. Dit was niet eerder gedekt door een widgettest; de
  nieuwe kaartknoppen-test in `product_card_settings_fields_test.dart` legde het bloot. Omgezet
  naar een blok-body (patroon dat elders in hetzelfde bestand, bv. bij `IterationSessionDialog`,
  al zo stond), zonder gedragswijziging.

## Nieuwe tests

- `dashboard-frontend/test/product_card_settings_fields_test.dart`: de zes velden staan niet meer
  op de kaart; de Instellingen-knop opent de dialoog met de verplaatste waarden; Pauzeren/Start
  overleg blijven werken (met een MockClient conform de projectconventie, hele interactie binnen
  `http.runWithClient`).
- `dashboard-frontend/test/product_settings_dialog_test.dart`: alleen-lezen sectie
  (label+toelichting, `TextField.readOnly == true`); alle bewerkbare velden (max stories,
  wip-limiet, doelrepository, AI-provider/model, cyclustijden) wijzigen en komen met de juiste
  sleutel in de payload; dialoog opent met focus binnen de dialoog en houdt Tab-focus binnen de
  dialoog (25 Tab-cycli getest); Escape sluit de dialoog en de focus keert terug naar de
  Instellingen-knop.

## Vangnet

- `flutter analyze` (dashboard-frontend): geen issues.
- `flutter test` (dashboard-frontend): 165 tests, alle geslaagd (inclusief 2 nieuwe testbestanden).
- `mvn -B --no-transfer-progress clean verify` (repo-root): BUILD SUCCESS, alle backend-modules
  groen (geen backend-wijzigingen in deze story; gedraaid conform het verplichte vangnet).
- `dart format` uitsluitend op de gewijzigde bestanden (`lib/main.dart` en de twee nieuwe
  testbestanden).
- `.factory/verification.yaml` ongewijzigd gelaten: bestaande command-ids/argv/workingDirectory/
  timeouts dekken deze wijziging al (frontend-only diff, geen nieuwe tools/commando's nodig).

## Niet gedaan / bewust buiten scope

- Backend, database, `UpdateProductSettingsRequest`-schema: ongewijzigd (niet nodig, `
  targetRepositoryName` en `acceptanceUrl` waren al ondersteund).
- `acceptanceUrl` (wél ondersteund door `UpdateProductSettingsRequest`) niet toegevoegd — expliciet
  buiten scope volgens de Aannames-sectie van de story.
- Geen wijziging aan de `AiProviderModelFields`/`IterationTimesField`-widgets zelf; alleen hun
  positie/omgeving in de dialoog is aangepast waar nodig voor de nieuwe layout.

## Reviewnotities (product-150)

- Diff tegen `main` beperkt tot `dashboard-frontend/lib/main.dart`, twee nieuwe testbestanden en
  deze worklog; geen backend-/API-/database-wijzigingen — conform scope.
- Zelf herdraaid in de werktree: `flutter analyze` → geen issues; `flutter test` → 165/165 groen
  (incl. de twee nieuwe testbestanden); `dart format --set-exit-if-changed` op de drie gewijzigde
  bestanden → geen wijzigingen. `pubspec.lock` ongewijzigd (geen toolchain-divergentie-risico).
- Alle zes velden (missie, project-key, repo, workspace, max-stories, wip, AI, cyclustijden)
  geverifieerd afwezig van de kaart; alleen-lezen sectie + bewerkbaar `targetRepositoryName`-veld
  geverifieerd in `ProductSettingsDialog`; payload-sleutel `targetRepositoryName` komt overeen met
  `UpdateProductSettingsRequest` in `ProductCatalog.kt`. `api.updateProductSettings` stuurt de
  settings-map ongewijzigd als JSON-body, dus geen mapping-mismatch.
- `traversalEdgeBehavior` op `showDialog` bestaat in de gebruikte Flutter-versie (geverifieerd in
  de Flutter SDK-bron); geen API-misbruik.
- Pauzeren/Hervatten/Start overleg-knoppen ongewijzigd qua gedrag; enige wijziging is de
  Instellingen-knop die vervangen is door `SettingsButton` (focus-teruggave), gedekt door tests.
- Geen blockers gevonden. Akkoord.
