import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_frontend/authentication.dart';
import 'package:product_factory_frontend/build_identity.dart';
import 'package:product_factory_frontend/frontend_version_monitor.dart';
import 'package:product_factory_frontend/main.dart';
import 'package:product_factory_frontend/testbed.dart';

void main() {
  testWidgets('wacht op sessiestatus voordat inhoud zichtbaar wordt', (
    tester,
  ) async {
    final session = Completer<AuthenticationStatus>();
    await tester.pumpWidget(
      ProductFactoryApp(
        authenticationGateway: FakeAuthenticationGateway(
          sessionResult: session.future,
        ),
      ),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('Nog geen producten'), findsNothing);

    session.complete(
      const AuthenticationStatus(authenticated: true, authRequired: false),
    );
    await tester.pumpAndSettle();
    expect(find.text('Nog geen producten'), findsOneWidget);
  });

  testWidgets('wisselt Google-login om en kan de eigen sessie uitloggen', (
    tester,
  ) async {
    final gateway = FakeAuthenticationGateway(
      sessionResult: Future.value(
        const AuthenticationStatus(authenticated: false, authRequired: true),
      ),
    );
    var federatedSignOutCalled = false;
    await tester.pumpWidget(
      ProductFactoryApp(
        authenticationGateway: gateway,
        versionGateway: FakeVersionGateway(),
        federatedSignOut: () async => federatedSignOutCalled = true,
        googleLoginButtonBuilder: (onToken) => FilledButton(
          onPressed: () => onToken('short-lived-google-token'),
          child: const Text('Test Google-login'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Log in met het toegestane Google-account om verder te gaan.'),
      findsOneWidget,
    );
    await tester.tap(find.text('Test Google-login'));
    await tester.pumpAndSettle();
    expect(gateway.lastGoogleToken, 'short-lived-google-token');
    expect(find.text('Nog geen producten'), findsOneWidget);

    await tester.tap(find.byTooltip('Uitloggen'));
    await tester.pumpAndSettle();
    expect(gateway.logoutCsrf, 'csrf-from-backend');
    expect(federatedSignOutCalled, isTrue);
    expect(find.text('Test Google-login'), findsOneWidget);
  });

  testWidgets('acceptatiebanner staat boven iedere funderingspagina', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 720));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      const MaterialApp(home: FoundationPage(showAcceptanceBanner: true)),
    );

    expect(
      find.text(
        'Acceptatie — synthetische tijdelijke data — authenticatie uit',
      ),
      findsOneWidget,
    );
    expect(find.text('Nog geen producten'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('backendbevestiging activeert acceptatiebanner en Testbed', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      ProductFactoryApp(
        authenticationGateway: FakeAuthenticationGateway(
          sessionResult: Future.value(
            const AuthenticationStatus(
              authenticated: true,
              authRequired: false,
              environment: 'acceptance',
            ),
          ),
        ),
        versionGateway: FakeVersionGateway(),
        testControlGateway: FakeTestControlGateway(),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Acceptatie — synthetische tijdelijke data — authenticatie uit',
      ),
      findsOneWidget,
    );
    expect(find.text('Acceptatietesten'), findsOneWidget);
  });

  testWidgets('beheer toont frontend en backendidentiteit op brede schermen', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(home: FoundationPage(versionGateway: FakeVersionGateway())),
    );

    expect(find.byType(NavigationRail), findsOneWidget);
    await tester.tap(find.text('Beheer'));
    await tester.pumpAndSettle();

    expect(find.text('Frontend'), findsOneWidget);
    expect(find.text('Backend'), findsOneWidget);
    expect(find.text('0.1.0+0123456789ab'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('nieuwere frontendbuild toont precies één vernieuwmelding', (
    tester,
  ) async {
    final current = concreteIdentity('0.1.0+aaaaaaaaaaaa');
    await tester.pumpWidget(
      MaterialApp(
        home: FoundationPage(
          versionGateway: FakeVersionGateway(),
          frontendVersionSource: FakeFrontendVersionSource(
            concreteIdentity('0.1.0+bbbbbbbbbbbb'),
          ),
          currentBuildIdentity: current,
          onReload: () {},
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Nieuwe versie beschikbaar — vernieuwen'), findsOneWidget);
  });

  testWidgets('Testbed is alleen in acceptatie zichtbaar en kan resetten', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final gateway = FakeTestControlGateway();
    await tester.pumpWidget(
      MaterialApp(
        home: FoundationPage(
          showAcceptanceBanner: true,
          versionGateway: FakeVersionGateway(),
          testControlGateway: gateway,
        ),
      ),
    );

    await tester.tap(find.text('Acceptatietesten'));
    await tester.pumpAndSettle();
    expect(find.text('Schone technische fundering'), findsAtLeast(1));
    expect(find.text('Dataset: foundation-v1'), findsOneWidget);

    await tester.tap(find.text('Omgeving resetten'));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Alle tijdelijke acceptatiewijzigingen verdwijnen. De vaste synthetische data wordt opnieuw geladen.',
      ),
      findsOneWidget,
    );
    await tester.tap(find.text('Resetten'));
    await tester.pumpAndSettle();

    expect(gateway.resetScenario, 'foundation-clean');
    expect(gateway.resetBrowserSession, startsWith('browser-'));
  });

  testWidgets('productie toont geen navigatie naar Testbed', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(home: FoundationPage(versionGateway: FakeVersionGateway())),
    );

    expect(find.text('Acceptatietesten'), findsNothing);
  });
}

class FakeVersionGateway implements VersionGateway {
  @override
  Future<BuildIdentity> backendIdentity() async => BuildIdentity.validated(
    applicationVersion: '0.1.0',
    apiVersion: '1',
    gitRevision: '0123456789abcdef0123456789abcdef01234567',
    buildTime: '2026-08-24T18:00:00Z',
    environment: 'production',
    buildIdentity: '0.1.0+0123456789ab',
  );
}

class FakeFrontendVersionSource implements FrontendVersionSource {
  FakeFrontendVersionSource(this.identity);
  final BuildIdentity identity;

  @override
  Future<BuildIdentity> latest() async => identity;
}

BuildIdentity concreteIdentity(String buildIdentity) => BuildIdentity(
  applicationVersion: '0.1.0',
  apiVersion: '1',
  gitRevision: ''.padLeft(40, 'a'),
  buildTime: '2026-08-24T18:00:00Z',
  environment: 'production',
  buildIdentity: buildIdentity,
);

class FakeAuthenticationGateway implements AuthenticationGateway {
  FakeAuthenticationGateway({required this.sessionResult});

  final Future<AuthenticationStatus> sessionResult;
  String? lastGoogleToken;
  String? logoutCsrf;

  @override
  Future<AuthenticationStatus> googleLogin(String idToken) async {
    lastGoogleToken = idToken;
    return const AuthenticationStatus(
      authenticated: true,
      authRequired: true,
      stakeholderEmail: 'stakeholder@example.com',
      csrfToken: 'csrf-from-backend',
    );
  }

  @override
  Future<void> logout(String? csrfToken) async {
    logoutCsrf = csrfToken;
  }

  @override
  Future<AuthenticationStatus> session() => sessionResult;
}

class FakeTestControlGateway implements TestControlGateway {
  String? resetScenario;
  String? resetBrowserSession;

  @override
  Future<TestbedSnapshot> load() async => TestbedSnapshot(
    active: const TestScenarioDetails(
      scenario: TestScenarioSummary(
        key: 'foundation-clean',
        version: '1',
        title: 'Schone technische fundering',
        description: 'Vaste synthetische basisdata.',
      ),
      datasetVersion: 'foundation-v1',
      testbedVersion: '0.1.0',
      currentStep: 0,
    ),
    scenarios: const [
      TestScenarioSummary(
        key: 'foundation-clean',
        version: '1',
        title: 'Schone technische fundering',
        description: 'Vaste synthetische basisdata.',
      ),
    ],
  );

  @override
  Future<void> reset(String scenarioKey, String browserSessionId) async {
    resetScenario = scenarioKey;
    resetBrowserSession = browserSessionId;
  }

  @override
  Future<void> activate(String scenarioKey, String browserSessionId) async {}
}
