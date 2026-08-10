# product-factory-22 - Vervang badge-label 'guardrail-conflict' door neutraal 'technische fout' voor status FAILED

## Story

Vervang badge-label 'guardrail-conflict' door neutraal 'technische fout' voor status FAILED

<!-- refined-by-factory -->

## Scope

In `dashboard-frontend/lib/classification.dart` mapt `kBekendeStatuswaardenPerCategorie` de backend-status `FAILED` momenteel naar de classificatiecategorie `kGuardrailConflict` (badge-tekst 'guardrail-conflict', gesuggereerd als 'in strijd met de regels'). Dit is feitelijk onjuist: `FAILED` ontstaat uit een generieke catch-all bij onverwachte exceptions en uit weeskind-/timeout-opruiming (een zuiver technische oorzaak), terwijl het échte guardrail-pad in `ShadowIterationEngine.kt` altijd resulteert in status `REJECTED`, die al correct naar `kRichtingVerworpen` ('richting-verworpen') mapt.

Deze story voegt een nieuwe, neutrale classificatiecategorie toe (bijvoorbeeld `kTechnischeFout`, badge-tekst 'technische fout') met een eigen WCAG 2.1 AA-conform kleurenpaar, en past de mapping voor `FAILED` aan zodat deze naar deze nieuwe categorie wijst in plaats van naar `kGuardrailConflict`. De mapping voor `REJECTED` → `kRichtingVerworpen` blijft ongewijzigd, evenals de mappings voor `ACCEPTED` → `kRichtingGekozen` en `NEEDS_REVISION`/`QUEUED`/`RUNNING` → `kOnderzoekOnvoldoende`.

De wijziging blijft beperkt tot de status-naar-badge-mappinglaag in de frontend (`classification.dart` en de plekken die deze mapping consumeren/documenteren, zoals `main.dart` en `functional-spec.md`). Er wordt geen backend-, schema-, database- of API-wijziging aangebracht; het statusschema (FAILED/REJECTED/ACCEPTED/NEEDS_REVISION/QUEUED/RUNNING) blijft ongewijzigd.

**Bekende overige referenties naar `kGuardrailConflict`** (gevonden via grep, ter documentatie voor de implementerende agent — geen nieuwe grep-actie meer nodig, wel verifiëren of nog actueel):
- `dashboard-frontend/lib/main.dart` (~regel 1790): gebruikt `kClassificationColors[kGuardrailConflict]` voor de kleur van het "geblokkeerd"-label op storyqueue-kaarten (dependsOn-blokkade), losstaand van de iteratie-statusbadge. Deze referentie blijft ongewijzigd en de constante `kGuardrailConflict` (en `kClassificationColors[kGuardrailConflict]`) mag daarom **niet** verwijderd worden.
- Testbestanden die op dit moment FAILED-status expliciet aan `kGuardrailConflict` koppelen en die aangepast moeten worden naar `kTechnischeFout`: `dashboard-frontend/test/classification_test.dart`, `dashboard-frontend/test/iteration_readable_artifact_fields_test.dart`, `dashboard-frontend/test/iteration_session_dialog_classification_badge_test.dart`.
- Testbestanden die `kGuardrailConflict` gebruiken in de context van het blokkeerlabel (storyqueue) en dus ongewijzigd kunnen blijven, tenzij een gerichte controle anders uitwijst: `dashboard-frontend/test/story_queue_blocked_label_test.dart`, `dashboard-frontend/test/classification_badge_disclosure_test.dart` (dit laatste test de disclosure-badge generiek met een willekeurige classificatiewaarde; te beoordelen of het scenario beter bij `kTechnischeFout` past).

`docs/factory/functional-spec.md` noemt `guardrail-conflict` normatief als een van de vijf vaste badges (rond regel 21-22 en 134-135) — deze sectie moet meebewegen naar de nieuwe badge-naam 'technische fout' zodat de spec de code blijft weerspiegelen.

## Acceptance criteria

- `classification.dart` bevat een nieuwe classificatiecategorie-constante (bv. `kTechnischeFout`) met badge-tekst 'technische fout' en een WCAG 2.1 AA-conform voorgrond/achtergrond-kleurenpaar (contrastratio minimaal 4.5:1 voor normale tekst), analoog aan de bestaande categorieën, en toegevoegd aan `kIterationClassifications`.
- In `kBekendeStatuswaardenPerCategorie` wijst de sleutel `FAILED` naar de nieuwe categorie `kTechnischeFout` in plaats van naar `kGuardrailConflict`; geverifieerd met een geautomatiseerde unit-/goldentest.
- `classifyIterationOutcome()` retourneert voor elke FAILED-testfixture een badge-string die het woord 'technische fout' bevat en niet het woord 'guardrail' bevat; afgedwongen met een nieuwe of uitgebreide automatische testcase.
- De mapping voor `REJECTED` → `kRichtingVerworpen` ('richting-verworpen') blijft exact ongewijzigd, geverifieerd met een regressietest die voor REJECTED-invoer dezelfde outputstring retourneert als vóór de wijziging.
- De mappings voor `ACCEPTED` (`kRichtingGekozen`) en `NEEDS_REVISION`/`QUEUED`/`RUNNING` (`kOnderzoekOnvoldoende`) blijven ongewijzigd, geverifieerd met bestaande of uitgebreide regressietests.
- `kGuardrailConflict` en de bijbehorende entry in `kClassificationColors` blijven bestaan (niet verwijderd), omdat `main.dart` (~regel 1790) deze nog gebruikt voor het "geblokkeerd"-label op storyqueue-kaarten; dit gebruik en de bijbehorende test (`story_queue_blocked_label_test.dart`) blijven ongewijzigd werken.
- De accessible name/semantics-label van de FAILED-badge bevat 'technische fout' en niet 'guardrail' of 'regel'; gecontroleerd via de Flutter-Web a11y-inspectietechniek (flt-semantics-placeholder click + ariaSnapshot + CDP AX tree) op de gerenderde badge-node in de acceptatieomgeving.
- De bestaande disclaimer-tooltip ('Dit toont wat de uitkomst was, niet waarom') blijft ongewijzigd aanwezig en toetsenbord-/schermlezerbereikbaar (Tab/Enter) bij zowel de FAILED- als de REJECTED-badge.
- De implementerende agent controleert (grep) alle overige verwijzingen naar `kGuardrailConflict` (widgets, tests, golden-bestanden), werkt testbestanden die FAILED expliciet aan `kGuardrailConflict` koppelen bij naar `kTechnischeFout`, laat referenties die bij het storyqueue-blokkeerlabel horen ongewijzigd, en documenteert deze bevinding in de PR/commit-omschrijving.
- `docs/factory/functional-spec.md` wordt bijgewerkt zodat de vermeldingen van de vijf vaste badges (o.a. rond de sectie over de overzichtspagina) 'technische fout' in plaats van 'guardrail-conflict' noemen voor de FAILED-badge.
- Geen backend-, schema-, database- of API-wijziging; het statusschema (FAILED/REJECTED/ACCEPTED/NEEDS_REVISION/QUEUED/RUNNING) blijft ongewijzigd.

