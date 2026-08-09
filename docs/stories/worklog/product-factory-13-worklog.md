# product-factory-13 - Worklog

Story-context bij eerste pickup:
Vervang rauwe-status-Chip door ClassificationBadge in IterationSessionDialog

Vervang in dashboard-frontend/lib/main.dart (IterationSessionDialog, regel ~836) de Chip(label: Text(status)) door ClassificationBadge(classification: classifyIterationOutcome(status: iteration['status'], criticVerdict: iteration['criticVerdict'], errorMessage: iteration['errorMessage'])), exact zoals al gedaan op de lijstkaart (main.dart:562-593). Geen wijziging aan classification.dart of de kleurenset. De overige chips in dezelfde Wrap (delivery-mode, 'bezig: rol') blijven ongewijzigd. Schrijf in dashboard-frontend/test/ (naar het patroon van classification_badge_widget_test.dart en classification_badge_disclosure_test.dart): (1) een pariteitstest die voor de vijf classificatiewaarden badge-tekst en kClassificationColors-kleurenpaar op lijstkaart en in het dialoog vergelijkt, (2) een test die bevestigt dat er geen Chip met de rauwe statuswaarde meer in de widget-tree van IterationSessionDialog staat, (3) een toetsenbordnavigatietest (tester.sendKeyEvent Tab/Enter/Space) die aantoont dat de badge in het dialoog focusbaar is en de toelichting toggelt, (4) een Semantics-test (tester.getSemantics) die de aankondigingstekst van de badge in het dialoog vergelijkt met die op de lijstkaart, (5) een contrasttest die contrastRatio/relativeLuminance uit classification.dart hergebruikt op de kleuren van de badge in het dialoog (≥4.5:1), (6) een focusvolgorde-test die bevestigt dat de badge het eerste focusbare element in het dialoog is, vóór Voortgang/agentresultaten/workspace-publicaties. Zet waar nodig een lichte mock/fake voor DashboardApi.shadowIterationSession op om IterationSessionDialog geïsoleerd te kunnen pumpen.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `dashboard-frontend/lib/main.dart`: in `IterationSessionDialog` (regel ~836) `Chip(label: Text(status))`
  vervangen door `ClassificationBadge(classification: classifyIterationOutcome(status:
  iteration['status'], criticVerdict: iteration['criticVerdict'], errorMessage:
  iteration['errorMessage']))`, exact zoals al gedaan op de lijstkaart (regel ~570-591). Geen
  wijziging aan `classification.dart`/kleuren. De overige chips in dezelfde `Wrap` (delivery-mode,
  'bezig: rol') zijn ongewijzigd gebleven.
- Nieuw testbestand `dashboard-frontend/test/iteration_session_dialog_classification_badge_test.dart`
  (14 testcases, naar het patroon van `classification_badge_widget_test.dart`,
  `classification_badge_disclosure_test.dart` en `iteration_session_error_message_test.dart`):
  1. pariteit lijstkaart vs. dialoog voor alle vijf classificatiewaarden (badge-tekst +
     `kClassificationColors`-paar);
  2. geen `Chip` met de rauwe statuswaarde meer in de widget-tree van `IterationSessionDialog`;
  3. toetsenbordnavigatie (Tab/Enter/Space) naar en activatie van de badge in het dialoog;
  4. Semantics-vergelijking (label bevat classificatienaam, knop-rol, hint, expanded-status) tussen
     dialoog- en lijstkaartbadge — merkte op dat `getSemanticsData().label` de gemergde tekst van de
     hele rij teruggeeft (titel/subtitle erbij), niet alleen het eigen badge-label, dus vergeleken op
     classificatienaam + knop-/hint-gelijkheid i.p.v. exacte stringgelijkheid (zie ook agent-tip
     'mergesemantics-getsemanticsdata-voor-gemergd-label');
  5. WCAG-contrast (`contrastRatio`/`relativeLuminance`) van de daadwerkelijk in het dialoog
     gerenderde badgekleuren, alle vijf ≥4.5:1;
  6. focusvolgorde: één Tab vanaf het geopende dialoog geeft de badge focus, vóór het
     agentresultaat-`ExpansionTile` (Voortgang/agentresultaten/workspace-publicaties).
- Vangnet gedraaid en groen: `flutter analyze` (geen issues), `flutter test` (93/93 geslaagd,
  inclusief de 14 nieuwe tests), `mvn -B --no-transfer-progress clean verify` (BUILD SUCCESS, 0
  failures/errors over alle modules).
- `.factory/verification.yaml` ongewijzigd gelaten: de story raakt uitsluitend bestanden die al onder
  bestaande `pathPrefixes` (`dashboard-frontend/`) vallen.
- `dart format` alleen op de aangepaste bestanden gedraaid; één ongerelateerde cosmetische
  regelherformattering in `main.dart` (buiten de gewijzigde sectie) handmatig teruggedraaid om de
  diff beperkt te houden (zie agent-tip 'main-dart-niet-dart-formatted').

## Review (product-73)

- Diff tegen `main` beperkt tot `dashboard-frontend/lib/main.dart` (regel ~836), het nieuwe
  testbestand en deze worklog. Geen ongerelateerde bestanden geraakt; overige chips (delivery-mode,
  'bezig: rol') ongewijzigd, `classification.dart` niet aangeraakt. `pubspec.lock` ongewijzigd (geen
  toolchain-divergentierisico).
- Zelf gericht geverifieerd i.p.v. de developer-claim alleen op prijs te nemen:
  `flutter test test/iteration_session_dialog_classification_badge_test.dart` → 14/14 groen;
  volledige `flutter test` → 93/93 groen; `flutter analyze lib/main.dart
  test/iteration_session_dialog_classification_badge_test.dart` → geen issues. Dit dekt alle vijf
  acceptatiecriteria (pariteit, geen rauwe Chip meer, toetsenbord, Semantics, contrast, focusvolgorde).
- `dart format --set-exit-if-changed lib/main.dart` geeft wel een diff, maar die zit uitsluitend op
  regel ~1150 (`isBlocked`), ver buiten de gewijzigde `IterationSessionDialog`-sectie — bekend
  pre-existing patroon (agent-tip 'flutter-dart-format-main-dart-diff-triage'), geen regressie van
  deze subtaak.
- [suggestie] De nieuwe `ClassificationBadge` in het dialoog wordt onvoorwaardelijk getoond, ook
  tijdens QUEUED/RUNNING; de lijstkaart toont dan i.p.v. de badge een `IterationProgressIndicator`
  (main.dart:589-591). Omdat `classifyIterationOutcome` QUEUED/RUNNING bewust op
  `onderzoek-onvoldoende` mapt (pre-existing gedrag in `classification.dart`, niet gewijzigd door
  deze story), toont het dialoog nu tijdens een lopende/wachtende cyclus een badge die suggereert dat
  het onderzoek al ontoereikend was, terwijl de cyclus nog moet beginnen of loopt (er staat wel al een
  aparte `LinearProgressIndicator` + toelichtingstekst onder de Wrap). De storyscope vroeg letterlijk
  om vervanging van de Chip door de badge zonder een running-uitzondering te noemen, en de nieuwe
  tests sluiten QUEUED/RUNNING expliciet en beargumenteerd uit als geldige pariteitscase — dus geen
  blocker voor deze subtaak, maar het is de moeite waard om in een vervolgstory te overwegen of het
  dialoog dezelfde running-conditional als de lijstkaart moet krijgen.
