# product-factory-36 - Toon voor ieder product een toestandsbewuste, compacte cyclusgeschiedenis

## Story

Toon voor ieder product een toestandsbewuste, compacte cyclusgeschiedenis

<!-- refined-by-factory -->

## Scope

- Gebruik voor alle producten één toestandsbewust presentatiemodel. De productslug bepaalt uitsluitend de productscope en identificatie, niet langer het componenttype, de veldvolgorde of de labels.
- Behandel `ACCEPTED`, `NO_CHANGE`, `NEEDS_REVISION`, `REJECTED` en `FAILED` als terminale statussen. Toon iedere terminale cyclus als een compacte, niet-uitklapbare bewijsregel met, in deze betekenisvolle leesvolgorde:
  1. `Datum`;
  2. `Cyclusuitkomst`;
  3. `Reden`;
  4. `Beslisbron`;
  5. `Gekoppelde opbrengst`;
  6. één alleen-lezen detailactie.
- Hergebruik voor datum, uitkomst, operationele reden, beslisbron en exacte leveringskoppeling de bestaande veilige formatterings-, classificatie- en groeperingsregels. Een ontbrekende of onbekende waarde wordt niet uit vrije tekst of waarschijnlijkheid geschat.
- Bepaal de beslisbron conservatief:
  - een herkende expliciete beslissing heeft voorrang;
  - een geldige handmatige annulering toont `Mens` en `Handmatig geannuleerd`;
  - zonder beslisrecord krijgt alleen een door de bestaande classifier bewezen combinatie van eindstatus, criticusverdict en waar toepasselijk foutaanwezigheid het achtervoegsel `(Afgeleid)`;
  - ontbrekende, onbekende, tegenstrijdige of niet aantoonbaar reconstrueerbare provenance toont uitsluitend `Onbekend`, zonder `(Afgeleid)`;
  - ruwe fouttekst wordt nooit als reden of beslisbron gerenderd.
- Bereken `Gekoppelde opbrengst` uitsluitend uit de reeds geladen, exact op productslug en cyclus-id gekoppelde Software Factory-leveringen. Toon bij laden `laden…`, bij bronfalen `niet beschikbaar` en alleen bij succesvol geladen gegevens een aantal.
- Behandel `QUEUED` en `RUNNING` als actieve cycli. Toon deze als niet-uitklapbare voortgangskaart met uitsluitend de veilige status, de beschikbare huidige stap en reeds beschikbare betrouwbare voortgang. Toon geen cyclusuitkomst, reden, beslissing, beslisbron, classificatiebadge, `Onbekend`, `(Afgeleid)`, opbrengsttoggle of terminale opbrengstvelden.
- Gebruik de reeds geladen `currentRole` als huidige stap wanneer die bruikbaar aanwezig is. Verzin geen stap of voortgang wanneer deze ontbreekt en haal daarvoor geen aanvullende stap- of detailgegevens op.
- Geef een actieve cyclus één neutraal gelabelde, alleen-lezen detailactie. De actie opent hetzelfde bestaande cyclusdetail, maar haar zichtbare en toegankelijke naam bevat geen uitkomst- of beslisterminologie.
- Een ontbrekende of onbekende status wordt niet als terminale uitkomst geïnterpreteerd. Toon daarvoor een veilige statuspresentatie zonder uitkomst, reden, beslisbron of afleidingsclaim.
- Behoud productscope, sortering, de bestaande 5/+10-lijstbeperking, automatische verversing, exacte recordkoppeling en focusherstel na het sluiten van het bestaande detail.
- Voeg geen endpoint, extra overzichtsrequest, contractveld, opslag, migratie, telemetrie, muterende actie of nieuwe frontenddependency toe.
- Werk de functionele en technische factorydocumentatie bij zodat de productslugvoorwaarde en de oude presentatie van onbekende afleiding niet langer als normatief gedrag beschreven staan.

## Acceptance criteria

