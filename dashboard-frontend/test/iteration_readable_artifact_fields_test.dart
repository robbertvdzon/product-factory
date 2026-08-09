import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/classification.dart';
import 'package:product_factory_dashboard/main.dart';

/// Regressie-/functionele tests voor product-79: naast de bestaande rauwe-JSON-weergave toont
/// `IterationSessionDialog` per agentrol ook een leesbare veldweergave, met veilige fallback naar
/// alleen rauwe JSON bij een onherkende structuur. Vervangt de echte HTTP-oproep van
/// [DashboardApi.shadowIterationSession] door vaste data, conform het patroon in
/// `iteration_session_error_message_test.dart`.
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
  String status = 'ACCEPTED',
  String? criticVerdict = 'ACCEPT',
  dynamic errorMessage,
  String? summary,
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
    'criticVerdict': criticVerdict,
    'candidateCount': 2,
    'workspaceRunId': null,
    'workspacePullRequestUrl': null,
    'workspaceCommitSha': null,
    'errorMessage': errorMessage,
    'summary': summary,
    'createdAt': DateTime(2026, 1, 1).toIso8601String(),
    'startedAt': DateTime(2026, 1, 1).toIso8601String(),
    'completedAt': DateTime(2026, 1, 1).toIso8601String(),
  },
  'steps': <dynamic>[],
  'artifacts': artifacts ?? <dynamic>[],
  'dossier': null,
};

