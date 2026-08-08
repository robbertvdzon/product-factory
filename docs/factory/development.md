# Development

## Commands

Het volledige vangnet (identiek aan `.factory/verification.yaml`):

- Build + backendtests: `mvn -B --no-transfer-progress clean verify` (vanuit de repo-root)
- Frontend-lint: `flutter analyze` (vanuit `dashboard-frontend`)
- Frontend-tests: `flutter test` (vanuit `dashboard-frontend`)
- Agent-image (niet agent-runnable, draait in CI): `docker build --target build -f Dockerfile.agent .`
- Frontend-image (niet agent-runnable, draait in CI): `docker build -f dashboard-frontend/Dockerfile dashboard-frontend`

Aanvullend lokaal:

- Formatteren frontend: `dart format <gewijzigde bestanden>` (niet de hele map: `lib/main.dart` is
  historisch niet volledig dart-formatted en zou dan onnodig grote diffs opleveren)
- Detekt op de Kotlin-modules: `mvn -B -Pquality verify`

Leg het volledige verplichte vangnet ook vast in `.factory/verification.yaml` schema 1: per command
een stabiele `id`, `argv`-lijst zonder impliciete shell, relatief bestaand `workingDirectory` en
`timeoutSeconds` (1..7200). Ontbrekende of onbekende config blokkeert testergoedkeuring.
Een working-directorysymlink mag niet buiten de repository uitkomen.

## Flutter-toolchain

Drie plekken bepalen met welke Flutter/Dart de frontend wordt gebouwd; ze horen dezelfde
Flutter-minor te delen, want `dashboard-frontend/pubspec.lock` legt een Dart-ondergrens vast:

- `.github/workflows/verify.yml`: `flutter-version: 3.44.6` (analyze, test, web-build)
- `dashboard-frontend/Dockerfile`: `ghcr.io/cirruslabs/flutter:3.44.0` (productie-image; 3.44.6 is
  niet als image-tag gepubliceerd, 3.44.0 is de nieuwste beschikbare 3.44)
- de agentcontainer, momenteel Flutter 3.44.x / Dart 3.12

Loopt dat uiteen, dan herschrijft een `flutter pub get` op de nieuwste toolchain de lock met een
Dart-ondergrens die de oudere image niet meer haalt. Controleer bij elke frontend-story of
`pubspec.lock` meeverandert en of de Dockerfile-base daar nog bij past.

## Conventions

- Repo-structuur en stack: zie `technical-spec.md`; functioneel gedrag: zie `functional-spec.md`.
- Maven-modules: `productfactory-contracts`, `productfactory-common`, `productfactory`, `agentworker`,
  `dashboard-backend`. De Flutter-app `dashboard-frontend` staat buiten de Maven-reactor.
- Teststrategie frontend: pure logica (formattering, sortering) in losse libraries met unittests
  (`test/formatting_test.dart`), UI-gedrag met widgettests op herbruikbare widgets
  (`test/limited_list_test.dart`). Widgettests doen géén echte HTTP-calls.
- UI-teksten zijn Nederlands; commentaar beschrijft het *waarom*.
