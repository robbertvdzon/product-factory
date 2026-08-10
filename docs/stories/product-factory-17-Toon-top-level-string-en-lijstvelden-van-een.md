# product-factory-17 - Toon top-level string- en lijstvelden van een rolresultaat generiek leesbaar wanneer het rolspecifieke schema niet matcht

## Story

Toon top-level string- en lijstvelden van een rolresultaat generiek leesbaar wanneer het rolspecifieke schema niet matcht

<!-- refined-by-factory -->

## Samenvatting
Op dit moment tonen alle zichtbare productcycli rauwe, ingesprongen JSON voor agentresultaten, omdat de gebruikte rollen een vereenvoudigd schema opleveren dat niet overeenkomt met het bestaande rijke schema per rol. Deze story voegt een generieke vangnet-weergave toe: als het rijke schema niet matcht, worden top-level tekstvelden en simpele lijsten alsnog leesbaar getoond met een herkenbaar label, in plaats van rauwe JSON. Complexere, geneste data blijft gewoon als rauwe JSON getoond, zoals nu al het geval is.

## Scope
- Uitbreiding van de bestaande artefact-renderer (`_readableArtifactFields`, `dashboard-frontend/lib/main.dart`, huidige default-branch `_ => const <Widget>[]` rond regel 1289) met een generieke fallback voor wanneer geen van de bestaande rolspecifieke branches (researcher/product_owner/ux_designer/story_writer/critic) matcht.
- De fallback itereert over de top-level sleutels van de gedecodeerde artefact-JSON en toont per sleutel een gelabelde leesbare regel wanneer de waarde:
  - een string is, of
  - een lijst is die uitsluitend uit primitieve waarden bestaat (string, getal, boolean).
- Het label per veld wordt afgeleid via de reeds bestaande, nog ongekoppelde functie `humanizeFieldKey` (main.dart, regel ~1425), die al specifieke labels kent voor `findings`, `decision`, `story`, `verdict` en `reason`.
- Bevat het artefact geen enkel top-level veld dat aan bovenstaande voorwaarde voldoet (bv. uitsluitend geneste objecten of arrays van objecten), dan blijft het bestaande gedrag ongewijzigd: de aanroeper toont in dat geval de rauwe JSON zoals nu al gebeurt wanneer `_readableArtifactFields` een lege lijst teruggeeft (main.dart, regel ~1015-1021), zonder toggle.
- Levert de generieke fallback wél widgets op, dan geldt hetzelfde bestaande gedrag als bij een rijk schema: de rauwe JSON verdwijnt achter de bestaande, standaard ingeklapte `TechnicalDetailsToggle` ('Toon technische details').
- Reeds matchende rijke rolspecifieke schema's (bv. researcher met summary/findings/sources) blijven via hun bestaande switch-branch renderen; deze wijziging raakt alleen de default-branch.
- Geen wijziging aan HKH Autopilot, backend, of databronnen; puur een aanpassing van de frontend-renderlaag.
- De implementerende agent inspecteert eerst de huidige integratie van `humanizeFieldKey` en de exacte structuur van `_readableArtifactFields` in main.dart, en documenteert eventuele afwijkingen van deze beschrijving in de pull request.

## Acceptance criteria
- Widget- of golden-test met een fixture die uitsluitend een top-level `findings`-veld bevat (string, bv. de tekst over vrijwilligers en herkenbare huisnummers) bevestigt dat de output een gelabelde leesbare regel bevat (label via `humanizeFieldKey('findings')` = "Bevindingen") en geen rauw, ingesprongen JSON-blok als primaire content toont.
- Widget- of golden-tests met fixtures die respectievelijk alleen een `decision`-veld (string), alleen een `story`-veld (string), en een `verdict`- plus `reason`-veld (beide strings) bevatten, bevestigen voor elk dat alle top-level velden als gelabelde leesbare regels verschijnen in plaats van rauwe JSON.
- Regressietest hergebruikt de bestaande testfixtures uit product-factory-37/38 voor een matchend rijk rolschema (bv. researcher met summary/findings-lijst/sources) en bevestigt dat de output structureel identiek blijft aan vóór deze wijziging.
- Test met een fixture die uitsluitend geneste objecten of een array van objecten op top-level bevat, bevestigt dat het bestaande fallbackgedrag (rauwe JSON, geen toggle) ongewijzigd blijft en er geen lege of kapotte weergave ontstaat.
- Test bevestigt dat een top-level lijstveld met gemengde of niet-primitieve elementen (bv. een lijst van objecten) niet als generieke leesbare regel wordt getoond, maar het bestaande rauwe-JSON-fallbackpad volgt.
- De pull request documenteert de inspectie van de bestaande `humanizeFieldKey`-integratie en `_readableArtifactFields`-structuur, inclusief eventuele geconstateerde afwijkingen van deze beschrijving.

