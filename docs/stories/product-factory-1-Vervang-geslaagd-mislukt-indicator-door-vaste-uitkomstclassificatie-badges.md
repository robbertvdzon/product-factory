# product-factory-1 - Vervang geslaagd/mislukt-indicator door vaste uitkomstclassificatie-badges in het iteratieoverzicht

## Story

Vervang geslaagd/mislukt-indicator door vaste uitkomstclassificatie-badges in het iteratieoverzicht

<!-- refined-by-factory -->

## Samenvatting
In het productcyclus-overzicht van de Product Factory-dashboard ziet de eigenaar nu alleen impliciet of een iteratie geslaagd of mislukt is. We voegen een duidelijk badge toe aan elke iteratierij met precies één van vier vaste labels: onderzoek-onvoldoende, guardrail-conflict, richting-gekozen of richting-verworpen. Zo is in één oogopslag te zien wat voor uitkomst een autonome iteratie had, zonder dat er iets aan de bestaande data, knoppen of PR-koppeling verandert. Het is puur een verbetering van de weergave, gebaseerd op gegevens die al bestaan.

## Scope
- Wijziging beperkt tot `dashboard-frontend` (het Flutter-webdashboard), specifiek de iteratierij in de sectie "Productcycli en onderzoekssessies" op de overzichtspagina (`main.dart`).
- Toevoegen van een classificatiebadge per iteratierij met exact één van deze vier tekstwaarden: `onderzoek-onvoldoende`, `guardrail-conflict`, `richting-gekozen`, `richting-verworpen`.
- De classificatie wordt afgeleid via pure mapping-logica uit reeds bestaande velden op de iteratie (`status`, `criticVerdict`, eventueel `errorMessage`) — geen nieuwe backend-velden, geen nieuwe API-call, geen nieuwe databronnen.
- Geen wijziging aan routing, authenticatie, PR-goedkeuringsflow, Software Factory-koppeling of andere overzichtssecties.
- Geen wijziging aan de bestaande statustekst/subtitle-inhoud van de rij; de badge komt er additioneel bij.

## Acceptance criteria
- Elke iteratierij in de bestaande lijst "Productcycli en onderzoekssessies" toont exact één classificatiebadge met een van de vier vaste tekstwaarden: `onderzoek-onvoldoende`, `guardrail-conflict`, `richting-gekozen`, `richting-verworpen`.
- Een geautomatiseerde widgettest (flutter_test) bevestigt dat elke gerenderde iteratierij precies één van de vier toegestane badge-teksten bevat en geen vrije tekst of andere waarde.
- De badge communiceert de classificatie via een tekstlabel dat programmatisch leesbaar is (bijv. zichtbare `Text` in de badge en/of de Semantics-accessible-name van het badge-widget), niet uitsluitend via kleur; dit wordt geautomatiseerd geverifieerd via een widgettest die de Semantics-boom/tekstinhoud van de badge inspecteert (axe-core is niet van toepassing, want dit is een Flutter-canvas-app zonder browser-DOM).
- Kleurcontrast tussen badge-tekst en badge-achtergrond voldoet voor elk van de vier badge-varianten aan WCAG 2.1 AA (minimaal 4.5:1 voor normale tekst); dit wordt geautomatiseerd geverifieerd met een unit test die het contrastratio berekent voor elk van de vier kleurenparen.
- De bestaande weergave van de workspace-publicatie/PR-referentie (de `workspacePullRequestUrl`/`workspaceCommitSha`-tekst in het detaildialoog van een iteratie) blijft ongewijzigd in inhoud en positie; een regressietest legt de huidige weergave vast als baseline en faalt bij afwijking.
- De mapping-logica die bestaande iteratie-uitkomstdata (`status`, `criticVerdict`, eventueel `errorMessage`) omzet naar een van de vier classificatiewaarden is gedekt met unit tests, inclusief een expliciet, getest fallback-gedrag voor de gevallen waarin de data niet ondubbelzinnig op een van de vier waarden is te mappen (bijv. iteraties die nog lopen/in de wachtrij staan, of statuscombinaties die niet zijn voorzien).
- Geen enkele test of codewijziging raakt authenticatie, nieuwe routes, of de koppeling met Software Factory-uitvoeringspaden; bestaande widget-/integratietests voor het overzicht blijven slagen zonder aanpassing van hun verwachte navigatiepaden.

