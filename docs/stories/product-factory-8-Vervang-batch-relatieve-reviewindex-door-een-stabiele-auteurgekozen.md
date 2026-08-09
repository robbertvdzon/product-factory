# product-factory-8 - Vervang batch-relatieve reviewindex door een stabiele, auteurgekozen sleutel voor dependsOn-verwijzingen, indien bevestigd nodig

## Story

Vervang batch-relatieve reviewindex door een stabiele, auteurgekozen sleutel voor dependsOn-verwijzingen, indien bevestigd nodig

<!-- refined-by-factory -->

## Samenvatting
Wanneer de AI in één keer meerdere storyvoorstellen (een "batch") genereert, kan het ene voorstel verwijzen naar het andere (bijv. "hangt af van Kandidaat 0"). Die verwijzing is nu een los volgnummer dat verandert zodra voorstellen in een andere volgorde worden gereviewd of gepubliceerd, waardoor de koppeling kan gaan wijzen naar het verkeerde voorstel. Deze story geeft elk voorstel bij aanmaak een vaste, herkenbare naam (zoals "stable-candidate-key") die niet meer verandert, en zorgt dat afhankelijkheden voortaan via die naam worden gecontroleerd in plaats van via het volgnummer. Er komt een test die bevestigt dat dit ook werkt als voorstellen in een andere volgorde worden verwerkt.

## Scope
- Vastgesteld: de precondition uit deze story (uitkomst van `verify-dependson-datamodel`, vastgelegd in `docs/architecture/dependson-datamodel.md`) is `uses-positional-index`, dus deze story wordt uitgevoerd (niet geskipt).
- `publish-mechanism-supports-symbolic-keys` staat op `false`. Dit betekent: de implementatie gaat door zoals gepland, maar de agent documenteert expliciet als bekende beperking dat de eigen `dependsOn`-verwijzing van deze kandidaat naar `verify-dependson-datamodel` mogelijk niet resolveert via het bestaande publicatiepad (dat leest `dependsOn` nergens — zie `WorkspacePublisher.kt`, `AutonomousDelivery.kt`, `StoryCandidateApi.kt`).
- Wijziging beperkt tot Product Factory's eigen story-batch-generatie- en validatiecode, met name:
  - `productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowSchemas.kt` — JSON-schema van `candidates[]` (`stories`-schema): toevoegen van een verplicht `candidateKey`-veld (kebab-case-slug, mensleesbaar, uniek binnen de batch).
  - `productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowIterationEngine.kt` — STORY_WRITER-promptlogica (zodat het model per kandidaat een stabiele sleutel genereert of toegekend krijgt), `reviewedCandidates`/`ReviewedCandidate`-verwerking (regels rond 384-393) en `applyAutonomyPolicy` (regel 343) waar `dependsOn` nu tekstueel wordt gescand.
  - `productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowDossierRenderer.kt` — `ReviewedCandidate` (voeg `candidateKey` toe) en rendering van `dependsOn`/`candidateKey` in het dossier.
- Geen wijziging van het bestaande, persistente `story_candidate.id`-veld (database-ID, toegekend na de schrijf-/kritiekcyclus in `saveCandidate`) of het formaat daarvan. De nieuwe sleutel is uitsluitend een aanvullend, in-batch koppelmechanisme vóór publicatie.
- Geen wijziging van data of gedrag van hkh, hkh-autopilot of andere producten.

