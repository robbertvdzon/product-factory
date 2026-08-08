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

## Review SF-2037 (reviewer)

Beoordeeld: volledige story-diff `main...HEAD` (formatting.dart, limited_list.dart, main.dart,
pubspec.lock, beide testbestanden, docs/factory, deze worklog).

Akkoord op de functionele kern: AC1-AC11 zijn afgedekt (starttijd/doorlooptijd met fallback en
'loopt nog', vast `dd-MM-yyyy HH:mm`-formaat, 5/+10 met eigen teller per sectie buiten de
`FutureBuilder`, metric-tegels blijven totalen tonen, sortering nieuwste eerst vóór het afkappen).
AC12 (docs) en AC13 (18 unittests + 5 widgettests) ook. Helpers zijn defensief en goed getest.

Blokkerend:
- `dashboard-frontend/pubspec.lock` bumpt `sdks.dart` van `>=3.9.0` naar `>=3.10.0-0`, terwijl
  `dashboard-frontend/Dockerfile` op `ghcr.io/cirruslabs/flutter:3.35.0` (Dart 3.9.x) staat. Die
  image wordt op elke PR/main gebouwd (`.github/workflows/images.yml`) en is niet onderdeel van
  `.factory/verification.yaml`, dus het groene vangnet dekt hem niet. Oorzaak is een
  toolchain-divergentie (agentcontainer Dart 3.12 vs CI Flutter 3.44.6 vs image 3.35.0).
  Oplossing: Dockerfile-base gelijktrekken met CI (3.44.6) óf de lock terugdraaien; kies bewust en
  onderbouw het.
Kleiner (suggestie):
- `_showMore` liet de teller ongelimiteerd groeien; begrenzen op het aantal items.

## SF-2037 (development, ronde 2 - review-bevindingen verwerkt)

Stappenplan:
[x]: blocker pubspec.lock vs Dockerfile-base oplossen
[x]: suggestie `_showMore` begrenzen + test
[x]: vangnet opnieuw groen draaien

### Blocker: toolchain-divergentie (opgelost via optie a)

Gekozen: **Dockerfile-base gelijktrekken met de CI-toolchain**, niet de lock terugdraaien.

Onderbouwing - terugdraaien werkt aantoonbaar niet. Met `git checkout main -- pubspec.lock` gevolgd
door `flutter pub get` herschrijft pub de lock meteen weer naar exact dezelfde inhoud (`characters`
1.4.1, `matcher` 0.12.19, `material_color_utilities` 0.13.0, `meta` 1.18.0, `test_api` 0.7.11,
`sdks.dart >=3.10.0-0`). Dat is geen keuze van deze story: de `flutter_test` die met Flutter 3.44
meekomt pint die pakketten. Zowel de agentcontainer als `verify.yml` (`flutter-version: 3.44.6`)
draaien op 3.44, dus optie (b) zou bij de eerstvolgende `pub get` in CI of in een agentrun opnieuw
stuk gaan. De echte oorzaak is de image die nog op 3.35.0 (Dart 3.9) stond.

- `dashboard-frontend/Dockerfile`: base van `ghcr.io/cirruslabs/flutter:3.35.0` naar `:3.44.0`.
  3.44.6 bestaat niet als image-tag - de tag-lijst van `ghcr.io/cirruslabs/flutter` gaat bij de
  stabiele releases tot `3.44.0`. Zelfde Flutter-minor en zelfde Dart 3.12 als CI, dus de lock
  wordt daar nu wel gehaald.
- Geverifieerd zonder docker (niet beschikbaar in de agentcontainer) door de bouwstappen uit de
  Dockerfile lokaal op Flutter 3.44.7 te draaien: `flutter build web --release --pwa-strategy=none`
  slaagt en de cache-busting erna (`sha256sum main.dart.js`, hernoemen, `sed` in
  `flutter_bootstrap.js`/`flutter.js`) haalt ook op 3.44 zijn eigen guard
  `test -z "$(grep -rl 'main\.dart\.js' .)"`. De image-build zelf is dus niet in deze container
  bewezen; dat gebeurt in `.github/workflows/images.yml`.
- Gat in het vangnet gedicht: `.factory/verification.yaml` heeft nu
  `dashboard-frontend-image-build` (`agentRunnable: false`, zoals `agent-image-build`), en
  `docs/factory/development.md` beschrijft de drie plekken waar de Flutter-versie is gepind.

### Suggestie: teller begrenzen

- `nextVisibleCount(current, itemCount)` in `lib/limited_list.dart`: +10 per klik, maar nooit verder
  dan het aantal items. `_showMore` in `main.dart` gebruikt hem, net als de testharness.
  Zonder die grens stond een lijst die tussen twee refreshes krimpt en daarna weer groeit verder
  open dan de gebruiker had aangeklikt.
- Tests erbij: een unittest voor `nextVisibleCount` (ook de randgevallen teller-te-hoog en lege
  lijst) en een widgettest die op een lijst van 8 klikt en daarna naar 40 laat groeien; die toont
  nog steeds 8 items met `Meer (nog 32)`.

Vangnet ronde 2 (allemaal groen):
- `flutter analyze` in `dashboard-frontend`: "No issues found!"
- `flutter test` in `dashboard-frontend`: 25 tests, alles geslaagd
- `mvn -B --no-transfer-progress clean verify` vanuit de root: BUILD SUCCESS, exitcode 0

