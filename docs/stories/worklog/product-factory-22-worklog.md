# product-factory-22 - Worklog

Story-context bij eerste pickup:
Vervang FAILED-badge 'guardrail-conflict' door neutrale 'technische fout'-categorie

In dashboard-frontend/lib/classification.dart een nieuwe classificatiecategorie kTechnischeFout toevoegen (badge-tekst 'technische fout', eigen WCAG 2.1 AA-conform kleurenpaar ≥4.5:1, opgenomen in kIterationClassifications en kClassificationColors). De sleutel FAILED in kBekendeStatuswaardenPerCategorie laten wijzen naar kTechnischeFout in plaats van kGuardrailConflict. kGuardrailConflict (constante + kleurdefinitie) laten bestaan omdat main.dart (~regel 1790) deze onafhankelijk gebruikt voor de kleur van het 'Geblokkeerd: <reden>'-label op storyqueue-kaarten. Grep herhalen naar alle overige referenties naar kGuardrailConflict en het resultaat documenteren in de commit-/PR-omschrijving. Testbestanden classification_test.dart, iteration_readable_artifact_fields_test.dart en iteration_session_dialog_classification_badge_test.dart bijwerken zodat FAILED-fixtures kTechnischeFout/'technische fout' verwachten en niet 'guardrail' bevatten; regressietests behouden/uitbreiden voor REJECTED (kRichtingVerworpen, ongewijzigd) en ACCEPTED/NEEDS_REVISION/QUEUED/RUNNING (ongewijzigd). story_queue_blocked_label_test.dart ongewijzigd laten werken; classification_badge_disclosure_test.dart beoordelen en zo nodig aanpassen zonder dekkingsverlies. De accessible name/semantics-label van de FAILED-badge controleren op 'technische fout' (niet 'guardrail'/'regel') en de disclaimer-tooltip-toetsenbordbereikbaarheid voor FAILED- en REJECTED-badge behouden. docs/factory/functional-spec.md bijwerken (rond regel 21-22, 134-135) zodat de vaste-badge-opsomming 'technische fout' i.p.v. 'guardrail-conflict' noemt voor FAILED, zonder de storyqueue-blokkeerlabel-passage (regel ~95, die het losstaande kGuardrailConflict-kleurgebruik beschrijft) te wijzigen. Geen backend-, schema-, database- of API-wijziging.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `classification.dart`: nieuwe constante `kTechnischeFout = 'technische fout'` toegevoegd aan
  `kIterationClassifications` en `kClassificationColors` (bg `0xFFE4E1F5` / fg `0xFF33306B`,
  contrastratio ≈ 9.23:1, ruim boven de vereiste 4.5:1). `kBekendeStatuswaardenPerCategorie['FAILED']`
  gewijzigd van `kGuardrailConflict` naar `kTechnischeFout`. `kGuardrailConflict` (constante +
  kleurdefinitie + entry in `kIterationClassifications`) blijft ongewijzigd bestaan, want
  `main.dart:1790` gebruikt `kClassificationColors[kGuardrailConflict]` nog steeds los voor het
  'Geblokkeerd: <reden>'-label op storyqueue-kaarten.
- Grep-resultaat op alle resterende `kGuardrailConflict`/`guardrail-conflict`-referenties na de
  wijziging (herhaald, zoals gevraagd in de story):
  - `dashboard-frontend/lib/main.dart:1790` — storyqueue-blokkeerlabel, losstaand van de
    FAILED-badge-mapping, bewust ongewijzigd.
  - `dashboard-frontend/test/story_queue_blocked_label_test.dart:199-200` — test voor datzelfde
    blokkeerlabel-kleurenpaar, ongewijzigd.
  - `dashboard-frontend/test/classification_badge_disclosure_test.dart:137` — gebruikt
    `kGuardrailConflict` als generiek (niet FAILED-specifiek) voorbeeld voor het disclosure-paneel
    (Escape-gedrag); dekking van alle classificatiewaarden (incl. de nieuwe `kTechnischeFout`) loopt
    al via de bestaande `for (final classification in kIterationClassifications)`-lussen in
    hetzelfde bestand, dus geen dekkingsverlies — ongewijzigd gelaten conform de story-aanname.
  - Overige `lib/`-bestanden en `docs/factory/functional-spec.md` bevatten geen resterende
    `kGuardrailConflict`/`guardrail-conflict`-referenties buiten de hierboven genoemde,
    bewust-ongewijzigde storyqueue-context.
- Testbestanden bijgewerkt naar `kTechnischeFout`/'technische fout' voor FAILED-fixtures:
  `classification_test.dart` (FAILED-test + expliciete `isNot(kGuardrailConflict)` en
  `isNot(contains('guardrail'))`-checks), `iteration_readable_artifact_fields_test.dart`,
  `iteration_session_dialog_classification_badge_test.dart`. REJECTED/ACCEPTED/NEEDS_REVISION/
  QUEUED/RUNNING-mappings ongewijzigd gelaten (geen edits in die testcases).
- `docs/factory/functional-spec.md` regels ~21-22 en ~134-135 (de vaste-vijf-badges-opsomming)
  bijgewerkt van `guardrail-conflict` naar `technische fout`; de storyqueue-blokkeerlabel-passage
  (regel ~95, `kGuardrailConflict`-kleurgebruik) bewust ongewijzigd gelaten.
- Geen backend-, schema-, database- of API-wijziging aangebracht.
- Vangnet: `dart format` (0 gewijzigd op de aangepaste bestanden), `flutter analyze` (geen issues),
  `flutter test` (139/139 groen, inclusief `FunctionalSpecStatusConclusionDocTest`-equivalent aan
  de frontendkant en de bestaande `mvn`-doc-test), en `mvn -B --no-transfer-progress clean verify`
  vanuit de repo-root (BUILD SUCCESS, alle modules groen — inclusief
  `nl.vdzon.productfactory.FunctionalSpecStatusConclusionDocTest`, die de bijgewerkte
  `functional-spec.md` meeleest en groen bleef). Geen bestaande rode tests aangetroffen.