## Aannames
- Er bestaat in de huidige app geen apart klikbaar "link/knop naar de PR-goedkeuringsflow" met een eigen href/route; de dichtstbijzijnde bestaande functionaliteit is de `workspacePullRequestUrl`/`workspaceCommitSha`-tekst in het iteratie-detaildialoog. De regressie-eis in de acceptatiecriteria is hierop aangepast: deze tekst/weergave moet ongewijzigd blijven, in plaats van een href/route te vergelijken die niet bestaat.
- Toegankelijkheids- en contrastverificatie gebeurt met Flutter-widgettests (Semantics-inspectie en een handmatige WCAG-contrastberekening), niet met axe-core, omdat dit een Flutter-webapp zonder toegankelijke browser-DOM is en er geen axe-core-tooling in het project aanwezig is.
- Voorgestelde default-mapping (te bevestigen/aan te passen door de implementerende agent aan de hand van de echte databron): `status = ACCEPTED` → `richting-gekozen`; `status = NEEDS_REVISION` → `onderzoek-onvoldoende`; `status = REJECTED` → `richting-verworpen`; `status = FAILED` → `guardrail-conflict`. Voor iteraties die nog niet klaar zijn (`QUEUED`/`RUNNING`) geldt het verplichte fallback-gedrag, aangezien er nog geen uitkomst is om te classificeren.
- De badge is een puur visuele/tekstuele toevoeging aan de bestaande rij; er worden geen nieuwe interactiemogelijkheden (klikken, expanderen) aan de badge zelf toegevoegd.

## Eindsamenvatting

Genoeg context om de eindsamenvatting te schrijven.

## Eindsamenvatting — product-factory-1: Vaste uitkomstclassificatie-badges in het iteratieoverzicht

**Wat is gebouwd**
In het dashboard-frontend (Flutter-webdashboard) is aan elke iteratierij in de sectie "Productcycli en onderzoekssessies" een classificatiebadge toegevoegd met exact één van vier vaste labels: `onderzoek-onvoldoende`, `guardrail-conflict`, `richting-gekozen`, `richting-verworpen`. De badge staat additief naast de bestaande titeltekst; subtitle, trailing-icoon, onTap en de workspace-publicatie-weergave (PR-url/commit-sha) in het detaildialoog zijn ongewijzigd gebleven.

**Belangrijkste keuzes**
- Mapping-logica en badge-widget zijn ondergebracht in een nieuw, apart bestand (`classification.dart`), consistent met bestaande patronen voor pure logica in dit project.
- Mapping: `ACCEPTED` → richting-gekozen, `NEEDS_REVISION` → onderzoek-onvoldoende, `REJECTED` → richting-verworpen, `FAILED` → guardrail-conflict. Voor alle overige/onbekende statussen (o.a. lopende/wachtende iteraties `QUEUED`/`RUNNING`) is expliciet gekozen voor fallback naar `onderzoek-onvoldoende`, zoals gevraagd in de acceptatiecriteria.
- De badge toont de classificatie zowel als zichtbare tekst als via een Semantics-label, zodat de uitkomst niet uitsluitend via kleur wordt gecommuniceerd.
- Kleurenparen voor alle vier varianten zijn gecontroleerd op WCAG 2.1 AA-contrast (ratio's tussen 6.98:1 en 8.88:1, ruim boven de vereiste 4.5:1).

**Testen**
- Nieuwe unit tests voor de mapping (alle vier gevallen plus fallback) en voor het contrastratio per kleurvariant.
- Nieuwe widgettest die per iteratierij bevestigt dat precies één toegestane badge-tekst wordt getoond, inclusief Semantics-inspectie.
- Nieuwe regressietest die de bestaande workspace-publicatie-weergave in het detaildialoog als baseline vastlegt (ongewijzigde inhoud/positie).
- Volledig testvangnet gedraaid en groen: `flutter analyze` (geen issues), `flutter test` (40/40, inclusief bestaande tests ongewijzigd), extra rooktest `flutter build web` succesvol, en (bij development) ook de mvn-buildmodules groen.
- Een echte preview-/browserverificatie was niet mogelijk (geen preview-omgeving in de agentcontainer); dit is gecompenseerd met widgettests, een build-rooktest en handmatige codecontrole.

**Bewust niet gedaan**
- Geen wijzigingen aan backend, API's, routing, authenticatie of de Software Factory-koppeling.
- Geen nieuwe interactiemogelijkheden aan de badge zelf (geen klik/expand).
- Geen aanpassing van bestaande statustekst/subtitle-inhoud van de rij.

<!-- deploy-summary:start -->
In het overzicht van productcycli zie je nu direct bij elke iteratie een duidelijk label dat het resultaat samenvat: onderzocht maar onvoldoende, in strijd met de regels, gekozen richting, of afgewezen richting. Zo hoef je niet meer zelf af te leiden wat de uitkomst was — het staat er in één oogopslag bij. Er is verder niets veranderd aan hoe je met het overzicht werkt.
<!-- deploy-summary:end -->
