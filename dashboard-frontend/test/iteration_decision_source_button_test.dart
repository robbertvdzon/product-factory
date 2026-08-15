import 'dart:convert';
import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/classification.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

Map<String, dynamic> _iteration({
  required String id,
  required int sequenceNumber,
  required String errorMessage,
  required DateTime startedAt,
  Map<String, dynamic>? decision,
}) => {
  'id': id,
  'productSlug': 'demo',
  'sequenceNumber': sequenceNumber,
  'focus': 'Unieke opdracht voor cyclus $sequenceNumber',
  'mode': 'autonomous',
  'status': 'FAILED',
  'currentRole': null,
  'criticVerdict': null,
  'candidateCount': 0,
  'workspaceRunId': null,
  'workspacePullRequestUrl': null,
  'workspaceCommitSha': null,
  'errorMessage': errorMessage,
  'outcomeReason': 'TECHNICAL_FAILURE',
  'summary': null,
  'prompt': 'Ruwe prompt voor cyclus $sequenceNumber',
  'logs': 'Ruwe logs voor cyclus $sequenceNumber',
  'artifactContent': 'Ruwe artefactinhoud voor cyclus $sequenceNumber',
  'createdAt': startedAt.toIso8601String(),
  'startedAt': startedAt.toIso8601String(),
  'completedAt': startedAt.add(const Duration(minutes: 1)).toIso8601String(),
  if (decision != null) 'decision': decision,
};

final _iterations = <Map<String, dynamic>>[
  _iteration(
    id: 'iter-34',
    sequenceNumber: 34,
    errorMessage: 'Synthetische foutreden uitsluitend voor cyclus 34',
    startedAt: DateTime.utc(2026, 8, 12, 11),
    decision: {
      'iterationId': 'iter-34',
      'actorType': 'HUMAN',
      'mechanism': 'MANUAL_CANCELLATION',
      'reasonCode': 'MANUALLY_CANCELLED',
      'decidedAt': DateTime.utc(2026, 8, 12, 11, 1).toIso8601String(),
    },
  ),
  _iteration(
    id: 'iter-12',
    sequenceNumber: 12,
    errorMessage: 'Synthetische foutreden uitsluitend voor cyclus 12',
    startedAt: DateTime.utc(2026, 8, 12, 10),
  ),
];

MockClient _buildMockClient(List<Map<String, String>> callLog) {
  return MockClient((request) async {
    callLog.add({'method': request.method, 'path': request.url.path});
    final detailMatch = RegExp(
      r'^/api/shadow-iterations/(iter-(?:12|34))$',
    ).firstMatch(request.url.path);
    if (detailMatch != null) {
      final id = detailMatch.group(1)!;
      return http.Response(
        jsonEncode(
          _iterations.singleWhere((iteration) => iteration['id'] == id),
        ),
        200,
      );
    }
    if (RegExp(
      r'^/api/shadow-iterations/iter-(?:12|34)/(?:steps|artifacts)$',
    ).hasMatch(request.url.path)) {
      return http.Response(jsonEncode(<dynamic>[]), 200);
    }
    switch (request.url.path) {
      case '/api/products':
        return http.Response(
          jsonEncode([
            {'slug': 'demo', 'name': 'Demo'},
          ]),
          200,
        );
      case '/api/shadow-iterations':
        return http.Response(jsonEncode(_iterations), 200);
      case '/api/ai-catalog':
        return http.Response(jsonEncode(<String, dynamic>{}), 200);
      default:
        return http.Response(jsonEncode(<dynamic>[]), 200);
    }
  });
}

