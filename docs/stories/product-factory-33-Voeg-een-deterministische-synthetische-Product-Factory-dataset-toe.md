# product-factory-33 - Voeg een deterministische synthetische Product Factory-dataset toe aan acceptatie

## Story

Voeg een deterministische synthetische Product Factory-dataset toe aan acceptatie

<!-- refined-by-factory -->

## Scope

- Voeg uitsluitend voor de standing acceptatieomgeving een afzonderlijke, versieerbare synthetische dataset toe voor exact productslug `product-factory`.
- De dataset bevat exact vier fixturecycli, herkenbaar aan gereserveerde vaste identifiers:
  - één actieve cyclus met status `RUNNING`, zonder beslisrecord en zonder terminale bewijsregel;
  - één terminale `FAILED`-cyclus met een geldig gekoppeld beslisrecord met `HUMAN`, `MANUAL_CANCELLATION` en `MANUALLY_CANCELLED`, zodat de bestaande presentatie een expliciete menselijke beslisbron en handmatige annulering toont;
  - één terminale `ACCEPTED`-cyclus met `criticVerdict` `ACCEPT` en zonder beslisrecord, zodat de bestaande presentatie `Evaluatie-agent (Afgeleid)` toont;
  - één terminale `REJECTED`-cyclus met `criticVerdict` `ACCEPT` en zonder beslisrecord, zodat het bestaande conservatieve guardrailpad `Onbekend (Afgeleid)` toont.
- Koppel aan de afgeleide `ACCEPTED`-cyclus exact twee volledig synthetische, voltooide storyleveringen met bijbehorende kandidaten. Gebruik vaste titels, statussen, externe sleutels en cyclusrelaties. De records zijn lokaal leesbare acceptatiedata en mogen geen externe levering, synchronisatie of reconciliatie starten.
- Laat ten minste de onbekende terminale cyclus zonder kandidaten en zonder storyleveringen, zodat de bestaande bewijsregel daar `Gekoppelde opbrengst: 0` toont.
- Gebruik voor alle fixturecycli, beslisrecords, kandidaten en leveringen vooraf vastgelegde identifiers, cyclussnummers, teksten, statussen en UTC-tijdstippen. De onderlinge sortering en koppelingen mogen niet afhangen van de actuele tijd, gegenereerde database-identiteiten of vrije invoer.
- Maak het laden idempotent met een eigen acceptatie-seedversie. Een reeds exact aanwezige fixture is een no-op; een gereserveerde identifier met afwijkende inhoud veroorzaakt een duidelijke fout en wordt niet overschreven.
- Valideer vóór opslag het volledige fixturemodel tegen een gesloten allowlist van toegestane velden en exacte waarden. De validator accepteert geen andere productslug, extra velden, vrije gebruikersinvoer, persoonsgegevens, prompts, tokens, stacktraces, productie-identifiers of verwijzingen naar echte `hkh-autopilot`-records.
- Laat de bestaande PR-previewdataset ongewijzigd. De acceptatie-seed mag geen previewdata voor andere productslugs aanmaken, verwijderen of aanpassen en verwijdert ook geen reeds bestaande gegevens uit de standing acceptatiedatabase.
- Toon uitsluitend in de acceptatievariant direct onder de kop `Productoverzicht` en vóór metrics, producten en cyclusgegevens een compacte melding met de titel `Synthetische acceptatiedata`. De zichtbare en toegankelijke tekst bevat minimaal `1 actief`, `3 terminaal` en de scenariocategorieën `expliciet`, `afgeleid` en `onbekend`.
- Begrens de frontendwijziging tot deze statische, acceptatiegebonden melding. Bestaande sortering, classificatie, bewijsregels, storygroepering, beheerweergave, verversing en detailbediening blijven ongewijzigd.
- Gebruik uitsluitend bestaande tabellen en API-contracten. Voeg geen migratie, nieuw endpoint, contractveld, productieauthenticatie, telemetrie of Software Factory-integratieflow toe.
- Werk de factorydocumentatie bij met de acceptatie-activering, datasetscheiding, scenariodekking en veiligheidsgrenzen.

## Acceptance criteria

- Met de acceptatiemarkering actief worden de vier beschreven `product-factory`-fixturecycli exact eenmaal geladen; herhaald initialiseren verandert geen rij of waarde en maakt geen duplicaten.
- De actieve fixture blijft in de bestaande actieve kaartweergave staan en wordt niet als terminale bewijsregel weergegeven.
- De drie terminale fixtures worden door de bestaande presentatie zonder speciale fixtureclassificatie respectievelijk weergegeven met:
  - een expliciete beslisbron `Mens` en reden `Handmatig geannuleerd`;
  - `Evaluatie-agent (Afgeleid)`;
  - `Onbekend (Afgeleid)`.
