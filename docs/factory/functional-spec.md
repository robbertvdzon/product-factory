# Functional Spec

De Product Factory laat producten autonoom doorontwikkelen: per product draaien productcycli
(shadow iterations) waarin agents onderzoek doen, storykandidaten schrijven en die — als het product op
autonoom staat — als stories naar de Software Factory sturen.

## De overzichtspagina

De Flutter-webapp (`dashboard-frontend`) heeft één hoofdscherm: het productoverzicht. Het ververst zichzelf
elke 5 seconden en bestaat van boven naar beneden uit:

1. **Metric-tegels** — totalen voor producten, interne storykandidaten, workspace-publicaties,
   shadow-iteraties en Software Factory-stories. Deze tellers tonen altijd het *totaal*, ook als de lijst
   eronder is ingekort.
2. **Producten** — per product status, ontwikkelmodus en knoppen voor pauzeren/hervatten,
   instellingen en 'Start productcyclus nu'. 'Start productcyclus nu' staat als losstaande,
   visueel dominante CTA (`StartCycleButton`, eigen rand/kleurenpaar met WCAG AA-contrast
   ≥4.5:1 en zichtbare focusring) op een eigen rij, boven en met extra ruimte gescheiden van
   de secundaire knoppenrij (Pauzeren/Hervatten, Instellingen, Start overleg); enabling-conditie,
   `_startCycle`-gedrag, icoon en tekst zijn ongewijzigd. Missie, Software Factory-project, doelrepository,
   workspace, `maxStoriesPerCycle`, `wipLimit`, AI-provider/model en cyclustijden staan niet meer
   op de kaart zelf, maar in het Instellingen-scherm (`ProductSettingsDialog`, geopend via de
   Instellingen-knop): missie, project en workspace als alleen-lezen tekst (gekoppeld aan de
   Software Factory-integratie, dus niet bewerkbaar in dit scherm), de overige velden — inclusief
   de doelrepository — bewerkbaar en opslaanbaar. Het scherm is met het toetsenbord te bedienen:
   opent met focus binnen de dialoog, houdt de tab-focus binnen de dialoog (focus-trap) en sluit
   met Escape waarbij de focus terugkeert naar de Instellingen-knop. Volgorde van de productenlijst:
   zoals de backend hem levert (op slug).
