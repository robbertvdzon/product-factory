# product-factory-2 - Voeg verplichte fallback-waarde 'niet-classificeerbaar' toe aan de iteratie-uitkomstmapping, gebaseerd op het daadwerkelijke datamodel

## Story

Voeg verplichte fallback-waarde 'niet-classificeerbaar' toe aan de iteratie-uitkomstmapping, gebaseerd op het daadwerkelijke datamodel

<!-- refined-by-factory -->

## Samenvatting
De iteratie-classificatiebadge in het productoverzicht toont nu altijd één van vier vaste waarden (onderzoek-onvoldoende, guardrail-conflict, richting-gekozen, richting-verworpen), maar elke onherkende ruwe status valt momenteel stilzwijgend terug op 'onderzoek-onvoldoende' — dat is misleidend, want die iteratie is helemaal niet als "onderzoek onvoldoende" beoordeeld. Deze story voegt een aparte, herkenbare vijfde waarde 'niet-classificeerbaar' toe voor precies die situatie: een afgeronde iteratie met een status die het systeem niet kent. Zo blijft de badge altijd eerlijk over wat er werkelijk bekend is.

## Scope
- Uitbreiding van de bestaande mapping- en weergavelaag in `dashboard-frontend/lib/classification.dart` (functie `classifyIterationOutcome`, constanten `kIterationClassifications`/`kClassificationColors`, widget `ClassificationBadge`) en het gebruik ervan in `dashboard-frontend/lib/main.dart`.
- Geen nieuwe databron, geen nieuwe backend-velden, geen wijziging van `ShadowIterationView`/`ShadowIterationApi.kt`, geen wijziging van de PR-goedkeuringsflow.
- "Bekende waarden" voor de vier bestaande categorieën blijven ongewijzigd, gebaseerd op het daadwerkelijke datamodel (`status`-veld, vrij `varchar(32)`, geen DB-enum):
  - `ACCEPTED` → richting-gekozen
  - `NEEDS_REVISION` → onderzoek-onvoldoende
  - `REJECTED` → richting-verworpen
  - `FAILED` → guardrail-conflict
- `QUEUED` en `RUNNING` zijn bekende, niet-afgeronde tussenstatussen; dit blijft het bestaande, bewust gekozen gedrag (badge toont 'onderzoek-onvoldoende', zie huidige test "fallback: lopende/wachtende iteraties") en verandert niet door deze story.
- Nieuw: elke status die niet in de vier bovenstaande categorieën of in `{QUEUED, RUNNING}` voorkomt (inclusief `null`/lege waarde en elke toekomstige onbekende status) mapt voortaan naar de nieuwe vaste waarde `niet-classificeerbaar`, in plaats van naar `onderzoek-onvoldoende`.
- De set "bekende ruwe waarden per categorie" wordt expliciet en uitbreidbaar gedocumenteerd in code (bijv. als benoemde lijst/mapping-tabel in `classification.dart`), zodat een toekomstige nieuwe status bewust aan een categorie toegevoegd kan worden in plaats van impliciet te worden opgevangen door de fallback.
- `niet-classificeerbaar` krijgt een eigen kleurenpaar (achtergrond/voorgrond) in `kClassificationColors`, met een tekst-op-achtergrond contrastverhouding van minimaal 4.5:1 (WCAG 2.1 AA), volgens hetzelfde patroon als de vier bestaande kleurenparen.
- De niet-kleur-onderscheiding van de fallback-badge volgt het bestaande, in `functional-spec.md` gedocumenteerde patroon van de vier bestaande badges: eigen zichtbare teksttabel ("niet-classificeerbaar") én een eigen Semantics-label — er is geen icoon-vorm-element in de huidige badge-implementatie, dus dat wordt niet toegevoegd (zie Aannames).

## Acceptance criteria
1. `classification.dart` documenteert (in code, als expliciete en uitbreidbare structuur) de daadwerkelijk voorkomende bekende ruwe `status`-waarden per categorie: ACCEPTED, NEEDS_REVISION, REJECTED, FAILED (de vier badge-categorieën) en QUEUED/RUNNING (bestaand, blijft op onderzoek-onvoldoende gemapt).
2. `classifyIterationOutcome` retourneert voor elke ruwe `status`-waarde die niet in die gedocumenteerde lijst voorkomt (inclusief `null`) exact de vaste waarde `niet-classificeerbaar` — nooit `null`, een lege string of een exception.
3. `kIterationClassifications` bevat alle vijf waarden; `niet-classificeerbaar` heeft een eigen entry in `kClassificationColors`.
4. Unit tests in `classification_test.dart` dekken minimaal:
   - elke bekende waarde (ACCEPTED, NEEDS_REVISION, REJECTED, FAILED) mapt naar de juiste van de vier bestaande categorieën (bestaande tests blijven groen);
   - QUEUED en RUNNING blijven op onderzoek-onvoldoende gemapt (bestaand gedrag, ongewijzigd);
   - een onbekende/nooit eerder geziene statuswaarde (bijv. een nieuw fictief statuslabel) én `null` mappen naar `niet-classificeerbaar`;
   - `kIterationClassifications` bevat alle vijf waarden en elke aanroep van `classifyIterationOutcome` retourneert altijd een waarde uit die lijst.
5. De WCAG-contrastcheck-test in `classification_test.dart` (die nu al over `kIterationClassifications` itereert) dekt automatisch ook `niet-classificeerbaar` en moet slagen (≥ 4.5:1).
6. Een widget-test (Flutter `flutter_test`, geen axe-core/DOM-tooling — niet beschikbaar in dit project) rendert een testdataset van iteratierijen die zowel de vijf bekende statuscombinaties als minstens één onbekende statuswaarde bevat, en assert dat elke rij precies één `ClassificationBadge` toont met een classificatietekst uit de vijfwaardige set, nooit een lege statuscel.
7. `functional-spec.md` wordt bijgewerkt: de opsomming van de vier badgewaarden wordt uitgebreid met `niet-classificeerbaar` en de bijbehorende voorwaarde (onbekende/onherkende status).

