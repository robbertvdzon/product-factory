# product-factory-24 - Worklog

Story-context bij eerste pickup:
Fix generieke fallback per top-level veld in _readableArtifactFields

Herstructureer _readableArtifactFields in dashboard-frontend/lib/main.dart zodat voor een bekende baseRole (researcher/product_owner/ux_designer/story_writer/critic) elk top-level veld waarvoor de rolspecifieke helper geen widgets oplevert (schema-mismatch, bv. findings als losse string i.p.v. objectenlijst), alsnog via _readableGenericFields/humanizeFieldKey leesbaar wordt getoond - mits het veld een string is of een lijst van uitsluitend primitieve waarden. Dit gebeurt aanvullend per veld, niet als vervanging van de rolspecifieke branch; reeds conforme velden blijven via hun bestaande rolspecifieke pad gerenderd zonder duplicatie. Geneste objecten/arrays-van-objecten binnen een niet-conform artefact blijven buiten scope (geen nieuwe generieke rendering daarvoor). Blijft readableFields na rolspecifieke + generieke poging volledig leeg, dan verandert het kale-JSON-fallbackgedrag (buiten de 'Toon technische details'-toggle) niet. Schrijf hierbij ook de vereiste geautomatiseerde test(s) in dashboard-frontend/test/: (1) reproductie van het live scenario - researcher-artefact met content_json {"findings": "Onvoldoende gedateerde bronnen beschikbaar voor een sluitende tijdlijn."} - die verifieert dat de primaire weergave deze platte tekst bevat zonder { } of "veldnaam":-patronen; (2) regressietest dat een conform researcher-artefact (findings als correcte objectenlijst) ongewijzigd correct blijft gerenderd; (3) test dat een artefact zonder enig herkend leesbaar/fallback-bruikbaar veld nog steeds de kale-JSON-weergave zonder toggle toont. Wijziging blijft beperkt tot dashboard-frontend/.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- product-138: `_readableArtifactFields` in `dashboard-frontend/lib/main.dart` herstructureerd naar
  een nieuwe helper `_roleSpecificFieldEntries` die per rolspecifiek top-level veld (bv.
  researcher: summary/findings/currentState/...) een `MapEntry<String, List<Widget>>` teruggeeft
  i.p.v. één platte widgetlijst. `_readableArtifactFields` loopt die entries af: levert een veld
  geen widgets op (schema-mismatch of onbekend), dan wordt voor precies dat top-level veld de
  generieke fallback (`_readableGenericFieldEntry`, nieuw geëxtraheerd uit `_readableGenericFields`)
  toegepast, mits het veld een string of een lijst van uitsluitend primitieven is. Top-level velden
  die de rolspecifieke branch helemaal niet kent (bv. een vreemd extra veld) krijgen dezelfde
  generieke fallback. Voor een onbekende `baseRole` blijft het gedrag exact `_readableGenericFields`
  over alle velden, zoals vóór de wijziging.
- Bestaande test `iteration_readable_artifact_fields_test.dart` ("onherkende JSON-vorm binnen een
  bekende rol") aangepast: 'findings' als losse string toont nu (bedoeld, was de bug) het label
  "Bevindingen" met de platte tekst, i.p.v. stilzwijgend te verdwijnen.
- Nieuwe tests toegevoegd (groep "product-138: generieke fallback per top-level veld voor een
  bekende rol"): (1) exacte reproductie van het live scenario uit cyclus
  shadow-hkh-autopilot-0003 (researcher, `findings` als losse string) met assert dat het primaire
  paneel geen `{`, `}` of `"findings":`-patroon bevat en de rauwe JSON nog steeds volledig achter de
  toggle staat; (2) regressietest dat een conform researcher-artefact (findings als correcte
  objectenlijst) ongewijzigd via de rolspecifieke weergave rendert; (3) test dat een artefact zonder
  enig bruikbaar top-level veld (alleen geneste objecten) nog steeds de kale-JSON-fallback zonder
  toggle toont.
- Gecontroleerd (per agent-tip over eerdere product-97-fix) of andere testbestanden top-level
  string-/primitieve-lijstvelden als "onherkende structuur"-fixture gebruikten die door deze
  wijziging zouden gaan renderen; geen conflicten gevonden (bestaande fallback-fixtures gebruiken
  uitsluitend geneste objecten/objectarrays).
- Vangnet gedraaid: `flutter analyze` (geen issues), `flutter test` (156 tests, alles groen) vanuit
  `dashboard-frontend/`, en `mvn -B --no-transfer-progress clean verify` vanuit de repo-root (BUILD
  SUCCESS, 0 failures/errors). `dart format` gedraaid op de gewijzigde bestanden.
- Wijziging blijft volledig beperkt tot `dashboard-frontend/` (lib/main.dart + test/); geen
  backend-, schema- of HKH-Autopilot-wijzigingen.

## product-139 (tester)

- Diff geïnspecteerd (`git diff main...HEAD -- dashboard-frontend/lib/main.dart`): de nieuwe
  helper `_roleSpecificFieldEntries` geeft per rolspecifiek top-level veld een
  `MapEntry<String, List<Widget>>` terug; `_readableArtifactFields` past voor elke entry zonder
  widgets (en voor onbekende extra top-level keys) `_readableGenericFieldEntry` toe. Dit dekt de
  AC's uit product-139: per-veld fallback, geen vervanging van de rolspecifieke branch bij
  conforme velden, geen nieuwe generieke rendering voor geneste structuren, en de bestaande
  "Toon technische details"-toggle blijft ongewijzigd achter het leesbare blok.
- Testbestand `iteration_readable_artifact_fields_test.dart` bevat de vereiste scenario's: exacte
  reproductie van cyclus shadow-hkh-autopilot-0003 (researcher, findings als losse string, geen
  `{`/`}`/`"findings":` in het primaire paneel, rauwe JSON blijft achter de toggle), regressie op
  een conform researcher-artefact (findings als objectenlijst, geen dubbele rendering), en het
  volledig-lege-fallback-geval (alleen geneste objecten → kale JSON zonder toggle).
- Wijziging raakt uitsluitend `dashboard-frontend/lib/main.dart` en
  `dashboard-frontend/test/iteration_readable_artifact_fields_test.dart` (plus worklogs) - geen
  backend/schema/HKH-Autopilot-bestanden aangeraakt, conform AC7.
- Vangnet gedraaid (alleen `dashboard-frontend/`-prefixmatch in `.factory/verification.yaml` is
  van toepassing; de mvn-command en de twee docker-image-builds hebben geen pathPrefix-match resp.
  zijn agentRunnable: false):
  - `flutter analyze` vanuit `dashboard-frontend/`: "No issues found!" (exit 0).
  - `flutter test` vanuit `dashboard-frontend/`: "All tests passed!", 156 tests, 0 failures,
    exit 0. Inclusief de nieuwe product-138-testgroep.
- Geen preview-omgeving met browsertool beschikbaar in de agentcontainer (zie agent-tip
  `geen-preview-omgeving`/`dashboard-frontend-preview-now-available`); ondanks een gevulde
  SF_PREVIEW_URL in dit run is een interactieve UI-check niet mogelijk, dus is geleund op de
  widgettests die het exacte AC1/AC2-scenario end-to-end reproduceren (dialoog openen, tegel
  uitklappen, tekstassertie op het gerenderde paneel).
- Geen bugs gevonden; geen code/tests/infra gewijzigd. Alleen deze worklog bijgewerkt.