3. **Productcycli en onderzoekssessies** — per cyclus status, huidige rol, **starttijd** en
   **doorlooptijd**, aantal kandidaten en of de cyclus doorgezet mag worden. Een iteratie met
   `status` QUEUED of RUNNING (nog lopend) toont een neutrale voortgangsindicator
   (`IterationProgressIndicator`) in plaats van een badge; elke andere status zonder expliciet
   beslisrecord toont een vaste, afgeleide classificatiebadge — `onderzoek-onvoldoende`, `technische fout`,
   `richting-gekozen`, `richting-verworpen` of `niet-classificeerbaar` — afgeleid uit de bestaande
   velden `status`, `criticVerdict` en `errorMessage`. `niet-classificeerbaar` verschijnt voor elke
   ruwe statuswaarde die het systeem niet als een van de vier bekende categorieën herkent
   (inclusief een ontbrekende status of een tijdens uitvoering afgebroken iteratie, waarvoor geen
   apart statusveld bestaat), zodat de badge nooit ten onrechte 'onderzoek-onvoldoende' claimt voor
   een uitkomst die niet bekend is. De badge communiceert de classificatie zowel via zichtbare
   tekst als via een Semantics-label (niet uitsluitend via kleur) en elk van de vijf kleurenparen
   haalt WCAG 2.1 AA-contrast (≥ 4.5:1). De voortgangsindicator gebruikt `Semantics(liveRegion:
   true)` (het Flutter-web-equivalent van `aria-live="polite"`), zodat een schermlezer meekrijgt
   wanneer een iteratie nog loopt. De badge zelf is met muis én toetsenbord (Tab, Enter/Spatie)
   te activeren en klapt dan een inline scope-disclaimerpaneel open direct onder de badge, met de
   vaste tekst "Dit toont wat de uitkomst was, niet waarom." — geen pop-upvenster of dialoog,
   `Semantics(expanded: ...)` volgt de open/dicht-status. Nogmaals activeren of Escape klapt het
   paneel weer in en herstelt de focus op de badge. Iedere cyclusregel toont daarnaast één native
   button met de beslisbron. Bij een expliciet handmatig-annuleringsrecord bevat de button de
   zichtbare en toegankelijke teksten `Beslisbron: Mens` en `Reden: Handmatig geannuleerd`.
   Dit record is gekoppeld via hetzelfde `iterationId` en heeft voorrang op de afleiding uit
   status, criticusoordeel en foutmelding. Daarom worden voor deze cyclus de afgeleide
   classificatiebadge en `outcomeReason`-verklaring niet getoond; een bestaande `errorMessage`
   blijft wel als foutreden in het detail beschikbaar, maar geldt niet als provenance.
   Voor cycli zonder gekoppeld record toont de button `Beslisbron: Evaluatie-agent (Afgeleid)`,
   `Beslisbron: Technische fout (Afgeleid)` of `Beslisbron: Onbekend (Afgeleid)`. De fallbackwaarde
   wordt read-only afgeleid uit `criticVerdict`, `status` en `errorMessage`: `ACCEPT`/`ACCEPTED`,
   `REVISE`/`NEEDS_REVISION` en `REJECT`/`REJECTED` leveren `Evaluatie-agent`; uitsluitend
   `FAILED` met een ontbrekend verdict en een niet-lege foutmelding levert `Technische fout`;
   iedere andere, ontbrekende, onbekende of tegenstrijdige combinatie levert `Onbekend`. Voor de
   vergelijking wordt omringende witruimte verwijderd, maar de bekende waarden blijven
   hoofdlettergevoelig. De button opent met klik, Enter of Spatie de bestaande detaildialoog van
   precies die cyclus. `Afgeleid` staat zowel in de zichtbare tekst als in de toegankelijke naam;
   een gekoppeld expliciet record is eveneens met bron en reden toegankelijk en de betekenis wordt
   niet uitsluitend via kleur gecommuniceerd. De rijcontainer zelf is geen tweede detailbediening
   en toont daarom ook geen navigatie-chevron; de afzonderlijke annuleeractie bij een lopende
   cyclus blijft ongewijzigd.
   Sluiten met de zichtbare sluitactie of Escape herstelt de focus naar de beslisbronbutton die het
   dialoog opende. Het cyclusoverzicht toont bij deze beslisbron geen ruwe foutmelding, prompt, log
   of artefactinhoud en openen/sluiten veroorzaakt alleen de bestaande leesverzoeken. Deze
   detaildialoog (`IterationSessionDialog`, `dashboard-frontend/lib/main.dart`) toont voor een
   expliciet handmatig-annuleringsrecord dezelfde bron en reden, plus
   `Mechanisme: Handmatige annulering` en `Beslist op: <lokale datum en tijd>` uit `decidedAt`.
   De afgeleide badge en uitkomstreden blijven daar eveneens verborgen. Voor een historische cyclus
   zonder record toont het detail de beslisbron met `(Afgeleid)` en dezelfde
   `ClassificationBadge` met dezelfde `classifyIterationOutcome`-uitkomst als de lijstkaart-rij
   (identieke badge-tekst en `kClassificationColors`-kleurenpaar) — geen losse `Chip` met de ruwe
   backend-statuswaarde (bv. 'NEEDS_REVISION') meer. Waar de badge aanwezig is, is deze het eerste
   focusbare element in het dialoog, vóór de secties Voortgang, agentresultaten en
   workspace-publicaties, en is er via toetsenbord (Tab, Enter/Spatie) op dezelfde manier te
   bedienen als op de lijstkaart. In de sectie agentresultaten toont elke
   uitgeklapte roltegel (Onderzoeker, Product owner, UX-ontwerp, Story writer, Criticus) een
   leesbare samenvatting van de bekende tekstvelden van die rol (bv. `summary`, `findings`,
   `decisions`/`rationale`, `steps`, `candidates`, `issues`), direct zichtbaar zonder extra klik en
   zonder lege of `null`-velden. De bijbehorende ruwe JSON staat in dat geval niet meer direct
   zichtbaar, maar achter een geneste, standaard ingeklapte toggle met zichtbaar label 'Toon
   technische details' (`TechnicalDetailsToggle`, `dashboard-frontend/lib/main.dart`), onafhankelijk
   van de in-/uitklapstatus van de roltegel zelf. Deze toggle is met muis én toetsenbord (Tab,
   Enter/Spatie) te bedienen en communiceert zijn open/dicht-status via `Semantics(expanded: ...)`
   (het Flutter-web-equivalent van `aria-expanded`). Matcht het rolresultaat geen van de vijf
   rolspecifieke schema's hierboven, dan valt de weergave volledig terug op een generieke leesbare
   weergave: elk top-level veld dat een string is, of een lijst die uitsluitend uit primitieve
   waarden (tekst, getal, boolean) bestaat, verschijnt als gelabelde regel — het label komt van
   dezelfde `humanizeFieldKey`-functie die ook de vaste labels voor `findings`, `decision`,
   `story`, `verdict` en `reason` levert — en ook dan verdwijnt de ruwe JSON achter de toggle
   'Toon technische details'. Dezelfde generieke fallback wordt ook toegepast per afzonderlijk
   top-level veld binnen een wél herkende rol: wijkt het content_json van die rol af van het
   verwachte schema (bv. `findings` als losse string in plaats van een objectenlijst bij de
   Onderzoeker-rol), dan levert de rolspecifieke branch voor dat ene veld niets op, en verschijnt
   het alsnog leesbaar via dezelfde generieke regel — de overige, wél conforme velden van diezelfde
   rol blijven gewoon via hun rolspecifieke weergave zichtbaar. Alleen als noch de rolspecifieke
   branch, noch deze per-veld generieke fallback voor een top-level veld iets oplevert (bv. geneste
   objecten of arrays van objecten binnen een niet-conform artefact), blijft dat veld ongerenderd.
   Bevat het resultaat op het hoogste niveau uitsluitend geneste
   objecten, arrays van objecten, of is de inhoud niet decodeerbaar of onherkend (of van een
   retry-poging met `-2`/`-3`-suffix op `artifactType`, die dezelfde weergave als de eerste
   poging krijgt), dan blijft uitsluitend de ruwe JSON direct zichtbaar zonder toggle, zonder
   dat het dialoog crasht. Heeft de iteratie
   `status == 'FAILED'`, dan toont dit detaildialoog (`IterationSessionDialog`,
   `dashboard-frontend/lib/main.dart`) direct onder het 'Opdracht'-blok een 'Foutreden'-blok met de
   inhoud van `iteration['errorMessage']`, of exact de tekst 'Geen foutreden beschikbaar' als dat
   veld leeg of `null` is; bij elke andere status blijft dit blok volledig verborgen. Het blok heeft
   een expliciet `Semantics`-label `'Foutreden: <tekst>'`, zodat het als afzonderlijk betekenisvol
   blok wordt aangekondigd door schermlezers. Heeft de iteratie in plaats daarvan
   `status == 'NEEDS_REVISION'` of `status == 'REJECTED'`, dan toont ditzelfde dialoog direct onder
   het 'Opdracht'-blok en vóór de roltegels-sectie een 'Reden'-blok (titel + `SelectableText`,
   dezelfde stijl als het 'Foutreden'-blok). Is er in `artifacts` een criticus-artefact voor deze
   iteratie aanwezig (`artifactType` `critic` of, bij een retrypoging, `critic-2`/`critic-3`/…,
   waarbij het meest recente/hoogste-suffix-artefact wordt gebruikt), dan bevat het blok leesbare
   lopende tekst opgebouwd uit `overallVerdict`, `summary` en `requiredChanges[]` van dat artefact
   (`ShadowSchemas.kt`-schema `critic`) — nooit rauwe JSON. Ontbreekt zo'n criticus-artefact, dan
   hangt de getoonde tekst af van `iteration['criticVerdict']`. Is `criticVerdict` wél gezet (niet
   `null`), dan toont het blok een tekst die de letterlijke verdict-waarde expliciet benoemt samen
   met een expliciete melding dat er geen onderliggend criticus-artefact beschikbaar is (bv.
   'Criticusoordeel REVISE geregistreerd, maar geen onderliggend criticus-artefact beschikbaar.'),
   in plaats van de onvoorwaardelijke 'Criticus-oordeel ontbreekt'-tekst — dit voorkomt een
   tegenspraak met een elders in de UI getoonde criticus-badge, die uitsluitend op
   `criticVerdict != null` is gebaseerd, ongeacht of er een artefact bestaat. Is `criticVerdict`
   `null`, dan toont het blok in plaats daarvan exact de tekst 'Criticus-oordeel ontbreekt voor
   deze cyclus' — behalve voor de deelcasus hieronder. Is de status `NEEDS_REVISION`, is er géén
   `iteration['criticVerdict']` (`null`) én ontbreekt het criticus-artefact, dan bepaalt het dialoog
   uit `steps` (role/status/attempt/startedAt/completedAt/errorMessage) welke agentrol als laatste
   `COMPLETED` is, en toont in plaats van de generieke fallbacktekst de naam van die rol (via de
   bestaande `_roleLabel`-mapping) plus een leesbare resultaatsamenvatting uit het bijbehorende
   artefact in `artifacts` — voor researcher/critic/summary het `summary`-veld, voor
   product_owner/ux_designer/story_writer een samenvatting opgebouwd uit hun belangrijkste velden
   (dezelfde weergavelogica/labels als de roltegels, via `humanizeFieldKey`) — nooit rauwe JSON. Is
   geen enkele rol `COMPLETED`, dan toont het blok in plaats daarvan een aparte, expliciete
   fallbacktekst die dat meldt (ongelijk aan de generieke 'Criticus-oordeel ontbreekt'-tekst).
   `steps`/`artifacts` geven geen betrouwbaar onderscheid tussen een bewuste pipeline-stop en een
   timeout/technische fout (een niet-gestarte rol levert domweg geen step-record op), dus benoemt
   deze tekst uitsluitend rolnaam en resultaat, zonder een gegokte oorzaak. Deze
   `NEEDS_REVISION`-zonder-`criticVerdict`-deelcasus geldt uitsluitend als `criticVerdict` ontbreekt;
   is `criticVerdict` wél gezet, dan geldt in plaats daarvan de verdict-tekst hierboven, ook zonder
   een voltooide rol. Is de iteratiestatus
   `REJECTED` en is `iteration['criticVerdict'] == 'ACCEPT'` (het
   guardrail-pad: alle door de criticus goedgekeurde kandidaten zijn alsnog geblokkeerd op
   duplicaat/guardrail), dan wordt aan de criticus-tekst een extra, statische alinea toegevoegd
   met exact de tekst 'Let op: Alle voorgestelde kandidaten zijn geblokkeerd (duplicaat of
   guardrail), waardoor deze cyclus niet doorgaat ondanks een positief criticusoordeel.' — puur
   tekstueel, binnen dezelfde `Semantics`-scope, zonder kleur of icoon. Voor alle overige
   `REJECTED`-/`NEEDS_REVISION`-combinaties (`criticVerdict != 'ACCEPT'`, incl. `null`) blijft het
   blok ongewijzigd zonder deze toelichtingszin.
   Ook dit blok heeft een expliciet `Semantics`-label `'Reden: <tekst>'`. Bij elke andere status
   (o.a. `ACCEPTED`, `PENDING`, `QUEUED`, `RUNNING`) blijft het Reden-blok volledig verborgen; het
   bestaande 'Foutreden'-blok en de standaard ingeklapte criticus-roltegel met volledig artefact
   blijven ongewijzigd.
