# Functioneel overzicht: wat doet een productcyclus precies

Dit document beschrijft, los van implementatiedetails, wat er functioneel gebeurt zodra een
productcyclus start: welke agents er achter elkaar draaien, wat ze opleveren, hoe storykandidaten
bij de Software Factory terechtkomen, hoe die uitvoering gevolgd en van antwoorden voorzien wordt,
en wat je daarvan in het dashboard terugziet. Voor de vertrouwensgrenzen rond shadow mode, zie
[shadow-mode.md](shadow-mode.md).

## 1. Wat start een cyclus

Een cyclus (in de code een "shadow-iteratie", ook voor autonome producten) start op twee manieren:

- **Handmatig** vanuit het dashboard: onder `Cyclus starten` opent de knop "Start productcyclus nu"
  eerst de benoemde dialoog `Productcyclus starten` voor het op dat moment actieve product. De
  productscope van een geopende dialoog blijft vaststaan. De knop is alleen
  beschikbaar bij de exacte waarden productstatus `active` en workspace-eigenaarschap
  `product-factory`. Bij blokkade staat de primaire reden direct bij de knop en opent `Bekijk
  productdetails` lokaal een veilige, alleen-lezen uitleg van beide startvoorwaarden. Een lopende
  cyclus en andere product- of cyclusgegevens veranderen deze startbeschikbaarheid niet. De runtime
  leidt de modus nog steeds af uit de productinstelling (`autonomous` blijft autonoom, iedere andere
  ontwikkelmodus wordt `shadow`).
- **Automatisch**, uitsluitend in `autonomous`-modus: de `AutonomousCoordinator` controleert elke
  minuut (`product-factory.autonomy.poll-delay`, standaard elke minuut) of het product aan de
  planning toe is (`iterationSchedule`, standaard `0 3 * * *`, dus rond 03:00). Een nieuwe
  automatische cyclus start alleen als **al het werk van de vorige cyclus volledig is afgerond**
  (geen actieve of nog-niet-geëvalueerde levering, geen lopende iteratie), er geen open fouten of
  openstaande access-tokenacties zijn, en er die dag nog geen automatische cyclus is gestart. Er
  loopt dus nooit meer dan één cyclus tegelijk per product.

Bij een handmatige start kiest de eigenaar tussen de canonieke autonome opdracht en een eigen
onderzoeksvraag. Eigen invoer wordt eenmaal aan begin en einde getrimd en moet daarna 1 tot en met
300 tekens bevatten; interne witruimte verandert niet. De dialoog toont vóór bevestiging product,
effectieve opdracht en de herkomst `Autonome standaard` of `Eigenaarinput`. Dezelfde effectieve
opdracht gaat via `POST /api/products/{slug}/cycles` naar opslag en uitvoering. Automatische starts
houden hun bestaande standaardfocus en krijgen geen handmatige herkomst. Iedere cyclus krijgt
daarnaast de eerder geaccepteerde iteraties en bestaande storykandidaten als context, zodat hij niet
opnieuw hetzelfde voorstelt.

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
geen eigen URL of gegevensbron. Het productoverzicht bevat de focusbare link `Beheer`, met
linksemantiek en zichtbare toetsenbordfocus.

Op maximaal 320 CSS-pixels staat direct onder `Productoverzicht` een compacte, alleen-lezen
buildidentiteit met `Omgeving` en `Revisie/build-ID`; `Uitgerold op` blijft daar weg. Daarna volgen
de actieve productkeuze en productnaam en de native, gelabelde keuze `Sectie kiezen`. Die bevat
zonder horizontaal scrollen exact `Overzicht`, `Productcycli`, `Stories`, `Roadmap`, `Bugs`, `Epics`,
`Testsessies` en `Overleggen`, in die volgorde. `Productcycli` is alleen het mobiele label van
`Productsessies`; de data en acties veranderen niet. Breder dan 320 CSS-pixels blijven de bestaande
horizontale sectienavigatie, labels en volgorde staan.

