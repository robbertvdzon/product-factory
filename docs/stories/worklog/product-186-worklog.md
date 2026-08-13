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

## Review (product-186)

- [bug] `iterationEvidencePresentation` formatteert `startedAt ?? createdAt` rechtstreeks. Daardoor
  levert een aanwezige maar onleesbare `startedAt` met een geldige `createdAt` nu `Onbekend` op,
  terwijl de bestaande `iterationTiming`-presentatie in dat geval juist naar de geldige
  `createdAt` terugvalt. Dit wijkt af van de geëiste bestaande datumweergave. Voeg naast de reparatie
  een regressietest toe voor zowel ontbrekende als onleesbare `startedAt` met geldige `createdAt`.
- [bug] Bij ieder gekoppeld expliciet `decision`-record dat niet exact de handmatige-annuleringscodes
  bevat, negeert `iterationEvidencePresentation` de expliciete provenance en roept het rechtstreeks
  `classifyDecisionSource` aan. Een toekomstig/onbekend expliciet record op een ACCEPTED-cyclus kan
  zo onterecht `Evaluatie-agent (Afgeleid)` tonen, terwijl de bestaande presentatielogica het record
  als expliciet herkent en conservatief `Onbekend` hoort te tonen. Leid alleen af wanneer een
  gekoppeld expliciet record werkelijk ontbreekt; claim bij een aanwezig maar niet geldig herkend
  record geen mens en geen afgeleide bron.
- [blocker] De expliciet vereiste widgettestmatrix voor ongewijzigde kaarten is niet compleet.
  `iteration_evidence_overview_test.dart` bewijst alleen een RUNNING `product-factory`-cyclus en een
  terminale cyclus van een ander product. QUEUED voor `product-factory` en een actieve cyclus van een
  ander product worden alleen indirect/pure-logisch of in oudere componentharnassen geraakt, niet in
  de overzichtsroute die deze story wijzigt. Voeg overzicht-widgetfixtures/asserties toe voor alle
  vier vereiste combinaties: QUEUED en RUNNING van `product-factory`, plus actief en terminaal van
  een ander product.
- [info] Het factorybewijs is revisiongebonden en groen voor de geraakte runnable gates:
  `testedTreeSha` is gelijk aan de huidige `HEAD^{tree}`; frontend analyze en tests zijn geslaagd.
  De Maven-gate is volgens de geconfigureerde `pathPrefixes` niet van toepassing op deze
  frontend-only diff.

## Herstelronde na review

Stappenplan:
- [x] Datumfallback herstellen en afdekken voor ontbrekende én onleesbare `startedAt`.
- [x] Onbekende expliciete provenance conservatief presenteren en regressietesten.
- [x] De overzicht-widgettestmatrix aanvullen voor QUEUED/RUNNING en andere producten.
- [x] Gewijzigde Dart-bestanden gericht formatteren en gerichte tests uitvoeren.
- [x] Het volledige vangnet uitvoeren en de zelfreview actualiseren.

Gedaan / rationale:
- De reviewbevindingen zijn bij de start van deze herstelrun overgenomen. De reparatie blijft beperkt
  tot de bestaande bewijs-presentatielogica en de ontbrekende overzichtsdekking.
- De datum kiest nu de eerste daadwerkelijk parseerbare waarde uit `startedAt` en `createdAt`. Tests
  bewijzen zowel de ontbrekende als de onleesbare `startedAt`-variant met een geldige fallback.
- Een gekoppeld maar niet als geldige handmatige annulering herkend beslisrecord blijft expliciet
  `Onbekend`; alleen wanneer het record ontbreekt wordt de bestaande conservatieve bron als
  `(Afgeleid)` getoond. Zo claimt een onbekend toekomstig record geen menselijke beslisser.
- De dashboard-overzichttest bevat nu terminal en actief voor een ander product én QUEUED en RUNNING
  voor `product-factory`; alle vier blijven een `IterationCycleCard` op het bestaande codepad.

Zelfreview herstelronde:
- Scope en regressie: uitsluitend de bewijswaarde-opbouw en testfixtures zijn gewijzigd. De selector,
  kaartweergave, detaildialoog, bronstatussen, koppeling, API en opslag zijn ongemoeid gebleven.
- Privacy en provenance: onbekende expliciete codes worden niet weergegeven en veroorzaken geen
  menselijke of afgeleide claim; reden- en foutpayloads blijven buiten de bewijsregel.
- Toegankelijkheid: de bestaande semantische groep, labels, native bewijsactie, focusbegrenzing en
  focusherstel zijn niet gewijzigd en blijven door de volledige widgettests afgedekt.

Verificatie herstelronde (definitieve eindrun, alle exitcode 0):
- `mvn -B --no-transfer-progress clean verify`: `BUILD SUCCESS`; 142 tests, 0 failures, 0 errors.
- `flutter analyze`: `No issues found!`.
- `flutter test`: 291 tests geslaagd, 0 failures, 0 errors.

## Vervolgreview

- [info] De datumbevinding is opgelost: `startedAt` en `createdAt` worden afzonderlijk defensief
  geparseerd, waardoor zowel een ontbrekende als een onleesbare starttijd correct naar een geldige
  aanmaaktijd terugvalt. Beide varianten hebben een gerichte regressietest.
- [info] De provenancebevinding is opgelost: een gekoppeld expliciet maar onbekend beslisrecord
  blijft `Onbekend`, zonder menselijke of afgeleide beslisser te claimen. Alleen bij een werkelijk
  ontbrekend gekoppeld record wordt de bestaande conservatieve afleiding als `Afgeleid` getoond.
- [info] De ontbrekende overzichtsdekking is opgelost: de widgettest verifieert nu expliciet
  `QUEUED` en `RUNNING` voor `product-factory` en actieve en terminale cycli van een ander product;
  alle vier behouden de bestaande kaartweergave.
- [info] De herstelwijzigingen introduceren geen zichtbare regressie. Gerichte reviewrun:
  `flutter test test/iteration_evidence_test.dart test/iteration_evidence_overview_test.dart` —
  34 tests geslaagd, 0 failures en 0 errors. `git diff --check` is schoon.
- [info] Het nieuwste factorybewijs is geldig en revisiongebonden: `testedTreeSha`
  `824a4d480876db26013901d56725312db6bfaa70` is exact gelijk aan de huidige `HEAD^{tree}`;
  `dashboard-flutter-analyze` en `dashboard-flutter-test` zijn groen. De Maven-gate is volgens de
  geconfigureerde padselectie niet van toepassing op deze frontend-only story-diff.