4. **Software Factory-stories** — de leveringen met externe storykey, status en fase.
5. **Benodigde access tokens** — openstaande handmatige acties, af te melden met een toelichting.
6. **Storywachtrij** — storykandidaten verdeeld over Fout / Bezig / In wachtrij / Klaar. Is een
   kandidaat geblokkeerd door een onopgeloste `dependsOn`-verwijzing (`blocked == true` met een
   niet-lege `blockedReason`), dan toont de kaart direct — zonder extra klik — onder de titel een
   label met icoon en de tekst "Geblokkeerd: <reden>", in het bestaande WCAG AA-contrasterende
   kleurenpaar `kGuardrailConflict` (`classification.dart`) en opvraagbaar via de semantics-tree
   van de kaart. Ontbreekt de blokkade of de reden, dan blijft de kaart ongewijzigd; er wordt geen
   extra data opgehaald voor dit label (`_buildStoryQueueSections`,
   `dashboard-frontend/lib/main.dart`).
7. **Workspace** — gepubliceerde artifacts, klikbaar om de inhoud te tonen.

### Start- en doorlooptijd van een productcyclus

- Starttijd = `startedAt`; is die leeg, dan `createdAt`.
- Doorlooptijd = `completedAt - startedAt`, leesbaar als `2u 13m`, `4m 12s` of `35s`.
- Loopt de cyclus nog, dan staat er `loopt nog: <tijd sinds start>`; die waarde loopt mee met de
  auto-refresh.
