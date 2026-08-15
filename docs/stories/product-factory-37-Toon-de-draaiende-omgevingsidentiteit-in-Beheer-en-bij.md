# product-factory-37 - Toon de draaiende omgevingsidentiteit in Beheer en bij acceptatiebewijs

## Story

Toon de draaiende omgevingsidentiteit in Beheer en bij acceptatiebewijs

<!-- refined-by-factory -->

## Scope

- Voeg één alleen-lezen omgevingsidentiteit toe aan het dashboard, gevoed met metadata die tijdens de frontendbuild door de bouw- en uitrolstraat wordt aangeleverd.
- De identiteit bestaat uit drie onafhankelijk gevalideerde waarden:
  - `Omgeving`;
  - `Revisie/build-ID`;
  - `Uitgerold op`.
- Toon het volledige blok onder de semantische kop `Omgevingsidentiteit` in Beheer, direct na de titel `Beheer` en vóór de bestaande Beheerscope en operationele lijsten. De bestaande teruglink blijft de eerste focusbare bediening.
- Normaliseer de drie ondersteunde omgevingswaarden als volgt: `production` wordt `Productie`, `acceptance` wordt `Acceptatie` en `preview` wordt `Preview`. Iedere andere, lege of ontbrekende waarde wordt `Onbekend`.
- Gebruik voor deze repository de door de bouwstraat bepaalde bronrevisie. Accepteer daarvoor uitsluitend een volledige hexadecimale bronrevisie en toon daarvan de eerste twaalf tekens. Een ontbrekende of ongeldige waarde wordt `Onbekend`.
- Accepteer de uitroltijd uitsluitend als geldige ISO-8601-tijd met tijdzone en toon deze in de lokale browsertijd als `dd-MM-yyyy HH:mm`. Een ontbrekende of ongeldige waarde wordt `Onbekend`.
- Toon in iedere bestaande terminale bewijsregel voor `ACCEPTED`, `NEEDS_REVISION`, `REJECTED`, `NO_CHANGE` en `FAILED` één subtiele, niet-interactieve regel met uitsluitend `Omgeving: <waarde>` en `Revisie/build-ID: <waarde>`, afkomstig uit exact hetzelfde genormaliseerde presentatiemodel als het volledige blok.
- Toon de uitroltijd niet in terminale bewijsregels. Voeg de verwijzing niet toe aan actieve of onbekende-statuskaarten.
- Voeg geen laadstatus, spinner, foutscherm, uitklapgedrag, deploymentgeschiedenis of bedieningen voor deployen, vernieuwen, wijzigen of terugdraaien toe.
- Lees geen repositorybestanden in de draaiende applicatie en leid de identiteit niet af uit product-, cyclus-, kandidaat- of leveringsgegevens.
- Wijzig geen backendcontract, database, productdata, authenticatie, productselectie, cyclusgedrag of Software Factory-koppeling.
- Laat de bouwstraat voor productie, acceptatie en PR-previews expliciet de juiste omgevingswaarde, bronrevisie en één deterministisch vastgelegde UTC-tijd aan de betreffende frontendbuild meegeven. Lokale builds zonder deze waarden blijven bruikbaar en tonen per veld `Onbekend`.
- Werk de functionele, technische en deploymentdocumentatie bij met de bron, normalisatie, veilige fallbacks en betekenis van de getoonde identiteit.

## Acceptance criteria

