# product-222 - Worklog

Story-context bij eerste pickup:
Implementeer controleerbare handmatige cyclusstart

Bouw de database-, contract-, runtime-, dashboardbackend- en Flutter-wijzigingen inclusief
geautomatiseerde ontwikkeltests voor validatie, idempotentie, toegankelijkheid, productscope,
privacy en regressiebehoud; voer daarna de ingebouwde reviewstap uit.

Stappenplan:
[x]: read issue and target docs
[x]: implement database, contract and backend start guarantees
[x]: implement accessible Flutter start dialog and detail provenance
[x]: add and run focused automated tests
[x]: run the complete factory verification suite
[x]: review changes and update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- De bestaande startflow en productscope zijn eerst end-to-end geïnventariseerd om automatische
  starts, hervatten, annuleren en compacte cyclusregels buiten de wijziging te houden.
- Migratie V26 voegt alleen de nullable, door een check-constraint gesloten
  `manual_start_origin` toe. Historische rijen krijgen geen backfill; het gedeelde contract levert
  alleen `AUTONOMOUS_DEFAULT`, `OWNER_INPUT` of `null`.
- De runtime valideert de canonieke autonome opdracht exact en eigenaarinput na één trim op 1..300
  tekens. Een product-row-lock serialiseert gelijktijdige starts vóór de actieve-cycluscontrole,
  zodat één cyclus en één startpublicatie uit de bevestiging kunnen ontstaan. Automatische starts
  behouden hun eerdere focusroute en slaan geen handmatige provenance op.
- Dashboardproxy en Flutter-client sturen uitsluitend de effectieve opdracht en gekozen herkomst.
  De startknop opent voor de vastgelegde productslug een benoemde dialoog met autonome default,
  conditioneel één gelabeld veld, bevestigingssamenvatting, gesloten focuslus, Escape/focusretour,
  in-flight blokkering en een veilige live foutstatus die vrije invoer niet herhaalt.
- Het bestaande cyclusdetail toont opdracht plus Nederlands herkomstlabel alleen bij bekende
  opgeslagen handmatige provenance; historische details en compacte cyclusregels leiden niets af.
- Nieuwe migratie-, contract-, proxy-, validatie-, concurrency-, productscope-, privacy-,
  toetsenbord-, semantiek-, detail- en regressietests zijn toegevoegd. Eén bestaande dashboardtest
  is aangepast aan de nieuwe verplichte bevestigingsstap.
- Volledig vangnet groen: Maven `clean verify` met 174 tests en 0 failures/errors; `flutter analyze`
  met 0 issues; `flutter test` met 434 tests; Docker Engine-runner 3 tests; frontend-imagebuilds met
  veilige defaults en expliciete metadata allebei 19/19 stappen succesvol.

Review:
- Diff gecontroleerd op productscopelekken, vrije-tekstlogging, afgeleide historische provenance,
  verborgen input in requests, dubbele bevestiging, onbedoelde formatteringsruis, conflictmarkers
  en whitespacefouten. Geen open blocker of aanvullende wijziging gevonden.

Reviewer-run 2026-08-16:
- [bug] Client en server gebruiken niet exact dezelfde trimsemantiek. Dart `String.trim()` verwijdert
  onder meer U+0085 en U+FEFF, terwijl Kotlin/JVM `String.trim()` deze tekens niet als witruimte
  behandelt. Een directe `OWNER_INPUT`-request met alleen zo'n teken wordt daardoor door de server
  als een geldige opdracht opgeslagen, terwijl de client dezelfde invoer als leeg afwijst.
- [blocker] De geclaimde privacytest controleert alleen dat een door de widgetcallback opgegooide
  fouttekst niet in de Flutter-melding verschijnt. Er is geen geautomatiseerde controle dat vrije
  eigenaarinput ontbreekt uit backend-foutresponses, request-/foutlogs en telemetry, zoals het
  expliciete acceptatiecriterium vereist.
- [blocker] De concurrency-integratietest telt uitsluitend succesvolle calls en database-rijen. Zij
  observeert niet hoeveel `ShadowIterationStarted`-events zijn gepubliceerd en bewijst daardoor de
  expliciete maximaal-een-garantie voor startgebeurtenissen niet.
- Revisiongebonden factorybewijs gecontroleerd: geteste tree
  `cac505c0849ecd5a0f5929f7cc9ff2923c4ce44e` is gelijk aan `HEAD^{tree}`; Maven, Flutter analyze,
  Flutter tests en beide frontend-imagebuilds zijn groen. De Engine-runner-test is terecht door de
  pathselectie overgeslagen omdat geen runner/configbestand is gewijzigd.

