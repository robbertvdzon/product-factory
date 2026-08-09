# product-factory-7 - Worklog

Story-context bij eerste pickup:
Inspecteer dependsOn-datamodel en documenteer uitkomst

Inspecteer op gedragsniveau (geen codewijziging) hoe story-batchkandidaten en hun `dependsOn`-veld gegenereerd en verwerkt worden: (1) het JSON-schema van `dependsOn` in `productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowSchemas.kt` (vrije string vs. formeel ID-type), (2) of `dependsOn` ergens in `ShadowIterationEngine.kt` geparsed/gevalideerd/geresolved wordt naar een concreet kandidaat- of story-ID (let op het aparte, wél echt positionele veld `candidateIndex` - niet verwarren met `dependsOn`), (3) hoe `dependsOn` in `ShadowDossierRenderer.kt` gerenderd wordt, en (4) of het publicatiepad (`WorkspacePublisher.kt`, `AutonomousDelivery.kt`, `StoryCandidateApi.kt` onder `productfactory/src/main/kotlin/nl/vdzon/productfactory/`) `dependsOn` ooit leest of resolvet. Leg de uitkomst vast in een nieuw, op zichzelf staand documentatiebestand onder `docs/architecture/` (bijv. `dependson-datamodel.md`) met: (a) een machineleesbaar veld met exact één van `uses-positional-index`, `uses-stable-key-already`, `uses-persistent-id-only`, `inconclusive`; (b) bij `uses-positional-index` een concreet bestand+regel-voorbeeld dat aantoont dat een verwijzing als 'Kandidaat 0' een batch-relatieve positie is; (c) een aparte machineleesbare boolean `publish-mechanism-supports-symbolic-keys` met korte onderbouwing; (d) vermelding van de batchsleutel `verify-dependson-datamodel` zodat afhankelijke stories deze bron herkennen. Gebruik een vast, eenduidig parsebaar formaat (bijv. YAML-frontmatter of vaste key-value-regels). Als de code op enig punt niet ondubbelzinnig te doorgronden is, leg `inconclusive` vast met motivatie welke code-paden onduidelijk bleven, in plaats van een aanname te doen. Er wordt geen bestaande code, schema, prompt of publicatielogica gewijzigd - uitsluitend het nieuwe documentatiebestand wordt toegevoegd.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Code-inspectie uitgevoerd van `ShadowSchemas.kt`, `ShadowIterationEngine.kt`,
  `ShadowDossierRenderer.kt`, `ShadowIterationApi.kt` (existingCandidateContext) en van
  `WorkspacePublisher.kt`, `AutonomousDelivery.kt`, `StoryCandidateApi.kt` (grep bevestigt: geen
  enkele treffer voor `dependsOn` in die drie publicatiebestanden).
- Uitkomst vastgelegd in nieuw bestand `docs/architecture/dependson-datamodel.md`:
  `outcome: uses-positional-index`, `publish-mechanism-supports-symbolic-keys: false`,
  `batch-key: verify-dependson-datamodel`, met concrete bestand+regel-onderbouwing (o.a.
  `ShadowSchemas.kt:39`, `ShadowIterationEngine.kt:343/383/390`, `ShadowDossierRenderer.kt:129`,
  `ShadowIterationApi.kt:226-230`).
- Geen productiecode, schema, prompt of publicatielogica gewijzigd; alleen het nieuwe
  documentatiebestand en deze worklog zijn aangepast/toegevoegd.
- Backend-vangnet (`mvn -B --no-transfer-progress clean verify`) gedraaid ter bevestiging dat de
  build ongewijzigd slaagt (zie build-log hieronder).

## Reviewnotities (reviewer, product-37)
- Diff t.o.v. `main` bevat uitsluitend de twee verwachte bestanden (nieuwe doc + worklog); geen
  productiecode, schema of publicatielogica aangeraakt.
- Alle bestand+regel-verwijzingen in `docs/architecture/dependson-datamodel.md` geverifieerd tegen
  de actuele bron: `ShadowSchemas.kt:39` (dependsOn-schema, vrije string zonder ID-formaat),
  `ShadowIterationEngine.kt:343` (applyAutonomyPolicy, tekstuele scan), `:383/390`
  (reviewedCandidates, ongewijzigd doorgeven via textList), `:130` (call naar
  persistValidatedResults) en `:408` (saveCandidate, ID pas hier toegekend),
  `ShadowDossierRenderer.kt:129` (letterlijke rendering), `ShadowIterationApi.kt:226-230`
  (existingCandidateContext met echte database-id's voor cross-batch context) — komen allemaal
  overeen met de huidige code.
- Grep bevestigt onafhankelijk: geen enkele treffer voor `dependsOn` buiten
  `.../iteration/` in de hele productiecode, dus de claim "publicatiepad leest dependsOn nergens"
  klopt.
- `outcome: uses-positional-index` en `publish-mechanism-supports-symbolic-keys: false` zijn
  correct onderbouwd en in het vereiste machineleesbare (YAML-frontmatter) formaat; batchsleutel
  `verify-dependson-datamodel` is aanwezig.
- `.factory/verification.yaml` heeft geen `pathPrefixes`-match voor `docs/`, dus dit vangnet
  hoefde niet te draaien voor deze wijziging; dat is consistent met de acceptatiecriteria
  (geen productiecode gewijzigd). Geen blockers gevonden.
