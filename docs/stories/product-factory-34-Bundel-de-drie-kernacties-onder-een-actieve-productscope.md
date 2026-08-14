# product-factory-34 - Bundel de drie kernacties onder één actieve productscope

## Story

Bundel de drie kernacties onder één actieve productscope

<!-- refined-by-factory -->

## Scope

- Vervang op het hoofdscherm de volledige productkaarten door een compacte productkeuze en één actieve productscope. Wanneer minstens één product beschikbaar is, is exact één product actief en blijft de naam daarvan zichtbaar.
- Gebruik uitsluitend de volgende canonieke, hoofdlettergevoelige mapping:
  - product: de niet-lege `Product.slug`;
  - cyclus: de niet-lege `Iteration.productSlug`;
  - bewijsregel: de `productSlug` van de bijbehorende `Iteration`;
  - story: de niet-lege `StoryCandidate.productSlug`.
- Centraliseer selectie, filtering, tellingen, lokale voorkeur en detailnavigatie in gedeelde frontendlogica. Normaliseer, trim of vervang productslugs niet en gebruik geen namen, ids, lijstposities of andere velden als fallback.
- Herstel na het laden een lokaal opgeslagen slug alleen wanneer deze exact bij één beschikbaar product hoort. Kies anders het eerste product in de door de API ontvangen volgorde. Verwijder een opgeslagen onbekende of niet meer beschikbare slug.
- Bewaar uitsluitend de actieve canonieke productslug lokaal. Productdata en andere bronrecords worden niet lokaal gekopieerd of gewijzigd.
- Toon binnen de actieve productscope, in deze vaste volgorde:
  1. `Cyclus starten`, met het bestaande startgedrag en de bestaande voorwaarden voor beschikbaarheid;
  2. `Eerdere cycli`, met uitsluitend cycli waarvan `Iteration.productSlug` exact overeenkomt met de actieve `Product.slug`;
  3. `Gekoppelde stories`, met uitsluitend storykandidaten die dezelfde productslug hebben en via `StoryCandidate.iterationSequenceNumber` exact en eenduidig zijn gekoppeld aan het `sequenceNumber` van een cyclus binnen die productscope.
- Behoud voor daarvoor reeds geschikte cycli de compacte, niet standaard uitklapbare bewijsregels met de bestaande afzonderlijk gelabelde waarden. Andere cyclusstatussen behouden hun bestaande kaart-, start-, annuleer-, hervat- en detailgedrag.
- Sluit records met een ontbrekende, anders getypeerde, lege, kruisproduct- of ambigue relatie uit van de productscope. Er wordt geen relatie geschat.
- Laat een productwissel uitsluitend de lokale presentatiestatus en opgeslagen voorkeur wijzigen. De reeds geladen bronobjecten blijven ongewijzigd en de wissel veroorzaakt geen API-aanroep.
- Open `Beheer` met dezelfde actieve productslug. Bied daar naast iedere afzonderlijke productscope de expliciete keuze `Alle producten`; deze keuze bestaat niet op het hoofdscherm.
- Filter in Beheer de storywachtrij via `StoryCandidate.productSlug`. Bepaal de scope van een Software Factory-levering via haar eenduidige bestaande koppeling aan een storykandidaat en vervolgens via `StoryCandidate.productSlug`; gebruik de productslug van de levering niet als alternatieve canonieke bron.
- Toon de gekozen Beheer-scope zichtbaar in iedere lijstkop. `Alle producten` behoudt de bestaande globale lijsten en is de enige Beheer-scope waarin records zonder eenduidig bepaalbare productrelatie mogen verschijnen.
- Voeg geen backendopslag, endpoint, contractveld, muterende API, routing, telemetrie, database- of infrastructuurwijziging toe.

## Acceptance criteria

