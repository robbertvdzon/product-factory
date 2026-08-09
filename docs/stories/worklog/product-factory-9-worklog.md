# product-factory-9 - Worklog

Story-context bij eerste pickup:
Resolve-stap dependsOn → backlog-ID bij batchpublicatie, met legacy-fallback

Implementeer in ShadowIterationEngine.kt/ShadowIterationApi.kt/ShadowDossierRenderer.kt een resolve-stap die bij het definitief vastleggen van een storybatch elke dependsOn-sleutel binnen de batch vertaalt naar het story_candidate.id van de betreffende kandidaat. Verwijder de huidige batch-brede afwijzing in validateStories voor het legacy-patroon 'Kandidaat <n>' (LEGACY_POSITIONAL_DEPENDSON_PATTERN); herken dit patroon voortaan automatisch en probeer het te vertalen naar het batch-item op die nulgebaseerde reviewpositie. Lukt de vertaling (via candidateKey of legacy-positie), persisteer de kandidaat normaal en log expliciet of resolutie via het legacy-fallbackpad verliep. Lukt vertaling niet (onbekende sleutel of niet-bestaande legacy-positie), blokkeer uitsluitend die kandidaat (niet persisteren in story_candidate, fout loggen met de onvertaalde waarde) zonder de rest van de batch te raken - geen cascaderende blokkade. Pas repository.saveCandidate aan zodat het gegenereerde story_candidate.id wordt teruggegeven (KeyHolder). Leg de volledige sleutel-naar-backlog-ID-mapping (inclusief legacy-resoluties en blokkades) duurzaam en achteraf doorzoekbaar vast, bijvoorbeeld als nieuw artifact_type in de bestaande shadow_iteration_artifact-tabel, en maak deze zichtbaar in het door ShadowDossierRenderer.kt gerenderde dossier naast de bestaande candidateKey/resolvedDependsOn-weergave. Schrijf als onderdeel van dit ontwikkelwerk twee geautomatiseerde tests in ShadowIterationEngineTest.kt: (1) een batch met een opzettelijk niet-bestaande dependsOn-sleutel blokkeert publicatie van uitsluitend de afhankelijke kandidaat, terwijl overige kandidaten normaal publiceren; (2) een batch met een dependsOn-waarde in het oude 'Kandidaat N'-formaat resolveert correct via het legacy-fallbackpad en wordt als zodanig in de mapping-log gemarkeerd (vervang hiervoor de bestaande test 'dependsOn using the old positional Kandidaat N format is rejected', die het oude afwijsgedrag toetst, en gebruik een fixture met minimaal twee kandidaten zodat de fallback niet zelf-referentieel is). story_candidate.id, WorkspacePublisher.kt, AutonomousDelivery.kt, StoryCandidateApi.kt en hkh/hkh-autopilot blijven ongewijzigd van gedrag.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `ShadowIterationEngine.validateStories` wijst het legacy-patroon "Kandidaat <n>" niet langer
  batch-breed af; de `require`-check die dat deed (regel ~315-317) is verwijderd.
- `ShadowDossierRenderer.kt`: nieuwe pure top-level functie `resolveDependencyReferences`
  (vervangt `resolveCandidateDependencies`) resolveert elke `dependsOn`-waarde eerst via een
  `candidateKey`-lookup binnen de batch; lukt dat niet en matcht de waarde
  `LEGACY_POSITIONAL_DEPENDSON_PATTERN` ("Kandidaat <n>", nu met capture-group voor de positie),
  dan valt hij terug op het batch-item op die nulgebaseerde positie (`candidatesByPosition`, dezelfde
  volgorde als `candidates[]`/`candidateIndex`). Nieuwe `DependencyResolution`-data class draagt
  per dependsOn-waarde: `resolvedCandidateKey`, `viaLegacyFallback` en afgeleide `resolved`.
  `ReviewedCandidate` kreeg `dependencyResolutions` en `blocked` (true zodra minstens één
  dependsOn-waarde onopgelost blijft).
- `ShadowIterationEngine.reviewedCandidates` gebruikt de nieuwe resolver; `persistValidatedResults`
  slaat een geblokkeerde kandidaat niet op in `story_candidate` en logt (SLF4J `log.error`) de
  onvertaalde waarde, zonder de rest van de batch te raken (geen cascaderende blokkade — een
  kandidaat die naar een geblokkeerde kandidaat verwijst via een wél bestaande candidateKey/legacy-
  positie resolveert gewoon en wordt normaal gepersisteerd). `run()` sluit geblokkeerde kandidaten
  ook uit van de geaccepteerde/gepubliceerde set (`accepted`-filter kreeg `&& !it.blocked`).
- `ShadowIterationRepository.saveCandidate` (in `ShadowIterationApi.kt`) gebruikt nu een
  `GeneratedKeyHolder` en geeft het toegekende `story_candidate.id` terug (`Long`).
