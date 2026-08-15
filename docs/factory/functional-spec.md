# Functional Spec

De Product Factory laat producten autonoom doorontwikkelen: per product draaien productcycli
(shadow iterations) waarin agents onderzoek doen, storykandidaten schrijven en die — als het product op
autonoom staat — als stories naar de Software Factory sturen.

## De dashboardweergaven

De Flutter-webapp (`dashboard-frontend`) heeft één hoofdscherm, het productoverzicht, en daarbinnen
een secundaire beheerweergave. Beide gebruiken dezelfde beveiligde dashboardsessie en dezelfde elke
5 seconden ververste gegevens; Beheer heeft geen eigen URL, gegevensbron of netwerkverzoek. Op het
productoverzicht staat de als link vormgegeven navigatieactie `Beheer`. Deze is met muis of toetsenbord
te activeren, heeft linksemantiek en een zichtbare focusrand.

Het productoverzicht bestaat van boven naar beneden uit:

Uitsluitend in de standing acceptatievariant staat direct onder de kop `Productoverzicht` en vóór
de navigatieacties, metrics en overige overzichtsinhoud de melding `Synthetische acceptatiedata`.
De zichtbare én toegankelijke tekst beschrijft de vaste catalogus als `1 actief` en `3 terminaal`,
met `expliciet`, `afgeleid` en `onbekend` beslisgedrag. Dit zijn statische aantallen voor de
synthetische scenariodekking, geen dynamische totalen van alle gegevens in de acceptatiedatabase.
De melding groeit bij 320 CSS-pixels en 200% tekstvergroting verticaal mee en blijft zonder kleur
begrijpelijk. Productie en PR-previews tonen deze melding niet.

1. **Metric-tegels** — het globale aantal geldige producten en workspace-publicaties, plus de
   aantallen interne storykandidaten, shadow-iteraties en Software Factory-stories binnen het
   actieve product. Een succesvol geladen teller toont altijd het volledige scope-aantal, ook als
   de lijst eronder is ingekort. Afgeleide tellingen blijven `Laden…` of `Niet beschikbaar` zolang
   een van hun benodigde bronnen laadt of is mislukt, zodat een onvolledige bron niet als nul wordt
   gepresenteerd.
2. **Actief product** — een compacte productkeuze en daaronder de blijvend zichtbare productnaam.
   Alleen producten met een niet-lege `String` in `Product.slug` zijn beschikbaar. De slugs zijn
   canoniek en hoofdlettergevoelig: de frontend trimt of normaliseert ze niet en gebruikt geen naam,
   id of lijstpositie als vervanging. Een lokaal opgeslagen slug wordt uitsluitend hersteld als hij
   exact één beschikbaar product aanwijst; anders wordt de eerste geldige API-respons in ontvangen
   volgorde actief en wordt een aanwezige ongeldige voorkeur verwijderd. Alleen een bewust gekozen
   actieve slug wordt in de browservoorkeur opgeslagen; bronrecords blijven ongewijzigd.

   De keuze heeft een toegankelijke naam en actuele waarde, een zichtbare focusrand en is volledig
   met het toetsenbord bedienbaar. Na wisselen blijft de focus op de keuze en meldt een zichtbare
   live-status het product en de bijgewerkte cyclus- en storytellingen zonder focus te verplaatsen.
   De wissel filtert uitsluitend reeds geladen gegevens en start geen netwerkverzoek. Zijn er geen
   geldige producten, dan wordt de voorkeur verwijderd, verschijnt een lege toestand en ontbreken
   alle productgebonden acties en resultaten. Op brede en smalle schermen volgen de zichtbare,
   semantische en toetsenbordvolgorde hierna steeds `Cyclus starten`, `Eerdere cycli` en
   `Gekoppelde stories`.
