import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/main.dart';

/// Vervangt de echte HTTP-oproep van [DashboardApi.shadowIterationSession] door vaste data, zodat
/// het detaildialoog getest kan worden zonder netwerkverkeer (conform de projectconventie dat
/// widgettests géén echte HTTP-calls doen). Zie ook `iteration_workspace_publication_regression_test.dart`.
class _FakeApi extends DashboardApi {
  _FakeApi(this.session) : super('http://example.invalid', null);

  final Map<String, dynamic> session;

  @override
  Future<Map<String, dynamic>> shadowIterationSession(
    String productSlug,
    String iterationId,
  ) async => session;
}

Map<String, dynamic> _sessionWith({
  required String status,
  dynamic errorMessage,
}) => <String, dynamic>{
  'iteration': {
    'id': 'iter-1',
    'productSlug': 'demo',
    'sequenceNumber': 1,
    'focus': 'Onderzoek reizigersvoorkeuren',
    'mode': 'autonomous',
    'status': status,
    'currentRole': null,
    'criticVerdict': null,
    'candidateCount': 2,
    'workspaceRunId': null,
    'workspacePullRequestUrl': null,
    'workspaceCommitSha': null,
    'errorMessage': errorMessage,
    'summary': null,
    'createdAt': DateTime(2026, 1, 1).toIso8601String(),
    'startedAt': DateTime(2026, 1, 1).toIso8601String(),
    'completedAt': DateTime(2026, 1, 1).toIso8601String(),
  },
  'steps': <dynamic>[],
  'artifacts': <dynamic>[],
  'dossier': null,
};

Future<void> _openDialog(
  WidgetTester tester,
  Map<String, dynamic> session,
) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: Builder(
          builder: (context) => ElevatedButton(
            onPressed: () => showDialog<void>(
              context: context,
              builder: (_) => IterationSessionDialog(
                api: _FakeApi(session),
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
  testWidgets(
    'toont het Foutreden-blok met de exacte foutmelding bij status FAILED',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'FAILED',
          errorMessage: 'Workspace-publicatie tijdelijk niet beschikbaar.',
        ),
      );

      expect(find.text('Foutreden'), findsOneWidget);
      expect(
        find.text('Workspace-publicatie tijdelijk niet beschikbaar.'),
        findsOneWidget,
      );

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont "Geen foutreden beschikbaar" bij status FAILED zonder errorMessage',
    (tester) async {
      await _openDialog(tester, _sessionWith(status: 'FAILED'));

      expect(find.text('Foutreden'), findsOneWidget);
      expect(find.text('Geen foutreden beschikbaar'), findsOneWidget);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont "Geen foutreden beschikbaar" bij status FAILED met een leeg (whitespace) errorMessage',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(status: 'FAILED', errorMessage: '   '),
      );

      expect(find.text('Foutreden'), findsOneWidget);
      expect(find.text('Geen foutreden beschikbaar'), findsOneWidget);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont geen Foutreden-blok bij een niet-FAILED status, ook niet met een gevulde errorMessage',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'ACCEPTED',
          errorMessage: 'Deze melding hoort niet getoond te worden.',
        ),
      );

      expect(find.text('Foutreden'), findsNothing);
      expect(
        find.text('Deze melding hoort niet getoond te worden.'),
        findsNothing,
      );

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont geen Foutreden-blok bij status NEEDS_REVISION met een gevulde errorMessage',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'NEEDS_REVISION',
          errorMessage: 'Deze melding hoort niet getoond te worden.',
        ),
      );

      expect(find.text('Foutreden'), findsNothing);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'het Foutreden-blok heeft een expliciet Semantics-label "Foutreden: <tekst>"',
    (tester) async {
      final handle = tester.ensureSemantics();

      await _openDialog(
        tester,
        _sessionWith(
          status: 'FAILED',
          errorMessage: 'Workspace-publicatie tijdelijk niet beschikbaar.',
        ),
      );

      final semanticsFinder = find.byWidgetPredicate(
        (widget) =>
            widget is Semantics &&
            widget.properties.label ==
                'Foutreden: Workspace-publicatie tijdelijk niet beschikbaar.',
      );
      expect(semanticsFinder, findsOneWidget);

      final label = tester
          .getSemantics(semanticsFinder)
          .getSemanticsData()
          .label;
      expect(
        label,
        'Foutreden: Workspace-publicatie tijdelijk niet beschikbaar.',
      );

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
      handle.dispose();
    },
  );

  test(
    'contract: /api/shadow-iterations(/{id}) response-velden en -types blijven ongewijzigd',
    () async {
      // Bewaakt dat DashboardApi.shadowIterationSession de door de backend geleverde velden en
      // types ongewijzigd doorgeeft; deze story wijzigt uitsluitend presentatielogica in
      // IterationSessionDialog en raakt /api/shadow-iterations, de DTO's of classification.dart niet aan.
      final iterationJson = <String, dynamic>{
        'id': 'iter-1',
        'productSlug': 'demo',
        'sequenceNumber': 1,
        'focus': 'Onderzoek reizigersvoorkeuren',
        'mode': 'autonomous',
        'status': 'FAILED',
        'currentRole': null,
        'criticVerdict': null,
        'candidateCount': 2,
        'workspaceRunId': null,
        'workspacePullRequestUrl': null,
        'workspaceCommitSha': null,
        'errorMessage': 'Workspace-publicatie tijdelijk niet beschikbaar.',
        'summary': null,
        'createdAt': DateTime(2026, 1, 1).toIso8601String(),
        'startedAt': DateTime(2026, 1, 1).toIso8601String(),
        'completedAt': DateTime(2026, 1, 1).toIso8601String(),
      };
      final stepsJson = <dynamic>[];
      final artifactsJson = <dynamic>[];

      final mockClient = MockClient((request) async {
        final path = request.url.path;
        if (path == '/api/shadow-iterations/iter-1') {
          return http.Response(jsonEncode(iterationJson), 200);
        }
        if (path == '/api/shadow-iterations/iter-1/steps') {
          return http.Response(jsonEncode(stepsJson), 200);
        }
        if (path == '/api/shadow-iterations/iter-1/artifacts') {
          return http.Response(jsonEncode(artifactsJson), 200);
        }
        return http.Response('niet gevonden', 404);
      });

      final result = await http.runWithClient(() async {
        final api = const DashboardApi('http://example.invalid', null);
        return api.shadowIterationSession('demo', 'iter-1');
      }, () => mockClient);

      expect(result.keys.toSet(), {
        'iteration',
        'steps',
        'artifacts',
        'dossier',
      });
      final iteration = result['iteration'] as Map<String, dynamic>;
      expect(iteration.keys.toSet(), iterationJson.keys.toSet());
      for (final key in iterationJson.keys) {
        expect(
          iteration[key],
          iterationJson[key],
          reason: 'veld "$key" moet ongewijzigd doorgegeven worden',
        );
      }
      expect(result['steps'], isA<List<dynamic>>());
      expect(result['artifacts'], isA<List<dynamic>>());
    },
  );
}