- Is de cyclus nog niet gestart, dan staat er geen doorlooptijd.
- Datum en tijd staan in de lokale tijdzone van de browser als `dd-MM-yyyy HH:mm`, nooit als ruwe
  ISO-string.

### Lijstbeperking met de 'Meer'-knop

Alle lijsten op de overzichtspagina (producten, productcycli, Software Factory-stories, access tokens,
elke subsectie van de storywachtrij en workspace-publicaties) tonen standaard **5 items**. Staat er meer
klaar, dan verschijnt eronder een knop **'Meer (nog N)'** die er telkens **10** bij toont; de knop
verdwijnt zodra alles zichtbaar is. Elke sectie heeft een eigen, onafhankelijke teller, en die teller
overleeft de auto-refresh: een uitgeklapte lijst blijft uitgeklapt en nieuwe items verschijnen bovenaan.
Lijsten met een bruikbaar tijdstempel staan gesorteerd op nieuwste eerst; workspace-publicaties hebben geen
tijdstempel en houden de volgorde van de backend.

## Status en conclusion van een productcyclus

Dit blok legt vast wat "status" en "conclusion" van een productcyclus (shadow iteration) betekenen
en hoe ze zich tot elkaar verhouden, als zelfstandige uitleg naast de badge-beschrijving hierboven.

