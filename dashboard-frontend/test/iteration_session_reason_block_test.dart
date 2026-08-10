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
  String? criticVerdict,
  List<dynamic>? steps,
}) => <String, dynamic>{
  'iteration': {
    'id': 'iter-1',
    'productSlug': 'demo',
    'sequenceNumber': 1,
    'focus': 'Onderzoek reizigersvoorkeuren',
    'mode': 'autonomous',
    'status': status,
    'currentRole': null,
    'criticVerdict': criticVerdict,
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
  'steps': steps ?? <dynamic>[],
  'artifacts': artifacts ?? <dynamic>[],
  'dossier': null,
};

Map<String, dynamic> _criticArtifact(String artifactType, String contentJson) =>
    <String, dynamic>{
      'artifactType': artifactType,
      'contentJson': contentJson,
      'createdAt': DateTime(2026, 1, 1).toIso8601String(),
    };

Map<String, dynamic> _step({
  required String role,
  required String status,
  int attempt = 1,
  String? completedAt,
  String? errorMessage,
}) => <String, dynamic>{
  'role': role,
  'status': status,
  'attempt': attempt,
  'startedAt': DateTime(2026, 1, 1).toIso8601String(),
  'completedAt': completedAt,
  'errorMessage': errorMessage,
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
    'toont de guardrail-toelichtingszin bij REJECTED met criticVerdict ACCEPT',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'REJECTED',
          criticVerdict: 'ACCEPT',
          artifacts: [
            _criticArtifact(
              'critic',
              '{"overallVerdict":"ACCEPT","summary":"Prima onderbouwd."}',
            ),
          ],
        ),
      );

      expect(find.text('Reden'), findsOneWidget);
      expect(
        find.textContaining(
          'Let op: Alle voorgestelde kandidaten zijn geblokkeerd '
          '(duplicaat of guardrail), waardoor deze cyclus niet doorgaat '
          'ondanks een positief criticusoordeel.',
        ),
        findsOneWidget,
      );

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont geen guardrail-toelichtingszin bij regulier REJECTED (criticVerdict != ACCEPT)',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'REJECTED',
          criticVerdict: 'REJECT',
          artifacts: [
            _criticArtifact(
              'critic',
              '{"overallVerdict":"REJECT","summary":"Onvoldoende onderbouwd."}',
            ),
          ],
        ),
      );

      expect(find.text('Reden'), findsOneWidget);
      expect(find.textContaining('Let op:'), findsNothing);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont een aparte "geen rol voltooid"-fallbacktekst bij NEEDS_REVISION zonder criticVerdict, '
    'zonder criticus-artefact en zonder enige voltooide rol',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(status: 'NEEDS_REVISION', artifacts: [], steps: []),
      );

      expect(find.text('Reden'), findsOneWidget);
      expect(
        find.text('Criticus-oordeel ontbreekt voor deze cyclus'),
        findsNothing,
      );
      final textFinder = find.byWidgetPredicate(
        (widget) =>
            widget is SelectableText &&
            (widget.data ?? '').isNotEmpty &&
            widget.data != 'undefined',
      );
      expect(textFinder, findsWidgets);
      final shownText = tester.widget<SelectableText>(textFinder.first).data!;
      expect(shownText, isNot('Criticus-oordeel ontbreekt voor deze cyclus'));
      expect(shownText, isNot('undefined'));
      expect(shownText, isNotEmpty);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont rolnaam + resultaatsamenvatting bij NEEDS_REVISION zonder criticVerdict, zonder '
    'criticus-artefact, met Onderzoeker als laatst voltooide rol',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'NEEDS_REVISION',
          artifacts: [
            <String, dynamic>{
              'artifactType': 'researcher',
              'contentJson':
                  '{"summary":"Reizigers willen snellere check-in."}',
              'createdAt': DateTime(2026, 1, 1).toIso8601String(),
            },
          ],
          steps: [
            _step(
              role: 'researcher',
              status: 'COMPLETED',
              completedAt: DateTime(2026, 1, 1, 10).toIso8601String(),
            ),
          ],
        ),
      );

      expect(find.text('Reden'), findsOneWidget);
      final textFinder = find.byWidgetPredicate(
        (widget) =>
            widget is SelectableText &&
            (widget.data ?? '').contains('Onderzoeker'),
      );
      expect(textFinder, findsOneWidget);
      final shownText = tester.widget<SelectableText>(textFinder).data!;
      expect(shownText, isNot('Criticus-oordeel ontbreekt voor deze cyclus'));
      expect(shownText, contains('Reizigers willen snellere check-in.'));
      expect(RegExp(rawJsonPattern).hasMatch(shownText), isFalse);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'toont een leesbare samenvatting zonder rauwe JSON voor een rol zonder summary-veld '
    '(product_owner) bij NEEDS_REVISION zonder criticVerdict en zonder criticus-artefact',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'NEEDS_REVISION',
          artifacts: [
            <String, dynamic>{
              'artifactType': 'product_owner',
              'contentJson':
                  '{"productDirection":"Focus op zakelijke reizigers.",'
                  '"rationale":"Grootste betalingsbereidheid."}',
              'createdAt': DateTime(2026, 1, 1).toIso8601String(),
            },
          ],
          steps: [
            _step(
              role: 'product_owner',
              status: 'COMPLETED',
              completedAt: DateTime(2026, 1, 1, 10).toIso8601String(),
            ),
          ],
        ),
      );

      expect(find.text('Reden'), findsOneWidget);
      final textFinder = find.byWidgetPredicate(
        (widget) =>
            widget is SelectableText &&
            (widget.data ?? '').contains('Product owner'),
      );
      expect(textFinder, findsOneWidget);
      final shownText = tester.widget<SelectableText>(textFinder).data!;
      expect(shownText, contains('Focus op zakelijke reizigers.'));
      expect(shownText, contains('Grootste betalingsbereidheid.'));
      expect(RegExp(rawJsonPattern).hasMatch(shownText), isFalse);

      await tester.tap(find.text('Sluiten'));
      await tester.pump(const Duration(milliseconds: 300));
    },
  );

  testWidgets(
    'regressie: NEEDS_REVISION mét criticVerdict maar zonder criticus-artefact behoudt de '
    'bestaande fallbacktekst, ook met een voltooide rol aanwezig',
    (tester) async {
      await _openDialog(
        tester,
        _sessionWith(
          status: 'NEEDS_REVISION',
          criticVerdict: 'REVISE',
          artifacts: [],
          steps: [
            _step(
              role: 'researcher',
              status: 'COMPLETED',
              completedAt: DateTime(2026, 1, 1, 10).toIso8601String(),
            ),
          ],
        ),
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

  test('latestArtifactForRole geeft null zonder artefact voor die rol', () {
    expect(
      latestArtifactForRole([
        {'artifactType': 'critic', 'contentJson': '{}'},
      ], 'researcher'),
      isNull,
    );
  });

  test(
    'latestArtifactForRole kiest het artefact met de hoogste retry-suffix',
    () {
      final first = <String, dynamic>{
        'artifactType': 'story_writer',
        'contentJson': '{"candidates":[]}',
      };
      final second = <String, dynamic>{
        'artifactType': 'story_writer-2',
        'contentJson': '{"candidates":[]}',
      };
      expect(
        latestArtifactForRole([first, second], 'story_writer'),
        same(second),
      );
    },
  );

  test('lastCompletedStep geeft null als geen enkele stap COMPLETED is', () {
    expect(
      lastCompletedStep([
        _step(role: 'researcher', status: 'RUNNING'),
        _step(role: 'product_owner', status: 'FAILED'),
      ]),
      isNull,
    );
  });

  test(
    'lastCompletedStep kiest de COMPLETED-stap met de recentste completedAt',
    () {
      final earlier = _step(
        role: 'researcher',
        status: 'COMPLETED',
        completedAt: DateTime(2026, 1, 1, 9).toIso8601String(),
      );
      final later = _step(
        role: 'product_owner',
        status: 'COMPLETED',
        completedAt: DateTime(2026, 1, 1, 11).toIso8601String(),
      );
      expect(
        lastCompletedStep([later, earlier]),
        same(later),
        reason: 'recentste completedAt wint, ongeacht positie in de lijst',
      );
    },
  );

  test(
    'lastCompletedStep valt terug op het laatst voorkomende exemplaar bij gelijke/ontbrekende completedAt',
    () {
      final first = _step(role: 'researcher', status: 'COMPLETED');
      final second = _step(role: 'product_owner', status: 'COMPLETED');
      expect(lastCompletedStep([first, second]), same(second));
    },
  );

  test(
    'roleResultSummaryText gebruikt het summary-veld voor researcher/critic/summary',
    () {
      expect(
        roleResultSummaryText(
          'researcher',
          '{"summary":"Reizigers willen snellere check-in."}',
        ),
        'Reizigers willen snellere check-in.',
      );
    },
  );

  test(
    'roleResultSummaryText bouwt een leesbare samenvatting zonder rauwe JSON voor product_owner',
    () {
      final text = roleResultSummaryText(
        'product_owner',
        '{"productDirection":"Focus op zakelijke reizigers.",'
            '"rationale":"Grootste betalingsbereidheid.",'
            '"priorities":["Snellere check-in","Loyaliteitsprogramma"]}',
      );

      expect(text, contains('Focus op zakelijke reizigers.'));
      expect(text, contains('Grootste betalingsbereidheid.'));
      expect(text, contains('Snellere check-in'));
      expect(RegExp(r'\{"|":"').hasMatch(text), isFalse);
    },
  );

  test('roleResultSummaryText geeft lege string bij niet-parseerbare JSON', () {
    expect(roleResultSummaryText('researcher', 'geen json'), isEmpty);
  });

  test('roleResultSummaryText geeft lege string voor een onbekende rol', () {
    expect(
      roleResultSummaryText('onbekende_rol', '{"summary":"iets"}'),
      isEmpty,
    );
  });

  test(
    'missingCriticReasonText geeft de "geen rol voltooid"-fallback zonder COMPLETED-stap',
    () {
      final text = missingCriticReasonText([], []);
      expect(text, isNotEmpty);
      expect(text, isNot('undefined'));
      expect(text, isNot('Criticus-oordeel ontbreekt voor deze cyclus'));
    },
  );

  test(
    'missingCriticReasonText bevat rolnaam en samenvatting bij een COMPLETED-stap',
    () {
      final steps = [
        _step(
          role: 'researcher',
          status: 'COMPLETED',
          completedAt: DateTime(2026, 1, 1, 10).toIso8601String(),
        ),
      ];
      final artifacts = [
        <String, dynamic>{
          'artifactType': 'researcher',
          'contentJson': '{"summary":"Reizigers willen snellere check-in."}',
        },
      ];

      final text = missingCriticReasonText(steps, artifacts);

      expect(text, contains('Onderzoeker'));
      expect(text, contains('Reizigers willen snellere check-in.'));
      expect(RegExp(r'\{"|":"').hasMatch(text), isFalse);
    },
  );
}
