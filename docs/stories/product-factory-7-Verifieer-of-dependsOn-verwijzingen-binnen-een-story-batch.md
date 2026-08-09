# product-factory-7 - Verifieer of dependsOn-verwijzingen binnen een story-batch nu op een batch-relatieve positie-index of op een stabiele sleutel zijn gebaseerd

## Story

Verifieer of dependsOn-verwijzingen binnen een story-batch nu op een batch-relatieve positie-index of op een stabiele sleutel zijn gebaseerd

<!-- refined-by-factory -->

## Samenvatting
Bij het schrijven van storykandidaten in een batch verwijst een kandidaat soms naar een andere kandidaat uit dezelfde batch (bijv. "Kandidaat 0"). Onduidelijk is of zo'n verwijzing een stabiel ID is of een tijdelijk volgnummer dat verdwijnt zodra de kandidaat gepubliceerd wordt. Deze story onderzoekt alleen hoe dat nu daadwerkelijk werkt in de code, en legt de uitkomst vast in een document, zodat latere stories daarop kunnen voortbouwen zonder zelf opnieuw te hoeven uitzoeken. Er verandert geen gedrag van het systeem.

## Scope
- Geautomatiseerde, agent-uitgevoerde inspectie van de broncode die story-batchkandidaten en hun `dependsOn`-veld genereert, valideert en verwerkt, met name:
  - `productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowSchemas.kt` (JSON-schema van `candidates[].dependsOn`)
  - `productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowIterationEngine.kt` (STORY_WRITER-/CRITIC-promptlogica, `ReviewedCandidate`, `candidateIndex`-verwerking)
  - `productfactory/src/main/kotlin/nl/vdzon/productfactory/iteration/ShadowDossierRenderer.kt` (rendering van `dependsOn` in het dossier)
  - het publicatiepad (`WorkspacePublisher.kt`, `AutonomousDelivery.kt`, `StoryCandidateApi.kt`) om vast te stellen of `dependsOn` daar ooit wordt gelezen/geresolved.
- Geen wijziging van schema, prompts, publicatielogica of enig ander functioneel gedrag.
- Resultaat is uitsluitend een nieuw, doorzoekbaar documentatiebestand binnen de repo (bijv. onder `docs/architecture/`), geen wijziging van bestaande bestanden buiten die ene nieuwe/aangevulde documentatiepagina.

## Acceptance criteria
- De inspectie is volledig automatisch uitgevoerd door de implementerende agent (geen menselijke tussenkomst) en behandelt expliciet: het JSON-schema van `dependsOn` (vrije string vs. formeel ID-type), of `dependsOn`-waarden ergens in de codebase geparsed/gevalideerd/geresolved worden naar een concreet kandidaat- of story-ID, en of het publicatiemechanisme (workspace-publish, autonome levering, storykandidaat-API) `dependsOn` op enige manier leest of gebruikt.
- De uitkomst wordt vastgelegd als machineleesbaar veld met exact één van: `uses-positional-index`, `uses-stable-key-already`, `uses-persistent-id-only`, `inconclusive`.
- Bij uitkomst `uses-positional-index`: het document bevat een concreet code- of gedragsvoorbeeld (bestand + regel/verwijzing) dat aantoont dat een `dependsOn`-verwijzing als "Kandidaat 0" een batch-relatieve positie is, geen stabiele sleutel.
- Er wordt een aparte machineleesbare boolean `publish-mechanism-supports-symbolic-keys` vastgelegd, met een korte onderbouwing die aangeeft of het publicatiepad een vrije symbolische sleutel (string) in `dependsOn` zou kunnen resolveren, dan wel `dependsOn` daar helemaal niet leest.
- Het document is een op zichzelf staand, doorzoekbaar bestand in de repo (nieuw bestand, bijv. `docs/architecture/dependson-datamodel.md`), leesbaar door latere stories zonder herinspectie, en vermeldt de batchsleutel van deze story (`verify-dependson-datamodel`) zodat afhankelijke stories de bron kunnen herkennen.
- Als de code op dit punt niet ondubbelzinnig te doorgronden is (bijv. verspreide of asynchrone verwerking), wordt in plaats van een aanname `inconclusive` vastgelegd, met motivatie welke code-paden onduidelijk bleven.
- Geen bestaand functioneel gedrag (schema, prompts, publicatie, tests) wordt gewijzigd; alleen het nieuwe documentatiebestand wordt toegevoegd.
- Backend build/tests (`mvn -B --no-transfer-progress clean verify`) blijven ongewijzigd slagen, aangezien er geen productiecode wordt aangepast.

