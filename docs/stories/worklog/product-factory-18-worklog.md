# product-factory-18 - Worklog

Story-context bij eerste pickup:
Implementeer zichtbaar 'Reden'-blok bij NEEDS_REVISION/REJECTED in IterationSessionDialog

Voeg in dashboard-frontend/lib/main.dart, binnen _IterationSessionDialogState.build, direct na het 'Opdracht'-blok en vóór de roltegels/'Voortgang'-sectie, een nieuw 'Reden'-blok toe dat uitsluitend rendert bij iteration['status'] gelijk aan NEEDS_REVISION of REJECTED. Zoek in artifacts naar het criticus-artefact voor deze iteratie (artifactType 'critic' of met retrysuffix '-2'/'-3', via dezelfde stripping als _readableArtifactFields/_roleLabel al toepassen); bij meerdere criticus-artefacten het meest recente (hoogste suffix, anders laatst voorkomende) gebruiken. Parse contentJson van dat artefact volgens het bestaande critic-schema uit ShadowSchemas.kt (overallVerdict, summary, requiredChanges[], evt. issues[]/candidateReviews[]) en bouw daaruit leesbare, lopende tekst zonder rauwe JSON/sleutel-waardeparen, met zoveel mogelijk hergebruik van bestaande helpers (_readableText/_readableBulletList-achtige opbouw). Ontbreekt het criticus-artefact, toon dan de vaste tekst 'Criticus-oordeel ontbreekt voor deze cyclus'. Style het blok identiek aan het bestaande foutredenblok (titel + SelectableText, Semantics(label:'Reden: <tekst>') rond ExcludeSemantics-child). Laat het bestaande foutredenblok (FAILED) en de bestaande criticus-roltegel ongewijzigd. Geen wijzigingen aan classification.dart, ShadowSchemas.kt of backend/API. Schrijf hierbij ook alle nieuwe/uitgebreide widgettests: aanwezigheid van het blok bij NEEDS_REVISION/REJECTED met leesbare tekst (regex-check op afwezigheid van rauwe JSON-patronen), fallbacktekst bij ontbrekend criticus-artefact, afwezigheid van het blok bij ACCEPTED/PENDING/QUEUED/RUNNING, controle van de Semantics-boom, en regressietests die bevestigen dat het foutredenblok (FAILED) en de criticus-roltegel ongewijzigd blijven.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Nieuw 'Reden'-blok toegevoegd in `dashboard-frontend/lib/main.dart` binnen
  `_IterationSessionDialogState.build`, direct na het 'Opdracht'-blok en vóór 'Voortgang',
  zichtbaar uitsluitend bij `status` `NEEDS_REVISION`/`REJECTED`. Stijl (titel + `SelectableText`,
  `Semantics(label: 'Reden: <tekst>')` rond `ExcludeSemantics`) is analoog aan het bestaande
  FAILED-foutredenblok.
- Twee nieuwe publieke pure helpers toegevoegd (top-level, geen `_`-prefix, zodat ze los
  testbaar zijn vanuit `test/`, analoog aan het bestaande `humanizeFieldKey`-patroon):
  - `latestCriticArtifact(artifacts)`: zoekt artefacten met `artifactType` `critic`/`critic-<n>`
    (zelfde `-\d+$`-stripping als `_readableArtifactFields`) en geeft het meest recente terug
    (hoogste suffix wint; bij gelijke/geen suffix wint het laatst voorkomende exemplaar).
  - `criticReasonSummary(contentJson)`: parseert `contentJson` volgens het bestaande
    `critic`-schema uit `ShadowSchemas.kt` (`overallVerdict`, `summary`, `requiredChanges[]`) en
    bouwt daaruit leesbare, lopende tekst (géén rauwe JSON/sleutel-waardeparen).
  - Ontbreekt het criticus-artefact of levert het geen bruikbare tekst op, dan toont het blok de
    vaste tekst 'Criticus-oordeel ontbreekt voor deze cyclus'.
- Geen wijzigingen aan `classification.dart`, `ShadowSchemas.kt` of backend/API; het bestaande
  FAILED-foutredenblok en de criticus-roltegel (met volledig artefact + technische-details-toggle)
  blijven ongewijzigd.
- Nieuw testbestand `dashboard-frontend/test/iteration_session_reason_block_test.dart`:
  widgettests voor aanwezigheid/leesbare tekst bij NEEDS_REVISION/REJECTED (incl. regex-check op
  afwezigheid van `{"`/`":"`-patronen), keuze van het meest recente criticus-artefact bij retries,
  fallbacktekst zonder criticus-artefact, afwezigheid van het blok bij
  ACCEPTED/PENDING/QUEUED/RUNNING, de `Semantics`-boom, en regressietests dat het FAILED-blok en de
  criticus-roltegel ongewijzigd blijven; plus losse `test()`-unittests voor `latestCriticArtifact`
  en `criticReasonSummary`.
- Vangnet gedraaid: `flutter analyze` (geen issues), `flutter test` (133/133 groen, incl. nieuwe
  tests), `mvn -B --no-transfer-progress clean verify` vanaf de repo-root (BUILD SUCCESS, 0
  failures/errors over alle modules). `.factory/verification.yaml` ongewijzigd gelaten: de
  bestaande entries dekken deze wijziging al volledig (uitsluitend
  `dashboard-frontend/lib/main.dart` + nieuw testbestand onder `dashboard-frontend/`).
- Niet gedaan / bewust buiten scope: geen wijziging aan de bredere hoofdschermherstructurering,
  geen onderzoek naar de live NEEDS_REVISION-iteratie zonder criticus-artefact op
  shadow-hkh-autopilot-0003 (expliciet buiten scope per de story-aannames).

## Tester (product-104)

- Diff van deze story raakt uitsluitend `dashboard-frontend/lib/main.dart`,
  `dashboard-frontend/test/iteration_session_reason_block_test.dart` en de worklog; volgens
  `.factory/verification.yaml`-pathPrefixes zijn alleen `dashboard-flutter-analyze` en
  `dashboard-flutter-test` van toepassing.
- `flutter analyze` (dashboard-frontend): No issues found!
- `flutter test` (dashboard-frontend): 133/133 groen, exitcode 0, twee keer gedraaid (incl.
  losse herrun) — geen failures/errors, ook niet flaky. Nieuwe testfile
  `iteration_session_reason_block_test.dart` dekt alle acceptance criteria (Reden-blok bij
  NEEDS_REVISION/REJECTED met leesbare tekst zonder rauwe JSON, fallback zonder criticus-artefact,
  meest-recente-retry-selectie, afwezigheid bij ACCEPTED/PENDING/QUEUED/RUNNING, Semantics-boom,
  regressie op bestaand FAILED-blok en criticus-roltegel).
- Codeverificatie van `latestCriticArtifact`/`criticReasonSummary` en de renderlogica in
  `IterationSessionDialog` tegen de story-scope: komt overeen met de acceptance criteria, geen
  nieuwe API-velden, geen wijziging aan `classification.dart`/`ShadowSchemas.kt`.
- Geen preview-omgeving beschikbaar in de agentcontainer (geen browsertool); zie bestaande
  agent-tip `dashboard-frontend-preview-now-available`/`geen-preview-omgeving`. Interactieve
  E2E-/screenshotverificatie was daarom niet mogelijk; verificatie leunt op widgettests +
  codeinspectie, conform eerdere testerrondes in dit repo.
- Oordeel: `tested` — vangnet voor deze diff (flutter analyze + flutter test) volledig groen,
  geen regressies, gedrag komt overeen met de story.
