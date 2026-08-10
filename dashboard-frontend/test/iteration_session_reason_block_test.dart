import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/main.dart';

/// Vervangt de echte HTTP-oproep van [DashboardApi.shadowIterationSession] door vaste data, zodat
/// het detaildialoog getest kan worden zonder netwerkverkeer (conform de projectconventie dat
/// widgettests géén echte HTTP-calls doen). Zie ook `iteration_session_error_message_test.dart`.
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
  List<dynamic>? artifacts,
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
    'errorMessage': null,
    'summary': null,
    'createdAt': DateTime(2026, 1, 1).toIso8601String(),
    'startedAt': DateTime(2026, 1, 1).toIso8601String(),
    'completedAt': DateTime(2026, 1, 1).toIso8601String(),
  },
  'steps': <dynamic>[],
  'artifacts': artifacts ?? <dynamic>[],
  'dossier': null,
};

Map<String, dynamic> _criticArtifact(String artifactType, String contentJson) =>
    <String, dynamic>{
      'artifactType': artifactType,
      'contentJson': contentJson,
      'createdAt': DateTime(2026, 1, 1).toIso8601String(),
    };

void _growTestWindow(WidgetTester tester) {
  tester.view.physicalSize = const Size(1200, 3000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Future<void> _openDialog(
  WidgetTester tester,
  Map<String, dynamic> session,
) async {
  _growTestWindow(tester);
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
  const rawJsonPattern = r'\{"|":"';

  testWidgets(
    'toont het Reden-blok met leesbare tekst bij status NEEDS_REVISION met criticus-artefact',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'NEEDS_REVISION',
          artifacts: [
            _criticArtifact(
              'critic',
              '{"overallVerdict":"REVISE","summary":"Nog niet compleet.",'
                  '"requiredChanges":["Voeg acceptatiecriteria toe","Verduidelijk scope"]}',
            ),
          ],
        ),
      );

      expect(find.text('Reden'), findsOneWidget);
      final textFinder = find.byWidgetPredicate(
        (widget) =>
            widget is SelectableText &&
            (widget.data ?? '').contains('Eindoordeel: REVISE'),
      );
      expect(textFinder, findsOneWidget);
      final shownText = tester.widget<SelectableText>(textFinder).data!;
      expect(shownText, contains('Nog niet compleet.'));
      expect(shownText, contains('• Voeg acceptatiecriteria toe'));
      expect(shownText, contains('• Verduidelijk scope'));
      expect(RegExp(rawJsonPattern).hasMatch(shownText), isFalse);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'gebruikt het meest recente criticus-artefact (hoogste retry-suffix) bij meerdere pogingen',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'REJECTED',
          artifacts: [
            _criticArtifact(
              'critic',
              '{"overallVerdict":"REVISE","summary":"Eerste poging."}',
            ),
            _criticArtifact(
              'critic-2',
              '{"overallVerdict":"REJECT","summary":"Tweede poging, definitief afgewezen."}',
            ),
          ],
        ),
      );

      expect(find.textContaining('Eindoordeel: REJECT'), findsOneWidget);
      expect(
        find.textContaining('Tweede poging, definitief afgewezen.'),
        findsOneWidget,
      );
      expect(find.textContaining('Eerste poging.'), findsNothing);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont de vaste fallbacktekst als er geen criticus-artefact is bij NEEDS_REVISION',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(status: 'NEEDS_REVISION', artifacts: []),
      );

      expect(find.text('Reden'), findsOneWidget);
      expect(
        find.text('Criticus-oordeel ontbreekt voor deze cyclus'),
        findsOneWidget,
      );

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont de vaste fallbacktekst als er geen criticus-artefact is bij REJECTED',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(status: 'REJECTED', artifacts: []),
      );

      expect(find.text('Reden'), findsOneWidget);
      expect(
        find.text('Criticus-oordeel ontbreekt voor deze cyclus'),
        findsOneWidget,
      );

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  for (final status in ['ACCEPTED', 'PENDING', 'QUEUED', 'RUNNING']) {
    testWidgets('toont geen Reden-blok bij status $status', (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: status,
          artifacts: [
            _criticArtifact(
              'critic',
              '{"overallVerdict":"ACCEPT","summary":"Prima."}',
            ),
          ],
        ),
      );

      expect(find.text('Reden'), findsNothing);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    });
  }

  testWidgets(
    'het Reden-blok heeft een expliciet Semantics-label "Reden: <tekst>"',
    (tester) async {
      final handle = tester.ensureSemantics();

      await _openDialog(
        tester,
        _sessionWith(
          status: 'NEEDS_REVISION',
          artifacts: [
            _criticArtifact(
              'critic',
              '{"overallVerdict":"REVISE","summary":"Nog niet compleet."}',
            ),
          ],
        ),
      );

      final expectedLabel = 'Reden: Eindoordeel: REVISE\nNog niet compleet.';
      final semanticsFinder = find.byWidgetPredicate(
        (widget) =>
            widget is Semantics && widget.properties.label == expectedLabel,
      );
      expect(semanticsFinder, findsOneWidget);

      final label = tester
          .getSemantics(semanticsFinder)
          .getSemanticsData()
          .label;
      expect(label, expectedLabel);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
      handle.dispose();
    },
  );

  testWidgets(
    'regressie: het Foutreden-blok (FAILED) blijft ongewijzigd naast het ontbrekende Reden-blok',
    (tester) async {
      final session = _sessionWith(status: 'FAILED');
      session['iteration']['errorMessage'] =
          'Workspace-publicatie tijdelijk niet beschikbaar.';

      await _openDialog(tester, session);

      expect(find.text('Foutreden'), findsOneWidget);
      expect(
        find.text('Workspace-publicatie tijdelijk niet beschikbaar.'),
        findsOneWidget,
      );
      expect(find.text('Reden'), findsNothing);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'regressie: de criticus-roltegel met volledig artefact blijft ongewijzigd zichtbaar naast het Reden-blok',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'NEEDS_REVISION',
          artifacts: [
            _criticArtifact(
              'critic',
              '{"overallVerdict":"REVISE","summary":"Nog niet compleet.",'
                  '"requiredChanges":["Voeg acceptatiecriteria toe"]}',
            ),
          ],
        ),
      );

      expect(find.text('Reden'), findsOneWidget);
      expect(find.text('Criticus'), findsOneWidget);

      await tester.ensureVisible(find.text('Criticus'));
      await tester.pump();
      await tester.tap(find.text('Criticus'));
      await tester.pumpAndSettle();
      expect(find.text('Eindoordeel'), findsOneWidget);
      expect(find.text('Vereiste wijzigingen'), findsOneWidget);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  test('latestCriticArtifact geeft null bij geen criticus-artefacten', () {
    expect(
      latestCriticArtifact([
        {'artifactType': 'researcher', 'contentJson': '{}'},
      ]),
      isNull,
    );
  });

  test(
    'latestCriticArtifact kiest het artefact met de hoogste retry-suffix',
    () {
      final first = _criticArtifact('critic', '{"summary":"eerste"}');
      final second = _criticArtifact('critic-2', '{"summary":"tweede"}');
      final third = _criticArtifact('critic-3', '{"summary":"derde"}');

      expect(
        latestCriticArtifact([third, first, second]),
        same(third),
        reason: 'hoogste suffix wint, ongeacht positie in de lijst',
      );
    },
  );

  test(
    'latestCriticArtifact valt terug op het laatst voorkomende exemplaar bij gelijke suffix',
    () {
      final first = _criticArtifact('critic', '{"summary":"eerste"}');
      final second = _criticArtifact('critic', '{"summary":"tweede"}');

      expect(latestCriticArtifact([first, second]), same(second));
    },
  );

  test('criticReasonSummary geeft lege string bij niet-parseerbare JSON', () {
    expect(criticReasonSummary('geen json'), isEmpty);
  });

  test(
    'criticReasonSummary bevat geen rauwe JSON-notatie en bevat de kernvelden',
    () {
      final text = criticReasonSummary(
        '{"overallVerdict":"REJECT","summary":"Onvoldoende onderbouwd.",'
        '"requiredChanges":["Voeg bronnen toe"]}',
      );

      expect(text, contains('Eindoordeel: REJECT'));
      expect(text, contains('Onvoldoende onderbouwd.'));
      expect(text, contains('• Voeg bronnen toe'));
      expect(RegExp(r'\{"|":"').hasMatch(text), isFalse);
    },
  );
}
