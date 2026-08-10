# product-factory-20 - Voeg expliciete assertie op criticVerdict toe aan het bestaande DUPLICATE-testscenario in ShadowIterationEngineTest.kt

## Story

Voeg expliciete assertie op criticVerdict toe aan het bestaande DUPLICATE-testscenario in ShadowIterationEngineTest.kt

<!-- refined-by-factory -->

## Scope
Geen implementatiewerk nodig. De gevraagde wijziging — een expliciete assertie dat `criticVerdict` gelijk is aan `'ACCEPT'` in het DUPLICATE-scenario van `ShadowIterationEngineTest.kt` — is al aanwezig op regel 83, toegevoegd door product-factory-19 (commit ff8d109, PR #53), samen met de bijbehorende fix in `ShadowIterationEngine.kt` (regel 187: `repository.markReviewed(iteration.id, verdict, "REJECTED")`, gebruikt het echte `verdict` in plaats van een hardgecodeerde waarde).

## Acceptance criteria
- `ShadowIterationEngineTest.kt` bevat op regel 83 `assertEquals("ACCEPT", repository.require("hkh-autopilot", duplicate.id).criticVerdict)` — geverifieerd aanwezig, geen wijziging nodig.
- De bestaande assertie op regel 82 dat de iteratiestatus `'REJECTED'` wordt voor dit scenario staat er ongewijzigd naast — geverifieerd aanwezig.
- Geen enkel bestand wordt gewijzigd; deze story wordt gesloten als duplicaat van product-factory-19 (PR #53).

## Aannames
- Er is geen nieuw, nog niet benoemd aspect (bijv. een derde verdict-waarde, ander testbestand, of edge case) bedoeld met deze story — de storybeschrijving verwijst één-op-één naar wat al gerealiseerd is.
- Mocht bij verdere afhandeling blijken dat de asserties op regel 82/83 zijn gewijzigd of verwijderd sinds commit ff8d109, dan is dat regressie op de bestaande fix en een aparte, kleine hersteltaak — niet een heropening van deze story met nieuwe scope.

## Eindsamenvatting

Nu heb ik voldoende context om de eindsamenvatting te schrijven.

## Eindsamenvatting — product-factory-20

**Story:** Voeg expliciete assertie op `criticVerdict` toe aan het DUPLICATE-testscenario in `ShadowIterationEngineTest.kt`.

**Uitkomst:** geen implementatiewerk nodig. Bij onderzoek bleek de gevraagde assertie al aanwezig te zijn, toegevoegd in een eerdere story (product-factory-19, commit ff8d109, PR #53) samen met de bijbehorende fix in `ShadowIterationEngine.kt` (gebruik van het echte `verdict` in plaats van een hardgecodeerde waarde).

**Verificatie (door tester, product-115):**
- Code-inspectie bevestigt dat regels 82-83 van `ShadowIterationEngineTest.kt` ongewijzigd de verwachte asserties bevatten: iteratiestatus `REJECTED` én `criticVerdict == "ACCEPT"` voor het DUPLICATE-scenario.
- `git log` op het testbestand toont geen wijzigingen sinds ff8d109.
- Volledige multi-module build (`mvn clean verify` vanaf de repo-root) als extra controle gedraaid: BUILD SUCCESS, 0 failures/errors; `ShadowIterationEngineTest` 9/9 groen.
- Geen bestanden gewijzigd op deze branch (buiten de worklog); dashboard-frontend niet van toepassing, geen Flutter-checks nodig.

**Bewust niet gedaan:** geen codewijzigingen, geen nieuwe tests toegevoegd — de story is een bevestiging van al gerealiseerde functionaliteit, geen nieuwe scope. Deze run wordt gesloten als duplicaat van product-factory-19.

<!-- deploy-summary:start -->
Deze wijziging betrof alleen een controle: er is gecheckt of een eerder toegevoegde test nog steeds correct werkt. Er is niets aangepast, omdat alles al goed stond sinds een eerdere update.
<!-- deploy-summary:end -->
