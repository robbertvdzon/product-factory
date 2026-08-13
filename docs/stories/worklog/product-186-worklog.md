# product-186 - Developerworklog

Story-context:
Compacte bewijsregels voor uitsluitend terminale `product-factory`-cycli op het hoofdscherm.

Stappenplan:
- [x] Story, factory-documentatie, agent-tips en verificatieconfig lezen.
- [x] Bestaande cycluskaart, classificatie, koppeling, detailactie en tests inventariseren.
- [x] Compacte bewijsregel met hergebruik van bestaande presentatielogica implementeren.
- [x] Unit- en widgettests voor scope, gegevens, bronstatussen, toetsenbord en responsive gedrag schrijven.
- [x] Gewijzigde code gericht formatteren en gerichte tests uitvoeren.
- [x] Volledig vangnet uit `docs/factory/development.md` tot het einde uitvoeren.
- [x] Zelfreview op scope, regressies, privacy en toegankelijkheid uitvoeren en vastleggen.

Gedaan / rationale:
- Dit subtaakworklog is bij de start aangemaakt zodat voortgang, ontwerpkeuzes en verificatie als
  onderdeel van de factory-handover controleerbaar blijven.
- `iteration_evidence.dart` bevat de pure, exact afgebakende selector en bouwt veilige bewijswaarden
  met de bestaande datum-, uitkomst-, reden- en provenanceclassificatie. Alleen exact
  `product-factory` met `ACCEPTED`, `NEEDS_REVISION`, `REJECTED`, `NO_CHANGE` of `FAILED` gebruikt
  de nieuwe presentatie.
- `IterationEvidenceRow` toont Datum, Cyclusuitkomst, Reden, Beslisbron en Gekoppelde opbrengst in
  één semantische container. De regel is niet uitklapbaar en telt uitsluitend de al exact gekoppelde
  Software Factory-leveringen; kandidaatrecords worden niet in dit getal opgenomen.
- De native actie `Bekijk bewijs` gebruikt de bestaande detailcallback en een eigen focusnode. Het
  bestaande detail blijft een gesloten tab-focusroute en sluiten via de zichtbare actie of Escape
  herstelt de focus naar precies dezelfde knop.
- Onbekende operationele codes, onbruikbare tijden en ongeldige provenance worden als `Onbekend`
  getoond. `Mens` wordt uitsluitend geclaimd voor een volledig geldig, aan dezelfde FAILED-cyclus
  gekoppeld record met de drie handmatige-annuleringscodes.
- Nieuwe unit- en widgettests dekken alle zes representatieve uitkomsten, actieve en andere
  producten, veilige fallbacks, semantiek, muis/Enter/Spatie/Escape, focusbegrenzing en -herstel,
  contrast en 320/1200 pixels bij 200% tekst. De overzichttest bewijst exacte leveringskoppeling,
  uitsluiting van kandidaten en ontbrekende, verkeerd getypeerde, kruisproduct- en ambigue relaties.

Zelfreview:
- Scope: alleen de dashboardpresentatie van de bedoelde terminale `product-factory`-cycli wijzigt.
  Actieve cycli, andere producten, detailinhoud, lijstbeperking, sortering, refresh, API's, contracten,
  opslag en de globale Software Factory-weergave blijven op hun bestaande codepad.
- Privacy: de bewijsregel rendert geen ruwe status, foutmelding, prompt, logs, payload, artefactinhoud,
  persoonsgegevens of andere-productgegevens. Een onbekende reden wordt niet als backendcode
  doorgegeven en een onvolledig beslisrecord claimt nooit een mens.
- Toegankelijkheid: alle waarden zijn zichtbaar gelabeld en afzonderlijk semantisch benoemd binnen
  één groep; de actie heeft product- en cycluscontext, native keyboardbediening, een 3px-focusrand en
  voldoende tekst-, bedienings- en focuscontrast.
- Regressie: de bestaande kaart en detailactie blijven ongewijzigd voor alle niet-geselecteerde
  cycli. De bestaande redenmapping is alleen naar een losse library verplaatst; haar standaard
  verliesvrije fallback voor kaart en detail is behouden.
- `.factory/verification.yaml` is geldig en ongewijzigd; alle geraakte frontendpaden vallen onder
  analyze, test en de niet-agent-runnable frontend-imagebuild.

Verificatie (definitieve eindrun, alle exitcode 0):
- `mvn -B --no-transfer-progress clean verify`: `BUILD SUCCESS`; 142 tests, 0 failures, 0 errors.
- `flutter analyze`: `No issues found!`.
- `flutter test`: 289 tests geslaagd, 0 failures, 0 errors.
