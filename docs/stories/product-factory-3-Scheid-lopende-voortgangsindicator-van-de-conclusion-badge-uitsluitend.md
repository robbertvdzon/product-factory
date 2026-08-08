# product-factory-3 - Scheid lopende-voortgangsindicator van de conclusion-badge, uitsluitend indien een bestaand data-signaal dit al ondersteunt

## Story

Scheid lopende-voortgangsindicator van de conclusion-badge, uitsluitend indien een bestaand data-signaal dit al ondersteunt

<!-- refined-by-factory -->

## Samenvatting
De uitkomstbadge (zoals 'guardrail-conflict' of 'richting-gekozen') hoort alleen bij afgeronde productcycli. Nu kan die badge ook per ongeluk verschijnen bij een cyclus die nog bezig is. Deze story zorgt dat een lopende cyclus voortaan een neutrale voortgangsindicator toont in plaats van een (mogelijk misleidende) uitkomstbadge, en dat een cyclus die halverwege is afgebroken altijd nette valt onder 'niet-classificeerbaar'.

## Scope
- Broncode-inspectie is al uitgevoerd: `ShadowIterationView.status` (QUEUED/RUNNING/ACCEPTED/NEEDS_REVISION/REJECTED/FAILED, `productfactory-contracts/.../Contracts.kt`) is het bestaande veld dat lopend van afgerond onderscheidt. QUEUED/RUNNING = lopend; ACCEPTED/NEEDS_REVISION/REJECTED/FAILED = afgerond. Dit patroon (`status == 'QUEUED' || status == 'RUNNING'`) wordt al elders gebruikt in `dashboard-frontend/lib/main.dart` (regel ~767, detaildialoog).
- Er bestaat geen apart 'geannuleerd'-statusveld of -waarde in het datamodel. Een tijdens uitvoering afgebroken iteratie is dus geen apart geval om te bouwen; deze valt (net als elke onbekende status) al via de bestaande fallback-logica in `classifyIterationOutcome` (`dashboard-frontend/lib/classification.dart`) op `kNietClassificeerbaar`, zodra zo'n iteratie niet als lopend (QUEUED/RUNNING) wordt herkend.
- Wijzig de iteratierij in `dashboard-frontend/lib/main.dart` (rond regel 558-603, `_limitedSection('iterations', ...)`): voor een iteratie met `status` QUEUED of RUNNING wordt geen `ClassificationBadge` getoond maar een neutrale voortgangsindicator; voor elke andere status blijft exact één `ClassificationBadge` staan (ongewijzigd gedrag).
- Geen nieuw databaseveld, geen migratie, geen wijziging aan `ShadowIterationView`/`Contracts.kt` of het backend-datamodel.

## Acceptance criteria
- Voor een iteratie met `status` QUEUED of RUNNING toont de overzichtsrij uitsluitend een neutrale voortgangsindicator en geen van de vijf conclusion-badges.
- Voor een iteratie met elke andere `status`-waarde (ACCEPTED, NEEDS_REVISION, REJECTED, FAILED, of onbekend/anders) toont de overzichtsrij exact één conclusion-badge en geen voortgangsindicator, ongewijzigd t.o.v. het huidige gedrag.
- Een geautomatiseerde unit test dekt het geval 'onbekende/niet-QUEUED/RUNNING statuswaarde die niet in `kBekendeStatuswaardenPerCategorie` voorkomt' (representatief voor een tijdens uitvoering afgebroken iteratie) en assert dat dit deterministisch naar `kNietClassificeerbaar` mapt.
- Een geautomatiseerde widget-/snapshot-test doorloopt rijen gegroepeerd op `status` (QUEUED/RUNNING vs. de vier afgeronde statuswaarden) uit een testdataset en assert wederzijdse exclusiviteit tussen voortgangsindicator en conclusion-badge.
- De voortgangsindicator-widget/-cel gebruikt `aria-live="polite"`-equivalent gedrag (in Flutter-web: het corresponderende semantics-/live-region-attribuut), geverifieerd via een geautomatiseerde test.
- De diff bevat geen migratie en geen wijziging aan `Contracts.kt`/databaseschema; de implementatie leest uitsluitend het bestaande `status`-veld.
- De PR-/commitbeschrijving documenteert expliciet de bevinding dat `status` (QUEUED/RUNNING vs. overig) het gebruikte bestaande onderscheidende signaal is, en dat er geen apart CANCELLED-statuswaarde bestaat.