- Componenttests met equivalente synthetische cycli voor `product-factory` en `hkh-autopilot` bewijzen dat alle vijf ondersteunde terminale statussen voor beide producten hetzelfde bewijsregelcomponent, dezelfde labels en dezelfde veldvolgorde gebruiken.
- Iedere terminale bewijsregel toont de vijf kernvelden afzonderlijk zichtbaar en in de Flutter-semantiek gelabeld, is niet uitklapbaar en bevat precies één detailactie en geen muterende bediening.
- De terminale detailactie opent precies de gekoppelde cyclus. Haar unieke toegankelijke naam bevat minimaal product, cyclusdatum en gebruikersgerichte uitkomst.
- Parametrische classificatietests dekken:
  - een bewezen agentuitkomst via een passend criticusverdict en eindstatus;
  - een geldig expliciet handmatig-annuleringsrecord;
  - een deterministisch reconstrueerbare historische beslisbron zonder record;
  - een ontbrekende, onbekende of tegenstrijdige provenance.
- Deze tests bewijzen respectievelijk behoud van de bestaande gebruikersgerichte bron, `Mens` met `Handmatig geannuleerd`, een bron met `(Afgeleid)`, en uitsluitend `Onbekend` zonder `(Afgeleid)`. Een fixture zonder bewezen afleidingsgrond mag nergens `(Afgeleid)` tonen.
- Tests voor ontbrekende, onbekende en kruis-cyclusbeslisrecords bewijzen dat geen expliciete of afgeleide bron van een andere cyclus wordt gebruikt.
- Tests voor geladen, ladende en mislukte leveringsbronnen bewijzen respectievelijk het exact gekoppelde aantal, `laden…` en `niet beschikbaar`; kandidaten en ambigue of kruisproductleveringen tellen niet mee.
- Fixtures met `QUEUED` en `RUNNING` voor beide producten renderen als voortgangskaart. Tekst- en semantiekasserties bewijzen dat alleen veilige status, beschikbare huidige stap, beschikbare voortgang en de neutrale detailactie voorkomen.
- De actieve fixtures bevatten bewust terminale velden en onbekende provenance in de testdata; toch ontbreken in de volledige kaart- en semantics-tree cyclusuitkomst, reden, beslissing, beslisbron, classificatiebadge, `Onbekend`, `Afgeleid` en ruwe fouttekst.
- Een actieve cyclus zonder `currentRole` blijft bruikbaar, toont geen verzonnen stap en veroorzaakt geen extra request. Een onbekende status claimt evenmin een terminale uitkomst of beslisbron.
- Widgettests op een brede viewport en op 320 logische pixels, tevens met 200% tekstvergroting, bewijzen voor beide producten dat kernvelden en detailactie zichtbaar en bereikbaar blijven zonder horizontale overflow, clipping of overlap.
- De zichtbare widgetvolgorde en Flutter-semantiekvolgorde zijn gelijk aan de betekenisvolle leesvolgorde. De geschiedenis vormt één benoemde semanticsgroep en iedere zichtbare cyclus vormt een afzonderlijke semanticscontainer.
- Focus- en toetsenbordtests bewijzen dat iedere detailactie met Tab bereikbaar en met Enter en Spatie activeerbaar is, een zichtbare focusrand heeft en na sluiten van het detail opnieuw focus ontvangt.
- Toegankelijkheidsverificatie gebruikt de bestaande Flutter-equivalenten: semantics-tree-inspectie, focus-, toetsenbord-, contrast- en overflowtests. Deze tests introduceren geen ernstige of kritieke axe-equivalente overtredingen.
- Privacytests gebruiken synthetische operationele metadata met herkenbare sentinelwaarden en bewijzen dat de compacte geschiedenis, detailactienamen, browser-URL en semantics-tree geen e-mailadressen, tokens, geheimen, ruwe prompts, ruwe foutmeldingen of foutlogs lekken.
- Een request-spy of gelijkwaardige componenttest bewijst dat renderen, productscope wisselen en openen/sluiten van de compacte presentatie geen nieuwe overzichtsrequests of muterende calls introduceren. Alleen het bestaande detailpad mag bij activering zijn bestaande leesverzoeken uitvoeren.
- Het volledige bestaande frontendvangnet blijft slagen.

