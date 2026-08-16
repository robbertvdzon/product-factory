import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/classification.dart';
import 'package:product_factory_dashboard/environment_identity.dart';
import 'package:product_factory_dashboard/formatting.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _revision = '0123456789abcdef0123456789abcdef01234567';
const _deployedAt = '2026-08-15T18:04:19Z';

final _identity = EnvironmentIdentityPresentation.fromBuildMetadata(
  environment: 'preview',
  sourceRevision: _revision,
  deployedAt: _deployedAt,
);

http.Response _json(Object body) => http.Response(jsonEncode(body), 200);

Map<String, dynamic> _iteration(String status, int sequence) => {
  'id': 'iteration-$sequence',
  'productSlug': 'demo',
  'sequenceNumber': sequence,
  'status': status,
  'criticVerdict': switch (status) {
    'ACCEPTED' || 'NO_CHANGE' => 'ACCEPT',
    'NEEDS_REVISION' => 'REVISE',
    'REJECTED' => 'REJECT',
    _ => null,
  },
  'outcomeReason': switch (status) {
    'ACCEPTED' => 'ACCEPT',
    'NO_CHANGE' => 'ALREADY_DELIVERED',
    'NEEDS_REVISION' => 'PARTIAL_ACCEPT',
    'REJECTED' => 'REJECT',
    'FAILED' => 'TECHNICAL_FAILURE',
    _ => null,
  },
  'errorMessage': status == 'FAILED' ? 'technische fout' : null,
  'createdAt': '2026-08-15T17:00:00Z',
  'startedAt': '2026-08-15T17:01:00Z',
  // Deze gelijknamige velden mogen de buildidentiteit niet beïnvloeden.
  'environment': 'production',
  'sourceRevision': 'ffffffffffffffffffffffffffffffffffffffff',
  'deployedAt': '2000-01-01T00:00:00Z',
};

MockClient _dashboardClient(List<String> calls) => MockClient((request) async {
  calls.add('${request.method} ${request.url.path}');
  switch (request.url.path) {
    case '/api/products':
      return _json([
        {
          'slug': 'demo',
          'name': 'Demo',
          'status': 'active',
          'workspaceOwnership': 'product-factory',
          'environment': 'production',
          'sourceRevision': 'ffffffffffffffffffffffffffffffffffffffff',
          'deployedAt': '2000-01-01T00:00:00Z',
        },
      ]);
    case '/api/shadow-iterations':
      return _json([
        _iteration('ACCEPTED', 8),
        _iteration('NEEDS_REVISION', 7),
        _iteration('REJECTED', 6),
        _iteration('NO_CHANGE', 5),
        _iteration('FAILED', 4),
        _iteration('RUNNING', 3),
        _iteration('FUTURE_STATUS', 2),
      ]);
    case '/api/ai-catalog':
      return _json(<String, dynamic>{});
    default:
      return _json(<dynamic>[]);
  }
});

