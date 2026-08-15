import 'dart:convert';
import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:product_factory_dashboard/start_availability.dart';
import 'package:shared_preferences/shared_preferences.dart';

Widget _panelHarness(Map<String, dynamic> product, {VoidCallback? onStart}) =>
    MaterialApp(
      home: Scaffold(
        body: StartAvailabilityPanel(
          availability: StartAvailability.fromProduct(product),
          onStart: onStart ?? () {},
        ),
      ),
    );

Map<String, dynamic> _dashboardProduct() => {
  'slug': 'demo',
  'name': 'Demo product',
  'status': 'paused',
  'workspaceOwnership': 'owner',
  'developmentMode': 'autonomous',
  'maxStoriesPerCycle': 3,
  'wipLimit': 2,
  'aiProvider': 'openai',
  'aiModel': 'test-model',
  'iterationTimes': ['03:00'],
  'meetingRequestedTopics': <String>[],
  'meetingRequestedAt': null,
};

Map<String, dynamic> _longRunningCycle() => {
  'id': 'running-cycle-technical-id',
  'productSlug': 'demo',
  'sequenceNumber': 91,
  'status': 'RUNNING',
  'mode': 'autonomous',
  'currentRole': 'researcher',
  'createdAt': '2020-01-01T00:00:00Z',
  'startedAt': '2020-01-01T00:00:00Z',
  'completedAt': null,
  'candidateCount': 0,
  'acceptedCandidateCount': 0,
  'criticVerdict': null,
  'errorMessage': null,
  'outcomeReason': null,
  'decision': null,
};

MockClient _dashboardClient(
  List<Map<String, dynamic>> cycles,
  List<String> requests,
) => MockClient((request) async {
  requests.add('${request.method} ${request.url.path}');
  switch (request.url.path) {
    case '/api/products':
      return http.Response(jsonEncode([_dashboardProduct()]), 200);
    case '/api/shadow-iterations':
      return http.Response(jsonEncode(cycles), 200);
    case '/api/ai-catalog':
      return http.Response(jsonEncode(<String, dynamic>{}), 200);
    default:
      return http.Response(jsonEncode(<dynamic>[]), 200);
  }
});

typedef _DashboardSnapshot = ({
  bool buttonDisabled,
  String blockedSemantics,
  List<String> dialogTexts,
});

