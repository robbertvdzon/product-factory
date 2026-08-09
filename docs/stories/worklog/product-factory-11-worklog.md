# product-factory-11 - Worklog

Story-context bij eerste pickup:
Blokkeerlabel op storykandidaat-kaart

Toon in `_buildStoryQueueSections` (dashboard-frontend/lib/main.dart, rond regel 1099-1123) een compact label met icoon en tekst 'Geblokkeerd: <blockedReason>' direct onder de titel van de kaart, alleen wanneer `story['blocked'] == true` en `story['blockedReason']` niet leeg is; bij afwezigheid blijft de kaart ongewijzigd. Hergebruik het bestaande WCAG AA-geverifieerde kleurenpaar `kGuardrailConflict` uit `dashboard-frontend/lib/classification.dart` (achtergrond `0xFFF8D7DA` / voorgrond `0xFF7A1220`) als label-achtergrond+tekstkleur, zodat het contrast (≥4.5:1) al geverifieerd is. Koppel de label-tekst aan de accessible name/description van de kaart via `Semantics`, zonder de bestaande `Card`/`ListTile`-elementen (leading icon, title, subtitle-opbouw, trailing chevron, `onTap` naar `_showStoryCandidateDetails`) te wijzigen of extra API-calls te introduceren. Schrijf bijbehorende Flutter widget-, semantics- en contrasttests in `dashboard-frontend/test/`: label aanwezig bij blocked:true, afwezig bij blocked:false/lege reden, label in semantics-tree, contrastratio-check (hergebruik `contrastRatio` uit classification.dart), en een test die bevestigt dat het aantal API-calls niet toeneemt bij het renderen van dezelfde kaartenlijst.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `_buildStoryQueueSections` in `dashboard-frontend/lib/main.dart`: onder de titel van de
  storykandidaat-kaart wordt nu, alleen als `story['blocked'] == true` en `story['blockedReason']`
  niet leeg is, een compact label getoond met een blokkeer-icoon en 'Geblokkeerd: <reden>'. Kleuren
  hergebruiken het bestaande, al AA-geverifieerde `kGuardrailConflict`-paar uit `classification.dart`
  (contrast ≥4.5:1, hertest in `story_queue_blocked_label_test.dart`). Geen nieuwe API-call, dialoog,
  route of berekening toegevoegd; alle bestaande kaart-elementen (icoon, titel, subtitle, chevron,
  onTap naar `_showStoryCandidateDetails`) blijven ongewijzigd.
- De kaart is gewrapt in `MergeSemantics` zodat titel/subtitle/blokkeerlabel samen als één
  toegankelijke naam opvraagbaar zijn via de semantics-tree (geverifieerd met
  `tester.getSemantics(...).getSemanticsData().label`), zonder de bestaande widgetstructuur of het
  tap-gedrag te wijzigen.
- Nieuwe tests in `dashboard-frontend/test/story_queue_blocked_label_test.dart`: label zichtbaar bij
  blocked:true+reden, afwezig bij blocked:false, afwezig bij lege/ontbrekende reden, label in
  semantics-tree, bestaande kaart-elementen/tap-gedrag blijven werken, aantal API-calls blijft gelijk
  (7 endpoints, elk 1x) en een contrastcheck op `kGuardrailConflict` (hergebruikt `contrastRatio` uit
  `classification.dart`). Volledige app gerenderd via `http.runWithClient` + `MockClient` (géén echte
  HTTP-calls), conform bestaande teststrategie.
- Boyscout-fix: bij het renderen van de volledige `OverviewPage` in de nieuwe test kwam een
  pre-existente, tot nu toe ongeteste layout-bug in `MetricCard` aan het licht (de labelkolom had geen
  `Expanded`, waardoor langere labels als 'Interne storykandidaten' een `RenderFlex`-overflow
  veroorzaken, onafhankelijk van testvenstergrootte). Minimale, veilige fix: de kolom in een
  `Expanded` gewrapt zodat de tekst binnen de kaart blijft; geen ander gedrag gewijzigd.
- `flutter analyze` en `flutter test` (72 tests, dashboard-frontend) zijn groen. `mvn -B
  --no-transfer-progress clean verify` (repo-root) is gedraaid als onderdeel van het volledige
  vangnet; deze story raakt geen backend-code.