## Aannames
- "Afgeronde iteratie" in de oorspronkelijke AC wordt voor de fallback-logica gelezen als: elke status buiten de expliciet gedocumenteerde bekende-waardenlijst. QUEUED/RUNNING zijn zelf al een bekende, bewust gemodelleerde tussentoestand (bestaande test in `classification_test.dart` regel 40-52) en worden dus niet naar `niet-classificeerbaar` verplaatst — alleen genuine onbekende statuswaarden (inclusief eventuele toekomstige, nu nog niet bestaande statuscodes) triggeren de nieuwe fallback.
- De AC-tekst "icoon-vorm" wordt niet letterlijk geïmplementeerd: uit `dashboard-frontend/lib/classification.dart` en `functional-spec.md` blijkt dat de bestaande vier badges hun niet-kleur-onderscheiding al via zichtbare tekst + Semantics-label realiseren, zonder icoon. De fallback-badge volgt ditzelfde, al bestaande patroon (eigen tekst + eigen Semantics-label) in plaats van een nieuw icoon-element te introduceren dat inconsistent zou zijn met de vier bestaande badges.
- "DOM-test" uit de AC wordt vertaald naar een Flutter `WidgetTester`-test (geen DOM/axe-core beschikbaar in dit project, conform eerdere agent-inspectie).

## Eindsamenvatting

## Eindsamenvatting — product-factory-2

**Story:** Voeg verplichte fallback-waarde 'niet-classificeerbaar' toe aan de iteratie-uitkomstmapping

**Gebouwd:**
- `dashboard-frontend/lib/classification.dart`: nieuwe classificatiewaarde `kNietClassificeerbaar` toegevoegd aan `kIterationClassifications` (nu 5 waarden i.p.v. 4). De bekende ruwe statuswaarden per categorie (ACCEPTED, NEEDS_REVISION, REJECTED, FAILED, QUEUED, RUNNING) zijn expliciet en uitbreidbaar gedocumenteerd in een nieuwe tabel `kBekendeStatuswaardenPerCategorie`. `classifyIterationOutcome` zoekt de status daarin op; elke niet-gevonden waarde (onbekende status, `null`, lege string) valt nu terug op `kNietClassificeerbaar` in plaats van foutief op `onderzoek-onvoldoende`. QUEUED/RUNNING blijven bewust ongewijzigd op `onderzoek-onvoldoende` gemapt.
- Nieuw kleurenpaar toegevoegd aan `kClassificationColors` (achtergrond `#CFE2FF`, voorgrond `#073880`, blauw — visueel onderscheidend van de vier bestaande paren), contrastratio ~8,47:1, ruim boven de vereiste 4.5:1 (WCAG 2.1 AA).
- `functional-spec.md` bijgewerkt: badgewaarden-opsomming uitgebreid met `niet-classificeerbaar` + voorwaarde, "vier kleurenparen" → "vijf kleurenparen".
- Geen wijzigingen aan `main.dart`, backend of databasemodel nodig — die gebruiken de classificatiefunctie ongewijzigd en profiteren automatisch van de fix.

**Keuzes:**
- De fallback-logica is via een expliciete lookup-tabel geïmplementeerd (i.p.v. impliciete if/else-fallback), zodat toekomstige nieuwe statuswaarden bewust aan een categorie toegevoegd moeten worden.
- De AC-tekst "icoon-vorm" is bewust niet letterlijk geïmplementeerd: de bestaande vier badges onderscheiden zich al via tekst + Semantics-label zonder icoon; de nieuwe badge volgt hetzelfde patroon voor consistentie.

**Getest:**
- `flutter analyze`: schoon (0 issues).
- `flutter test`: 41/41 groen, inclusief aangepaste fallback-test (onbekend/`null`/lege string → `niet-classificeerbaar`) en uitgebreide widget-test (8 rijen: 5 bekende statussen + QUEUED/RUNNING + onbekende status + `null`), die assert dat elke rij precies één badge toont uit de vijfwaardige set, nooit leeg.
- `mvn clean verify`: BUILD SUCCESS (backendmodules ongewijzigd, alleen ter controle gedraaid tijdens development; niet vereist tijdens de teststap omdat geen backendbestand in de diff valt).
- Preview-omgeving bereikbaar (HTTP 200), maar zonder browser-/screenshot-tooling in de agentcontainer kon geen visuele DOM-inspectie na JS-rendering plaatsvinden; dit scenario is afdoende gedekt door de widget-test met gecontroleerde testdata.

**Bewust niet gedaan:**
- Geen wijzigingen aan `ShadowIterationView`/`ShadowIterationApi.kt`, geen nieuwe backend-velden of databronnen (buiten scope).
- Geen live E2E-verificatie met een échte onbekende status op de preview-omgeving (niet aan te maken zonder backend-mutaties); afgedekt via widget-test.

Reviewer en tester hebben beide akkoord gegeven, geen blockers.

<!-- deploy-summary:start -->
Het label bij een afgeronde onderzoeksronde toont voortaan altijd een duidelijke status. Kwam er eerder een onbekende of niet-herkende uitkomst binnen, dan werd die per ongeluk getoond als "onderzoek onvoldoende" — dat kon verwarrend zijn. Zo'n uitkomst krijgt nu een eigen, duidelijk herkenbaar label ("niet-classificeerbaar") met een eigen kleur, zodat altijd duidelijk is wat wel en niet bekend is.
<!-- deploy-summary:end -->