## Aannames

- De naam van de nieuwe constante (`kTechnischeFout`) en de exacte kleurwaarden zijn een implementatievrijheid van de developer, zolang de badge-tekst 'technische fout' is en het kleurenpaar AA-contrast haalt.
- `kGuardrailConflict` als constante en kleurdefinitie blijft bestaan vanwege het herbruik in de storyqueue-blokkeerweergave (main.dart ~1790); alleen de FAILED→categorie-mapping in `classifyIterationOutcome`/`kBekendeStatuswaardenPerCategorie` verandert.
- `classification_badge_disclosure_test.dart` gebruikt `kGuardrailConflict` als generiek voorbeeld voor de disclosure-badge (niet FAILED-specifiek); de developer mag zelf beoordelen of dit voorbeeld ongewijzigd blijft of naar `kTechnischeFout` verschuift, zolang de testdekking van het disclosure-gedrag niet afneemt.

## Eindsamenvatting

Ik heb voldoende context uit het worklog. Dit is een duidelijke, goed gedocumenteerde story — geen onduidelijkheden die een vraag rechtvaardigen.

## Eindsamenvatting (product-factory-22)

**Wat is gebouwd**
De badge-tekst voor de iteratiestatus `FAILED` toont niet langer 'guardrail-conflict', maar de neutrale term 'technische fout'. In `dashboard-frontend/lib/classification.dart` is een nieuwe classificatiecategorie `kTechnischeFout` toegevoegd (badge-tekst 'technische fout', kleurenpaar bg `0xFFE4E1F5` / fg `0xFF33306B`, contrastratio ≈9.23:1 — ruim boven de vereiste WCAG 4.5:1). De mapping `FAILED → kGuardrailConflict` is vervangen door `FAILED → kTechnischeFout`.

**Belangrijke keuzes**
- `kGuardrailConflict` (constante + kleurdefinitie) is bewust **niet** verwijderd, omdat `main.dart:1790` deze los gebruikt voor het "Geblokkeerd: <reden>"-label op storyqueue-kaarten (dependsOn-blokkade), los van de iteratiestatusbadge.
- Overige mappings (REJECTED → kRichtingVerworpen, ACCEPTED → kRichtingGekozen, NEEDS_REVISION/QUEUED/RUNNING → kOnderzoekOnvoldoende) zijn ongewijzigd gebleven.
- `story_queue_blocked_label_test.dart` en `classification_badge_disclosure_test.dart` zijn bewust ongewijzigd gelaten (generiek/blokkeerlabel-context, geen dekkingsverlies).
- `docs/factory/functional-spec.md` (rond regel 21-22 en 134-135) is bijgewerkt naar 'technische fout'; de storyqueue-blokkeerlabel-passage (~regel 95) blijft ongemoeid.

**Getest**
- `classification_test.dart`, `iteration_readable_artifact_fields_test.dart` en `iteration_session_dialog_classification_badge_test.dart` bijgewerkt met expliciete checks dat FAILED-badges 'technische fout' bevatten en niet 'guardrail'.
- `dart format`, `flutter analyze` (geen issues), `flutter test` (139/139 groen) en `mvn clean verify` (BUILD SUCCESS, inclusief `FunctionalSpecStatusConclusionDocTest`) zijn groen.
- Tester heeft diff-scope, contrastratio onafhankelijk herberekend, en smoketest op preview-omgeving (`/` en `/actuator/health` → HTTP 200) uitgevoerd; akkoord gegeven.

**Bewust niet gedaan**
- Geen backend-, schema-, database- of API-wijziging; het statusschema blijft ongewijzigd.
- Interactieve a11y-inspectie (flt-semantics-placeholder/CDP AX tree) op de live FAILED-badge kon niet in de agentcontainer worden uitgevoerd (geen browsertool beschikbaar); dit is afgedekt via de groene widget-/semantics-tests in plaats daarvan.

<!-- deploy-summary:start -->
De melding bij een mislukte iteratie noemt voortaan 'technische fout' in plaats van 'guardrail-conflict', zodat duidelijker is dat het om een technisch probleem gaat en niet om een inhoudelijke afwijzing. Er verandert verder niets aan hoe het systeem werkt.
<!-- deploy-summary:end -->
