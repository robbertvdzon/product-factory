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
  `limited_list.dart` — de 5/+10-lijstbeperking; `classification.dart` — de bestaande pure
  mappings op `status`/`criticVerdict`/`errorMessage` plus
  `iterationDecisionPresentation`, dat eerst een aan het iteratie-id gekoppeld `decision`-record
  selecteert en alleen bij ontbreken daarvan terugvalt op de afleiding. De vaste
  iteratie-uitkomstclassificatie naar één van vijf badge-waarden (inclusief de fallbackwaarde
  `niet-classificeerbaar` voor onbekende statuswaarden) en `classifyDecisionSource` naar
  `Evaluatie-agent`, `Technische fout` of `Onbekend`. De beslisbronmapping trimt invoer, vergelijkt
  bekende waarden hoofdlettergevoelig, accepteert alleen de bewezen paren `ACCEPT`/`ACCEPTED`,
  `REVISE`/`NEEDS_REVISION` en `REJECT`/`REJECTED` als `Evaluatie-agent`, en alleen `FAILED` zonder
  verdict maar met foutmelding als `Technische fout`; alle overige combinaties zijn `Onbekend`.
  Het bestand bevat daarnaast de AA-contrastkleuren en de `ClassificationBadge`-widget. Voor een
  iteratie met `status` QUEUED of RUNNING toont de iteratierij in plaats van de badge de
  `IterationProgressIndicator`-widget (`main.dart`), met `Semantics(liveRegion: true)` als
  Flutter-web-equivalent van `aria-live="polite"`; elke andere status toont een
  `ClassificationBadge`, behalve wanneer expliciete provenance aanwezig is. Dan krijgen bron en
  reden voorrang en worden de afgeleide badge en `outcomeReason` zowel in lijst als detail
  onderdrukt. `IterationSessionDialog` toont bij expliciete handmatige annulering daarnaast het
  mechanisme en `decidedAt` via de bestaande lokale datum-/tijdformatter. Zonder expliciet record
  toont lijst en detail de conservatief afgeleide bron zichtbaar en toegankelijk met `(Afgeleid)`.
  Elke iteratierij bevat ook één
  `IterationDecisionSourceButton` (`main.dart`): een native `OutlinedButton` die de bestaande
  detaildialoog voor het gekoppelde iteratie-id opent. De `ListTile` zelf heeft geen `onTap` of
  navigatie-chevron, zodat er geen geneste of dubbele detailbediening is. De button bewaart een
  eigen `FocusNode`; na sluiten via de sluitactie of Escape keert de focus terug naar de opener.
  De dialoogtitel gebruikt het user-facing `sequenceNumber`, met het iteratie-id als fallback als
  dat nummer ontbreekt. Openen en sluiten gebruikt uitsluitend de bestaande GET-calls.
  `roadmap.dart` bevat het epic-contract voor de UI, de horizontale dependencygrafiek, kaartjes en
  de maak-/detaildialogen. De process-rank en score zijn daar alleen-lezen; klant-rank,
  dependencies, titel, beschrijving en status worden via de epic-routes opgeslagen.
- Geen extra dependencies voor formattering: datum/tijd wordt met eigen helpers naar het vaste formaat
  `dd-MM-yyyy HH:mm` in de lokale tijdzone gebracht, duur naar maximaal twee eenheden (`2u 13m`,
  `4m 12s`, `35s`). Backendtijdstempels zijn ISO-8601 in UTC; `parseInstant` is defensief en levert
  `null` bij ontbrekende of onleesbare waarden.
- Paginering gebeurt client-side: alle lijstdata komt in één refresh binnen, de frontend toont er
  standaard 5 van en laadt er per klik op 'Meer' 10 bij. De tellers staan in `_OverviewPageState`
  (dus buiten de `FutureBuilder`) zodat de auto-refresh van 5 s de uitklapstand behoudt.
- Teksten in de UI zijn Nederlands; commentaar legt het *waarom* vast, niet het *wat*.
- Formatteer nieuwe of gewijzigde code met `dart format`; laat ongerelateerde regels met rust, zodat de
  diff van een story leesbaar blijft (het bestand is historisch niet volledig dart-formatted).

## Bekende valkuilen

- De conclusie van een `shadow_iteration` (kolommen `status`/`critic_verdict`) is write-once zodra
  de iteratie een terminale staat bereikt (`ACCEPTED`/`NEEDS_REVISION`/`REJECTED`/`FAILED`):
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
