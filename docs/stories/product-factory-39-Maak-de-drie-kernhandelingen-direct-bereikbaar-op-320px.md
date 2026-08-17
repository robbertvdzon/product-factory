# product-factory-39 - Maak de drie kernhandelingen direct bereikbaar op 320px

## Story

Maak de drie kernhandelingen direct bereikbaar op 320px

<!-- refined-by-factory -->

## Scope

Wijzig uitsluitend de responsieve presentatie van het productgebonden hoofdscherm op viewports van maximaal 320 CSS-pixels.

Toon daar een compacte omgevingsaanduiding en bied vervolgens de actieve productscope, cyclusstart, recente cycli en gekoppelde stories als eerste inhoud aan. Binnen `Overzicht` is de onderlinge zichtbare, semantische en DOM-volgorde:

1. actieve productkeuze en productnaam;
2. de bestaande cyclusstartactie, inclusief de bestaande verklaring en detailactie wanneer starten niet beschikbaar is;
3. recente toestandsbewuste cycli;
4. gekoppelde stories;
5. `Operationele samenvatting`.

Andere bestaande overzichtsonderdelen, waaronder de bugsamenvatting, productbeheer, benodigde access tokens en workspace-publicaties, blijven bereikbaar en volgen na deze kerninhoud.

Vervang op maximaal 320 CSS-pixels de horizontale sectienavigatie door één native, zichtbaar en toegankelijk gelabelde sectiekeuze. Deze bevat in deze volgorde:

1. Overzicht;
2. Productcycli;
3. Stories;
4. Roadmap;
5. Bugs;
6. Epics;
7. Testsessies;
8. Overleggen.

`Overzicht` toont cyclusstart, recente cycli en gekoppelde stories achter elkaar. `Productcycli` en `Stories` blijven daarnaast afzonderlijke secties voor gerichte bediening. De overige bestaande secties en hun gedrag blijven eveneens beschikbaar.

Plaats de vijf bestaande metriekwaarden op maximaal 320 CSS-pixels in een standaard ingeklapte `Operationele samenvatting`. Behoud hun bestaande waarden, laad- en foutstatussen en product- of globale scope.

Behoud op bredere viewports de bestaande sectienavigatie, volgorde en direct zichtbare metriekpresentatie. Wijzig geen gegevensbronnen, bewijswaarden, sortering, lijstbeperking, startvoorwaarden, authenticatie, productscope, Software Factory-koppeling of muterend gedrag.

## Acceptance criteria

- Een geautomatiseerde viewporttest op 320×900 toont in de initiële viewport zonder gebruikersscroll de compacte omgevingsaanduiding, de actieve productnaam en de bestaande cyclusstartactie.
- Wanneer starten niet beschikbaar is, blijft de bestaande primaire verklaring zichtbaar en programmatisch aan de uitgeschakelde actie gekoppeld. De bestaande detailactie en startvoorwaarden blijven ongewijzigd.
- Op maximaal 320 CSS-pixels is de onderlinge DOM-, zichtbare en semantische volgorde van de inhoudsblokken exact: productscope, cyclusstart, recente cycli, gekoppelde stories en operationele samenvatting. Deze volgorde wordt niet met uitsluitend visuele CSS-herordening gerealiseerd.
- De mobiele sectiekeuze is een native bediening met een zichtbare en toegankelijke naam en bevat exact de acht vastgelegde opties in de vastgelegde volgorde. Een geautomatiseerd toetsenbordscenario bereikt en activeert iedere optie zonder horizontaal scrollen.
- `Overzicht` toont cyclusstart, recente cycli en gekoppelde stories achter elkaar. De afzonderlijke keuzes `Productcycli` en `Stories` tonen dezelfde productgebonden gegevens en bestaande acties zonder extra of afwijkende records.
- Widget- of snapshottests met de bestaande synthetische Product Factory-data bevestigen dat de herordening de cycluspresentatie niet verandert: een actieve cyclus toont uitsluitend de bestaande voortgangsinformatie en terminale cycli behouden alle vijf bewijswaarden, de compacte omgevingsreferentie en hun detailactie.
- De `Operationele samenvatting` is op maximaal 320 CSS-pixels standaard ingeklapt. De bediening is een button met een correcte `aria-expanded`-status; ingeklapte inhoud staat niet in de focus- of toegankelijkheidsvolgorde. Na uitklappen zijn alle vijf bestaande metriekwaarden met hun actuele laad-, fout- of succesweergave beschikbaar.
- Geautomatiseerde tests op 320×900 en op een brede viewport bevestigen dat de actieve productscope en gekozen sectie behouden blijven tijdens detailnavigatie en automatische verversing. Sluiten via de zichtbare sluitactie of Escape herstelt de focus logisch naar de oorspronkelijke cyclus- of storyactie.
- Op een brede viewport blijven de bestaande horizontale sectienavigatie, sectielabels, sectiegedrag en direct zichtbare metriekpresentatie ongewijzigd.
- Contract- en integratietests bevestigen dat de wijziging geen API, contractveld, opslag, telemetrie, extra leesaanroep of nieuwe muterende aanroep introduceert. Geen enkele weergave toont cycli, stories of andere productgegevens van een andere productslug binnen de actieve productscope.
- Geautomatiseerde contrastcontroles op 320×900 en een brede viewport bevestigen WCAG AA-contrast voor tekst en essentiële iconen en minimaal 3:1 contrast voor zichtbare focusindicatoren van ten minste de mobiele sectiekeuze, cyclusstartactie, cyclusdetailacties, storyacties en de knop `Operationele samenvatting`.