- Geautomatiseerde contracttests met de bestaande modellen en synthetische fixtures bewijzen de vaste mapping `Product.slug` → `Iteration.productSlug` → bewijsregel via `Iteration.productSlug` → `StoryCandidate.productSlug`.
- De contracttests bewijzen dat selectie, filtering, tellingen, lokale opslag en detailnavigatie uitsluitend niet-lege, exact overeenkomende canonieke slugs gebruiken. Ontbrekende, anders getypeerde, afwijkende en ambigue relaties worden niet toegeschreven.
- Met minstens twee producten herstelt een geautomatiseerde test een geldige lokaal opgeslagen slug. Bij een ontbrekende, lege, onbekende of niet meer beschikbare voorkeur wordt exact het eerste product in de ontvangen API-volgorde actief en wordt de ongeldige voorkeur verwijderd.
- Wanneer geen producten beschikbaar zijn, wordt een bestaande voorkeur verwijderd, verschijnt een duidelijke lege toestand en worden geen productgebonden acties of resultaten aan een fictieve scope toegeschreven.
- Na laden bevatten `Eerdere cycli`, de bewijsregels, `Gekoppelde stories` en alle daarbij getoonde aantallen uitsluitend records die exact tot de actieve productscope behoren.
- De actieve productnaam en de secties `Cyclus starten`, `Eerdere cycli` en `Gekoppelde stories` staan op brede en smalle viewports in deze zichtbare, programmatisch bepaalbare lees-, DOM- en tabvolgorde.
- `Cyclus starten` roept voor het actieve product het bestaande startgedrag aan en behoudt de bestaande beschikbaarheidsvoorwaarden, foutafhandeling en terugkoppeling.
- `Eerdere cycli` behoudt de bestaande sorteervolgorde, lijstbeperking en detailbediening. De bestaande compacte bewijsregels blijven niet standaard uitklapbaar en tonen hun afzonderlijk gelabelde velden.
- Iedere actie vanuit `Gekoppelde stories` opent het bestaande detail van precies de gekozen `StoryCandidate`. De kandidaat heeft dezelfde productslug als de gekoppelde cyclus en haar `iterationSequenceNumber` verwijst exact en eenduidig naar het `sequenceNumber` van die cyclus.
- Een geautomatiseerde netwerk- en toestandstest wisselt tussen twee producten en bewijst dat alleen de zichtbare scope en lokale voorkeur wijzigen, de geladen bronobjecten inhoudelijk ongewijzigd blijven en geen HTTP-request door de wissel wordt gestart.
- De productkeuze is volledig met toetsenbord te bedienen en exposeert een toegankelijke naam, rol en actuele waarde plus een zichtbare focusindicator. Na een wissel blijft de focus op de keuze.
- Een `role=status`/`aria-live=polite`-equivalent meldt na een wissel het gekozen product en de bijgewerkte aantallen zonder focus te verplaatsen.
- Beheer opent standaard met de actieve canonieke productslug. Iedere lijstkop vermeldt zichtbaar de gekozen productnaam of `Alle producten`.
- Geautomatiseerde tests bewijzen dat een afzonderlijke Beheer-scope alleen eenduidig aan dat product gekoppelde kandidaten en leveringen toont. `Alle producten` toont de bestaande globale lijsten, inclusief records met een ontbrekende of ambigue productrelatie.
- Een afzonderlijk product kiezen in Beheer maakt dat product ook actief op het hoofdscherm en slaat zijn slug op. `Alle producten` is tijdelijke Beheer-presentatiestatus, wordt niet opgeslagen en vervangt de laatst gekozen afzonderlijke productslug niet.
- Bestaande geautomatiseerde tests voor cyclusstart, bewijsregels, cyclusdetails, storydetails, lijstbeperking, automatische verversing en Beheer blijven slagen. Er zijn geen wijzigingen aan backendcontracten, database of infrastructuur.

## Aannames

- De volgorde van de producten zoals ontvangen van de API is leidend; de frontend sorteert deze niet voordat de fallbackselectie wordt bepaald.
- Productslugs zijn hoofdlettergevoelig. Omringende witruimte en andere mogelijke schrijfwijzen worden niet gecorrigeerd.
- `Eerdere cycli` bevat alle bestaande cyclusrecords binnen de actieve productscope, inclusief eventueel nog lopende cycli; deze story introduceert geen nieuwe statusfilter.
- `Gekoppelde stories` betekent dat een kandidaat naast een overeenkomende productslug ook exact en eenduidig aan een geladen cyclus is gekoppeld via het bestaande cyclusvolgnummer.
- Een levering ontleent haar productscope in Beheer aan haar bestaande kandidaatkoppeling en de canonieke slug van die kandidaat. Ontbreekt die kandidaat of is de koppeling ambigu, dan verschijnt de levering alleen onder `Alle producten`.
- Bestaande hoofdschermonderdelen buiten de vervangen productkaarten en de drie geordende productonderdelen behouden hun huidige gedrag. Alleen cyclus- en storytellingen die binnen de actieve productscope worden getoond, worden opnieuw berekend voor die scope.
- De lokaal opgeslagen voorkeur is browser- en gebruikersprofielgebonden; er wordt geen synchronisatie tussen browsers of accounts toegevoegd.

## Eindsamenvatting

De productkaarten zijn vervangen door één duidelijke, actieve productkeuze. Het hoofdscherm toont voor dat product achtereenvolgens `Cyclus starten`, `Eerdere cycli` en `Gekoppelde stories`, met bijbehorende tellingen en details.

De selectie gebruikt uitsluitend exacte, niet-lege productcodes. Een geldige lokale voorkeur wordt hersteld; anders wordt het eerste beschikbare product gekozen en een ongeldige voorkeur verwijderd. Wisselen blijft lokaal, verandert geladen gegevens niet en veroorzaakt geen extra netwerkverzoek.

Beheer opent met hetzelfde actieve product. Daar kan tijdelijk `Alle producten` worden gekozen; afzonderlijke scopes tonen alleen eenduidig gekoppelde kandidaten en leveringen. Tijdens review zijn samengestelde laad- en foutstatussen hersteld en zijn statusmeldingen tussen hoofdscherm en Beheer gescheiden.

Gecontroleerd met een volledige backendbuild, statische frontendcontrole en 314 frontendtests. Daarnaast slaagden 43 gerichte storytests en browsercontroles op brede en smalle schermen, inclusief toetsenbordbediening, focusbehoud, scopefiltering en netwerkstilte. Er zijn geen backendcontracten, database, infrastructuur of API’s gewijzigd; functionele documentatie volgt in de aparte documentatiesubtaak.

<!-- deploy-summary:start -->
Je kiest voortaan één actief product en ziet direct de bijbehorende cycli en gekoppelde stories. Ook in Beheer kun je per product filteren of tijdelijk alles bekijken.
<!-- deploy-summary:end -->