- **Status is altijd óf lopend, óf voltooid — nooit iets ertussenin.** Het bestaande `status`-veld
  (`ShadowIterationView.status`, `productfactory-contracts/.../Contracts.kt`) kent de ruwe waarden
  QUEUED, RUNNING, ACCEPTED, NEEDS_REVISION, REJECTED en FAILED. QUEUED en RUNNING zijn **lopend**;
  ACCEPTED, NEEDS_REVISION, REJECTED en FAILED zijn **voltooid**. Het eindoordeel (conclusion) is
  pas relevant en geldig zodra de status voltooid is; zolang een iteratie nog loopt, bestaat er nog
  geen conclusion om te tonen.
- **Er bestaat geen apart `conclusion`-veld in het datamodel.** De term "conclusion" verwijst naar
  het geheel van de bestaande velden `status`, `criticVerdict` (en `errorMessage` bij een FAILED
  iteratie), samen vertaald naar één van de vijf vaste badges — `onderzoek-onvoldoende`,
  `technische fout`, `richting-gekozen`, `richting-verworpen` of `niet-classificeerbaar` — via
  `classifyIterationOutcome` in `dashboard-frontend/lib/classification.dart`. Dit wijkt af van een
  eventueel aspirational onderzoeksmodel waarin "conclusion" als apart databaseveld wordt
  gesuggereerd: dat veld bestaat niet en is ook niet nodig, omdat `status`/`criticVerdict`/
  `errorMessage` de conclusion samen al volledig bepalen.
