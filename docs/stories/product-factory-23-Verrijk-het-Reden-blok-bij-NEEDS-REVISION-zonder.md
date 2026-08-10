# product-factory-23 - Verrijk het Reden-blok bij NEEDS_REVISION zonder criticusoordeel met de daadwerkelijke stopoorzaak i.p.v. alleen 'ontbreekt'

## Story

Verrijk het Reden-blok bij NEEDS_REVISION zonder criticusoordeel met de daadwerkelijke stopoorzaak i.p.v. alleen 'ontbreekt'

<!-- refined-by-factory -->

## Scope
Uitsluitend voor het detaildialoog `IterationSessionDialog` in `dashboard-frontend` (main.dart, Reden-blok, huidige fallback rond regel 1082-1101) wordt het gedrag voor precies één deelgeval aangepast: status `NEEDS_REVISION` zonder `criticVerdict` én zonder criticus-artefact in `artifacts`.

- In dat geval bepaalt de code, uit de reeds beschikbare sessiedata (`steps`: role/status/attempt/startedAt/completedAt/errorMessage, en `artifacts`: contentJson per rol), welke agentrol als laatste `COMPLETED` is.
  - Is er zo'n rol, dan toont het Reden-blok een tekst met de naam van die rol (via de bestaande `_roleLabel`-mapping) en een leesbare samenvatting van het resultaat van die rol, afgeleid uit het bijbehorende artefact in `artifacts`. Voor rollen met een `summary`-veld in hun schema (researcher, critic, summary) wordt dat veld gebruikt; voor rollen zonder `summary`-veld (product_owner, ux_designer, story_writer) wordt een leesbare samenvatting samengesteld uit de bestaande weergavelogica voor die rol (`_readableArtifactFields`/`humanizeFieldKey`), zonder rauwe JSON te tonen.
  - Is er geen enkele rol `COMPLETED`, dan toont het blok een aparte, expliciete fallbacktekst die dat meldt (niet leeg, niet 'undefined', en niet gelijk aan de bestaande 'Criticus-oordeel ontbreekt'-tekst).
- De implementerende agent onderzoekt of het datamodel (steps/artifacts, inclusief `errorMessage` en timing) betrouwbaar onderscheid kan maken tussen een bewuste pipeline-stop en een timeout/technische fout, en legt die uitkomst vast (bijv. als code-commentaar of in een agent-tip). Alleen als dat onderscheid met zekerheid uit de data volgt, wordt het expliciet benoemd in de getoonde tekst; kan het niet betrouwbaar worden afgeleid, dan blijft de tekst beperkt tot rolnaam + resultaatsamenvatting, zonder gegokte oorzaak.
- Alle overige combinaties blijven exact zoals nu:
  - `NEEDS_REVISION`/`REJECTED` mét criticus-artefact: bestaande `criticReasonSummary`-tekst, ongewijzigd.
  - `REJECTED` zonder criticus-artefact: bestaande fallbacktekst 'Criticus-oordeel ontbreekt voor deze cyclus', ongewijzigd (dit AC geldt alleen voor `NEEDS_REVISION`).
  - `REJECTED` met `criticVerdict == 'ACCEPT'` (guardrail-pad): bestaande guardrail-toelichtingszin, ongewijzigd.
  - `ACCEPTED`, `FAILED`, `PENDING`, `QUEUED`, `RUNNING`: Reden-blok blijft afwezig/ongewijzigd zoals nu.
- Geen nieuw API-veld, geen backend-wijziging, geen wijziging aan HKH Autopilot. `docs/factory/functional-spec.md` (rond regel 70-87, die de huidige fallbacktekst normatief documenteert) wordt bijgewerkt zodat het nieuwe gedrag voor dit deelgeval correct beschreven staat.

## Acceptance criteria
- Voor een cyclus met status `NEEDS_REVISION`, zonder `criticVerdict` en zonder criticus-artefact, waarbij minstens één rol `COMPLETED` is: het Reden-blok toont de naam van de laatst voltooide agentrol en een leesbare samenvatting van diens resultaat (geen rauwe JSON), in plaats van uitsluitend 'Criticus-oordeel ontbreekt voor deze cyclus'.
- Kan uit de bestaande data (steps/artifacts) betrouwbaar worden afgeleid of de cyclus bewust stopte of door een timeout/technische fout, dan wordt dat onderscheid expliciet benoemd in de tekst; kan dat niet betrouwbaar, dan blijft de tekst beperkt tot rolnaam + resultaatsamenvatting. Deze afweging is gedocumenteerd (code-commentaar of agent-tip).
- Voor dezelfde statuscombinatie, maar met géén enkele rol `COMPLETED`: het Reden-blok toont een aparte, expliciete fallbacktekst die dat meldt (niet leeg, niet 'undefined', niet gelijk aan de bestaande generieke fallbacktekst).
- Een geautomatiseerde widget-test (in `dashboard-frontend/test/iteration_session_reason_block_test.dart` of een nieuw testbestand) verifieert voor het scenario 'laatste rol Onderzoeker (`researcher`) COMPLETED, criticus niet gestart, geen criticus-artefact' dat de gerenderde Reden-tekst niet meer gelijk is aan 'Criticus-oordeel ontbreekt voor deze cyclus' en wél de rolnaam (via `_roleLabel`) en een niet-lege resultaatsamenvatting bevat.
- Een geautomatiseerde regressietest bevestigt dat de Reden-tekst voor de volgende bestaande scenario's exact ongewijzigd blijft: `ACCEPTED` (geen Reden-blok), `REJECTED` zonder criticus-artefact (bestaande fallbacktekst), `REJECTED`/`NEEDS_REVISION` mét criticus-artefact (bestaande `criticReasonSummary`-tekst), en `REJECTED` met `criticVerdict == 'ACCEPT'` (bestaande guardrail-toelichtingszin).
- De wijziging blijft volledig beperkt tot de presentatielaag van `IterationSessionDialog` in `dashboard-frontend`; er wordt geen nieuw API-veld, database-schemaveld of wijziging aan HKH Autopilot geïntroduceerd.
- `docs/factory/functional-spec.md` beschrijft het nieuwe gedrag voor dit deelgeval correct, naast de bestaande beschrijving van het Reden-blok.

