# product-factory-6 - Voeg eigen, statische documentatie toe die het status/conclusion-onderscheid van Product Factory expliciet en zelfstandig uitlegt, conditioneel op de bevestigde vergrendelingsuitkomst

## Story

Voeg eigen, statische documentatie toe die het status/conclusion-onderscheid van Product Factory expliciet en zelfstandig uitlegt, conditioneel op de bevestigde vergrendelingsuitkomst

<!-- refined-by-factory -->

## Samenvatting
Het dashboard van Product Factory laat bij elke productcyclus een 'status' en een badge zien, maar nergens staat uitgelegd wat die twee precies betekenen en hoe ze zich tot elkaar verhouden. Deze story voegt daarom één vast, statisch stukje documentatie toe dat dat onderscheid uitlegt: wanneer een cyclus nog loopt versus wanneer die klaar is, en dat een eindoordeel pas telt zodra een cyclus echt is afgerond. Ook wordt vermeld dat een cyclus die halverwege wordt afgebroken vanzelf, zonder dat iemand daar apart over hoeft te beslissen, als "niet te classificeren" wordt behandeld. Omdat er inmiddels al een technische beveiliging is gebouwd die voorkomt dat een eindoordeel achteraf per ongeluk wordt overschreven, mag de documentatie dat ook gewoon zo stellen. Er verandert verder niets aan de werking van de applicatie.

## Scope
- Voeg exact één nieuw, statisch documentatie-artefact toe (nieuw bestand onder `docs/factory/` of `docs/`, of een vast tekstblok in `docs/factory/functional-spec.md`) dat het status/conclusion-onderscheid van Product Factory's iteratieoverzicht uitlegt.
- Basis voor de inhoud is uitsluitend wat daadwerkelijk in de code bevestigd is (kandidaten 22-24, reeds gemerged):
  - `ShadowIterationView.status` (`productfactory-contracts/.../Contracts.kt`) kent de waarden QUEUED, RUNNING, ACCEPTED, NEEDS_REVISION, REJECTED, FAILED. QUEUED/RUNNING = lopend; de overige vier = afgerond.
  - Er bestaat geen apart `conclusion`-veld. De "conclusie" van een afgeronde iteratie wordt gevormd door de bestaande velden `status`, `criticVerdict` en `errorMessage` samen, vertaald naar één van vijf vaste badges (`onderzoek-onvoldoende`, `guardrail-conflict`, `richting-gekozen`, `richting-verworpen`, `niet-classificeerbaar`) via `dashboard-frontend/lib/classification.dart`.
  - Er bestaat geen apart CANCELLED-statusveld: een tijdens uitvoering afgebroken iteratie krijgt geen apart menselijk besluitmoment, maar valt automatisch via de bestaande fallback-logica in `classifyIterationOutcome` op de badge `niet-classificeerbaar`, zodra de ruwe status niet QUEUED/RUNNING is en niet voorkomt in `kBekendeStatuswaardenPerCategorie`.
- Kandidaat 0 (write-once-guard, `product-factory-5`) is al uitgevoerd en gemerged; de vastgestelde, machineleesbare uitkomst is **`guard-added`**: `markAccepted`/`markReviewed`/`markFailed` in `ShadowIterationApi.kt` weigeren sindsdien een tweede schrijfpoging op `status`/`critic_verdict` zodra een iteratie al in een terminale staat staat (`... where id = ? and status not in (TERMINAL_STATUSES)`), en loggen dit als `log.warn` met iteratie-id. Omdat de uitkomst `guard-added` is, moet de documentatie de immutabiliteitszin **onvoorwaardelijk** opnemen: het eindoordeel wijzigt na vaststelling niet meer.
- Documenteer expliciet elke afwijking tussen het hier beschreven, bevestigde model en een eventueel afwijkend "aspirational" onderzoeksmodel (bijv.: er bestaat geen apart `conclusion`-veld, ondanks dat de term in de story/onderzoeksdocumenten wel zo gebruikt wordt — de documentatie moet dit expliciet benoemen, niet verdoezelen).
- Puur additief: geen nieuwe route, geen wijziging aan databaseschema, authenticatie of PR-goedkeuringsflow, geen wijziging aan bestaande code-paden (behalve het toevoegen van het documentatie-artefact zelf).
- Voeg een geautomatiseerde check toe (bijv. een eenvoudige test of scriptstap) die verifieert dat het documentatie-artefact bestaat en de vereiste kernzinnen bevat: de status-definitie (alleen 'lopend' of 'voltooid', conclusion pas geldig bij voltooid), de zin over autonome classificatie van een tijdens uitvoering onderbroken iteratie, en de onvoorwaardelijke immutabiliteitszin (passend bij de vastgestelde `guard-added`-uitkomst).

## Acceptance criteria
- Er is exact één nieuw, statisch documentatie-artefact (bestand of vast tekstblok in een bestaand overzichtsdocument) dat uitlegt wat status en conclusion betekenen binnen Product Factory's iteratieoverzicht.
- De documentatie bevat expliciet de zin dat status alleen 'lopend' of 'voltooid' kan zijn, en dat het eindoordeel (conclusion) pas relevant/geldig is zodra status 'voltooid' is.
- De documentatie vermeldt expliciet dat een tijdens uitvoering onderbroken iteratie automatisch en zonder apart menselijk besluitmoment wordt geclassificeerd (als 'niet-classificeerbaar', via de bestaande fallback-logica).
- De documentatie bevat de onvoorwaardelijke immutabiliteitszin: het eindoordeel van een iteratie wijzigt, na vaststelling, niet meer — dit is toegestaan omdat kandidaat 0 (`product-factory-5`) al is uitgevoerd met de bevestigde uitkomst `guard-added`.
- De documentatie benoemt expliciet dat er geen apart `conclusion`-veld bestaat in het datamodel, en dat status, `criticVerdict` en `errorMessage` samen de conclusie vormen (afwijking t.o.v. een eventueel aspirational model waarin 'conclusion' als apart veld wordt gesuggereerd).
- De vermelde veldnamen en waarden (status-waarden, badge-namen, veldnamen) komen uitsluitend uit wat al bevestigd is in de gemergede kandidaten 22-24 (`dashboard-frontend/lib/classification.dart`, `Contracts.kt`, `functional-spec.md`).
- Een geautomatiseerde check (test of scriptstap) verifieert dat het documentatie-artefact bestaat en de drie vereiste kernzinnen bevat (status-definitie, autonome classificatie van een afgebroken iteratie, de onvoorwaardelijke immutabiliteitszin).
- Geen wijziging aan bestaande routes, databaseschema, authenticatie of PR-goedkeuringsflow.

