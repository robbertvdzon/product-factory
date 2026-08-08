# SF-2036 - Worklog

Story-context bij eerste pickup:
Start-/doorlooptijd bij productcycli en 'Meer'-knop op de overzichtslijsten

Implementeer in dashboard-frontend (main.dart, eventueel een nieuw lib/formatting.dart): (1) gedeelde, defensieve helpers parseInstant/formatDateTime (lokale tijdzone, vast formaat dd-MM-yyyy HH:mm, zonder nieuwe dependency zoals intl) en formatDuration (max twee eenheden: '2u 13m', '4m 12s', '35s'); (2) per productcyclus in de lijst 'Productcycli en onderzoekssessies' en in IterationSessionDialog de starttijd tonen op basis van startedAt met createdAt als fallback, plus de doorlooptijd completedAt-startedAt, en bij een lopende cyclus de tijd sinds start met duidelijke 'loopt nog'-aanduiding die meeloopt met de bestaande 5s auto-refresh; bij lege startedAt geen doorlooptijd; (3) een herbruikbaar 5/+10-mechanisme met per sectie een eigen teller in _OverviewPageState (buiten de FutureBuilder, zodat auto-refresh de uitklapstand behoudt en nieuwe items bovenaan verschijnen), met een 'Meer'-knop die het aantal resterende items toont en verdwijnt zodra alles zichtbaar is; toepassen op Producten, Productcycli en onderzoekssessies, Software Factory-stories, Benodigde access tokens, elke subsectie van de Storywachtrij (_buildStoryQueueSections aanpassen zodat het de tellers meekrijgt) en Workspace-publicaties; (4) sortering nieuwste eerst vóór het afkappen voor iteraties (startedAt ?? createdAt), stories, deliveries en human actions, defensief bij ontbrekende velden; producten behouden hun huidige volgorde; publicaties hebben geen tijdstempel in de contracts, dus daar alleen de beperking - vermeld die afwijking in de worklog/PR-tekst; (5) metric-tegels blijven het totaal tonen; (6) tests schrijven: unittests voor formatDuration/formatDateTime en minimaal een widgettest die 5 zichtbare items, het resterende aantal op de 'Meer'-knop, +10 na een tik en het verdwijnen van de knop afdekt, zonder echte HTTP-calls; (7) docs/factory/technical-spec.md en functional-spec.md vullen met concrete repo-informatie (stack, module-indeling, verificatiecommando's mvn -B clean verify / flutter analyze / flutter test, beschrijving van de overzichtspagina en het 5/+10-gedrag). Rond af met een eigen reviewstap en zorg dat 'flutter analyze' en 'flutter test' in dashboard-frontend groen zijn.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-2037 (development)

Gedaan:
- `lib/formatting.dart` toegevoegd met `parseInstant`, `formatDateTime` (lokaal, `dd-MM-yyyy HH:mm`),
  `formatDuration` (max twee eenheden), `iterationTiming` (start + doorlooptijd van een cyclus) en
  `sortedByNewestFirst`. Geen nieuwe dependency: `intl` is niet nodig voor één vast formaat, en de
  helpers zijn defensief zodat een leeg of onleesbaar tijdstempel nooit de lijst laat crashen.
- `lib/limited_list.dart` toegevoegd met `LimitedListSection` en de constanten `kInitialVisibleItems`
  (5) en `kShowMoreStep` (10). De teller zit bewust *buiten* de widget, in `_OverviewPageState`
  (`visibleCounts`, per sectie een eigen sleutel), zodat de auto-refresh van 5 s de uitklapstand
  behoudt en nieuwe items gewoon bovenaan verschijnen.
- Overzichtspagina: 5/+10 toegepast op Producten, Productcycli, Software Factory-stories, Benodigde
  access tokens, elke subsectie van de Storywachtrij (`_buildStoryQueueSections` krijgt de tellers als
  callbacks mee) en Workspace-publicaties. De metric-tegels blijven de totalen tonen.
- Productcycli tonen nu `gestart <datum tijd>` en de doorlooptijd; een lopende cyclus toont
  `loopt nog: <tijd sinds start>`, een niet-gestarte cyclus toont de aanmaaktijd zonder doorlooptijd.
  Dezelfde regels gelden in `IterationSessionDialog`; daar zijn ook de ruwe ISO-tijdstempels van
  stappen en artifacts door `formatDateTime` gehaald, zodat het scherm consistent leest.
- Sortering nieuwste eerst vóór het afkappen: iteraties (`startedAt` → `createdAt`), storykandidaten,
  leveringen en human actions.
- Tests: `test/formatting_test.dart` (unittests voor parsen, formatteren, duur, timing en sortering) en
  `test/limited_list_test.dart` (widgettests: 5 zichtbaar, resterend aantal op de knop, +10 per tik,
  knop verdwijnt, uitklapstand overleeft een refresh, doorlooptijd in de rij). Geen HTTP in de tests.
- `docs/factory/technical-spec.md`, `functional-spec.md` en `development.md` gevuld met de echte stack,
  module-indeling, frontend-conventies, de overzichtspagina en de verificatiecommando's.

Afwijking / let op:
- `WorkspacePublicationView` heeft geen tijdstempel in de contracts. Voor Workspace-publicaties geldt
  daarom alleen de 5/+10-beperking en géén 'nieuwste eerst'-sortering; de backendvolgorde blijft staan.
- Producten behouden bewust hun bestaande volgorde (op slug), conform de aannames in de story.

Vangnet (allemaal groen):
- `flutter analyze` in `dashboard-frontend`: "No issues found!"
- `flutter test` in `dashboard-frontend`: 23 tests, alles geslaagd
- `mvn -B --no-transfer-progress clean verify` vanuit de root: BUILD SUCCESS, exitcode 0,
  0 failures en 0 errors (59 backendtests)

`dashboard-frontend/pubspec.lock` staat mee in de wijziging: `flutter pub get` heeft een paar
transitieve pakketten bijgewerkt. Bewust niet teruggedraaid, zodat een volgende `flutter test`-run in
dezelfde workspace de worktree niet alsnog wijzigt tijdens het verzamelen van bewijs.
