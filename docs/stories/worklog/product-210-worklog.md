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
- [x] Reviewbevindingen en de bijbehorende bestaande tests/documentatie opnieuw inventariseren.
- [x] Semantiekvolgorde, echte Tab-bereikbaarheid en privacy van URL/semantics expliciet testen.
- [x] Het volledige render/open/sluit-requestscenario afdekken.
- [x] Verouderde product- en uitklapdocumentatie corrigeren.
- [x] Gewijzigde bestanden formatteren, gerichte tests en het volledige vangnet opnieuw uitvoeren.

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

Reviewherstel:
- De reviewerbevindingen zijn leidend genomen. De implementatie blijft ongewijzigd waar het gedrag
  al correct is; deze ronde vult het ontbrekende toetsbewijs aan en verwijdert de twee achterhaalde
  normatieve documentatiepassages.
- De volledige request-spy maakte zichtbaar dat sluiten van het cyclusdetail alsnog alle
  overzichtsbronnen herlaadde. Die expliciete reload is verwijderd; de bestaande automatische
  verversing blijft de overzichtsdata actualiseren.
- De veldtest vergelijkt nu de semantics-traversalvolgorde rechtstreeks met de zichtbare
  widgetvolgorde. Beide terminale en actieve detailactietests focussen de knop eerst via Tab en
  activeren hem daarna met Enter en Spatie.
- De overzichtstest controleert e-mail-, token- en foutsentinels in de semantics-tree en browser-URL
  en bewaakt requestmethode en -pad over renderen, openen en sluiten.
- De functionele en technische specificatie beschrijven de cycluspresentatie nu voor ieder product
  en leggen vast dat bewijsregels en voortgangskaarten niet uitklapbaar zijn.
- Herverificatie: gerichte cyclusgeschiedenissuite groen (52 tests),
  `mvn -B --no-transfer-progress clean verify` groen (6 reactoronderdelen, 164 tests),
  `flutter analyze` groen en `flutter test` groen (416 tests); overal 0 failures en 0 errors.