- Nieuwe generieke `ShadowIterationRepository.saveArtifact(...)` en, na iedere batchpersistering,
  een nieuw artifact_type `dependson_resolution` in de bestaande `shadow_iteration_artifact`-tabel
  (primary key `(iteration_id, artifact_type)`, dus één rij per iteratie) met de volledige
  sleutel→backlog-ID-mapping per kandidaat (inclusief blokkade- en legacy-fallbackstatus per
  dependsOn-waarde) — dit maakt de mapping duurzaam en achteraf doorzoekbaar, ook voor batches die
  uiteindelijk niet naar de workspace gepubliceerd worden (REVISE/REJECT/DUPLICATE).
- `ShadowIterationEngineTest.kt`: bestaande test "dependsOn using the old positional Kandidaat N
  format is rejected" vervangen door "a dependsOn value in the legacy Kandidaat N format resolves
  via the positional fallback and is marked as such" (fixture met twee kandidaten, zodat de
  legacy-verwijzing niet zelf-referentieel is). Nieuwe test "a dependsOn value that cannot be
  resolved to a backlog-ID blocks only that candidate" toont dat alleen de afhankelijke kandidaat
  wordt tegengehouden terwijl de rest normaal publiceert. De pure-resolver-test is aangepast aan de
  nieuwe functienaam/signatuur en uitgebreid met een legacy-fallback- en een
  onopgelost-blijft-onopgelost-case. Het artefactenaantal in de ACCEPT-scenario-test is van 6 naar 7
  bijgewerkt (het nieuwe `dependson_resolution`-artefact komt boven op de 6 bestaande rolartefacten).
- Vangnet gedraaid: `mvn -B --no-transfer-progress clean verify` (volledige reactor, achtergrond
  vanwege de >600s Bash-timeout) — BUILD SUCCESS, alle modules groen, inclusief 9 tests in
  `ShadowIterationEngineTest` (was 7). Ook `mvn -B -Pquality verify -pl productfactory -am` gedraaid
  ter aanvulling — eveneens BUILD SUCCESS. Geen wijzigingen aan `dashboard-frontend/`, dus de
  Flutter-commando's uit het vangnet zijn niet van toepassing (path-scoped in
  `.factory/verification.yaml`).
- Niet gewijzigd (bewust buiten scope, conform de story): `story_candidate.id`-semantiek (nog
  steeds de auto-increment primary key), `WorkspacePublisher.kt`, `AutonomousDelivery.kt`,
  `StoryCandidateApi.kt`, hkh/hkh-autopilot en alle authenticatie-/PR-goedkeuringsflows.

## Reviewer-notities (product-49)

- Volledige diff (main...HEAD) bekeken: ShadowIterationEngine.kt, ShadowDossierRenderer.kt,
  ShadowIterationApi.kt, ShadowIterationEngineTest.kt.
- Geverifieerd dat de batch-brede `require`-afwijzing voor het legacy `"Kandidaat <n>"`-patroon in
  `validateStories` is verwijderd, en dat `resolveDependencyReferences` eerst op `candidateKey`
  resolveert en pas daarna op legacy batchpositie terugvalt, zonder cascade naar andere kandidaten.
- `persistValidatedResults` slaat geblokkeerde kandidaten niet op, logt de onvertaalde waarde, en
  legt via het nieuwe `saveArtifact(...)`/`dependson_resolution`-artefact een duurzame, doorzoekbare
  sleutel→backlog-ID-mapping vast (ook voor niet-ACCEPT-uitkomsten, want `persistValidatedResults`
  loopt onvoorwaardelijk). `run()` sluit geblokkeerde kandidaten terecht ook uit van `accepted`
  (en dus van het gepubliceerde dossier).
- `saveCandidate` gebruikt nu `GeneratedKeyHolder` en geeft `story_candidate.id` terug.
- Beide vereiste tests aanwezig en inhoudelijk correct: "a dependsOn value that cannot be resolved to
  a backlog-ID blocks only that candidate" en "a dependsOn value in the legacy Kandidaat N format
  resolves via the positional fallback and is marked as such" (fixtures met 2 kandidaten, dus niet
  zelf-referentieel). Pure-resolver-tests dekken candidateKey-lookup, legacy-fallback en onopgeloste
  gevallen.
- Scope gecontroleerd: geen wijzigingen in `WorkspacePublisher.kt`, `AutonomousDelivery.kt`,
  `StoryCandidateApi.kt` of hkh/hkh-autopilot.
- Gericht herdraaid: `mvn -B --no-transfer-progress -pl productfactory test
  -Dtest=ShadowIterationEngineTest -Dsurefire.failIfNoSpecifiedTests=false` → BUILD SUCCESS, 9/9
  tests groen (inclusief de 2 nieuwe). Ook `mvn -pl productfactory -am install -DskipTests` groen
  (volledige compile van de betrokken modules); de resterende Kotlin-warnings in
  `AutonomousDeliveryIntegrationTest.kt` zijn pre-existing en niet door deze subtaak geraakt.
- Geen blockers gevonden. Akkoord.