Widget _blockHarness({double textScale = 1}) => MaterialApp(
  theme: ThemeData(useMaterial3: true),
  home: MediaQuery(
    data: MediaQueryData(textScaler: TextScaler.linear(textScale)),
    child: Scaffold(
      body: RepaintBoundary(
        key: const ValueKey('environment-identity-golden'),
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: EnvironmentIdentityBlock(identity: _identity),
        ),
      ),
    ),
  ),
);

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets(
    'volledig blok heeft gekoppelde velden in semantische leesvolgorde',
    (tester) async {
      await tester.pumpWidget(_blockHarness());
      final semantics = tester.ensureSemantics();
      final expectedTime = formatDateTime(DateTime.parse(_deployedAt));

      final heading = find.byWidgetPredicate(
        (widget) => widget is Semantics && widget.properties.header == true,
      );
      expect(heading, findsOneWidget);
      expect(find.text('Omgevingsidentiteit'), findsOneWidget);
      final labels = [
        'Omgeving: Preview',
        'Revisie/build-ID: 0123456789ab',
        'Uitgerold op: $expectedTime',
      ];
      for (final label in labels) {
        expect(find.text(label, findRichText: true), findsOneWidget);
        expect(find.bySemanticsLabel(label), findsOneWidget);
      }
      final topPositions = labels
          .map(
            (label) =>
                tester.getTopLeft(find.text(label, findRichText: true)).dy,
          )
          .toList();
      expect(topPositions, orderedEquals([...topPositions]..sort()));
      expect(find.byType(IconButton), findsNothing);
      expect(find.byType(TextButton), findsNothing);
      expect(find.byType(OutlinedButton), findsNothing);
      expect(
        contrastRatio(kCycleCardSecondaryText, kCycleCardBackground),
        greaterThanOrEqualTo(4.5),
      );
      semantics.dispose();
    },
  );

  testWidgets(
    'dashboard hergebruikt exact dezelfde identiteit zonder extra requests',
    (tester) async {
      tester.view.physicalSize = const Size(1200, 5000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final calls = <String>[];
      await http.runWithClient(() async {
        await tester.pumpWidget(
          MaterialApp(
            home: OverviewPage(session: null, environmentIdentity: _identity),
          ),
        );
        for (var pump = 0; pump < 5; pump++) {
          await tester.pump();
        }
        await tester.tap(find.text('Productsessies'));
        await tester.pump();

        final terminalRows = find.byType(IterationEvidenceRow);
        expect(terminalRows, findsNWidgets(5));
        expect(find.byType(EnvironmentIdentityReference), findsNWidgets(5));
        await tester.tap(find.text('Meer (nog 2)'));
        await tester.pump();
        expect(find.byType(IterationProgressCard), findsNWidgets(2));
        for (final reference
            in find.byType(EnvironmentIdentityReference).evaluate()) {
          final scope = find.byElementPredicate(
            (element) => element == reference,
          );
          expect(
            find.descendant(
              of: scope,
              matching: find.textContaining('Omgeving: Preview'),
            ),
            findsOneWidget,
          );
          expect(
            find.descendant(
              of: scope,
              matching: find.textContaining('Revisie/build-ID: 0123456789ab'),
            ),
            findsOneWidget,
          );
          expect(
            find.descendant(
              of: scope,
              matching: find.textContaining('Uitgerold op'),
            ),
            findsNothing,
          );
          expect(
            find.descendant(of: scope, matching: find.byType(GestureDetector)),
            findsNothing,
          );
        }
        for (final progressCard
            in find.byType(IterationProgressCard).evaluate()) {
          final scope = find.byElementPredicate(
            (element) => element == progressCard,
          );
          expect(
            find.descendant(
              of: scope,
              matching: find.byType(EnvironmentIdentityReference),
            ),
            findsNothing,
          );
        }

        final callsBeforeManagement = List<String>.of(calls);
        await tester.tap(find.text('Beheer'));
        await tester.pump();
        expect(calls, callsBeforeManagement);
        expect(find.byType(EnvironmentIdentityBlock), findsOneWidget);
        expect(find.bySemanticsLabel('Omgeving: Preview'), findsOneWidget);
        expect(
          find.bySemanticsLabel('Revisie/build-ID: 0123456789ab'),
          findsOneWidget,
        );
        expect(
          find.bySemanticsLabel(
            'Uitgerold op: ${formatDateTime(DateTime.parse(_deployedAt))}',
          ),
          findsOneWidget,
        );
        for (final forbidden in ['ffffffffffff', 'Productie', '01-01-2000']) {
          expect(find.textContaining(forbidden), findsNothing);
        }
        expect(calls.map((call) => call.split(' ').first), everyElement('GET'));
      }, () => _dashboardClient(calls));

      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'ongeldige gevoelige metadata bereikt ook de semanticsboom niet',
    (tester) async {
      const sentinels = [
        'feat: intern commitbericht',
        'Auteur Achternaam',
        'auteur@example.invalid',
        'ghp_TOKENACHTIGEWAARDE',
        'SECRET_CONFIG=verborgen',
        'https://git.internal.invalid/organisatie/repository',
      ];
      for (final sentinel in sentinels) {
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: EnvironmentIdentityBlock(
                identity: EnvironmentIdentityPresentation.fromBuildMetadata(
                  environment: sentinel,
                  sourceRevision: sentinel,
                  deployedAt: sentinel,
                ),
              ),
            ),
          ),
        );
        final semantics = tester.ensureSemantics();
        expect(find.textContaining(sentinel), findsNothing);
        expect(
          tester
              .getSemantics(find.byType(EnvironmentIdentityBlock))
              .toStringDeep(),
          isNot(contains(sentinel)),
        );
        expect(find.textContaining('Onbekend'), findsNWidgets(3));
        semantics.dispose();
      }
    },
  );

  for (final fixture in [
    (name: 'breed', size: const Size(900, 400), textScale: 1.0),
    (name: 'smal-200-procent', size: const Size(320, 700), textScale: 2.0),
  ]) {
    testWidgets(
      '${fixture.name} heeft geen overflow en blijft visueel stabiel',
      (tester) async {
        tester.view.physicalSize = fixture.size;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        await tester.pumpWidget(_blockHarness(textScale: fixture.textScale));
        await tester.pump();

        expect(tester.takeException(), isNull);
        final rect = tester.getRect(find.byType(EnvironmentIdentityBlock));
        expect(rect.left, greaterThanOrEqualTo(0));
        expect(rect.right, lessThanOrEqualTo(fixture.size.width));
        await expectLater(
          find.byKey(const ValueKey('environment-identity-golden')),
          matchesGoldenFile('goldens/environment_identity_${fixture.name}.png'),
        );
      },
    );
  }
}
