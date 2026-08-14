# Worklog product-198

## Stappenplan

- [x] Bestaande dashboardlogica, contracten en tests inventariseren.
- [x] Canonieke productscope, selectie en lokale voorkeur centraliseren en unit-testen.
- [x] Hoofdscherm en Beheer op de actieve productscope laten werken.
- [x] Widget-, netwerk-, toestand-, viewport- en toegankelijkheidstests toevoegen.
- [x] Gewijzigde Dart-code formatteren en gerichte controles draaien.
- [x] Het volledige factory-vangnet succesvol afronden.
- [x] De uiteindelijke diff zelf reviewen.

## Uitvoering

De implementatie houdt de API-contracten en bronrecords ongewijzigd. Productrelaties worden in
gedeelde frontendlogica uitsluitend via niet-lege, exact overeenkomende canonieke slugs bepaald.

De productkeuze herstelt alleen een exact unieke opgeslagen slug, valt anders terug op het eerste
geldige API-product en verwijdert een ongeldige voorkeur. Het hoofdscherm toont één actieve scope
met de vaste sectievolgorde; Beheer leidt leveringsscope uitsluitend via een eenduidige kandidaat af
en houdt `Alle producten` tijdelijk. Bestaande start-, detail-, bewijs- en productbeheeracties zijn
behouden. Pure contracttests en widgettests dekken filtering, opslag, netwerkstilte, bronimmutabiliteit,
toetsenbord/focus, live-status, viewports en Beheer.

## Verificatie

- `mvn -B --no-transfer-progress clean verify`: `BUILD SUCCESS`, 0 failures, 0 errors.
- `flutter analyze`: geen issues.
- `flutter test`: 310 tests, alle tests geslaagd.
- `git diff --check`: geen whitespacefouten.
- Geen conflictmarkeringen aangetroffen; `.factory/verification.yaml` en
  `dashboard-frontend/pubspec.lock` zijn ongewijzigd.