## Aannames
- "Machineleesbaar" betekent: de vier waarden en de boolean staan in het documentatiebestand in een vast, eenduidig te parsen formaat (bijv. YAML-frontmatter of een vaste key-value-regel), niet noodzakelijk in een apart JSON-bestand.
- Het documentatiebestand hoort thuis onder `docs/architecture/` (bestaande plek voor dit soort systeeminzicht, zie `shadow-mode.md`), niet onder `docs/factory/` (dat is factory-configuratie, geen productinzicht).
- Op basis van eigen code-inspectie tijdens refinement is `dependsOn` in het huidige datamodel een los JSON-schema-veld van vrije strings (`ShadowSchemas.kt`), dat nergens in de engine of het publicatiepad wordt geparsed of geresolveerd naar een candidate-/story-ID (geen treffers in `WorkspacePublisher.kt`, `AutonomousDelivery.kt`, `StoryCandidateApi.kt`); dit maakt de story goed uitvoerbaar zonder verspreide/asynchrone verwerking die tot `inconclusive` zou dwingen, maar de implementerende agent bepaalt zelf de definitieve uitkomst.
- Deze story heeft zelf geen `dependsOn` naar andere kandidaten in deze batch nodig (het is de eerste/onderzoekende stap).

## Eindsamenvatting

Ik heb voldoende context om de eindsamenvatting te schrijven.

## Eindsamenvatting

**Story product-factory-7**: Verifieer of `dependsOn`-verwijzingen binnen een story-batch op een positie-index of stabiele sleutel gebaseerd zijn.

**Wat is gedaan**
Dit was een puur onderzoekende story zonder codewijziging. De implementerende agent heeft de volledige keten geïnspecteerd waarin storykandidaten en hun `dependsOn`-veld ontstaan en verwerkt worden: het JSON-schema (`ShadowSchemas.kt`), de generatie-/kritieklogica (`ShadowIterationEngine.kt`), de dossierweergave (`ShadowDossierRenderer.kt`) en het publicatiepad (`WorkspacePublisher.kt`, `AutonomousDelivery.kt`, `StoryCandidateApi.kt`).

**Uitkomst**
Vastgelegd in nieuw document `docs/architecture/dependson-datamodel.md`, met machineleesbare YAML-frontmatter:
- `outcome: uses-positional-index` — een verwijzing als "Kandidaat 0" binnen dezelfde batch is een tijdelijke, batch-relatieve positie-index, géén stabiele sleutel. Reden: kandidaten krijgen pas een echte database-ID nádat de hele schrijf-/kritiekcyclus is afgerond (`ShadowIterationEngine.kt:397-412`); op het moment dat `dependsOn` geschreven wordt, is de arrayvolgorde de enige beschikbare identiteit.
- `publish-mechanism-supports-symbolic-keys: false` — het publicatiepad (`WorkspacePublisher.kt`, `AutonomousDelivery.kt`, `StoryCandidateApi.kt`) leest `dependsOn` nergens; er is dus ook geen mechanisme dat een vrije symbolische sleutel zou kunnen resolven.
- `batch-key: verify-dependson-datamodel` opgenomen zodat afhankelijke vervolgstories deze bron kunnen herkennen.

Het schema staat vrije tekst toe (geen `pattern`/`enum`/ID-formaat), en geen enkel codepad parseert of resolvet `dependsOn`-waarden naar een concreet kandidaat- of story-ID — dit is met bestand+regel-verwijzingen onderbouwd in het document.

**Getest**
De reviewer heeft alle bestand+regel-verwijzingen onafhankelijk geverifieerd tegen de actuele broncode en bevestigd dat het genoemde grep-resultaat (geen `dependsOn`-treffers buiten `iteration/`) klopt. Het backend-vangnet (`mvn clean verify`) is gedraaid en slaagt ongewijzigd, zoals verwacht aangezien er geen productiecode is aangepast.

**Bewust niet gedaan**
Geen wijziging van schema, prompts, engine- of publicatielogica; geen bouw van een resolutiemechanisme voor `dependsOn`. Dat is expliciet buiten scope en blijft werk voor een eventuele vervolgstory.

<!-- deploy-summary:start -->
Er is onderzocht hoe verwijzingen tussen nieuwe voorstellen binnen één batch precies werken, en die uitkomst is vastgelegd in een document voor later gebruik. Er is niets aan de werking van het systeem veranderd; gebruikers merken dus geen verschil.
<!-- deploy-summary:end -->
