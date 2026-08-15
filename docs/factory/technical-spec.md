# Technical Spec

## Stack

| Onderdeel | Technologie |
| --- | --- |
| Backend-modules | Kotlin 2.1.21 op Java 21, Spring Boot 3.5.14, Spring Modulith 1.4.11 |
| Build backend | Maven (multi-module, root `pom.xml`) |
| Database | PostgreSQL via Spring JDBC (`JdbcTemplate`); tijdstempels als `Instant` (UTC) |
| Frontend | Flutter/Dart (`environment: sdk: ^3.9.0`), web-target, Material 3 |
| Frontend-dependencies | `http`, `google_sign_in(_web)`, `shared_preferences`, lints via `flutter_lints` |
| Statische analyse | `flutter analyze` (frontend), detekt via Maven-profiel `quality` (backend) |

## Module-indeling

- `productfactory-contracts` — gedeelde datacontracten (`Contracts.kt`) tussen runtime, agentworker en
  dashboard. De view-types die het dashboard toont (`ShadowIterationView`, de daarin optionele
  `ShadowIterationDecisionView`, `StoryCandidateView`, `WorkspacePublicationView`) staan hier;
  `StoryDeliveryView`/`HumanActionView` staan in
  `productfactory/.../autonomy/AutonomousDelivery.kt`.
- `productfactory-common` — gedeelde infrastructuurcode.
- `productfactory` — de runtime: productcycli (shadow iterations), storykandidaten, autonome levering.
- `agentworker` — draait agents in containers (zie `Dockerfile.agent`).
- `dashboard-backend` — Spring Boot API + Google-authenticatie; ontsluit de runtime voor het dashboard.
- `dashboard-frontend` — de Flutter-webapp met het productoverzicht.

## Synthetische omgevingsdatasets

- `PreviewRuntimeConfig` selecteert fail-closed exact één `SyntheticDataset`: `PR_PREVIEW` bij
  marker `product-factory-pr-preview` plus een positief PR-nummer, `ACCEPTANCE` bij marker
  `product-factory-acceptance` zonder PR-nummer, en anders `NONE`. Een ingeschakelde synthetische
  modus accepteert uitsluitend de in-namespace PostgreSQL-URL. `PreviewDataStartup` routeert op
  deze selectie; daardoor kan een acceptance-start nooit de PR-previewseed laden en omgekeerd.
- De bestaande PR-previewcatalogus voor `hkh-autopilot` blijft ongewijzigd. Alleen `ACCEPTANCE`
  laadt `AcceptanceFixtureCatalog` versie `acceptance-product-factory-cycles-v1` voor exact slug
  `product-factory`: vier cycli met vaste ids, nummers 9201–9204 en UTC-tijden, één expliciet
  beslisrecord, twee kandidaten en twee voltooide leveringen. De scenario's zijn één `RUNNING`,
  één handmatig geannuleerde `FAILED`, één `ACCEPTED` met `criticVerdict=ACCEPT` en twee gekoppelde
  opbrengsten, en één `REJECTED` met `criticVerdict=ACCEPT` zonder gekoppelde opbrengst.
- `AcceptanceFixtureValidator` vergelijkt vóór opslag recursief de gesloten veldenset, recordaantallen
  en iedere exacte waarde met de versieerbare catalogus; er is geen invoerpad voor vrije
  fixturewaarden. Daardoor worden een andere productslug, extra velden en gewijzigde waarden met
  persoonsgegevens, contactgegevens, prompts, tokens, stacktraces, productie-identiteiten of echte
  `hkh-autopilot`-verwijzingen geweigerd. Na een insert wordt dezelfde vergelijking op de opgeslagen
  data uitgevoerd. Een exact reeds aanwezige catalogus is een no-op. Iedere botsing op een
  gereserveerde id, cyclussleutel, fingerprint, idempotency key of seedversie faalt concreet binnen
  dezelfde transactie; bestaande data wordt niet overschreven en gedeeltelijke fixturedata wordt
  teruggedraaid.
