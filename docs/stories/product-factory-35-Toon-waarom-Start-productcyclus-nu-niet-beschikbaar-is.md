# product-factory-35 - Toon waarom ‘Start productcyclus nu’ niet beschikbaar is

## Story

Toon waarom ‘Start productcyclus nu’ niet beschikbaar is

<!-- refined-by-factory -->

## Scope

- Breng de beschikbaarheid van `Start productcyclus nu` onder in één gedeeld presentatiemodel dat uitsluitend de productstatus en het workspace-eigenaarschap beoordeelt.
- Behoud de bestaande startbeslissing exact: starten is alleen beschikbaar wanneer de productstatus exact `active` is en het workspace-eigenaarschap exact `product-factory` is.
- Toon bij een uitgeschakelde startknop direct en zonder uitklappen exact één primaire reden:
  - bij ontbrekende of onbekende metadata: `Startbeschikbaarheid kan niet betrouwbaar worden vastgesteld.`
  - bij een bekende, niet-actieve productstatus: `Starten is niet beschikbaar omdat dit product niet actief is.`
  - bij een actief product waarvan de workspace niet door Product Factory wordt beheerd: `Starten is niet beschikbaar omdat deze workspace niet door Product Factory wordt beheerd.`
- Bepaal de primaire reden in deze vaste volgorde: ontbrekende of onbekende metadata, product niet actief, workspace niet door Product Factory beheerd.
- Tel iedere niet-vervulde startvoorwaarde eenmaal. Zijn beide voorwaarden niet vervuld, toon naast de primaire reden: `Daarnaast is nog 1 andere voorwaarde niet vervuld.`
- Bied vanuit de blokkademelding een toetsenbordbedienbare actie `Bekijk productdetails` aan. Deze opent binnen dezelfde productscope een kleine read-only detailweergave voor het geselecteerde product.
- Toon in de detailweergave uitsluitend:
  - dezelfde primaire reden en eventuele aanvullende telling;
  - `Productstatus` als `Actief`, `Niet actief` of `Onbekend`;
  - `Workspacebeheer` als `Door Product Factory beheerd`, `Niet door Product Factory beheerd` of `Onbekend`;
  - de niet-vervulde voorwaarden als gebruikersgerichte teksten `Product moet actief zijn.` en/of `Workspace moet door Product Factory worden beheerd.`
- Gebruik voor de detailweergave alleen het reeds geladen geselecteerde product. Voeg geen gegevens van andere producten, ruwe backendwaarden, technische identifiers of overige productconfiguratie toe.
- Voeg in de blokkademelding en detailweergave geen verversings-, herstel-, annuleer-, retry-, statuscorrectie- of andere muterende actie toe. De bestaande algemene verversing van het productoverzicht blijft ongewijzigd.
- Een zichtbare of langlopende `RUNNING`-cyclus, de looptijd daarvan en andere gegevens buiten de twee startvoorwaarden hebben geen invloed op de beschikbaarheidsuitkomst of melding.
- Wijzig het bestaande startverzoek, de foutafhandeling, API-contracten, opslag, telemetrie, cyclusprocessen en Software Factory-processen niet.

## Acceptance criteria