## Aannames

- `Maximaal 320 CSS-pixels` is inclusief een viewportbreedte van exact 320 pixels; bredere viewports volgen het bestaande desktopgedrag.
- De compacte omgevingsaanduiding hergebruikt de bestaande veilige, genormaliseerde waarden voor `Omgeving` en `Revisie/build-ID`. `Uitgerold op` wordt in deze compacte aanduiding niet toegevoegd.
- `Productcycli` is op mobiel het label voor de bestaande sectie `Productsessies`; gegevens, sortering, toestandspresentatie, detailbediening en de bestaande 5/+10-lijstbeperking veranderen niet.
- Recente cycli en gekoppelde stories in `Overzicht` komen uit de reeds geladen, exact op de actieve productslug gefilterde gegevens. De presentatie veroorzaakt geen aanvullende netwerkverzoeken.
- De PO-opmerking is leidend: Epics, Testsessies en Overleggen blijven onderdeel van de mobiele sectiekeuze, ondanks het eerdere acceptatiecriterium dat slechts vijf opties noemde.
- Bestaande overzichtsfuncties die niet tot de drie kernhandelingen of metriekwaarden behoren, verdwijnen niet; ze volgen op mobiel na de kerninhoud.

## Eindsamenvatting

### Eindsamenvatting voor PO

Gebouwd: voor schermen tot en met 320 pixels staat de kerninhoud nu in de afgesproken volgorde: actieve productscope, cyclusstart, recente cycli, gekoppelde stories en de standaard ingeklapte operationele samenvatting. De horizontale navigatie is daar vervangen door een toegankelijke sectiekeuze met alle acht opties. Omgevingsinformatie wordt compact getoond en detailvensters herstellen na sluiten de focus naar de oorspronkelijke actie. Brede schermen behouden hun bestaande presentatie en gedrag.

Keuzes: bestaande gegevens, filters, sortering, lijstlimieten en acties worden hergebruikt. Er zijn geen extra verzoeken, API-, contract-, opslag-, telemetrie- of dependencywijzigingen toegevoegd. Ook zonder actief product blijven de vijf metrieken en de eventuele acceptatiemelding bereikbaar.

Getest: het volledige toepasselijke vangnet was groen, waaronder Maven, frontendanalyse, 450 Flutter-tests, browser-DOM-tests, Docker Engine-tests en beide frontend-imagebuilds. Daarnaast slaagden 40 gerichte tests en controles in een echte preview op mobiel en desktop voor volgorde, toetsenbordbediening, focusherstel, contrast, productscope en verversing.

Bewust niet uitgevoerd: de agent-imagebuild blijft conform de factoryconfig aan CI voorbehouden. De functionele en technische dashboarddocumentatie is bijgewerkt; merge en productie-uitrol horen bij de volgende subtaken en zijn nog niet uitgevoerd.

<!-- deploy-summary:start -->
Op zeer smalle schermen staan de productkeuze, de knop om een cyclus te starten, recente cycli en gekoppelde stories voortaan direct bij elkaar. De overige onderdelen blijven bereikbaar via een duidelijke keuzelijst en de operationele cijfers kunnen naar behoefte worden uitgeklapt.
<!-- deploy-summary:end -->
