# product-factory-8 - Worklog

Story-context bij eerste pickup:
Implementeer stabiele candidateKey voor dependsOn-verwijzingen binnen een story-batch

Voeg in ShadowSchemas.kt een verplicht, kebab-case candidateKey-veld toe aan het stories-schema (candidates[]). Pas in ShadowIterationEngine.kt de STORY_WRITER- en revisionPrompt aan zodat het model elke kandidaat een unieke, stabiele candidateKey geeft (behouden bij revisie) en dependsOn voortaan naar elkaars candidateKey verwijst i.p.v. 'Kandidaat N'. Breid validateStories uit met: candidateKey verplicht/kebab-case/uniek binnen de batch, en afwijzing van dependsOn-waarden die het oude positionele patroon ('Kandidaat <n>') matchen. Voeg een pure, ordonafhankelijke resolutiehelper toe die dependsOn-waarden via een candidateKey-lookup (niet via arrayindex) aan de juiste kandidaat koppelt, en gebruik die in reviewedCandidates. Voeg candidateKey toe aan ReviewedCandidate en de dossierrendering in ShadowDossierRenderer.kt. Verifieer dat applyAutonomyPolicy functioneel ongewijzigd kan blijven (puur tekstuele scan). Schrijf de regressietest(en) die aantonen dat twee kandidaten via hun sleutel naar elkaar kunnen verwijzen en correct resolveren ongeacht volgorde, en dat het oude 'Kandidaat N'-formaat wordt afgewezen (bijv. uitbreiding van ShadowIterationEngineTest.kt met een nieuw FakeShadowAgentBridge-scenario, of een losstaande unit test op de resolutiehelper). Documenteer in de worklog en/of het dossier expliciet de bekende, niet-blokkerende beperking dat de eigen dependsOn-verwijzing van deze kandidaat naar 'verify-dependson-datamodel' mogelijk niet resolveert via het bestaande publicatiepad, omdat publish-mechanism-supports-symbolic-keys false is. Geen wijziging aan story_candidate.id, WorkspacePublisher.kt, AutonomousDelivery.kt, StoryCandidateApi.kt, of aan hkh/hkh-autopilot.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## product-43: Implementeer stabiele candidateKey voor dependsOn-verwijzingen binnen een story-batch

Gedaan:
- `ShadowSchemas.kt`: `candidateKey` toegevoegd als verplicht veld aan het `stories`-schema
  (`candidates[]`), met een JSON-schema `pattern` voor kebab-case en min/max-lengte.
- `ShadowIterationEngine.kt`:
  - `validateStories` uitgebreid: `candidateKey` verplicht, moet voldoen aan
    `CANDIDATE_KEY_PATTERN` (kebab-case) en moet uniek zijn binnen de batch; `dependsOn`-waarden
    die het oude batch-relatieve patroon `"Kandidaat <n>"` matchen (`LEGACY_POSITIONAL_DEPENDSON_PATTERN`)
    worden nu afgewezen.
  - Nieuwe pure, ordonafhankelijke helper `resolveCandidateDependencies` (in
    `ShadowDossierRenderer.kt`, samen met `ReviewedCandidate`) die `dependsOn`-waarden koppelt aan
    de juiste kandidaat via een `candidateKey -> ReviewedCandidate`-kaart (lookup, geen
    arrayindex). `reviewedCandidates` gebruikt deze helper om per kandidaat `resolvedDependsOn`
    (de subset van `dependsOn` die binnen dezelfde batch is herkend) te bepalen.
  - `storyPrompt`/`revisionPrompt` geïnstrueerd om per kandidaat een unieke, stabiele
    kebab-case `candidateKey` te leveren (behouden bij revisie) en `dependsOn` voortaan via die
    sleutel te laten verwijzen i.p.v. "Kandidaat N".
  - `applyAutonomyPolicy` bewust ongewijzigd gelaten: het scant `acceptanceCriteria` +
    `dependsOn` puur tekstueel op eigenaar-actiepatronen en is agnostisch voor het format van de
    `dependsOn`-waarden, dus blijft functioneel correct met sleutel-gebaseerde verwijzingen.
