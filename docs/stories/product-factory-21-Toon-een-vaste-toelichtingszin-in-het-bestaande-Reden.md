# product-factory-21 - Toon een vaste toelichtingszin in het bestaande Reden-blok wanneer het criticusoordeel ACCEPT is maar de iteratie toch REJECTED is (guardrail-pad)

## Story

Toon een vaste toelichtingszin in het bestaande Reden-blok wanneer het criticusoordeel ACCEPT is maar de iteratie toch REJECTED is (guardrail-pad)

<!-- refined-by-factory -->

## Scope
Het bestaande 'Reden'-blok in `IterationSessionDialog` (`dashboard-frontend/lib/main.dart`, rond regel 1068-1101) toont bij iteratiestatus `NEEDS_REVISION`/`REJECTED` leesbare tekst afgeleid van het criticus-artefact. Sinds de guardrail-verdict-fix (`ShadowIterationEngine.kt`, reeds gemerged) kan `criticVerdict == 'ACCEPT'` legitiem samengaan met iteratiestatus `REJECTED`: dit gebeurt wanneer alle door de criticus goedgekeurde kandidaten alsnog geblokkeerd worden (duplicaat of guardrail) en er dus niets overblijft om te publiceren.

Deze story voegt aan het bestaande Reden-blok een voorwaardelijke, statische toelichtingszin toe die uitsluitend verschijnt wanneer:
- de iteratiestatus `REJECTED` is, EN
- `iteration['criticVerdict'] == 'ACCEPT'`.

De toelichtingszin luidt exact:
"Let op: Alle voorgestelde kandidaten zijn geblokkeerd (duplicaat of guardrail), waardoor deze cyclus niet doorgaat ondanks een positief criticusoordeel."

Voor elke andere combinatie van status en `criticVerdict` (inclusief alle overige `REJECTED`- en `NEEDS_REVISION`-gevallen waarbij `criticVerdict != 'ACCEPT'`) blijft het Reden-blok exact zoals het nu is (candidate 41 / commit 7e5b846), zonder enige wijziging in structuur, tekst of `Semantics`-label-opbouw buiten de toegevoegde zin.

Er wordt geen nieuw API-veld, geen nieuwe backendlogica en geen wijziging aan `ShadowIterationEngine.kt` toegevoegd — `criticVerdict` is al beschikbaar op het iteratie-object dat `IterationSessionDialog` ontvangt.

## Acceptance criteria
- Wanneer `status == 'REJECTED'` en `criticVerdict == 'ACCEPT'` (guardrail-pad), toont het Reden-blok naast de bestaande, uit het criticus-artefact afgeleide tekst ook de vaste toelichtingszin: "Let op: Alle voorgestelde kandidaten zijn geblokkeerd (duplicaat of guardrail), waardoor deze cyclus niet doorgaat ondanks een positief criticusoordeel."
- Voor alle overige `REJECTED`- en `NEEDS_REVISION`-iteraties (`criticVerdict != 'ACCEPT'`, inclusief `criticVerdict == null`) rendert het Reden-blok exact zoals vóór deze wijziging, zonder de toelichtingszin.
- Voor iedere andere status (`ACCEPTED`, `PENDING`, `QUEUED`, `RUNNING`) blijft het Reden-blok volledig verborgen, ongewijzigd t.o.v. de huidige situatie.
- De toelichtingszin is statische, vaste tekst (geen interpolatie, geen afgeleide waarden); er wordt geen nieuw API-veld en geen nieuwe backendlogica toegevoegd.
- De toelichtingszin is opgenomen in dezelfde `Semantics`-structuur als het bestaande Reden-blok (het `Semantics(label: 'Reden: ...')`-blok), zodat schermlezers ook deze toelichting meekrijgen als onderdeel van het Reden-blok.
- De toelichtingszin begint met het herkenbare voorvoegsel 'Let op:' en wordt niet uitsluitend via kleur of icoon gecommuniceerd (puur tekstueel, conform bestaande toegankelijkheidsstijl).
- Een geautomatiseerde widgettest (uitbreiding van of aanvulling op `dashboard-frontend/test/iteration_session_reason_block_test.dart`) dekt minimaal twee scenario's:
  1. Guardrail-pad: `status == 'REJECTED'` met `criticVerdict == 'ACCEPT'` → toelichtingszin is zichtbaar in het Reden-blok.
  2. Regulier REJECTED-geval: `status == 'REJECTED'` met `criticVerdict != 'ACCEPT'` (bijv. `'REJECT'` of `null`) → toelichtingszin is afwezig.
  De test moet falen als deze twee gevallen door elkaar gehaald worden (d.w.z. de test controleert zowel aanwezigheid als expliciete afwezigheid van de toelichtingszin, niet alleen één van beide).