Herstelplan na review:
[x]: align Unicode-trimsemantiek tussen Flutter en Kotlin en voeg grensgevallen toe
[x]: bewijs privacy voor backend-foutresponses, logging en telemetry
[x]: tel gepubliceerde ShadowIterationStarted-events in de concurrency-integratietest
[x]: draai gerichte tests en het volledige factory-vangnet
[x]: controleer de uiteindelijke diff en werk dit log bij

Herstel na review:
- Flutter en Kotlin gebruiken nu dezelfde expliciete trimset (Unicode White_Space plus U+FEFF),
  zodat onder meer U+0085 en U+FEFF aan beide kanten als randwitruimte gelden. Beide testsets
  bewijzen alle opgenomen grenscodepunten en één bewust niet-getrimd codepunt.
- De runtime-integratietest stuurt unieke vrije eigenaarinput via de echte HTTP-route en bewijst dat
  een afwijzing geen cyclus maakt en de invoer niet voorkomt in response/error, root request- en
  foutlogging of metadata van de actieve `http.server.requests`-telemetry.
- De gelijktijdigheidstest registreert Spring-applicatie-events en koppelt de enige overgebleven
  cyclusrij expliciet aan exact één `ShadowIterationStarted`-event.
- Gerichte backendtest: 6 tests, 0 failures/errors. Gerichte Fluttertest: 7 tests, alles groen.
- Volledig vangnet groen: Maven `clean verify` met 175 tests en 0 failures/errors; `flutter analyze`
  met 0 issues; `flutter test` met 435 tests; Docker Engine-runner 3 tests; frontend-imagebuilds met
  veilige defaults en expliciete metadata allebei 19/19 stappen succesvol.
- Einddiff gecontroleerd op conflictmarkers, whitespacefouten, onbedoelde lockfilewijzigingen,
  logging van vrije tekst en wijzigingen buiten de drie reviewbevindingen; geen open punt gevonden.

Vervolgreview 2026-08-16:
- Eerdere [bug] opgelost: Flutter en Kotlin gebruiken dezelfde expliciete trimset en dezelfde
  UTF-16-lengtesemantiek; de gerichte grensgevallen bewijzen onder meer U+0085 en U+FEFF en laten
  U+200B bewust ongemoeid.
- Eerdere privacy-[blocker] opgelost: de integratietest stuurt unieke vrije tekst via de echte
  HTTP-route en controleert foutresponse, foutmelding, root request-/foutlogging en actieve
  `http.server.requests`-telemetriemetadata; er ontstaat geen cyclus.
- Eerdere concurrency-[blocker] opgelost: de gelijktijdigheidstest koppelt de enige opgeslagen
  iteratie aan exact één opgenomen `ShadowIterationStarted`-event.
- Geen door de fixes geïntroduceerde regressie gevonden; `git diff --check` en conflictmarkercontrole
  zijn schoon. Gerichte verificatie: `ManualCycleStartIntegrationTest` 6/6 groen zonder skips en
  `manual_cycle_start_test.dart` 7/7 groen.
- Het nieuwste factorybewijs is geldig: tested tree
  `b40d2a66472d3b8941c46fa504346df780aee85a` is gelijk aan de developercommit-tree; Maven, Flutter
  analyze/tests en beide toepasselijke frontend-imagebuilds zijn groen. De Engine-runner-test is
  volgens de versioned pathselectie niet van toepassing op deze fix.

Herontwikkelrun na testafwijzing 2026-08-16:
- [x] controleer taakcontext, factorydocumentatie, branchstatus en eerdere reviewbevindingen
- [x] draai de storygerichte backend- en Flutter-tests opnieuw
- [x] draai het volledige agent-runnable factory-vangnet opnieuw
- [x] controleer de uiteindelijke worktree en leg de resultaten vast

Aanleiding:
- De storygerichte testfase was inhoudelijk groen, maar werd afgewezen doordat de previewruntime
  instabiel was. De eerder gereviewde implementatie staat ongewijzigd op de huidige branch; deze run
  herbevestigt daarom de ontwikkeltests en het volledige lokale vangnet na de laatste merge met main.

Resultaat herontwikkelrun:
- Storygerichte backendtests: 8 tests, 0 failures/errors/skips; storygerichte Fluttertests: 7 tests,
  alles groen.
- Volledig vangnet: Maven `clean verify` met 175 tests en 0 failures/errors/skips; `flutter analyze`
  met 0 issues; `flutter test` met 435 tests; Docker Engine-runner met 3 geslaagde tests; beide
  frontend-imagebuilds met 19/19 succesvolle stappen.
- `agent-image-build` is volgens de versioned verificatieconfiguratie niet agent-runnable en wordt
  door CI uitgevoerd. De story-implementatie en tests hoefden inhoudelijk niet aangepast te worden;
  alleen dit worklog is voor de nieuwe run aangevuld.

