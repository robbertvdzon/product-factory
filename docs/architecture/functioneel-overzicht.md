# Functioneel overzicht: wat doet een productcyclus precies

Dit document beschrijft, los van implementatiedetails, wat er functioneel gebeurt zodra een
productcyclus start: welke agents er achter elkaar draaien, wat ze opleveren, hoe storykandidaten
bij de Software Factory terechtkomen, hoe die uitvoering gevolgd en van antwoorden voorzien wordt,
en wat je daarvan in het dashboard terugziet. Voor de vertrouwensgrenzen rond shadow mode, zie
[shadow-mode.md](shadow-mode.md).

## 1. Wat start een cyclus

Een cyclus (in de code een "shadow-iteratie", ook voor autonome producten) start op twee manieren:

- **Handmatig** vanuit het dashboard: de knop "Shadow-iteratie" (modus `shadow`, blijft altijd
  intern) of "Start productcyclus nu" (modus `autonomous`, alleen zichtbaar bij een actief,
  autonoom product met workspace-eigenaarschap `product-factory`). Rechtstreeks op de runtime kan
  hetzelfde via `POST /api/products/{slug}/shadow-iterations` respectievelijk
  `POST /api/products/{slug}/autonomous-cycles`.
- **Automatisch**, uitsluitend in `autonomous`-modus: de `AutonomousCoordinator` controleert elke
  minuut (`product-factory.autonomy.poll-delay`, standaard elke minuut) of het product aan de
  planning toe is (`iterationSchedule`, standaard `0 3 * * *`, dus rond 03:00). Een nieuwe
  automatische cyclus start alleen als **al het werk van de vorige cyclus volledig is afgerond**
  (geen actieve of nog-niet-geëvalueerde levering, geen lopende iteratie), er geen open fouten of
  openstaande access-tokenacties zijn, en er die dag nog geen automatische cyclus is gestart. Er
  loopt dus nooit meer dan één cyclus tegelijk per product.

