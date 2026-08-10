# product-factory-18 - Toon direct zichtbaar 'Reden'-blok bij NEEDS_REVISION/REJECTED, gevuld uit Criticus-artefact of expliciete fallback

## Story

Toon direct zichtbaar 'Reden'-blok bij NEEDS_REVISION/REJECTED, gevuld uit Criticus-artefact of expliciete fallback

<!-- refined-by-factory -->

## Samenvatting
Bij een productcyclus die door de eigen criticus is afgewezen of om revisie vraagt, zie je nu niet direct waarom — die motivatie zit verstopt in een ingeklapte roltegel. Deze story voegt een duidelijk zichtbaar 'Reden'-blok toe, direct onder de opdracht, met dezelfde stijl als het bestaande foutreden-blok bij mislukte cycli. Is er geen oordeel van de criticus beschikbaar (wat soms voorkomt), dan meldt het blok dat expliciet in plaats van niets te tonen.

## Scope
- Wijziging uitsluitend in `dashboard-frontend/lib/main.dart`, binnen `IterationSessionDialog` (rond regel 1031-1067, direct na het bestaande 'Opdracht'-blok en vóór de roltegels/'Voortgang'-sectie).
- Nieuw 'Reden'-blok, zichtbaar uitsluitend bij `iteration['status']` gelijk aan `NEEDS_REVISION` of `REJECTED` (niet bij `ACCEPTED`, `PENDING`, `QUEUED`, `RUNNING`).
- Het blok zoekt in `artifacts` (uit de sessie-response) naar het criticus-artefact voor deze iteratie: `artifactType` gelijk aan `critic` of met een retry-suffix (`critic-2`, `critic-3`, …, via dezelfde stripping als `_readableArtifactFields`/`_roleLabel` al toepassen). Bij meerdere criticus-artefacten (retries) wordt het meest recente (hoogste attempt / laatste in de lijst) gebruikt.
- `contentJson` van dat artefact wordt geparsed volgens het bestaande, autoritatieve schema in `ShadowSchemas.kt` (`critic`): `overallVerdict` (ACCEPT/REVISE/REJECT), `summary`, `requiredChanges[]`, eventueel `issues[]`/`candidateReviews[]`. Hiervan wordt een leesbare samenvattingstekst opgebouwd (géén rauwe JSON, geen accolades/aanhalingstekens rond sleutel-waardeparen) — bijvoorbeeld eindoordeel + summary + opsomming van requiredChanges, in lopende tekst/opsomming zoals de bestaande `_readableText`/`_readableBulletList`-helpers dat al doen voor de roltegel.
- Is er geen criticus-artefact voor deze iteratie (ondanks status NEEDS_REVISION/REJECTED), dan toont het blok in plaats daarvan de vaste tekst 'Criticus-oordeel ontbreekt voor deze cyclus' (geen stacktrace, geen interne paden).
- Het blok krijgt dezelfde visuele stijl (titel + `SelectableText`) en toegankelijkheidspatroon als het bestaande foutreden-blok: een `Semantics`-label (bv. `'Reden: <tekst>'`) rond een `ExcludeSemantics`-child, zodat het als één blok vóór de roltegels wordt aangekondigd en in dezelfde taborde bereikbaar is.
- Het bestaande foutreden-blok (status FAILED) en de bestaande, standaard ingeklapte criticus-roltegel met volledig artefact blijven ongewijzigd zichtbaar en functioneel.
- Geen nieuwe API-velden, geen wijziging aan `classification.dart`, geen wijziging aan HKH Autopilot of aan `ShadowSchemas.kt`/backend-datamodel. Puur een renderlaag-toevoeging in `IterationSessionDialog` op basis van reeds beschikbare `artifacts`-data.
- De bredere hoofdschermherstructurering (zes gelijkwaardige secties) blijft expliciet buiten scope.