## Aannames
- 'Lopend' wordt gedefinieerd als `status` gelijk aan QUEUED of RUNNING; alle overige waarden (inclusief nog onbekende toekomstige statuscodes) gelden als 'afgerond' voor deze scheiding, consistent met het bestaande gebruik in `main.dart` regel ~767.
- Omdat er geen CANCELLED-status bestaat, wordt de acceptatie-eis "geannuleerd tijdens uitvoering → niet-classificeerbaar" ingevuld via het bestaande fallback-pad van `classifyIterationOutcome` voor elke niet-QUEUED/RUNNING status die niet in `kBekendeStatuswaardenPerCategorie` voorkomt; er wordt geen nieuwe statuswaarde of schemaveld geïntroduceerd om 'geannuleerd' expliciet te modelleren.
- De voortgangsindicator is puur visueel/tekstueel (bijv. een spinner of neutraal label), zonder eigen classificatiewaarde, en vervangt alleen de `ClassificationBadge` in de rij-weergave voor lopende iteraties.

## Eindsamenvatting

Ik heb alle context (worklog, review- en testresultaten, en de diffs). Dit is voldoende om de eindsamenvatting te schrijven.

## Eindsamenvatting — product-factory-3

**Wat is gebouwd**
De iteratierij in het dashboard toont niet langer per ongeluk een uitkomstbadge (bijv. 'guardrail-conflict', 'richting-gekozen') bij een cyclus die nog loopt. Op basis van het bestaande `status`-veld (`ShadowIterationView.status`, al aanwezig in `Contracts.kt`) is de weergave gesplitst:
- Status QUEUED of RUNNING → nieuwe, neutrale `IterationProgressIndicator`-widget (in plaats van de badge).
- Elke andere status (ACCEPTED, NEEDS_REVISION, REJECTED, FAILED, of onbekend) → ongewijzigd exact één `ClassificationBadge`.

**Belangrijke keuze/bevinding**
Er bestaat geen apart 'geannuleerd'-statusveld in het datamodel. Een tijdens uitvoering afgebroken iteratie hoeft daarom niet apart gebouwd te worden: die valt automatisch via de bestaande fallback-logica in `classifyIterationOutcome` op 'niet-classificeerbaar', zodra de status niet QUEUED/RUNNING is. Deze bevinding is expliciet vastgelegd in commit/worklog, zoals de story vereiste. Er is geen wijziging aan `Contracts.kt`, het databaseschema of migraties — alleen het bestaande `status`-veld wordt gelezen.

**Techniek**
De nieuwe indicator gebruikt `Semantics(liveRegion: true)` als Flutter-web-equivalent van `aria-live="polite"`, zodat lopende voortgang ook voor schermlezers netjes wordt aangekondigd.

**Getest**
- Unit test in `classification_test.dart`: een onbekende status (`CANCELLED`, als representant van 'afgebroken tijdens uitvoering') mapt deterministisch naar 'niet-classificeerbaar'.
- Nieuwe widget-/snapshot-test `iteration_progress_indicator_test.dart`: doorloopt rijen per statusgroep (QUEUED/RUNNING vs. de vier afgeronde statussen) en toetst wederzijdse exclusiviteit tussen indicator en badge, plus de live-region-eigenschap.
- Volledig vangnet groen: `mvn clean verify` (backend, 7 tests), `flutter analyze` (geen issues), `flutter test` (44 tests groen), plus een rooktest `flutter build web`.
- Review bevestigde dat de diff strikt beperkt bleef tot het beoogde blok in `main.dart` (regels 557-603, 1501-1548) en dat pre-existing formatting-afwijkingen elders niet zijn aangeraakt.

**Bewust niet gedaan**
- Geen aanpassing aan de detaildialoog — die had al eigen, aparte progress-UI en gebruikte sowieso geen `ClassificationBadge`, dus terecht buiten scope.
- Geen browsergebaseerde/preview-verificatie: er was geen preview-omgeving beschikbaar in de agentcontainer; dit is opgevangen met codeverificatie en het volledige geautomatiseerde testvangnet.

<!-- deploy-summary:start -->
In het dashboard zie je nu bij een productcyclus die nog bezig is een duidelijke 'bezig'-indicator in plaats van een eindoordeel-label. Zodra de cyclus is afgerond (of onderbroken raakt), verschijnt gewoon het eindoordeel zoals voorheen. Zo kun je in één oogopslag zien of iets nog loopt of al klaar is, zonder verwarrende tussentijdse labels.
<!-- deploy-summary:end -->