- Op een lege acceptatiedatabase maakt de seeder alleen de noodzakelijke, gepauzeerde
  `product-factory`-productcontext aan. Een al bestaand product en alle niet-gereserveerde cycli of
  records blijven ongemoeid. De kandidaten staan al op `PUBLISHED`; de leveringen op `DONE`, fase
  `developed`, bevestigd deployed en geëvalueerd. Daardoor starten ze geen agentrun, reconciliatie,
  workspacepublicatie of Software Factory-levering. De implementatie gebruikt uitsluitend de
  bestaande tabellen en API-contracten: er is geen migratie, endpoint of contractveld toegevoegd.
- De frontend krijgt de omgevingsmarkering uitsluitend compile-time via Dart-define
  `ACCEPTANCE_DATASET`. De Dockerfile-default is `false`; alleen de apart getagde acceptance-image
  zet deze op `true`. `OverviewPage` voegt dan de statische `AcceptanceDatasetNotice` direct onder
  `Productoverzicht` toe. De bestaande sortering, classificatie, koppeling, beheerweergave,
  verversing en detailbediening blijven hetzelfde.

## Beslisprovenance, opslag en API-contract

- Flyway-migratie `V20__shadow_iteration_decision.sql` voegt de tabel
  `shadow_iteration_decision` toe. `iteration_id` is zowel primary key als foreign key naar
  `shadow_iteration(id)`, waardoor per cyclus maximaal één record bestaat. De overige verplichte
  kolommen zijn `actor_type`, `mechanism`, `reason_code` en `decided_at`; er zijn bewust geen
  actoridentiteit, aangeleverde reden of andere vrije-tekstkolommen.
- `ShadowIterationView.decision` is nullable en bevat, wanneer aanwezig, exact
  `iterationId`, `actorType`, `mechanism`, `reasonCode` en `decidedAt`. De bestaande lijst- en
  detailroutes (`GET /api/shadow-iterations?productSlug=...` en
  `GET /api/shadow-iterations/{id}?productSlug=...`) bouwen hun view via dezelfde left join en
  leveren dus hetzelfde optionele record. De dashboard-backend geeft dit contract via zijn
  bestaande proxy-routes ongewijzigd door. Cycli zonder record blijven geldig; de migratie voert
  geen backfill uit.
- `POST /api/shadow-iterations/{id}/cancel?productSlug=...` kan alleen QUEUED of RUNNING
  beëindigen. `ShadowIterationService.cancel` is transactioneel: `markManuallyCancelled` zet de
  status op FAILED en schrijft daarna het record met `HUMAN`, `MANUAL_CANCELLATION` en
  `MANUALLY_CANCELLED`. Eén `Instant` wordt voor zowel `completed_at` als `decided_at` gebruikt.
  Raakt de conditionele statusupdate geen rij of faalt de insert, dan resulteert dit in een conflict
  respectievelijk rollback en blijft geen halve overgang achter. De optionele request-`reason`
  blijft uitsluitend in het bestaande `error_message` en wordt niet naar provenance gekopieerd.

## Epic-roadmap, ranking en dependencies

- Flyway-migratie `V21__roadmap_epic_ranking.sql` voegt `customer_rank` en `process_rank` toe aan
  de bestaande `roadmap_theme`-tabel. De tabelnaam en bestaande epic-ID's blijven bewust behouden
  zodat historische storykoppelingen geldig blijven; in contracten, API en UI heet het object een
  epic. De migratie initialiseert beide ranglijsten deterministisch vanuit de oude
  HIGH/MEDIUM/LOW-prioriteit en het volgnummer.
- Beide ranks vormen per product een unieke, aaneengesloten volgorde. Verplaatsen gebeurt binnen
  een transactie na een row lock op `product_definition`. Klant-API's kunnen alleen
  `customerRank` wijzigen; de roadmap-session-agent kan alleen `processRank` wijzigen.
- De score is een integer van 0 tot 100. Elke rank wordt lineair genormaliseerd binnen het actuele
  aantal epics (rank 1 = 100; laatste rank = 0; één epic = 100), waarna
  `round(0,75 × klantpunten + 0,25 × procespunten)` wordt toegepast.