## Review ronde 2 (SF-2037) — akkoord

Volledige story-diff (`git diff main...HEAD`) beoordeeld: alle 13 acceptatiecriteria gedekt.
Gerichte hercontroles in de reviewcontainer: `flutter analyze` → No issues found, `flutter test` →
25/25 groen, worktree bleef daarna schoon (pubspec.lock werd niet herschreven). De eerder gemelde
blocker is opgelost: `ghcr.io/cirruslabs/flutter:3.44.0` bestaat aantoonbaar (manifest HTTP 200 op
ghcr.io) en is de nieuwste gepubliceerde stabiele tag, dus de gepinde base kan de lock-ondergrens
`dart >=3.10.0-0` halen. De frontend-imagebuild zelf blijft CI-dekking (`agentRunnable: false`).

Open, niet-blokkerende punten voor een volgende ronde:
- `docs/factory/technical-spec.md` noemt zijn tabel "exact de commandoset uit
  `.factory/verification.yaml`", maar mist `dashboard-frontend-image-build`;
  `development.md` noemt hem wel.
- `dashboard-frontend/pubspec.yaml` staat nog op `sdk: ^3.9.0` terwijl de lock `>=3.10.0-0` eist.
- De doc-comment van `sortedByNewestFirst` belooft dat items zonder tijdstempel onderling hun
  volgorde houden; `List.sort` in Dart is niet stabiel.

## Test (SF-2038) — akkoord

Volledig vangnet lokaal gedraaid, alles tot het einde:
- `mvn -B --no-transfer-progress clean verify` (root): BUILD SUCCESS, exitcode 0 — 33 + 17 + 7 tests,
  0 failures, 0 errors.
- `flutter analyze` (`dashboard-frontend`): No issues found!, exitcode 0.
- `flutter test` (`dashboard-frontend`): 25/25 groen, exitcode 0. Geen flakes gezien.
- Extra: `flutter build web` slaagt (exitcode 0) — de webcompile van `main.dart` met de nieuwe
  `formatting.dart`/`limited_list.dart` werkt. De Docker-imagebuilds staan als `agentRunnable: false`
  in `.factory/verification.yaml` en blijven CI-dekking; docker is in de agentcontainer niet aanwezig.

Gedragscontrole tegen de acceptatiecriteria (code + tests gelezen):
- AC1-5: `iterationTiming` levert start op `startedAt` met `createdAt`-fallback, duur
  `completedAt - startedAt`, `loopt nog: <duur>` zonder `completedAt` en géén duur als `startedAt`
  leeg is; `formatDateTime` toont `dd-MM-yyyy HH:mm` lokaal (`parseInstant` doet `toLocal()`).
  Toegepast in de lijst én in `IterationSessionDialog`; ook stap- en artifacttijden zijn niet langer
  ruwe ISO-strings. De 'loopt nog'-waarde wordt bij elke rebuild opnieuw berekend en loopt dus mee
  met de 5s-refresh.
- AC6-8: `LimitedListSection` + `nextVisibleCount` (5 initieel, +10 per klik, knop weg bij 0 verborgen,
  label 'Meer (nog N)'). Toegepast op producten, iteraties, deliveries, humanActions, alle vier de
  wachtrij-subsecties en publicaties, elk met een eigen sleutel in `visibleCounts`.
- AC9-10: de tellers staan in `_OverviewPageState` (buiten de `FutureBuilder`), dus een refresh
  behoudt de uitklapstand; lijsten met tijdstempel zijn nieuwste-eerst gesorteerd, dus nieuwe items
  komen bovenaan. Widgettest 'houdt de uitklapstand vast over een refresh heen' dekt dit.
- AC11: de metric-tegels gebruiken `products.length`/`stories.length`/etc. op de volledige lijsten.
- AC12: `technical-spec.md` en `functional-spec.md` bevatten concrete stack-, build- en
  overzichtspagina-informatie.
- AC13: `test/formatting_test.dart` en `test/limited_list_test.dart` dekken duurformattering en de
  'Meer'-knop; alles groen.
- Sorteervelden bestaan in de contracts: `ShadowIterationView.startedAt/createdAt`,
  `StoryCandidateView.createdAt`, `StoryDeliveryView.createdAt`, `HumanActionView.createdAt`.
  `WorkspacePublicationView` heeft er geen — daar geldt terecht alleen de beperking.

Niet uitgevoerd (geen mogelijkheid, niet blokkerend):
- Preview/E2E in een browser: `deployment.md` heeft een lege `preview_url_template` en de
  `SF_PREVIEW_*`-velden in `.task.md` zijn leeg; in de container is geen browser beschikbaar.
  Daarom geen screenshots in `/work/screenshots`.

Niet-blokkerende observaties (voor een volgende story):
- De widgettests draaien op een eigen harness die het dashboardgebruik nabootst, niet op
  `OverviewPage` zelf; de bedrading van de sectiesleutels in `main.dart` is alleen via lezen
  geverifieerd.
- De eerder door de reviewer gemelde open punten (pubspec `sdk: ^3.9.0` vs lock `>=3.10.0-0`,
  ontbrekende `dashboard-frontend-image-build` in de tabel van `technical-spec.md`, stabiliteitsclaim
  bij `sortedByNewestFirst`) staan nog open en zijn cosmetisch/documentair.