3. **Cyclus starten** — de bestaande visueel dominante `StartCycleButton`, met hetzelfde
   `_startCycle`-gedrag en dezelfde foutafhandeling. Eén gedeeld presentatiemodel bepaalt de
   knopstatus, blokkaderedenen, veilige statuslabels en onvervulde voorwaarden uitsluitend uit
   `Product.status` en `Product.workspaceOwnership`. De knop is alleen actief wanneer die waarden
   exact `active` en `product-factory` zijn; er wordt niet getrimd, genormaliseerd of
   hoofdletterongevoelig vergeleken. De bekende onvoldoende waarden zijn `draft`, `paused` en
   `archived` voor de productstatus en `owner` voor workspacebeheer. Een ontbrekende sleutel,
   `null`, lege tekst, ander type of andere niet-lege tekst geldt als onbekend en dus als een
   onvervulde voorwaarde.

   Bij een uitgeschakelde knop staat direct precies één primaire reden. Ontbrekende of onbekende
   metadata heeft de hoogste prioriteit en toont `Startbeschikbaarheid kan niet betrouwbaar worden
   vastgesteld.`; daarna volgt een bekende niet-actieve status met `Starten is niet beschikbaar
   omdat dit product niet actief is.`; als laatste volgt een actief product met ander bekend
   workspacebeheer met `Starten is niet beschikbaar omdat deze workspace niet door Product Factory
   wordt beheerd.` Zijn beide voorwaarden onvervuld, dan staat daarnaast exact `Daarnaast is nog 1
   andere voorwaarde niet vervuld.` De uitgeschakelde actie en deze redencontext vormen samen één
   betekenisvolle semanticsgroep.

   Alleen bij blokkade staat de toetsenbordbedienbare actie `Bekijk productdetails`. Deze opent
   zonder netwerkverzoek een lokale, alleen-lezen dialoog binnen dezelfde productscope met dezelfde
   redencontext, `Productstatus` als `Actief`, `Niet actief` of `Onbekend`, `Workspacebeheer` als
   `Door Product Factory beheerd`, `Niet door Product Factory beheerd` of `Onbekend`, en uitsluitend
   de toepasselijke voorwaarden `Product moet actief zijn.` en/of `Workspace moet door Product
   Factory worden beheerd.` De dialoog toont geen ruwe backendwaarden, identifiers, overige
   productconfiguratie of muterende acties; alleen sluiten is mogelijk. De detailactie is bereikbaar
   met Tab en activeerbaar met Enter en Spatie. Sluiten via de zichtbare sluitactie of Escape zet de
   focus terug op `Bekijk productdetails`. Bij een beschikbare start ontbreken blokkademelding en
   detailactie. Cycli — inclusief een zichtbare of langlopende `RUNNING`-cyclus — en alle andere
   productgegevens beïnvloeden deze presentatie niet; de algemene dashboardverversing blijft
   ongewijzigd.