## Acceptance criteria
- Elk storykandidaat dat binnen een batch wordt gegenereerd, krijgt bij aanmaak een stabiele, mensleesbare symbolische sleutel (kebab-case-slug) die niet verandert wanneer de batch- of reviewvolgorde wijzigt (analoog aan GitHub Actions' `job_id`, GitLab CI's `needs:`-jobnaam, Terraform's resource-adres).
- Het `dependsOn`-veld van elk kandidaat wordt gevalideerd op verwijzing via deze sleutel in plaats van via een batch-relatief volgnummer zoals "Kandidaat 0".
- Een geautomatiseerde regressietest simuleert een batch waarin twee kandidaten via hun sleutel naar elkaar verwijzen en bevestigt dat de `dependsOn`-referentie correct resolveert, ongeacht de volgorde waarin de kandidaten zijn gegenereerd of gereviewed.
- Er wordt geen bestaand persistent backlog-ID-veld (`story_candidate.id`) verwijderd of van formaat veranderd; de nieuwe sleutel wordt uitsluitend gebruikt voor koppelingen binnen een nog niet gepubliceerde batch.
- De wijziging is beperkt tot Product Factory's eigen story-batch-generatie- en validatiecode (zie Scope); er wordt geen data of gedrag van hkh, hkh-autopilot of andere producten gewijzigd.
- Omdat `publish-mechanism-supports-symbolic-keys: false` is vastgesteld: de implementatie wordt alsnog gebouwd zoals gepland, maar de developer documenteert (bijv. in de worklog en/of het dossier) expliciet als bekende, niet-blokkerende beperking dat de eigen `dependsOn`-verwijzing van déze kandidaat naar `verify-dependson-datamodel` mogelijk niet resolveert via het bestaande publicatiepad, omdat dat pad `dependsOn` nooit leest.
- Backend build/tests (`mvn -B --no-transfer-progress clean verify`) blijven slagen, inclusief de nieuwe regressietest.

## Aannames
- De precondition is geverifieerd op basis van het gemergede document `docs/architecture/dependson-datamodel.md` (YAML-frontmatter: `outcome: uses-positional-index`, `publish-mechanism-supports-symbolic-keys: false`, `batch-key: verify-dependson-datamodel`). Deze story wordt dus uitgevoerd, niet geskipt.
- De sleutel wordt toegekend/gegenereerd op het moment dat de STORY_WRITER-rol de batch produceert (bijv. door het model zelf een `candidateKey` per kandidaat te laten opgeven in het JSON-schema, met server-side validatie op kebab-case-formaat en uniciteit binnen de batch); de exacte generatiestrategie (model-gegenereerd vs. server-side afgeleid van de titel) is een implementatiedetail dat de developer mag invullen zolang de sleutel stabiel en mensleesbaar is.
- "Validatie van `dependsOn` op basis van de sleutel" betekent minimaal: waar `dependsOn`-verwijzingen binnen dezelfde batch worden verwerkt (`applyAutonomyPolicy`, dossierrendering), wordt de vergelijking/koppeling gedaan op `candidateKey` in plaats van op arrayvolgorde/tekstpatroon zoals "Kandidaat N"; een volledig nieuw resolutiemechanisme richting het publicatiepad is expliciet geen vereiste (zie beperking hierboven).
- Deze story's eigen batchsleutel binnen déze factory-batch is `stable-candidate-key`, gebruikt puur als factory-interne referentie voor deze taak zelf — dit is niet hetzelfde concept als de `candidateKey` die in productcode wordt gebouwd, en heeft geen invloed op de implementatie.

## Eindsamenvatting

## Eindsamenvatting — product-factory-8: Stabiele candidateKey voor dependsOn-verwijzingen

**Wat is gebouwd**
Elk storykandidaat dat de Product Factory in een batch genereert, krijgt nu bij aanmaak een verplichte, stabiele en mensleesbare `candidateKey` (kebab-case-slug, uniek binnen de batch). Verwijzingen tussen kandidaten (`dependsOn`) worden voortaan via deze sleutel gevalideerd en opgelost, in plaats van via het kwetsbare, batch-relatieve volgnummer ("Kandidaat 0", "Kandidaat 1", ...) dat kon verschuiven zodra kandidaten in een andere volgorde werden gereviewd of gepubliceerd.

**Belangrijkste keuzes**
- `ShadowSchemas.kt`: `candidateKey` toegevoegd als verplicht schemaveld met een kebab-case-patroon.
- `ShadowIterationEngine.kt`: validatie uitgebreid — `candidateKey` verplicht/kebab-case/uniek, en het oude "Kandidaat N"-patroon in `dependsOn` wordt nu expliciet afgewezen. Prompts naar het AI-model (STORY_WRITER) instrueren om per kandidaat een stabiele sleutel te leveren die behouden blijft bij revisie.
- `ShadowDossierRenderer.kt`: nieuwe, pure en ordeonafhankelijke resolutiehelper (`resolveCandidateDependencies`) koppelt `dependsOn`-waarden via een sleutel-lookup (geen arrayindex) aan de juiste kandidaat; het dossier toont nu de `candidateKey` en herkende afhankelijkheden per kandidaat.
- `applyAutonomyPolicy` is bewust ongewijzigd gelaten: die logica scant puur tekstueel en is agnostisch voor het format van `dependsOn`.
- Het bestaande, persistente `story_candidate.id` (database-ID) is niet aangeraakt — de nieuwe sleutel is uitsluitend een in-batch koppelmechanisme vóór publicatie.

**Getest**
Nieuwe/uitgebreide regressietests in `ShadowIterationEngineTest.kt` tonen aan dat twee kandidaten via hun sleutel naar elkaar kunnen verwijzen en correct resolven, ongeacht generatie-/reviewvolgorde (inclusief een test die voorwaartse en achterwaartse invoegvolgorde vergelijkt), en dat het oude positionele "Kandidaat N"-formaat wordt afgewezen. Zowel de gerichte testrun als de volledige `mvn clean verify` over alle backend-modules gaven BUILD SUCCESS, zonder falende of foutieve tests. De reviewer heeft de diff, scope-naleving en teststatus onafhankelijk bevestigd; geen blockers gevonden. Frontend was niet geraakt en is niet apart getest.

**Bewust niet gedaan**
- Geen wijziging aan `story_candidate.id`, `WorkspacePublisher.kt`, `AutonomousDelivery.kt` of `StoryCandidateApi.kt` — het bestaande publicatiepad leest `dependsOn` nergens. Dit is een expliciet vastgelegde, niet-blokkerende beperking: de eigen `dependsOn`-verwijzing van deze kandidaat naar `verify-dependson-datamodel` resolveert mogelijk niet via het publicatiepad, omdat `publish-mechanism-supports-symbolic-keys` op `false` staat.
- Geen wijziging aan het `critic`-schema/`candidateIndex` (blijft index-gebaseerd) — buiten scope.
- Geen data of gedrag van hkh of hkh-autopilot gewijzigd.

<!-- deploy-summary:start -->
Wanneer meerdere nieuwe werkvoorstellen tegelijk worden gemaakt en het ene voorstel verwijst naar het andere, gebeurt die koppeling nu via een vaste, herkenbare naam in plaats van een volgnummer. Hierdoor blijft de verwijzing correct kloppen, ook als de voorstellen later in een andere volgorde worden bekeken of goedgekeurd.
<!-- deploy-summary:end -->
