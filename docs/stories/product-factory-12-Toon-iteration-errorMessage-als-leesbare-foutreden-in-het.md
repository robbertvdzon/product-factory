# product-factory-12 - Toon iteration.errorMessage als leesbare foutreden in het detaildialoog bij een FAILED-iteratie

## Story

Toon iteration.errorMessage als leesbare foutreden in het detaildialoog bij een FAILED-iteratie

<!-- refined-by-factory -->

## Samenvatting
Bij een mislukte productcyclus (status FAILED) toont het detaildialoog nu geen enkele reden waarom het misging — terwijl de API die reden wél meelevert. We voegen een duidelijk tekstblok 'Foutreden' toe aan het dialoog, zodat je meteen ziet wat er fout ging zonder ergens anders te hoeven zoeken. Bij andere statussen verandert er niets.

## Scope
- Wijzig uitsluitend `IterationSessionDialog` in `dashboard-frontend/lib/main.dart`.
- Voeg een voorwaardelijk tekstblok toe met het label **'Foutreden'** dat `iteration['errorMessage']` toont, analoog aan het bestaande patroon voor `step['errorMessage']` (regel ~923) en `delivery['errorMessage']` (regel ~1246-1253).
- Zichtbaarheidsregel:
  - `status == 'FAILED'` én `errorMessage` niet leeg/null → toon label 'Foutreden' met de exacte inhoud van `errorMessage`.
  - `status == 'FAILED'` én `errorMessage` leeg of null → toon in plaats daarvan exact de tekst **'Geen foutreden beschikbaar'** (nooit een leeg of ontbrekend blok).
  - Elke andere status → blok volledig verborgen, geen wijziging aan bestaand gedrag.
- Het blok krijgt een expliciet `Semantics`-label `'Foutreden: <tekst>'` zodat het als afzonderlijk betekenisvol blok wordt aangekondigd door schermlezers (vergelijkbaar met het bestaande `Semantics`-gebruik elders in dit bestand, bv. `IterationProgressIndicator`).
- Geen wijziging aan `/api/shadow-iterations`, aan `ShadowIterationView`/DTO's, of aan de classificatielogica in `classification.dart`. Zuiver een presentatiewijziging binnen `IterationSessionDialog`.
- De bestaande `step['errorMessage']`- en `delivery['errorMessage']`-blokken blijven ongewijzigd functioneren.

## Acceptance criteria
1. Een geautomatiseerde Flutter widget-/integratietest mockt een `/api/shadow-iterations`-respons met een iteratie met status FAILED en een niet-lege `errorMessage`, opent het detaildialoog voor die iteratie, en verifieert dat een tekstnode met het label 'Foutreden' en de exacte inhoud van `errorMessage` aanwezig is in de widget-tree.
2. Een geautomatiseerde test mockt een iteratie met status FAILED en `errorMessage` leeg of null, opent het detaildialoog, en verifieert dat in plaats daarvan exact de tekst 'Geen foutreden beschikbaar' zichtbaar is (geen leeg of ontbrekend blok).
3. Een geautomatiseerde test mockt een iteratie met een status ongelijk aan FAILED (bv. COMPLETED of NEEDS_REVISION) met een gevulde `errorMessage`, opent het detaildialoog, en verifieert dat het 'Foutreden'-blok afwezig is in de widget-tree (geen regressie op bestaand gedrag voor niet-FAILED statussen).
4. Het nieuwe foutredenblok heeft een expliciet `Semantics`-label 'Foutreden: <tekst>', geverifieerd via een geautomatiseerde ariaSnapshot/semantics-tree-inspectie op de web-build, zodat het als afzonderlijk betekenisvol blok wordt aangekondigd.
5. Een geautomatiseerde contracttest bevestigt dat de velden en types van de `/api/shadow-iterations`-respons vóór en na deze wijziging ongewijzigd zijn (geen backend-aanpassing in deze story).
6. De bestaande, elders in hetzelfde dialoog al gerenderde `step['errorMessage']`- en `delivery['errorMessage']`-blokken blijven functioneel ongewijzigd, geverifieerd door de bestaande of een equivalente geautomatiseerde test die deze blokken al dekt.

