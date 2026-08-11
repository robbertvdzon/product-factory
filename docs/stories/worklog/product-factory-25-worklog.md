# product-factory-25 - Worklog

Story-context bij eerste pickup:
Verdict-tekst tonen bij criticVerdict aanwezig zonder criticus-artefact

Wijzig IterationSessionDialog (dashboard-frontend/lib/main.dart, Reden-blok ~regel 1071-1101): voeg een derde detectiepad toe naast de twee bestaande - criticArtifact afwezig én criticVerdict aanwezig toont voortaan een tekst met de letterlijke verdict-waarde plus een expliciete 'geen onderliggend criticus-artefact beschikbaar'-melding, in plaats van de onvoorwaardelijke 'Criticus-oordeel ontbreekt voor deze cyclus'. De twee bestaande paden (criticArtifact aanwezig → criticReasonSummary; criticArtifact afwezig + criticVerdict==null → missingCriticReasonText/vaste fallback) blijven ongewijzigd, evenals de guardrail-alinea bij REJECTED+criticVerdict=='ACCEPT'. Geen nieuw API-veld, geen wijziging aan ShadowIterationEngine.kt of PreviewDataSeeder.kt. Pas de bestaande test in dashboard-frontend/test/iteration_session_reason_block_test.dart (regel 354-383) aan, die nu nog het te herstellen bug-gedrag verifieert, en voeg een nieuwe widgettest toe die specifiek het scenario 'criticVerdict aanwezig, artefact/stap afwezig' dekt (incl. samenloop met de guardrail-alinea), met behulp van de bestaande _sessionWith(...)-helper. Werk daarnaast docs/factory/functional-spec.md (Reden-blok-alinea, ~regel 76-108) bij zodat de drie deelcasussen correct gedocumenteerd staan.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `dashboard-frontend/lib/main.dart` (Reden-blok, `IterationSessionDialog`): derde detectiepad
  toegevoegd. Als `criticArtifact == null` maar `iteration['criticVerdict'] != null`, toont het
  blok nu de tekst uit de nieuwe helper `criticVerdictWithoutArtifactText(criticVerdict)`
  ('Criticusoordeel $criticVerdict geregistreerd, maar geen onderliggend criticus-artefact
  beschikbaar.') in plaats van de onvoorwaardelijke 'Criticus-oordeel ontbreekt voor deze
  cyclus'-tekst. De twee bestaande paden (artefact aanwezig → `criticReasonSummary`;
  artefact afwezig + `criticVerdict == null` → `missingCriticReasonText`/vaste fallback) zijn
  ongewijzigd gebleven, evenals de guardrail-alinea bij `REJECTED` + `criticVerdict == 'ACCEPT'`
  (die combineert nu ook met de nieuwe verdict-tekst).
- `dashboard-frontend/test/iteration_session_reason_block_test.dart`: bestaande regressietest die
  het te herstellen bug-gedrag verifieerde, aangepast naar het nieuwe verwachte gedrag; drie nieuwe
  widgettests toegevoegd voor NEEDS_REVISION met verdict zonder artefact, REJECTED met verdict
  zonder artefact/steps, en de samenloop met de guardrail-toelichting bij criticVerdict 'ACCEPT'.
- `docs/factory/functional-spec.md` (Reden-blok-alinea, ~regel 76-108): bijgewerkt zodat de drie
  deelcasussen (criticVerdict null zonder artefact / criticVerdict aanwezig zonder artefact /
  artefact aanwezig) expliciet en correct gedocumenteerd staan.
- Vangnet: `flutter analyze` (0 issues), `flutter test` (158/158 geslaagd, incl. de
  gewijzigde/nieuwe tests in `iteration_session_reason_block_test.dart`),
  `mvn -B --no-transfer-progress clean verify` (backend, ongewijzigd geraakt door deze story maar
  volledig gedraaid volgens het verplichte vangnet).
- `.factory/verification.yaml` behoefde geen wijziging: alle gewijzigde bestanden vallen al onder
  bestaande `pathPrefixes` (`dashboard-frontend/`).
- Geen nieuw API-veld, geen wijziging aan `ShadowIterationEngine.kt` of `PreviewDataSeeder.kt`.
