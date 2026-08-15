# product-210 - Worklog

Story-context bij eerste pickup:
Maak cyclusgeschiedenis productonafhankelijk en toestandsbewust.

Stappenplan:
- [x] Factory-instructies, taakcontext en bestaande implementatiecontext lezen.
- [x] Huidige cyclusgeschiedenis, classificatie, groepering en tests inventariseren.
- [x] Productonafhankelijk toestandsmodel en toegankelijke presentatiewidgets implementeren.
- [x] Unit- en widgettests voor terminale, actieve, onbekende, privacy- en requestscenario's toevoegen.
- [x] Functionele en technische factorydocumentatie actualiseren.
- [x] Gewijzigde frontendbestanden formatteren en gerichte tests uitvoeren.
- [x] Volledig verplicht vangnet uitvoeren en eigen review afronden.

Gedaan / rationale:
- De factory- en developerinstructies zijn gelezen. De wijziging blijft frontend-only en hergebruikt
  de bestaande geladen bronnen en detailroute, conform de storyscope.
- `iterationHistoryKind` selecteert nu voor elk product terminale bewijsregels, actieve
  voortgangskaarten of een veilige onbekende-statuskaart. De productslug bepaalt alleen scope en
  identificatie.
- Terminale kaarten behouden de bestaande formattering, classificatie en exacte leveringsgroepering.
  Alleen aantoonbare provenance krijgt `(Afgeleid)`; onbekende provenance blijft `Onbekend`.
- Actieve en onbekende kaarten renderen geen terminale metadata, vrije fouttekst of muterende
  bediening. Bekende `currentRole`-waarden worden via een gesloten mapping getoond.
- De terminale en actieve detailacties zijn read-only, toetsenbordbedienbaar, hebben een zichtbare
  focusrand en herstellen focus na sluiten van het bestaande detail.
- De functionele en technische specificatie beschrijven niet langer een productslugvoorwaarde of
  `Onbekend (Afgeleid)` als normatief overzichtsgedrag.
- Verificatie: `mvn -B --no-transfer-progress clean verify` groen (6 reactoronderdelen, 164 tests),
  `flutter analyze` groen en `flutter test` groen (416 tests); overal 0 failures en 0 errors.