Future<void> _withDashboard(
  WidgetTester tester,
  List<Map<String, String>> callLog,
  Future<void> Function() body,
) async {
  tester.view.physicalSize = const Size(1200, 3000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  final mockClient = _buildMockClient(callLog);
  await http.runWithClient(() async {
    await tester.pumpWidget(const ProductFactoryDashboard());
    await tester.pump();
    await tester.pump();
    await body();
  }, () => mockClient);
}

Finder _detailButton(String id) =>
    find.byKey(ValueKey('iteration-evidence-$id'));

Finder _iterationRow(String id) => find.ancestor(
  of: _detailButton(id),
  matching: find.byType(IterationEvidenceRow),
);

Future<void> _finishDialogTransition(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets(
    'iedere terminale cyclus toont één native detailactie zonder ruwe inhoud',
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, callLog, () async {
        final semantics = tester.ensureSemantics();
        expect(find.byType(IterationEvidenceRow), findsNWidgets(2));
        expect(find.byType(IterationEvidenceButton), findsNWidgets(2));
        expect(find.byType(IterationDecisionSourceButton), findsNothing);
        expect(
          find.text('Beslisbron: Mens', findRichText: true),
          findsOneWidget,
        );
        expect(
          find.text('Reden: Handmatig geannuleerd', findRichText: true),
          findsOneWidget,
        );
        expect(
          find.text(
            'Beslisbron: Technische fout (Afgeleid)',
            findRichText: true,
          ),
          findsOneWidget,
        );
        final explicitRow = _iterationRow('iter-34');
        expect(
          find.descendant(
            of: explicitRow,
            matching: find.byType(ClassificationBadge),
          ),
          findsNothing,
        );
        expect(
          find.descendant(
            of: explicitRow,
            matching: find.textContaining(
              'De cyclus is door een technische fout gestopt',
            ),
          ),
          findsNothing,
        );

        final historicalRow = _iterationRow('iter-12');
        expect(
          find.descendant(
            of: historicalRow,
            matching: find.text(
              'Cyclusuitkomst: technische fout',
              findRichText: true,
            ),
          ),
          findsOneWidget,
        );
        expect(
          find.descendant(
            of: historicalRow,
            matching: find.textContaining(
              'De cyclus is door een technische fout gestopt',
            ),
          ),
          findsOneWidget,
        );

        for (final iteration in _iterations) {
          final button = _detailButton('${iteration['id']}');
          expect(button, findsOneWidget);
          expect(
            find.descendant(of: button, matching: find.byType(OutlinedButton)),
            findsOneWidget,
          );

          expect(
            find.descendant(
              of: _iterationRow('${iteration['id']}'),
              matching: find.byType(OutlinedButton),
            ),
            findsOneWidget,
          );
          expect(
            find.descendant(
              of: _iterationRow('${iteration['id']}'),
              matching: find.byIcon(Icons.chevron_right),
            ),
            findsNothing,
          );
          expect(
            find.ancestor(of: button, matching: find.byType(ButtonStyleButton)),
            findsNothing,
          );
        }
        expect(
          tester
              .getSemantics(
                find.descendant(
                  of: _detailButton('iter-34'),
                  matching: find.byType(OutlinedButton),
                ),
              )
              .getSemanticsData()
              .label,
          contains('uitkomst Handmatig geannuleerd'),
        );
        expect(
          tester
              .getSemantics(
                find.descendant(
                  of: _detailButton('iter-12'),
                  matching: find.byType(OutlinedButton),
                ),
              )
              .getSemanticsData()
              .label,
          contains('uitkomst technische fout'),
        );

        for (final forbiddenText in [
          'Synthetische foutreden uitsluitend voor cyclus 12',
          'Synthetische foutreden uitsluitend voor cyclus 34',
          'Ruwe prompt voor cyclus 12',
          'Ruwe logs voor cyclus 12',
          'Ruwe artefactinhoud voor cyclus 12',
        ]) {
          expect(find.text(forbiddenText), findsNothing);
        }
        semantics.dispose();
      });
    },
  );

  testWidgets(
    'expliciete annulering toont in detail dezelfde bron en reden plus mechanisme en tijd',
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, callLog, () async {
        await tester.tap(
          find.descendant(
            of: _detailButton('iter-34'),
            matching: find.byType(OutlinedButton),
          ),
        );
        await _finishDialogTransition(tester);

        expect(find.text('Productcyclus 34'), findsOneWidget);
        expect(
          find.text('Beslisbron: Mens', findRichText: true),
          findsNWidgets(2),
        );
        expect(
          find.text('Reden: Handmatig geannuleerd', findRichText: true),
          findsNWidgets(2),
        );
        expect(find.text('Mechanisme: Handmatige annulering'), findsOneWidget);
        expect(find.text('Beslist op: 12-08-2026 11:01'), findsOneWidget);
        expect(
          find.descendant(
            of: find.byType(AlertDialog),
            matching: find.byType(ClassificationBadge),
          ),
          findsNothing,
        );
        expect(
          find.descendant(
            of: find.byType(AlertDialog),
            matching: find.text(
              'De cyclus is door een technische fout gestopt',
            ),
          ),
          findsNothing,
        );
      });
    },
  );

  testWidgets(
    'klik opent uitsluitend de gekozen cyclus en sluiten herstelt focus; Enter, Spatie en Escape werken',
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, callLog, () async {
        final button = _detailButton('iter-12');
        final nativeButton = find.descendant(
          of: button,
          matching: find.byType(OutlinedButton),
        );

        await tester.tap(nativeButton);
        await _finishDialogTransition(tester);

        expect(find.text('Productcyclus 12'), findsOneWidget);
        expect(
          find.text('Synthetische foutreden uitsluitend voor cyclus 12'),
          findsOneWidget,
        );
        expect(find.text('Productcyclus 34'), findsNothing);
        expect(
          find.text('Synthetische foutreden uitsluitend voor cyclus 34'),
          findsNothing,
        );
        expect(
          tester
              .widget<ClassificationBadge>(
                find.descendant(
                  of: find.byType(AlertDialog),
                  matching: find.byType(ClassificationBadge),
                ),
              )
              .classification,
          kTechnischeFout,
        );
        expect(
          find.descendant(
            of: find.byType(AlertDialog),
            matching: find.text(
              'De cyclus is door een technische fout gestopt',
            ),
          ),
          findsOneWidget,
        );

        await tester.tap(find.text('Sluiten'));
        await _finishDialogTransition(tester);
        expect(
          tester.getSemantics(nativeButton).flagsCollection.isFocused,
          Tristate.isTrue,
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await _finishDialogTransition(tester);
        expect(find.text('Productcyclus 12'), findsOneWidget);

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await _finishDialogTransition(tester);
        expect(find.text('Productcyclus 12'), findsNothing);
        expect(
          tester.getSemantics(nativeButton).flagsCollection.isFocused,
          Tristate.isTrue,
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.space);
        await _finishDialogTransition(tester);
        expect(find.text('Productcyclus 12'), findsOneWidget);
        await tester.tap(find.text('Sluiten'));
        await _finishDialogTransition(tester);

        expect(
          callLog.where(
            (call) =>
                call['path'] == '/api/shadow-iterations/iter-12' ||
                call['path'] == '/api/shadow-iterations/iter-12/steps' ||
                call['path'] == '/api/shadow-iterations/iter-12/artifacts',
          ),
          hasLength(9),
        );
        expect(
          callLog.where((call) => call['path']?.contains('iter-34') ?? false),
          isEmpty,
        );
        expect(callLog.every((call) => call['method'] == 'GET'), isTrue);
      });
    },
  );
}