Elke cyclus krijgt een korte, vrije "focus" mee (of automatisch: "bepaal zelf de belangrijkste nog
onbeantwoorde productvraag"), plus de eerder geaccepteerde iteraties en bestaande storykandidaten
als context, zodat hij niet opnieuw hetzelfde voorstelt.

## 2. De agentketen binnen één cyclus

Eén cyclus doorloopt vijf agenttaken, altijd in deze volgorde, elk met een eigen rol, prompt,
strikt JSON-schema en eigen validatie:

1. **RESEARCHER** — doet webonderzoek, legt per bron URL, raadpleegdatum, rechtenindicatie en
   relevantie vast, en levert een aantal onderbouwde bevindingen op (geen productbesluit).
2. **PRODUCT_OWNER** — kiest op basis van dat onderzoek één kleine, samenhangende productrichting
   en legt ook verworpen opties vast; verwijst alleen naar bronnen uit stap 1.
3. **UX_DESIGNER** — werkt die richting uit tot een gebruikersflow, een tekstueel wireframe en
   toetsbare hypotheses, met expliciete aandacht voor toegankelijkheid en privacy.
4. **STORY_WRITER** — schrijft één tot maximaal `maxStoriesPerCycle` (gemaximeerd op 3) kleine,
   afzonderlijk toetsbare storykandidaten met acceptatiecriteria, bronverwijzingen, afhankelijkheden
   en risico's.
5. **CRITIC** — beoordeelt bronkwaliteit, rechten, privacy, toegankelijkheid, scope, duplicaten en
   iedere kandidaat afzonderlijk, en geeft een eindoordeel: `ACCEPT`, `REVISE` of `REJECT`.

Bij `REVISE` (en minimaal één *blokkerende* bevinding) herschrijft STORY_WRITER de kandidaten met de
criticusfeedback erin verwerkt, waarna CRITIC opnieuw beoordeelt — tot maximaal 3 pogingen. Elke
stap wordt vastgelegd als een `agent_run` en is per stap zichtbaar in het dashboard (status,
starttijd, eindtijd, foutmelding).

**Harde autonomieregel** (alleen relevant in `autonomous`-modus): geen enkele story of
acceptatiecriterium mag een handmatige actie van de eigenaar vereisen — geen handmatige test,
productbesluit, accountaanmaak, betaling, DNS-wijziging of apparaatcontrole. De enige toegestane
uitzondering is een concreet, onvermijdelijk extern **access token / API-key / OAuth-secret**. Als
STORY_WRITER dat toch voorstelt, forceert de runtime zelf een `REVISE` (los van wat CRITIC zegt) en
moet de kandidaat herschreven worden.

## 3. Uitkomst van een cyclus

- Alleen bij eindoordeel `ACCEPT` én minstens één geaccepteerde, niet-dubbele kandidaat wordt er
  iets gepubliceerd. Anders eindigt de cyclus als `NEEDS_REVISION` of `REJECTED` en gebeurt er
  verder niets.
- Bij acceptatie schrijft de workspace-publisher één leesbaar dossier
  (`products/<slug>/research/shadow-iteration-NNNN.md`) met onderzoek, productbesluit, UX,
  criticusoordeel en de geaccepteerde storykandidaten, als pull request met auto-merge naar de
  aparte repository `product-factory-workspace`.
- Alle kandidaten (geaccepteerd én afgewezen), ruwe agentoutput en criticusoordelen blijven
  daarnaast in de eigen PostgreSQL-database staan; alleen het geaccepteerde dossier komt in Git.
- Geaccepteerde kandidaten krijgen in de database status `INTERNAL` (in `shadow`-modus blijven ze
  daar definitief) totdat ze naar de Software Factory geleverd zijn.

## 4. Hoe kandidaten bij de Software Factory terechtkomen

Dit gebeurt alleen voor `autonomous`-producten, en pas nadat de workspace-PR met het dossier ook
daadwerkelijk gemerged is. De `AutonomousCoordinator` doet elke minuut het volgende, per actief
autonoom product:

1. **Eén tegelijk leveren, niet allemaal tegelijk.** Van alle kandidaten die klaarstaan (`ACCEPT`,
   dossier gemerged, nog niet geleverd) pakt de coordinator er telkens **hoogstens één** en meldt
   die aan bij de Software Factory (`POST /api/integrations/v1/stories`, idempotent per kandidaat).
   Dit gebeurt alleen als:
   - het aantal nog actieve leveringen onder de `wipLimit` van het product blijft (standaard **1**,
     dus normaal gesproken staat er maar één story tegelijk open bij de Software Factory);
   - de daglimiet (`maxStoriesPerCycle`, gemaximeerd op 3) nog niet bereikt is;
   - er voor dit product geen enkele levering in `ERROR` staat en geen open access-tokenactie is —
     zo'n blokkade stopt *alle* nieuwe leveringen voor dat product, niet alleen de betrokken story.

   Met de standaard `wipLimit` van 1 betekent dit dus: ook al accepteert een cyclus 3 kandidaten
   tegelijk, ze worden na elkaar geleverd — pas als de vorige story bij de Software Factory
   volledig `DONE` is (en geëvalueerd), krijgt de volgende kandidaat een kans. Een hogere `wipLimit`
   staat wél meerdere gelijktijdig lopende stories toe.
2. De story krijgt de titel en omschrijving van de kandidaat mee, plus een verwijzing naar het
   onderbouwende dossier (workspace-commit en pad), het doelrepository en het te gebruiken
   AI-provider/model. De Software Factory antwoordt met een eigen storysleutel (`storyKey`); de
   levering krijgt in Product Factory status `DELIVERED` en de kandidaat status `PUBLISHED`.

## 5. Hoe de voortgang bij de Software Factory gevolgd wordt

Zodra een story een `storyKey` heeft, haalt de coordinator elke minuut de actuele status op
(`GET /api/integrations/v1/stories/{key}`) en leidt daaruit af:

- de huidige fase (`storyPhase`, bijvoorbeeld een refine-, plan-, develop-, review-, test-,
  summary- of documentation-fase bij de Software Factory) — zichtbaar in het dashboard als
  "fase";
- of er een subtaak op de eigenaar wacht (`awaiting-human`);
- of er ergens een fout is gemeld (op de story of op een subtaak, of een mislukte deploy);
- of alles klaar is: de story zelf staat op `done`/`finished`/`closed`, óf alle subtaken staan in
  een afgeronde fase (o.a. `review-approved`, `test-approved`, `merge-approved`, `deploy-approved`).

Op basis daarvan krijgt de levering in Product Factory status `RUNNING`, `WAITING_FOR_ANSWER`,
`ERROR` of `DONE`. Zodra een story `DONE` is, wordt er automatisch een korte evaluatie
(`product-memory/<storyKey>-evaluation.md`) naar de workspace gepubliceerd — dat productgeheugen
gebruikt de *volgende* cyclus weer als context, zodat er geen dubbel werk wordt voorgesteld.

## 6. Hoe vragen van de Software Factory beantwoord worden

Zolang een story nog niet klaar of fout is, kijkt de coordinator bij elke poll ook naar
`agentQuestions` in de storydetails: vragen die de Software Factory-agents stellen (op de story
zelf of op een subtaak) tijdens het uitvoeren van een fase.

- Elke nieuwe vraag wordt vastgelegd en direct doorgestuurd naar een eigen **question-resolver**
  agent: de autonome "product owner" voor dat product, met de productmissie, guardrails en de
  storycontext. Die kiest:
  - **`ANSWER`** — voor vrijwel alles: product-, UX-, technische, test-, juridische of andere
    uitvoeringskeuzes. Hij kiest zelf een veilig, omkeerbaar alternatief en stuurt dat antwoord
    terug naar de Software Factory (`POST /api/integrations/v1/stories/{key}/answers`), zodat de
    uitvoering vanzelf doorgaat. De vraag wordt dus **niet** aan jou voorgelegd.
  - **`HUMAN_ACTION`**, categorie `ACCESS_TOKEN` — alleen wanneer er echt een extern access token,
    API-key of OAuth-secret nodig is dat een agent niet zelf kan aanmaken. Dan wordt de levering op
    `WAITING_HUMAN` gezet (die story pauzeert dus) en verschijnt de vraag in het dashboard onder
    "Benodigde access tokens", met titel, categorie en reden. Zie ook de eerdere toelichting in dit
    gesprek over hoe dat mechanisme werkt: jij zet het token zelf ergens neer (bijvoorbeeld een
    secret/omgevingsvariabele) en bevestigt in het dashboard *dat* het geregeld is — het token zelf
    wordt nooit in het dashboard ingevoerd. Na bevestiging gaat de betreffende story weer verder.
- Zolang er voor een product een open access-tokenactie is, worden er geen nieuwe stories geleverd
  (zie punt 4) — het proces wacht dus liever dan dat het blind doorgaat.

## 7. Wat je in het dashboard ziet

Het (Google-beveiligde) Flutter-dashboard ververst zichzelf elke 5 seconden en toont, van boven naar
beneden:

- **Metrics**: aantal producten, interne storykandidaten, workspace-publicaties, cycli en
  Software Factory-stories. Deze tegels tonen altijd het *totaal*, ook wanneer de lijst eronder is
  ingekort.
- **Producten**: per product status (`draft`/`active`/gepauzeerd), ontwikkelmodus
  (`manual`/`autonomous`/`observe-only`), project- en repositorykoppeling, `maxStoriesPerCycle` en
  `wipLimit`, met knoppen om een cyclus of shadow-iteratie te starten of het product te
  pauzeren/hervatten. Gepauzeerd betekent: geen nieuwe agents, stories of automatische antwoorden
  meer, maar extern lopend werk wordt niet afgebroken.
- **Productcycli en onderzoekssessies**: elke cyclus met status, huidige rol (als hij nog loopt),
  starttijd, doorlooptijd, aantal kandidaten en criticusoordeel. De starttijd komt uit `startedAt`,
  of uit `createdAt` zolang de cyclus nog niet gestart is. De doorlooptijd is het verschil tussen
  start en afronding, compact weergegeven als bijvoorbeeld `2u 13m`, `4m 12s` of `35s`; loopt de
  cyclus nog, dan staat er `loopt nog: <tijd sinds start>` en loopt die waarde mee met de
  auto-refresh. Een nog niet gestarte cyclus toont geen doorlooptijd. Datum en tijd staan in de
  lokale tijdzone van de browser als `dd-MM-yyyy HH:mm`, nooit als ruwe ISO-string.
  Elke iteratierij toont daarnaast exact één van twee dingen, puur afgeleid uit bestaande velden
  (geen nieuwe databron): iteraties die nog lopen of in de wachtrij staan (`status` QUEUED/RUNNING)
  tonen een neutrale voortgangsindicator (`IterationProgressIndicator`) in plaats van een badge;
  elke andere status toont een vaste classificatiebadge — `onderzoek-onvoldoende`,
  `guardrail-conflict`, `richting-gekozen`, `richting-verworpen` of `niet-classificeerbaar` —
  afgeleid uit de velden `status`, `criticVerdict` en `errorMessage`. Elke onvoorziene of
  ontbrekende statuswaarde (inclusief een tijdens uitvoering afgebroken iteratie, waarvoor geen
  apart CANCELLED-statusveld bestaat) mapt naar `niet-classificeerbaar`. De badge toont de
  classificatie zowel als zichtbare tekst als via een Semantics-label, dus niet uitsluitend via
  kleur, en elk van de vijf kleurenparen haalt WCAG 2.1 AA-contrast (≥ 4.5:1). De
  voortgangsindicator gebruikt `Semantics(liveRegion: true)` (het Flutter-web-equivalent van
  `aria-live="polite"`). De classificatiebadge zelf is met muis én toetsenbord (Tab, Enter/Spatie)
  activeerbaar en klapt dan direct onder de badge een inline scope-disclaimerpaneel open met de
  vaste tekst "Dit toont wat de uitkomst was, niet waarom." — geen `AlertDialog`/`showDialog`,
  geen focus-trap, `Semantics(expanded: ...)` volgt de open/dicht-status. Nogmaals activeren of
  Escape klapt het paneel weer in en herstelt de focus op de badge; het paneel bevat geen link
  naar een externe iteratielog-route. Aanklikken van de rij buiten de badge opent nog steeds een
  detailscherm met de opdracht, alle vijf agentstappen (status, start-/eindtijd, foutmelding), het
  volledige gepubliceerde dossier en per rol (Onderzoeker, Product owner, UX-ontwerp, Story writer,
  Criticus) een leesbare samenvatting van de bekende tekstvelden (bv. `summary`, `findings`,
  `decisions`/`rationale`, `steps`, `candidates`, `issues`) — dit scherm ververst zichzelf elke 3
  seconden zolang de cyclus loopt. De leesbare samenvatting verschijnt direct zodra de roltegel
  wordt uitgeklapt (geen extra klik) en laat lege/`null`-velden weg. Staat er een leesbare
  samenvatting, dan staat de bijbehorende ruwe JSON-output niet meer direct zichtbaar ernaast, maar
  achter een geneste, standaard ingeklapte toggle met zichtbaar label 'Toon technische details'
  (`TechnicalDetailsToggle`, `dashboard-frontend/lib/main.dart`) — onafhankelijk bedienbaar van de
  in-/uitklapstatus van de roltegel zelf, met muis én toetsenbord (Tab, Enter/Spatie), en met een
  `Semantics(expanded: ...)` (het Flutter-web-equivalent van `aria-expanded`) die de open/dicht-status
  communiceert. Matcht het rolresultaat geen van de vijf rolspecifieke schema's (bv. omdat een rol
  een vereenvoudigd schema oplevert dat niet overeenkomt met het rijke schema per rol), dan valt de
  weergave terug op een generieke leesbare weergave (`_readableGenericFields`,
  `dashboard-frontend/lib/main.dart`): elk top-level veld dat een string is, of een lijst die
  uitsluitend uit primitieve waarden (tekst, getal, boolean) bestaat, verschijnt alsnog als
  gelabelde leesbare regel — het label komt via dezelfde `humanizeFieldKey`-functie die ook de
  vaste labels voor `findings`, `decision`, `story`, `verdict` en `reason` levert — en ook dan
  verdwijnt de ruwe JSON achter de toggle 'Toon technische details'. Bevat het rolresultaat op het
  hoogste niveau uitsluitend geneste objecten, arrays van objecten, of lijsten met gemengde/
  niet-primitieve elementen (dus geen enkel top-level veld dat aan bovenstaande voorwaarde
  voldoet), of is de structuur niet decodeerbaar/onherkend (`readableFields` leeg), dan toont het
  dialoog uitsluitend de bestaande ruwe JSON direct zichtbaar, zonder toggle en zonder te crashen.
  Retry-pogingen (`artifactType` met `-2`/`-3`-suffix) gebruiken dezelfde leesbare weergave als de
  eerste poging. Dit detailscherm (`IterationSessionDialog`,
  `dashboard-frontend/lib/main.dart`) toont bovenaan dezelfde `ClassificationBadge` (zelfde
  `classifyIterationOutcome`-uitkomst, zelfde badge-tekst en `kClassificationColors`-kleurenpaar) als
  de lijstkaart-rij, in plaats van de ruwe backend-statuswaarde (bv. 'NEEDS_REVISION') als losse
  `Chip`; de badge is er het eerste focusbare element, vóór Voortgang/agentresultaten/
  workspace-publicaties, en met toetsenbord (Tab, Enter/Spatie) op dezelfde manier bedienbaar als op
  de lijstkaart. De weergave van de workspace-publicatie/PR-referentie
  (`workspacePullRequestUrl`/`workspaceCommitSha`) in dit detailscherm is door de badge-/
  indicator-toevoeging ongewijzigd gebleven. Heeft de iteratie zelf `status == 'FAILED'`, dan toont
  dit detailscherm (`IterationSessionDialog`, `dashboard-frontend/lib/main.dart`) direct onder het
  'Opdracht'-blok een apart 'Foutreden'-blok met de inhoud van `iteration['errorMessage']`, of exact
  de tekst 'Geen foutreden beschikbaar' als dat veld leeg of `null` is; bij elke andere status blijft
  dit blok volledig verborgen. Net als de classificatiebadge communiceert dit blok zijn inhoud ook
  via een expliciet `Semantics`-label (`'Foutreden: <tekst>'`), zodat het als afzonderlijk
  betekenisvol blok wordt aangekondigd door schermlezers. Heeft de iteratie in plaats daarvan
  `status == 'NEEDS_REVISION'` of `status == 'REJECTED'`, dan toont hetzelfde detailscherm direct
  onder het 'Opdracht'-blok en vóór de roltegels-sectie een 'Reden'-blok, visueel gelijk aan het
  'Foutreden'-blok (titel + `SelectableText`). Is er onder de `artifacts` van deze iteratie een
  criticus-artefact (`artifactType` `critic`, of bij een retry `critic-2`/`critic-3`/…, waarbij het
  meest recente/hoogste-suffix-artefact telt), dan bevat het blok leesbare lopende tekst opgebouwd
  uit `overallVerdict`, `summary` en `requiredChanges[]` van dat artefact (schema `critic` uit
  `ShadowSchemas.kt`) — nooit rauwe JSON-notatie. Ontbreekt zo'n criticus-artefact voor deze
  iteratie, dan toont het blok in plaats daarvan exact de tekst 'Criticus-oordeel ontbreekt voor
  deze cyclus'. Ook dit blok heeft een expliciet `Semantics`-label (`'Reden: <tekst>'`). Bij elke
  andere status (o.a. `ACCEPTED`, `PENDING`, `QUEUED`, `RUNNING`) blijft het Reden-blok volledig
  verborgen; het bestaande 'Foutreden'-blok en de standaard ingeklapte criticus-roltegel met
  volledig artefact blijven ongewijzigd zichtbaar/functioneel.