Herstelrun na toegankelijkheidsbevinding 2026-08-16:
- [x] lees taakcontext, factorydocumentatie, agent-tips en bestaande implementatie
- [x] voeg een expliciete toegankelijke naam aan de handmatige-startdialoog toe
- [x] voeg een gerichte semantiektest toe die de eerdere regressie afvangt
- [x] draai gerichte tests en het volledige agent-runnable factory-vangnet
- [x] controleer de uiteindelijke diff en leg de resultaten vast

Aanleiding:
- De previewtest bevestigde dat standaardkeuze, samenvatting, focuslus en focusherstel werken, maar
  het element met `role="alertdialog"` kreeg vanuit alleen de titelsemantiek geen programmatische
  naam. Deze run koppelt daarom de zichtbare titel expliciet als naam aan de dialoog zelf.

Resultaat toegankelijkheidsherstel:
- `ManualCycleStartDialog` geeft de `AlertDialog` nu expliciet het semantische label
  `Productcyclus starten`. De losse `namesRoute`-wrapper rond de zichtbare titel is verwijderd,
  zodat de dialoognaam niet dubbel als afzonderlijke routenaam wordt aangeboden.
- De bestaande toetsenbord-/semantiektest controleert nu ook exact de `semanticLabel`-waarde. Deze
  assertie faalde vóór de productiecodewijziging met `null` en is daarna samen met alle zeven
  storygerichte Fluttertests groen.
- Volledig vangnet groen: Maven `clean verify` met 177 tests en 0 failures/errors/skips;
  `flutter analyze` met 0 issues; `flutter test` met 436 tests; Docker Engine-runner met 3 tests;
  beide frontend-imagebuilds met 19/19 succesvolle stappen.
- `agent-image-build` blijft volgens `.factory/verification.yaml` niet agent-runnable en wordt door
  CI uitgevoerd. Eindcontrole op diff, whitespace, conflictmarkers en onverwachte bestanden is
  schoon; alleen productiecode, regressietest en dit worklog zijn gewijzigd.

Vervolgreview toegankelijkheidsherstel 2026-08-16:
- De volledige story-diff `main...HEAD` is opnieuw gecontroleerd. De drie eerdere reviewbevindingen
  blijven opgelost; conform de vervolgreviewregels zijn daarnaast de nieuwe dialoognaamfix en haar
  regressierisico beoordeeld.
- De testerbevinding is opgelost: `AlertDialog.semanticLabel` levert in Flutter een route-node met
  `scopesRoute` en `namesRoute`; de Flutter Web-engine zet de labelwaarde daardoor als `aria-label`
  op het element met `role="alertdialog"`. De zichtbare titel blijft ongewijzigd aanwezig.
- Het verwijderen van de losse `namesRoute`-wrapper om de titel introduceert geen regressie: de
  dialoog benoemt nu zichzelf en Flutter voorkomt daarmee juist een dubbele routeaankondiging.
- Gerichte verificatie: `flutter test test/manual_cycle_start_test.dart --reporter expanded` is
  groen met 8/8 tests. `git diff --check main...HEAD`, conflictmarkercontrole en worktreestatus waren
  vóór deze toegestane worklognotitie schoon.
- Het nieuwste factorybewijs is geldig: tested tree
  `153589e03c45a6aad2b6639feea95cfb7ba98f9b` is exact gelijk aan de developercommit-tree. Maven,
  Flutter analyze/tests en beide toepasselijke frontend-imagebuilds zijn groen; de Engine-runner-test
  is volgens de versievaste pathselectie niet van toepassing op deze frontendfix.

Herontwikkelrun na merge met main 2026-08-16:
- [x] lees taakcontext, factorydocumentatie, agent-tips en bestaand worklog
- [x] controleer de gemergde story-implementatie, tests en verificatieconfiguratie
- [x] draai storygerichte tests
- [x] draai het volledige agent-runnable factory-vangnet
- [x] controleer de uiteindelijke worktree en leg de resultaten vast

Aanleiding:
- De actuele taakcontext bevat de eerdere toegankelijkheidsafwijzing én het latere leidende
  PO-/reviewcommentaar dat het herstel is goedgekeurd. Na de meest recente merge met `main`
  verifieert deze ontwikkelrun daarom dat de volledige implementatie en het herstel intact en groen
  zijn, zonder de gereviewde productcode onnodig opnieuw te wijzigen.

Resultaat herontwikkelrun na merge:
- De story-diff bevat nog steeds de nullable migratie zonder backfill, gesloten contractwaarden,
  servervalidatie en serialisatie van gelijktijdige starts, productscope, veilige proxyroute,
  toegankelijke Flutterdialoog en detailprovenance. De herstelde `AlertDialog.semanticLabel`
  benoemt de dialoog zelf expliciet als `Productcyclus starten`; de regressietest controleert dit.
