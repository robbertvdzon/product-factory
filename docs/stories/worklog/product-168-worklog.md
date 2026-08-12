# Worklog product-168

## Stappenplan

- [x] Factory-instructies, taakcontext en bestaande agenttips gelezen.
- [x] Bestaande opslag, annulering, API-contracten en dashboardpresentatie inventariseren.
- [x] Optioneel beslisrecord en atomische handmatige annulering implementeren.
- [x] Expliciete en afgeleide beslisinformatie in overzicht en detail implementeren.
- [x] Backend-, contract- en Fluttertests toevoegen of bijwerken.
- [x] Gewijzigde code formatteren en gerichte tests draaien.
- [x] Volledig vangnet uit `docs/factory/development.md` met succes afronden.
- [x] Reviewbevinding herstellen: afgeleide technische uitkomst bij expliciete annulering onderdrukken.
- [x] Regressietests voor overzicht, detail en historische fallback aanscherpen.
- [x] Gerichte tests en het volledige verplichte vangnet opnieuw groen afronden.

## Notities

De implementatie bewaart maximaal één privacy-minimaal beslisrecord per iteratie en koppelt dit
atomisch aan de bestaande terminale overgang. Historische iteraties blijven zonder backfill geldig.

Het gedeelde API-viewmodel bevat een optioneel nested `decision`-record. De repository schrijft bij
handmatige annulering één tijdswaarde naar zowel `completed_at` als `decided_at`; een conflict op de
status of unieke provenance laat door de transactie geen halve overgang achter. Het dashboard kiest
expliciete provenance vóór de bestaande conservatieve afleiding en kwalificeert historische uitkomsten
zichtbaar en toegankelijk als `Afgeleid`.

Verificatie: `mvn -B --no-transfer-progress clean verify` (141 tests), `flutter analyze`
(geen issues) en `flutter test` (227 tests) zijn afgerond met exitcode 0, 0 failures en 0 errors.

Na de reviewbevinding toont een cyclus met expliciete handmatige-annuleringsprovenance geen uit
`FAILED` afgeleide badge of technische uitkomstverklaring meer, zowel in het overzicht als in het
detail. De aangescherpte widgettest bewijst per iteratie dat een historische cyclus zonder record
zijn badge en technische fallback ongewijzigd behoudt. Het volledige vangnet is daarna opnieuw
afgerond met dezelfde groene resultaten: 141 backendtests, geen Flutter-analyseproblemen en 227
Fluttertests.

## Review

- [bug] Een expliciet handmatig geannuleerde cyclus toont in het overzicht naast
  `Beslisbron: Mens` en `Reden: Handmatig geannuleerd` nog steeds de afgeleide badge
  `technische fout` en via `outcomeReason = TECHNICAL_FAILURE` de verklaring `De cyclus is door
  een technische fout gestopt` (`dashboard-frontend/lib/main.dart`, classificatie rond regel 709-731
  en de onvoorwaardelijke uitkomstreden rond regel 751). Dit spreekt de expliciete provenance tegen;
  de story verlangt dat `Technische fout` bij zo'n record niet als beslisbron of vervangende
  verklaring wordt gepresenteerd. De detailweergave houdt bovendien de afgeleide badge in stand.
  Breid de widgetregressie uit zodat de expliciete iteratieregel en het detail geen afgeleide
  technische verklaring tonen, terwijl de historische iteratie zonder record zijn bestaande
  technische fallback mét `Afgeleid` behoudt.
- [info] Gerichte reviewchecks: de twee beslisbron-widget/unitbestanden draaiden 51 tests groen;
  `ShadowIterationCancelTest` en `ShadowIterationDecisionMigrationTest` draaiden samen 6 tests groen.
  Het volledige vangnet is niet opnieuw gestart, conform de reviewer-instructie.
