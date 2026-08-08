# product-factory-2 - Worklog

Story-context bij eerste pickup:
Voeg fallback-classificatie 'niet-classificeerbaar' toe aan iteratie-uitkomstmapping

In dashboard-frontend/lib/classification.dart: nieuwe constante kNietClassificeerbaar='niet-classificeerbaar' toevoegen aan kIterationClassifications; classifyIterationOutcome zo aanpassen dat QUEUED/RUNNING expliciet (via eigen case-regels) op kOnderzoekOnvoldoende blijven gemapt, en de default-tak (elke andere onbekende/ontbrekende status) naar kNietClassificeerbaar wijst in plaats van kOnderzoekOnvoldoende. Nieuw kleurenpaar toevoegen aan kClassificationColors voor kNietClassificeerbaar, visueel onderscheidend van de vier bestaande paren en met contrastratio >=4.5:1 (verifiëren met de bestaande contrastRatio-helper). Unit tests in classification_test.dart bijwerken: de bestaande test 'fallback: onbekende of ontbrekende statuswaarde' aanpassen naar een assert op kNietClassificeerbaar, en de test 'elke classificatiewaarde zit in de vaste toegestane lijst' uitbreiden. Nieuwe Flutter widget-test toevoegen die een testdataset van iteratierijen rendert (de vijf bekende statuscombinaties inclusief QUEUED/RUNNING plus minstens één onbekende statuswaarde) en assert dat elke rij precies één ClassificationBadge toont uit de vijfwaardige set, nooit een lege statuscel. Tot slot docs/factory/functional-spec.md bijwerken: opsomming van badgewaarden uitbreiden met 'niet-classificeerbaar' en de bijbehorende voorwaarde. Geen wijzigingen aan main.dart, backend of databasemodel nodig.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `dashboard-frontend/lib/classification.dart`: nieuwe constante `kNietClassificeerbaar`
  toegevoegd aan `kIterationClassifications` (nu 5 waarden). De vier bekende badge-categorieën en
  de bekende tussenstatussen QUEUED/RUNNING worden nu gevoed door een expliciete, uitbreidbare
  tabel `kBekendeStatuswaardenPerCategorie` (status → categorie); `classifyIterationOutcome` zoekt
  de status in die tabel op en valt voor elke niet-gevonden waarde (incl. `null`/leeg en elke
  toekomstige onbekende status) terug op `kNietClassificeerbaar` i.p.v. op
  `kOnderzoekOnvoldoende`. QUEUED/RUNNING blijven ongewijzigd op `kOnderzoekOnvoldoende` gemapt.
- Nieuw kleurenpaar toegevoegd aan `kClassificationColors` voor `kNietClassificeerbaar`
  (achtergrond `#CFE2FF`, voorgrond `#073880`), visueel onderscheidend (blauw) van de vier
  bestaande paren (amber/rood/groen/grijs); contrastratio geverifieerd op ~8,47:1 (ruim boven de
  vereiste 4.5:1) en gedekt door de bestaande, over `kIterationClassifications` iterende
  WCAG-test in `classification_test.dart`.
- `classification_test.dart`: bestaande fallback-test aangepast om `kNietClassificeerbaar` te
  verwachten voor een onbekende status, `null` én een lege string; de test die controleert dat elke
  classificatie in de toegestane lijst zit uitgebreid met een onbekende statuswaarde.
- `classification_badge_widget_test.dart`: testdataset uitgebreid met een onbekende toekomstige
  statuswaarde en `null`, zodat alle vijf classificaties (incl. QUEUED/RUNNING →
  onderzoek-onvoldoende) in de rendered rijenlijst voorkomen; test asserts nu op de vijfwaardige
  set en dat elke rij precies één `ClassificationBadge` toont, nooit een lege statuscel. Testvenster
  vergroot (`tester.view.physicalSize`) zodat alle 8 rijen binnen de sliver-cache-extent van de
  `ListView` vallen (zie agent-tips over sliver-cache-extent en lange lijsten in widgettests).
- `docs/factory/functional-spec.md`: opsomming van de vier badgewaarden uitgebreid met
  `niet-classificeerbaar` inclusief de bijbehorende voorwaarde (onbekende/ontbrekende status), en
  "vier kleurenparen" bijgewerkt naar "vijf kleurenparen".
- Geen wijzigingen aan `main.dart`, backend of databasemodel (buiten scope, niet nodig — main.dart
  gebruikt `classifyIterationOutcome`/`ClassificationBadge` ongewijzigd en profiteert automatisch
  van de nieuwe classificatie).
- Vangnet gedraaid: `flutter analyze` (schoon), `flutter test` (41/41 groen, incl. de aangepaste en
  nieuwe tests), `mvn -B --no-transfer-progress clean verify` (BUILD SUCCESS, backendmodules
  ongewijzigd door deze story). Geen `pubspec.lock`-wijziging.
