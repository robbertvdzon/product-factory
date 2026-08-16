import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:product_factory_dashboard/manual_cycle_start.dart';

Widget _harness({
  required Future<void> Function(ManualCycleStartSubmission) onStart,
}) => MaterialApp(
  home: Scaffold(
    body: Builder(
      builder: (context) => StartCycleButton(
        onPressed: () async {
          await showDialog<bool>(
            context: context,
            traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
            builder: (_) => ManualCycleStartDialog(
              productSlug: 'actief-product',
              onStart: onStart,
            ),
          );
        },
      ),
    ),
  ),
);

Future<void> _open(WidgetTester tester, Widget harness) async {
  await tester.pumpWidget(harness);
  await tester.tap(find.byType(StartCycleButton));
  await tester.pumpAndSettle();
}

class _DetailApi extends DashboardApi {
  _DetailApi(this.origin) : super('', null);

  final Object? origin;

  @override
  Future<Map<String, dynamic>> shadowIterationSession(
    String productSlug,
    String iterationId,
  ) async => {
    'iteration': <String, dynamic>{
      'id': iterationId,
      'productSlug': productSlug,
      'sequenceNumber': 7,
      'focus': 'Exact opgeslagen opdracht',
      'manualStartOrigin': origin,
      'mode': 'autonomous',
      'status': 'RUNNING',
      'currentRole': null,
      'criticVerdict': null,
      'candidateCount': 0,
      'acceptedCandidateCount': 0,
      'revisionRounds': 0,
      'errorMessage': null,
      'summary': null,
      'createdAt': '2026-08-16T05:00:00Z',
      'startedAt': null,
      'completedAt': null,
      'outcomeReason': null,
      'resumedFromIterationId': null,
      'decision': null,
    },
    'steps': <dynamic>[],
    'artifacts': <dynamic>[],
    'dossier': null,
  };
}