- `roadmap_epic_dependency` legt gerichte afhankelijkheden vast. De roadmapvolgorde is een
  topologische sortering: alleen epics waarvan alle predecessors geplaatst zijn komen in
  aanmerking; tussen die epics wint de hoogste score. Onbekende, self- en circulaire dependencies
  worden geweigerd en de hele mutatie wordt teruggedraaid.
- De primaire routes zijn `/api/products/{slug}/roadmap/epics`; de oude `/themes`-routes blijven
  tijdelijk als rolloutcompatibiliteit bestaan. `RoadmapEpicView` levert onder andere
  `customerRank`, `processRank`, `priorityScore`, `roadmapRank`, `dependencyIds`, `blockedByIds`
  en `blocksIds`.

## Frontend-conventies (`dashboard-frontend/lib`)

- `main.dart` — widgets en pagina's; `api.dart` — HTTP-client; `config.dart` — build-time config;
  `session.dart` — Google-login; `formatting.dart` — datum/tijd- en duurformattering;
  `limited_list.dart` — de 5/+10-lijstbeperking; `iteration_results.dart` — pure, verliesvrije
  client-side koppeling van geladen kandidaten en leveringen aan cycli; `iteration_evidence.dart` —
  de pure selector en veilige presentatie-opbouw voor terminale `product-factory`-cycli;
  `product_scope.dart` — canonieke productselectie, scopefilters en browservoorkeur;
  `start_availability.dart` — het pure, gedeelde presentatiemodel voor de handmatige
  startbeschikbaarheid;
  `classification.dart` — de bestaande pure
  mappings op `status`/`criticVerdict`/`errorMessage` plus
  `iterationDecisionPresentation`, dat eerst een aan het iteratie-id gekoppeld `decision`-record
  selecteert en alleen bij ontbreken daarvan terugvalt op de afleiding. De vaste
  iteratie-uitkomstclassificatie naar één van vijf badge-waarden (inclusief de fallbackwaarde
  `niet-classificeerbaar` voor onbekende statuswaarden) en `classifyDecisionSource` naar
  `Evaluatie-agent`, `Technische fout` of `Onbekend`. De beslisbronmapping trimt invoer, vergelijkt
  bekende waarden hoofdlettergevoelig, accepteert alleen de bewezen paren `ACCEPT`/`ACCEPTED`,
  `REVISE`/`NEEDS_REVISION` en `REJECT`/`REJECTED` als `Evaluatie-agent`, en alleen `FAILED` zonder
  verdict maar met foutmelding als `Technische fout`; alle overige combinaties zijn `Onbekend`.
  Alleen de twee bewezen bronnen krijgen in `iterationDecisionPresentation` de vlag `(Afgeleid)`;
  `Onbekend` krijgt die bewijsclaim nooit. Het bestand bevat daarnaast de AA-contrastkleuren en de
  `ClassificationBadge`-widget voor terminale detailpresentatie. `IterationSessionDialog` toont bij
  expliciete handmatige annulering daarnaast het
  mechanisme en `decidedAt` via de bestaande lokale datum-/tijdformatter. Zonder expliciet record
  toont het detail een bewezen conservatief afgeleide bron zichtbaar en toegankelijk met
  `(Afgeleid)`, of anders uitsluitend `Onbekend`.
  De dialoogtitel gebruikt het user-facing `sequenceNumber`, met het iteratie-id als fallback als
  dat nummer ontbreekt. Openen en sluiten gebruikt uitsluitend de bestaande GET-calls.
  `roadmap.dart` bevat het epic-contract voor de UI, de horizontale dependencygrafiek, kaartjes en
  de maak-/detaildialogen. De process-rank en score zijn daar alleen-lezen; klant-rank,
  dependencies, titel, beschrijving en status worden via de epic-routes opgeslagen.
