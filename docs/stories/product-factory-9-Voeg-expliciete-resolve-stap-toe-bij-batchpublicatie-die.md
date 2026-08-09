# product-factory-9 - Voeg expliciete resolve-stap toe bij batchpublicatie die sleutels naar definitieve backlog-ID's vertaalt, met gedefinieerd fallbackgedrag voor bestaande legacy-batches

## Story

Voeg expliciete resolve-stap toe bij batchpublicatie die sleutels naar definitieve backlog-ID's vertaalt, met gedefinieerd fallbackgedrag voor bestaande legacy-batches

<!-- refined-by-factory -->

## Samenvatting
Wanneer Product Factory een set nieuwe storykandidaten (een "batch") definitief in zijn eigen backlog opslaat, moet elke `dependsOn`-verwijzing naar een andere kandidaat uit diezelfde batch vertaald worden naar het echte, blijvende backlog-ID van die kandidaat — net zoals bij een bulkimport eerst tijdelijke sleutels gebruikt worden die achteraf naar definitieve ID's omgezet worden. Verwijst een kandidaat naar een sleutel die niet bestaat, dan wordt alleen díe kandidaat tegengehouden; de rest van de batch gaat gewoon door. Verwijzingen in het oude formaat ("Kandidaat 0") worden niet meer meteen afgewezen, maar krijgen eerst een automatische vertaalpoging; lukt die niet, dan wordt ook daar alleen de betreffende kandidaat geblokkeerd. Achteraf is voor elke kandidaat na te gaan welke sleutel tot welk backlog-ID heeft geleid.

