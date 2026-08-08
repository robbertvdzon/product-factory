# Development

## Commands

Het volledige vangnet (identiek aan `.factory/verification.yaml`):

- Build + backendtests: `mvn -B --no-transfer-progress clean verify` (vanuit de repo-root)
- Frontend-lint: `flutter analyze` (vanuit `dashboard-frontend`)
- Frontend-tests: `flutter test` (vanuit `dashboard-frontend`)
- Agent-image (niet agent-runnable, draait in CI): `docker build --target build -f Dockerfile.agent .`

Aanvullend lokaal:

- Formatteren frontend: `dart format <gewijzigde bestanden>` (niet de hele map: `lib/main.dart` is
  historisch niet volledig dart-formatted en zou dan onnodig grote diffs opleveren)
- Detekt op de Kotlin-modules: `mvn -B -Pquality verify`

Leg het volledige verplichte vangnet ook vast in `.factory/verification.yaml` schema 1: per command
een stabiele `id`, `argv`-lijst zonder impliciete shell, relatief bestaand `workingDirectory` en
`timeoutSeconds` (1..7200). Ontbrekende of onbekende config blokkeert testergoedkeuring.
Een working-directorysymlink mag niet buiten de repository uitkomen.

## Conventions

- Repo-structuur en stack: zie `technical-spec.md`; functioneel gedrag: zie `functional-spec.md`.
- Maven-modules: `productfactory-contracts`, `productfactory-common`, `productfactory`, `agentworker`,
  `dashboard-backend`. De Flutter-app `dashboard-frontend` staat buiten de Maven-reactor.
- Teststrategie frontend: pure logica (formattering, sortering) in losse libraries met unittests
  (`test/formatting_test.dart`), UI-gedrag met widgettests op herbruikbare widgets
  (`test/limited_list_test.dart`). Widgettests doen géén echte HTTP-calls.
- UI-teksten zijn Nederlands; commentaar beschrijft het *waarom*.
