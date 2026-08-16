# product-223 - Tester-worklog

Testdatum: 2026-08-16

Resultaat: test afgekeurd.

- Preview en API-healthcheck reageerden met HTTP 200.
- De preview toont revisie `4542b93947d9`; dit correspondeert met de huidige branch-HEAD
  `4542b93`.
- Reproductie: activeer in de Flutter-Webpreview de toegankelijkheidssemantiek, geef
  `Start productcyclus nu` focus en druk Enter.
- Werkelijk: het zichtbare dialoog opent, maar het element met `role="alertdialog"` heeft geen
  `aria-label` en geen `aria-labelledby`. De tekst `Productcyclus starten` staat alleen als
  `aria-label` op een kindnode. Daardoor levert een rolquery naar de alertdialog met de naam
  `Productcyclus starten` nul resultaten op.
- Verwacht: de modale alertdialog zelf heeft de toegankelijke naam `Productcyclus starten`.
- Bronlocatie: `dashboard-frontend/lib/main.dart`, `ManualCycleStartDialog`.
- Gerichte widgettest `flutter test test/manual_cycle_start_test.dart --reporter expanded`:
  7 tests groen. Deze test dekt de fout in de gebouwde Flutter-Websemantiek dus niet af.
- Er is geen startverzoek verstuurd en de previewdatabase is niet gewijzigd.
- Screenshots: `/work/screenshots/product-223-preview-failure.png` en
  `/work/screenshots/product-223-preview-overview.png`.
- Het volledige vangnet is niet opnieuw gedraaid nadat deze blokkerende gedragsfout was
  vastgesteld; de revisiongebonden harness draait na de tester-run onafhankelijk.

## Hertest na developer/reviewer-ronde

Testdatum: 2026-08-16

Resultaat: opnieuw test afgekeurd.

- Frontend en API-healthcheck van PR-preview 74 reageerden met HTTP 200.
- De preview rapporteerde revisie/build-ID `5264d06aafdc`, gelijk aan de actuele branch-HEAD
  `5264d06aafdc39c083117d4f3ee73bc1f01c197c`.
- Reproductie in headless Chromium: activeer Flutter-Websemantiek, focus
  `Start productcyclus nu`, druk Enter en inspecteer de toegankelijke dialoogboom.
- Werkelijk: er is exact één element met `role="alertdialog"`, maar dit element heeft zowel
  `aria-label=null` als `aria-labelledby=null`. De tekst `Productcyclus starten` staat als
  `aria-label` op een onderliggende `flt-semantics`-node. Daardoor vindt
  `getByRole('alertdialog', {name: 'Productcyclus starten', exact: true})` nul elementen.
- Verwacht: het element met `role="alertdialog"` is zelf programmatisch benoemd als
  `Productcyclus starten`.
- Escape sloot de dialoog. Er is niet op `Cyclus starten` geklikt; er is geen startverzoek
  verstuurd en geen testdata aangemaakt.
- Screenshot: `/work/screenshots/product-223-preview-failure-head-5264d06.png`.
- Het volledige vangnet is vanwege deze reeds blokkerende browserregressie niet handmatig
  gedupliceerd; de revisiongebonden harness draait na deze tester-run onafhankelijk.

## Hertest op actuele HEAD na integratie met main

Testdatum: 2026-08-16

Resultaat: opnieuw test afgekeurd.

- Actuele branch-HEAD is `eb8dc260945c8d090877f6926159b0925ec727e1`. De geserveerde
  frontendbundle bevat exact deze volledige revisie; de bevinding komt dus niet van een verouderde
  frontendpreview.
- De onbehandelde PR-preview laadt niet door: `GET /api/bugs` en `GET /api/test-sessions` geven
  herhaaldelijk HTTP 404, terwijl frontend, overige dashboardrequests en API-health HTTP 200 geven.
  Zichtbaar resultaat: `Dashboard kon niet laden: Dashboard API gaf 404.`
- Om de storydialoog zonder persistente testdata alsnog te controleren zijn uitsluitend deze twee
  GET-responses in Playwright in-memory als lege lijsten onderschept.
- Reproductie: activeer Flutter-Websemantiek, focus `Start productcyclus nu`, druk Enter en
  inspecteer het element met `role="alertdialog"`.
- Werkelijk op HEAD: er is exact één `alertdialog`, maar die heeft `aria-label=null` en
  `aria-labelledby=null`. `Productcyclus starten` staat als `aria-label` op een onderliggende
  `flt-semantics`-node. Een rolquery met naam vindt daardoor geen dialoog.
- Verwacht: de `alertdialog` zelf is programmatisch benoemd als `Productcyclus starten`.
- De zichtbare dialoog opent met de autonome keuze en juiste samenvatting; er is niet bevestigd,
  dus er is geen startverzoek verstuurd en geen testdata aangemaakt.
- Bewijs: `/work/screenshots/product-223-preview-dialog-diagnostic-head-eb8dc26.png` en
  `/work/screenshots/product-223-preview-diagnostic-head-eb8dc26.png`.
- Het volledige vangnet is na deze blokkerende browserbevinding niet handmatig gedupliceerd; de
  revisiongebonden harness draait na deze tester-run onafhankelijk.