Map<String, dynamic> _artifact(
  String artifactType,
  Map<String, dynamic> content,
) => {
  'artifactType': artifactType,
  'createdAt': DateTime(2026, 1, 1).toIso8601String(),
  'contentJson': jsonEncode(content),
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

Future<void> _expandArtifactTile(WidgetTester tester, String roleLabel) async {
  await tester.tap(find.text(roleLabel));
  await tester.pumpAndSettle();
}

/// De rauwe JSON staat, wanneer er leesbare velden zijn, achter een ingeklapte
/// 'Toon technische details'-toggle; klap die eerst uit voordat je op rauwe-JSON-tekst matcht.
Future<void> _expandTechnicalDetails(WidgetTester tester) async {
  final toggle = find.text('Toon technische details');
  await tester.ensureVisible(toggle);
  await tester.pump();
  await tester.tap(toggle);
  await tester.pumpAndSettle();
}

/// De dialoog kan meer content bevatten dan het standaard 800x600-testvenster (bv. het FAILED-
/// foutredenblok samen met een uitgeklapte artefacttegel); vergroot het venster zodat een
/// `ListView(children:...)` niet-zichtbare content ook daadwerkelijk bouwt (sliver-cache-extent).
void _growTestWindow(WidgetTester tester) {
  tester.view.physicalSize = const Size(1200, 3000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

void main() {
  testWidgets(
    'researcher-artefact met tekstuele findings toont leesbare tekst naast de rauwe JSON',
    (tester) async {
      _growTestWindow(tester);
      final artifact = _artifact('researcher', {
        'summary': 'Reizigers willen sneller inzicht in vertragingen.',
        'findings': [
          {
            'title': 'Vertraging is de grootste ergernis',
            'finding':
                'Gebruikers noemen onvoorspelbare vertragingen als hoofdklacht.',
            'sourceUrls': ['https://example.invalid/onderzoek'],
          },
        ],
        'currentState': {
          'purpose': 'Reisinformatie tonen',
          'gaps': ['Geen realtime melding'],
        },
        'improvementOpportunities': ['Pushmeldingen toevoegen'],
        'sources': [
          {'url': 'https://example.invalid/bron', 'rationale': 'Primaire bron'},
        ],
        'inspiration': [],
      });

      await _openDialog(tester, _sessionWith(artifacts: [artifact]));
      await _expandArtifactTile(tester, 'Onderzoeker');

      expect(find.text('Samenvatting'), findsOneWidget);
      expect(
        find.text('Reizigers willen sneller inzicht in vertragingen.'),
        findsOneWidget,
      );
      expect(find.text('Bevindingen'), findsOneWidget);
      expect(find.text('Vertraging is de grootste ergernis'), findsOneWidget);
      expect(
        find.text(
          'Gebruikers noemen onvoorspelbare vertragingen als hoofdklacht.',
        ),
        findsOneWidget,
      );
      expect(find.text('• https://example.invalid/onderzoek'), findsOneWidget);
      expect(find.text('Huidige situatie'), findsOneWidget);
      expect(find.text('Reisinformatie tonen'), findsOneWidget);
      expect(find.text('• Geen realtime melding'), findsOneWidget);
      expect(find.text('Verbetermogelijkheden'), findsOneWidget);
      expect(find.text('• Pushmeldingen toevoegen'), findsOneWidget);
      // Lege 'inspiration'-array levert geen kopje op (AC4).
      expect(find.text('Inspiratie'), findsNothing);

      // De rauwe-JSON-weergave blijft bestaan naast de leesbare tekst, achter de toggle (AC1/AC2).
      expect(find.textContaining('"summary"'), findsNothing);
      await _expandTechnicalDetails(tester);
      expect(find.textContaining('"summary"'), findsOneWidget);
    },
  );

  testWidgets(
    'product_owner-artefact met decisions-array inclusief rationale wordt leesbaar getoond',
    (tester) async {
      _growTestWindow(tester);
      final artifact = _artifact('product_owner', {
        'productDirection': 'Focus op realtime reisinformatie.',
        'rationale': 'Onderzoek toont dit als grootste knelpunt.',
        'priorities': ['Realtime vertraging tonen'],
        'decisions': [
          {
            'decision': 'Bouw een pushmeldingenflow',
            'rationale': 'Reduceert onzekerheid bij reizigers',
            'sourceUrls': ['https://example.invalid/besluit'],
          },
        ],
        'rejectedOptions': [],
      });

      await _openDialog(tester, _sessionWith(artifacts: [artifact]));
      await _expandArtifactTile(tester, 'Product owner');

      expect(find.text('Productrichting'), findsOneWidget);
      expect(find.text('Focus op realtime reisinformatie.'), findsOneWidget);
      expect(find.text('Besluiten'), findsOneWidget);
      expect(find.text('Bouw een pushmeldingenflow'), findsOneWidget);
      expect(find.text('Reduceert onzekerheid bij reizigers'), findsOneWidget);
      // Lege 'rejectedOptions'-array levert geen kopje op (AC4).
      expect(find.text('Afgewezen opties'), findsNothing);
    },
  );

  testWidgets(
    'artefact met een niet-herkende structuur toont uitsluitend de rauwe-JSON-fallback, geen crash',
    (tester) async {
      _growTestWindow(tester);
      final artifact = _artifact('onbekende_rol', {
        'someWeirdField': 'iets',
        'nested': {'x': 1},
      });

      await _openDialog(tester, _sessionWith(artifacts: [artifact]));
      await _expandArtifactTile(tester, 'onbekende rol');

      expect(tester.takeException(), isNull);
      expect(find.textContaining('"someWeirdField"'), findsOneWidget);
      // Geen van de bekende-rol-kopjes verschijnt voor een onherkende structuur.
      expect(find.text('Samenvatting'), findsNothing);
      expect(find.text('Productrichting'), findsNothing);
      expect(find.text('Bevindingen'), findsNothing);
    },
  );

  testWidgets(
    'artefact met onherkende JSON-vorm binnen een bekende rol valt terug op alleen rauwe JSON',
    (tester) async {
      _growTestWindow(tester);
      // 'findings' is hier een string i.p.v. een array: decodeerbaar als JSON, maar niet als het
      // verwachte schema. De leesbare velden die wél passen (summary) mogen getoond worden; de
      // niet-passende ('findings') moeten stilzwijgend overgeslagen worden zonder crash.
      final artifact = _artifact('researcher', {
        'summary': 'Korte samenvatting.',
        'findings': 'niet een array',
      });

      await _openDialog(tester, _sessionWith(artifacts: [artifact]));
      await _expandArtifactTile(tester, 'Onderzoeker');

      expect(tester.takeException(), isNull);
      expect(find.text('Samenvatting'), findsOneWidget);
      expect(find.text('Bevindingen'), findsNothing);
      await _expandTechnicalDetails(tester);
      expect(find.textContaining('"findings"'), findsOneWidget);
    },
  );

  testWidgets(
    'artefact met niet-decodeerbare contentJson toont alleen de rauwe tekst, geen crash',
    (tester) async {
      _growTestWindow(tester);
      final artifact = {
        'artifactType': 'researcher',
        'createdAt': DateTime(2026, 1, 1).toIso8601String(),
        'contentJson': 'geen geldige json {',
      };

      await _openDialog(tester, _sessionWith(artifacts: [artifact]));
      await _expandArtifactTile(tester, 'Onderzoeker');

      expect(tester.takeException(), isNull);
      expect(find.text('Samenvatting'), findsNothing);
      expect(find.text('geen geldige json {'), findsOneWidget);
    },
  );

  group('regressie: bestaande onderdelen van het dialoog blijven ongewijzigd', () {
    testWidgets(
      'classificatiebadge, FAILED-foutredenblok en samenvattingskaart blijven zichtbaar en functioneel',
      (tester) async {
        _growTestWindow(tester);
        final artifact = _artifact('researcher', {
          'summary': 'Reizigers willen sneller inzicht in vertragingen.',
          'findings': [
            {
              'title': 'Titel',
              'finding': 'Bevinding',
              'sourceUrls': ['https://example.invalid'],
            },
          ],
        });

        await _openDialog(
          tester,
          _sessionWith(
            status: 'FAILED',
            criticVerdict: null,
            errorMessage: 'Workspace-publicatie tijdelijk niet beschikbaar.',
            summary: 'Samenvatting voor de gebruiker.',
            artifacts: [artifact],
          ),
        );

        final badge = tester.widget<ClassificationBadge>(
          find.byType(ClassificationBadge),
        );
        expect(badge.classification, kGuardrailConflict);

        expect(find.text('Foutreden'), findsOneWidget);
        expect(
          find.text('Workspace-publicatie tijdelijk niet beschikbaar.'),
          findsOneWidget,
        );

        expect(find.text('Samenvatting voor jou'), findsOneWidget);
        expect(find.text('Samenvatting voor de gebruiker.'), findsOneWidget);

        await _expandArtifactTile(tester, 'Onderzoeker');
        expect(find.text('Bevindingen'), findsOneWidget);

        // De badge/foutreden/samenvattingskaart blijven onveranderd zichtbaar nadat de artefacttegel
        // is uitgeklapt.
        expect(find.byType(ClassificationBadge), findsOneWidget);
        expect(find.text('Foutreden'), findsOneWidget);
        expect(find.text('Samenvatting voor jou'), findsOneWidget);
      },
    );
  });
}