4. **Eerdere cycli** — uitsluitend cycli waarvan de niet-lege `Iteration.productSlug` exact gelijk
   is aan de actieve `Product.slug`, in de bestaande sorteervolgorde en met de bestaande
   5/+10-lijstbeperking. De productslug bepaalt uitsluitend de scope en identificatie; status bepaalt
   voor ieder product hetzelfde niet-uitklapbare presentatiemodel. De geschiedenis is één benoemde
   semanticsgroep en iedere zichtbare cyclus vormt daarin een afzonderlijke semanticscontainer.

   De vaste acceptatiecatalogus vult de bestaande presentatieregels zonder fixturespecifieke
   classificatie: de ene `RUNNING`-cyclus blijft een actieve kaart; de drie terminale cycli tonen
   respectievelijk `Mens` met `Handmatig geannuleerd`, `Evaluatie-agent (Afgeleid)` en `Onbekend`.
   De geaccepteerde cyclus heeft twee exact gekoppelde Software Factory-leveringen en
   toont dus `Gekoppelde opbrengst: 2`; de afgewezen onbekende cyclus heeft geen kandidaat of
   levering en toont `Gekoppelde opbrengst: 0`. De twee voltooide synthetische leveringen blijven
   via de bestaande beheerweergave zichtbaar.

   Voor ieder product toont status `ACCEPTED`, `NEEDS_REVISION`, `REJECTED`, `NO_CHANGE` of `FAILED`
   een terminale bewijsregel. Iedere bewijsregel toont in zichtbare en semantische leesvolgorde vijf
   afzonderlijk gelabelde waarden:

   - `Datum`: `startedAt`, met een ontbrekende of onleesbare waarde defensief terugvallend op
     `createdAt`, in de lokale browsertijd; zonder bruikbare datum staat er `Onbekend`;
   - `Cyclusuitkomst`: de bestaande gebruikersgerichte classificatie, of `Handmatig geannuleerd`
     bij een volledig geldig, aan dezelfde FAILED-cyclus gekoppeld annuleringsrecord;
   - `Reden`: uitsluitend de bestaande gelabelde operationele reden of de gecodeerde reden
     `Handmatig geannuleerd`; ontbrekende en onbekende waarden worden `Onbekend`;
   - `Beslisbron`: de bestaande expliciete provenance, of bij het ontbreken daarvan de bestaande
   conservatieve afleiding met `(Afgeleid)` wanneer die aantoonbaar is; ontbrekende, onbekende,
     tegenstrijdige of niet reconstrueerbare provenance wordt uitsluitend `Onbekend`, evenals een
     aanwezig maar onbekend expliciet record;
   - `Gekoppelde opbrengst`: alleen het aantal uniek en exact op productslug plus cyclus-id
     gekoppelde Software Factory-leveringen. Kandidaten en ongeldige, kruisproduct- of ambigue
     leveringskoppelingen tellen niet mee. Tijdens laden staat er `laden…`, bij bronfalen
     `niet beschikbaar`, nooit een misleidende nul.

   De bewijsregel toont geen tokens, prompts, ruwe foutmeldingen of foutpayloads, stacktraces,
   persoonsgegevens, artefactinhoud of gegevens van andere producten. De native actie `Bekijk
   cyclusdetail` opent met muis, Enter of Spatie het bestaande detail van precies dezelfde cyclus;
   haar toegankelijke naam bevat product, cyclus, cyclusdatum en gebruikersgerichte uitkomst. De
   waarden en actie blijven bij smalle en brede
   schermen en 200% tekstvergroting zonder horizontale pagina-scroll bruikbaar. Sluiten via de
   zichtbare actie of Escape herstelt de focus naar dezelfde bewijsactie; semantiek, tekstcontrast,
   bedieningscontrast en zichtbare focus voldoen aan WCAG 2.2 AA. Het detail zelf, de
   bewijs-presentatie, sorteervolgorde, 5/+10-lijstbeperking en auto-refresh blijven behouden; de
   bewijsregel staat uitsluitend tussen de eerdere cycli van het actieve product.

   `QUEUED` en `RUNNING` renderen voor ieder product als veilige voortgangskaart. Die toont alleen de
   gelabelde status, de via een gesloten mapping bruikbare `currentRole` als huidige stap, betrouwbare
   voortgang die direct uit de actieve status volgt en één neutrale actie `Bekijk cyclusdetail`.
   Ontbrekende of onbekende rollen worden weggelaten. Een ontbrekende of onbekende status toont
   uitsluitend `Status: Onbekend` en dezelfde neutrale detailactie. Deze kaarten tonen geen
   cyclusuitkomst, reden, beslissing, beslisbron, classificatiebadge, afleidingsclaim, terminale
   opbrengst of ruwe fouttekst.

   De frontend groepeert alle geladen cycli binnen de actieve productscope voordat de bestaande
   5/+10-lijstbeperking wordt toegepast. Een kandidaat koppelt alleen bij precies één exacte,
   hoofdlettergevoelige match op `productSlug` + het integerpaar
   `iterationSequenceNumber`/`sequenceNumber`; een levering alleen bij precies één exacte match op
   `productSlug` + het stringpaar `iterationId`/`id`. Ontbrekende, lege, anders getypeerde,
   kruisproduct- of ambigue relaties worden niet geschat via titel, kandidaat-id, lijstpositie,
   volgorde of waarschijnlijkheid en verschijnen niet in de actieve productscope.

   Cycli, kandidaten en leveringen houden elk een eigen laad- en foutstatus. Een terminale
   bewijsregel toont voor de leveringsbron `laden…`, `niet beschikbaar` of een geladen aantal. De productspecifieke Software
   Factory-lijst in Beheer wacht behalve op leveringen ook op kandidaten, omdat de leveringsscope
   via kandidaten wordt bepaald; `Alle producten` houdt de onafhankelijke globale bronstatus. De
   storywachtrij meldt dat zij onvolledig is totdat zowel kandidaten als leveringen beschikbaar
   zijn. Het compacte cyclusoverzicht gebruikt geen kandidaatbron en toont uit de leveringsbron
   alleen het exact gekoppelde terminale aantal. Openen en sluiten van de enige detailactie doet
   uitsluitend de bestaande detail-GET-verzoeken; scopewisselen en renderen voegen geen requests
   toe. Sluiten via de zichtbare actie of Escape herstelt focus naar precies de opener. Deze
   detaildialoog (`IterationSessionDialog`, `dashboard-frontend/lib/main.dart`) toont voor een
   expliciet handmatig-annuleringsrecord dezelfde bron en reden, plus
   `Mechanisme: Handmatige annulering` en `Beslist op: <lokale datum en tijd>` uit `decidedAt`.
   De afgeleide badge en uitkomstreden blijven daar eveneens verborgen. Voor een historische cyclus
   zonder record toont het detail alleen bij een bewezen classifiercombinatie de beslisbron met
   `(Afgeleid)`; onbekende provenance blijft ook daar uitsluitend `Onbekend`. De
   `ClassificationBadge` gebruikt dezelfde `classifyIterationOutcome`-uitkomst als de terminale
   bewijsregel (identieke tekst en `kClassificationColors`-kleurenpaar) — geen losse `Chip` met de ruwe
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
5. **Gekoppelde stories** — uitsluitend storykandidaten waarvan de niet-lege
   `StoryCandidate.productSlug` exact gelijk is aan de actieve productslug en waarvan het integer
   `iterationSequenceNumber` exact één `sequenceNumber` binnen de geladen cycli van die scope
   aanwijst. De bestaande kandidaatdetailactie opent precies de gekozen kandidaat. Een ontbrekende,
   anders getypeerde, lege, kruisproduct- of ambigue relatie wordt niet toegeschreven. De sectie
   gebruikt dezelfde 5/+10-lijstbeperking en toont pas een volledig aantal wanneer kandidaten én
   cycli geladen zijn.
