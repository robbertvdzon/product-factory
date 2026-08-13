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

Bij `REVISE` (en minimaal één *blokkerende* bevinding) past STORY_WRITER alleen de geraakte
kandidaten/velden aan, waarna CRITIC opnieuw beoordeelt — tot maximaal 3 inhoudelijke pogingen.
Kapotte of redactionele story-/criticoutput krijgt daarvoor een afzonderlijke technische
`OUTPUT_REPAIR`, zodat modelslordigheid geen inhoudelijke poging verbruikt. Bij maximaal twee lokale,
oplosbare resterende blockers mag één begrensde laatste reparatie volgen. Elke
stap wordt vastgelegd als een `agent_run` en is per stap zichtbaar in het dashboard (status,
starttijd, eindtijd, foutmelding).

**Harde autonomieregel** (alleen relevant in `autonomous`-modus): geen enkele story of
acceptatiecriterium mag een handmatige actie van de eigenaar vereisen — geen handmatige test,
productbesluit, accountaanmaak, betaling, DNS-wijziging of apparaatcontrole. De enige toegestane
uitzondering is een concreet, onvermijdelijk extern **access token / API-key / OAuth-secret**. Als
STORY_WRITER dat toch voorstelt, forceert de runtime zelf een `REVISE` (los van wat CRITIC zegt) en
moet de kandidaat herschreven worden.

## 3. Uitkomst van een cyclus

- Acceptatie gebeurt per kandidaat. Iedere onafhankelijke kandidaat met `ACCEPT` kan worden
  gepubliceerd, ook als een andere kandidaat uit dezelfde batch nog `REVISE` heeft. Een exact reeds
  geleverd resultaat wordt niet dubbel gepubliceerd en geeft de cyclusstatus `NO_CHANGE`, niet
  `REJECTED`. Zonder leverbare kandidaat eindigt de cyclus als `NEEDS_REVISION` of `REJECTED`.
- Een cyclus met `NEEDS_REVISION` kan via API of dashboard worden hervat. De nieuwe cyclus verwijst
  naar de broncyclus, hergebruikt het gevalideerde onderzoek, productbesluit en UX-ontwerp en start
  bij de gerichte storyrevisie.
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

Het (Google-beveiligde) Flutter-dashboard heeft een productoverzicht en een secundaire beheerweergave.
Beide gebruiken dezelfde dashboardsessie en dezelfde elke 5 seconden ververste gegevens; Beheer heeft
geen eigen URL of gegevensbron. Het productoverzicht bevat bovenaan de focusbare link `Beheer`, met
linksemantiek en zichtbare toetsenbordfocus, en toont daarna van boven naar beneden:

- **Metrics**: aantal producten, interne storykandidaten, workspace-publicaties, cycli en
  Software Factory-stories. Een succesvol geladen tegel toont altijd het *totaal*, ook wanneer de
  lijst eronder is ingekort. Kandidaten, cycli en Software Factory-stories laden afzonderlijk; hun
  tegel toont tijdens laden `Laden…` en bij een fout `Niet beschikbaar` in plaats van een
  misleidende nul.
- **Producten**: per product status (`draft`/`active`/gepauzeerd), ontwikkelmodus
  (`manual`/`autonomous`/`observe-only`), met knoppen om een cyclus of shadow-iteratie te starten,
  het product te pauzeren/hervatten, of de instellingen te openen. "Start productcyclus nu" (de
  cyclus-CTA, alleen zichtbaar bij een actief, autonoom product met workspace-eigenaarschap
  `product-factory`) staat als losstaande, visueel dominante knop (`StartCycleButton`) op een
  eigen rij, boven en met extra ruimte gescheiden van de secundaire knoppenrij
  (Pauzeren/Hervatten, Instellingen, Start overleg); onderscheidend door een eigen rand en
  kleurenpaar met WCAG AA-contrast (≥4.5:1) en een zichtbare focusring, niet uitsluitend door
  kleur. Gedrag, enabling-conditie, icoon en tekst van de knop zijn ongewijzigd. Gepauzeerd
  betekent: geen nieuwe agents, stories of automatische antwoorden meer, maar extern lopend werk
  wordt niet afgebroken.
  Missie, Software Factory-projectkoppeling, doelrepository, workspace, `maxStoriesPerCycle`,
  `wipLimit`, AI-provider/model en cyclustijden staan niet meer los op de kaart, maar in het
  Instellingen-scherm (`ProductSettingsDialog`) achter de Instellingen-knop: missie, project en
  workspace als alleen-lezen tekst met een toelichting dat ze aan de Software Factory-integratie
  gekoppeld en dus niet bewerkbaar zijn, de overige velden — inclusief de doelrepository — als
  bewerkbaar en opslaanbaar veld. Het scherm opent met focus binnen de dialoog, houdt de tab-focus
  binnen de dialoog (focus-trap) en sluit met Escape, waarbij de focus terugkeert naar de
  Instellingen-knop.