- Storygerichte backendtests: 8 tests, 0 failures/errors/skips. Storygerichte Fluttertests: 7 tests,
  alles groen.
- Volledig vangnet: Maven `clean verify` met 178 tests en 0 failures/errors/skips; `flutter analyze`
  met 0 issues; `flutter test` met 436 tests; Docker Engine-runner met 3 geslaagde tests; beide
  frontend-imagebuilds met 19/19 succesvolle stappen.
- `agent-image-build` is volgens `.factory/verification.yaml` niet agent-runnable en wordt door CI
  uitgevoerd. Er waren geen inhoudelijke codewijzigingen nodig na de merge; alleen dit worklog is
  voor de actuele ontwikkelrun aangevuld.

Vervolgreview na merge met main 2026-08-16:
- De volledige story-diff `main...HEAD` is beoordeeld. Sinds de vorige reviewgoedkeuring veranderde
  de story-implementatie niet; de merge bracht alleen de afzonderlijke productcatalogusfix en
  deploymentpin van `main` binnen en introduceert geen regressie in de handmatige startflow.
- De drie oorspronkelijke bevindingen blijven opgelost: client en server delen dezelfde expliciete
  Unicode-trimset, de backendprivacytest controleert response/logging/telemetrie, en de
  concurrencytest bewijst exact één cyclusrij en één `ShadowIterationStarted`-event.
- De latere testerbevinding blijft opgelost: `AlertDialog.semanticLabel` benoemt de dialoog als
  `Productcyclus starten`; de zichtbare titel, gesloten focuslus, Escape en focusretour blijven
  intact en worden gericht getest.
- Gerichte verificatie is groen: backend 8/8 zonder failures, errors of skips en Flutter 7/7.
  `git diff --check main...HEAD` en de conflictmarkercontrole zijn schoon.
- Het nieuwste factorybewijs is volledig groen voor alle toepasselijke commando's en geldig voor de
  developer-tree: `59ce6c49c912fc35a669d17105345211933754bd` kwam vóór deze toegestane
  worklognotitie exact overeen met `HEAD^{tree}`. De Engine-runner-test is terecht niet geselecteerd
  omdat de bijbehorende runner- en configuratiepaden niet veranderden.

Ontwikkelrun na nieuwe main-integratie 2026-08-16:
- [x] lees taakcontext, factorydocumentatie, agent-tips en bestaand worklog
- [x] los de mergeconflictstatus op met behoud van story- en main-gedrag
- [x] draai storygerichte tests
- [x] draai het volledige agent-runnable factory-vangnet
- [x] controleer de uiteindelijke worktree en leg de resultaten vast

Aanleiding:
- De factory-checkout bevatte na integratie van de nieuwste `main` één conflict in
  `ShadowIterationApi.kt`: de story voegde handmatige-startherkomst toe en `main` voegde
  bugprioriteitsblokkering toe. Beide gedragingen zijn vereist en worden gecombineerd en opnieuw
  volledig geverifieerd.

Resultaat nieuwe main-integratie:
- Het serviceconflict is opgelost met behoud van zowel handmatige startvalidatie, herkomst en
  maximaal-één-garantie als de nieuwe blokkering voor actief P0/P1-bugwerk. De expliciete
  `AlertDialog.semanticLabel` en bijbehorende storytest voor `Productcyclus starten` bleven intact.
- De twee branches gebruikten beide Flyway-versie 25. De ongewijzigde nullable
  handmatige-herkomstschemawijziging staat daarom nu als eerstvolgende vrije migratie V26, na de
  nieuwe V25 voor bugs en testsessies; de migratietest bewijst nog steeds geen backfill en de
  gesloten waardenconstraint.
- De nieuwe bugblokkeringstest gebruikt nu een contractgeldige autonome handmatige startrequest,
  zodat hij het bedoelde 409-conflict bereikt. Een bestaande cyclusdetailwidgettest opent nu eerst
  de door `main` geïntroduceerde sectie `Productsessies` voordat zij de detailregels controleert.
- Gerichte storytests: backend 8/8 en Flutter 7/7 groen; gecombineerde backendregressietests 9/9
  en de herstelde cyclusdetailtests 3/3 groen.
- Volledig vangnet groen: Maven `clean verify` met 190 tests en 0 failures/errors/skips; `flutter
  analyze` met 0 issues; `flutter test` met 440 tests; Docker Engine-runner met 3 tests; beide
  frontend-imagebuilds met 19/19 succesvolle stappen.
- `agent-image-build` blijft volgens `.factory/verification.yaml` niet agent-runnable en wordt door
  CI uitgevoerd. Conflictmarker- en whitespacecontroles zijn schoon; `pubspec.lock` veranderde niet.
