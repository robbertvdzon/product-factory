---
batch-key: verify-dependson-datamodel
outcome: uses-positional-index
publish-mechanism-supports-symbolic-keys: false
---

# `dependsOn`-datamodel in storykandidaten

Deze pagina legt vast hoe het `dependsOn`-veld van storykandidaten in de shadow-iteratiecyclus
op het moment van inspectie daadwerkelijk werkte: is een verwijzing zoals "Kandidaat 0" een
stabiele sleutel of een tijdelijk, batch-relatief volgnummer? De inspectie zelf was uitsluitend een
leesactie; er is toen geen productiecode gewijzigd. Latere stories die hierop voortbouwen kunnen
deze pagina herkennen aan de batchsleutel `verify-dependson-datamodel` (zie YAML-frontmatter
hierboven).

> **Update (product-factory-8):** de hieronder beschreven `outcome: uses-positional-index` is
> inmiddels opgelost. Elk storykandidaat krijgt sindsdien bij aanmaak een verplichte, stabiele
> `candidateKey` (kebab-case-slug, uniek binnen de batch; schema in `ShadowSchemas.kt`, validatie
> in `ShadowIterationEngine.validateStories`), en `dependsOn`-verwijzingen binnen dezelfde batch
> worden via die sleutel gekoppeld (`resolveCandidateDependencies` in `ShadowDossierRenderer.kt`)
> in plaats van via de arrayvolgorde. Het oude, batch-relatieve patroon `"Kandidaat <n>"` wordt nu
> actief afgewezen. De sectie "Publicatiepad: leest `dependsOn` nergens" hieronder blijft wél
> onverkort van toepassing: `WorkspacePublisher.kt`, `AutonomousDelivery.kt` en
> `StoryCandidateApi.kt` lezen `dependsOn` nog steeds nergens — `publish-mechanism-supports-symbolic-keys`
> stond op `false` en is niet alsnog gebouwd, dat viel expliciet buiten de scope van
> product-factory-8. De rest van dit document beschrijft, ongewijzigd, de situatie zoals die vóór
> product-factory-8 was en dient als historische onderbouwing van dat besluit.

## Uitkomst

`outcome: uses-positional-index` — binnen één storybatch is een `dependsOn`-verwijzing naar een
andere kandidaat uit dezelfde batch onvermijdelijk een batch-relatieve positie-index, geen stabiele
sleutel. Geen enkel codepad kent op het moment dat `dependsOn` geschreven wordt al een stabiele
identifier toe aan de overige kandidaten in dezelfde batch.

## Onderbouwing

### 1. Schema: `dependsOn` is een vrij-tekstveld zonder ID-formaat

`ShadowSchemas.kt:39` (schema `stories`) definieert `dependsOn` als:

```json
"dependsOn": { "type": "array", "maxItems": 5, "items": { "type": "string", "maxLength": 500 } }
```

Dit is een kale array van vrije strings (max. 500 tekens per item). Er is geen `pattern`, `enum`,
`$ref` of ander mechanisme dat een waarde dwingt tot een specifiek ID-formaat (numeriek,
UUID, of anderszins). Het schema staat zowel "Kandidaat 0" als een vrije zin als
"hangt af van de betaalintegratie" toe: het is puur beschrijvende tekst voor de mensen/agents die
het dossier lezen, geen gestructureerde referentie.

### 2. Geen enkel codepad parseert, valideert of resolvet `dependsOn`

In `ShadowIterationEngine.kt` komt `dependsOn` maar op twee plekken voor:

- Regel 343 (`applyAutonomyPolicy`): `dependsOn`-teksten worden samengevoegd met
  `acceptanceCriteria` en met een regex (`OWNER_ACTION_PATTERN`) gescand op taal die op
  handmatige/menselijke uitvoering wijst. Dit is een puur tekstuele scan, geen ID-resolutie.
- Regel 390 (`reviewedCandidates`): `dependsOn` wordt via `textList(...)` ongewijzigd doorgegeven
  aan `ReviewedCandidate.dependsOn` (`ShadowDossierRenderer.kt:20`) voor persistentie/rendering.

Nergens wordt een `dependsOn`-waarde vergeleken met, opgezocht via, of omgezet naar een
`candidateIndex` (het aparte, wél echt 0-gebaseerde positieveld uit het `critic`-schema,
`ShadowSchemas.kt:49`) of naar een database-`id`. Er bestaat dus geen resolutiestap die van een
tekst als "Kandidaat 0" een concrete kandidaat- of story-referentie maakt.