- `StartAvailability.fromProduct` leest uitsluitend de sleutels `status` en `workspaceOwnership`.
  Het model vergelijkt bekende waarden exact en hoofdlettergevoelig, zonder trimmen of normaliseren,
  en levert uit één instantie `canStart`, de geprioriteerde primaire reden, de aanvullende telling,
  veilige labels en de lijst met onvervulde voorwaarden. Ontbrekende sleutels, `null`, lege tekst,
  andere typen en onbekende teksten vallen fail-closed in de categorie `unknown`; ruwe invoer wordt
  niet bewaard of gerenderd. Andere productvelden en alle cyclusdata worden niet geconsumeerd.
- `_OverviewPageState._startCycleSection` bouwt het model eenmaal voor het geselecteerde product.
  `StartAvailabilityPanel` gebruikt dezelfde instantie voor de bestaande `StartCycleButton`, de
  blokkademelding en `StartAvailabilityDetailsDialog`. Bij blokkade groepeert één expliciete
  `Semantics`-container de uitgeschakelde button met primaire en eventuele aanvullende reden.
  `StartAvailabilityDetailsButton` opent met een native `TextButton` de lokale `AlertDialog`, zonder
  API-client, productrecord of muterende callback. De dialoog krijgt alleen het veilige model, heeft
  een gesloten focuslus en uitsluitend de actie `Sluiten`; na sluiten via die actie of Escape vraagt
  de openerfocusnode opnieuw focus. Het beschikbare pad roept ongewijzigd `_startCycle` aan en toont
  geen blokkademelding of detailactie. Er zijn geen nieuwe routes, requests, contractvelden,
  dependencies, opslag of telemetrie toegevoegd.
- `DashboardSource<T>` en `_OverviewResultsBuilder` in `main.dart` volgen de drie bestaande
  leesverzoeken voor cycli, kandidaten en leveringen onafhankelijk als loading, loaded of failure.
  De bijbehorende metrics en secties renderen daarom geen nul of compleet resultaat voor een bron
  die nog laadt of is mislukt. `_OverviewPageState.managementView` wisselt binnen dezelfde
  `OverviewPage` tussen productoverzicht en Beheer. Beide weergaven hergebruiken dezelfde futures,
  vijfsecondenrefresh en zichtbaarheidstellers; er zijn geen nieuwe routes, browser-URL's, requests of
  contractvelden. In een afzonderlijke Beheer-scope wordt de leveringslijst pas afgeleid als zowel
  kandidaten als leveringen geladen zijn; `Alle producten` rendert de globale leveringsbron
  zelfstandig. De storywachtrij verschijnt pas compleet als kandidaat- én leveringsbron geladen
  zijn. Zijn alleen kandidaten geladen, dan toont zij het kandidaataantal met een expliciete
  onvolledigheidsmelding.
- `product_scope.dart` accepteert alleen productrecords met een niet-lege `String slug` en vergelijkt
  canonieke slugs exact en hoofdlettergevoelig, zonder trimmen of fallbackvelden. `selectProductScope`
  herstelt een voorkeur alleen bij exact één match en valt anders terug op het eerste geldige product
  in de ontvangen API-volgorde. `ProductScopePreferences` leest, schrijft en verwijdert uitsluitend
  de slug onder `product-factory.dashboard.active-product-slug` via `shared_preferences`; een
  opslagfout blokkeert de lokale presentatiewissel niet.
- `iterationsInProductScope` filtert cycli rechtstreeks via `Iteration.productSlug`.
  `linkedStoriesInProductScope` vereist daarnaast een integer `iterationSequenceNumber` dat exact
  één integer `sequenceNumber` binnen de productcycli aanwijst. `candidatesInManagementScope`
  filtert rechtstreeks via `StoryCandidate.productSlug`; `deliveriesInManagementScope` vereist
  exact één kandidaat met hetzelfde integer `candidateId` en bepaalt de scope uitsluitend via de
  slug van die kandidaat. Ontbrekende, anders getypeerde, lege, kruisproduct- en ambigue relaties
  vallen buiten een afzonderlijke scope en blijven alleen in de globale Beheer-keuze zichtbaar.