## Scope
- Betreft uitsluitend Product Factory's eigen stap waarin een gevalideerde storybatch definitief wordt vastgelegd in zijn interne backlog (`story_candidate`), d.w.z. `ShadowIterationEngine.reviewedCandidates`/`persistValidatedResults` en `ShadowDossierRenderer.kt` (waar `candidateKey`/`resolvedDependsOn` al bestaan sinds product-factory-8).
- "Definitief, doorlopend backlog-ID" = `story_candidate.id` (Product Factory's eigen interne, auto-increment backlog-ID). Dit is uitdrukkelijk niet het `externalStoryKey` van Software Factory.
- Expliciet buiten scope, ongewijzigd: `WorkspacePublisher.kt` (workspace-git-publicatie), `AutonomousDelivery.kt` en `StoryCandidateApi.kt` (levering aan Software Factory), en alles in hkh/hkh-autopilot of de authenticatie-/PR-goedkeuringsflows. Deze lezen `dependsOn` bewust niet en blijven dat ook niet doen.
- `ShadowIterationEngine.validateStories` wijst momenteel (sinds product-factory-8, regel ~315-316) elke batch met een `dependsOn`-waarde in het oude `"Kandidaat <n>"`-formaat volledig af (de hele batch faalt). Die harde afwijzing wordt in deze story vervangen door: eerst herkennen als legacy-verwijzing en automatisch proberen te vertalen via de reviewpositie binnen de batch; alleen bij een mislukte vertaling wordt de batch-brede afwijzing vervangen door het blokkeren van uitsluitend die ene kandidaat (zie acceptatiecriteria).

## Acceptance criteria
- Bij het definitief vastleggen van een storybatch in de interne backlog resolveert het systeem elke gebruikte `dependsOn`-sleutel binnen dezelfde batch naar het `story_candidate.id` van de betreffende kandidaat.
- Een `dependsOn`-waarde die matcht met het patroon "Kandidaat" gevolgd door een geheel getal (bijv. "Kandidaat 0", case-insensitive, zoals reeds herkend door `LEGACY_POSITIONAL_DEPENDSON_PATTERN`) wordt automatisch, zonder menselijke tussenkomst, als legacy-verwijzing herkend in plaats van als onbekende sleutel te worden behandeld — en leidt niet langer tot afwijzing van de hele batch door `validateStories`.
- Voor een herkende legacy-verwijzing probeert de resolve-stap automatisch te vertalen door het batch-item te identificeren dat op de betreffende (nulgebaseerde) positie binnen dezelfde batch staat (dezelfde volgorde als de `candidates[]`-array/`candidateIndex`). Lukt dit, dan wordt de kandidaat normaal gepersisteerd en wordt in de mapping expliciet gelogd dat resolutie via het legacy positionele fallbackpad is verlopen.
- Lukt resolutie niet — noch via een bestaande `candidateKey`, noch via een geldige legacy-positie binnen de batch — dan wordt uitsluitend die specifieke kandidaat niet in `story_candidate` gepersisteerd/gepubliceerd en wordt dit als fout gelogd (inclusief de onvertaalde `dependsOn`-waarde); overige kandidaten in de batch, ook kandidaten die niet van de geblokkeerde kandidaat afhangen, worden normaal gepersisteerd.
- Na het vastleggen van de batch is de volledige sleutel-naar-backlog-ID-mapping (inclusief eventuele legacy-resoluties en geblokkeerde kandidaten) duurzaam en achteraf naar te zoeken vastgelegd — niet uitsluitend als vluchtige applicatielog. Het gerenderde dossier (`ShadowDossierRenderer.kt`) is hiervoor een geschikte plek, aangezien dat al per kandidaat `candidateKey`/`resolvedDependsOn` toont en al gepubliceerd wordt naar de workspace-repo; de exacte opslagvorm is een implementatiekeuze zolang de mapping na afloop verifieerbaar is.
- Twee geautomatiseerde tests (bijv. uitbreiding van `ShadowIterationEngineTest.kt`) tonen aan: (1) een batch met een opzettelijk niet-bestaande `dependsOn`-sleutel blokkeert publicatie van uitsluitend de afhankelijke kandidaat, terwijl overige kandidaten normaal publiceren; (2) een batch met een `dependsOn`-waarde in het oude "Kandidaat N"-formaat resolveert correct via het legacy-fallbackpad en wordt als zodanig in de mapping-log gemarkeerd.
- `story_candidate.id`, `WorkspacePublisher.kt`, `AutonomousDelivery.kt`, `StoryCandidateApi.kt` en hkh/hkh-autopilot blijven ongewijzigd van gedrag; er wordt geen wijziging aangebracht aan Software Factory of aan authenticatie-/PR-goedkeuringsflows.

## Aannames
- "Publiceren van een story-batch" in deze story betekent het definitief vastleggen van gevalideerde kandidaten in Product Factory's eigen backlogtabel `story_candidate` (via `ShadowIterationEngine`/`ShadowDossierRenderer`), niet de latere, asynchrone levering aan Software Factory (`AutonomousDelivery.kt`/`StoryCandidateApi.kt`) en niet de git-publicatie van het dossier (`WorkspacePublisher.kt`) — die twee paden lezen `dependsOn` bewust niet en blijven buiten scope.
- "Definitief, doorlopend backlog-ID" is `story_candidate.id`, niet het externe `externalStoryKey` van Software Factory.
- De precondition is voldaan: product-factory-8 (stable-candidate-key) is daadwerkelijk geïmplementeerd (gemerged, commit 2e135cc), dus deze story wordt uitgevoerd en niet geskipt.

## Eindsamenvatting

Ik heb voldoende context uit `.task.md` en het worklog. Hierbij de eindsamenvatting.

## Eindsamenvatting — product-factory-9: Resolve-stap dependsOn → backlog-ID bij batchpublicatie

**Wat is gebouwd**

Bij het definitief vastleggen van een storybatch in Product Factory's interne backlog (`story_candidate`) wordt elke `dependsOn`-verwijzing nu vertaald naar het echte, blijvende backlog-ID van de betreffende kandidaat, in plaats van een tijdelijke batch-sleutel:

- `ShadowDossierRenderer.kt` kreeg een nieuwe resolver (`resolveDependencyReferences`) die per `dependsOn`-waarde eerst zoekt op `candidateKey` binnen de batch, en pas daarna terugvalt op het oude "Kandidaat N"-positieformaat (legacy).
- De batch-brede afwijzing die eerder een *hele* batch blokkeerde zodra één kandidaat het oude "Kandidaat N"-formaat gebruikte, is verwijderd. Dat formaat wordt nu automatisch herkend en waar mogelijk vertaald.
- Kan een `dependsOn`-waarde niet vertaald worden (onbestaande sleutel, of legacy-positie buiten bereik), dan wordt **alleen die ene kandidaat** geblokkeerd en niet gepersisteerd/gepubliceerd; de rest van de batch gaat gewoon door — zonder cascade-effecten.
- `story_candidate.id` wordt nu correct teruggegeven bij het opslaan (via `GeneratedKeyHolder` in `ShadowIterationApi.kt`).
- De volledige sleutel-naar-backlog-ID-mapping (inclusief legacy-resoluties en blokkades) wordt duurzaam vastgelegd als nieuw artefact (`dependson_resolution`) in de bestaande `shadow_iteration_artifact`-tabel, zichtbaar in het dossier — ook voor batches die niet worden gepubliceerd.

**Gemaakte keuzes**

- Vertaling verloopt in twee stappen: eerst `candidateKey`-lookup, dan legacy-positionele fallback — conform de acceptatiecriteria.
- Blokkade is strikt per kandidaat: een kandidaat die zelf naar een geblokkeerde kandidaat verwijst (via een wél geldige sleutel/positie) resolveert en publiceert gewoon normaal.
- Mapping wordt onvoorwaardelijk gelogd, ook bij REVISE/REJECT/DUPLICATE-uitkomsten, zodat de historie altijd doorzoekbaar is.

**Wat is getest**

- Twee nieuwe/aangepaste tests in `ShadowIterationEngineTest.kt`: (1) een niet-bestaande `dependsOn`-sleutel blokkeert uitsluitend de afhankelijke kandidaat, overige kandidaten publiceren normaal; (2) een legacy "Kandidaat N"-verwijzing resolveert correct via het positionele fallbackpad en wordt als zodanig gemarkeerd in de mapping.
- Reviewer en tester hebben onafhankelijk het volledige vangnet (`mvn clean verify`, volledige reactor) groen gedraaid, inclusief 9/9 tests in `ShadowIterationEngineTest`.
- Preview-omgeving gecontroleerd (health check 200); verdere browserverificatie was niet van toepassing, want deze story betreft uitsluitend interne backend-persistentielogica zonder UI-oppervlak.

**Bewust niet gedaan**

- `story_candidate.id`-semantiek, `WorkspacePublisher.kt`, `AutonomousDelivery.kt`, `StoryCandidateApi.kt` en hkh/hkh-autopilot zijn ongewijzigd gebleven, conform scope.
- Geen wijzigingen aan Software Factory of aan authenticatie-/PR-goedkeuringsflows.

<!-- deploy-summary:start -->
Bij het definitief opslaan van een groep nieuwe, samenhangende ideeën in de backlog worden onderlinge koppelingen tussen die ideeën nu betrouwbaar en blijvend vastgelegd. Een idee met een verwijzing die niet klopt, wordt niet meer opgeslagen, maar de rest van de groep gaat gewoon door in plaats van dat alles vastloopt. Ook oudere, verouderde verwijzingen worden nu automatisch herkend en zoveel mogelijk correct opgelost.
<!-- deploy-summary:end -->
