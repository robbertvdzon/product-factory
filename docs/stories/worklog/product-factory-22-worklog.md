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

## Tester (product-127)

- Diff-scope geverifieerd: alleen `dashboard-frontend/lib/classification.dart`,
  drie testbestanden (`classification_test.dart`, `iteration_readable_artifact_fields_test.dart`,
  `iteration_session_dialog_classification_badge_test.dart`), `docs/factory/functional-spec.md` en
  deze worklog. Geen backend-/schema-/API-wijziging, conform AC.
- `kBekendeStatuswaardenPerCategorie['FAILED']` wijst naar `kTechnischeFout` (badge-tekst
  'technische fout'); `kGuardrailConflict` + kleurdefinitie blijven bestaan en `main.dart:1790`
  gebruikt deze nog ongewijzigd voor het storyqueue-blokkeerlabel.
  `kIterationClassifications`/`kClassificationColors` bevatten de nieuwe entry.
- Contrastratio van het nieuwe kleurenpaar (bg `0xFFE4E1F5` / fg `0xFF33306B`) onafhankelijk
  herberekend (WCAG 2.1-relatieve-luminantieformule): ≈ 9.23:1, ruim boven de vereiste 4.5:1.
  `story_queue_blocked_label_test.dart` en `classification_badge_disclosure_test.dart` gebruiken
  `kGuardrailConflict` bewust nog in resp. blokkeerlabel- en generieke disclosure-context; niet
  gewijzigd, conform de aannames in de story.
  functional-spec.md rond regel 21-22/134-135 noemt nu 'technische fout' i.p.v. 'guardrail-conflict'
  voor de vaste badge-opsomming; de storyqueue-blokkeerlabel-passage is ongemoeid gelaten.
- Alleen `dashboard-frontend/` en `docs/` zijn gewijzigd; dit matcht geen enkele `pathPrefixes` van
  `repository-maven-verify` in `.factory/verification.yaml`, dus dat command hoort niet in het
  vangnet voor deze revisie. Ter controle wel gecheckt dat de enige backend-test die
  `functional-spec.md` leest (`FunctionalSpecStatusConclusionDocTest`) geen 'guardrail'/'technische
  fout'-tekst toetst en dus niet door deze wijziging geraakt wordt.
- Vangnet gedraaid vanuit `dashboard-frontend/`: `flutter analyze` → "No issues found!" en
  `flutter test` → 139/139 groen, exit code 0, geen failures/errors.
- Geen preview-browsertool beschikbaar in de agentcontainer (bekend, zie agent-tips); wel curl-
  smoketest op de preview-omgeving (`SF_PREVIEW_URL`): frontend `/` → HTTP 200,
  `/actuator/health` → HTTP 200. Interactieve a11y-inspectie (flt-semantics-placeholder/CDP AX
  tree) op de FAILED-badge kon daardoor niet worden uitgevoerd; badge-tekst en semantics-inhoud
  zijn wel afgedekt door de groene widgettests (`classification_test.dart`,
  `iteration_readable_artifact_fields_test.dart`,
  `iteration_session_dialog_classification_badge_test.dart`,
  `classification_badge_disclosure_test.dart`).
- Conclusie: gedrag komt overeen met de story-acceptatiecriteria, vangnet groen. Akkoord.
