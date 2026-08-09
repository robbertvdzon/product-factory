import 'dart:convert';
import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/main.dart';

/// Tests voor product-85: de ruwe-JSON-weergave per agentrol-artefact staat, wanneer er leesbare
/// tekst is (`readableFields.isNotEmpty`), standaard ingeklapte achter een 'Toon technische
/// details'-toggle (`TechnicalDetailsToggle`). Vervangt de echte HTTP-oproep van
/// [DashboardApi.shadowIterationSession] door vaste data, conform het patroon in
/// `iteration_readable_artifact_fields_test.dart`.
class _FakeApi extends DashboardApi {
  _FakeApi(this.session) : super('http://example.invalid', null);

  final Map<String, dynamic> session;

  @override
  Future<Map<String, dynamic>> shadowIterationSession(
    String productSlug,
    String iterationId,
  ) async => session;
}

Map<String, dynamic> _sessionWith({List<dynamic>? artifacts}) =>
    <String, dynamic>{
      'iteration': {
        'id': 'iter-1',
        'productSlug': 'demo',
        'sequenceNumber': 1,
        'focus': 'Onderzoek reizigersvoorkeuren',
        'mode': 'autonomous',
        'status': 'ACCEPTED',
        'currentRole': null,
        'criticVerdict': 'ACCEPT',
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

Map<String, dynamic> _readableArtifact() => {
  'artifactType': 'researcher',
  'createdAt': DateTime(2026, 1, 1).toIso8601String(),
  'contentJson': jsonEncode({
    'summary': 'Reizigers willen sneller inzicht in vertragingen.',
    'findings': [
      {
        'title': 'Vertraging is de grootste ergernis',
        'finding':
            'Gebruikers noemen onvoorspelbare vertragingen als hoofdklacht.',
        'sourceUrls': ['https://example.invalid/onderzoek'],
      },
    ],
  }),
};

Map<String, dynamic> _fallbackArtifact() => {
  'artifactType': 'onbekende_rol',
  'createdAt': DateTime(2026, 1, 1).toIso8601String(),
  'contentJson': jsonEncode({'someWeirdField': 'iets'}),
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

Future<void> _expandArtifactTile(WidgetTester tester, String roleLabel) async {
  await tester.tap(find.text(roleLabel));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'raw JSON staat standaard ingeklapt achter de toggle als er leesbare tekst is (AC1)',
    (tester) async {
      _growTestWindow(tester);
      await _openDialog(tester, _sessionWith(artifacts: [_readableArtifact()]));
      await _expandArtifactTile(tester, 'Onderzoeker');

      expect(find.text('Toon technische details'), findsOneWidget);
      expect(find.byType(TechnicalDetailsToggle), findsOneWidget);
      expect(find.textContaining('"summary"'), findsNothing);
    },
  );

  testWidgets(
    'toggle is bereikbaar via Tab en activeerbaar met Enter en Spatie (AC2)',
    (tester) async {
      _growTestWindow(tester);
      await _openDialog(tester, _sessionWith(artifacts: [_readableArtifact()]));
      await _expandArtifactTile(tester, 'Onderzoeker');

      final toggleKey = find.byKey(const Key('technicalDetailsToggle'));

      var focused = false;
      for (var i = 0; i < 20 && !focused; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        final semantics = tester.getSemantics(toggleKey);
        focused =
            semantics.getSemanticsData().flagsCollection.isFocused ==
            Tristate.isTrue;
      }
      expect(focused, isTrue, reason: 'toggle moet via Tab bereikbaar zijn');

      expect(find.textContaining('"summary"'), findsNothing);

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(find.textContaining('"summary"'), findsOneWidget);

      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pump();
      expect(find.textContaining('"summary"'), findsNothing);
    },
  );

  testWidgets(
    'in-/uitgeklapte status wordt gecommuniceerd via Semantics(expanded:...) (AC3)',
    (tester) async {
      _growTestWindow(tester);
      await _openDialog(tester, _sessionWith(artifacts: [_readableArtifact()]));
      await _expandArtifactTile(tester, 'Onderzoeker');

      final toggleKey = find.byKey(const Key('technicalDetailsToggle'));
      final collapsed = tester.getSemantics(toggleKey).getSemanticsData();
      expect(collapsed.flagsCollection.isExpanded, Tristate.isFalse);

      await tester.ensureVisible(find.text('Toon technische details'));
      await tester.pump();
      await tester.tap(find.text('Toon technische details'));
      await tester.pump();

      final expanded = tester.getSemantics(toggleKey).getSemanticsData();
      expect(expanded.flagsCollection.isExpanded, Tristate.isTrue);
    },
  );

  testWidgets(
    'uitgeklapte technische detailtekst is na decodering gelijk aan de brondata (AC4)',
    (tester) async {
      _growTestWindow(tester);
      final artifact = _readableArtifact();
      await _openDialog(tester, _sessionWith(artifacts: [artifact]));
      await _expandArtifactTile(tester, 'Onderzoeker');

      await tester.ensureVisible(find.text('Toon technische details'));
      await tester.pump();
      await tester.tap(find.text('Toon technische details'));
      await tester.pumpAndSettle();

      final toggle = tester.widget<TechnicalDetailsToggle>(
        find.byType(TechnicalDetailsToggle),
      );
      final displayed = jsonDecode(toggle.content);
      final source = jsonDecode('${artifact['contentJson']}');
      expect(displayed, equals(source));
    },
  );

  testWidgets(
    'zonder leesbare velden blijft de ruwe JSON direct zichtbaar, zonder toggle (AC5)',
    (tester) async {
      _growTestWindow(tester);
      await _openDialog(tester, _sessionWith(artifacts: [_fallbackArtifact()]));
      await _expandArtifactTile(tester, 'onbekende rol');

      expect(find.text('Toon technische details'), findsNothing);
      expect(find.byType(TechnicalDetailsToggle), findsNothing);
      expect(find.textContaining('"someWeirdField"'), findsOneWidget);
    },
  );
}
