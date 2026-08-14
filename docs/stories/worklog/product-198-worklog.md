# Worklog product-198

## Stappenplan

- [x] Bestaande dashboardlogica, contracten en tests inventariseren.
- [x] Canonieke productscope, selectie en lokale voorkeur centraliseren en unit-testen.
- [x] Hoofdscherm en Beheer op de actieve productscope laten werken.
- [x] Widget-, netwerk-, toestand-, viewport- en toegankelijkheidstests toevoegen.
- [x] Gewijzigde Dart-code formatteren en gerichte controles draaien.
- [x] Het volledige factory-vangnet succesvol afronden.
- [x] De uiteindelijke diff zelf reviewen.
- [x] Afgeleide laad- en foutstatussen voor hoofdscherm en Beheer herstellen.
- [x] Live-statusmeldingen per weergave isoleren.
- [x] Regressietests voor beide reviewbevindingen toevoegen en gericht draaien.
- [x] Het volledige factory-vangnet na de reviewfixes opnieuw succesvol afronden.
- [x] De reviewfix-diff zelf controleren.

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

## Review

- [bug] Productafhankelijke afleidingen verliezen de laad-/foutstatus van hun tweede bron. De
  hoofdschermmetrics tonen daardoor `0` zodra respectievelijk kandidaten of leveringen al geladen
  zijn terwijl cycli of kandidaten nog laden/mislukt zijn. In een afzonderlijke Beheer-scope wordt
  een geladen leveringsbron bij een ladende/mislukte kandidaatbron bovendien als een lege, complete
  leveringslijst weergegeven. Toon voor deze afgeleide tellingen en lijsten pas een compleet
  resultaat wanneer alle noodzakelijke bronnen geladen zijn en voeg regressietests voor beide
  bronvolgordes en fouten toe (`dashboard-frontend/lib/main.dart:578-590, 666-685, 884-907`).
- [bug] `productScopeAnnouncement` wordt voor hoofdscherm en Beheer gedeeld en bij navigatie niet
  gewist of per view bijgehouden. Reproductie: open Beheer, kies `Alle producten` en ga terug naar
  het overzicht. Onder de naam van het actieve product blijft dan de zichtbare/live tekst
  `Beheerscope Alle producten ...` staan, hoewel die keuze volgens de story niet op het hoofdscherm
  bestaat. Houd de meldingen per view gescheiden of reset ze bij navigatie en dek de heen-en-terugflow
  af (`dashboard-frontend/lib/main.dart:612, 627-629, 656-660, 854-857, 954-958`).

Revisiongebonden factorybewijs gecontroleerd: de gemeten tree
`722e27da265ac9aa8512d7e1c3d7bf239b6fc3bd` is gelijk aan `HEAD^{tree}`. De twee toepasselijke
Flutter-commando's zijn groen; Maven is volgens de path-prefixconfig terecht overgeslagen.

## Reviewfix-run

De twee reviewbevindingen worden gereproduceerd en met gerichte regressietests hersteld. De
bronstatus blijft daarbij expliciet totdat alle bronnen voor een afleiding geladen zijn, en
hoofdscherm- en Beheer-meldingen krijgen ieder hun eigen presentatiestatus.

De hoofdschermmetrics combineren nu de status van alle bronnen die voor hun canonieke afleiding
nodig zijn. Een productspecifieke Beheer-leveringslijst verschijnt pas nadat zowel leveringen als
kandidaten geladen zijn; laden en fouten krijgen een expliciete melding en worden niet meer als een
lege lijst gepresenteerd. `Alle producten` behoudt de onafhankelijke globale leveringslijst.

De live-status voor het hoofdscherm en die voor Beheer zijn gescheiden. Bij een productwijziging in
Beheer wordt een eventueel verouderde hoofdschermmelding gewist; `Alle producten` kan daardoor niet
meer naar het hoofdscherm lekken.

Gerichte verificatie: de drie betrokken testbestanden zijn samen groen met 29 tests. Volledig
vangnet na de fixes:

- `mvn -B --no-transfer-progress clean verify`: `BUILD SUCCESS`, 0 failures, 0 errors.
- `flutter analyze`: geen issues.
- `flutter test`: 314 tests, alle tests geslaagd.
- `git diff --check`: geen whitespacefouten.
- Geen conflictmarkeringen aangetroffen; `.factory/verification.yaml` en
  `dashboard-frontend/pubspec.lock` zijn ongewijzigd.