6. **Product beheren** — de bestaande acties voor pauzeren/hervatten, instellingen, overleg en
   roadmap-sessie gelden uitsluitend voor het actieve product. Missie, Software Factory-project,
   doelrepository, workspace, `maxStoriesPerCycle`, `wipLimit`, AI-provider/model en cyclustijden
   staan in het Instellingen-scherm (`ProductSettingsDialog`): missie, project en workspace als
   alleen-lezen tekst, de overige velden — inclusief de doelrepository — bewerkbaar en opslaanbaar.
   Het scherm opent met focus binnen de dialoog, houdt de tab-focus binnen de dialoog en sluit met
   Escape waarbij de focus terugkeert naar de Instellingen-knop.
7. **Epic-roadmap** — de berekende epicvolgorde per product, met de bestaande detail- en
   beheeracties. Eventuele afgehandelde onderzoeksvragen staan direct onder de roadmap.
8. **Roadmap-sessies** — de sessiestatus, samenvatting en, indien aanwezig, een actie om het verslag
   te bekijken.
9. **Overleggen** — de overlegstatus en uitkomst, met de bestaande detail- en notulenacties.
10. **Benodigde access tokens** — openstaande handmatige acties, af te melden met een toelichting.
11. **Workspace** — gepubliceerde artifacts, klikbaar om de inhoud te tonen.

### De beheerweergave

Beheer begint met de als link vormgegeven navigatieactie `Terug naar overzicht`, gevolgd door de titel
`Beheer`. De link is de eerste focusbare actie, heeft linksemantiek en een zichtbare focusrand, werkt met
muis en toetsenbord en brengt de gebruiker terug naar het bestaande productoverzicht. De focus blijft
over de automatische verversing behouden. Daaronder staat een `Beheerscope`-keuze die opent met het
actieve product. Naast ieder geldig product is hier, en uitsluitend hier, `Alle producten` beschikbaar.
Iedere lijstkop vermeldt zichtbaar de gekozen productnaam of `Alle producten`. Een product kiezen maakt
het ook actief op het hoofdscherm en bewaart de slug; `Alle producten` is tijdelijke presentatiestatus
en wijzigt de opgeslagen actieve slug niet. Een live-status meldt de gekozen Beheer-scope en tellingen
zonder focus te verplaatsen. De weergave bevat daarna in deze volgorde:

1. **Software Factory-stories** — leveringen binnen de gekozen scope, nieuwste eerst, met externe
   storykey of de bestaande fallbacktekst, titel, product, leveringsstatus en laatst bekende Software
   Factory-fase. In een afzonderlijke productscope wordt een levering uitsluitend toegeschreven via
   exact één kandidaat met hetzelfde integer `candidateId` en vervolgens via de exacte
   `StoryCandidate.productSlug`; een productslug op de levering is geen fallback. Daarom zijn voor
   een productspecifiek resultaat zowel kandidaat- als leveringsbron nodig. `Alle producten` toont
   de bestaande globale lijst en de onafhankelijke leveringsbronstatus, inclusief niet eenduidig
   koppelbare records.
