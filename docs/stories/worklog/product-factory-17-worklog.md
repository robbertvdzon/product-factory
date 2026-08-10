# product-factory-17 - Worklog

Story-context bij eerste pickup:
Generieke fallback voor top-level velden in artefact-renderer

Vervang de default-branch `_ => const <Widget>[]` in `_readableArtifactFields` (dashboard-frontend/lib/main.dart, ~regel 1289) door een nieuwe helper die itereert over de top-level velden van de gedecodeerde artefact-JSON: string-waarden via `_readableText` en lijsten van uitsluitend primitieve waarden (String/num/bool, geen null/Map/geneste List) via `_readableBulletList`, met labels afgeleid via de bestaande `humanizeFieldKey` (main.dart ~1425). Null-waarden en niet-matchende types (geneste objecten, gemengde/objectlijsten) worden overgeslagen; levert de fallback niets op, dan blijft het bestaande rauwe-JSON-pad zonder toggle ongewijzigd. De vijf bestaande rolspecifieke branches (researcher/product_owner/ux_designer/story_writer/critic) blijven functioneel ongewijzigd. Breid `dashboard-frontend/test/iteration_readable_artifact_fields_test.dart` uit met: (1) alleen-`findings`-fixture (vrijwilligers/huisnummers-tekst) → gelabelde regel 'Bevindingen'; (2) alleen-`decision`, alleen-`story`, en `verdict`+`reason`-fixtures → alle velden als gelabelde regels; (3) regressiecase met de bestaande rijke researcher-fixture die structureel identieke output houdt; (4) fixture met uitsluitend geneste objecten/objectarray op top-level → ongewijzigd rauwe-JSON-fallbackgedrag zonder toggle; (5) fixture met een lijstveld met gemengde/niet-primitieve elementen → geen generieke regel, valt terug op rauwe JSON. Documenteer in de PR expliciet de inspectie-uitkomst (geen afwijkingen gevonden t.o.v. de storybeschrijving).

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## product-97: Generieke fallback voor top-level velden

Inspectie-uitkomst (vóór implementatie):
- `_readableArtifactFields` (main.dart, was regel 1143-1291) heeft een `switch (baseRole)` met vijf
  rolspecifieke branches en een default `_ => const <Widget>[]`. Dit klopt exact met de
  storybeschrijving; de default is precies het vervangpunt.
- `humanizeFieldKey` (main.dart, was regel 1425) bestond al, publiek en side-effectvrij, met vaste
  labels voor `findings`/`decision`/`story`/`verdict`/`reason` en een generieke camelCase/snake_case
  → titelcase-fallback voor onbekende sleutels. Was nog nergens aangeroepen vanuit
  `_readableArtifactFields` (zoals de docstring ook al vermeldde) — dit is de eerste koppeling.
- `_readableText`/`_readableBulletList` bestonden al en filteren zelf lege/`null`-waarden weg; geen
  aanpassing nodig aan die helpers zelf.
- Geen afwijkingen gevonden t.o.v. de storybeschrijving.

Implementatie:
- Nieuwe helper `_readableGenericFields(context, data)` toegevoegd na `_readableArtifactFields`:
  itereert over `data.entries`, toont `String`-waarden via `_readableText` en lijsten die uitsluitend
  uit `String`/`num`/`bool` bestaan via `_readableBulletList`, met labels via `humanizeFieldKey`.
  Geneste `Map`/`List`-waarden, gemengde lijsten en `null` worden overgeslagen (impliciet: `null` is
  geen `String` en geen matchende `List`, dus die tak wordt nooit geraakt).
- Default-branch `_ => const <Widget>[]` vervangen door `_ => _readableGenericFields(context, data)`.
  De vijf bestaande rolspecifieke branches zijn niet aangeraakt.
- Bestaande test `iteration_readable_artifact_fields_test.dart`: de fixture voor
  "niet-herkende structuur toont uitsluitend de rauwe-JSON-fallback" bevatte een top-level
  `someWeirdField` (string), wat na deze wijziging terecht wél een leesbare regel oplevert. Fixture
  aangepast naar uitsluitend geneste objecten/objectarray (AC4-scenario), zodat de test zijn
  oorspronkelijke intentie (geen leesbare content, rauwe JSON zonder toggle) behoudt.
