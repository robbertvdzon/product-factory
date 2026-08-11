# product-factory-26 - Verplaats missie/repo/workspace/max-stories/WIP-limiet/AI-model/cyclustijden van de Producten-kaart naar het bestaande Instellingen-paneel

## Story

Verplaats missie/repo/workspace/max-stories/WIP-limiet/AI-model/cyclustijden van de Producten-kaart naar het bestaande Instellingen-paneel

<!-- refined-by-factory -->

## Scope
- Verwijder de directe weergave op de productkaart (`dashboard-frontend/lib/main.dart`, huidige regels ~564-586) van: missie, `softwareFactoryProjectKey`, `targetRepositoryName`, `workspaceOwnership`, `maxStoriesPerCycle`, `wipLimit`, AI-provider/model en cyclustijden.
- Voeg in het bestaande `ProductSettingsDialog` (geopend via de "Instellingen"-knop) een alleen-lezen sectie toe met missie, `softwareFactoryProjectKey` en `workspaceOwnership`, elk met een korte toelichting dat deze velden gekoppeld zijn aan de Software Factory-integratie en daarom niet bewerkbaar zijn in dit scherm.
- Voeg in `ProductSettingsDialog` een bewerkbaar veld toe voor `targetRepositoryName` (de backend-request `UpdateProductSettingsRequest` ondersteunt dit veld al: `productfactory/.../product/api/ProductCatalog.kt:49-58`).
- De velden `maxStoriesPerCycle`, `wipLimit`, AI-provider/model (`AiProviderModelFields`) en cyclustijden (`IterationTimesField`) staan al bewerkbaar in `ProductSettingsDialog` (regels ~2363-2478) en blijven ongewijzigd functioneel; alleen de dubbele weergave op de kaart vervalt.
- Geen wijziging aan backend, database, API-schema (`UpdateProductSettingsRequest` blijft ongewijzigd), of aan andere homepage-secties (metric-tegels, Productcycli, SF-stories, access tokens, Storywachtrij, Workspace) of andere producten.
- De knoppen "Pauzeren"/"Hervatten" en "Start overleg" op de productkaart blijven functioneel exact ongewijzigd.
- Toegankelijkheidseisen worden vertaald naar de Flutter-equivalenten van het bestaande `AlertDialog`-patroon (zoals ook in eerdere stories in dit project is gedaan): `Semantics`/focus-beheer in plaats van HTML/ARIA-attributen, en `flutter_test`-widgettests in plaats van axe-core/DOM-tests. Dit project heeft geen HTML-DOM- of axe-core-tooling; Flutter web rendert geen `role`/`aria-*`-attributen.

## Acceptance criteria
- De developer documenteert (in commit-omschrijving of code-comment) de bevindingen van de inspectie van `ProductSettingsDialog` vóór wijziging: welke velden al aanwezig en bewerkbaar waren, en welke ontbraken.
- Missie, `softwareFactoryProjectKey` (projectnaam), `targetRepositoryName`, `workspaceOwnership`, `maxStoriesPerCycle`, `wipLimit`, AI-provider/model en cyclustijden staan niet meer standaard zichtbaar op de productkaart zelf.
- `maxStoriesPerCycle`, `wipLimit`, AI-provider/model, cyclustijden en `targetRepositoryName` zijn volledig bereikbaar, invoerbaar en opslaanbaar via het Instellingen-scherm, geverifieerd met een geautomatiseerde widget-test (`flutter_test`) die elk veld wijzigt, opslaat, en controleert dat de juiste waarden in de opslag-request (`UpdateProductSettingsRequest`) terechtkomen.
- Missie, `softwareFactoryProjectKey` en `workspaceOwnership` zijn zichtbaar in het Instellingen-scherm als alleen-lezen tekst, met een korte, voor gebruikers begrijpelijke toelichting waarom deze niet bewerkbaar zijn.
- Het Instellingen-scherm (`AlertDialog`) is toetsenbord-bedienbaar: opent met focus binnen de dialoog, sluit met Escape waarbij de focus terugkeert naar de Instellingen-knop, en blijft de tab-focus binnen de dialoog (focus-trap), geverifieerd met een geautomatiseerde `flutter_test`-toetsenbordtest (`tester.sendKeyEvent`).
- Alle verplaatste bewerkbare velden behouden hun bestaande label/helperText-koppeling (zoals nu al in `ProductSettingsDialog`); er ontstaat geen regressie in de bestaande widget-tests voor dit dialoogvenster.
- Geen wijziging aan backend, database, API-schema (`UpdateProductSettingsRequest` ongewijzigd), of aan overige homepage-secties (metric-tegels, Productcycli, SF-stories, access tokens, Storywachtrij, Workspace) of andere producten.
- De knoppen "Pauzeren"/"Hervatten" en "Start overleg" op de productkaart blijven functioneel exact ongewijzigd (geverifieerd met bestaande of aangepaste widget-tests).
- Alle bestaande en nieuwe Flutter-tests (`dashboard-frontend/test/`) en de build (`flutter analyze`/`flutter test`) slagen.