void main() {
  testWidgets(
    'dialoog is benoemd, toont autonome samenvatting en houdt focus vast tot Escape terugkeert naar opener',
    (tester) async {
      final semantics = tester.ensureSemantics();
      await _open(tester, _harness(onStart: (_) async {}));

      expect(find.byType(ManualCycleStartDialog), findsOneWidget);
      expect(find.text('Actief product: actief-product'), findsNWidgets(2));
      expect(find.textContaining(autonomousDefaultFocus), findsOneWidget);
      expect(find.text('Herkomst: Autonome standaard'), findsOneWidget);
      expect(
        tester
            .getSemantics(find.byKey(const ValueKey('manual-start-summary')))
            .getSemanticsData()
            .label,
        contains(autonomousDefaultFocus),
      );

      for (var i = 0; i < 12; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        final focusContext = FocusManager.instance.primaryFocus?.context;
        expect(focusContext, isNotNull);
        final focusedElement = find.byElementPredicate(
          (element) => identical(element, focusContext),
        );
        expect(
          find.ancestor(
            of: focusedElement,
            matching: find.byType(ManualCycleStartDialog),
          ),
          findsOneWidget,
        );
      }

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.byType(ManualCycleStartDialog), findsNothing);
      expect(
        FocusManager.instance.primaryFocus?.debugLabel,
        'Start-productcyclus-knop',
      );
      semantics.dispose();
    },
  );

  testWidgets(
    'eigenaarinput valideert na trimmen en gebruikt een bytegelijke effectieve opdracht',
    (tester) async {
      ManualCycleStartSubmission? submission;
      await _open(
        tester,
        _harness(onStart: (value) async => submission = value),
      );

      await tester.tap(find.text('Eigen onderzoeksvraag').first);
      await tester.pump();
      expect(find.byKey(const ValueKey('owner-focus-field')), findsOneWidget);

      await tester.enterText(
        find.byKey(const ValueKey('owner-focus-field')),
        '   ',
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Cyclus starten'));
      await tester.pump();
      expect(find.text('Vul een onderzoeksvraag in.'), findsOneWidget);
      expect(submission, isNull);

      await tester.enterText(
        find.byKey(const ValueKey('owner-focus-field')),
        " ${'x'.padRight(301, 'x')} ",
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Cyclus starten'));
      await tester.pump();
      expect(find.textContaining('maximaal 300 tekens'), findsOneWidget);
      expect(submission, isNull);

      const raw = '  Welke  vraag\nblijft open?  ';
      await tester.enterText(
        find.byKey(const ValueKey('owner-focus-field')),
        raw,
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Cyclus starten'));
      await tester.pumpAndSettle();
      expect(submission?.focus, 'Welke  vraag\nblijft open?');
      expect(submission?.origin, ManualStartOrigin.ownerInput);
    },
  );

  testWidgets(
    'mislukking bewaart keuze en invoer, meldt veilig en schakelt bevestigen opnieuw in',
    (tester) async {
      var calls = 0;
      await _open(
        tester,
        _harness(
          onStart: (_) async {
            calls++;
            throw StateError(
              'serverfout met vrije tekst die niet getoond mag worden',
            );
          },
        ),
      );
      await tester.tap(find.text('Eigen onderzoeksvraag').first);
      await tester.pump();
      await tester.enterText(
        find.byKey(const ValueKey('owner-focus-field')),
        'Bewaar deze vrije vraag',
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Cyclus starten'));
      await tester.pumpAndSettle();

      expect(calls, 1);
      expect(find.text('Bewaar deze vrije vraag'), findsWidgets);
      expect(find.textContaining('serverfout met vrije tekst'), findsNothing);
      expect(
        find.byKey(const ValueKey('manual-start-error-status')),
        findsOneWidget,
      );
      final confirm = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Cyclus starten'),
      );
      expect(confirm.onPressed, isNotNull);
    },
  );

  testWidgets('lopend verzoek accepteert geen dubbele bevestiging', (
    tester,
  ) async {
    final pending = Completer<void>();
    var calls = 0;
    await _open(
      tester,
      _harness(
        onStart: (_) {
          calls++;
          return pending.future;
        },
      ),
    );

    await tester.tap(find.widgetWithText(FilledButton, 'Cyclus starten'));
    await tester.pump();
    expect(calls, 1);
    expect(find.widgetWithText(FilledButton, 'Starten…'), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(find.widgetWithText(FilledButton, 'Starten…'))
          .onPressed,
      isNull,
    );

    await tester.tap(find.widgetWithText(FilledButton, 'Starten…'));
    await tester.pump();
    expect(calls, 1);
    pending.complete();
    await tester.pumpAndSettle();
  });

  test(
    'pure validatie en gesloten detailmapping leiden onbekende herkomst niet af',
    () {
      expect(validateOwnerFocus(' x '), isNull);
      expect(validateOwnerFocus(" ${'x'.padRight(300, 'x')} "), isNull);
      expect(validateOwnerFocus(" ${'x'.padRight(301, 'x')} "), isNotNull);
      expect(
        manualStartOriginLabel('AUTONOMOUS_DEFAULT'),
        'Autonome standaard',
      );
      expect(manualStartOriginLabel('OWNER_INPUT'), 'Eigenaarinput');
      expect(manualStartOriginLabel(null), isNull);
      expect(manualStartOriginLabel('UNKNOWN'), isNull);
    },
  );

  test(
    'eigenaarinput gebruikt dezelfde expliciete Unicode-trimset als de runtime',
    () {
      const edgeWhitespace = <String>[
        '\u0009',
        '\u000A',
        '\u000B',
        '\u000C',
        '\u000D',
        '\u0020',
        '\u0085',
        '\u00A0',
        '\u1680',
        '\u2000',
        '\u200A',
        '\u2028',
        '\u2029',
        '\u202F',
        '\u205F',
        '\u3000',
        '\uFEFF',
      ];

      for (final whitespace in edgeWhitespace) {
        expect(trimManualStartFocus('${whitespace}Vraag$whitespace'), 'Vraag');
        expect(validateOwnerFocus(whitespace), isNotNull);
      }
      expect(trimManualStartFocus('\u200BVraag\u200B'), '\u200BVraag\u200B');
    },
  );

  testWidgets(
    'bestaand cyclusdetail toont opgeslagen opdracht en alleen bekende handmatige herkomst',
    (tester) async {
      tester.view.physicalSize = const Size(1200, 1200);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        MaterialApp(
          home: IterationSessionDialog(
            key: const ValueKey('owner-detail'),
            api: _DetailApi('OWNER_INPUT'),
            productSlug: 'actief-product',
            iterationId: 'iteration-7',
          ),
        ),
      );
      await tester.pump();
      expect(find.text('Exact opgeslagen opdracht'), findsOneWidget);
      expect(find.text('Herkomst: Eigenaarinput'), findsOneWidget);

      await tester.pumpWidget(
        MaterialApp(
          home: IterationSessionDialog(
            key: const ValueKey('historical-detail'),
            api: _DetailApi(null),
            productSlug: 'actief-product',
            iterationId: 'iteration-historical',
          ),
        ),
      );
      await tester.pump();
      expect(find.textContaining('Herkomst:'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