## Acceptance criteria
- Bij status `NEEDS_REVISION` of `REJECTED` toont `IterationSessionDialog` een 'Reden'-blok direct onder 'Opdracht' en vóór de roltegels-sectie, visueel gestyled (titel + tekstkader) analoog aan het bestaande foutreden-blok.
- Is er een criticus-artefact (`artifactType` `critic` of `critic-<n>`) voor deze iteratie aanwezig, dan bevat het Reden-blok niet-lege, leesbare tekst afgeleid van `overallVerdict`/`summary`/`requiredChanges` (of de daadwerkelijk aanwezige subset daarvan) uit dat artefact, zonder rauwe JSON-notatie. Een widgettest met een gemockte iteratie + criticus-artefact controleert dit (bv. via een regex die controleert dat er geen `{"..."` / `":"`-patronen in de getoonde tekst zitten).
- Is er geen criticus-artefact voor deze iteratie, dan toont het Reden-blok de vaste tekst 'Criticus-oordeel ontbreekt voor deze cyclus'. Een widgettest met een gemockte iteratie zonder criticus-artefact (status NEEDS_REVISION of REJECTED) controleert dit.
- Bij status `ACCEPTED` of `PENDING` (en analoog bij `QUEUED`/`RUNNING`) wordt géén Reden-blok gerenderd; een widgettest bevestigt de afwezigheid ervan in de widget tree voor deze statussen.
- Het bestaande foutreden-blok bij status `FAILED` en de bestaande, standaard ingeklapte criticus-roltegel met volledig artefact blijven ongewijzigd zichtbaar/functioneel; een regressietest dekt beide (bv. uitbreiding van of aanvulling op bestaande tests rond `IterationSessionDialog`).
- Het Reden-blok heeft een `Semantics`-label (bv. `'Reden: <tekst>'`) zodat het voor schermlezers als eigen blok vóór de roltegels wordt aangekondigd, en zit in dezelfde taborde/scroll-volgorde als het foutreden-blok (widgettest op de `Semantics`-boom).
- Er wordt geen nieuw API-veld, geen wijziging aan `classification.dart`, geen wijziging aan `ShadowSchemas.kt` of ander backend-/HKH-Autopilot-gedrag geïntroduceerd; de wijziging blijft beperkt tot `dashboard-frontend/lib/main.dart` (renderlaag van `IterationSessionDialog`).
- Nieuwe/aangepaste Dart-tests slagen (`flutter test` in `dashboard-frontend`) zonder regressie in bestaande tests (o.a. `iteration_progress_indicator_test.dart` en overige `IterationSessionDialog`-tests).

## Aannames
- "Recentste criticus-artefact" bij meerdere pogingen betekent: het artefact met de hoogste retry-suffix (`critic-3` > `critic-2` > `critic`), consistent met hoe `_roleLabel` retries al labelt; is er geen suffix-onderscheid mogelijk, dan het laatst voorkomende `critic`-artefact in de `artifacts`-lijst.
- De samenvattingstekst mag hergebruik maken van de bestaande `_readableText`/`_readableBulletList`-achtige opbouw (leesbare labels, geen sleutel-waardeparen), zolang het eindresultaat geen rauwe JSON-notatie bevat; de exacte tekstindeling (bv. wel/niet alle `issues[]` tonen) is aan de implementerende agent, mits `overallVerdict`, `summary` en `requiredChanges` er herkenbaar in terugkomen.
- "Toetsenbord-bereikbaar in dezelfde taborde als het foutreden-blok" betekent hier: het blok bevindt zich in dezelfde scrollbare `ListView`/focusvolgorde vóór de roltegels-sectie, net als het foutreden-blok nu al is (er is geen apart interactief element nodig, aangezien het foutreden-blok ook geen eigen focusbare widget heeft — puur `SelectableText`).
- De live geconstateerde NEEDS_REVISION-iteratie zonder zichtbaar criticus-artefact op shadow-hkh-autopilot-0003 wordt niet apart onderzocht of gerepareerd in deze story; het Reden-blok's fallbacktekst is de enige vereiste reactie op dat scenario.