Binnen het compacte `Overzicht` is de zichtbare, semantische en DOM-volgorde productscope,
cyclusstart, eerdere cycli, gekoppelde stories en `Operationele samenvatting`. De bugsamenvatting,
productbeheer, benodigde access tokens, workspace-publicaties en dashboardacties blijven bereikbaar
en volgen na deze kerninhoud. `Productcycli` en `Stories` blijven ook afzonderlijk te kiezen en
hergebruiken dezelfde reeds geladen, exact op de actieve productslug gefilterde records. De bredere
variant behoudt haar bestaande compositie. Het productoverzicht bevat verder:

- **Synthetische acceptatiedata**: uitsluitend in de standing acceptatievariant staat op bredere
  viewports direct onder `Productoverzicht`, vóór navigatieacties, metrics en overige inhoud, een
  zichtbare en semantisch gegroepeerde melding. Op maximaal 320 CSS-pixels volgt zij na de
  operationele samenvatting en dus na de kerninhoud; zonder actief product volgt zij na de lege
  toestand en die samenvatting. Zij beschrijft de vaste catalogus als `1 actief`, `3 terminaal` en met
  `expliciet`, `afgeleid` en `onbekend` beslisgedrag. Dit is scenariodekking en geen dynamische
  telling van alle acceptatiegegevens. De melding ontbreekt in productie en PR-previews en blijft
  zonder kleur, bij 320 CSS-pixels en 200% tekstvergroting begrijpelijk.
- **Metrics**: het globale aantal geldige producten en workspace-publicaties, plus de aantallen
  interne storykandidaten, cycli en Software Factory-stories binnen het actieve product. Een
  succesvol geladen tegel toont het volledige scope-aantal, ook wanneer de lijst eronder is
  ingekort. Een afgeleide telling toont `Laden…` of `Niet beschikbaar` zolang een benodigde bron
  laadt of is mislukt, nooit een misleidende nul. Op maximaal 320 CSS-pixels bevat de standaard
  ingeklapte button `Operationele samenvatting` alle vijf tegels, ook zonder actief product. De
  button meldt haar toestand via `aria-expanded`; ingeklapte tegels staan niet in DOM-, focus- of
  toegankelijkheidsvolgorde. Op bredere viewports blijven de tegels direct zichtbaar.
- **Actief product**: de volledige productkaarten zijn vervangen door een compacte productkeuze en
  de blijvend zichtbare naam van exact één actief product. Alleen een niet-lege `Product.slug` is
  canoniek; vergelijking is exact en hoofdlettergevoelig, zonder trimmen, normaliseren of fallback
  naar naam, id of positie. Een exact unieke browservoorkeur wordt hersteld. Anders wordt het eerste
  geldige product in de ontvangen API-volgorde actief en wordt een aanwezige ongeldige voorkeur
  verwijderd. Bij geen geldige producten verschijnt een lege toestand zonder productacties.
  Wisselen bewaart alleen de gekozen slug, filtert de al geladen bronrecords en veroorzaakt geen
  request. De keuze is met het toetsenbord bedienbaar, heeft een toegankelijke naam, actuele waarde
  en zichtbare focus; de focus blijft na wisselen staan en een zichtbare live-status meldt de nieuwe
  scope en tellingen. Productscope en gekozen sectie blijven bij automatische verversing en het
  openen en sluiten van details behouden. In het compacte `Overzicht` volgen daarna steeds
  `Cyclus starten`, `Eerdere cycli` en `Gekoppelde stories`; op brede schermen blijven cycli en
  stories daarnaast in hun bestaande afzonderlijke secties staan.
