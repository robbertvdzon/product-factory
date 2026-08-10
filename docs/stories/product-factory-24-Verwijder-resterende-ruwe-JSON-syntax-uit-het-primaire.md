# product-factory-24 - Verwijder resterende ruwe JSON-syntax uit het primaire 'Resultaat en onderbouwing'-paneel per agentrol

## Story

Verwijder resterende ruwe JSON-syntax uit het primaire 'Resultaat en onderbouwing'-paneel per agentrol

<!-- refined-by-factory -->

## Scope

Het primaire "Resultaat en onderbouwing"-paneel in het iteratie-detaildialoog (dashboard-frontend, `IterationSessionDialog`, functie `_readableArtifactFields` in `lib/main.dart`) toont voor een voltooide rol soms nog rauwe JSON (accolades, aanhalingstekens rond veldnamen) in plaats van platte leesbare tekst. Root cause: `_readableArtifactFields` matcht eerst op de bekende `baseRole` (researcher/product_owner/ux_designer/story_writer/critic) en gebruikt dan uitsluitend rolspecifieke helpers die een strikt JSON-type per veld verwachten (bv. `findings` als lijst van objecten). Wijkt het daadwerkelijke content_json van dat verwachte type af (bv. `findings` als losse string, zoals waargenomen bij de Onderzoeker-rol in cyclus shadow-hkh-autopilot-0003), dan leveren alle rolspecifieke helpers niets op. Omdat de `switch` al op de bekende `baseRole` heeft gematcht, wordt de generieke fallback (`_readableGenericFields`, top-level string-/lijstvelden) niet aangeroepen, en valt de UI terug op de kale-JSON-weergave buiten de bestaande "Toon technische details"-toggle om.

De fix past uitsluitend de renderlogica van `_readableArtifactFields` (en indien nodig direct omliggende helperfuncties) aan zodat, voor een bekende `baseRole`, top-level velden die door de rolspecifieke branch geen widgets opleveren, alsnog via de generieke fallback (`_readableGenericFields`/`humanizeFieldKey`) als leesbare tekst/bullets getoond worden — mits het top-level veld een string of een lijst van uitsluitend primitieve waarden is (conform het bestaande candidate-40-fallbackpad). Velden die na zowel de rolspecifieke als de generieke poging niets opleveren (bv. geneste objecten/arrays van objecten binnen een niet-conform artefact) blijven ongewijzigd buiten beschouwing — er wordt geen nieuwe generieke rendering voor geneste structuren toegevoegd.

De bestaande "Toon technische details"-toggle (candidate 38) met de ongewijzigde ruwe JSON blijft onder het leesbare blok intact en ongewijzigd beschikbaar zodra er minstens één leesbaar veld is gerenderd. Voor artefacten waarvoor noch de rolspecifieke branch, noch de generieke fallback ook maar één veld oplevert (dus `readableFields` blijft volledig leeg), verandert er niets: die tonen nog steeds de kale-JSON-fallback zonder toggle, exact als voorheen.

De wijziging blijft volledig beperkt tot de frontend-renderlaag van Product Factory (`dashboard-frontend/lib/main.dart`); geen wijziging aan data, schema of gedrag van HKH Autopilot, en geen wijziging aan het reeds bestaande rolspecifieke of generieke renderpad zelf (alleen aan hoe/wanneer ze gecombineerd worden).

## Acceptance criteria

1. Voor een `researcher`-artefact met content_json `{"findings": "Onvoldoende gedateerde bronnen beschikbaar voor een sluitende tijdlijn."}` toont het primaire resultaatpaneel (buiten de "Toon technische details"-toggle) de platte tekst "Onvoldoende gedateerde bronnen beschikbaar voor een sluitende tijdlijn." zonder omringende accolades of aanhalingstekens rond een veldnaam gevolgd door een dubbele punt.
2. Er is een geautomatiseerde widget-/unit-test die dit exacte scenario (Onderzoeker-rol, findings-veld als losse string met bovenstaande tekst) reproduceert en verifieert dat de geziene tekst in het primaire paneel geen `{`, `}` of `"veldnaam":`-patroon bevat, en dat de leesbare tekst wél aanwezig is.
3. Voor elke bekende `baseRole` (researcher/product_owner/ux_designer/story_writer/critic) geldt: als een top-level veld van het content_json een string is of een lijst van uitsluitend primitieve waarden, en de rolspecifieke branch levert voor dat veld geen widgets op, dan verschijnt het via de generieke fallback (label via `humanizeFieldKey`) alsnog leesbaar in het primaire paneel.
4. De bestaande, reeds correct gerenderde velden voor conforme artefacten (bv. een researcher-artefact met `findings` als correcte objectenlijst) blijven ongewijzigd correct gerenderd — geen regressie op de bestaande rolspecifieke rendering of bestaande tests.
5. De ruwe JSON blijft ongewijzigd en volledig beschikbaar achter de bestaande "Toon technische details"-toggle (candidate 38), zodra er minstens één leesbaar veld getoond wordt.
6. Artefacten zonder enig herkend leesbaar of top-level string-/lijstveld (dus waarvoor `readableFields` na de fix nog steeds volledig leeg is) tonen exact hetzelfde gedrag als vóór de wijziging: kale-JSON-weergave zonder toggle.
7. De wijziging raakt uitsluitend bestanden onder `dashboard-frontend/` (frontend-renderlaag); geen wijziging aan backend, database-schema of aan HKH Autopilot.
8. Bestaande tests in `dashboard-frontend/test/` (o.a. voor `_readableArtifactFields`/`humanizeFieldKey`/de technische-details-toggle) blijven slagen.