## Aannames

- `QUEUED` en `RUNNING` zijn de enige ondersteunde actieve statussen; `ACCEPTED`, `NO_CHANGE`, `NEEDS_REVISION`, `REJECTED` en `FAILED` zijn de ondersteunde terminale statussen.
- De overzichtsrepresentatie bevat geen stapgeschiedenis of afzonderlijk veld voor “laatste voortgang”. `currentRole` is daarom het enige betrouwbare signaal voor de huidige stap; ontbrekende voortgang wordt weggelaten in plaats van geschat of via extra requests opgehaald.
- Een passend criticusverdict plus terminale status blijft de bestaande aantoonbare grond voor `Evaluatie-agent (Afgeleid)`. `FAILED` zonder verdict maar met aanwezige foutinformatie blijft de bestaande aantoonbare grond voor `Technische fout (Afgeleid)`; de foutinhoud zelf wordt niet getoond.
- `Onbekend` zonder `(Afgeleid)` betekent dat de bron niet betrouwbaar kon worden vastgesteld. Het is geen nieuwe beslisbroncategorie en maakt geen beslisrecord aan.
- De bestaande cyclusdetailweergave en haar gevestigde inhoud blijven buiten deze presentatiewijziging; de privacy-eisen gelden voor het overzicht, de detailactie en de metadata waarmee deze actie wordt geopend.
- Flutter biedt in de huidige projecttooling geen letterlijke HTML-`list`/`listitem`-rollen of axe-core-scan. De functioneel gelijkwaardige toetsing gebeurt met benoemde semanticsgroepen, afzonderlijke cycluscontainers, traversalvolgorde, focus en contrast.
- Er is geen backend-, database-, contract-, infrastructuur- of handmatige dataconversie nodig.

## Eindsamenvatting

De cyclusgeschiedenis is productonafhankelijk en toestandsbewust gemaakt. Afgeronde cycli tonen één compacte bewijsregel met datum, uitkomst, reden, beslisbron, gekoppelde opbrengst en één alleen-lezen detailactie. Actieve en onbekende cycli tonen uitsluitend veilige status- en voortgangsinformatie.

Belangrijkste keuzes:

- Alleen aantoonbare beslisbronnen krijgen “(Afgeleid)”; onzekere herkomst blijft “Onbekend”.
- Leveringen tellen alleen mee bij een exacte product- en cycluskoppeling.
- Detailacties zijn toetsenbordbedienbaar en krijgen na sluiten opnieuw focus.
- Bestaande productscope, sortering, lijstbeperking, automatische verversing en detailweergave zijn behouden.
- Bewust zijn geen backend-, opslag-, contract-, telemetrie- of dependencywijzigingen toegevoegd.

Testresultaat: de gerichte cyclusgeschiedenissuite is opnieuw groen met 52 tests. Het vastgelegde volledige vangnet was eveneens groen: 164 backendtests, frontend-analyse en 416 frontendtests. De dekking omvat beide producten, alle ondersteunde toestanden, beslisbronnen, leveringskoppeling, privacy, semantiek, toetsenbord/focus, contrast, smalle en brede schermen met 200% tekst en het uitblijven van extra overzichts- of wijzigingsverzoeken. Functionele en technische factorydocumentatie is bijgewerkt.

<!-- deploy-summary:start -->
Bij ieder product is nu direct te zien of een cyclus loopt of is afgerond en welke betrouwbare informatie daarbij hoort. De geschiedenis is compacter, werkt ook op kleine schermen en is beter bruikbaar met toetsenbord en schermlezer.
<!-- deploy-summary:end -->
