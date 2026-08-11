# product-factory-25 - Herstel tegenspraak in Reden-blok: toon 'criticusoordeel geregistreerd zonder artefact' i.p.v. 'oordeel ontbreekt' bij NEEDS_REVISION/REJECTED

## Story

Herstel tegenspraak in Reden-blok: toon 'criticusoordeel geregistreerd zonder artefact' i.p.v. 'oordeel ontbreekt' bij NEEDS_REVISION/REJECTED

<!-- refined-by-factory -->

## Scope
In `IterationSessionDialog` (`dashboard-frontend/lib/main.dart`, Reden-blok rond regel 1071-1101) wordt de detectie van "geen onderliggend criticus-artefact/stap" losgekoppeld van de bestaande `criticVerdict == null`-voorwaarde.

Huidige situatie: `missingCriticContext` is alleen `true` als `status == 'NEEDS_REVISION' && criticVerdict == null && criticArtifact == null`. Is `criticVerdict` wél gezet maar ontbreekt het criticus-artefact (`latestCriticArtifact(artifacts) == null`), dan is `reasonText` leeg en valt de UI terug op de onvoorwaardelijke tekst `'Criticus-oordeel ontbreekt voor deze cyclus'` — terwijl het cyclusoverzicht wél een criticus-badge toont zodra `criticVerdict != null`. Dat is de te herstellen tegenspraak.

Nieuwe situatie:
- Is `criticArtifact` aanwezig: ongewijzigd gedrag (`criticReasonSummary` op het artefact, candidate 41).
- Is `criticArtifact` afwezig én `criticVerdict == null`: ongewijzigd gedrag (bestaande `missingCriticReasonText`-pad op basis van laatst voltooide rol, candidate 47/product-132).
- Is `criticArtifact` afwezig én `criticVerdict` wél aanwezig (nieuw scenario): toon een tekst die de daadwerkelijke verdict-waarde expliciet benoemt, bijv. `'Criticusoordeel $criticVerdict geregistreerd, maar geen onderliggend criticus-artefact beschikbaar.'`, in plaats van de generieke 'ontbreekt'-tekst.
- De bestaande guardrail-toelichting (extra alinea bij `status == 'REJECTED' && criticVerdict == 'ACCEPT'`) blijft ongewijzigd van toepassing, ook op dit nieuwe scenario.

Detectie is uitsluitend gebaseerd op reeds opgehaalde `/artifacts`- en `/steps`-data (`latestCriticArtifact`/`latestArtifactForRole`, `steps`); geen nieuw API-veld, geen wijziging aan `ShadowIterationEngine.kt` of `PreviewDataSeeder.kt`. De wijziging is beperkt tot de presentatielaag in `dashboard-frontend/lib/main.dart` (en het bijbehorende Reden-blok in `docs/factory/functional-spec.md`, dat het huidige — te herstellen — gedrag rond regel 76-108 normatief documenteert en moet meebewegen).

## Acceptance criteria
- Given een cyclus met `criticVerdict` gezet (bijv. `'REVISE'`), status `NEEDS_REVISION` of `REJECTED`, geen criticus-artefact en geen `COMPLETED` CRITIC-stap, when het detaildialoog wordt geopend, then bevat het Reden-blok de verdict-waarde (bijv. `'REVISE'`) samen met een expliciete melding dat er geen onderliggend criticus-artefact beschikbaar is, en wordt niet meer de tekst `'Criticus-oordeel ontbreekt voor deze cyclus'` getoond.
- Given een cyclus met `criticVerdict == null` (geen criticusoordeel gezet) en geen criticus-artefact, when het detaildialoog wordt geopend, then blijft het bestaande gedrag ongewijzigd: bij status `NEEDS_REVISION` de bestaande rol-gebaseerde samenvattingstekst (`missingCriticReasonText`), en voor overige combinaties de bestaande fallbacktekst `'Criticus-oordeel ontbreekt voor deze cyclus'` — geen regressie.
- Given een cyclus met `criticVerdict` aanwezig én een aanwezig criticus-artefact, when het detaildialoog wordt geopend, then toont het Reden-blok ongewijzigd de bestaande artefact-afgeleide onderbouwing (`criticReasonSummary`), niet de nieuwe 'geen artefact'-tekst.
- Given de guardrail-deelcasus (`status == 'REJECTED' && criticVerdict == 'ACCEPT'`), when deze samenvalt met het nieuwe 'geen artefact'-scenario, then blijft de bestaande guardrail-toelichtingsalinea ongewijzigd toegevoegd aan de nieuwe verdict-tekst.
- Een geautomatiseerde widget-/integratietest dekt expliciet het scenario 'criticVerdict aanwezig, critic-artefact/stap afwezig' en faalt zodra de oude onvoorwaardelijke fallbacktekst opnieuw voor dit scenario wordt getoond.
- Bestaande, reeds gepubliceerde tests voor het null-criticVerdict-scenario en het artefact-aanwezig-scenario (o.a. `dashboard-frontend/test/iteration_session_reason_block_test.dart`) blijven ongewijzigd slagen.
- De wijziging introduceert geen nieuw API-veld, geen backend-wijziging en geen wijziging aan `ShadowIterationEngine.kt` of `PreviewDataSeeder.kt`; alle functionele aanpassingen zijn beperkt tot `IterationSessionDialog` in `dashboard-frontend/lib/main.dart`.
- `docs/factory/functional-spec.md` (Reden-blok-beschrijving, rond regel 76-108) wordt bijgewerkt zodat de tekst het nieuwe onderscheid tussen de drie deelcasussen (`criticVerdict == null` zonder artefact / `criticVerdict` aanwezig zonder artefact / artefact aanwezig) correct en volledig documenteert.