## Aannames

- "Primaire paneel" verwijst naar de sectie "Resultaat en onderbouwing" in `IterationSessionDialog` (dashboard-frontend/lib/main.dart, rond regel 1161-1219), niet naar het aparte "Volledig productdossier"-blok (dat toont losstaande, reeds geformatteerde markdown-tekst en is niet in scope).
- Het scenario is reproduceerbaar met een losse, geïsoleerde unit-/widgettest die `_readableArtifactFields` (of de onderliggende helperfuncties) direct aanroept met de gegeven content_json, zonder dat een echte backend-/database-fixture voor cyclus shadow-hkh-autopilot-0003 nodig is.
- De generieke fallback wordt per top-level veld toegepast (aanvullend op de rolspecifieke branch), niet als volledige vervanging van de rolspecifieke branch bij elke mismatch — zo blijven reeds correct werkende rolspecifieke velden (bv. objectenlijsten die wél conform zijn) ongewijzigd via hun bestaande specifieke weergave gerenderd, en wordt alleen het niet-geraakte top-level veld aangevuld.
- Er wordt geen nieuwe generieke rendering toegevoegd voor geneste objecten of arrays-van-objecten binnen een artefact van een bekende rol; dat blijft, net als bij candidate 40, buiten scope.

## Eindsamenvatting

## Eindsamenvatting product-factory-24 / product-138 / product-139

**Wat is gebouwd:**
De renderlogica van het primaire "Resultaat en onderbouwing"-paneel (`IterationSessionDialog._readableArtifactFields` in `dashboard-frontend/lib/main.dart`) is herstructureerd. Voorheen leverde een bekende rol (researcher/product_owner/ux_designer/story_writer/critic) alleen widgets op via de strikt-getypeerde rolspecifieke helper; week het echte `content_json` af van het verwachte type (bv. `findings` als losse string i.p.v. objectenlijst), dan viel de UI stil terug op de kale-JSON-weergave met accolades en aanhalingstekens. Nu geeft een nieuwe helper `_roleSpecificFieldEntries` per top-level veld apart een resultaat terug; levert een specifiek veld geen widgets op (of is het onbekend), dan wordt uitsluitend dat veld alsnog via de bestaande generieke fallback (`_readableGenericFieldEntry`/`humanizeFieldKey`) leesbaar getoond — mits het een string of een lijst van uitsluitend primitieve waarden is. Reeds conforme velden blijven ongewijzigd via hun rolspecifieke pad gerenderd; geneste objecten/objectarrays blijven bewust buiten scope. Voor onbekende rollen en voor artefacten waar helemaal niets leesbaars uit komt, is het gedrag exact als voorheen (kale JSON, geen toggle).

**Keuzes:**
- Per-veld aanvulling in plaats van rol-brede vervanging, zodat er geen regressie optreedt op reeds correct werkende rolspecifieke rendering.
- Geen nieuwe generieke rendering voor geneste structuren toegevoegd (bewust buiten scope, conform de story-aannames).
- De bestaande "Toon technische details"-toggle en het kale-JSON-fallbackpad zijn niet aangeraakt.

**Getest:**
- Nieuwe/aangepaste widgettests in `dashboard-frontend/test/iteration_readable_artifact_fields_test.dart`: exacte reproductie van het live scenario (researcher, `findings` als string, geen `{`, `}` of `"findings":` in het primaire paneel, rauwe JSON blijft achter de toggle), regressietest op een conform researcher-artefact, en een test dat een artefact zonder bruikbaar veld nog steeds de kale-JSON-fallback zonder toggle toont.
- `flutter analyze` (geen issues), `flutter test` (156 tests groen, inclusief 15/15 targeted tests), `dart format --set-exit-if-changed` (geen diff), en `mvn -B clean verify` vanuit de repo-root (BUILD SUCCESS).
- Reviewer heeft diff en scope geverifieerd en goedgekeurd; tester heeft het vangnet herbevestigd. Geen bugs gevonden.

**Bewust niet gedaan:**
- Geen interactieve UI-check in een browser (geen preview-tooling beschikbaar in de agentcontainer); hierop is geleund op end-to-end widgettests die het exacte scenario reproduceren.
- Geen wijzigingen buiten `dashboard-frontend/` (geen backend, schema of HKH Autopilot geraakt), conform AC7.

<!-- deploy-summary:start -->
In het factory-dashboard toonde een resultaatscherm soms rommelige technische tekst (met accolades en aanhalingstekens) in plaats van gewone leesbare tekst, wanneer een onderdeel van een AI-rol een net iets ander formaat had dan verwacht. Dat is nu opgelost: dat onderdeel wordt voortaan gewoon als leesbare tekst getoond, terwijl de rest van het scherm ongewijzigd blijft werken.
<!-- deploy-summary:end -->