- `ProductScopePicker` in `main.dart` gebruikt één `DropdownButtonFormField` met expliciete
  Semantics-naam en actuele waarde, een focusrand van drie pixels en een eigen `FocusNode` dat na
  wisselen focus herstelt. Alleen de Beheer-variant voegt `Alle producten` toe.
  `ProductScopeStatus` is een zichtbare `Semantics(liveRegion: true)`-melding; hoofdscherm en Beheer
  bewaren afzonderlijke meldingstoestand zodat de tijdelijke Beheer-scope niet naar het overzicht
  lekt. Een scopewissel gebruikt de reeds geladen objecten en start zelf geen HTTP-request.
- `DashboardNavigationLink` in `main.dart` verzorgt de interne links `Beheer` en `Terug naar
  overzicht`. Eén expliciete `Semantics`-node levert link-, focus- en tapsemantiek; de onderliggende
  `TextButton` levert pointer- en toetsenbordactivatie en een focusrand van drie pixels. Een eigen
  `FocusNode` houdt Flutter-focus en webfocus gekoppeld, ook tijdens de automatische refresh.
- `SoftwareFactoryDeliveryTile` laat leveringsrecords bij smalle schermen en tekstvergroting verticaal
  meegroeien. Daardoor kunnen lange sleutel-, titel-, product-, status- en faseteksten teruglopen zonder
  horizontale pagina-scroll.
- `groupIterationResults` in `iteration_results.dart` indexeert alle geladen cycli binnen de actieve
  productscope vóór `LimitedListSection` ze tot 5/+10 zichtbare kaarten beperkt. Kandidaten matchen
  alleen met exact één cyclus via een niet-lege `String productSlug` en een `int
  iterationSequenceNumber` gelijk aan `sequenceNumber`; leveringen alleen via dezelfde productslug
  en een niet-lege `String iterationId` gelijk aan `id`. Ontbrekende of anders getypeerde waarden en
  sleutels met nul of meerdere matches belanden eenmaal in de interne unlinked-lijst en worden niet
  aan een scope-item toegeschreven.
- `iterationHistoryKind` in `iteration_evidence.dart` selecteert productslug-onafhankelijk een
  terminale bewijsregel voor `ACCEPTED`, `NEEDS_REVISION`, `REJECTED`, `NO_CHANGE` en `FAILED`, een
  actieve voortgangskaart voor `QUEUED` en `RUNNING`, en anders een veilige onbekende-statuskaart.
  De productslug bepaalt alleen productscope, sleutel en toegankelijke identificatie. Een stabiele
  sibling-key bestaat uit productslug, iteratie-id, cyclusnummer en alleen waar nodig een
  deterministische duplicaatpositie, zodat onverwachte dubbele cycli defensief blijven renderen.
- `iterationEvidencePresentation` hergebruikt
  `parseInstant`/`formatDateTime`, `classifyIterationOutcome`, `outcomeReasonLabel` en
  `iterationDecisionPresentation`. De datum valt per parseerpoging van `startedAt` terug op
  `createdAt`. Alleen een volledig geldig, aan hetzelfde iteratie-id gekoppeld
  handmatig-annuleringsrecord overschrijft uitkomst en reden; een aanwezig maar onbekend expliciet
  record blijft `Onbekend` en activeert geen afleiding.
- `IterationEvidenceRow` in `main.dart` rendert de vijf bewijswaarden en
  `IterationEvidenceButton` binnen één expliciete semanticscontainer. De gekoppelde opbrengst is
  uitsluitend `linked.deliveries.length` uit de bestaande exacte groepering; de onafhankelijke
  leveringsbronstatus bepaalt `laden…`, `niet beschikbaar` of het geladen aantal. Een responsieve
  `Wrap` gebruikt één, twee of drie kolommen. De native `OutlinedButton` heeft een eigen `FocusNode`,
  opent de bestaande `_showIteration`-detailroute en herstelt focus na sluiten of Escape. De
  toegankelijke actienaam bevat product, cyclus, datum en uitkomst.