- **Cyclus starten**: de bestaande visueel dominante `StartCycleButton` voor het actieve product.
  Eén presentatiemodel leest uitsluitend `status` en
  `workspaceOwnership`; alleen de exacte waarden `active` en `product-factory` activeren de knop.
  `draft`, `paused`, `archived` en `owner` zijn bekende maar onvoldoende waarden. Een ontbrekende
  sleutel, `null`, lege tekst, ander type of andere tekst is onbekend; vergelijking trimt of
  normaliseert niet en blijft hoofdlettergevoelig.

  Bij blokkade staat direct één primaire reden, in de vaste prioriteit onbekende metadata, product
  niet actief en workspace niet door Product Factory beheerd. Zijn beide voorwaarden onvervuld, dan
  meldt de weergave daarnaast dat nog één andere voorwaarde niet is vervuld. De uitgeschakelde knop
  en redencontext vormen één semanticsgroep. `Bekijk productdetails` is met Tab, Enter en Spatie
  bedienbaar en opent zonder netwerkverzoek een lokale, alleen-lezen dialoog. Die toont alleen
  dezelfde redencontext, veilige labels voor productstatus en workspacebeheer en de toepasselijke
  voorwaarden `Product moet actief zijn.` en `Workspace moet door Product Factory worden beheerd.`
  Ruwe waarden, technische identifiers, overige configuratie en muterende acties ontbreken; alleen
  sluiten is mogelijk. Sluiten of Escape herstelt de focus naar de opener. Bij beschikbaarheid zijn
  blokkademelding en detailactie afwezig. Een zichtbare of langlopende `RUNNING`-cyclus en alle
  overige gegevens beïnvloeden de uitkomst niet; de vijfsecondenrefresh blijft ongewijzigd.

  Een beschikbare knop opent de programmatisch benoemde dialoog `Productcyclus starten`, met
  `Autonome standaard` vooraf geselecteerd en daarnaast `Eigen onderzoeksvraag`. De autonome keuze
  toont en verstuurt exact de vaste autonome opdracht. Alleen de eigen keuze toont één gelabeld
  tekstveld; na trimmen aan begin en einde zijn 1–300 tekens toegestaan en toont ongeldige invoer
  een zichtbaar, veldgebonden foutbericht. Eerder ingevoerde tekst mag bij wisselen bewaard blijven,
  maar wordt bij de autonome keuze niet verstuurd. De samenvatting toont vóór bevestiging het vaste
  actieve product, de effectieve opdracht en precies één herkomstlabel. De dialoog houdt focus
  binnen zichzelf; Escape sluit en herstelt de focus naar dezelfde startknop.

  Tijdens starten zijn alle dialoogacties en invoer uitgeschakeld. Een mislukking houdt keuze en
  invoer vast en toont een toegankelijke, vaste foutstatus zonder vrije eigenaarinput; daarna kan
  opnieuw worden bevestigd. Succes sluit de dialoog, meldt de start en vernieuwt het overzicht.
  Servervalidatie en een product-row-lock zorgen dat onbekende of inconsistente requestcombinaties
  geen cyclus maken en dat gelijktijdige starts maximaal één cyclus en startgebeurtenis opleveren.
