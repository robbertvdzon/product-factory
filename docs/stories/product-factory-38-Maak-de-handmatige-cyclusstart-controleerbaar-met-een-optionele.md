# product-factory-38 - Maak de handmatige cyclusstart controleerbaar met een optionele onderzoeksvraag

## Story

Maak de handmatige cyclusstart controleerbaar met een optionele onderzoeksvraag

<!-- refined-by-factory -->

## Scope

De bestaande actie `Cyclus starten` blijft alleen beschikbaar volgens de huidige voorwaarden voor productstatus en workspacebeheer. Activering start niet direct, maar opent een compacte modale dialoog die ondubbelzinnig bij de op dat moment actieve productslug hoort.

De dialoog:

- toont het actieve product;
- biedt precies de keuzes `Autonome standaard` en `Eigen onderzoeksvraag`;
- selecteert standaard `Autonome standaard`;
- toont het invoerveld uitsluitend wanneer `Eigen onderzoeksvraag` is geselecteerd;
- toont vóór bevestiging een samenvatting met het actieve product, de effectieve opdracht en exact één herkomstlabel: `Autonome standaard` of `Eigenaarinput`.

De canonieke autonome standaardopdracht is:

`Bepaal autonoom de belangrijkste nog onbeantwoorde productvraag op basis van missie, bestaand dossier en eerdere iteraties.`

Bij eigenaarinput is de effectieve opdracht de eenmaal getrimde invoer. Dezelfde tekenreeks wordt zonder verdere normalisatie gebruikt in de samenvatting, het startverzoek en de opslag.

Nieuwe handmatig gestarte cycli bewaren naast de bestaande opdracht een gesloten herkomstwaarde voor uitsluitend `Autonome standaard` of `Eigenaarinput`. Het bestaande cyclusdetail toont voor zulke cycli de opgeslagen opdracht en het bijbehorende Nederlandse herkomstlabel. De compacte cyclusregels tonen deze gegevens niet.

De opslaguitbreiding is nullable en krijgt geen backfill. Historische en automatisch gestarte cycli houden ontbrekende herkomst; de UI leidt daarvoor geen herkomst af en voegt geen herkomstweergave toe.

Automatische cyclusstarts, hervatten en annuleren van cycli, andere productslugs, de verwerking van cyclusresultaten en de Software Factory-koppeling blijven functioneel en inhoudelijk ongewijzigd.

## Acceptance criteria

- Activering van de bestaande startactie opent voor de actuele productslug een programmatisch benoemde modale dialoog. `Autonome standaard` is vooraf geselecteerd.
- Een geautomatiseerde toetsenbordtest bevestigt dat de focus binnen de dialoog blijft, Escape de dialoog sluit en de focus daarna terugkeert naar dezelfde startknop.
- De autonome route toont vóór bevestiging het actieve product, exact de canonieke standaardopdracht en `Herkomst: Autonome standaard`.
- Bevestiging van de autonome route verstuurt exact die standaardopdracht en uitsluitend de bijbehorende handmatige herkomst. Er wordt maximaal één cyclus voor de getoonde productslug aangemaakt.
- Selectie van `Eigen onderzoeksvraag` toont precies één zichtbaar en programmatisch gelabeld tekstveld. Het veld heeft een limiet van 300 tekens na trimmen.
- Client en server passen dezelfde validatie toe: na trimmen zijn 1 tot en met 300 tekens toegestaan. Lege invoer, uitsluitend witruimte en meer dan 300 tekens blokkeren de start met een zichtbaar en programmatisch aan het veld gekoppeld foutbericht.
- Bij eigenaarinput zijn de effectieve opdracht in de samenvatting, het request en de opgeslagen cyclus bytegelijk aan dezelfde getrimde invoer. De opgeslagen herkomst resulteert uitsluitend in `Herkomst: Eigenaarinput`.
- Verborgen of niet-geselecteerde invoer wordt niet in het request opgenomen. Een eerder ingevulde eigen vraag mag lokaal behouden blijven bij wisselen van keuze, maar wordt bij de autonome keuze niet verstuurd of opgeslagen.
- De server accepteert voor een handmatige start alleen de twee afgesproken herkomsten en valideert dat opdracht en herkomst bij elkaar passen; onbekende of inconsistente combinaties worden geweigerd zonder cyclus aan te maken.
- Tijdens een lopend startverzoek is de bevestigingsactie uitgeschakeld. Herhaalde bediening of gelijktijdige verwerking van dezelfde bevestiging maakt maximaal één cyclus aan en publiceert maximaal één startgebeurtenis.
- Een geautomatiseerde integratietest bewijst de maximaal-één-garantie en controleert dat een afgewezen tweede verzoek geen extra cyclus voor dezelfde productslug achterlaat.
- Bij een mislukte start blijft de dialoog open met behoud van keuze en invoer. De bevestigingsactie wordt weer beschikbaar zodra dat veilig is en de fout verschijnt als toegankelijke statusmelding zonder de vrije opdrachttekst te herhalen.
- Na een succesvolle start sluit de dialoog, wordt het overzicht volgens het bestaande gedrag vernieuwd en toont het bestaande detail van de nieuwe cyclus exact dezelfde opdracht en hetzelfde herkomstlabel als de bevestigingssamenvatting.
- Contract- en migratietests bevestigen dat herkomst voor bestaande rijen nullable blijft, dat er geen backfill plaatsvindt en dat ontbrekende herkomst niet wordt afgeleid.
- Regressietests bevestigen dat automatische starts geen handmatige herkomst krijgen, hun effectieve standaardgedrag behouden en geen gewijzigde request- of uitvoeringsroute nodig hebben.
- Regressietests bevestigen dat historische cyclusdetails, compacte cyclusregels, cycli van andere productslugs, hervat- en annuleerflows en Software Factory-koppelingen ongewijzigd blijven.
- Productscopetests bevestigen dat opdracht en herkomst alleen via de bestaande geautoriseerde routes voor de juiste productslug leesbaar zijn. Een detailverzoek met een andere productslug levert de cyclusgegevens niet op en de frontend toont ze niet binnen een andere actieve productscope.
- Privacytests bevestigen dat vrije eigenaarinput niet wordt opgenomen in algemene telemetry, requestlogging, foutlogs of foutmeldingen. Gebruik als opgeslagen cyclusopdracht en als invoer voor de bestaande cyclusuitvoering valt wel binnen de bedoelde verwerking.
- Alle nieuwe dialoogtoestanden, validatiefouten, statusmeldingen, keuzevelden en acties zijn zonder kleur begrijpelijk, toetsenbordbedienbaar en programmatisch benoemd.

