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
