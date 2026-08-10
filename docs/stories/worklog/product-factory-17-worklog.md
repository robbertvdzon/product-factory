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
