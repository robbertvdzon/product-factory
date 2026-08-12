# product-factory-28 - Toon per cyclus een eenduidig bewijsbare beslisbron met expliciete onbekend-toestand

## Story

Toon per cyclus een eenduidig bewijsbare beslisbron met expliciete onbekend-toestand

<!-- refined-by-factory -->

## Scope

- Toon in iedere bestaande cyclusregel één zichtbare bediening met exact de tekst `Beslisbron: Evaluatie-agent`, `Beslisbron: Technische fout` of `Beslisbron: Onbekend`.
- Leid de waarde centraal en read-only af uit de reeds aangeleverde velden `criticVerdict`, `status` en `errorMessage`.
- Gebruik een pure, herbruikbare classificatiefunctie die ontbrekende en uitsluitend uit witruimte bestaande waarden als afwezig behandelt.
- Classificeer als `Evaluatie-agent` wanneer verdict en eindstatus exact een bewezen engine-uitkomst vormen:
  - `ACCEPT` met `ACCEPTED`;
  - `REVISE` met `NEEDS_REVISION`;
  - `REJECT` met `REJECTED`.
- Classificeer als `Technische fout` uitsluitend bij `FAILED`, een ontbrekend verdict en een niet-lege foutmelding.
- Classificeer iedere andere, ontbrekende, onbekende, ambigue of tegenstrijdige combinatie als `Onbekend`.
- Laat de beslisbron via één native button de bestaande detailweergave van precies de geselecteerde cyclus openen. De rijcontainer is niet daarnaast een tweede detailbediening en de button is niet genest in een andere interactieve bediening.
- Behoud de bestaande uitkomstbadge of voortgangsindicator, detailinhoud en afzonderlijke annuleeractie.
- De wijziging blijft beperkt tot de frontend. API’s, contracten, backend, opslag, statussen, leveringsselectie en de vervanging of uitsluiting van kandidaten 53–55 vallen buiten deze story.

## Acceptance criteria

- De classificatiefunctie retourneert uitsluitend `Evaluatie-agent`, `Technische fout` of `Onbekend`.
- `ACCEPT` met `ACCEPTED`, `REVISE` met `NEEDS_REVISION` en `REJECT` met `REJECTED` retourneren exact `Evaluatie-agent`, mits beide waarden aanwezig zijn.
- Het warning-only enginepad, dat daadwerkelijk als `ACCEPT` met `ACCEPTED` wordt aangeleverd, retourneert via die bewezen combinatie `Evaluatie-agent`.
- De niet-opgeslagen waarde `WARNING_ONLY_REVISE` wordt niet afgeleid of gesimuleerd en retourneert voor iedere status, waaronder `NEEDS_REVISION`, exact `Onbekend`.
- `FAILED` met een ontbrekend of leeg verdict en een niet-lege `errorMessage` retourneert exact `Technische fout`.
- `FAILED` zonder niet-lege foutmelding en `FAILED` met enig niet-leeg verdict retourneren exact `Onbekend`.
- `ACCEPT` met `REJECTED` retourneert exact `Onbekend`, omdat de eindstatus in dit bestaande guardrailpad niet rechtstreeks door het positieve verdict wordt verklaard.
- Parametrische tests dekken alle combinaties van `ACCEPT`, `REVISE`, `REJECT` en `WARNING_ONLY_REVISE` met `ACCEPTED`, `NEEDS_REVISION`, `REJECTED` en `FAILED`, plus ontbrekende, lege, uitsluitend uit witruimte bestaande en onbekende waarden.
- Een uitputtende mappingtest borgt de gesloten waardenverzameling en bevestigt dat geen invoer `Mens`, `Guardrail` of een andere niet-toegestane beslisbron kan produceren.
- Iedere cyclusregel toont het volledige tekstlabel `Beslisbron: <waarde>`; de betekenis is niet uitsluitend afhankelijk van kleur, positie of een pictogram.
- De beslisbron is een native button en opent met klik, Enter en Spatie de bestaande detailweergave met het cyclusnummer en de gegevens van de geactiveerde cyclus.
- De detailbediening gebruikt geen losse statische click-handler en is niet genest in een actieve rij of andere interactieve bediening.
- Sluiten via de zichtbare sluitactie en via Escape herstelt de focus naar de beslisbronbutton waarmee de detailweergave werd geopend.
- Een widgettest met minimaal twee cycli en per cyclus unieke synthetische redenstekst en cyclusnummers bewijst dat na activering uitsluitend de gekozen cyclus in de detailweergave staat; tekst die alleen bij de andere fixturecyclus hoort komt niet in de geopende detailweergave voor.
- Het overzicht rendert geen ruwe `errorMessage`, prompts, logs of artefactinhoud als onderdeel van de beslisbron.
- Activeren en sluiten van de beslisbron/detailweergave veroorzaakt uitsluitend bestaande leesverzoeken en geen schrijfverzoek of statusmutatie. De afzonderlijke, reeds bestaande annuleeractie voor een lopende cyclus blijft buiten deze controle en verandert niet.
- Bestaande tests voor uitkomstbadges, voortgangsindicatoren en de detailweergave blijven slagen.