- Een tabelgedreven test dekt alle combinaties van de gedragscategorieën voor beide velden: geldig en voldoende, geldig maar onvoldoende, ontbrekend en onbekend.
- De tabelgedreven test bewijst dat de startknop uitsluitend actief is voor exact `status == active` en `workspaceOwnership == product-factory`; alle overige combinaties blijven uitgeschakeld.
- Een ontbrekende waarde is een afwezige sleutel, `null` of lege tekst. Een onbekende waarde is een waarde van een ander type of een niet-lege tekst buiten de bekende waarden. Waarden worden niet getrimd, genormaliseerd of hoofdletterongevoelig vergeleken.
- Bij de combinatie `active` en `product-factory` is de startknop actief en ontbreken zowel de blokkademelding als de detailactie.
- Bij iedere uitgeschakelde combinatie staat direct bij de knop precies één niet-lege primaire reden met de vastgelegde tekst en prioriteit.
- Wanneer beide startvoorwaarden niet zijn vervuld, blijft de primaire reden ongewijzigd en wordt exact één overige onvervulde voorwaarde gemeld.
- De knopstatus, primaire reden, aanvullende telling, statuspresentatie en lijst met ontbrekende voorwaarden worden uit hetzelfde beschikbaarheidsmodel afgeleid.
- De read-only detailweergave opent voor het geselecteerde product zonder nieuwe netwerkrequest, behoudt de actieve productscope en bevat geen bewerkbare velden of andere acties dan sluiten.
- De detailactie is met Tab bereikbaar en met Enter en Spatie te activeren. Sluiten via de zichtbare sluitactie of Escape herstelt de focus naar de detailactie.
- Widget- en semantische tests bewijzen dat de uitgeschakelde startactie, primaire reden en aanvullende context programmatisch als één betekenisvolle groep zijn gekoppeld en niet uitsluitend via kleur of visuele nabijheid worden gecommuniceerd.
- Geautomatiseerde tests vergelijken dezelfde geldige productfixture met en zonder een langlopende `RUNNING`-cyclus en bewijzen dat knopstatus, blokkademelding en detailinhoud identiek blijven.
- Tests bewijzen dat teksten over looptijd, stilstand of een `RUNNING`-cyclus nooit als blokkadeoorzaak verschijnen.
- Tests bewijzen dat het beschikbaarheidsmodel geen andere product- of cyclusvelden consumeert en dat de melding en detailweergave geen ruwe onbekende waarden, technische identifiers of gegevens van andere productfixtures renderen.
- De bestaande startactie blijft bij beschikbaarheid hetzelfde startverzoek uitvoeren.
- De volledige bestaande frontend-analyse en frontend-tests blijven slagen.

## Aannames

- De bekende productstatussen zijn `active`, `draft`, `paused` en `archived`; alleen `active` vervult de startvoorwaarde.
- De bekende waarden voor workspace-eigenaarschap zijn `product-factory` en `owner`; alleen `product-factory` vervult de startvoorwaarde.
- `draft`, `paused` en `archived` worden gebruikersgericht samengevat als `Niet actief`; `owner` wordt samengevat als `Niet door Product Factory beheerd`.
- Een onbekende of ontbrekende waarde blijft een onvervulde voorwaarde, zodat de bestaande uitgeschakelde toestand behouden blijft.
- Er zijn twee startvoorwaarden. Daardoor kan naast de primaire reden momenteel maximaal één overige onvervulde voorwaarde worden gemeld.
- De nieuwe productdetailweergave is een lokale read-only dialoog of gelijkwaardig paneel binnen het bestaande productoverzicht en vormt geen nieuwe route.
- De algemene periodieke en handmatige verversing van het productoverzicht blijft bestaan, maar krijgt binnen deze story geen nieuwe koppeling vanuit de blokkademelding of detailweergave.

## Eindsamenvatting

### Eindsamenvatting voor de PO

Gebouwd: de startknop toont nu direct één duidelijke blokkeerreden en, indien beide voorwaarden falen, één aanvullende melding. Via `Bekijk productdetails` opent een toetsenbordbedienbare, alleen-lezen weergave met veilige statuslabels en onvervulde voorwaarden. Starten blijft uitsluitend mogelijk bij status `active` en workspacebeheer `product-factory`.

Keuzes: alle presentatie wordt uit hetzelfde model afgeleid. Ontbrekende of onbekende gegevens krijgen prioriteit als reden; daarna volgen een niet-actief product en onjuist workspacebeheer. Lopende cycli hebben bewust geen invloed.

Getest: 80 combinaties van geldige, onvoldoende, ontbrekende en onbekende waarden; daarnaast toegankelijkheid, toetsenbordbediening, focusherstel, privacy, netwerkgedrag, het bestaande startverzoek en onafhankelijkheid van `RUNNING`-cycli. De gerichte suite had 93 geslaagde tests; de volledige backendcontrole, frontend-analyse en alle 400 frontendtests slaagden. Image-builds zijn conform het factory-vangnet aan CI overgelaten.

Bewust niet gewijzigd: API’s, opslag, telemetrie, cyclusprocessen, startverzoek, foutafhandeling, algemene verversing en Software Factory-processen. Er zijn geen herstel-, retry- of andere muterende acties toegevoegd.

<!-- deploy-summary:start -->
Als een productcyclus niet gestart kan worden, zie je voortaan meteen waarom. Via “Bekijk productdetails” krijg je veilig meer uitleg over wat nog niet aan de voorwaarden voldoet, terwijl lopende cycli de beschikbaarheid niet beïnvloeden.
<!-- deploy-summary:end -->
