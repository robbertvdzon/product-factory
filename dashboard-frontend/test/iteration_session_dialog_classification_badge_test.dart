import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/classification.dart';
import 'package:product_factory_dashboard/main.dart';

/// Regressietests voor product-73: `IterationSessionDialog` toont voor de statuschip nu dezelfde
/// [ClassificationBadge] als de iteratierij op het overzicht, i.p.v. de kale rauwe-status-`Chip`.
/// Vervangt de echte HTTP-oproep van [DashboardApi.shadowIterationSession] door vaste data, conform
/// het patroon in `iteration_session_error_message_test.dart`.
class _FakeApi extends DashboardApi {
  _FakeApi(this.session) : super('http://example.invalid', null);

  final Map<String, dynamic> session;

  @override
  Future<Map<String, dynamic>> shadowIterationSession(
    String productSlug,
    String iterationId,
  ) async => session;
}

/// Bouwt dezelfde rijstructuur na als de iteratierij in `main.dart` ('Productcycli en
/// onderzoekssessies', regels ~582-593): titel plus [ClassificationBadge] in een `Wrap`.
class _IterationListRowHarness extends StatelessWidget {
  const _IterationListRowHarness({required this.iteration});

  final Map<String, dynamic> iteration;

  @override
  Widget build(BuildContext context) => MaterialApp(
    home: Scaffold(
      body: Card(
        child: ListTile(
          title: Wrap(
            spacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(
                '${iteration['productSlug']} · iteratie ${iteration['sequenceNumber']}',
              ),
              ClassificationBadge(
                classification: classifyIterationOutcome(
                  status: iteration['status'] as String?,
                  criticVerdict: iteration['criticVerdict'] as String?,
                  errorMessage: iteration['errorMessage'] as String?,
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

Map<String, dynamic> _sessionWith({
  required String? status,
  String? criticVerdict,
  dynamic errorMessage,
  List<dynamic>? steps,
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
    'summary': null,
    'createdAt': DateTime(2026, 1, 1).toIso8601String(),
    'startedAt': DateTime(2026, 1, 1).toIso8601String(),
    'completedAt': DateTime(2026, 1, 1).toIso8601String(),
  },
  'steps': steps ?? <dynamic>[],
  'artifacts': artifacts ?? <dynamic>[],
  'dossier': null,
};

/// Eén representatieve, afgeronde (niet QUEUED/RUNNING) iteratie per van de vijf toegestane
/// classificatiewaarden, zodat lijstkaart en dialoog voor dezelfde data vergeleken kunnen worden.
/// QUEUED/RUNNING blijven hier bewust buiten beeld: de lijstkaart toont dan `IterationProgressIndicator`
/// in plaats van de badge (main.dart:589-591), dus die statussen zijn geen geldige paritietscase.
final Map<String, Map<String, dynamic>> _sampleIterationByClassification = {
  kRichtingGekozen: {'status': 'ACCEPTED', 'criticVerdict': 'ACCEPT'},
  kOnderzoekOnvoldoende: {
    'status': 'NEEDS_REVISION',
    'criticVerdict': 'REVISE',
  },
  kRichtingVerworpen: {'status': 'REJECTED', 'criticVerdict': 'REJECT'},
  kTechnischeFout: {
    'status': 'FAILED',
    'errorMessage': 'Workspace-publicatie tijdelijk niet beschikbaar.',
  },
  kNietClassificeerbaar: {'status': 'ONVOORZIEN_TOEKOMSTIG_LABEL'},
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

Future<void> _closeDialog(WidgetTester tester) async {
  await tester.tap(find.text('Sluiten'));
  await tester.pump(const Duration(milliseconds: 300));
}

void main() {
  group('pariteit lijstkaart vs. detaildialoog', () {
    for (final entry in _sampleIterationByClassification.entries) {
      final classification = entry.key;
      testWidgets(
        '$classification: badge-tekst en kClassificationColors-paar identiek op lijstkaart en in dialoog',
        (tester) async {
          final fields = entry.value;
          final session = _sessionWith(
            status: fields['status'] as String?,
            criticVerdict: fields['criticVerdict'] as String?,
            errorMessage: fields['errorMessage'],
          );
          final iteration = session['iteration'] as Map<String, dynamic>;

          await tester.pumpWidget(
            _IterationListRowHarness(iteration: iteration),
          );
          await tester.pump();
          final rowBadge = tester.widget<ClassificationBadge>(
            find.byType(ClassificationBadge),
          );
          final rowColors = kClassificationColors[rowBadge.classification]!;

          await _openDialog(tester, session);
          final dialogBadge = tester.widget<ClassificationBadge>(
            find.byType(ClassificationBadge),
          );
          final dialogColors =
              kClassificationColors[dialogBadge.classification]!;

          expect(rowBadge.classification, classification);
          expect(dialogBadge.classification, classification);
          expect(dialogBadge.classification, rowBadge.classification);
          expect(dialogColors.background, rowColors.background);
          expect(dialogColors.foreground, rowColors.foreground);

          await _closeDialog(tester);
        },
      );
    }
  });

  group('geen rauwe-statuschip meer in het dialoog', () {
    testWidgets(
      'IterationSessionDialog bevat geen Chip die de rauwe statuswaarde als label toont',
      (tester) async {
        await _openDialog(
          tester,
          _sessionWith(status: 'NEEDS_REVISION', criticVerdict: 'REVISE'),
        );

        final rawStatusChip = find.byWidgetPredicate(
          (widget) =>
              widget is Chip &&
              widget.label is Text &&
              (widget.label as Text).data == 'NEEDS_REVISION',
        );
        expect(rawStatusChip, findsNothing);
        expect(find.byType(ClassificationBadge), findsOneWidget);

        await _closeDialog(tester);
      },
    );
  });

  group('toetsenbordnavigatie naar de badge in het dialoog', () {
    testWidgets(
      'badge is bereikbaar via Tab en toggelt de toelichting via Enter en Space',
      (tester) async {
        await _openDialog(
          tester,
          _sessionWith(status: 'ACCEPTED', criticVerdict: 'ACCEPT'),
        );

        var focused = false;
        for (var i = 0; i < 10 && !focused; i++) {
          await tester.sendKeyEvent(LogicalKeyboardKey.tab);
          await tester.pump();
          final semantics = tester.getSemantics(
            find.byType(ClassificationBadge),
          );
          focused = semantics.flagsCollection.isFocused == Tristate.isTrue;
        }
        expect(focused, isTrue, reason: 'badge moet via Tab bereikbaar zijn');
        expect(find.text(kBadgeScopeDisclaimer), findsNothing);

        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pump();
        expect(find.text(kBadgeScopeDisclaimer), findsOneWidget);

        await tester.sendKeyEvent(LogicalKeyboardKey.space);
        await tester.pump();
        expect(find.text(kBadgeScopeDisclaimer), findsNothing);

        await _closeDialog(tester);
      },
    );
  });

  group('Semantics-aankondiging identiek tussen lijstkaart en dialoog', () {
    testWidgets('label komt exact overeen voor dezelfde classificatie', (
      tester,
    ) async {
      final session = _sessionWith(status: 'REJECTED', criticVerdict: 'REJECT');
      final iteration = session['iteration'] as Map<String, dynamic>;

      await tester.pumpWidget(_IterationListRowHarness(iteration: iteration));
      await tester.pump();
      // getSemanticsData() geeft het gemergde label van de hele omringende rij (bv. titeltekst)
      // terug, niet uitsluitend het eigen label van de badge (zie ook
      // classification_badge_disclosure_test.dart e.a.); vergelijk daarom op classificatienaam en
      // op de knop-rol (die de 'activeren toont uitleg'-hint draagt), niet op de volledige,
      // rij-afhankelijke tekst.
      final rowSemantics = tester
          .getSemantics(find.byType(ClassificationBadge))
          .getSemanticsData();

      await _openDialog(tester, session);
      final dialogSemantics = tester
          .getSemantics(find.byType(ClassificationBadge))
          .getSemanticsData();

      expect(dialogSemantics.label, contains(kRichtingVerworpen));
      expect(rowSemantics.label, contains(kRichtingVerworpen));
      expect(
        dialogSemantics.flagsCollection.isButton,
        rowSemantics.flagsCollection.isButton,
      );
      expect(dialogSemantics.flagsCollection.isButton, isTrue);
      expect(dialogSemantics.hint, rowSemantics.hint);
      expect(
        dialogSemantics.flagsCollection.isExpanded,
        rowSemantics.flagsCollection.isExpanded,
      );

      await _closeDialog(tester);
    });
  });

  group('WCAG-contrast van de badgekleuren in het dialoog', () {
    for (final entry in _sampleIterationByClassification.entries) {
      final classification = entry.key;
      testWidgets(
        '$classification: contrast van de badge in het dialoog haalt minimaal 4.5:1',
        (tester) async {
          final fields = entry.value;
          await _openDialog(
            tester,
            _sessionWith(
              status: fields['status'] as String?,
              criticVerdict: fields['criticVerdict'] as String?,
              errorMessage: fields['errorMessage'],
            ),
          );

          final badge = tester.widget<ClassificationBadge>(
            find.byType(ClassificationBadge),
          );
          expect(badge.classification, classification);
          final colors = kClassificationColors[badge.classification]!;
          final ratio = contrastRatio(colors.background, colors.foreground);
          expect(ratio, greaterThanOrEqualTo(4.5));

          await _closeDialog(tester);
        },
      );
    }
  });

  group('focusvolgorde in het dialoog', () {
    testWidgets(
      'de badge is het eerste focusbare element, vóór Voortgang/agentresultaten/workspace-publicaties',
      (tester) async {
        await _openDialog(
          tester,
          _sessionWith(
            status: 'ACCEPTED',
            criticVerdict: 'ACCEPT',
            steps: [
              {
                'role': 'writer',
                'attempt': 1,
                'status': 'COMPLETED',
                'startedAt': DateTime(2026, 1, 1).toIso8601String(),
                'completedAt': DateTime(2026, 1, 1).toIso8601String(),
              },
            ],
            artifacts: [
              {
                'artifactType': 'onderzoek',
                'createdAt': DateTime(2026, 1, 1).toIso8601String(),
                'contentJson': '{}',
              },
            ],
          ),
        );

        // Eén enkele Tab vanuit de net geopende dialoog moet direct op de badge landen: die is het
        // eerste focusbare element in de widget-tree, vóór 'Voortgang' (agentstappen),
        // agentresultaten (ExpansionTile) en workspace-publicaties.
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();

        final badgeSemantics = tester.getSemantics(
          find.byType(ClassificationBadge),
        );
        expect(badgeSemantics.flagsCollection.isFocused, Tristate.isTrue);

        // Er is geen enkel ander focusbaar element (bv. het agentresultaat-ExpansionTile) dat vóór
        // de badge focus zou kunnen hebben gekregen.
        final expansionTileSemantics = tester.getSemantics(
          find.byType(ExpansionTile).first,
        );
        expect(
          expansionTileSemantics.flagsCollection.isFocused,
          isNot(Tristate.isTrue),
        );

        await _closeDialog(tester);
      },
    );
  });
}
