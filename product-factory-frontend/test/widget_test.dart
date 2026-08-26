import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_frontend/authentication.dart';
import 'package:product_factory_frontend/build_identity.dart';
import 'package:product_factory_frontend/frontend_version_monitor.dart';
import 'package:product_factory_frontend/main.dart';
import 'package:product_factory_frontend/memory_ai_management.dart';
import 'package:product_factory_frontend/product_workspace.dart';
import 'package:product_factory_frontend/testbed.dart';

void main() {
  testWidgets('wacht op sessiestatus voordat inhoud zichtbaar wordt', (
    tester,
  ) async {
    final session = Completer<AuthenticationStatus>();
    await tester.pumpWidget(
      ProductFactoryApp(
        productGateway: const FakeProductGateway(),
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
        productGateway: const FakeProductGateway(),
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
      const MaterialApp(
        home: FoundationPage(
          showAcceptanceBanner: true,
          productGateway: FakeProductGateway(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Acceptatie — synthetische tijdelijke data — authenticatie uit',
      ),
      findsOneWidget,
    );
    expect(find.text('Nog geen producten'), findsOneWidget);
    // Renderfouten worden door de binding als testfout gerapporteerd.
  });

  testWidgets('backendbevestiging activeert acceptatiebanner en Testbed', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      ProductFactoryApp(
        productGateway: const FakeProductGateway(),
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
      MaterialApp(
        home: FoundationPage(
          versionGateway: FakeVersionGateway(),
          productGateway: const FakeProductGateway(),
          memoryAiGateway: const FakeMemoryAiGateway(),
        ),
      ),
    );

    expect(find.text('Overzicht'), findsAtLeast(1));
    await tester.tap(find.text('Beheer'));
    await tester.pumpAndSettle();

    expect(find.text('Richting, geheugen en techniek'), findsOneWidget);
    await tester.tap(find.text('Release-informatie'));
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
          productGateway: const FakeProductGateway(),
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
          productGateway: const FakeProductGateway(),
          showAcceptanceBanner: true,
          versionGateway: FakeVersionGateway(),
          testControlGateway: gateway,
        ),
      ),
    );

    await tester.tap(find.text('Acceptatietesten'));
    await tester.pumpAndSettle();
    expect(find.text('Schone technische fundering'), findsAtLeast(1));
    expect(find.text('Dataset: product-stakeholder-v1'), findsOneWidget);

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
      MaterialApp(
        home: FoundationPage(
          versionGateway: FakeVersionGateway(),
          productGateway: const FakeProductGateway(),
        ),
      ),
    );

    expect(find.text('Acceptatietesten'), findsNothing);
  });

  testWidgets('product aanmaken valideert het stabiele ID voor verzending', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final gateway = RecordingProductGateway();
    await tester.pumpWidget(
      MaterialApp(home: FoundationPage(productGateway: gateway)),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Product aanmaken'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).at(0), 'HKH Autopilot');
    await tester.enterText(find.byType(TextField).at(1), 'HKH_AUTOPILOT');
    await tester.tap(find.text('Aanmaken'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Gebruik 3–100 kleine letters, cijfers of koppeltekens; begin en eindig zonder koppelteken.',
      ),
      findsOneWidget,
    );
    expect(gateway.createdId, isNull);

    await tester.enterText(find.byType(TextField).at(1), 'hkh-autopilot');
    await tester.tap(find.text('Aanmaken'));
    await tester.pumpAndSettle();

    expect(gateway.createdName, 'HKH Autopilot');
    expect(gateway.createdId, 'hkh-autopilot');
  });

  testWidgets('ontwerp toont onrijpe status bronnen en UX-modellen', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      const MaterialApp(
        home: ProductWorkspacePage(
          gateway: ResearchProductGateway(),
          section: ProductWorkspaceSection.design,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Bronnen verbinden · NEEDS_RESEARCH'));
    await tester.pump();

    expect(find.text('Nog niet klaar voor planning'), findsOneWidget);
    expect(find.text('UX-modellen'), findsOneWidget);
    expect(find.text('Onderzochte bronnen'), findsOneWidget);
    expect(
      find.textContaining('Noord-Hollands Archief · VALIDATED'),
      findsOneWidget,
    );

    final uxModel = find.byTooltip('Open UX-model en zoom in');
    final openAction = find.descendant(
      of: uxModel,
      matching: find.byType(InkWell),
    );
    tester.widget<InkWell>(openAction).onTap!();
    await tester.pumpAndSettle();
    expect(find.byTooltip('Uitzoomen'), findsOneWidget);
    expect(find.byTooltip('Zoom herstellen'), findsOneWidget);
    expect(find.byTooltip('Inzoomen'), findsOneWidget);
    expect(find.byTooltip('Sluiten'), findsOneWidget);
  });

  testWidgets('planning starten geeft direct zichtbare terugmelding', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: ProductWorkspacePage(
            gateway: ResearchProductGateway(),
            section: ProductWorkspaceSection.planning,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Planning starten of hervatten'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Planning is gestart of hervat. De voortgang wordt automatisch bijgewerkt.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('Runtime-catalogus gebruikt de gekozen projectprefix', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final gateway = FakeAgentRuntimeManagementGateway();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: MemoryAiManagementPanel(gateway: gateway),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(gateway.catalogPrefixes, contains('HKH'));

    await tester.enterText(find.byType(TextFormField).last, 'HKH_AUTOPILOT');
    await tester.tap(find.text('Runtime-catalogus verversen'));
    await tester.pumpAndSettle();

    expect(gateway.refreshedPrefix, 'HKH_AUTOPILOT');
    expect(gateway.catalogPrefixes.last, 'HKH_AUTOPILOT');
    expect(find.text('Agenttoegang · HKH_AUTOPILOT'), findsOneWidget);
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

class FakeMemoryAiGateway implements MemoryAiGateway {
  const FakeMemoryAiGateway();
  @override
  Future<List<Map<String, Object?>>> products() async => const [];
  @override
  Future<List<Map<String, Object?>>> aiSettings() async => const [];
  @override
  Future<List<Map<String, Object?>>> roles(String productId) async => const [];
  @override
  Future<Map<String, Object?>> budget(String productId, String role) =>
      throw UnimplementedError();
  @override
  Future<List<Map<String, Object?>>> items(
    String productId,
    String role, {
    DateTime? date,
  }) => throw UnimplementedError();
  @override
  Future<List<Map<String, Object?>>> history(
    String productId,
    String role,
    String itemId,
  ) => throw UnimplementedError();
  @override
  Future<void> add(
    String productId,
    String role,
    String title,
    String content,
    String reason,
  ) => throw UnimplementedError();
  @override
  Future<void> replace(
    String productId,
    String role,
    Map<String, Object?> item,
    String title,
    String content,
    String reason,
  ) => throw UnimplementedError();
  @override
  Future<void> retract(
    String productId,
    String role,
    Map<String, Object?> item,
    String reason,
  ) => throw UnimplementedError();
  @override
  Future<void> updateAi(
    Map<String, Object?> setting,
    String provider,
    String model,
    bool enabled,
  ) => throw UnimplementedError();
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
      datasetVersion: 'product-stakeholder-v1',
      testbedVersion: '0.2.0',
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

class FakeProductGateway implements ProductGateway {
  const FakeProductGateway();
  @override
  Future<List<ProductSummary>> products() async => const [];
  @override
  Future<ProductWorkspaceData> workspace(ProductSummary product) =>
      throw UnimplementedError();
  @override
  Future<void> addMeetingMessage(
    String meetingId,
    int version,
    String text,
  ) async {}
  @override
  Future<void> answerQuestion(
    String questionId,
    int version,
    String meetingId,
    String messageId,
    String answer,
  ) async {}
  @override
  Future<void> closeMeeting(
    String meetingId,
    int version,
    String minutes,
    String? openOutcome,
  ) async {}
  @override
  Future<void> completeSignal(
    String signalId,
    int version,
    String outcome,
  ) async {}
  @override
  Future<void> createDecision(String productId, String decision) async {}
  @override
  Future<void> createMeeting(String productId, String reason) async {}
  @override
  Future<void> createProduct(String name, String? requestedId) async {}
  @override
  Future<void> createSignal(String productId, String text) async {}
  @override
  Future<void> reviewSignal(String signalId, int version) async {}
  @override
  Future<void> runProductDesign(String productId) async {}
  @override
  Future<void> reviseDecision(
    String productId,
    String decisionId,
    int version,
    String decision,
  ) async {}
  @override
  Future<void> saveAssignment(
    String productId,
    Map<String, Object?> body,
  ) async {}
  @override
  Future<void> saveSchedule(
    String productId,
    String process,
    Map<String, Object?> body,
  ) async {}
  @override
  Future<void> saveTestConfiguration(
    String productId,
    Map<String, Object?> body,
  ) async {}
  @override
  Future<void> setDispatching(ProductSummary product, bool enabled) async {}
  @override
  Future<void> setStatus(ProductSummary product, String status) async {}
  @override
  Future<void> supersedeDecision(
    String productId,
    String decisionId,
    int version,
    String replacement,
  ) async {}
  @override
  Future<void> withdrawDecision(
    String productId,
    String decisionId,
    int version,
    String reason,
  ) async {}
  @override
  Future<void> withdrawEpic(String epicId, int version, String reason) async {}
  @override
  Future<void> cancelEpic(String epicId, int version, String reason) async {}
  @override
  Future<void> runProductPlanning(String productId) async {}
  @override
  Future<void> requestManualReplan(String productId, String reason) async {}
  @override
  Future<void> reprioritizeEpic(
    String productId,
    String epicId,
    String reason,
  ) async {}
  @override
  Future<void> runQuality(String productId) async {}
  @override
  Future<void> retryQualityWorkItem(String workItemId) async {}
  @override
  Future<void> runDispatcher(String productId) async {}
  @override
  Future<void> runScheduledProcess(String productId, String process) async {}
}

class ResearchProductGateway extends FakeProductGateway {
  const ResearchProductGateway();

  static const product = ProductSummary(
    id: 'hkh-autopilot',
    name: 'HKH Autopilot',
    status: 'ACTIVE',
    dispatchingEnabled: false,
    version: 1,
  );

  @override
  Future<List<ProductSummary>> products() async => const [product];

  @override
  Future<ProductWorkspaceData> workspace(ProductSummary product) async =>
      ProductWorkspaceData(
        product: product,
        assignment: const {},
        testConfiguration: null,
        schedules: const [],
        signals: const [],
        questions: const [],
        meetings: const [],
        decisions: const [],
        decisionArchive: const [],
        epics: const [
          {
            'id': {'value': 'epic-1'},
            'title': 'Bronnen verbinden',
            'status': 'NEEDS_RESEARCH',
            'version': 1,
            'summary': 'Eerst concrete bronnen valideren.',
            'problem': 'Er zijn nog geen gevalideerde gegevensbronnen.',
            'solution': 'Onderzoek en verbind publieke collecties.',
            'uxDesign': 'Zoekscherm met bronverwijzingen.',
            'uxArtifacts': [
              {
                'name': 'zoekscherm.png',
                'mediaType': 'image/png',
                'uri': '/api/ai/tasks/task-1/artifacts/main',
              },
            ],
            'readiness': {
              'readyForPlanning': false,
              'unmetConditions': ['Valideer minimaal twee bronnen.'],
              'openQuestions': [],
            },
            'researchSources': [
              {
                'name': 'Noord-Hollands Archief',
                'provider': 'Noord-Hollands Archief',
                'uri': 'https://example.org/archive',
                'accessMethod': 'Publieke zoekroute',
                'license': 'Rechten per object',
                'coverage': 'Regionale historische records',
                'status': 'VALIDATED',
                'validationEvidence': 'Zoekroute geopend en records gevonden.',
              },
            ],
            'acceptanceCriteria': ['Bronnen zijn zichtbaar.'],
            'slicabilityRationale': 'Eén gebruikersroute.',
            'directionReferences': [],
          },
        ],
        designSessions: const [],
        epicHistories: const {'epic-1': []},
        stories: const [],
        backlog: const [],
        planningWorkItems: const [],
        planningSessions: const [],
        qualitySnapshot: null,
        qualityHistory: const [],
        bugs: const [],
        verifications: const [],
        qualityWorkItems: const [],
        qualitySessions: const [],
      );
}

class RecordingProductGateway extends FakeProductGateway {
  String? createdName;
  String? createdId;

  @override
  Future<void> createProduct(String name, String? requestedId) async {
    createdName = name;
    createdId = requestedId;
  }
}

class FakeAgentRuntimeManagementGateway extends FakeMemoryAiGateway
    implements AgentRuntimeGateway {
  final List<String> catalogPrefixes = [];
  String? refreshedPrefix;

  @override
  Future<List<Map<String, Object?>>> products() async => const [
    {'id': 'hkh-autopilot', 'name': 'HKH Autopilot'},
  ];

  @override
  Future<List<Map<String, Object?>>> aiTasks() async => const [];

  @override
  Future<List<Map<String, Object?>>> environmentCatalog(
    String projectPrefix,
  ) async {
    catalogPrefixes.add(projectPrefix);
    return const [];
  }

  @override
  Future<List<Map<String, Object?>>> productEnvironmentKeys(
    String productId,
  ) async => const [];

  @override
  Future<void> refreshEnvironmentCatalog(String projectPrefix) async {
    refreshedPrefix = projectPrefix;
  }

  @override
  Future<void> setProductEnvironmentKey(
    String productId,
    String name,
    bool active,
    int expectedVersion,
  ) async {}

  @override
  Future<void> setAgentEnvironmentGrant(
    String productId,
    String name,
    String role,
    bool granted,
  ) async {}

  @override
  Future<void> cancelAiTask(String taskId, String reason) async {}
}
