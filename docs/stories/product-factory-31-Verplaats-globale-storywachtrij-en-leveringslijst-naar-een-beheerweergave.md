# product-factory-31 - Verplaats globale storywachtrij en leveringslijst naar één beheerweergave

## Story

Verplaats globale storywachtrij en leveringslijst naar één beheerweergave

<!-- refined-by-factory -->

## Scope

- Voeg binnen het bestaande, beveiligde dashboard één secundaire weergave met de titel `Beheer` toe.
- Plaats op het hoofdscherm een als link herkenbare navigatiebediening met de zichtbare naam `Beheer`. Plaats in de beheerweergave een equivalente link `Terug naar overzicht`.
- Verwijder uitsluitend de globale secties `Software Factory-stories` en `Storywachtrij` van het hoofdscherm. De gelijknamige metriek, de opbrengsten in cycluskaarten en de niet-koppelbare-opbrengstmelding blijven daar aanwezig.
- Toon in Beheer eerst de bestaande globale sectie `Software Factory-stories` en daarna de bestaande `Storywachtrij`.
- Behoud in de leveringslijst alle huidige records, sortering op nieuwste eerst, externe storysleutel of bestaande fallbacktekst, titel, product, leveringsstatus, Software Factory-fase en lege, ladende en mislukte toestand.
- Behoud in de storywachtrij alle huidige kandidaten en de categorieën `Fout`, `Bezig`, `In wachtrij` en `Klaar`. Behoud de zichtbare kandidaat- en leveringsstatussen, blokkeerreden, foutinformatie en de bestaande kandidaatdetailweergave.
- Blijf leveringen uitsluitend via de bestaande kandidaatrelatie gebruiken voor de huidige categorisering en detailinformatie van de storywachtrij. Koppel, verlies, combineer of schrijf geen records opnieuw en schrijf geen records aan een cyclus toe voor deze beheerweergave.
- Behoud de bestaande client-side lijstbeperking van vijf items en telkens tien extra items, met een onafhankelijke teller voor de leveringslijst en iedere wachtrijcategorie. De tellers blijven tijdens de automatische verversing behouden.
- Gebruik voor Beheer dezelfde periodiek ververste kandidaat- en leveringsgegevens als het hoofdscherm. Voeg geen nieuwe gegevensbron of beheer-specifiek netwerkverzoek toe.
- Houd kandidaat- en leveringsgegevens onafhankelijk:
  - de leveringslijst toont haar eigen laad-, lege, succes- of fouttoestand;
  - de storywachtrij toont de toestand van de kandidaatbron;
  - zolang de leveringsbron voor de wachtrij nog laadt of is mislukt, wordt de wachtrij herkenbaar als onvolledig gepresenteerd en niet als een compleet of leeg resultaat.
- Productbeheer, metrieken, cycluskaarten, epic-roadmap, afgehandelde onderzoeksvragen, roadmap-sessies, overleggen, tokenacties en workspace-publicaties behouden op het hoofdscherm hun bestaande functionaliteit en onderlinge volgorde.
- Dit is uitsluitend een wijziging aan het dashboard. Backend, API-contracten, database, schema, authenticatie, telemetrie, infrastructuur en Software Factory-leveringslogica vallen buiten scope.

## Acceptance criteria

- Na succesvolle gegevenslading bevat het hoofdscherm geen globale leveringslijst, geen globale storywachtrij en geen records uit die twee globale secties in de widget- of accessibility-tree. De bestaande metriek `Software Factory-stories` en de opbrengstgroepen in cycluskaarten mogen en moeten zichtbaar blijven.
- Het hoofdscherm bevat een focusbare navigatielink met de zichtbare en toegankelijke naam `Beheer`. De bediening heeft linksemantiek, een zichtbare toetsenbordfocus en opent de beheerweergave met muis of toetsenbord.
- Beheer bevat één globale leveringslijst en één globale storywachtrij. Voor vaste synthetische gegevens zijn dezelfde records, teksten, statussen, categorieën en detailacties zichtbaar als vóór de verplaatsing.
- Iedere kandidaat komt exact eenmaal in de toepasselijke wachtrijcategorie voor. Iedere levering komt exact eenmaal in de globale leveringslijst voor. Records worden niet samengevoegd, gefilterd of aan een cyclus toegeschreven.
- Activering van een kandidaat in de storywachtrij opent dezelfde kandidaatdetails, inclusief gekoppelde leveringsstatus en foutinformatie wanneer die beschikbaar zijn. Er wordt geen nieuwe detailactie voor leveringen geïntroduceerd.
- Geautomatiseerde tests dekken voor de kandidaatbron en de leveringsbron afzonderlijk succes, een succesvol geladen lege lijst en een fout. Een fout of laadstatus van één bron verbergt geen succesvol resultaat van de andere bron en wordt niet als nul gepresenteerd.
- Bij geladen kandidaten en een ladende of mislukte leveringsbron meldt de storywachtrij expliciet dat zij onvolledig is. Zodra beide bronnen succesvol zijn geladen, verschijnen de normale categorieën of de bestaande lege toestand.
- De bestaande sortering, de vijf-zichtbare-itemslimiet, `Meer (nog N)` en stappen van tien blijven voor de leveringslijst en iedere wachtrijcategorie werken en behouden hun toestand tijdens automatische verversing.
- Beheer bevat als eerste navigatieactie een focusbare link `Terug naar overzicht`. Deze heeft linksemantiek, een zichtbare toetsenbordfocus en brengt de gebruiker terug naar het bestaande hoofdscherm.
- Een toetsenbordtest verifieert voor beide weergaven dat de navigatielink in een logische, met de visuele volgorde overeenkomende tabvolgorde staat en dat focus zichtbaar blijft.
- Tests bij 320 logische pixels breed en 200% tekstvergroting verifiëren de beheerlinks, laad-/foutmeldingen, lange recordteksten, lijstitems, detailacties en `Meer`-bedieningen. Er ontstaat geen horizontale pagina-scroll, overlap, Flutter-overflow of onbereikbare bediening; verticale scrolling en tekstterugloop zijn toegestaan.
- Regressietests bewijzen op het hoofdscherm ten minste:
  - de bestaande prominente cyclusstartactie werkt met dezelfde voorwaarden en actie;
  - een cycluskaart kan zelfstandig worden geopend en gesloten;
  - de gesloten cycluskaart behoudt status, toepasselijke kernreden en beslisbron;
  - metrieken, productbeheer, roadmap, onderzoeksvragen, sessies, overleggen, tokenacties en workspace-publicaties blijven aanwezig en functioneel.
