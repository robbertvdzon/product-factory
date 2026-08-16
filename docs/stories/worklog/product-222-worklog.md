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
- Migratie V25 voegt alleen de nullable, door een check-constraint gesloten
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
