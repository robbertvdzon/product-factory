# product-factory-16 - Voeg pure functie toe die JSON-veldnamen van rolresultaten omzet naar leesbare labels

## Story

Voeg pure functie toe die JSON-veldnamen van rolresultaten omzet naar leesbare labels

<!-- refined-by-factory -->

## Samenvatting
De rolresultaten van HKH Autopilot tonen technische JSON-veldnamen soms rauw (bijv. `source_urls`, `unexpectedFieldName`) in plaats van leesbare labels. Deze story voegt een kleine, losstaande functie toe die zo'n veldnaam omzet naar een leesbaar label: voor een aantal bekende, veelvoorkomende velden een vast label, en voor alle andere velden een automatische omzetting naar nette, met hoofdletters beginnende woorden. Er verandert verder niets aan hoe gegevens worden opgehaald of getoond — dit is puur een bouwsteen voor een latere story.

## Scope
- Voeg in `dashboard-frontend/lib/main.dart` een pure, losstaande functie toe (bijv. `_humanizeFieldKey(String key) -> String`) die een JSON-sleutelnaam naar een leesbaar label omzet.
- De functie controleert eerst een kleine, in code vastgelegde lijst met bekende sleutels die daadwerkelijk voorkomen in de shadow-iteratie-artefacten (zie de bestaande switch in `_readableArtifactFields`, main.dart regel ~1143-1291): `findings`, `decision`, `story`, `verdict`, `reason`. Voor elk van deze sleutels retourneert de functie een vast, leesbaar label.
- Voor elke andere sleutel valt de functie terug op generieke humanisering:
  - underscores worden vervangen door spaties (snake_case → spaces),
  - camelCase-woordgrenzen krijgen een spatie,
  - elk woord begint met een hoofdletter.
- De functie is puur en side-effectvrij: geen state, geen widgets, geen netwerkverkeer, geen wijziging aan bestaande rendering/data-ophaling of aan `_readableArtifactFields`/`_roleLabel`. Deze story koppelt de nieuwe functie nergens aan de UI vast — dat is scope van een vervolgstory.
- Vóór implementatie inspecteert de agent geautomatiseerd (grep/read, geen aannames) de huidige staat van de artefact-renderer in `main.dart` na product-factory-37/38 (functie `_readableArtifactFields`, main.dart regel ~1143) en documenteert in de PR-beschrijving de exacte functienaam en locatie waarop wordt voortgebouwd.

## Acceptance criteria
- Unit test bevestigt dat elk van de bekende sleutels `findings`, `decision`, `story`, `verdict`, `reason` een vast, leesbaar label oplevert dat (a) niet gelijk is aan de rauwe sleutelnaam en (b) geen underscore of aaneengeschreven camelCase bevat.
- Unit test bevestigt dat de onbekende snake_case-sleutel `source_urls` wordt omgezet naar `Source Urls` (spatie-gescheiden, elk woord met hoofdletter).
- Unit test bevestigt dat de onbekende camelCase-sleutel `unexpectedFieldName` wordt omgezet naar spatie-gescheiden, met hoofdletter beginnende woorden (bijv. `Unexpected Field Name`).
- Unit test bevestigt dat de functie geen bijwerkingen heeft: dezelfde invoer levert bij herhaalde aanroep altijd dezelfde uitvoer op, zonder state, widget- of netwerkinteractie.
- De PR-beschrijving benoemt expliciet de exacte bestaande functienaam en locatie (`_readableArtifactFields`, main.dart, huidige regelnummer) waarop deze nieuwe functie voortbouwt/aansluit, ter onderbouwing dat de scope op de werkelijke code is gebaseerd.
- Bestaande tests (o.a. `dashboard-frontend/test/iteration_readable_artifact_fields_test.dart`) blijven ongewijzigd slagen; er wordt geen bestaand rendergedrag aangepast.

## Aannames
- De nieuwe functie wordt niet aangeroepen vanuit bestaande rendercode (`_readableArtifactFields`, `_roleLabel`) — dit is een losstaande, nog niet gekoppelde hulpfunctie; koppeling is scope van een vervolgstory.
- "Vast, leesbaar label" voor de bekende sleutels betekent een door de implementerende agent zelf gekozen, mensvriendelijke Nederlandse of Engelse tekst (taal niet voorgeschreven), zolang deze voldoet aan de testcriteria (geen underscore, geen samengevoegde camelCase, afwijkend van de rauwe sleutel).
- Testlocatie is een nieuw of bestaand testbestand in `dashboard-frontend/test/`, conform de bestaande testconventies in die map.

## Eindsamenvatting

## Eindsamenvatting — product-factory-16: pure functie voor leesbare veldnamen

**Wat is gebouwd**
Een nieuwe pure, side-effectvrije functie `humanizeFieldKey(String key)` in `dashboard-frontend/lib/main.dart` (direct na `_roleLabel`, rond regel 1420), die een technische JSON-veldnaam omzet naar een leesbaar label:
- Voor de vijf bekende sleutels `findings`, `decision`, `story`, `verdict`, `reason` levert de functie een vast Nederlands label (bijv. via een in-code mapping).
- Voor elke andere sleutel valt hij terug op generieke humanisering: underscores → spaties, camelCase-woordgrenzen krijgen een spatie, elk woord begint met een hoofdletter (bijv. `source_urls` → `Source Urls`, `unexpectedFieldName` → `Unexpected Field Name`).

De functie is bewust nog niet gekoppeld aan bestaande rendercode (`_readableArtifactFields`, main.dart:1143, en `_roleLabel`, main.dart:1403) — die koppeling is scope van een vervolgstory. Bestaand rendergedrag is ongewijzigd.

**Gemaakte keuzes**
- Afwijking van de letterlijke voorbeeldnaam uit de story (`_humanizeFieldKey`): gekozen voor de publieke naam `humanizeFieldKey` (geen underscore-prefix), omdat Dart-privacy per bestand geldt en een private functie niet vanuit een los testbestand aanroepbaar zou zijn. Dit volgt hetzelfde patroon als bestaande publieke pure functies in `formatting.dart`. De story noemde de naam zelf al als voorbeeld ("bijv."), dus dit valt binnen de vrijheid van de opdracht.

**Wat is getest**
Nieuw testbestand `dashboard-frontend/test/humanize_field_key_test.dart` met unit tests die alle acceptatiecriteria dekken: vaste labels voor de vijf bekende sleutels (afwijkend van de rauwe sleutel, geen underscore/samengevoegde camelCase), correcte omzetting van `source_urls` en `unexpectedFieldName`, en determinisme bij herhaalde aanroep. Volledig vangnet gedraaid: `flutter analyze` (geen issues), `flutter test` (alle 112 tests slagen, inclusief het ongewijzigde `iteration_readable_artifact_fields_test.dart`), en `mvn clean verify` op repo-niveau (BUILD SUCCESS). Review is uitgevoerd en akkoord bevonden, met eigen herhaling van de gerichte tests en formatting-checks.

**Bewust niet gedaan**
De nieuwe functie is niet gekoppeld aan `_readableArtifactFields`/`_roleLabel` of aan enige UI-weergave — dit was expliciet buiten scope en is voorbehouden aan een vervolgstory.

<!-- deploy-summary:start -->
Er is een bouwsteen toegevoegd die technische veldnamen straks netter leesbaar kan maken in het dashboard. Voor gebruikers verandert er op dit moment nog niets zichtbaars; dat volgt in een latere aanpassing.
<!-- deploy-summary:end -->
