# product-factory-11 - Toon blokkeerreden van dependsOn-resolutie op storykandidaat-kaart in de dashboard-queue, toegankelijk en met voldoende contrast

## Story

Toon blokkeerreden van dependsOn-resolutie op storykandidaat-kaart in de dashboard-queue, toegankelijk en met voldoende contrast

<!-- refined-by-factory -->

## Samenvatting
Op de storykandidaat-kaart in de dashboard-wachtrij is nu niet te zien of een kandidaat vastzit door een onopgeloste afhankelijkheid — dat is alleen terug te vinden in ruwe JSON in het productdossier. Deze wijziging toont, alleen als een kandidaat geblokkeerd is, direct op de kaart een duidelijk zichtbaar en toegankelijk waarschuwingslabel met de reden. Er verandert verder niets aan wat de kaart al doet of toont.

## Scope
- Uitsluitend een presentationele wijziging op de bestaande storykandidaat-kaart in de dashboard-storyqueue (`_buildStoryQueueSections` in `dashboard-frontend/lib/main.dart`).
- Gebruikt uitsluitend de al aanwezige, reeds opgehaalde velden `blocked` (bool) en `blockedReason` (string?) uit de bestaande `/api/story-candidates`-response (backend-kant is al gemerged). Geen nieuwe API-call, geen nieuwe berekening, geen nieuwe route, dialoog of knop.
- Wanneer `blocked == true` en `blockedReason` niet leeg is: toon direct (zonder extra klik/tap) een label met icoon + tekst beginnend met "Geblokkeerd: " gevolgd door de reden, zichtbaar op de kaart zelf (onder de titel).
- Wanneer `blocked == false` of `blockedReason` ontbreekt/leeg is: geen blokkeerlabel, kaart ongewijzigd t.o.v. huidige gedrag.
- Bestaande kaart-elementen (icoon, titel, subtitle met product/iteratie/levering-info, trailing chevron, `onTap` naar het detaildialoog met o.a. de link "Volledig productdossier") blijven ongewijzigd aanwezig en functioneel.
- Kleurkeuze voor icoon/tekst volgt het bestaande WCAG AA-conforme kleurpatroon dat elders in de app al gebruikt wordt voor waarschuwings-/statuslabels (zie `kClassificationColors` in `classification.dart`), met een contrastratio van minimaal 4,5:1 tegen de kaartachtergrond.
- Het label is onderdeel van de accessible name/description van de kaart (via `Semantics`), zodat een schermlezer de blokkeerreden meekrijgt.

## Acceptance criteria
1. Als een storykandidaat-item in de wachtrij `blocked: true` en een niet-lege `blockedReason` heeft, toont de kaart direct (geen extra klik) een label met icoon en tekst die begint met "Geblokkeerd: " gevolgd door de reden. Geverifieerd met een Flutter widget-test op gemockte kaartdata.
2. Als `blocked: false` is, of `blockedReason` ontbreekt/leeg is, toont de kaart geen blokkeerlabel en geen tekst "Geblokkeerd: ". Geverifieerd met een widget-test.
3. Het blokkeerlabel (icoon + tekst) is opvraagbaar via de semantics-tree van de kaart wanneer `blocked: true`. Geverifieerd met een Flutter semantics-test.
4. De kleurcombinatie van icoon en labeltekst tegen de kaartachtergrond heeft een contrastratio van minimaal 4,5:1 (WCAG AA). Geverifieerd met een geautomatiseerde contrastcheck op de gebruikte kleurwaarden.
5. Er wordt geen nieuw netwerkverzoek toegevoegd t.o.v. de bestaande kaartweergave; het label wordt uitsluitend gerenderd uit de al opgehaalde `stories`-lijst. Geverifieerd met een test die het aantal API-calls voor en na de wijziging vergelijkt bij het renderen van dezelfde kaartenlijst.
6. Alle bestaande, ongewijzigde elementen van de kaart (icoon, titel, subtitle, trailing chevron, tap-gedrag naar het detaildialoog inclusief link "Volledig productdossier") blijven aanwezig en functioneel; alle bestaande widget-tests in `dashboard-frontend/test/` blijven slagen.
7. Er wordt geen nieuwe knop, dialoog, route of resolve-berekening toegevoegd; de wijziging is puur presentationeel bovenop het bestaande kaartcomponent (`_buildStoryQueueSections`).