- `IterationProgressCard` rendert actieve cycli uitsluitend met de gesloten veilige statusmapping,
  een bekende `currentRole`, rechtstreeks uit status bepaalde voortgang en één neutrale
  `IterationProgressButton`. Voor onbekende status blijven alleen `Status: Onbekend` en die actie
  over. Beide varianten zijn niet uitklapbaar, vormen een eigen semanticscontainer en renderen geen
  terminale of vrije metadata. De bovenliggende geschiedenis is één benoemde semanticsgroep.
  Hiervoor
  zijn geen API-, contract-, opslag-, telemetrie- of dependencywijzigingen toegevoegd.
- Geen extra dependencies voor formattering: datum/tijd wordt met eigen helpers naar het vaste formaat
  `dd-MM-yyyy HH:mm` in de lokale tijdzone gebracht, duur naar maximaal twee eenheden (`2u 13m`,
  `4m 12s`, `35s`). Backendtijdstempels zijn ISO-8601 in UTC; `parseInstant` is defensief en levert
  `null` bij ontbrekende of onleesbare waarden.
- Paginering gebeurt client-side: alle lijstdata komt in één refresh binnen, de frontend toont er
  standaard 5 van en laat er per klik op 'Meer' 10 extra zien. De zichtbaarheidstellers staan in
  `_OverviewPageState` (dus buiten de `FutureBuilder`) zodat de auto-refresh van 5 s de
  lijstbeperking behoudt. De expanded-state van cycluskaarten staat in de stateful kaarten zelf en
  blijft via hun stabiele keys behouden zolang de betreffende cycli geladen blijven.
- Teksten in de UI zijn Nederlands; commentaar legt het *waarom* vast, niet het *wat*.
- Formatteer nieuwe of gewijzigde code met `dart format`; laat ongerelateerde regels met rust, zodat de
  diff van een story leesbaar blijft (het bestand is historisch niet volledig dart-formatted).

## Bekende valkuilen

- De conclusie van een `shadow_iteration` (kolommen `status`/`critic_verdict`) is write-once zodra
  de iteratie een terminale staat bereikt
  (`ACCEPTED`/`NO_CHANGE`/`NEEDS_REVISION`/`REJECTED`/`FAILED`):
  `markAccepted`/`markReviewed`/`markFailed` in `ShadowIterationRepository`
  (`productfactory/.../iteration/ShadowIterationApi.kt`) hebben een `and status not in (...)`-guard
  in hun `WHERE`-clausule, zodat een tweede schrijfpoging 0 rijen raakt in plaats van de bestaande
  conclusie stilzwijgend te overschrijven; zo'n genegeerde poging wordt gelogd (`log.warn`, met
  iteratie-id).
- Handmatige annulering gebruikt een andere conditionele guard: alleen
  `status in ('QUEUED', 'RUNNING')` mag naar FAILED overgaan. Houd de statusupdate en insert in
  `shadow_iteration_decision` binnen dezelfde `@Transactional`-servicecall; anders kan de
  write-once-garantie losraken van de provenance.
- `WorkspacePublicationView` heeft geen tijdstempel; die lijst kan dus niet op 'nieuwste eerst'
  gesorteerd worden en houdt de volgorde van de backend.
- Widgettests met lange lijsten hebben een hoog testvenster nodig (`tester.view.physicalSize`), anders
  valt de 'Meer'-knop buiten beeld en mist de tap.

## Verificatiecommando's

Exact de commandoset uit `.factory/verification.yaml`:

| id | commando | working directory |
| --- | --- | --- |
| `repository-maven-verify` | `mvn -B --no-transfer-progress clean verify` | `.` |
| `dashboard-flutter-analyze` | `flutter analyze` | `dashboard-frontend` |
| `dashboard-flutter-test` | `flutter test` | `dashboard-frontend` |
| `agent-image-build` | `docker build --target build -f Dockerfile.agent .` (niet agent-runnable) | `.` |

Na een tester-AI-run voert de agentworker deze zelf uit en schrijft additive revisiongebonden evidence in
`AgentResultFile`; de factory valideert config, commandset, exitcodes en HEAD/worktree-tree onafhankelijk en
fail-closed. Timeout stopt parent en child-processen; een output-readerfout is nooit groen. Duration moet
exact met start/eind overeenkomen en samenvatting/rapportlocatie zijn begrensd.