- Nieuwe tests toegevoegd voor: alleen-`findings` (label 'Bevindingen'), alleen-`decision`,
  alleen-`story`, `verdict`+`reason` samen, en een lijstveld met gemengde/niet-primitieve elementen
  (valt terug op rauwe JSON, geen generieke regel). De bestaande researcher/product_owner-tests
  dienen als regressiebewijs dat de rijke rolschema's structureel ongewijzigd blijven (die branches
  zijn niet aangeraakt).
- `flutter analyze` en `flutter test` groen; `mvn -B --no-transfer-progress clean verify` groen (geen
  backendwijzigingen).

## Review product-97 (2026-08-10)

- Diff tegen main beperkt tot main.dart (nieuwe `_readableGenericFields` helper +
  default-branch switch), bijbehorende tests, en worklog. Komt exact overeen met de
  storybeschrijving en subtaak-omschrijving.
- Implementatie geverifieerd: null-waarden en niet-matchende types worden correct
  overgeslagen; lege string/lege lijst leveren geen regel op omdat de al bestaande
  `_readableText`/`_readableBulletList` dat zelf al filteren (geen dubbele logica nodig).
- Bestaande fixtures met een los top-level `someWeirdField`-string (in beide
  testbestanden) zijn terecht aangepast naar uitsluitend geneste data, anders zouden ze
  door het nieuwe gedrag een vals-positieve "geen leesbare content"-verwachting hebben.
  Geen scope-overlap, hoort bij deze wijziging.
- Alle AC's gedekt door tests (findings/decision/story/verdict+reason, regressie
  researcher, geneste-objecten-fallback zonder toggle, gemengde lijst zonder toggle).
- Gerichte re-run: `flutter test test/iteration_readable_artifact_fields_test.dart
  test/iteration_technical_details_toggle_test.dart` → 16/16 groen. `dart format
  --output=none --set-exit-if-changed lib/main.dart` → geen wijzigingen.
- Geen bugs, regressies of scope-afwijkingen gevonden.

[info] Akkoord, geen blockers.

## Test product-98 (2026-08-10)

- Diff tegen main beperkt tot `dashboard-frontend/lib/main.dart`,
  `dashboard-frontend/test/iteration_readable_artifact_fields_test.dart`,
  `dashboard-frontend/test/iteration_technical_details_toggle_test.dart` en de worklog. Geen
  backendwijzigingen, dus `mvn clean verify` triggert niet via `.factory/verification.yaml`
  pathPrefixes en is voor deze story niet van toepassing.
- `flutter analyze` (dashboard-frontend): "No issues found!".
- `flutter test` (dashboard-frontend, volledige suite): 117/117 groen, exit code 0. Compacte
  reporter toonde interleaved herhaalde testregels door parallelle shard-uitvoer (bekend
  cosmetisch artefact, zie agent-tip `flutter-test-compact-reporter-concurrent-output`); +N-teller
  en eindtotaal (`All tests passed!`) zijn correct.
- Codeverificatie `_readableGenericFields`/default-branch (main.dart ~1289-1313): matcht exact de
  storybeschrijving — string-velden via `_readableText`, lijsten van uitsluitend
  String/num/bool via `_readableBulletList`, labels via `humanizeFieldKey`, overige
  types/`null` overgeslagen; lege fallback laat bestaand rauwe-JSON-pad zonder toggle intact.
- Testdiff geverifieerd tegen alle AC's: losse findings/decision/story-fixtures en
  verdict+reason-fixture tonen gelabelde regels + toggle; regressie op bestaande
  researcher-fixture ongewijzigd; geneste-objecten/objectarray-fixture en
  gemengde-lijst-fixture tonen beide alleen rauwe JSON zonder toggle. Alle scenario's uit de
  acceptatiecriteria zijn gedekt en slagen.
- Preview-smoketest: `https://product-factory-pr-51.vdzonsoftware.nl` → 200,
  `https://product-factory-api-pr-51.vdzonsoftware.nl/actuator/health` → 200. Geen browsertool
  in de agentcontainer beschikbaar; interactieve/screenshotverificatie van de gerenderde
  velden niet mogelijk, geverifieerd via widgettests en codeinspectie.
- Geen bugs of afwijkingen gevonden. Geen tijdelijke testdata aangemaakt, dus geen cleanup nodig.

[info] Tested, geen blockers.