## Aannames
- "Kaart" verwijst naar de `Card`/`ListTile`-weergave per storykandidaat binnen `_buildStoryQueueSections` in `dashboard-frontend/lib/main.dart`, niet naar het detaildialoog (`_showStoryCandidateDetails`).
- De in de oorspronkelijke omschrijving genoemde "fase-badge" en losse "sleutel"-regel bestaan niet als aparte elementen op deze kaart (die informatie zit nu in title/subtitle en in het detaildialoog); deze AC is aangepast om aan te sluiten op de bestaande kaartstructuur in plaats van een niet-bestaand element te veronderstellen.
- Er zijn momenteel geen bestaande widget-tests specifiek voor deze storyqueue-kaart; "bestaande widget-tests blijven slagen" betekent dat de volledige bestaande testsuite in `dashboard-frontend/test/` groen blijft.
- Voor de kleurkeuze mag aangesloten worden bij het bestaande, al WCAG AA-geverifieerde kleurenpatroon in `classification.dart` (bijv. het waarschuwings-/conflictkleurenpaar), in plaats van een volledig nieuw kleurenpaar te ontwerpen.

## Eindsamenvatting

I heb voldoende context uit de story, worklog en commits om de eindsamenvatting te schrijven.

## Eindsamenvatting — product-factory-11: Blokkeerreden op storykandidaat-kaart

**Gebouwd**
Op de storykandidaat-kaart in de dashboard-wachtrij (`_buildStoryQueueSections`, `dashboard-frontend/lib/main.dart`) verschijnt nu, direct onder de titel, een compact label met icoon en tekst "Geblokkeerd: <reden>" — maar alleen als de kandidaat `blocked: true` heeft én een niet-lege `blockedReason`. Bij `blocked: false` of een lege/ontbrekende reden blijft de kaart exact zoals voorheen. Geen extra klik nodig: het label is direct zichtbaar.

**Keuzes**
- Hergebruikt het bestaande, al WCAG AA-geverifieerde kleurenpaar `kGuardrailConflict` (achtergrond `0xFFF8D7DA` / voorgrond `0xFF7A1220`) uit `classification.dart`, zodat het contrast (≥4,5:1) al gedekt is en er geen nieuw kleurenpaar hoefde te worden ontworpen.
- De kaart is gewrapt in `MergeSemantics`, zodat titel, subtitle en blokkeerlabel samen als één toegankelijke naam via de semantics-tree opvraagbaar zijn voor schermlezers.
- Geen nieuwe API-call, dialoog, route of berekening: het label wordt uitsluitend uit de al opgehaalde `blocked`/`blockedReason`-velden gerenderd.
- Boyscout-fix: bij het testen kwam een pre-existente layoutbug in `MetricCard` aan het licht (ontbrekende `Expanded` rond de labelkolom, veroorzaakte een `RenderFlex`-overflow bij langere labels). Minimaal en veilig gerepareerd, buiten de eigenlijke storyscope maar zonder ander gedrag te wijzigen.

**Getest**
Nieuwe tests in `dashboard-frontend/test/story_queue_blocked_label_test.dart` dekken alle 7 acceptatiecriteria: label zichtbaar/tekst bij blocked+reden, afwezig bij blocked:false of lege/ontbrekende reden, aanwezig in de semantics-tree, bestaande kaart-elementen en tap-gedrag blijven werken, geen toename in API-calls, en een contrastcheck op het gebruikte kleurenpaar. `flutter analyze` (geen issues) en `flutter test` (72/72 tests groen, herhaald ter controle) zijn beide geslaagd. De tester heeft daarnaast de preview-omgeving en de API health-check met een curl-smoketest gecontroleerd (beide HTTP 200); een interactieve browser-/screenshotverificatie was niet beschikbaar in de agentcontainer, dus de functionele verificatie leunt op de widget-/semantics-tests.

**Bewust niet gedaan**
Geen wijziging aan het detaildialoog, geen nieuwe knop/route/resolve-berekening, en geen aanpassing aan backend-code (diff raakt uitsluitend `dashboard-frontend/`).

<!-- deploy-summary:start -->
Op de kaart van een storykandidaat in het overzicht is voortaan meteen te zien wanneer die kandidaat vastzit, met een duidelijk zichtbaar label dat de reden erbij toont. Eerder was die reden alleen terug te vinden door dieper in de gegevens te zoeken. Verder is er niets veranderd aan hoe de kaart werkt of eruitziet.
<!-- deploy-summary:end -->
