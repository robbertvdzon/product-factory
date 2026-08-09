import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/main.dart';

/// Vervangt de echte HTTP-oproepen door vaste data/een vlag, conform de projectconventie dat
/// widgettests géén echte HTTP-calls doen (zie ook iteration_workspace_publication_regression_test.dart).
class _FakeApi extends DashboardApi {
  _FakeApi(this.session) : super('http://example.invalid', null);

  final Map<String, dynamic> session;
  bool cancelled = false;

  @override
  Future<Map<String, dynamic>> shadowIterationSession(
    String productSlug,
    String iterationId,
  ) async => session;

  @override
  Future<void> cancelIteration(
    String productSlug,
    String iterationId, {
    String? reason,
  }) async {
    cancelled = true;
  }
}

Map<String, dynamic> _iterationData(String status) => <String, dynamic>{
  'iteration': {
    'id': 'iter-1',
    'productSlug': 'demo',
    'sequenceNumber': 1,
    'focus': 'Onderzoek reizigersvoorkeuren',
    'mode': 'shadow',
    'status': status,
    'currentRole': status == 'RUNNING' ? 'RESEARCHER' : null,
    'criticVerdict': null,
    'candidateCount': 0,
    'workspaceRunId': null,
    'workspacePullRequestUrl': null,
    'workspaceCommitSha': null,
    'errorMessage': null,
    'summary': null,
    'createdAt': DateTime(2026, 1, 1).toIso8601String(),
    'startedAt': DateTime(2026, 1, 1).toIso8601String(),
    'completedAt': null,
  },
  'steps': <dynamic>[],
  'artifacts': <dynamic>[],
  'dossier': null,
};

Future<void> _openDialog(WidgetTester tester, _FakeApi api) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: Builder(
          builder: (context) => ElevatedButton(
            onPressed: () => showDialog<void>(
              context: context,
              builder: (_) => IterationSessionDialog(
                api: api,
                productSlug: 'demo',
                iterationId: 'iter-1',
              ),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('open'));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

void main() {
  testWidgets('toont geen annuleerknop voor een afgeronde iteratie', (
    tester,
  ) async {
    await _openDialog(tester, _FakeApi(_iterationData('ACCEPTED')));

    expect(find.text('Cyclus annuleren'), findsNothing);

    await tester.tap(find.text('Sluiten'));
    await tester.pump(const Duration(milliseconds: 300));
  });

  testWidgets(
    'annuleerknop op een lopende iteratie vraagt bevestiging en roept de API aan',
    (tester) async {
      final api = _FakeApi(_iterationData('RUNNING'));
      await _openDialog(tester, api);

      expect(find.text('Cyclus annuleren'), findsOneWidget);
      await tester.tap(find.text('Cyclus annuleren'));
      await tester.pump();

      // Bevestigingsdialoog moet eerst expliciet 'ja' krijgen; de API mag nog niet zijn aangeroepen.
      expect(find.text('Cyclus annuleren?'), findsOneWidget);
      expect(api.cancelled, isFalse);

      await tester.tap(find.text('Ja, annuleren'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(api.cancelled, isTrue);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );
}