## Aannames

- `Actief product` betekent het product dat volgens de bestaande productscope geselecteerd is op het moment dat de dialoog opent; wisselingen door een latere dashboardverversing veranderen de productscope van een reeds geopende dialoog niet.
- De huidige canonieke autonome standaardopdracht blijft inhoudelijk ongewijzigd en vormt één gedeelde gedragsmatige bron voor samenvatting, validatie, request en opslag.
- De grens van 300 tekens wordt toegepast op de getrimde tekst volgens dezelfde tekenlengtesemantiek in client en server; witruimte binnen de opdracht blijft ongewijzigd.
- Ontbrekende herkomst betekent een historische of niet-handmatig geprovenanceerde cyclus. Er wordt dan geen alternatief label zoals `Onbekend` of een afgeleide herkomst getoond.
- Een serverconflict omdat er al een actieve cyclus loopt geldt als een mislukte start: er wordt geen tweede cyclus aangemaakt en de dialoog toont de toegankelijke foutstatus met behoud van invoer.

<!-- test-feedback:start -->
## Test-feedback
Test afgekeurd.

De HEAD-preview serveert frontendrevision `a3fb43d`, maar de bijbehorende runtime crasht door een Flyway-checksummismatch op migratie V25. Daardoor blijft een oude runtime actief en geven drie dashboardrequests HTTP 404. De UI toont alleen `Dashboard kon niet laden`, waardoor de echte start-, refresh- en detailflow niet testbaar is.

Bewijs: [worklog](/work/repo/docs/stories/worklog/product-223-worklog.md) en [screenshot](/work/screenshots/product-223-unintercepted-head-a3fb43d-current.png).

{"agent_tips_update":[{"category":"repo","key":"preview-runtime-revision-readiness","content":"Controleer bij previewtests naast de frontendbronrevisie ook de runtimepod en image revision. Een nieuwe frontend kan gereed zijn terwijl de HEAD-runtime door Flyway faalt en de service stilzwijgend een oude, nog gereedstaande runtimepod blijft gebruiken."}]}
{"phase":"test-rejected"}
<!-- test-feedback:end -->

## Eindsamenvatting

## Eindsamenvatting voor de PO

De handmatige cyclusstart opent nu een toegankelijke dialoog voor het actieve product. De gebruiker kiest tussen de vaste autonome opdracht en een eigen onderzoeksvraag van maximaal 300 tekens; vóór bevestiging worden product, opdracht en herkomst getoond. Eigen invoer wordt aan begin en einde opgeschoond en daarna ongewijzigd gebruikt voor bevestiging, uitvoering en opslag.

De herkomst wordt alleen voor nieuwe handmatige starts opgeslagen en in het cyclusdetail getoond. Gelijktijdige bevestigingen kunnen maximaal één cyclus en één startgebeurtenis opleveren. Fouten laten de dialoog met de gemaakte keuze en invoer open, zonder de vrije tekst in foutmeldingen, logging of algemene meetgegevens op te nemen.

Historische en automatische cycli krijgen bewust geen afgeleide herkomst; compacte cyclusregels, hervatten, annuleren en overige verwerkingsroutes zijn ongewijzigd. Het herstel van botsende databasewijzigingen is strikt beperkt tot aantoonbaar wegwerpbare PR-previewomgevingen; productie en acceptatie blijven veilig stoppen.

Het volledige vangnet slaagde: 198 backendtests, 441 frontendtests, statische analyse, de toetsenbord- en browsertoegankelijkheidstest, drie Docker Engine-tests en twee frontend-imagebuilds. De story-brede testsubtaak is goedgekeurd. Alleen de afzonderlijke agent-imagebuild is lokaal bewust niet uitgevoerd en blijft aan CI toegewezen.

<!-- deploy-summary:start -->
Wanneer je handmatig een productcyclus start, kun je voortaan de standaardopdracht gebruiken of zelf een onderzoeksvraag meegeven. Voor je bevestigt zie je duidelijk welk product, welke opdracht en welke herkomst worden opgeslagen, terwijl dubbele starts worden voorkomen.
<!-- deploy-summary:end -->