## Aannames
- De sessiedata die het dialoog al ophaalt (`steps` met role/status/attempt/startedAt/completedAt/errorMessage, en `artifacts` met contentJson per rol) is voldoende om de laatst voltooide rol en diens resultaat te bepalen; er is geen aanvullende backend-call nodig.
- 'Resultaatsamenvatting' betekent: voor rollen met een `summary`-veld in hun contentJson-schema (researcher, critic, summary) dat veld; voor rollen zonder `summary`-veld (product_owner, ux_designer, story_writer) een leesbare samenvatting opgebouwd uit de bestaande veld-weergavelogica, zonder rauwe JSON-notatie te tonen.
- Het scenario NEEDS_REVISION-zonder-criticVerdict-en-zonder-criticus-artefact komt in de praktijk voor (bevestigd via live-onderzoek op cyclus `shadow-hkh-autopilot-0003`) en hoeft niet verder herleid te worden naar een specifieke oorzaak in de backend-code om deze story te kunnen implementeren.
- Als betrouwbaar datamodel-onderzoek uitwijst dat bewuste stop en timeout/technische fout niet te onderscheiden zijn, is een tekst met alleen rolnaam + resultaatsamenvatting (zonder oorzaak-claim) voldoende om aan de acceptatiecriteria te voldoen.

## Eindsamenvatting

Genoeg context. Ik schrijf de eindsamenvatting.

## Eindsamenvatting — product-134

**Gebouwd:** In `dashboard-frontend/lib/main.dart` is het Reden-blok van `IterationSessionDialog` uitgebreid voor het specifieke geval `NEEDS_REVISION` zonder `criticVerdict` én zonder criticus-artefact. In plaats van uitsluitend de generieke tekst "Criticus-oordeel ontbreekt voor deze cyclus" toont het blok nu de naam van de laatst voltooide agentrol plus een leesbare samenvatting van diens resultaat (via nieuwe pure helpers `latestArtifactForRole`, `lastCompletedStep`, `roleResultSummaryText`, `missingCriticReasonText`). Voor rollen met een `summary`-veld (researcher, critic, summary) wordt dat veld gebruikt; voor rollen zonder (product_owner, ux_designer, story_writer) wordt een leesbare samenvatting opgebouwd uit bestaande veld-weergavelogica, zonder rauwe JSON. Is geen enkele rol `COMPLETED`, dan verschijnt een aparte, expliciete fallbacktekst. Alle overige bestaande combinaties (REJECTED zonder artefact, NEEDS_REVISION/REJECTED mét artefact, guardrail-tekst, ACCEPTED/FAILED/etc.) blijven ongewijzigd.

**Belangrijke keuze:** Onderzocht is of `steps`/`artifacts` (inclusief `errorMessage`/timing) betrouwbaar onderscheid geven tussen een bewuste pipeline-stop en een timeout/technische fout. Conclusie: dat kan niet betrouwbaar — een rol die nooit start levert geen step-record op, en `errorMessage` staat alleen op stappen die zelf faalden (wat normaliter tot `FAILED` leidt, niet tot dit scenario). Daarom toont de tekst uitsluitend rolnaam + resultaatsamenvatting, zonder gegokte oorzaak. Dit is vastgelegd als codecommentaar bij `missingCriticReasonText`.

**Getest:** Bestaande widget-test aangepast (het oude scenario met `steps: []` valt nu onder de nieuwe "geen rol voltooid"-tak); nieuwe tests toegevoegd voor: laatste rol Onderzoeker COMPLETED met samenvatting, geen enkele rol COMPLETED, een rol zonder summary-veld (product_owner, geen rauwe JSON), en een regressietest voor NEEDS_REVISION mét criticVerdict maar zonder artefact. Unit-tests voor alle vier nieuwe helperfuncties. Vangnet groen: `flutter analyze` (0 issues), `flutter test` (153 tests, 0 failures), `mvn clean verify` (BUILD SUCCESS).

**Documentatie:** `docs/factory/functional-spec.md` bijgewerkt met de nieuwe sub-casus en het onderzoeksresultaat over bewuste stop vs. timeout (verdere documentatie-afronding volgt in de aparte documentatie-subtaak product-135).

**Bewust niet gedaan:** Geen backend- of API-wijziging; wijziging blijft volledig in de presentatielaag. Geen wijziging aan `.factory/verification.yaml` (bestaande dekking volstaat).

<!-- deploy-summary:start -->
Bij een teruggekoppelde cyclus zonder duidelijk criticus-oordeel toont het dashboard voortaan wél welke stap als laatste is afgerond en wat daar het resultaat van was, in plaats van alleen "ontbreekt". Zo zie je sneller waar de cyclus is blijven steken. Er verandert verder niets aan hoe cycli zelf werken.
<!-- deploy-summary:end -->