- **Software Factory-stories**: elke levering met storysleutel, titel, status (`DELIVERING`,
  `DELIVERED`, `RUNNING`, `WAITING_FOR_ANSWER`, `WAITING_HUMAN`, `DONE`, `ERROR`) en de laatst
  bekende Software Factory-fase.
- **Benodigde access tokens**: alleen zichtbaar zodra er iets openstaat; met een knop "Gereed
  melden" per item.
- **Storykandidaten**: alle interne kandidaten (ook afgewezen of nog niet geleverde) met status.
  Zit een kandidaat vast door een onopgeloste `dependsOn`-verwijzing (`blocked == true` met een
  niet-lege `blockedReason`), dan toont de kaart direct — geen extra klik nodig — onder de titel een
  label met icoon en de tekst "Geblokkeerd: <reden>", in hetzelfde WCAG AA-geverifieerde
  kleurenpaar (`kGuardrailConflict`, `classification.dart`) als de classificatiebadges hierboven en
  opvraagbaar via de semantics-tree van de kaart. Zonder blokkade of reden blijft de kaart
  ongewijzigd; het label wordt uitsluitend uit de al opgehaalde `blocked`/`blockedReason`-velden
  gerenderd, zonder extra netwerkverzoek (`_buildStoryQueueSections`,
  `dashboard-frontend/lib/main.dart`).