## Aannames
- `iteration['criticVerdict']` is voor de bestaande sessiedata al beschikbaar in de payload die `IterationSessionDialog` ontvangt (zie regel 981 in `main.dart`) en hoeft niet apart opgehaald te worden.
- De helperfunctie `_sessionWith` in `iteration_session_reason_block_test.dart` heeft momenteel geen `criticVerdict`-parameter (deze staat daar hardcoded op `null`); de developer breidt deze testhelper uit met een `criticVerdict`-parameter om beide scenario's te kunnen testen.
- De guardrail-verdict-fix in `ShadowIterationEngine.kt` (die `criticVerdict='ACCEPT'` met `status='REJECTED'` legitiem maakt) is al gemerged en vereist geen verdere backendwijziging voor deze story.
- De volgorde/plaatsing van de nieuwe zin binnen het Reden-blok (bijv. als extra alinea ná de bestaande criticus-tekst) is een implementatiedetail dat aan de developer wordt overgelaten, zolang de zin binnen hetzelfde Reden-blok en dezelfde `Semantics`-scope valt.

## Eindsamenvatting

Ik heb alle context: de story-tekst, developer- en tester-worklog, en de daadwerkelijke code-diff. Dit is voldoende voor een eindsamenvatting.

## Eindsamenvatting — product-factory-21

**Gebouwd**
In `IterationSessionDialog` (`dashboard-frontend/lib/main.dart`) is het bestaande Reden-blok uitgebreid met een voorwaardelijke, statische toelichtingszin. Deze verschijnt uitsluitend wanneer `status == 'REJECTED'` én `iteration['criticVerdict'] == 'ACCEPT'` (het "guardrail-pad": alle door de criticus goedgekeurde kandidaten zijn alsnog geblokkeerd op duplicaat/guardrail, waardoor de cyclus niet doorgaat). De zin luidt exact: *"Let op: Alle voorgestelde kandidaten zijn geblokkeerd (duplicaat of guardrail), waardoor deze cyclus niet doorgaat ondanks een positief criticusoordeel."*

**Gemaakte keuzes**
- De zin wordt als extra alinea (`\n\n`) toegevoegd aan `displayText`, vóór de bestaande `Semantics(label: 'Reden: $displayText')` wordt opgebouwd — dus binnen dezelfde Semantics-scope, zodat schermlezers hem meekrijgen.
- Voor alle overige status/`criticVerdict`-combinaties (incl. `NEEDS_REVISION`, `criticVerdict != 'ACCEPT'`, `null`) blijft `displayText` exact ongewijzigd t.o.v. de vorige versie.
- Geen backend-, API- of `ShadowIterationEngine.kt`-wijzigingen; puur tekstuele, statische toevoeging (geen kleur/icoon).

**Getest**
- Twee nieuwe widgettests in `iteration_session_reason_block_test.dart`: guardrail-pad (zin zichtbaar) en regulier REJECTED met `criticVerdict='REJECT'` (zin afwezig, expliciet gecontroleerd met `findsNothing`).
- Volledig vangnet tweemaal groen gedraaid (developer + tester): `mvn clean verify` (16 tests, 0 failures), `flutter analyze` (geen issues), `flutter test` (135/135 groen).
- Preview-smoketest uitgevoerd (frontend en backend health-endpoints HTTP 200); interactieve/screenshot-verificatie in de preview was niet mogelijk (geen browsertool in de agentcontainer) — bewust achterwege gelaten, conform bestaande agent-tip.

**Bewust niet gedaan**
- Geen wijziging aan `.factory/verification.yaml` — bestaande Flutter-analyze/test-checks dekken dit al.
- Geen visuele/screenshot-verificatie van het Reden-blok in de preview-omgeving.

<!-- deploy-summary:start -->
Als een reeks voorstellen wordt afgewezen doordat ze allemaal dubbel bleken of tegen een veiligheidsregel aanliepen — ook al waren ze inhoudelijk goedgekeurd — laat het scherm nu duidelijk zien waarom dat is gebeurd. Er is verder niets aan het gedrag van de applicatie veranderd.
<!-- deploy-summary:end -->
