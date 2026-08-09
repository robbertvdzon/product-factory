# product-factory-5 - Vergrendel de conclusion-waarde van een iteratie zodra de terminale staat is bereikt, zodat latere events deze nooit overschrijven

## Story

Vergrendel de conclusion-waarde van een iteratie zodra de terminale staat is bereikt, zodat latere events deze nooit overschrijven

<!-- refined-by-factory -->

## Samenvatting
Bij CircleCI is een storend randgeval gezien: een al afgeronde uitkomst ('failed') werd achteraf stilletjes overschreven door een latere, ongerelateerde gebeurtenis. Dat risico willen we uitsluiten voor de eindstatus van een productcyclus in Product Factory. Deze story laat een agent eerst grondig uitzoeken of dat risico hier daadwerkelijk bestaat, en pas als dat zo is, een kleine beveiliging toevoegen die een tweede, latere wijziging van een al afgeronde uitkomst tegenhoudt en zichtbaar logt — in plaats van de waarde stilzwijgend te wijzigen. Bestaat het risico niet (of blijkt het te ingewikkeld om volledig te checken), dan wordt alleen die bevinding vastgelegd en verandert er niets aan de code.

## Scope
- Voorwaardelijkheid aan kandidaat 24 (`product-factory-3`, gemerged in PR #37): diens eigen Eindsamenvatting bevestigt dat er **geen apart 'conclusion'-veld** bestaat — de badge in `dashboard-frontend/lib/classification.dart` is een pure UI-afleiding van het bestaande `ShadowIterationView.status`-veld (`productfactory-contracts/.../Contracts.kt`), niet een zelfstandig geschreven veld. Er is dus, strikt genomen, geen "echte status/conclusion-scheiding op een bestaand veld" gebouwd zoals AC1 vereist.
- Los daarvan bestaat er wél een bestaand backend-veld `critic_verdict` (kolom op `shadow_iteration`, naast `status`) dat de facto de conclusie van een iteratie vastlegt. Dit veld wordt geschreven door drie plekken in `productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowIterationApi.kt`:
  - `markAccepted(...)` (regel ~322-339): zet `status='ACCEPTED'` + `critic_verdict`
  - `markReviewed(...)` (regel ~341-349): zet `status` (NEEDS_REVISION/REJECTED) + `critic_verdict`
  - `markFailed(...)` (regel ~351-358): zet `status='FAILED'` (geen `critic_verdict`)
  Alle drie zijn unconditionele `update shadow_iteration set ... where id = ?`, zonder guard tegen een iteratie die al in een terminale staat (ACCEPTED/NEEDS_REVISION/REJECTED/FAILED) staat.
- De implementerende agent voert zelf, geautomatiseerd, de inspectie uit die AC1/AC2 vereisen (bovenstaande bevindingen zijn een startpunt, geen vervanging) en registreert exact één van de vier mogelijke uitkomsten:
  1. Precondities niet vervuld (kandidaat 24 bouwde geen status/conclusion-scheiding op een apart veld) → **geen enkele codewijziging**, alleen een gedocumenteerde bevinding (conform AC1).
  2. `confirmed-immutable-native` — het bestaande schrijfpad blijkt al van nature write-once.
  3. `guard-added` — het schrijfpad is kwetsbaar gebleken; er is een schrijf-eenmaal-guard toegevoegd op het bestaande veld (geen nieuw schemaveld/kolom).
  4. `unconfirmed-partial-coverage` — het schrijfpad is te complex/asynchroon om binnen deze story volledig te dekken; alleen een bevinding, geen garantie.
- Bij `guard-added`: de guard negeert een tweede schrijfpoging op `status`/`critic_verdict` voor een iteratie die al in een terminale staat staat, en logt dit als afgewezen (traceerbare logregel met iteratie-id), in plaats van de bestaande waarde te overschrijven.
- Geen nieuw databaseveld/schema-element, geen migratie, geen wijziging aan Git-, GitHub-, OpenShift-, PR-goedkeuringsflow of clusterconfiguratie.

## Acceptance criteria
- De vastgestelde uitkomst wordt vastgelegd als exact één van: precondities-niet-vervuld (alleen bevinding, geen code), `confirmed-immutable-native`, `guard-added`, of `unconfirmed-partial-coverage`; dit staat expliciet en machineleesbaar in de story-oplevering/worklog van deze story.
- Indien precondities niet vervuld zijn (kandidaat 24 heeft geen status/conclusion-scheiding op een apart veld gebouwd): geen enkele codewijziging, alleen een gedocumenteerde bevinding.
- Bij `guard-added`: een tweede schrijfpoging op het bestaande conclusion-veld (`status`/`critic_verdict` op `shadow_iteration`) voor een iteratie die al in een terminale staat staat, wordt genegeerd en gelogd in plaats van de bestaande waarde te overschrijven.
- Geen nieuw databaseveld of schema-element in geen enkel scenario; elke wijziging werkt uitsluitend op de reeds bestaande `status`/`critic_verdict`-kolommen van `shadow_iteration`.
- Bij `confirmed-immutable-native` of `guard-added`: een geautomatiseerde test simuleert twee opeenvolgende iteraties en verifieert dat de opgeslagen conclusion-waarde van de eerste, al afgeronde iteratie ongewijzigd blijft nadat de tweede iteratie start en afrondt.
- Geen wijziging aan Git-, GitHub-, OpenShift-, database-schema- of PR-goedkeuringsflow buiten de beschreven guard-logica.
- Een afgewezen schrijfpoging door de guard wordt vastgelegd als traceerbare logregel (met iteratie-id) zodat toekomstige diagnose mogelijk blijft.

## Aannames
- "Het bestaande conclusion-veld" wordt geïnterpreteerd als de bestaande kolommen `status` en `critic_verdict` op `shadow_iteration` (geschreven via `markAccepted`/`markReviewed`/`markFailed` in `ShadowIterationApi.kt`), niet als een apart, nieuw te definiëren veld.
- Op basis van kandidaat 24's eigen, reeds gepubliceerde Eindsamenvatting (PR #37) is aannemelijk dat de precondities van AC1 niet vervuld zijn (geen zelfstandige status/conclusion-scheiding op een apart veld gebouwd); de implementerende agent bevestigt dit zelf opnieuw voordat een uitkomst wordt vastgelegd, conform AC2.
- `markRunning` (zet alleen `status='RUNNING'`, alleen vanuit `QUEUED`) en `startStep`/`completeStep`/`failStep` (stap-niveau, raken `status`/`critic_verdict` van de iteratie zelf niet) vallen buiten scope van de guard.

## Eindsamenvatting

Ik heb voldoende informatie. Nu stel ik de eindsamenvatting op.

## Eindsamenvatting — product-factory-5: Write-once-guard op conclusion-waarde van een iteratie

**Onderzoek eerst (conform AC1/AC2):** de agent heeft zelf herbevestigd dat er géén apart `conclusion`-veld bestaat op een iteratie — `status` en `critic_verdict` zijn de bestaande, losse kolommen op `shadow_iteration` die samen de conclusie vastleggen. De drie schrijfpaden (`markAccepted`, `markReviewed`, `markFailed` in `ShadowIterationApi.kt`) bleken inderdaad unconditionele `update ... where id = ?`-queries te zijn, zonder guard tegen een iteratie die al in een terminale staat staat. Het risico dat bij CircleCI gezien werd, bestond dus daadwerkelijk voor dit schrijfpad.

**Vastgestelde uitkomst: `guard-added`.**

**Wat is gebouwd:**
- Alle drie de schrijfmethoden kregen een extra voorwaarde in hun WHERE-clausule (`and status not in ('ACCEPTED','NEEDS_REVISION','REJECTED','FAILED')`), zodat een tweede schrijfpoging op een al-afgeronde iteratie 0 rijen raakt in plaats van de bestaande status/critic_verdict-waarde stilzwijgend te overschrijven.
- Een genegeerde tweede schrijfpoging wordt traceerbaar gelogd (SLF4J `log.warn`, met iteratie-id), per methode met een eigen boodschap.
- Er is géén nieuw databaseveld, kolom of migratie toegevoegd — precies zoals de AC's vereisten.
- Geen wijziging aan Git-, GitHub-, OpenShift- of PR-goedkeuringsflow.

**Getest:**
- Nieuwe geautomatiseerde test (`ShadowIterationRepositoryWriteOnceGuardTest`) simuleert twee opeenvolgende afrondingen van dezelfde iteratie en bevestigt dat de conclusie-waarde van de eerste, al afgeronde iteratie ongewijzigd blijft.
- Volledig vangnet (`mvn clean verify`) groen: 37 + 17 + 7 tests, 0 failures.
- Tester heeft de code onafhankelijk geverifieerd, de WARN-logregels bij genegeerde schrijfpogingen bevestigd, en de preview-omgeving (frontend + API health) met HTTP 200 gecontroleerd.

**Bewust niet gedaan:**
- Geen frontend-tests (`flutter analyze`/`flutter test`) opnieuw gedraaid — er is geen frontendcode gewijzigd, deze subtaak is backend-only.
- Geen interactieve UI-verificatie — geen browsertool beschikbaar in de agentcontainer; volstaan met HTTP-smoketests op de preview-omgeving.

<!-- deploy-summary:start -->
Er is een klein beveiligingsprobleem verholpen: zodra een productcyclus een definitieve uitkomst heeft (bijvoorbeeld "geslaagd" of "mislukt"), kan die uitkomst nu niet meer per ongeluk door een latere, ongerelateerde gebeurtenis worden overschreven. Als zoiets toch geprobeerd wordt, wordt dat voortaan zichtbaar vastgelegd in plaats van stilletjes te gebeuren. Er is verder niets aan het uiterlijk of de bediening van de applicatie veranderd.
<!-- deploy-summary:end -->