## Aannames
- Kandidaat 0 uit de oorspronkelijke dependsOn-vermelding verwijst naar `product-factory-5` ("Vergrendel de conclusion-waarde van een iteratie zodra de terminale staat is bereikt"), reeds gemerged (commit d8cd08d) met vastgestelde uitkomst `guard-added`. De implementerende agent hoeft dit dus niet opnieuw te onderzoeken, maar leest ter bevestiging kort de Eindsamenvatting van `product-factory-5` voordat de immutabiliteitszin geschreven wordt.
- "Conclusion" in de documentatie wordt uitgelegd als het geheel van `status` + `criticVerdict` (+ `errorMessage` bij FAILED), vertaald naar één van de vijf vaste badges, niet als een letterlijk apart databaseveld — conform de bevindingen van kandidaat 24 (`product-factory-3`).
- De precieze locatie van het documentatie-artefact (nieuw bestand vs. tekstblok in `functional-spec.md`) wordt aan de implementerende agent overgelaten, zolang het één op zichzelf staand, statisch artefact is dat de vereiste kernzinnen bevat.
- De geautomatiseerde check mag een eenvoudige test zijn (bijv. een bestandsinhoud-assertie) en hoeft geen onderdeel te worden van een bestaande testsuite met zwaardere infrastructuur.

## Eindsamenvatting

Alles is duidelijk. Ik heb voldoende informatie voor de eindsamenvatting.

## Eindsamenvatting product-factory-6

**Wat is gebouwd**
Er is een nieuwe, statische documentatiesectie "Status en conclusion van een productcyclus" toegevoegd aan `docs/factory/functional-spec.md`, direct vóór "Testerafspraken". Deze legt het status/conclusion-onderscheid van een shadow iteration expliciet uit:

- Status kent maar twee toestanden: lopend (QUEUED/RUNNING) of voltooid (ACCEPTED/NEEDS_REVISION/REJECTED/FAILED); het eindoordeel is pas geldig zodra status voltooid is.
- Er bestaat geen apart `conclusion`-veld in het datamodel — de conclusie is een afgeleide van `status` + `criticVerdict` (+ `errorMessage` bij FAILED), vertaald naar één van vijf vaste badges. Dit wijkt expliciet af van een eventueel aspirational onderzoeksmodel dat wél een apart conclusion-veld suggereert.
- Een tijdens uitvoering onderbroken iteratie wordt automatisch en zonder apart menselijk besluitmoment geclassificeerd als `niet-classificeerbaar`, via de bestaande fallback-logica in `classifyIterationOutcome`.
- Onvoorwaardelijke immutabiliteitszin: het eindoordeel wijzigt na vaststelling niet meer — toegestaan omdat de write-once-guard uit `product-factory-5` (`markAccepted`/`markReviewed`/`markFailed` in `ShadowIterationApi.kt`) al gemerged is en bevestigd `guard-added` opleverde.

**Gemaakte keuzes**
- Documentatie toegevoegd als sectie in het bestaande `functional-spec.md` in plaats van een nieuw bestand — één op zichzelf staand, statisch blok dat naadloos aansluit bij de badge-beschrijving erboven.
- Alle veldnamen/waarden herbevestigd tegen de daadwerkelijke code (`Contracts.kt`, `classification.dart`, `ShadowIterationApi.kt`) vóór het schrijven, zodat de documentatie geen aspirational claims bevat.

**Wat is getest**
- Nieuwe test `FunctionalSpecStatusConclusionDocTest.kt` (plain JUnit5) verifieert dat het bestand bestaat en de drie vereiste kernzinnen letterlijk bevat; valt onder de bestaande `repository-maven-verify`-stap, geen wijziging aan `.factory/verification.yaml` nodig.
- Volledig vangnet groen: `mvn clean verify` (alle modules, incl. de nieuwe test: 4/4 geslaagd), `flutter analyze` (geen issues) en `flutter test` (63/63 geslaagd).
- Reviewer en tester hebben de inhoud onafhankelijk tegen de broncode geverifieerd en akkoord bevonden.

**Bewust niet gedaan**
- Geen wijziging aan applicatiecode, routes, databaseschema, authenticatie of PR-goedkeuringsflow — puur additieve documentatie plus één doctest.
- Geen frontend-wijzigingen, dus geen nieuwe UI-verificatie nodig.

<!-- deploy-summary:start -->
Er is nu duidelijk uitgelegd, in de documentatie, wanneer een productcyclus nog bezig is en wanneer die klaar is, en dat een definitief oordeel alleen telt zodra een cyclus echt is afgerond. Ook is vastgelegd dat een cyclus die halverwege stopt automatisch als "niet te beoordelen" wordt gemarkeerd, zonder dat daar een aparte beslissing voor nodig is. Er is verder niets veranderd aan de werking van de applicatie zelf.
<!-- deploy-summary:end -->