- Componenttests bevestigen dat Beheer onder de semantische kop `Omgevingsidentiteit` de labels `Omgeving`, `Revisie/build-ID` en `Uitgerold op` in die leesvolgorde toont, waarbij ieder label programmatisch met zijn eigen waarde is verbonden.
- Een fixture met geldige metadata toont de genormaliseerde omgevingsnaam, de eerste twaalf tekens van de aangeleverde bronrevisie en de uitroltijd als lokale `dd-MM-yyyy HH:mm`.
- Tests bevestigen de gesloten omgevingsmapping voor `production`, `acceptance` en `preview`; onbekende, lege, anders gespelde of ontbrekende waarden tonen exact `Onbekend`.
- Fixtures voor geheel ontbrekende metadata, een ontbrekende of ongeldige revisie en een ontbrekende of ongeldige uitroltijd bevestigen dat uitsluitend het betreffende veld `Onbekend` wordt. Beheer, productnavigatie en cyclusweergave blijven zonder spinner of fout renderen.
- Een geïntegreerde dashboardtest opent Beheer en vergelijkt het volledige blok met alle terminale bewijsregels. Omgevingsnaam en revisie/build-ID zijn exact gelijk; de bewijsregels bevatten geen uitroltijd en blijven compact, niet-interactief en niet-uitklapbaar.
- Tests bevestigen dat actieve en onbekende-statuskaarten geen omgevingsverwijzing krijgen en dat de bestaande vijf terminale bewijswaarden, detailactie en leesvolgorde behouden blijven.
- Netwerk- en regressietests bevestigen dat het tonen en navigeren van de identiteit geen extra of muterende requests veroorzaakt en productselectie, cyclusstart, cyclusstatus, authenticatie en Software Factory-levering niet verandert.
- Negatieve fixtures met commitberichten, auteursnamen, e-mailadressen, tokenachtige waarden, geheime configuratie en interne repository-URL’s bevestigen dat deze inhoud nergens zichtbaar of semantisch opvraagbaar wordt. De test faalt zodra dergelijke inhoud wordt gerenderd; het betreffende ongeldige veld toont tijdens normaal gebruik `Onbekend`.
- Tests bevestigen dat uitsluitend de drie expliciete metadatawaarden worden gelezen en dat product- en cyclusrecords met misleidende gelijknamige velden geen invloed hebben.
- Toegankelijkheids- en layouttests bevestigen de semantische kop, gekoppelde labels en waarden, logische leesvolgorde en afwezigheid van nieuwe interactieve elementen.
- Contrastcontroles voldoen aan WCAG 2.2 AA. Screenshottests op vooraf vastgelegde brede en 320-pixels-smalle viewports, inclusief 200% tekstvergroting, tonen geen horizontale overflow of afgesneden waarden.
- Een frontend-imagebuild controleert dat alle drie metadatawaarden door de bouwstraat kunnen worden aangeleverd en dat veilige defaults gelden wanneer ze ontbreken.
- Het volledige bestaande Maven- en Flutter-verificatievangnet blijft groen.

## Aannames

- De identiteit beschrijft de frontendbuild die het dashboard momenteel serveert; zij claimt niet dat alle afzonderlijke backendonderdelen exact dezelfde revisie draaien.
- De door de bouwstraat aangeleverde bronrevisie is de autoritatieve versie-identificatie. Commitberichten, auteursinformatie en andere repositorymetadata behoren niet tot de invoer.
- `Uitgerold op` is de eenmaal door de bouwstraat vastgelegde UTC-tijd die bij de betreffende frontendbuild hoort; de browser vertaalt deze uitsluitend voor weergave naar de lokale tijdzone.
- De drie gedocumenteerde draaiende omgevingen zijn Productie, Acceptatie en Preview. Een lokale of toekomstige omgeving moet expliciet aan de gesloten mapping worden toegevoegd voordat zij anders dan `Onbekend` wordt getoond.
- Veldspecifieke validatie is onafhankelijk: één onbruikbare waarde maakt de andere twee waarden niet onbekend.
- De bestaande vijfsecondenverversing ververst operationele dashboardgegevens, maar verandert de buildgebonden omgevingsidentiteit niet.

## Eindsamenvatting

## Eindsamenvatting voor PO

De dashboardomgeving toont in Beheer voortaan de omgeving, versie en lokale uitroltijd. Afgeronde cyclusregels tonen dezelfde omgeving en verkorte versie, zonder uitroltijd; actieve en onbekende cycli blijven ongewijzigd. Alle waarden worden afzonderlijk gecontroleerd en vallen bij ontbrekende of ongeldige invoer veilig terug op `Onbekend`.

De metadata wordt tijdens de frontendbuild vastgelegd voor productie, acceptatie en previews. Er zijn bewust geen backendwijzigingen, extra netwerkverzoeken, runtime-bestandslezingen, nieuwe bedieningen of deploymentgeschiedenis toegevoegd. De relevante functionele, technische, ontwikkel- en deploymentdocumentatie is bijgewerkt.

Het volledige vangnet is groen: 164 Maven-tests, 427 Flutter-tests, analyse zonder meldingen, tests van de buildrunner en twee volledige frontend-imagebuilds. Daarnaast zijn 60 gerichte tests en browsercontroles uitgevoerd op de echte preview, inclusief toegankelijkheid, privacy, alleen-lezen netwerkgedrag, 320-pixelsbreedte en 200% tekstvergroting. De storybranch is nog niet samengevoegd of naar productie uitgerold.

<!-- deploy-summary:start -->
In Beheer zie je voortaan in welke omgeving het dashboard draait, welke versie actief is en wanneer die is uitgerold. Bij afgeronde productcycli staan dezelfde omgeving en versie, zodat je bewijsregels makkelijker aan de juiste draaiende versie kunt koppelen. Ontbrekende of ongeldige informatie wordt veilig als onbekend getoond.
<!-- deploy-summary:end -->