- **Een tijdens uitvoering onderbroken iteratie wordt automatisch geclassificeerd, zonder apart
  menselijk besluitmoment.** Deze historische regel geldt voor onbekende onderbrekingen zonder
  expliciet beslisrecord; er bestaat nog steeds geen apart CANCELLED-statusveld. Een geslaagde
  handmatige annulering is de expliciete uitzondering: die zet een QUEUED- of RUNNING-cyclus naar
  FAILED en legt daarnaast menselijke provenance vast. Andere onbekende of historische
  onderbrekingen blijven via `classifyIterationOutcome` op `niet-classificeerbaar` of een andere
  conservatieve fallback uitkomen; deze afleiding maakt geen beslisrecord aan.
- **De beslisbron is iets anders dan de conclusion-badge.** Een optioneel, expliciet
  `decision`-record bevat uitsluitend `iterationId`, `actorType`, `mechanism`, `reasonCode` en
  `decidedAt`. Voor handmatige annulering zijn de drie codewaarden respectievelijk `HUMAN`,
  `MANUAL_CANCELLATION` en `MANUALLY_CANCELLED`; dit record levert `Mens`, `Handmatig geannuleerd`
  en `Handmatige annulering` in de UI en onderdrukt de afgeleide conclusion-badge en uitkomstreden.
  Zonder gekoppeld record gebruikt `classifyDecisionSource` de bestaande invoervelden en kent de
  fallback bewust slechts drie uitkomsten: `Evaluatie-agent`, `Technische fout` en `Onbekend`.
  Alleen exact bewezen verdict-/eindstatusparen wijzen naar de evaluatie-agent; het guardrailpad
  `ACCEPT` met `REJECTED`, lopende statussen en alle ambigue combinaties blijven `Onbekend`. Deze
  waarden worden zichtbaar en toegankelijk als `Afgeleid` gemarkeerd.
- **Handmatige annulering is atomair en privacy-minimaal.** De terminale status, `completedAt` en
  het beslisrecord worden in één transactie opgeslagen, waarbij `decidedAt` exact dezelfde
  tijdswaarde krijgt als `completedAt`. Een conflict, afgewezen annulering of rollback laat geen
  los record of halve statusovergang achter. De tabel staat door `iterationId` als primary key
  maximaal één record per cyclus toe. Er is geen historische backfill en het record bevat geen
  naam, e-mailadres, account-id, aangeleverde annuleerreden of vrije tekst.
- **Het eindoordeel van een iteratie wijzigt, na vaststelling, niet meer.** Dit geldt
  onvoorwaardelijk: `markAccepted`, `markReviewed`, `markFailed` en `markManuallyCancelled` in
  `productfactory/.../ShadowIterationApi.kt` weigeren een tweede schrijfpoging op
  `status`/`critic_verdict` zodra een iteratie al in een terminale staat staat. De eerste drie
  methoden gebruiken `... status not in (TERMINAL_STATUSES_SQL)` en loggen een genegeerde poging;
  handmatige annulering schrijft uitsluitend bij `status in ('QUEUED', 'RUNNING')` en retourneert
  bij een gelijktijdige afronding een conflict zonder beslisrecord.

## Testerafspraken

Een testerresultaat bereikt alleen `tested` met compleet groen machinebewijs uit
`.factory/verification.yaml` voor exact dezelfde HEAD/worktree-tree. Missing bewijs/config, onbekende
versie, tool-missing, timeout, non-zero en revisionmismatch leveren altijd `test-rejected` op;
pre-existing, flaky en omgevingsfouten zijn nooit groen.