## Aannames
- "Top-level" betekent: direct op het hoogste niveau van de gedecodeerde artefact-JSON (`Map<String, dynamic>`), niet genest binnen een ander object of array.
- Een leeg string-veld of een lege lijst levert geen regel op, analoog aan het bestaande gedrag van `_readableText`/`_readableBulletList` voor de rijke schema's.
- De volgorde van de getoonde velden volgt de sleutelvolgorde zoals die uit `jsonDecode` komt (insertion order van de JSON), er wordt geen aparte sortering toegevoegd.
- `null`-waarden voor een top-level veld worden overgeslagen (geen lege regel of het woord "null"), consistent met bestaand gedrag elders in dezelfde renderer.
- De generieke fallback wordt alleen toegepast wanneer de rolspecifieke switch geen enkele widget oplevert (huidige `_ => const <Widget>[]`-pad); dit geldt ook als een bekende rol wél matcht maar toevallig geen van zijn eigen velden gevuld heeft — in dat geval blijft het bestaande gedrag (lege lijst, dus rauwe JSON) behouden en wordt niet alsnog de generieke fallback op dezelfde data toegepast, om regressie op de rijke schema's uit te sluiten.

## Eindsamenvatting

Ik heb voldoende context uit het worklog. Dit is een generieke, afgebakende frontend-wijziging zonder open vragen — ik lever nu de eindsamenvatting.

## Eindsamenvatting product-factory-17

**Wat is gebouwd**
De artefact-renderer in het dashboard (`dashboard-frontend/lib/main.dart`) toonde tot nu toe rauwe, ingesprongen JSON zodra een rolresultaat niet exact matchte met een van de vijf bestaande rolspecifieke schema's (researcher/product_owner/ux_designer/story_writer/critic). Er is een generieke vangnet-weergave (`_readableGenericFields`) toegevoegd die de bestaande lege default-branch vervangt: top-level velden die een string zijn, of een lijst van uitsluitend primitieve waarden (string/getal/boolean), worden nu getoond als gelabelde leesbare regels. Labels komen van de al bestaande maar nog niet-gekoppelde functie `humanizeFieldKey`.

**Belangrijkste keuzes**
- Alleen de default-branch is aangepast; de vijf bestaande rijke rolschema's zijn functioneel ongewijzigd gebleven.
- Geneste objecten, arrays van objecten, `null`-waarden en lijsten met gemengde/niet-primitieve elementen worden bewust overgeslagen — die vallen terug op het bestaande rauwe-JSON-pad zonder toggle, om geen vals-positieve of kapotte weergave te geven.
- Twee bestaande testfixtures (met een los top-level stringveld dat als "niet-herkende structuur" bedoeld was) zijn aangepast naar uitsluitend geneste data, omdat ze anders door de nieuwe functionaliteit terecht wél leesbare content zouden opleveren — dit is toegelicht als bewuste, in-scope aanpassing, geen scope-afwijking.

**Getest**
- `flutter analyze`: geen issues.
- Volledige `flutter test`-suite: 117/117 groen (developer + tester run, onafhankelijk bevestigd).
- Gerichte tests dekken alle acceptatiecriteria: losse `findings`/`decision`/`story`-velden en gecombineerde `verdict`+`reason` tonen gelabelde regels; regressietest op bestaande researcher-fixture blijft structureel identiek; fixtures met alleen geneste objecten/objectarrays en met gemengde lijsten blijven correct op de rauwe-JSON-fallback zonder toggle.
- Preview-omgeving (PR-51) smoke-getest: frontend en backend health-endpoint beide 200. Visuele/browserverificatie was niet mogelijk (geen browsertool in de agentcontainer); dit is expliciet gedekt via widgettests en codeinspectie in plaats daarvan.
- Backend-verificatie (`mvn clean verify`) niet getriggerd, want er zijn geen backendwijzigingen — terecht buiten scope.

**Bewust niet gedaan**
- Geen wijzigingen aan backend, datastructuur van agentresultaten, of andere renderpaden dan de default-branch.
- Geen sortering of herordening van velden toegevoegd; volgorde volgt de JSON-insertion-order, zoals afgesproken in de aannames.

<!-- deploy-summary:start -->
Wanneer een AI-medewerker een resultaat aanlevert dat niet in een van de bekende formaten past, laat het dashboard dat resultaat voortaan gewoon leesbaar zien in plaats van onleesbare technische tekst. Alleen bij echt complexe, diepgenestelde resultaten blijft de technische weergave zichtbaar, zoals voorheen. Er verandert verder niets aan hoe bestaande, herkende resultaten worden getoond.
<!-- deploy-summary:end -->