## Aannames
- Er bestaat al een lokale `status`-variabele in `IterationSessionDialog.build` (regel ~823, `'${iteration['status']}'`) die direct gebruikt kan worden voor de FAILED-check; geen nieuwe statuslogica nodig.
- Het tekstblok volgt qua opbouw het bestaande `Text('Foutreden', style: titleMedium)` + `SelectableText(...)`-patroon zoals gebruikt bij 'Opdracht' (regel ~884-889), zodat de UI stijlconsistent blijft; de exacte widgetkeuze (Text vs SelectableText) is aan de developer zolang de tekstinhoud en het Semantics-label kloppen.
- 'Leeg' betekent zowel `null` als een string die na `trim()` leeg is.
- Plaatsing van het blok in het dialoog (bv. direct onder de statuschips of bij 'Voortgang') is aan de developer; er is geen harde eis over de exacte positie, zolang het blok aanwezig en vindbaar is wanneer van toepassing.

## Eindsamenvatting

Ik heb voldoende context uit `.task.md`, het worklog en de diff. Nu de eindsamenvatting.

## Eindsamenvatting — product-factory-12: Foutreden bij FAILED-iteraties

**Gebouwd:**
In `IterationSessionDialog` (`dashboard-frontend/lib/main.dart`) is direct onder het bestaande 'Opdracht'-blok een nieuw voorwaardelijk 'Foutreden'-blok toegevoegd:
- Zichtbaar alleen bij `status == 'FAILED'`.
- Toont `iteration['errorMessage']` (getrimd) als die niet leeg is.
- Toont exact `'Geen foutreden beschikbaar'` als het veld leeg/whitespace/null is.
- Bij elke andere status is het blok volledig afwezig.
- Gewrapt in `Semantics(label: 'Foutreden: <tekst>')`, consistent met het patroon van `IterationProgressIndicator`.

**Keuzes:**
- Stijl volgt het bestaande `Text(titleMedium)` + `SelectableText`-patroon van het 'Opdracht'-blok, zoals de story als optie aangaf.
- Geen wijziging aan `/api/shadow-iterations`, DTO's of `classification.dart` — zuiver een presentatiewijziging, zoals gescoped.
- De bestaande `step['errorMessage']`- en `delivery['errorMessage']`-blokken zijn onaangeraakt gebleven.

**Getest:**
- Nieuwe testfile `iteration_session_error_message_test.dart` (7 tests) dekt alle 6 acceptance criteria: label+tekst bij FAILED+gevuld, fallback-tekst bij FAILED+leeg/null, afwezigheid bij niet-FAILED status, Semantics-labelverificatie, en een contracttest dat de velden/types van de `/api/shadow-iterations`-respons ongewijzigd blijven.
- Vangnet: `flutter analyze` (geen issues), `flutter test` (79/79 groen), `mvn clean verify` (BUILD SUCCESS).
- Tester heeft de diff en AC1-AC6 onafhankelijk geverifieerd, vangnet herhaald, en preview-endpoints (frontend + backend health) met HTTP 200 gecontroleerd; geen bugs gevonden.

**Bewust niet gedaan:**
- Geen interactieve/screenshot-verificatie in de preview-omgeving, omdat er geen browsertool beschikbaar is in de agentcontainer — dit is gecompenseerd met widget-/semantics-tests en codeverificatie.

<!-- deploy-summary:start -->
Als een productcyclus mislukt, zie je nu direct in het detailscherm wat er precies fout is gegaan, zonder daar zelf naar te hoeven zoeken. Bij een mislukking zonder bekende reden staat er duidelijk dat er geen foutmelding beschikbaar is. Voor cycli die niet mislukt zijn, verandert er niets.
<!-- deploy-summary:end -->