- **Eerdere cycli**: uitsluitend cycli waarvan `Iteration.productSlug` exact gelijk is aan de
  actieve `Product.slug`. De productslug bepaalt alleen scope en identificatie; de status bepaalt
  voor ieder product hetzelfde niet-uitklapbare presentatiemodel. De bestaande sortering,
  5/+10-lijstbeperking en auto-refresh blijven behouden. De geschiedenis vormt één benoemde
  semanticsgroep en iedere zichtbare cyclus vormt daarin een afzonderlijke semanticscontainer.

  `ACCEPTED`, `NEEDS_REVISION`, `REJECTED`, `NO_CHANGE` en `FAILED` verschijnen als terminale
  bewijsregel. Iedere regel toont zichtbaar en semantisch in dezelfde betekenisvolle volgorde
  `Datum`, `Cyclusuitkomst`, `Reden`, `Beslisbron`, `Gekoppelde opbrengst` en precies één
  alleen-lezen detailactie. Datum gebruikt de eerste parseerbare waarde van `startedAt` en
  `createdAt` en wordt in de lokale browsertijd als `dd-MM-yyyy HH:mm` getoond. De uitkomst en reden
  komen uit de bestaande veilige classificatie- en redenmapping; ontbrekende of onbekende waarden
  worden niet uit vrije tekst geschat.

  Een herkend expliciet beslisrecord heeft voorrang. Een geldig, aan dezelfde FAILED-cyclus
  gekoppeld handmatig-annuleringsrecord toont `Mens` en `Handmatig geannuleerd`. Zonder record
  krijgen alleen de bewezen paren `ACCEPT`/`ACCEPTED`, `REVISE`/`NEEDS_REVISION` en
  `REJECT`/`REJECTED` de bron `Evaluatie-agent (Afgeleid)`; `FAILED` zonder verdict en met aanwezige
  foutinformatie krijgt `Technische fout (Afgeleid)`. Ontbrekende, onbekende, tegenstrijdige of
  niet reconstrueerbare provenance toont uitsluitend `Onbekend`, zonder `(Afgeleid)`. Een aanwezig
  maar onbekend of aan een andere cyclus gekoppeld expliciet record activeert evenmin afleiding.
  Ruwe fouttekst wordt nooit als reden of beslisbron in het overzicht getoond.

  `Gekoppelde opbrengst` telt alleen uniek en exact op productslug plus cyclus-id gekoppelde
  Software Factory-leveringen, niet de interne kandidaten. Ongeldige, kruisproduct- en ambigue
  koppelingen tellen niet mee. Tijdens laden staat er `laden…`, bij bronfalen `niet beschikbaar` en
  alleen bij succesvol geladen gegevens een aantal. De native actie `Bekijk cyclusdetail` opent met
  muis, Enter of Spatie het bestaande detail van precies dezelfde cyclus. Haar toegankelijke naam
  bevat product, cyclus, datum en gebruikersgerichte uitkomst. Sluiten of Escape herstelt de focus
  naar dezelfde actie. De regel blijft zonder horizontale pagina-scroll bruikbaar op smalle en
  brede schermen en bij 200% tekstvergroting en toont geen ruwe foutgegevens, prompts, tokens,
  persoonsgegevens of artefactinhoud.

  Iedere terminale bewijsregel bevat daarnaast een subtiele, niet-interactieve verwijzing met
  uitsluitend `Omgeving` en `Revisie/build-ID`. Deze waarden komen uit exact dezelfde gevalideerde
  buildidentiteit als het volledige blok in Beheer. `Uitgerold op` staat bewust niet in de compacte
  bewijsregel. Actieve `QUEUED`-/`RUNNING`-kaarten en kaarten met een onbekende status krijgen geen
  omgevingsverwijzing.

  `QUEUED` en `RUNNING` verschijnen als veilige voortgangskaart met alleen de gesloten statusmapping,
  een bekende `currentRole` als huidige stap, voortgang die rechtstreeks uit de actieve status volgt
  en de neutrale detailactie `Bekijk cyclusdetail`. Ontbrekende of onbekende rollen worden
  weggelaten. Een ontbrekende of onbekende status toont alleen `Status: Onbekend` en dezelfde actie.
  Deze kaarten tonen geen cyclusuitkomst, reden, beslissing, beslisbron, classificatiebadge,
  afleidingsclaim, terminale opbrengst of ruwe fouttekst. Ook zij zijn niet uitklapbaar.

  De frontend groepeert alle geladen cycli binnen de actieve scope voordat de lijstbeperking wordt
  toegepast. Kandidaten koppelen alleen bij precies één exacte combinatie van productslug en het
  integerpaar `iterationSequenceNumber`/`sequenceNumber`; leveringen alleen bij precies één exacte
  combinatie van productslug en het stringpaar `iterationId`/`id`. Ontbrekende, ongeldige,
  kruisproduct- en ambigue relaties worden niet op basis van titel, positie of waarschijnlijkheid
  gegokt en verschijnen niet in de actieve productscope.

  De laadstatus van cycli, kandidaten en leveringen blijft afzonderlijk zichtbaar. Een
  productspecifieke Software Factory-lijst in Beheer wacht ook op kandidaten, omdat die de
  leveringsscope bepalen; `Alle producten` houdt de onafhankelijke globale leveringsbronstatus. De
  storywachtrij meldt dat zij onvolledig is totdat zowel kandidaten als leveringen beschikbaar zijn.
  Het compacte cyclusoverzicht gebruikt geen kandidaatbron. Renderen, scopewisselen en het openen of
  sluiten van detail voegen geen overzichtsrequest of muterende call toe; alleen het bestaande
  detailpad voert bij activering zijn bestaande leesverzoeken uit.

  De standing acceptatiecatalogus gebruikt dezelfde generieke paden: de `RUNNING`-fixture is een
  actieve kaart; de terminale fixtures tonen respectievelijk een expliciete menselijke handmatige
  annulering, `Evaluatie-agent (Afgeleid)` en uitsluitend `Onbekend`. Aan de `ACCEPTED`-fixture zijn
  exact twee voltooide synthetische leveringen gekoppeld; de onbekende `REJECTED`-fixture heeft geen
  kandidaat of levering. Daardoor tonen de regels respectievelijk twee en nul gekoppelde opbrengsten
  en verschijnen de twee leveringen ook in Beheer.

  De bestaande annuleeractie accepteert nog steeds een optionele vrije reden, maar bewaart die
  uitsluitend als bestaande foutmelding. Alleen als de overgang van QUEUED/RUNNING naar FAILED
  slaagt, wordt in dezelfde transactie maximaal één privacy-minimaal beslisrecord opgeslagen met
  `iterationId`, `actorType = HUMAN`, `mechanism = MANUAL_CANCELLATION`,
  `reasonCode = MANUALLY_CANCELLED` en `decidedAt`. Die laatste waarde is exact gelijk aan
  `completedAt`; een conflict of rollback laat geen los record of halve statusovergang achter.
  Historische cycli krijgen geen backfill. Het record bevat geen naam, e-mailadres, account-id,
  aangeleverde reden of andere vrije tekst.
  Voor een expliciet handmatig-annuleringsrecord
  toont het detailscherm dezelfde bron en reden, aangevuld met `Mechanisme: Handmatige annulering`
  en het lokale beslissingstijdstip uit `decidedAt`; de afgeleide badge en uitkomstreden blijven
  ook daar verborgen. Voor cycli zonder record toont het detail alleen een bewezen bron met
  `(Afgeleid)`; onbekende provenance blijft ook daar uitsluitend `Onbekend`.
  De titel van het detailscherm toont het user-facing cyclusnummer (met het interne iteratie-id als
  fallback) en het scherm bevat de opdracht. Voor een nieuwe handmatige cyclus staat daar direct
  onder de opgeslagen herkomst als `Autonome standaard` of `Eigenaarinput`. Historische,
  automatisch gestarte en hervatte cycli hebben geen handmatige herkomst; het dashboard leidt dan
  geen label af. De compacte cyclusregel toont opdracht en starthervkomst nooit. Daarna volgen alle
  vijf agentstappen (status, start-/eindtijd,
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
  eerste poging. Voor een terminale cyclus zonder expliciet beslisrecord toont dit detailscherm
  (`IterationSessionDialog`, `dashboard-frontend/lib/main.dart`) bovenaan een
  `ClassificationBadge` met dezelfde `classifyIterationOutcome`-uitkomst, badge-tekst en
  `kClassificationColors`-kleurenpaar als de uitkomstmapping van de terminale bewijsregel, in plaats
  van de ruwe backend-statuswaarde (bv. 'NEEDS_REVISION') als losse
  `Chip`; de badge is er het eerste focusbare element, vóór Voortgang/agentresultaten/
  workspace-publicaties, en met toetsenbord (Tab, Enter/Spatie) bedienbaar. Voor een expliciete
  handmatige annulering ontbreekt deze afgeleide badge zoals
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
- **Gekoppelde stories**: uitsluitend kandidaten met exact dezelfde niet-lege
  `StoryCandidate.productSlug` als het actieve product én een integer `iterationSequenceNumber` dat
  precies één `sequenceNumber` van een geladen cyclus in die scope aanwijst. Ontbrekende,
  anders getypeerde, kruisproduct- en ambigue relaties worden uitgesloten. Iedere actie opent het
  bestaande detail van precies de gekozen kandidaat. De lijst gebruikt de 5/+10-beperking en toont
  tijdens onvolledig laden geen definitieve nul.
- **Product beheren**: pauzeren/hervatten, instellingen, overleg en roadmap-sessie gelden voor het
  actieve product. Missie, Software Factory-projectkoppeling en workspace zijn in het
  Instellingen-scherm alleen-lezen; doelrepository, `maxStoriesPerCycle`, `wipLimit`,
  AI-provider/model en cyclustijden zijn daar bewerkbaar. De dialoog houdt toetsenbordfocus binnen
  het scherm en herstelt bij sluiten de focus naar de Instellingen-knop.
- **Toekomstvisie**: de nieuwste visieversie toont een north star, toekomstige ervaringen,
  screenshotachtige conceptschermen en capabilities verdeeld over `Nu`, `Hierna`, `Later` en
  `Horizon`. Technische onzekerheid verwijdert een ambitie niet: zij verschijnt als expliciete
  aanname met een voorgestelde haalbaarheidsproef. Alleen nieuw bewijs uit zulke proeven mag een
  volgende roadmap-sessie de horizon gemotiveerd laten aanpassen.
- **Epic-roadmap**: per product een horizontale grafiek met compacte epic-kaarten in de berekende
  uitvoervolgorde en pijlen voor afhankelijkheden. Elke kaart toont roadmap-rank,
  prioriteitsscore (0–100), klant-rank, process-rank en of de epic uitvoerbaar, geblokkeerd of
  afgerond is, plus de strategische horizon en of het om levering of discovery gaat. De klant-rank
  weegt voor 75% en de door het roadmapproces onderhouden process-rank
  voor 25%; afhankelijkheden zijn harde voorwaarden en gaan vóór de score. Een klik opent titel,
  beschrijving, status, gekoppelde stories en opleverrapporten. Daar kan de klant titel,
  beschrijving, eigen rank en dependencies aanpassen; de process-rank blijft alleen-lezen.
  Circulaire dependencies worden atomair geweigerd. Een nieuwe epic krijgt een korte titel van
  maximaal 80 tekens en wordt achteraan in beide ranglijsten toegevoegd. Afgehandelde
  onderzoeksvragen staan, wanneer aanwezig, direct onder de roadmap.
- **Roadmap-sessies**: een visionair concretiseert eerst vrij de bewust brede productmissie, een
  strateeg legt daarna de versieerbare eindvisie en backcast vast en een roadmapmanager maakt daar
  uitvoer- en discovery-epics van. Moeilijk, duur of nog niet ondersteund is geen reden om een
  ervaring te schrappen. Onzekerheid wordt een begrensde proef met verwacht bewijs en
  besliscriterium. Het dashboard toont status en samenvatting per sessie, met een actie om het
  gekoppelde verslag te bekijken wanneer dat beschikbaar is.
- **Overleggen**: status, initiatiefnemer en uitkomst per overleg, met de bestaande detailactie en,
  indien beschikbaar, een afzonderlijke notulenactie.
- **Benodigde access tokens**: alleen zichtbaar zodra er iets openstaat; met een knop "Gereed
  melden" per item.
- **Workspace**: alle gepubliceerde artefacten (dossiers, evaluaties), aanklikbaar om de volledige
  inhoud te lezen.

De twee globale lijsten staan niet op het productoverzicht. De link `Beheer` opent binnen dezelfde
dashboardpagina de beheerweergave met het actieve product als scope. Daar staat `Terug naar
overzicht` als eerste focusbare actie; ook deze link heeft linksemantiek, zichtbare toetsenbordfocus
en blijft tijdens auto-refresh bruikbaar. Direct na de titel `Beheer` en vóór `Beheerscope` staat
het alleen-lezen blok `Omgevingsidentiteit`, met in leesvolgorde `Omgeving`, `Revisie/build-ID` en
`Uitgerold op`. De gesloten omgevingsmapping toont uitsluitend `production`, `acceptance` en
`preview` als respectievelijk `Productie`, `Acceptatie` en `Preview`. Een revisie verschijnt alleen
als de invoer een volledige hexadecimale bronrevisie is en wordt dan tot de eerste twaalf tekens
ingekort. Een uitroltijd verschijnt alleen bij geldige ISO-8601-invoer met tijdzone, in de lokale
browsertijd als `dd-MM-yyyy HH:mm`. Ieder ontbrekend of ongeldig veld valt onafhankelijk terug op
`Onbekend`; het blok heeft geen laadstatus of bediening en gebruikt geen product- of cyclusdata.

De keuze `Beheerscope` biedt ieder geldig product én,
alleen in Beheer, `Alle producten`. Iedere lijstkop toont de gekozen productnaam of `Alle producten`.
Een productkeuze wordt ook het actieve product op het hoofdscherm en wordt lokaal bewaard;
`Alle producten` is tijdelijk en overschrijft die voorkeur niet. Een zichtbare live-status meldt
scope en tellingen zonder de focus te verplaatsen. Daarna toont Beheer, in deze volgorde:

- **Software Factory-stories**: leveringen binnen de gekozen scope, nieuwste eerst, met storysleutel
  of fallbacktekst, titel, product, status (`DELIVERING`, `DELIVERED`, `RUNNING`,
  `WAITING_FOR_ANSWER`, `WAITING_HUMAN`, `DONE`, `ERROR`) en de laatst bekende Software
  Factory-fase. Een afzonderlijke productscope leidt de levering uitsluitend via exact één kandidaat
  met hetzelfde integer `candidateId` en diens exacte `StoryCandidate.productSlug` af; de eventuele
  productslug op de levering is geen fallback. Daarom zijn dan kandidaat- én leveringsbron nodig.
  `Alle producten` behoudt de globale lijst en onafhankelijke leveringsbronstatus, inclusief records
  zonder eenduidige productrelatie.
- **Storywachtrij**: bij een afzonderlijk product alleen kandidaten met exact dezelfde
  `StoryCandidate.productSlug`; bij `Alle producten` alle interne kandidaten. Ze staan exact eenmaal
  in Fout, Bezig, In wachtrij of Klaar, met
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
Een scopewissel filtert alleen lokaal, doet geen request en herschrijft geen bronrecord. Alleen
`Alle producten` mag records zonder eenduidig bepaalbare productrelatie tonen.

Alle lijsten op beide weergaven (eerdere cycli, gekoppelde stories, afgehandelde onderzoeksvragen,
roadmap-sessies, overleggen, Software Factory-stories, access tokens, elke subsectie van de
storywachtrij en workspace-publicaties) tonen standaard **5 items**. Staat er meer klaar, dan verschijnt
eronder een knop **'Meer (nog N)'** die er telkens **10** bij toont; die knop verdwijnt zodra alles
zichtbaar is. Elke sectie heeft een eigen, onafhankelijke teller die de auto-refresh en het wisselen
tussen de weergaven overleeft: een uitgeklapte lijst blijft uitgeklapt en nieuwe items verschijnen
bovenaan.
Lijsten met een bruikbaar tijdstempel (cycli, leveringen, storykandidaten, access tokens) staan
gesorteerd op nieuwste eerst; de productkeuze houdt de ontvangen API-volgorde en
workspace-publicaties — die geen tijdstempel hebben — de volgorde van de backend. Het inkorten
gebeurt volledig in de frontend op de al opgehaalde data; de backend-endpoints kennen geen
paginering.

## 8. Samenvatting van de belangrijkste regel

Eén cyclus kan tot drie storykandidaten opleveren, maar Product Factory levert ze **niet** allemaal
tegelijk aan de Software Factory: met de standaardinstelling (`wipLimit = 1`) gaat dat strikt na
elkaar, pas nadat de vorige story volledig is afgehandeld. Pas als *al* het werk van een cyclus
(alle leveringen) klaar en geëvalueerd is, mag er automatisch een geheel nieuwe cyclus starten —
en dat gebeurt hooguit één keer per dag, rond het geconfigureerde tijdstip.