- Geautomatiseerde tests bevestigen dat geen nieuwe backendroute, contractveld, opslagbewerking, authenticatiestroom, telemetrie of leveringsactie is toegevoegd.

## Aannames

- `Software Factory-leveringslijst` verwijst naar de huidige globale sectie met de zichtbare titel `Software Factory-stories`; die bestaande titel en recordteksten blijven behouden.
- Beheer is een interne secundaire weergave binnen dezelfde ingelogde dashboardsessie. Een afzonderlijk bookmarkbaar webadres of nieuwe deep-linkroute is niet vereist.
- Een native link betekent hier een bediening die voor hulptechnologie als link wordt aangeboden, via Tab bereikbaar is, met Enter kan worden geactiveerd en een zichtbare focusindicator heeft.
- Kandidaten en leveringen blijven op het hoofdscherm geladen omdat de cyclusopbrengsten, metrieken en roadmap deze gegevens daar nog gebruiken. De verplaatsing betreft alleen de twee globale lijstpresentaties.
- Dezelfde records mogen zowel in hun globale beheercontext als, volgens de bestaande koppellogica, als opbrengst in een cycluskaart voorkomen.
- “Overige plaatsing ongewijzigd” betekent dat de resterende hoofdschermonderdelen hun bestaande sectie en onderlinge volgorde behouden; ze schuiven alleen op doordat de twee globale lijsten verdwijnen.
- De bestaande automatische verversing, sortering en lijstbeperking blijven leidend; er wordt geen server-side paginering toegevoegd.

<!-- test-feedback:start -->
## Test-feedback
Test afgewezen.

- Gerichte Flutter-tests: 20 geslaagd.
- Preview/API-health: HTTP 200.
- `Beheer` opent via Tab+Enter.
- Bug: `Terug naar overzicht` krijgt focus, maar Enter navigeert niet terug. Tweemaal gereproduceerd in Chromium.
- Screenshots staan in `/work/screenshots`.
- Details: [product-181-worklog.md](/work/repo/docs/stories/worklog/product-181-worklog.md).

{"agent_tips_update":[{"category":"repo","key":"flutter-web-keyboard-preview-required","content":"Flutter-widgettests voor semantics en Enter-activatie kunnen groen zijn terwijl de gebouwde webpreview afwijkt; activeer flt-semantics-placeholder en controleer beide navigatierichtingen met echte browser-Tab- en Enter-events."}]}
{"phase":"test-rejected"}
<!-- test-feedback:end -->

## Eindsamenvatting

Eindsamenvatting voor PO:

- De globale leveringslijst en storywachtrij zijn verplaatst naar de nieuwe beveiligde weergave `Beheer`, met toegankelijke heen- en terugnavigatie.
- Bestaande gegevens, volgorde, statussen, details, bronmeldingen en onafhankelijke `5/+10`-tellers zijn behouden, ook tijdens automatische verversing.
- Het hoofdscherm behoudt metrieken, cyclusopbrengsten en alle overige functies. Er zijn geen nieuwe gegevensbronnen, routes, backendwijzigingen of opslagacties toegevoegd.
- Een aanvankelijk gevonden toetsenbordprobleem met `Terug naar overzicht` is hersteld en opnieuw getest.
- Het volledige vangnet slaagde: 142 backendtests, 257 frontendtests en statische controle zonder bevindingen. Daarnaast zijn 20 gerichte tests en echte Chromium-controles uitgevoerd, inclusief Tab+Enter, focusbehoud tijdens verversing en een 320px-weergave zonder horizontale scroll.
- Bewust niet gedaan: records herkoppelen, samenvoegen of aan cycli toeschrijven; een aparte beheer-URL of nieuwe leveringsdetailactie toevoegen; backend-, contract-, authenticatie-, telemetrie- of infrastructuurwijzigingen maken.

<!-- deploy-summary:start -->
De algemene lijsten met aangeleverde stories en stories in de wachtrij staan nu samen op de aparte pagina Beheer. Je kunt daar met muis of toetsenbord naartoe en weer terug. Het gewone overzicht blijft daardoor rustiger, terwijl alle bestaande informatie en acties behouden blijven.
<!-- deploy-summary:end -->