### 3. Waarom een same-batch verwijzing toch alleen positioneel kán zijn

De `STORY_WRITER`-rol genereert alle kandidaten van een batch in één enkele modelaanroep
(`storyPrompt`, `ShadowIterationEngine.kt:500-524`) en levert ze als JSON-array `candidates[]`
terug. Op dat moment heeft nog geen enkele kandidaat uit diezelfde batch een stabiele database-ID:
die ID (`story_candidate.id`) wordt pas toegekend in `persistValidatedResults` →
`repository.saveCandidate` (`ShadowIterationEngine.kt:130`, `397-412`), dus ná afloop van de hele
schrijf-/kritiekcyclus, en op basis van de arrayvolgorde
(`stories.path("candidates").mapIndexed { index, candidate -> ... }`, regel 383).

Met andere woorden: wanneer de `STORY_WRITER` `dependsOn` schrijft, is de enige identiteit die een
kandidaat op dat moment al heeft zijn positie in de `candidates[]`-array — exact dezelfde
batch-relatieve, 0-gebaseerde index die de `CRITIC`-rol apart en expliciet als `candidateIndex`
gebruikt (`ShadowSchemas.kt:49`, geldig bereik 0-2, per batch opnieuw vanaf 0). Een verwijzing als
"Kandidaat 0" kan dus feitelijk alleen die tijdelijke positie aanduiden; een stabiele sleutel voor
een sibling-kandidaat bestaat simpelweg nog niet op het moment van schrijven.

Kandidaten uit eerdere, al afgeronde iteraties hebben wél een stabiele identiteit beschikbaar:
`existingCandidateContext` (`ShadowIterationApi.kt:226-230`) geeft de `STORY_WRITER`/`CRITIC` een
lijst van eerdere kandidaten als `"<id> | <title> | <description> | <status>"`, met de echte
database-`id`. Maar dat is context over ándere, al opgeslagen batches — geen mechanisme dat een
`dependsOn`-verwijzing bínnen de huidige batch aan een stabiele sleutel koppelt.

### 4. Rendering: ook het dossier laat `dependsOn` ongewijzigd

`ShadowDossierRenderer.kt:129` rendert `dependsOn` simpelweg als samengevoegde tekst:

```kotlin
if (candidate.dependsOn.isNotEmpty()) appendLine("\nAfhankelijkheden: ${candidate.dependsOn.joinToString()}")
```

Er vindt geen opzoeking of omzetting plaats naar een kandidaatnaam, ID of link; wat het model
schreef verschijnt letterlijk in het dossier.

## Publicatiepad: leest `dependsOn` nergens

`publish-mechanism-supports-symbolic-keys: false` — het publicatiepad heeft geen mogelijkheid om
een vrije symbolische sleutel in `dependsOn` te resolven, simpelweg omdat het `dependsOn` nooit
leest:

- `WorkspacePublisher.kt`: geen enkele treffer voor `dependsOn`.
- `AutonomousDelivery.kt`: geen enkele treffer voor `dependsOn`.
- `StoryCandidateApi.kt`: geen enkele treffer voor `dependsOn`.

`ShadowIterationEngine.kt` geeft aan `workspace.publish(...)` (regel 146-153) alleen het
gerenderde dossier (platte tekst, inclusief de ongewijzigde `dependsOn`-tekst) door; er wordt geen
apart `dependsOn`-veld of relatiestructuur meegestuurd naar het publicatiemechanisme. Een
publicatiepad dat vrije sleutels zou kunnen resolven zou daarvoor `dependsOn` gestructureerd
moeten inlezen en tegen een kandidaat- of story-register moeten opzoeken; dat bestaat op dit moment
niet.

## Conclusie voor afhankelijke stories

Een `dependsOn`-waarde als "Kandidaat 0" binnen één batch is op dit moment **niet** stabiel: zodra
de batch is verwerkt en kandidaten zijn opgeslagen (met hun eigen, wél stabiele `id`), verwijst
"Kandidaat 0" nergens meer naar terug — de tekst blijft alleen als leesbare aantekening in het
dossier en de database staan. Stories die op `dependsOn` willen voortbouwen (bijv. om
afhankelijkheden daadwerkelijk te resolven of te valideren) moeten rekening houden met beide
constateringen hierboven: (a) het schema staat vrije tekst toe, geen ID-formaat, en (b) er is geen
bestaand resolutiemechanisme — dat zou nieuw gebouwd moeten worden, zowel voor same-batch
(positionele) als voor cross-batch (stabiele ID) verwijzingen.