- **Productcycli en onderzoekssessies**: elke cyclus staat in een compacte, zelfstandig
  uitklapbare kaart met status, huidige rol (als hij nog loopt), starttijd, doorlooptijd, aantal
  kandidaten, aantal leverbare kandidaten, revisierondes en, wanneer van toepassing, uitkomstreden
  en criticusoordeel. De gesloten kaart toont daarnaast afzonderlijke aantallen voor interne
  kandidaten en Software Factory-leveringen uit de actuele geladen dashboardgegevens. Deze nieuwe
  aantallen zijn als geladen gegevens herkenbaar en staan los van de bestaande backendcycluswaarden.
  De starttijd komt uit `startedAt`,
  of uit `createdAt` zolang de cyclus nog niet gestart is. De doorlooptijd is het verschil tussen
  start en afronding, compact weergegeven als bijvoorbeeld `2u 13m`, `4m 12s` of `35s`; loopt de
  cyclus nog, dan staat er `loopt nog: <tijd sinds start>` en loopt die waarde mee met de
  auto-refresh. Een nog niet gestarte cyclus toont geen doorlooptijd. Datum en tijd staan in de
  lokale tijdzone van de browser als `dd-MM-yyyy HH:mm`, nooit als ruwe ISO-string.

  De button `Toon opbrengst` opent uitsluitend binnen dezelfde kaart twee groepen: `Interne
  kandidaten` en `Software Factory-leveringen`. Iedere gekoppelde titel staat daar samen met zijn
  tekstuele kandidaat- of leveringsstatus; een lege groep blijft expliciet herkenbaar als een leeg
  resultaat van de geladen gegevens. De button verandert bij openen in `Verberg opbrengst`, werkt
  met muis, Enter en Spatie, bevat het cyclusnummer in zijn toegankelijke naam en houdt focus bij
  openen en sluiten. De bestaande beslisbronbutton blijft los daarvan het detailscherm openen.
  Meerdere kaarten kunnen onafhankelijk openstaan en behouden hun toestand tijdens de normale
  auto-refresh zolang de betreffende cyclus geladen blijft.

  Koppeling gebeurt uitsluitend op een exacte combinatie van product en cyclusnummer voor een
  kandidaat, of product en cyclus-id voor een levering. De frontend gebruikt hiervoor alle geladen
  cycli, ook degene die nog achter de 5/+10-lijstbeperking staan. Ontbrekende, ongeldige,
  kruisproduct- en dubbelzinnige relaties worden niet op basis van titel, positie of
  waarschijnlijkheid gegokt en tellen ieder precies eenmaal als niet koppelbaar. Zodra cycli,
  kandidaten én leveringen succesvol zijn geladen, staat bij een positief aantal één melding buiten
  de kaarten: `Niet aan een cyclus te koppelen in geladen gegevens: <aantal>`; bij nul staat er geen
  melding.

  De laadstatus van cycli, kandidaten en leveringen blijft afzonderlijk zichtbaar. Per kaart toont
  een nog ladende of mislukte opbrengstbron geen nul, maar `laden…` of `niet beschikbaar`; een
  volledig niet-koppelbaar totaal volgt pas wanneer alle bronnen beschikbaar zijn. De globale
  Software Factory-lijst in Beheer toont eveneens haar eigen laad- of foutmelding. De epic-roadmap
  en de storywachtrij in Beheer melden dat ze onvolledig zijn totdat zowel kandidaten als
  leveringen beschikbaar zijn.

  Iteraties die nog lopen of in de wachtrij staan (`status` QUEUED/RUNNING)
  tonen een neutrale voortgangsindicator (`IterationProgressIndicator`) in plaats van een badge;
  elke andere status zonder expliciet beslisrecord toont een vaste, afgeleide
  classificatiebadge — `onderzoek-onvoldoende`, `technische fout`, `richting-gekozen`,
  `richting-verworpen` of `niet-classificeerbaar` —
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
  naar een externe iteratielog-route.
  Elke cyclusregel bevat daarnaast precies één beslisbronbutton. Bij een gekoppeld expliciet
  handmatig-annuleringsrecord toont die zichtbaar én toegankelijk `Beslisbron: Mens` en
  `Reden: Handmatig geannuleerd`. Het record heeft voorrang op de velden waaruit historische
  provenance wordt afgeleid; daarom blijven de afgeleide classificatiebadge en uitkomstreden voor
  die cyclus verborgen. De bestaande foutmelding mag in het detail blijven staan, maar wordt niet
  als beslisbron of vervangende verklaring gebruikt.
  De bestaande annuleeractie accepteert nog steeds een optionele vrije reden, maar bewaart die
  uitsluitend als bestaande foutmelding. Alleen als de overgang van QUEUED/RUNNING naar FAILED
  slaagt, wordt in dezelfde transactie maximaal één privacy-minimaal beslisrecord opgeslagen met
  `iterationId`, `actorType = HUMAN`, `mechanism = MANUAL_CANCELLATION`,
  `reasonCode = MANUALLY_CANCELLED` en `decidedAt`. Die laatste waarde is exact gelijk aan
  `completedAt`; een conflict of rollback laat geen los record of halve statusovergang achter.
  Historische cycli krijgen geen backfill. Het record bevat geen naam, e-mailadres, account-id,
  aangeleverde reden of andere vrije tekst.
  Zonder gekoppeld record toont de button `Beslisbron: Evaluatie-agent (Afgeleid)`,
  `Beslisbron: Technische fout (Afgeleid)` of `Beslisbron: Onbekend (Afgeleid)`. De bron wordt
  conservatief afgeleid uit `criticVerdict`, `status` en `errorMessage`: de bewezen paren
  `ACCEPT`/`ACCEPTED`, `REVISE`/`NEEDS_REVISION` en `REJECT`/`REJECTED` wijzen naar de
  evaluatie-agent; alleen `FAILED` zonder verdict en met een
  niet-lege foutmelding wijst naar een technische fout; alle overige combinaties zijn onbekend.
  Ontbrekende, lege en alleen uit witruimte bestaande waarden gelden als afwezig. Omringende
  witruimte wordt genegeerd, maar afwijkend hoofdlettergebruik en onbekende of tegenstrijdige
  waarden vallen terug op `Onbekend`. Ook deze onbekende fallback blijft zichtbaar en toegankelijk
  als `Afgeleid` herkenbaar.
  De native button opent met muis, Enter of Spatie het detailscherm van precies de gekozen cyclus.
  De rij zelf is niet meer klikbaar en heeft geen navigatie-chevron; zo is er geen tweede of
  geneste detailbediening naast de afzonderlijke annuleeractie. Na sluiten via de zichtbare
  sluitactie of Escape keert de focus terug naar de gebruikte beslisbronbutton. De beslisbron toont
  in het overzicht geen ruwe foutmelding, prompt, log of artefactinhoud, en het openen en sluiten
  doet uitsluitend de bestaande leesverzoeken. Voor een expliciet handmatig-annuleringsrecord
  toont het detailscherm dezelfde bron en reden, aangevuld met `Mechanisme: Handmatige annulering`
  en het lokale beslissingstijdstip uit `decidedAt`; de afgeleide badge en uitkomstreden blijven
  ook daar verborgen. Voor cycli zonder record toont het detail de bron met `(Afgeleid)`.
  De titel van het detailscherm toont het user-facing cyclusnummer (met het interne iteratie-id als
  fallback) en het scherm bevat de opdracht, alle vijf agentstappen (status, start-/eindtijd,
  foutmelding), het
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
  weergave volledig terug op een generieke leesbare weergave (`_readableGenericFields`,
  `dashboard-frontend/lib/main.dart`): elk top-level veld dat een string is, of een lijst die
  uitsluitend uit primitieve waarden (tekst, getal, boolean) bestaat, verschijnt alsnog als
  gelabelde leesbare regel — het label komt via dezelfde `humanizeFieldKey`-functie die ook de
  vaste labels voor `findings`, `decision`, `story`, `verdict` en `reason` levert — en ook dan
  verdwijnt de ruwe JSON achter de toggle 'Toon technische details'. Deze generieke fallback
  (`_readableGenericFieldEntry`) wordt sinds product-138 ook per afzonderlijk top-level veld
  toegepast binnen een wél herkende rol: matcht het content_json van die rol niet het verwachte
  type voor één specifiek veld (bv. `findings` als losse string in plaats van een objectenlijst bij
  de Onderzoeker-rol), dan levert de rolspecifieke branch (`_roleSpecificFieldEntries`) alleen voor
  dát veld geen widgets op, en verschijnt het alsnog via de generieke regel — de overige, wél
  conforme velden van diezelfde rol blijven ongewijzigd via hun rolspecifieke weergave zichtbaar.
  Levert noch de rolspecifieke branch, noch de per-veld generieke fallback iets op voor een
  top-level veld (bv. geneste objecten of arrays van objecten binnen een niet-conform artefact),
  dan blijft dat veld ongerenderd, zonder nieuwe generieke rendering voor geneste structuren. Bevat
  het rolresultaat op het hoogste niveau uitsluitend geneste objecten, arrays van objecten, of
  lijsten met gemengde/niet-primitieve elementen (dus geen enkel top-level veld dat aan
  bovenstaande voorwaarde voldoet), of is de structuur niet decodeerbaar/onherkend
  (`readableFields` leeg), dan toont het dialoog uitsluitend de bestaande ruwe JSON direct
  zichtbaar, zonder toggle en zonder te crashen.
  Retry-pogingen (`artifactType` met `-2`/`-3`-suffix) gebruiken dezelfde leesbare weergave als de
  eerste poging. Voor een cyclus zonder expliciet beslisrecord toont dit detailscherm
  (`IterationSessionDialog`, `dashboard-frontend/lib/main.dart`) bovenaan dezelfde
  `ClassificationBadge` (zelfde
  `classifyIterationOutcome`-uitkomst, zelfde badge-tekst en `kClassificationColors`-kleurenpaar) als
  de lijstkaart-rij, in plaats van de ruwe backend-statuswaarde (bv. 'NEEDS_REVISION') als losse
  `Chip`; de badge is er het eerste focusbare element, vóór Voortgang/agentresultaten/
  workspace-publicaties, en met toetsenbord (Tab, Enter/Spatie) op dezelfde manier bedienbaar als op
  de lijstkaart. Voor een expliciete handmatige annulering ontbreekt deze afgeleide badge zoals
  hierboven beschreven. De weergave van de workspace-publicatie/PR-referentie
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
  iteratie, dan hangt de getoonde tekst af van `iteration['criticVerdict']`. Is `criticVerdict` wél
  gezet (niet `null`), dan toont het blok een tekst die de letterlijke verdict-waarde expliciet
  benoemt samen met een expliciete melding dat er geen onderliggend criticus-artefact beschikbaar
  is (bv. 'Criticusoordeel REVISE geregistreerd, maar geen onderliggend criticus-artefact
  beschikbaar.'), in plaats van de onvoorwaardelijke 'Criticus-oordeel ontbreekt'-tekst — dit
  voorkomt een tegenspraak met een elders in de UI getoonde criticus-badge, die uitsluitend op
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
  `REJECTED` en is `iteration['criticVerdict'] == 'ACCEPT'`
  (het guardrail-pad: alle door de criticus goedgekeurde kandidaten zijn alsnog geblokkeerd op
  duplicaat/guardrail, waardoor de cyclus niet doorgaat), dan wordt aan de criticus-tekst een
  extra, statische alinea toegevoegd met exact de tekst 'Let op: Alle voorgestelde kandidaten zijn
  geblokkeerd (duplicaat of guardrail), waardoor deze cyclus niet doorgaat ondanks een positief
  criticusoordeel.' — puur tekstueel (geen kleur/icoon), binnen dezelfde `Semantics`-scope. Voor
  alle overige `REJECTED`-/`NEEDS_REVISION`-combinaties (`criticVerdict != 'ACCEPT'`, incl. `null`)
  blijft het blok ongewijzigd zonder deze toelichtingszin. Ook dit blok heeft een expliciet
  `Semantics`-label (`'Reden: <tekst>'`). Bij elke
  andere status (o.a. `ACCEPTED`, `PENDING`, `QUEUED`, `RUNNING`) blijft het Reden-blok volledig
  verborgen; het bestaande 'Foutreden'-blok en de standaard ingeklapte criticus-roltegel met
  volledig artefact blijven ongewijzigd zichtbaar/functioneel.
- **Epic-roadmap**: per product een horizontale grafiek met compacte epic-kaarten in de berekende
  uitvoervolgorde en pijlen voor afhankelijkheden. Elke kaart toont roadmap-rank,
  prioriteitsscore (0–100), klant-rank, process-rank en of de epic uitvoerbaar, geblokkeerd of
  afgerond is. De klant-rank weegt voor 75% en de door het roadmapproces onderhouden process-rank
  voor 25%; afhankelijkheden zijn harde voorwaarden en gaan vóór de score. Een klik opent titel,
  beschrijving, status, gekoppelde stories en opleverrapporten. Daar kan de klant titel,
  beschrijving, eigen rank en dependencies aanpassen; de process-rank blijft alleen-lezen.
  Circulaire dependencies worden atomair geweigerd. Een nieuwe epic krijgt een korte titel van
  maximaal 80 tekens en wordt achteraan in beide ranglijsten toegevoegd. Afgehandelde
  onderzoeksvragen staan, wanneer aanwezig, direct onder de roadmap.
- **Roadmap-sessies**: status en samenvatting per sessie, met een actie om het gekoppelde verslag te
  bekijken wanneer dat beschikbaar is.
- **Overleggen**: status, initiatiefnemer en uitkomst per overleg, met de bestaande detailactie en,
  indien beschikbaar, een afzonderlijke notulenactie.
- **Benodigde access tokens**: alleen zichtbaar zodra er iets openstaat; met een knop "Gereed
  melden" per item.
- **Workspace**: alle gepubliceerde artefacten (dossiers, evaluaties), aanklikbaar om de volledige
  inhoud te lezen.

De twee globale lijsten staan niet meer op het productoverzicht. De link `Beheer` opent binnen
dezelfde dashboardpagina de beheerweergave. Daar staat `Terug naar overzicht` als eerste focusbare
actie; ook deze link heeft linksemantiek, zichtbare toetsenbordfocus en blijft tijdens auto-refresh
bruikbaar. Daarna toont Beheer, in deze volgorde:

- **Software Factory-stories**: elke levering, nieuwste eerst, met storysleutel of fallbacktekst,
  titel, product, status (`DELIVERING`, `DELIVERED`, `RUNNING`, `WAITING_FOR_ANSWER`,
  `WAITING_HUMAN`, `DONE`, `ERROR`) en de laatst bekende Software Factory-fase. Laden, fout, leeg en
  succes worden onafhankelijk van de kandidaatbron getoond.
- **Storywachtrij**: alle interne kandidaten exact eenmaal in Fout, Bezig, In wachtrij of Klaar, met
  de bestaande status, foutinformatie, leveringskoppeling en kandidaatdetailactie. Zit een kandidaat
  vast door een onopgeloste `dependsOn`-verwijzing (`blocked == true` met een niet-lege
  `blockedReason`), dan toont de kaart direct — geen extra klik nodig — onder de titel een label met
  icoon en de tekst "Geblokkeerd: <reden>", in hetzelfde WCAG AA-geverifieerde kleurenpaar
  (`kGuardrailConflict`, `classification.dart`) als de classificatiebadges hierboven en opvraagbaar
  via de semantics-tree van de kaart. Zonder blokkade of reden blijft de kaart ongewijzigd; het label
  wordt uitsluitend uit de al opgehaalde `blocked`/`blockedReason`-velden gerenderd, zonder extra
  netwerkverzoek (`_buildStoryQueueSections`, `dashboard-frontend/lib/main.dart`). De kandidaatbron
  houdt zijn eigen laad-, fout-, lege en successtatus. Als kandidaten geladen zijn terwijl leveringen
  nog laden of niet beschikbaar zijn, toont de wachtrij het geladen kandidaataantal en meldt zij dat
  de categorisering onvolledig is, in plaats van een compleet of leeg resultaat.

Beheer hergebruikt de al geladen kandidaat- en leveringsgegevens en de bestaande kandidaatrelatie.
Het wisselen van weergave koppelt, filtert, combineert of schrijft geen records en schrijft niets aan
een cyclus toe. De Software Factory-metriek, cyclusopbrengsten en niet-koppelbare-opbrengstmelding
blijven op het productoverzicht staan.

Alle lijsten op beide weergaven (producten, productcycli, afgehandelde onderzoeksvragen,
roadmap-sessies, overleggen, Software Factory-stories, access tokens, elke subsectie van de
storywachtrij en workspace-publicaties) tonen standaard **5 items**. Staat er meer klaar, dan verschijnt
eronder een knop **'Meer (nog N)'** die er telkens **10** bij toont; die knop verdwijnt zodra alles
zichtbaar is. Elke sectie heeft een eigen, onafhankelijke teller die de auto-refresh en het wisselen
tussen de weergaven overleeft: een uitgeklapte lijst blijft uitgeklapt en nieuwe items verschijnen
bovenaan.
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