## Eindsamenvatting

Alle benodigde context is verzameld uit `.task.md` en de worklog. Hier is de eindsamenvatting.

## Eindsamenvatting — product-factory-18: Reden-blok bij NEEDS_REVISION/REJECTED

**Wat is gebouwd**
In `dashboard-frontend/lib/main.dart` is binnen `IterationSessionDialog` een nieuw, direct zichtbaar 'Reden'-blok toegevoegd, geplaatst net onder het 'Opdracht'-blok en vóór de roltegels/'Voortgang'-sectie. Het blok rendert uitsluitend bij iteratiestatus `NEEDS_REVISION` of `REJECTED`.

**Gemaakte keuzes**
- Twee nieuwe pure helperfuncties toegevoegd: `latestCriticArtifact(artifacts)` zoekt het meest recente criticus-artefact (`critic`, `critic-2`, …; bij retries wint de hoogste suffix, anders het laatst voorkomende exemplaar), en `criticReasonSummary(contentJson)` zet het bestaande critic-schema (`overallVerdict`, `summary`, `requiredChanges[]`) om naar leesbare lopende tekst zonder rauwe JSON.
- Ontbreekt het criticus-artefact (of levert het geen bruikbare tekst op), dan toont het blok de vaste tekst "Criticus-oordeel ontbreekt voor deze cyclus" — geen technische details of paden.
- Visuele stijl en toegankelijkheid (titel + `SelectableText`, `Semantics(label: 'Reden: ...')` rond `ExcludeSemantics`) zijn identiek aan het bestaande FAILED-foutredenblok, zodat het blok net zo door schermlezers wordt aangekondigd.
- Het bestaande FAILED-foutredenblok en de standaard ingeklapte criticus-roltegel met volledig artefact zijn ongewijzigd gebleven.
- Geen wijzigingen aan `classification.dart`, `ShadowSchemas.kt` of de backend/API — puur een renderlaag-toevoeging, zoals afgesproken in de scope.

**Wat is getest**
- Nieuw testbestand `dashboard-frontend/test/iteration_session_reason_block_test.dart` met widgettests voor: aanwezigheid en leesbare tekst bij NEEDS_REVISION/REJECTED (met regex-check dat er geen rauwe JSON-patronen in de tekst staan), keuze van het meest recente criticus-artefact bij retries, de fallbacktekst zonder criticus-artefact, afwezigheid van het blok bij ACCEPTED/PENDING/QUEUED/RUNNING, de Semantics-boom, en regressie op het bestaande FAILED-blok en de criticus-roltegel. Losse unittests dekken de twee nieuwe helperfuncties.
- `flutter analyze`: geen issues. `flutter test`: 133/133 groen (tweemaal gedraaid, ook los herrund, geen flakiness). `mvn clean verify` vanaf de repo-root: BUILD SUCCESS.
- Geen interactieve preview/E2E-verificatie mogelijk (geen browsertool in de agentcontainer); verificatie leunde op widgettests + codeinspectie, conform eerdere rondes in dit repo.

**Bewust niet gedaan**
- De bredere hoofdschermherstructurering (zes gelijkwaardige secties) blijft buiten scope.
- De live geconstateerde NEEDS_REVISION-iteratie zonder zichtbaar criticus-artefact op `shadow-hkh-autopilot-0003` is niet apart onderzocht of gerepareerd; de fallbacktekst van het Reden-blok is de enige vereiste reactie op dat scenario.

<!-- deploy-summary:start -->
Als een cyclus wordt afgekeurd of om aanpassing vraagt, zie je nu direct en duidelijk waarom — zonder dat je eerst iets moet openklikken. Is die reden onverwacht niet beschikbaar, dan zie je een duidelijke melding daarover in plaats van niets. Er is verder niets veranderd aan hoe de rest van het scherm werkt.
<!-- deploy-summary:end -->
