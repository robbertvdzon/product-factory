# product-factory-14 - Toon leesbare, veldspecifieke tekst naast de bestaande ruwe JSON per agentrol in het iteratie-detaildialoog

## Story

Toon leesbare, veldspecifieke tekst naast de bestaande ruwe JSON per agentrol in het iteratie-detaildialoog

<!-- refined-by-factory -->

## Samenvatting
In het iteratie-detaildialoog toont de app het werk van elke agentrol (Onderzoeker, Product owner, UX-ontwerp, Story writer, Criticus) nu alleen als ruwe JSON-tekst met accolades — moeilijk leesbaar voor gebruikers. Deze wijziging toont daarnaast een nette, leesbare samenvatting per rol (lopende tekst en lijstjes), zoals dat elders in de app (het storykandidaat-dialoog) al gebeurt. De ruwe JSON blijft altijd zichtbaar als achtervang. Als een resultaat een onverwachte vorm heeft, blijft alleen de ruwe JSON zichtbaar — de app crasht nooit.

## Scope
- Bestand: `dashboard-frontend/lib/main.dart`, sectie "Resultaat en onderbouwing" in `IterationSessionDialog` (huidige `...artifacts.map(...)`-blok, ca. regel 990-1004), dat elk artefact als `ExpansionTile` met `SelectableText(_prettyJson(...))` rendert.
- Toevoegen: per artefact, bovenaan de (reeds uitgeklapte) tegel-inhoud, vóór de bestaande ruwe-JSON-sectie, een leesbare weergave van de bekende tekstuele velden — analoog aan hoe `_showStoryCandidateDetails` (main.dart, ca. regel 1233-1303) description/acceptanceCriteria/criticReason als platte `SelectableText`-blokken met kopjes toont.
- De backend-schema's per rol (`productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowSchemas.kt`) zijn `additionalProperties:false` met vaste, afgedwongen veldnamen en zijn dus de autoritatieve bron voor de "bekende velden"-inspectie die de acceptatiecriteria vragen. Bekende velden per rol/artifactType (basisnaam vóór een eventuele `-2`/`-3`-suffix, zie hieronder):
  - `researcher`: `summary` (tekst), `findings[].{title,finding,sourceUrls[]}`, `currentState.{purpose,gaps[]}`, `improvementOpportunities[]`, `sources[].{url,rationale}`, `inspiration[].{name,url,relevance}`.
  - `product_owner`: `productDirection` (tekst), `rationale` (tekst), `priorities[]`, `decisions[].{decision,rationale,sourceUrls[]}`, `rejectedOptions[]`.
  - `ux_designer`: `flowName`, `userGoal`, `steps[]`, `wireframe`, `hypotheses[]`, `accessibility[]`, `privacyConsiderations[]`.
  - `story_writer`: `candidates[].{candidateKey,title,description,acceptanceCriteria[],dependsOn[],risks[]}`.
  - `critic`: `overallVerdict`, `summary` (tekst), `issues[].{severity,category,description}`, `candidateReviews[].{verdict,reason}`, `requiredChanges[]`.
  - De implementerende agent bevestigt dit tegen de code (schema's kunnen wijzigen) en documenteert de definitieve lijst; dit is het startpunt, geen blinde aanname.
- `artifact['artifactType']` bevat bij een retry-poging een suffix (`-2`, `-3`, zie `ShadowIterationApi.kt` regel 215, en bestaande `_roleLabel`-mapping voor `story_writer-2`/`critic-2`/`-3`). De leesbare-velden-matching moet op de basisrolnaam (zonder suffix) werken, zodat retries dezelfde leesbare weergave krijgen als de eerste poging.
- Geen wijziging aan backend, API, database of de bestaande ruwe-JSON-weergave zelf; puur additieve, isoleerbare frontend-rendering binnen `IterationSessionDialog`.

## Acceptance criteria
1. De implementerende agent inspecteert/bevestigt geautomatiseerd (of tegen de broncode van) `ShadowSchemas.kt` de daadwerkelijke `contentJson`-structuur per rol (Onderzoeker/`researcher`, Product owner/`product_owner`, UX-ontwerp/`ux_designer`, Story writer/`story_writer`, Criticus/`critic`) en documenteert de gebruikte veldnamen als "bekende velden"-lijst in de implementatie (bv. als commentaar of losse mapping-structuur), als basis voor de renderlogica.
2. Voor elk artefact waarvan de basisrolnaam (artifactType zonder `-2`/`-3`-suffix) een bekende-veldenlijst heeft, toont het dialoog deze velden als leesbare tekst en/of opsomming bovenaan de uitgeklapte roltegel — zichtbaar zonder extra klik zodra de tegel is uitgeklapt — zonder de bestaande `SelectableText(_prettyJson(...))`-sectie te verwijderen.
3. Voor een artefact met een JSON-structuur die niet (volledig) decodeerbaar is als het bekende schema van zijn basisrolnaam, of met een onbekende/afwijkende artifactType, blijft uitsluitend de bestaande ruwe-JSON-weergave zichtbaar; er verschijnt geen lege of foutieve leesbare sectie, en de app crasht niet (geen onafgevangen decode-exceptie).
4. Velden die leeg zijn, `null`, of een lege lijst/array, worden volledig weggelaten uit de leesbare weergave (geen zichtbare tekst "null", geen lege opsommingstekens, geen lege kopjes zonder inhoud).
5. Er is een geautomatiseerde widget- of unittest die minimaal drie gevallen dekt:
   - een `researcher`-artefact met tekstuele `findings` → leesbare tekst verschijnt;
   - een `product_owner`-artefact met een `decisions`-array inclusief `rationale` → leesbaar getoond;
   - een artefact met een niet-herkende structuur → alleen de ruwe-JSON-fallback is zichtbaar, geen crash.
6. Een regressietest bevestigt dat na deze wijziging de bestaande classificatiebadge, het foutredenblok bij `FAILED`, en de "Samenvatting voor jou"-kaart (gepubliceerde kandidaten) ongewijzigd zichtbaar en functioneel blijven.

## Aannames
- "Bovenaan de tegel, zichtbaar zonder extra klik" betekent: zodra de gebruiker de `ExpansionTile` van de rol uitklapt (bestaand gedrag, één klik), verschijnt de leesbare weergave direct — er is geen tweede interactie (bv. een aparte sub-toggle) nodig om van ruwe JSON naar leesbare tekst te wisselen.
- De leesbare weergave wordt met platte Flutter-widgets (`Text`/`SelectableText`/kopjes/opsommingen) opgebouwd, analoog aan `_showStoryCandidateDetails`, zonder nieuwe afhankelijkheden.
- De "bekende velden"-lijst wordt per basisrolnaam (na het strippen van een eventuele `-2`/`-3`-attempt-suffix) toegepast; een retry-artefact van dezelfde rol gebruikt dezelfde leesbare-veldenlogica als de eerste poging.
- De `SUMMARY`-rol (los backend-veld `shadow_iteration.summary`, niet een van de vijf genoemde rollen) valt buiten scope van deze story, conform de oorspronkelijke opsomming (Onderzoeker, Product owner, UX-ontwerp, Story writer, Criticus).

## Eindsamenvatting

Nu heb ik voldoende context. Ik lever de eindsamenvatting.

## Eindsamenvatting — product-factory-14: Leesbare veldweergave per agentrol in het iteratie-detaildialoog

**Wat is gebouwd**
In het iteratie-detaildialoog (`IterationSessionDialog`, `dashboard-frontend/lib/main.dart`) toont de app naast de bestaande ruwe JSON nu ook een leesbare samenvatting per agentrol (Onderzoeker, Product owner, UX-ontwerp, Story writer, Criticus), opgebouwd uit de bekende tekstvelden per rol (bv. `summary`, `findings`, `decisions`/`rationale`, `steps`, `candidates`, `issues`). Deze weergave verschijnt direct zodra de gebruiker de roltegel uitklapt, zonder extra klik, en staat boven de ongewijzigde ruwe-JSON-sectie.

**Belangrijke keuzes**
- De "bekende velden" per rol zijn afgeleid en bevestigd tegen de backend-schema's (`ShadowSchemas.kt`), die `additionalProperties:false` afdwingen en dus autoritatief zijn.
- Retry-artefacten (`artifactType` met `-2`/`-3`-suffix) worden vóór matching gestript naar de basisrolnaam, zodat een tweede of derde poging dezelfde leesbare weergave krijgt als de eerste.
- Bij een niet-decodeerbare of onherkende structuur valt de app veilig terug op alleen de ruwe JSON — geen crash, geen lege sectie.
- Lege, `null`- of lege-lijst-velden worden volledig weggelaten uit de leesbare weergave.

**Getest**
Nieuwe geautomatiseerde tests in `dashboard-frontend/test/iteration_readable_artifact_fields_test.dart` dekken: een researcher-artefact met tekstuele findings, een product_owner-artefact met decisions/rationale, een onbekende artifactType (fallback), een bekende rol met afwijkende JSON-vorm (partial fallback zonder crash), niet-decodeerbare content (fallback zonder crash), en een regressietest die bevestigt dat classificatiebadge, FAILED-foutredenblok en de "Samenvatting voor jou"-kaart ongewijzigd blijven werken. Volledig vangnet gedraaid en groen: `flutter analyze` (0 issues), `flutter test` (99/99 groen), en `mvn clean verify` (backend groen, ter bevestiging — geen backendwijzigingen in deze story).

**Bewust niet gedaan**
Geen wijzigingen aan backend, API, database of de bestaande ruwe-JSON-weergave; de `SUMMARY`-rol (los backendveld `shadow_iteration.summary`) blijft buiten scope, conform de oorspronkelijke afbakening.

<!-- deploy-summary:start -->
In het detailscherm van een iteratie is het werk van elke betrokken rol (zoals de onderzoeker, product owner en criticus) nu ook in gewone, leesbare tekst te zien, naast de technische ruwe gegevens die er al stonden. Zo is in één oogopslag duidelijk wat er is bevonden of besloten, zonder dat je zelf technische tekst hoeft te ontcijferen. Als een resultaat een ongebruikelijke vorm heeft, blijft alleen de bestaande technische weergave zichtbaar en werkt het scherm gewoon door.
<!-- deploy-summary:end -->
