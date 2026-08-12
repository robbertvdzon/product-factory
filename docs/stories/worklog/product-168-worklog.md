# Worklog product-168

## Stappenplan

- [x] Factory-instructies, taakcontext en bestaande agenttips gelezen.
- [x] Bestaande opslag, annulering, API-contracten en dashboardpresentatie inventariseren.
- [x] Optioneel beslisrecord en atomische handmatige annulering implementeren.
- [x] Expliciete en afgeleide beslisinformatie in overzicht en detail implementeren.
- [x] Backend-, contract- en Fluttertests toevoegen of bijwerken.
- [x] Gewijzigde code formatteren en gerichte tests draaien.
- [x] Volledig vangnet uit `docs/factory/development.md` met succes afronden.

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
