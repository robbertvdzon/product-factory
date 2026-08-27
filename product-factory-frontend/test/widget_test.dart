import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_frontend/authentication.dart';
import 'package:product_factory_frontend/build_identity.dart';
import 'package:product_factory_frontend/frontend_version_monitor.dart';
import 'package:product_factory_frontend/main.dart';
import 'package:product_factory_frontend/memory_ai_management.dart';
import 'package:product_factory_frontend/navigation_location.dart';
import 'package:product_factory_frontend/product_workspace.dart';
import 'package:product_factory_frontend/testbed.dart';

Finder appText(String value) => find.byWidgetPredicate(
  (widget) =>
      (widget is Text && widget.data == value) ||
      (widget is SelectableText && widget.data == value),
  description: 'Text or SelectableText containing exactly "$value"',
);

Finder appTextContaining(String value) => find.byWidgetPredicate(
  (widget) =>
      (widget is Text && (widget.data?.contains(value) ?? false)) ||
      (widget is SelectableText && (widget.data?.contains(value) ?? false)),
  description: 'Text or SelectableText containing "$value"',
);

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
    expect(appText('Nog geen producten'), findsNothing);

    session.complete(
      const AuthenticationStatus(authenticated: true, authRequired: false),
    );
    await tester.pumpAndSettle();
    expect(appText('Nog geen producten'), findsOneWidget);
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
      appText('Log in met het toegestane Google-account om verder te gaan.'),
      findsOneWidget,
    );
    await tester.tap(appText('Test Google-login'));
    await tester.pumpAndSettle();
    expect(gateway.lastGoogleToken, 'short-lived-google-token');
    expect(appText('Nog geen producten'), findsOneWidget);

    await tester.tap(find.byTooltip('Uitloggen'));
    await tester.pumpAndSettle();
    expect(gateway.logoutCsrf, 'csrf-from-backend');
    expect(federatedSignOutCalled, isTrue);
    expect(appText('Test Google-login'), findsOneWidget);
  });

  testWidgets('acceptatiebanner staat boven iedere funderingspagina', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 720));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: FoundationPage(
          showAcceptanceBanner: true,
          productGateway: FakeProductGateway(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      appText('Acceptatie — synthetische tijdelijke data — authenticatie uit'),
      findsOneWidget,
    );
    expect(appText('Nog geen producten'), findsOneWidget);
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
      appText('Acceptatie — synthetische tijdelijke data — authenticatie uit'),
      findsOneWidget,
    );
    expect(appText('Acceptatietesten'), findsOneWidget);
  });

  testWidgets('URL opent de juiste pagina en browsernavigatie blijft werken', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final navigation = FakeNavigationLocation(
      Uri.parse('/planning?product=hkh-autopilot'),
    );
    await tester.pumpWidget(
      ProductFactoryApp(
        productGateway: ResearchProductGateway(),
        navigationLocation: navigation,
        authenticationGateway: FakeAuthenticationGateway(
          sessionResult: Future.value(
            const AuthenticationStatus(
              authenticated: true,
              authRequired: false,
            ),
          ),
        ),
        versionGateway: FakeVersionGateway(),
      ),
    );
    await tester.pumpAndSettle();

    expect(appText('Geprioriteerde backlog'), findsOneWidget);
    expect(find.byType(SelectionArea), findsOneWidget);

    await tester.tap(appText('Ontwerp'));
    await tester.pumpAndSettle();
    expect(navigation.current.path, '/ontwerp');
    expect(navigation.current.queryParameters['product'], 'hkh-autopilot');

    navigation.navigateFromBrowser(
      Uri.parse('/kwaliteit?product=hkh-autopilot'),
    );
    await tester.pumpAndSettle();
    expect(appText('Kwaliteit'), findsWidgets);
  });

  testWidgets('gewone paginatekst kan met de muis worden geselecteerd', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      ProductFactoryApp(
        productGateway: ResearchProductGateway(),
        authenticationGateway: FakeAuthenticationGateway(
          sessionResult: Future.value(
            const AuthenticationStatus(
              authenticated: true,
              authRequired: false,
            ),
          ),
        ),
        versionGateway: FakeVersionGateway(),
      ),
    );
    await tester.pumpAndSettle();

    final selectable = find.byWidgetPredicate(
      (widget) => widget is SelectableText && widget.data == 'Overzicht',
    );
    final editableFinder = find.descendant(
      of: selectable,
      matching: find.byType(EditableText),
    );
    final editable = tester.widget<EditableText>(editableFinder);
    final editableRoot = tester.renderObject(editableFinder);
    late RenderEditable renderEditable;
    void findRenderEditable(RenderObject child) {
      if (child is RenderEditable) {
        renderEditable = child;
        return;
      }
      child.visitChildren(findRenderEditable);
    }

    editableRoot.visitChildren(findRenderEditable);
    Offset positionFor(int offset) {
      final endpoint = renderEditable
          .getEndpointsForSelection(TextSelection.collapsed(offset: offset))
          .single;
      return renderEditable.localToGlobal(endpoint.point) - const Offset(0, 2);
    }

    final gesture = await tester.startGesture(
      positionFor(0),
      kind: ui.PointerDeviceKind.mouse,
    );
    addTearDown(gesture.removePointer);
    await gesture.moveTo(positionFor(10));
    await tester.pump();
    await gesture.up();

    expect(editable.controller.selection.isCollapsed, isFalse);
    expect(editable.controller.selection.textInside('Overzicht'), isNotEmpty);
  });

  testWidgets('ververst handmatig en automatisch iedere twintig seconden', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final gateway = ResearchProductGateway();
    await tester.pumpWidget(
      MaterialApp(home: FoundationPage(productGateway: gateway)),
    );
    await tester.pumpAndSettle();
    expect(gateway.workspaceReads, 1);

    await tester.tap(find.byTooltip('Gegevens op deze pagina vernieuwen'));
    await tester.pump();
    await tester.pump();
    expect(gateway.workspaceReads, 2);

    await tester.pump(const Duration(seconds: 20));
    await tester.pump();
    expect(gateway.workspaceReads, 3);
  });

  testWidgets('processessies tonen starttijd duur en actieve toestand', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final gateway = ResearchProductGateway(
      planningSessions: const [
        {
          'id': {'value': 'oude-planning'},
          'status': 'SUCCEEDED',
          'startedAt': '2026-08-27T08:00:00Z',
          'finishedAt': '2026-08-27T08:03:12Z',
          'aiTaskIds': [],
        },
        {
          'id': {'value': 'lopende-planning'},
          'status': 'WAITING_FOR_AI',
          'startedAt': '2026-08-27T08:04:00Z',
          'aiTaskIds': [],
        },
      ],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: FoundationPage(
          productGateway: gateway,
          navigationLocation: FakeNavigationLocation(
            Uri.parse('/planning?product=hkh-autopilot'),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(appTextContaining('duur 3 min 12 sec'), findsOneWidget);
    expect(appTextContaining('actief: ja'), findsOneWidget);
    expect(appTextContaining('Gestart 27-08-2026'), findsNWidgets(2));
  });

  testWidgets('geblokkeerde planning toont een duidelijke herstelmelding', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final gateway = ResearchProductGateway(
      planningSessions: const [
        {
          'id': {'value': 'geblokkeerde-planning'},
          'status': 'BLOCKED',
          'startedAt': '2026-08-27T12:12:19Z',
          'blockedReason': 'Het plan kon nog niet worden opgeslagen.',
          'errorCode': 'PLANNING_PUBLICATION_CONFLICT',
          'aiTaskIds': [
            {'value': 'selectie-taak'},
            {'value': 'planning-taak'},
          ],
        },
      ],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: FoundationPage(
          productGateway: gateway,
          navigationLocation: FakeNavigationLocation(
            Uri.parse('/planning?product=hkh-autopilot'),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(
      appTextContaining('Planning kon niet worden afgerond'),
      findsOneWidget,
    );
    expect(appTextContaining('Het AI-plan is wel gemaakt'), findsOneWidget);
    expect(
      appTextContaining('Er is niets gedeeltelijk gepubliceerd'),
      findsOneWidget,
    );
    expect(
      appTextContaining('Foutcode: PLANNING_PUBLICATION_CONFLICT'),
      findsOneWidget,
    );
  });

  testWidgets(
    'productopdracht gebruikt een paginabrede editor met losse harde grenzen',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final gateway = ResearchProductGateway(
        assignment: const {
          'audience': 'Historisch geïnteresseerden',
          'goal': 'Historische bronnen toegankelijk maken.',
          'hardBoundaries': ['Eerste grens', 'Tweede grens'],
          'publicGitUrl': 'https://github.com/example/product.git',
          'version': 3,
        },
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ProductWorkspacePage(
              gateway: gateway,
              section: ProductWorkspaceSection.settings,
              initialProductId: 'hkh-autopilot',
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(appText('Opdracht bewerken'));
      await tester.pump();

      expect(find.byType(AlertDialog), findsNothing);
      expect(appText('Productopdracht bewerken'), findsOneWidget);
      expect(find.byKey(const ValueKey('hard-boundary-0')), findsOneWidget);
      expect(find.byKey(const ValueKey('hard-boundary-1')), findsOneWidget);

      await tester.enterText(
        find.byKey(const ValueKey('hard-boundary-0')),
        'Eerste grens\nmet een tweede regel',
      );
      await tester.ensureVisible(
        find.byKey(const ValueKey('add-hard-boundary')),
      );
      await tester.tap(find.byKey(const ValueKey('add-hard-boundary')));
      await tester.pump();
      await tester.enterText(
        find.byKey(const ValueKey('hard-boundary-2')),
        'Derde grens',
      );
      await tester.ensureVisible(find.byKey(const ValueKey('save-assignment')));
      await tester.tap(find.byKey(const ValueKey('save-assignment')));
      await tester.pumpAndSettle();

      expect(gateway.savedAssignment?['expectedVersion'], 3);
      expect(gateway.savedAssignment?['hardBoundaries'], [
        'Eerste grens\nmet een tweede regel',
        'Tweede grens',
        'Derde grens',
      ]);
      expect(appText('Productopdracht en testomgevingen'), findsOneWidget);
    },
  );

  testWidgets('nieuw signaal opent een ruime meerregelige editor', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ProductWorkspacePage(
            gateway: ResearchProductGateway(),
            section: ProductWorkspaceSection.signals,
            initialProductId: 'hkh-autopilot',
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(appText('Toevoegen'));
    await tester.pumpAndSettle();

    final input = find.byKey(const ValueKey('long-text-dialog-input'));
    expect(appText('Nieuw signaal'), findsOneWidget);
    expect(input, findsOneWidget);
    final size = tester.getSize(input);
    expect(size.width, greaterThanOrEqualTo(600));
    expect(size.height, greaterThanOrEqualTo(140));
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

    expect(appText('Overzicht'), findsAtLeast(1));
    await tester.tap(appText('Beheer'));
    await tester.pumpAndSettle();

    expect(appText('Richting, geheugen en techniek'), findsOneWidget);
    await tester.tap(appText('Release-informatie'));
    await tester.pumpAndSettle();

    expect(appText('Frontend'), findsOneWidget);
    expect(appText('Backend'), findsOneWidget);
    expect(appText('0.1.0+0123456789ab'), findsOneWidget);
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

    expect(appText('Nieuwe versie beschikbaar — vernieuwen'), findsOneWidget);
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

    await tester.tap(appText('Acceptatietesten'));
    await tester.pumpAndSettle();
    expect(appText('Schone technische fundering'), findsAtLeast(1));
    expect(appText('Dataset: product-stakeholder-v1'), findsOneWidget);

    await tester.tap(appText('Omgeving resetten'));
    await tester.pumpAndSettle();
    expect(
      appText(
        'Alle tijdelijke acceptatiewijzigingen verdwijnen. De vaste synthetische data wordt opnieuw geladen.',
      ),
      findsOneWidget,
    );
    await tester.tap(appText('Resetten'));
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

    expect(appText('Acceptatietesten'), findsNothing);
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

    await tester.tap(appText('Product aanmaken'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).at(0), 'HKH Autopilot');
    await tester.enterText(find.byType(TextField).at(1), 'HKH_AUTOPILOT');
    await tester.tap(appText('Aanmaken'));
    await tester.pumpAndSettle();

    expect(
      appText(
        'Gebruik 3–100 kleine letters, cijfers of koppeltekens; begin en eindig zonder koppelteken.',
      ),
      findsOneWidget,
    );
    expect(gateway.createdId, isNull);

    await tester.enterText(find.byType(TextField).at(1), 'hkh-autopilot');
    await tester.tap(appText('Aanmaken'));
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
      MaterialApp(
        home: ProductWorkspacePage(
          gateway: ResearchProductGateway(),
          section: ProductWorkspaceSection.design,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(appText('Bronnen verbinden · Onderzoek nodig'));
    await tester.pump();

    expect(appText('Nog niet klaar voor planning'), findsOneWidget);
    expect(appText('UX-modellen'), findsOneWidget);
    expect(appText('Onderzochte bronnen'), findsOneWidget);
    expect(
      appTextContaining('Noord-Hollands Archief · VALIDATED'),
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
      MaterialApp(
        home: Scaffold(
          body: ProductWorkspacePage(
            gateway: ResearchProductGateway(),
            section: ProductWorkspaceSection.planning,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(appText('Planning starten of hervatten'));
    await tester.pumpAndSettle();

    expect(
      appText(
        'Planning is gestart of hervat. De voortgang wordt automatisch bijgewerkt.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('planning kan dispatching aanzetten en toont story UX-modellen', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 1100));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final gateway = ResearchProductGateway();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ProductWorkspacePage(
            gateway: gateway,
            section: ProductWorkspaceSection.planning,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(appText('Bronnen verbinden'), findsOneWidget);
    expect(appTextContaining('1 story · Onderzoek nodig'), findsOneWidget);

    expect(appText('Dispatching aanzetten en versturen'), findsOneWidget);
    await tester.tap(appText('Dispatching aanzetten en versturen'));
    await tester.pumpAndSettle();
    expect(appText('Dispatching staat uit'), findsOneWidget);
    await tester.tap(appText('Aanzetten en versturen'));
    await tester.pumpAndSettle();
    expect(gateway.dispatchingChanges, 1);
    expect(gateway.dispatchRuns, 1);

    await tester.tap(appTextContaining('Story met UX-model · TODO'));
    await tester.pump();
    expect(appText('UX-modellen bij deze story'), findsOneWidget);
    expect(find.byTooltip('Open UX-model en zoom in'), findsOneWidget);
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
    await tester.tap(appText('Runtime-catalogus verversen'));
    await tester.pumpAndSettle();

    expect(gateway.refreshedPrefix, 'HKH_AUTOPILOT');
    expect(gateway.catalogPrefixes.last, 'HKH_AUTOPILOT');
    expect(appText('Agenttoegang · HKH_AUTOPILOT'), findsOneWidget);
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
  Future<void> setEpicApprovalMode(ProductSummary product, String mode) async {}
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
  Future<void> approveEpic(String epicId, int version) async {}
  @override
  Future<void> requestEpicRefinement(
    String epicId,
    int version,
    String reason,
  ) async {}
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
  ResearchProductGateway({
    this.planningSessions = const [],
    Map<String, Object?> assignment = const {},
  }) : assignment = Map<String, Object?>.from(assignment);

  bool dispatchingEnabled = false;
  int dispatchingChanges = 0;
  int dispatchRuns = 0;
  int workspaceReads = 0;
  final List<Map<String, Object?>> planningSessions;
  Map<String, Object?> assignment;
  Map<String, Object?>? savedAssignment;

  ProductSummary get product => ProductSummary(
    id: 'hkh-autopilot',
    name: 'HKH Autopilot',
    status: 'ACTIVE',
    dispatchingEnabled: dispatchingEnabled,
    version: dispatchingEnabled ? 2 : 1,
  );

  @override
  Future<List<ProductSummary>> products() async => [product];

  @override
  Future<void> setDispatching(ProductSummary product, bool enabled) async {
    dispatchingChanges++;
    dispatchingEnabled = enabled;
  }

  @override
  Future<void> runDispatcher(String productId) async {
    dispatchRuns++;
  }

  @override
  Future<void> saveAssignment(
    String productId,
    Map<String, Object?> body,
  ) async {
    savedAssignment = Map<String, Object?>.from(body);
    assignment = {
      ...body,
      'version': ((body['expectedVersion'] as num?)?.toInt() ?? 0) + 1,
    }..remove('expectedVersion');
  }

  @override
  Future<ProductWorkspaceData> workspace(ProductSummary product) async {
    workspaceReads++;
    return ProductWorkspaceData(
      product: product,
      assignment: assignment,
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
      stories: const [
        {
          'id': {'value': 'story-1'},
          'epicId': {'value': 'epic-1'},
          'epicVersion': 1,
          'sequenceNumber': 1,
          'type': 'PRODUCT_STORY',
          'title': 'Story met UX-model',
          'summary': 'Story gebruikt het gevalideerde ontwerp.',
          'content': 'Bouw de zichtbare gebruikersroute.',
          'acceptanceCriteria': ['De route volgt het UX-model.'],
          'uxDesign': 'Volg zoekscherm.png.',
          'uxArtifacts': [
            {
              'name': 'zoekscherm.png',
              'mediaType': 'image/png',
              'uri': '/api/ai/tasks/task-1/artifacts/main',
            },
          ],
          'dependencies': [],
          'priorityReason': 'Eerste zelfstandige waarde.',
          'status': 'TODO',
          'version': 1,
        },
      ],
      backlog: const [
        {
          'id': {'value': 'story-1'},
          'epicId': {'value': 'epic-1'},
          'epicVersion': 1,
          'sequenceNumber': 1,
          'type': 'PRODUCT_STORY',
          'title': 'Story met UX-model',
          'summary': 'Story gebruikt het gevalideerde ontwerp.',
          'content': 'Bouw de zichtbare gebruikersroute.',
          'acceptanceCriteria': ['De route volgt het UX-model.'],
          'uxDesign': 'Volg zoekscherm.png.',
          'uxArtifacts': [
            {
              'name': 'zoekscherm.png',
              'mediaType': 'image/png',
              'uri': '/api/ai/tasks/task-1/artifacts/main',
            },
          ],
          'dependencies': [],
          'priorityReason': 'Eerste zelfstandige waarde.',
          'status': 'TODO',
          'version': 1,
        },
      ],
      planningWorkItems: const [],
      planningSessions: planningSessions,
      qualitySnapshot: null,
      qualityHistory: const [],
      bugs: const [],
      verifications: const [],
      qualityWorkItems: const [],
      qualitySessions: const [],
    );
  }
}

class FakeNavigationLocation implements NavigationLocation {
  FakeNavigationLocation(this._current);

  Uri _current;
  VoidCallback? _listener;

  @override
  Uri get current => _current;

  @override
  VoidCallback listen(VoidCallback callback) {
    _listener = callback;
    return () => _listener = null;
  }

  @override
  void push(Uri location) => _current = location;

  @override
  void replace(Uri location) => _current = location;

  void navigateFromBrowser(Uri location) {
    _current = location;
    _listener?.call();
  }
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