## Aannames
- De PO heeft in issuecomment 3023 al gekozen voor optie (a): missie, `softwareFactoryProjectKey` en `workspaceOwnership` worden alleen-lezen in het Instellingen-scherm getoond; alleen de velden die `UpdateProductSettingsRequest` al ondersteunt (inclusief `targetRepositoryName`) krijgen een volledige bewerk-en-opslaan-flow. Dit is bindend en overschrijft de oorspronkelijke AC-tekst die alle zes velden "volledig bereikbaar, invoerbaar en opslaanbaar" noemde.
- De oorspronkelijke AC-eisen in HTML/ARIA-termen (`role="dialog"`, `aria-modal`, `aria-describedby`, axe-core-scan) zijn niet letterlijk van toepassing omdat dit een Flutter-webapp zonder DOM/axe-core-tooling is (bevestigd precedent uit eerdere stories in dit project); ze worden vertaald naar de functioneel gelijkwaardige Flutter-mechanismen (Semantics, Focus/FocusNode, flutter_test) zoals hierboven beschreven in Scope/AC.
- `acceptanceUrl` (ook ondersteund door `UpdateProductSettingsRequest`) valt buiten deze story, omdat dit veld niet in de oorspronkelijke lijst van zes te verplaatsen items voorkomt en nergens op de huidige productkaart wordt getoond.
- De exacte visuele indeling/volgorde van de nieuwe alleen-lezen sectie en de bewerkbare `targetRepositoryName`-toevoeging binnen `ProductSettingsDialog` is aan de implementerende developer, zolang alle velden aanwezig, gelabeld en per AC bereikbaar zijn.

## Eindsamenvatting

Alles is duidelijk. Ik heb voldoende informatie voor de eindsamenvatting; geen aanvullende bestanden nodig.

Eindsamenvatting voor product-152:

**Wat is gebouwd:** De productkaart op de homepage toont niet langer missie, Software Factory-projectnaam, doelrepository, workspace, max-stories-per-cyclus, WIP-limiet, AI-provider/model en cyclustijden. Deze zeven velden zijn verplaatst naar het bestaande Instellingen-paneel (`ProductSettingsDialog`), gesplitst in twee delen:
- Een nieuwe alleen-lezen sectie bovenaan met missie, projectnaam en workspace, elk met een toelichting dat deze gekoppeld zijn aan de Software Factory-integratie en dus niet bewerkbaar zijn.
- Een nieuw bewerkbaar veld "Doelrepository" (targetRepositoryName), dat wordt opgeslagen via de bestaande, ongewijzigde backend-call. De al aanwezige bewerkbare velden (max-stories, WIP-limiet, AI-provider/model, cyclustijden) blijven functioneel ongewijzigd.

**Keuzes:** Toetsenbordtoegankelijkheid van de dialoog is geborgd met een focus-trap (Tab blijft binnen de dialoog), autofocus bij openen, en Escape sluit de dialoog met focus terug naar de Instellingen-knop (nu een aparte `SettingsButton`-widget). Onderweg is een latente bug gefixt (een `setState`-callback die een Future retourneerde), aan het licht gebracht door de nieuwe tests, zonder gedragswijziging. Backend/API/database zijn bewust ongewijzigd gelaten, omdat het bestaande request-schema het nieuwe veld al ondersteunde. `acceptanceUrl` is bewust buiten scope gehouden, zoals in de story afgesproken.

**Getest:** Twee nieuwe widgettestbestanden dekken: afwezigheid van de zeven velden op de kaart, correcte weergave en opslag in het Instellingen-paneel, focus-trap (25 Tab-cycli) en Escape-gedrag, en dat Pauzeren/Hervatten/Start overleg ongewijzigd werken. `flutter analyze` en `flutter test` (165/165) zijn groen, evenals de backend-`mvn verify`. Reviewer en tester hebben beiden akkoord gegeven zonder blockers; preview-omgeving (PR-60) is smoke-getest en gezond.

**Bewust niet gedaan:** Geen wijziging aan backend/database/API-schema, geen `acceptanceUrl`-veld, en geen wijziging aan de interne werking van de AI-provider/model- en cyclustijden-widgets zelf (enkel hun positie).

<!-- deploy-summary:start -->
De instellingen van een product (zoals missie, repository, workspace en werklimieten) staan niet meer verspreid op de productkaart, maar overzichtelijk bij elkaar in het Instellingenscherm. Daar kun je nu ook direct de doelrepository aanpassen, en het scherm is volledig met het toetsenbord te bedienen. Er is verder niets aan de werking van producten veranderd.
<!-- deploy-summary:end -->
