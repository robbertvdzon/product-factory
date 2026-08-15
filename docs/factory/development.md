# Development

## Commands

Het volledige vangnet (identiek aan `.factory/verification.yaml`):

- Build + backendtests: `mvn -B --no-transfer-progress clean verify` (vanuit de repo-root)
- Frontend-lint: `flutter analyze` (vanuit `dashboard-frontend`)
- Frontend-tests: `flutter test` (vanuit `dashboard-frontend`)
- Docker Engine-runner-tests: `python3 -B .factory/test_docker_engine_build.py`
- Agent-image (niet agent-runnable, draait in CI): `docker build --target build -f Dockerfile.agent .`
- Frontend-image met veilige defaults: `python3 -B .factory/docker_engine_build.py --context dashboard-frontend`
- Frontend-image met metadata: `python3 -B .factory/docker_engine_build.py --context dashboard-frontend --build-arg BUILD_ENVIRONMENT=preview --build-arg SOURCE_REVISION=0123456789abcdef0123456789abcdef01234567 --build-arg DEPLOYED_AT=2026-08-15T18:00:00Z`

De frontend-imagecommando's gebruiken de lokale Docker Engine-socket omdat de agentcontainer bewust
geen Docker-CLI bevat. In CI blijft de workflow de normale Buildx-action gebruiken.

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