## Aannames
- De exacte bewoording van de nieuwe verdict-tekst mag door de developer worden ingevuld zolang deze de letterlijke verdict-waarde bevat én expliciet benoemt dat er geen onderliggend criticus-artefact beschikbaar is (de acceptatiecriteria schrijven geen exacte string voor).
- Het scenario is reproduceerbaar doordat `PreviewDataSeeder.kt` al een cyclus seedt met `criticVerdict` gezet via `markReviewed` zonder voorafgaande CRITIC-stap/artefact (zie `needsRevision`-seed); een losstaande widget-test met gemockte sessiedata is voldoende om het scenario te dekken en vereist geen wijziging van de seeder zelf.
- De testhelper `_sessionWith(...)` in `iteration_session_reason_block_test.dart` ondersteunt al een `criticVerdict`-parameter en kan direct hergebruikt worden voor de nieuwe testcase.

## Eindsamenvatting

Ik heb voldoende context uit het worklog. Ik geef nu de eindsamenvatting volgens het contract.

## Eindsamenvatting

**Wat is gebouwd:** In `IterationSessionDialog` (`dashboard-frontend/lib/main.dart`) is een derde detectiepad toegevoegd aan het Reden-blok, naast de twee bestaande. Wanneer er geen criticus-artefact aanwezig is, maar `criticVerdict` wél gezet is, toont het blok voortaan een nieuwe tekst (via de helper `criticVerdictWithoutArtifactText(criticVerdict)`): *"Criticusoordeel $criticVerdict geregistreerd, maar geen onderliggend criticus-artefact beschikbaar."* — in plaats van de misleidende onvoorwaardelijke tekst "Criticus-oordeel ontbreekt voor deze cyclus".

**Keuzes:**
- De twee bestaande paden (artefact aanwezig → `criticReasonSummary`; `criticVerdict == null` zonder artefact → `missingCriticReasonText`/fallback) zijn ongewijzigd gelaten, zoals vereist.
- De guardrail-alinea bij `REJECTED` + `criticVerdict == 'ACCEPT'` blijft van toepassing en combineert nu ook met de nieuwe verdict-tekst.
- Exacte bewoording was vrij te kiezen (conform aannames in de story), zolang de verdict-waarde letterlijk benoemd wordt.
- Geen nieuw API-veld, geen wijziging aan `ShadowIterationEngine.kt` of `PreviewDataSeeder.kt`.
- `docs/factory/functional-spec.md` (Reden-blok-alinea) is bijgewerkt zodat de drie deelcasussen correct gedocumenteerd zijn.

**Getest:**
- Bestaande test in `iteration_session_reason_block_test.dart` die het oude bug-gedrag verifieerde, aangepast naar het nieuwe verwachte gedrag; drie nieuwe widgettests toegevoegd (NEEDS_REVISION met verdict zonder artefact, REJECTED met verdict zonder artefact/steps, samenloop met guardrail-alinea bij `criticVerdict == 'ACCEPT'`).
- Vangnet: `flutter analyze` (0 issues), `flutter test` (158/158 groen), `mvn -B --no-transfer-progress clean verify` (backend, ongewijzigd maar volledig meegedraaid, 16/16 groen).
- Preview-smoketest (PR-59): frontend en backend health endpoints beide 200. Geen interactieve browserverificatie mogelijk in de agentcontainer; gedragsdekking leunt op de widgettests.

**Bewust niet gedaan:** Geen wijzigingen aan backend, API of seeder-logica; geen aanpassing aan `.factory/verification.yaml` (niet nodig, bestaande pathPrefixes dekken de wijziging).

<!-- deploy-summary:start -->
In het cyclusoverzicht van het dashboard stond soms een verwarrende melding dat een oordeel van de kwaliteitscontrole "ontbreekt", terwijl er wél een oordeel was geregistreerd — alleen het bijbehorende bewijsstuk ontbrak. Deze melding is nu verduidelijkt: het dashboard toont voortaan het daadwerkelijke oordeel, met de uitleg dat het onderliggende bewijsstuk niet beschikbaar is. Er is verder niets aan de werking van het systeem veranderd.
<!-- deploy-summary:end -->