2. **Storywachtrij** — in een afzonderlijke scope uitsluitend kandidaten met exact dezelfde
   `StoryCandidate.productSlug`; onder `Alle producten` alle storykandidaten. De records worden
   exact eenmaal verdeeld over Fout / Bezig / In wachtrij /
   Klaar. De bestaande kandidaatdetailactie, kandidaat- en leveringsstatussen, foutinformatie en
   leveringskoppeling blijven behouden. Is een
   kandidaat geblokkeerd door een onopgeloste `dependsOn`-verwijzing (`blocked == true` met een
   niet-lege `blockedReason`), dan toont de kaart direct — zonder extra klik — onder de titel een
   label met icoon en de tekst "Geblokkeerd: <reden>", in het bestaande WCAG AA-contrasterende
   kleurenpaar `kGuardrailConflict` (`classification.dart`) en opvraagbaar via de semantics-tree
   van de kaart. Ontbreekt de blokkade of de reden, dan blijft de kaart ongewijzigd; er wordt geen
   extra data opgehaald voor dit label (`_buildStoryQueueSections`,
   `dashboard-frontend/lib/main.dart`). De wachtrij volgt de eigen laad-, fout-, lege of successtatus
   van de kandidaatbron. Zolang kandidaten wel zijn geladen maar leveringen nog laden of zijn mislukt,
   meldt zij expliciet het geladen kandidaataantal en dat de categorisering onvolledig is; zij toont dan
   geen compleet of leeg resultaat. Alleen de bestaande kandidaatrelatie koppelt een levering aan een
   wachtrijrecord.

De scopewissel filtert alleen de al geladen bronrecords en veroorzaakt geen extra request. Records
worden niet herschreven of voor deze weergave aan een cyclus toegeschreven. Alleen `Alle producten`
mag kandidaten en leveringen zonder eenduidig bepaalbare productrelatie tonen.

### Start- en doorlooptijd van een productcyclus

- Starttijd = `startedAt`; is die leeg, dan `createdAt`.
- Doorlooptijd = `completedAt - startedAt`, leesbaar als `2u 13m`, `4m 12s` of `35s`.
- Loopt de cyclus nog, dan staat er `loopt nog: <tijd sinds start>`; die waarde loopt mee met de
  auto-refresh.
- Is de cyclus nog niet gestart, dan staat er geen doorlooptijd.
- Datum en tijd staan in de lokale tijdzone van de browser als `dd-MM-yyyy HH:mm`, nooit als ruwe
  ISO-string.

### Lijstbeperking met de 'Meer'-knop

De lijsten op het productoverzicht (eerdere cycli, gekoppelde stories, afgehandelde onderzoeksvragen,
roadmap-sessies, overleggen, access tokens en workspace-publicaties) en in Beheer (Software
Factory-stories en elke subsectie van de storywachtrij) tonen standaard **5 items**.
Staat er meer klaar, dan verschijnt eronder een knop **'Meer (nog N)'** die er telkens **10** bij toont;
de knop verdwijnt zodra alles zichtbaar is. Elke sectie heeft een eigen, onafhankelijke teller, en die
teller overleeft de auto-refresh en het wisselen tussen overzicht en Beheer: een uitgeklapte lijst blijft
uitgeklapt en nieuwe items verschijnen bovenaan.
De afzonderlijke cyclusregels en voortgangskaarten zijn niet uitklapbaar. Hun stabiele identiteit
zorgt tijdens de normale auto-refresh uitsluitend dat een zichtbare cyclus dezelfde widget en
detailopener houdt; verdwijnt de cyclus uit de geladen gegevens, dan verdwijnt ook de kaart.

Lijsten met een bruikbaar tijdstempel staan gesorteerd op nieuwste eerst; workspace-publicaties hebben geen
tijdstempel en houden de volgorde van de backend.

## Status en conclusion van een productcyclus

Dit blok legt vast wat "status" en "conclusion" van een productcyclus (shadow iteration) betekenen
en hoe ze zich tot elkaar verhouden, als zelfstandige uitleg naast de badge-beschrijving hierboven.

- **Status is altijd óf lopend, óf voltooid — nooit iets ertussenin.** Het bestaande `status`-veld
  (`ShadowIterationView.status`, `productfactory-contracts/.../Contracts.kt`) kent de ruwe waarden
  QUEUED, RUNNING, ACCEPTED, NO_CHANGE, NEEDS_REVISION, REJECTED en FAILED. QUEUED en RUNNING zijn
  **lopend**; ACCEPTED, NO_CHANGE, NEEDS_REVISION, REJECTED en FAILED zijn **voltooid**. Het
  eindoordeel (conclusion) is
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
  `ACCEPT` met `REJECTED`, lopende statussen en alle ambigue combinaties blijven `Onbekend`. Alleen
  `Evaluatie-agent` en de bewezen technische-foutcombinatie worden zichtbaar en toegankelijk als
  `(Afgeleid)` gemarkeerd; `Onbekend` blijft zonder afleidingsclaim.
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