- `ShadowDossierRenderer.kt`: `ReviewedCandidate` uitgebreid met `candidateKey` en
  `resolvedDependsOn`; dossierrendering toont nu de `candidateKey` per kandidaat en, waar van
  toepassing, welke `dependsOn`-sleutels binnen de batch zijn herkend.
- `story_candidate.id` (database-ID) is niet gewijzigd van veld of formaat; `candidateKey` is
  uitsluitend een in-memory/in-batch koppelmechanisme vóór publicatie, niet gepersisteerd.
- Regressietests toegevoegd/uitgebreid in `ShadowIterationEngineTest.kt`:
  - nieuw scenario `CROSS_KEY_DEPENDENCY`: twee kandidaten verwijzen in `dependsOn` naar elkaars
    `candidateKey`, waarbij de eerste kandidaat in de array-batch verwijst naar de kandidaat die
    er ná hem staat (dus niet op te lossen via arrayindex/-volgorde); test bevestigt dat het
    dossier beide kandidaten toont met correct herkende `resolvedDependsOn`.
  - directe unit test op de pure helper `resolveCandidateDependencies` met twee kandidaten die
    in twee verschillende kaart-invoegvolgordes (`linkedMapOf` voorwaarts/achterwaarts) correct
    resolven — bewijst ordeonafhankelijkheid onafhankelijk van de volledige enginepijplijn.
  - nieuw scenario `LEGACY_POSITIONAL_DEPENDSON`: een kandidaat met `dependsOn: ["Kandidaat 0"]`
    wordt door `validateStories` afgewezen (engine.run gooit een exception met "Kandidaat" in de
    boodschap, en er wordt geen `story_candidate`-rij gepersisteerd).
  - bestaande scenario's (`ACCEPT`, `DUPLICATE`, `REVISE`, ...) aangepast met een verplicht
    `candidateKey`-veld in de fake STORY_WRITER-JSON, zodat ze blijven slagen onder de nieuwe
    validatie.

Bekende, niet-blokkerende beperking (expliciet vastgelegd per story-scope):
- `publish-mechanism-supports-symbolic-keys` staat op `false` (zie
  `docs/architecture/dependson-datamodel.md`, batch-key `verify-dependson-datamodel`). De eigen
  `dependsOn`-verwijzing van déze kandidaat (`product-43`) naar `verify-dependson-datamodel`
  resolveert mogelijk niet via het bestaande publicatiepad, omdat `WorkspacePublisher.kt`,
  `AutonomousDelivery.kt` en `StoryCandidateApi.kt` het `dependsOn`-veld nergens lezen. Dat pad is
  bewust niet gewijzigd (buiten scope van deze story); de nieuwe `candidateKey` is uitsluitend een
  in-batch koppelmechanisme vóór publicatie, geen nieuw resolutiemechanisme richting het
  publicatiepad.

Niet gedaan / bewust buiten scope:
- Geen wijziging aan `story_candidate.id`, `WorkspacePublisher.kt`, `AutonomousDelivery.kt`,
  `StoryCandidateApi.kt`, of aan hkh/hkh-autopilot productdata of -gedrag.
- Geen wijziging aan het `critic`-schema/`candidateIndex` (blijft index-gebaseerd; dat viel
  buiten de scope van deze story).

Getest:
- `mvn -B --no-transfer-progress -pl productfactory -am clean verify`: BUILD SUCCESS, 45 tests,
  0 failures, 0 errors (inclusief de 3 nieuwe/uitgebreide tests in `ShadowIterationEngineTest`).
- Volledige `mvn -B --no-transfer-progress clean verify` vanuit de repo-root: BUILD SUCCESS over
  alle modules (`productfactory-contracts`, `productfactory-common`, `productfactory-app`,
  `productfactory-agentworker`, `productfactory-dashboard-backend`), 0 failures, 0 errors.
- Frontend (`dashboard-frontend`) niet geraakt door deze wijziging (geen bestand in die map
  aangepast), dus `flutter analyze`/`flutter test` niet apart gedraaid voor deze story.