Future<_DashboardSnapshot> _captureDashboard(
  WidgetTester tester,
  List<Map<String, dynamic>> cycles,
) async {
  tester.view.physicalSize = const Size(1400, 3000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final requests = <String>[];
  final client = _dashboardClient(cycles, requests);
  late _DashboardSnapshot result;

  await http.runWithClient(() async {
    await tester.pumpWidget(const ProductFactoryDashboard());
    await tester.pump();
    await tester.pump();
    final semantics = tester.ensureSemantics();

    final startButton = tester.widget<FilledButton>(
      find.descendant(
        of: find.byType(StartCycleButton),
        matching: find.byType(FilledButton),
      ),
    );
    final blockedSemantics = tester
        .getSemantics(
          find.byKey(const ValueKey('start-availability-blocked-group')),
        )
        .getSemanticsData()
        .label;
    final requestsBeforeDetails = List<String>.of(requests);

    await tester.tap(find.byType(StartAvailabilityDetailsButton));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    final dialog = find.byType(AlertDialog);
    final dialogTexts = tester
        .widgetList<Text>(
          find.descendant(of: dialog, matching: find.byType(Text)),
        )
        .map((text) => text.data)
        .whereType<String>()
        .toList(growable: false);

    expect(requests, requestsBeforeDetails);
    expect(
      dialogTexts.join(' '),
      isNot(
        anyOf(contains('RUNNING'), contains('looptijd'), contains('stilstand')),
      ),
    );

    result = (
      buttonDisabled: startButton.onPressed == null,
      blockedSemantics: blockedSemantics,
      dialogTexts: dialogTexts,
    );
    semantics.dispose();

    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pumpWidget(const SizedBox.shrink());
  }, () => client);

  return result;
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets(
    'active/product-factory houdt alleen de bestaande actieve startknop zonder melding of detailactie',
    (tester) async {
      var starts = 0;
      await tester.pumpWidget(
        _panelHarness({
          'status': 'active',
          'workspaceOwnership': 'product-factory',
        }, onStart: () => starts++),
      );

      final button = tester.widget<FilledButton>(
        find.descendant(
          of: find.byType(StartCycleButton),
          matching: find.byType(FilledButton),
        ),
      );
      expect(button.onPressed, isNotNull);
      expect(
        find.byKey(const ValueKey('start-availability-blocked-group')),
        findsNothing,
      );
      expect(find.byType(StartAvailabilityDetailsButton), findsNothing);

      await tester.tap(find.byType(StartCycleButton));
      expect(starts, 1);
    },
  );

  testWidgets(
    'uitgeschakelde actie en beide redenen vormen één semantische groep met één primaire reden',
    (tester) async {
      await tester.pumpWidget(
        _panelHarness({'status': 'paused', 'workspaceOwnership': 'owner'}),
      );
      final semantics = tester.ensureSemantics();

      expect(find.text(kStartAvailabilityInactiveReason), findsOneWidget);
      expect(find.text(kStartAvailabilityAdditionalReason), findsOneWidget);
      expect(find.text(kStartAvailabilityUnknownReason), findsNothing);
      expect(find.text(kStartAvailabilityWorkspaceReason), findsNothing);
      final group = tester
          .getSemantics(
            find.byKey(const ValueKey('start-availability-blocked-group')),
          )
          .getSemanticsData();
      expect(group.flagsCollection.isButton, isTrue);
      expect(group.flagsCollection.isEnabled, Tristate.isFalse);
      expect(group.label, contains('Start productcyclus nu'));
      expect(group.label, contains(kStartAvailabilityInactiveReason));
      expect(group.label, contains(kStartAvailabilityAdditionalReason));
      semantics.dispose();
    },
  );

  testWidgets(
    'detailactie opent met Enter en Spatie; zichtbaar sluiten en Escape herstellen focus',
    (tester) async {
      await tester.pumpWidget(
        _panelHarness({'status': 'paused', 'workspaceOwnership': 'owner'}),
      );

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(
        tester
            .getSemantics(find.byType(StartAvailabilityDetailsButton))
            .flagsCollection
            .isFocused,
        Tristate.isTrue,
      );

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(find.byType(StartAvailabilityDetailsDialog), findsOneWidget);
      await tester.tap(find.widgetWithText(TextButton, 'Sluiten'));
      await tester.pumpAndSettle();
      expect(
        tester
            .getSemantics(find.byType(StartAvailabilityDetailsButton))
            .flagsCollection
            .isFocused,
        Tristate.isTrue,
      );

      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();
      expect(find.byType(StartAvailabilityDetailsDialog), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.byType(StartAvailabilityDetailsDialog), findsNothing);
      expect(
        tester
            .getSemantics(find.byType(StartAvailabilityDetailsButton))
            .flagsCollection
            .isFocused,
        Tristate.isTrue,
      );
    },
  );

  testWidgets(
    'detailweergave is uitsluitend read-only en toont veilige labels, redenen en voorwaarden',
    (tester) async {
      await tester.pumpWidget(
        _panelHarness({
          'slug': 'technische-id-123',
          'name': 'Ander product geheim',
          'status': 'ruwe-status-geheim',
          'workspaceOwnership': {'ruw': 'workspace-geheim'},
          'mission': 'overige-configuratie-geheim',
        }),
      );
      await tester.tap(find.byType(StartAvailabilityDetailsButton));
      await tester.pumpAndSettle();

      final dialog = find.byType(StartAvailabilityDetailsDialog);
      expect(
        find.descendant(of: dialog, matching: find.byType(TextField)),
        findsNothing,
      );
      expect(
        find.descendant(
          of: dialog,
          matching: find.byType(DropdownButton<dynamic>),
        ),
        findsNothing,
      );
      expect(
        find.descendant(of: dialog, matching: find.byType(TextButton)),
        findsOneWidget,
      );
      for (final text in [
        kStartAvailabilityUnknownReason,
        kStartAvailabilityAdditionalReason,
        'Productstatus',
        'Workspacebeheer',
        'Onbekend',
        '• $kProductMustBeActive',
        '• $kWorkspaceMustBeManaged',
      ]) {
        expect(
          find.descendant(of: dialog, matching: find.text(text)),
          findsWidgets,
        );
      }
      final dialogText = tester
          .widgetList<Text>(
            find.descendant(of: dialog, matching: find.byType(Text)),
          )
          .map((text) => text.data)
          .whereType<String>()
          .join(' ');
      for (final forbidden in [
        'technische-id-123',
        'Ander product geheim',
        'ruwe-status-geheim',
        'workspace-geheim',
        'overige-configuratie-geheim',
      ]) {
        expect(dialogText, isNot(contains(forbidden)));
      }
    },
  );

  testWidgets(
    'langlopende RUNNING-cyclus verandert knop, blokkademelding en detailinhoud niet en veroorzaakt geen detailrequest',
    (tester) async {
      final withoutCycle = await _captureDashboard(tester, []);
      SharedPreferences.setMockInitialValues({});
      final withRunningCycle = await _captureDashboard(tester, [
        _longRunningCycle(),
      ]);

      expect(withoutCycle.buttonDisabled, isTrue);
      expect(withRunningCycle.buttonDisabled, isTrue);
      expect(withRunningCycle.blockedSemantics, withoutCycle.blockedSemantics);
      expect(withRunningCycle.dialogTexts, withoutCycle.dialogTexts);
      expect(withRunningCycle.blockedSemantics, isNot(contains('RUNNING')));
      expect(withRunningCycle.blockedSemantics, isNot(contains('loopt')));
    },
  );
}