- **Workspace**: alle gepubliceerde artefacten (dossiers, evaluaties), aanklikbaar om de volledige
  inhoud te lezen.

Alle lijsten hierboven (producten, productcycli, Software Factory-stories, access tokens, elke
subsectie van de storykandidaten en workspace-publicaties) tonen standaard **5 items**. Staat er meer
klaar, dan verschijnt eronder een knop **'Meer (nog N)'** die er telkens **10** bij toont; die knop
verdwijnt zodra alles zichtbaar is. Elke sectie heeft een eigen, onafhankelijke teller die de
auto-refresh overleeft: een uitgeklapte lijst blijft uitgeklapt en nieuwe items verschijnen bovenaan.
Lijsten met een bruikbaar tijdstempel (cycli, leveringen, storykandidaten, access tokens) staan
gesorteerd op nieuwste eerst; producten houden hun volgorde op slug en workspace-publicaties — die
geen tijdstempel hebben — de volgorde van de backend. Het inkorten gebeurt volledig in de frontend op
de al opgehaalde data; de backend-endpoints kennen geen paginering.

## 8. Samenvatting van de belangrijkste regel

Eén cyclus kan tot drie storykandidaten opleveren, maar Product Factory levert ze **niet** allemaal
tegelijk aan de Software Factory: met de standaardinstelling (`wipLimit = 1`) gaat dat strikt na
elkaar, pas nadat de vorige story volledig is afgehandeld. Pas als *al* het werk van een cyclus
(alle leveringen) klaar en geëvalueerd is, mag er automatisch een geheel nieuwe cyclus starten —
en dat gebeurt hooguit één keer per dag, rond het geconfigureerde tijdstip.