## Aannames

- De bestaande frontendgegevens bevatten voldoende bewijs; er is geen aanvullende databron nodig.
- Na het verwijderen van omringende witruimte worden verdicts en statussen hoofdlettergevoelig met de gedocumenteerde waarden vergeleken. Afwijkende schrijfwijzen vallen veilig terug op `Onbekend`.
- Lopende of wachtende cycli hebben nog geen bewijsbare eindbeslisbron en tonen daarom `Onbekend`.
- De bestaande detailweergave mag de reeds beschikbare uitgebreide cyclusinformatie blijven tonen; de beperking op ruwe inhoud geldt voor het overzicht.
- Het vervangen en gelijktijdig uitsluiten van kandidaten 53–55 wordt door de orchestrator afgehandeld en is geen voorwaarde of implementatieonderdeel van deze frontend-story.

## Eindsamenvatting

## Eindsamenvatting voor de PO

Gebouwd:

- Iedere cyclusregel toont nu één beslisbron: `Evaluatie-agent`, `Technische fout` of `Onbekend`.
- De classificatie wordt centraal, read-only en conservatief afgeleid uit verdict, status en foutmelding. Ontbrekende, lege, onbekende of tegenstrijdige waarden leveren `Onbekend` op.
- De beslisbron is een toetsenbordbedienbare knop die de details van precies de gekozen cyclus opent. Na sluiten of Escape keert de focus terug naar die knop.
- De cyclusrij zelf is niet meer klikbaar en de misleidende navigatie-chevron is verwijderd.

Keuzes en afbakening:

- Bestaande uitkomstbadges, voortgang, detailinhoud en de afzonderlijke annuleeractie zijn behouden.
- Het overzicht toont via de beslisbron geen foutmeldingen, prompts, logs of artefactinhoud.
- Er zijn geen API-, backend-, opslag- of contractwijzigingen gedaan.

Testbewijs:

- 45 gerichte classificatie- en bedieningstests zijn geslaagd, inclusief alle voorgeschreven combinaties, toetsenbordbediening, focusherstel, selectie van de juiste cyclus en uitsluitend leesverzoeken.
- De volledige frontendtestset is 217/217 groen, de statische analyse meldt geen problemen en de backend-verificatie is geslaagd.
- Een verouderde bestaande testfixture is aangepast aan de al aanwezige roadmapactie. De taakcontext vermeldt daarnaast dat de storybrede testfase is goedgekeurd; een afzonderlijk worklog voor `product-163` was niet aanwezig.

<!-- deploy-summary:start -->
Bij elke productcyclus staat nu duidelijk waarop de beslissing is gebaseerd: de evaluatie-agent, een technische fout of een onbekende oorzaak. Via deze tekst open je de details van precies die cyclus, ook met het toetsenbord.
<!-- deploy-summary:end -->
