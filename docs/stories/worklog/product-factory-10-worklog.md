# product-factory-10 - Worklog

Story-context bij eerste pickup:
Read-only blocked/blockedReason-velden toevoegen aan StoryCandidateView

Documenteer eerst expliciet (in commit-/PR-beschrijving) dat blocked/blockedReason nog niet in de /api/story-candidates-response zitten (geverifieerd tegen StoryCandidateApi.kt en Contracts.kt) en dat geblokkeerde kandidaten nooit in story_candidate belanden (persistValidatedResults slaat ze over), waardoor blocked in productiedata via de queue-flow praktisch altijd false is en tests met geseede fixture-data moeten werken. Breid StoryCandidateView (productfactory-contracts/src/main/kotlin/nl/vdzon/productfactory/contracts/Contracts.kt) uit met twee alleen-lezen velden: blocked: Boolean (default false) en blockedReason: String? (default null). Voeg in StoryCandidateController.list() (productfactory/src/main/kotlin/nl/vdzon/productfactory/story/StoryCandidateApi.kt) een read-only opzoeking toe op het bestaande dependson_resolution-artefact (shadow_iteration_artifact, artifact_type='dependson_resolution', content_json, gefilterd op de juiste iteration_id) om per kandidaat het array-item te vinden met matchende backlogId == c.id; neem blocked over en stel blockedReason samen uit de rawValue's van alle dependsOn[]-items met resolved == false in het formaat 'verwijzing naar onbekende sleutels: X, Y' (null als er geen zijn of geen match/artefact is). Geen wijziging aan resolveDependencyReferences, persistValidatedResults, ShadowIterationEngine, de endpoint-URL/-methode, of dashboard-backend. Parsing van content_json moet defensief zijn (ontbrekend artefact, leeg array, onverwachte structuur) zonder de bestaande response te breken. Voeg als onderdeel van dit ontwikkelwerk ook de tests toe in ProductFactoryApiTest.kt (of vergelijkbaar bestand): (1) een geseed dependson_resolution-artefact met meerdere onopgeloste dependsOn-sleutels voor één kandidaat, verifieer de samengevoegde blockedReason-tekst; (2) een kandidaat zonder gekoppeld geblokkeerd artefact-item, verifieer blocked:false en blockedReason:null. Zorg dat alle bestaande tests voor /api/story-candidates blijven slagen.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## product-55 — Read-only blocked/blockedReason-velden toevoegen aan StoryCandidateView

Bevinding (geverifieerd vóór wijziging): `StoryCandidateController.list()`
(`productfactory/src/main/kotlin/nl/vdzon/productfactory/story/StoryCandidateApi.kt`) selecteerde
alleen `story_candidate`- en `shadow_iteration.sequence_number`-kolommen en kende geen join met
`shadow_iteration_artifact`/`dependson_resolution`. `StoryCandidateView`
(`productfactory-contracts/src/main/kotlin/nl/vdzon/productfactory/contracts/Contracts.kt`) had geen
`blocked`/`blockedReason`-veld. Bevestigd: `ShadowIterationEngine.persistValidatedResults` slaat
geblokkeerde kandidaten expliciet over (`return@forEach` bij `candidate.blocked`), dus een geblokkeerde
kandidaat komt nooit in `story_candidate` terecht — in de huidige productiepijplijn is `blocked` via de
storyqueue-API dus in de praktijk altijd `false`. Deze wijziging test de geblokkeerde situatie daarom met
direct geseede fixture-data (shadow_iteration + shadow_iteration_artifact), niet via de volledige
pijplijn.

Gedaan:
- `StoryCandidateView` uitgebreid met `blocked: Boolean = false` en `blockedReason: String? = null`
  (uitsluitend nieuwe, achterwaarts compatibele velden met default; bestaande positionele constructie
  in `StoryCandidateController` blijft werken).
- `StoryCandidateController.list()` leest nu ook `i.id as iteration_id` mee, haalt per betrokken
  iteratie het bestaande `dependson_resolution`-artefact op (`shadow_iteration_artifact`,
  `artifact_type = 'dependson_resolution'`), matcht het array-item met `backlogId == c.id` en vult
  `blocked`/`blockedReason` (samengevoegde `rawValue`'s van onopgeloste `dependsOn`-items, formaat
  "verwijzing naar onbekende sleutels: X, Y"). Puur leeswerk: geen wijziging aan
  `resolveDependencyReferences`, `persistValidatedResults`, `ShadowIterationEngine`, de endpoint-URL/
  -methode of `dashboard-backend` (proxyt `StoryCandidateView` al generiek door).
- JSON-parsing is defensief (`try/catch` + `isArray`-check + `path(...)`-navigatie i.p.v. directe
  veldtoegang), zodat een ontbrekend artefact, lege array of onverwachte structuur de bestaande
  response niet breekt; alleen een `log.warn` bij parsefouten.
- Twee nieuwe testcases toegevoegd aan `ProductFactoryApiTest.kt`
  (`story candidates expose blocked and blockedReason read-only via the existing dependson_resolution
  artefact`): (1) geseed artefact met twee onopgeloste `dependsOn`-sleutels voor één kandidaat →
  samengevoegde `blockedReason`-tekst; (2) kandidaat met `blocked: false` in het artefact én een
  kandidaat zonder gekoppelde iteratie/artefact → beide `blocked: false`, `blockedReason: null`.
- Vangnet gedraaid: `mvn -B --no-transfer-progress clean verify` (repository-maven-verify, de enige
  gate uit `.factory/verification.yaml` die door deze wijziging geraakt wordt — `dashboard-frontend/`
  is niet aangepast) → BUILD SUCCESS, alle modules groen, `ProductFactoryApiTest` 5/5 (incl. de 2
  nieuwe assertions in de nieuwe test). Ter aanvulling ook `mvn -B -Pquality verify` gedraaid, groen.

Niet gedaan / buiten scope:
- Geen wijziging aan `dashboard-frontend` (geen UI-eis in deze story; nieuwe velden stromen automatisch
  door via de generieke `Map`-proxy in `dashboard-backend`).
- Geen wijziging aan `resolveDependencyReferences`/`persistValidatedResults`/`ShadowIterationEngine` of
  aan schema/migraties: puur een read-only opzoeklaag op bestaande, al opgeslagen data.