- De afgeleide `ACCEPTED`-fixture toont in de bestaande bewijsregel exact twee gekoppelde opbrengsten. De onbekende terminale fixture toont exact nul gekoppelde opbrengsten.
- De twee synthetische leveringen zijn via exact productslug en cyclus-id uniek aan de bedoelde cyclus gekoppeld en verschijnen met hun vaste sleutel, titel, status en fase in de bestaande beheerweergave.
- De synthetische leveringen veroorzaken geen uitgaand Software Factory-verzoek, reconciliatie, workspacepublicatie, agentrun of andere externe levering.
- Een geautomatiseerde fixturetest vergelijkt alle opgeslagen fixturevelden met de vaste catalogus en bewijst dat identifiers, cyclussnummers, teksten, statussen, relaties en tijdstippen bij iedere laadpoging identiek blijven.
- De fixturevalidatie weigert parametrisch iedere wijziging naar een andere productslug, een onbekend veld of een niet-allowlisted waarde. Tests dekken daarnaast persoons- en contactgegevens, prompts, tokenachtige waarden, stacktraces, vrije gebruikersinvoer, productie-identifiers en echte `hkh-autopilot`-verwijzingen.
- Bij een botsing tussen een gereserveerde fixture-identifier en niet-identieke bestaande data faalt het laden concreet en transactioneel; bestaande gegevens worden niet aangepast en er blijft geen gedeeltelijke dataset achter.
- In de acceptatievariant staat `Synthetische acceptatiedata` zichtbaar en semantisch vóór de overige overzichtsinhoud. Dezelfde melding bevat `1 actief`, `3 terminaal`, `expliciet`, `afgeleid` en `onbekend`.
- Widget- en semanticstests bewijzen dat de melding begrijpelijk is zonder kleur, bij 320 CSS-pixels en bij 200% tekstvergroting, zonder overlap of horizontale pagina-scroll.
- Productie en PR-previews bevatten de nieuwe `product-factory`-fixtures en de melding niet. De bestaande PR-previewdata blijft wel beschikbaar onder haar huidige productslug.
- Regressietests bewijzen dat bestaande cycli en gegevens van andere producten niet worden verwijderd of gewijzigd en dat productieauthenticatie ongewijzigd blijft.
- Regressietests gebruiken de bestaande beslisbronclassificatie, bewijsweergave, koppellogica en beheerweergave rechtstreeks; er wordt geen fixturespecifieke alternatieve presentatielogica geïntroduceerd.
- API-responses en databaseschema blijven contractueel ongewijzigd.

## Aannames

- “Exact vier cyclusscenario’s” betreft de records binnen de gereserveerde synthetische dataset, niet het totale aantal historische of handmatig aangemaakte cycli dat in de standing acceptatieomgeving kan bestaan.
- De aantallen en categorieën in de melding beschrijven de vaste scenariodekking van de synthetische dataset en zijn geen dynamische totalen van alle geladen acceptatiegegevens.
- “Fictieve stories” betekent lokale synthetische kandidaten met bijbehorende leveringsrecords, omdat de bestaande bewijsregel uitsluitend gekoppelde Software Factory-leveringen als opbrengst telt.
- `REJECTED` met `criticVerdict` `ACCEPT` is het bestaande geldige guardrailpad en levert zonder expliciet beslisrecord conservatief `Onbekend (Afgeleid)` op.
- De actieve fixture wordt niet door agents uitgevoerd. Acceptatie blijft met autonome uitvoering en externe publicatie uitgeschakeld.
- Vaste tijden garanderen de onderlinge volgorde van de vier fixtures; later toegevoegde niet-fixturecycli mogen daarnaast volgens de bestaande sorteervolgorde verschijnen.

## Eindsamenvatting

Voor de PO:

- Er is een versieerbare acceptance-dataset gebouwd met vier vaste cycli: één actief en drie afgerond met expliciete, afgeleide en onbekende beslisbronnen. Twee vaste, voltooide voorbeeldleveringen zijn aan de geaccepteerde cyclus gekoppeld.
- Laden is herhaalbaar en transactioneel. Afwijkende gereserveerde gegevens veroorzaken een duidelijke fout zonder bestaande gegevens te overschrijven of gedeeltelijke data achter te laten.
- Validatie staat uitsluitend vooraf vastgelegde velden en waarden toe en weigert onder meer persoonsgegevens, prompts, tokens, stacktraces en productie-identifiers.
- Productie en PR-preview blijven gescheiden en ongewijzigd. De voorbeeldleveringen kunnen geen externe uitvoering, publicatie of synchronisatie starten.
- Alleen de acceptance-weergave toont direct onder `Productoverzicht` een toegankelijke melding met de vaste scenariodekking.
- Bestaande tabellen, API-afspraken, classificatie, bewijsregels en beheerweergave zijn hergebruikt; er zijn geen migraties, endpoints of fixturespecifieke alternatieve presentatieregels toegevoegd.
- Het volledige ontwikkelvangnet was groen: alle zes Maven-modules, statische frontendcontrole en 296 frontendtests. De onafhankelijke storytest omvatte daarnaast 22 gerichte backendtests, 99 gerichte frontendtests en een succesvolle PR-previewcontrole.
- Een positieve browsercontrole van de gedeployde acceptance-variant was binnen deze PR niet mogelijk; positie, semantiek en bruikbaarheid bij 320 pixels en 200% tekst zijn daarom met widgettests bewezen.
- De afzonderlijke documentatie-, merge- en deploystappen zijn nog niet uitgevoerd.

<!-- deploy-summary:start -->
De acceptatieomgeving krijgt vaste voorbeeldgegevens waarmee één actieve en drie afgeronde productcycli betrouwbaar kunnen worden bekeken. Een duidelijke melding maakt zichtbaar dat het om synthetische acceptatiegegevens gaat, terwijl productie en andere previews ongewijzigd blijven.
<!-- deploy-summary:end -->
