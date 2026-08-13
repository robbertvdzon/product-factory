import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/classification.dart';
import 'package:product_factory_dashboard/iteration_evidence.dart';
import 'package:product_factory_dashboard/main.dart';

Map<String, dynamic> _iteration({
  String id = 'iteration-42',
  String productSlug = 'product-factory',
  Object? sequenceNumber = 42,
  String status = 'ACCEPTED',
  String? criticVerdict = 'ACCEPT',
  Object? outcomeReason = 'ACCEPT',
  String? errorMessage,
  Map<String, dynamic>? decision,
}) => {
  'id': id,
  'productSlug': productSlug,
  'sequenceNumber': sequenceNumber,
  'status': status,
  'criticVerdict': criticVerdict,
  'outcomeReason': outcomeReason,
  'errorMessage': errorMessage,
  'startedAt': '2026-08-12T10:30:00Z',
  'createdAt': '2026-08-12T10:00:00Z',
  if (decision != null) 'decision': decision,
  'prompt': 'GEHEIME RUWE PROMPT',
  'logs': 'GEHEIME RUWE LOGS',
  'artifactContent': 'GEHEIME ARTEFACTINHOUD',
};

Widget _harness({
  Map<String, dynamic>? iteration,
  List<Map<String, dynamic>>? deliveries = const [],
  bool deliveriesLoading = false,
  double textScale = 1,
  Future<void> Function()? onOpenDetails,
}) => MaterialApp(
  theme: ThemeData(useMaterial3: true),
  home: MediaQuery(
    data: MediaQueryData(textScaler: TextScaler.linear(textScale)),
    child: Scaffold(
      body: SingleChildScrollView(
        child: IterationEvidenceRow(
          iteration: iteration ?? _iteration(),
          deliveries: deliveries,
          deliveriesLoading: deliveriesLoading,
          onOpenDetails: onOpenDetails ?? () async {},
        ),
      ),
    ),
  ),
);

Finder _richText(String text) => find.text(text, findRichText: true);

void main() {
  group('shouldShowIterationEvidence', () {
    for (final status in kProductFactoryEvidenceStatuses) {
      test(
        'ondersteunt terminale status $status voor exact product-factory',
        () {
          expect(
            shouldShowIterationEvidence(_iteration(status: status)),
            isTrue,
          );
        },
      );
    }

    for (final status in ['QUEUED', 'RUNNING', 'CANCELLED', 'accepted']) {
      test('behoudt bestaande kaart voor status $status', () {
        expect(
          shouldShowIterationEvidence(_iteration(status: status)),
          isFalse,
        );
      });
    }

    test('productslug is exact en hoofdlettergevoelig', () {
      for (final slug in ['demo', 'Product-Factory', 'product-factory ']) {
        expect(
          shouldShowIterationEvidence(_iteration(productSlug: slug)),
          isFalse,
        );
      }
    });
  });

  group('iterationEvidencePresentation', () {
    final fixtures =
        <
          ({
            String status,
            String? verdict,
            String reason,
            String outcome,
            String source,
          })
        >[
          (
            status: 'ACCEPTED',
            verdict: 'ACCEPT',
            reason: 'Alle kandidaten zijn leverbaar',
            outcome: 'richting-gekozen',
            source: 'Evaluatie-agent (Afgeleid)',
          ),
          (
            status: 'NEEDS_REVISION',
            verdict: 'REVISE',
            reason:
                'Minstens één kandidaat is leverbaar; andere vragen nog werk',
            outcome: 'onderzoek-onvoldoende',
            source: 'Evaluatie-agent (Afgeleid)',
          ),
          (
            status: 'REJECTED',
            verdict: 'REJECT',
            reason: 'De gekozen richting is fundamenteel afgewezen',
            outcome: 'richting-verworpen',
            source: 'Evaluatie-agent (Afgeleid)',
          ),
          (
            status: 'NO_CHANGE',
            verdict: 'ACCEPT',
            reason: 'Het resultaat was al eerder geleverd',
            outcome: 'richting-gekozen',
            source: 'Evaluatie-agent (Afgeleid)',
          ),
          (
            status: 'FAILED',
            verdict: null,
            reason: 'De cyclus is door een technische fout gestopt',
            outcome: 'technische fout',
            source: 'Technische fout (Afgeleid)',
          ),
        ];

    for (final fixture in fixtures) {
      test('presenteert ${fixture.status} via bestaande mappings', () {
        final reasonCode = switch (fixture.status) {
          'ACCEPTED' => 'ACCEPT',
          'NEEDS_REVISION' => 'PARTIAL_ACCEPT',
          'REJECTED' => 'REJECT',
          'NO_CHANGE' => 'ALREADY_DELIVERED',
          _ => 'TECHNICAL_FAILURE',
        };
        final result = iterationEvidencePresentation(
          _iteration(
            status: fixture.status,
            criticVerdict: fixture.verdict,
            outcomeReason: reasonCode,
            errorMessage: fixture.status == 'FAILED'
                ? 'Ruwe technische fout die niet zichtbaar mag zijn'
                : null,
          ),
        );

        expect(result.date, '12-08-2026 10:30');
        expect(result.outcome, fixture.outcome);
        expect(result.reason, fixture.reason);
        expect(result.decisionSource, fixture.source);
      });
    }

    test('geldige handmatige annulering wint van technische afleiding', () {
      final result = iterationEvidencePresentation(
        _iteration(
          status: 'FAILED',
          criticVerdict: null,
          outcomeReason: 'TECHNICAL_FAILURE',
          errorMessage: 'Ruwe foutpayload',
          decision: {
            'iterationId': 'iteration-42',
            'actorType': 'HUMAN',
            'mechanism': 'MANUAL_CANCELLATION',
            'reasonCode': 'MANUALLY_CANCELLED',
            'decidedAt': '2026-08-12T10:31:00Z',
          },
        ),
      );

      expect(result.outcome, 'Handmatig geannuleerd');
      expect(result.reason, 'Handmatig geannuleerd');
      expect(result.decisionSource, 'Mens');
    });

    test('onbekende en ontbrekende presentatiegegevens lekken niet', () {
      final iteration = _iteration(
        status: 'FAILED',
        criticVerdict: 'ONBEKEND',
        outcomeReason: 'TOKEN=geheim',
      )..addAll({'startedAt': 'geen datum', 'createdAt': null});
      final result = iterationEvidencePresentation(iteration);

      expect(result.date, kEvidenceUnknown);
      expect(result.reason, kEvidenceUnknown);
      expect(result.decisionSource, 'Onbekend (Afgeleid)');
      expect(result.reason, isNot(contains('TOKEN')));
    });

    test('ontbrekende startedAt valt terug op geldige createdAt', () {
      final iteration = _iteration()
        ..addAll({'startedAt': null, 'createdAt': '2026-08-12T10:00:00Z'});

      expect(iterationEvidencePresentation(iteration).date, '12-08-2026 10:00');
    });

    test('onleesbare startedAt valt terug op geldige createdAt', () {
      final iteration = _iteration()
        ..addAll({
          'startedAt': 'geen datum',
          'createdAt': '2026-08-12T10:00:00Z',
        });

      expect(iterationEvidencePresentation(iteration).date, '12-08-2026 10:00');
    });

    test(
      'onbekend expliciet record claimt geen mens of afgeleide beslisser',
      () {
        final result = iterationEvidencePresentation(
          _iteration(
            status: 'ACCEPTED',
            criticVerdict: 'ACCEPT',
            outcomeReason: 'ACCEPT',
            errorMessage: null,
            decision: {
              'iterationId': 'iteration-42',
              'actorType': 'HUMAN',
              'mechanism': 'ONBEKEND_MECHANISME',
              'reasonCode': 'ONBEKENDE_REDEN',
            },
          ),
        );

        expect(result.outcome, 'richting-gekozen');
        expect(result.decisionSource, 'Onbekend');
        expect(result.decisionSource, isNot(contains('Mens')));
        expect(result.decisionSource, isNot(contains('Afgeleid')));
      },
    );
  });

  final widgetFixtures =
      <
        ({
          String name,
          Map<String, dynamic> iteration,
          String outcome,
          String reason,
          String source,
        })
      >[
        (
          name: 'ACCEPTED',
          iteration: _iteration(),
          outcome: 'richting-gekozen',
          reason: 'Alle kandidaten zijn leverbaar',
          source: 'Evaluatie-agent (Afgeleid)',
        ),
        (
          name: 'NEEDS_REVISION',
          iteration: _iteration(
            status: 'NEEDS_REVISION',
            criticVerdict: 'REVISE',
            outcomeReason: 'PARTIAL_ACCEPT',
          ),
          outcome: 'onderzoek-onvoldoende',
          reason: 'Minstens één kandidaat is leverbaar; andere vragen nog werk',
          source: 'Evaluatie-agent (Afgeleid)',
        ),
        (
          name: 'REJECTED',
          iteration: _iteration(
            status: 'REJECTED',
            criticVerdict: 'REJECT',
            outcomeReason: 'REJECT',
          ),
          outcome: 'richting-verworpen',
          reason: 'De gekozen richting is fundamenteel afgewezen',
          source: 'Evaluatie-agent (Afgeleid)',
        ),
        (
          name: 'NO_CHANGE',
          iteration: _iteration(
            status: 'NO_CHANGE',
            criticVerdict: 'ACCEPT',
            outcomeReason: 'ALREADY_DELIVERED',
          ),
          outcome: 'richting-gekozen',
          reason: 'Het resultaat was al eerder geleverd',
          source: 'Evaluatie-agent (Afgeleid)',
        ),
        (
          name: 'technisch FAILED',
          iteration: _iteration(
            status: 'FAILED',
            criticVerdict: null,
            outcomeReason: 'TECHNICAL_FAILURE',
            errorMessage: 'Ruwe foutmelding',
          ),
          outcome: 'technische fout',
          reason: 'De cyclus is door een technische fout gestopt',
          source: 'Technische fout (Afgeleid)',
        ),
        (
          name: 'handmatig geannuleerd',
          iteration: _iteration(
            status: 'FAILED',
            criticVerdict: null,
            outcomeReason: 'TECHNICAL_FAILURE',
            errorMessage: 'Ruwe foutmelding',
            decision: {
              'iterationId': 'iteration-42',
              'actorType': 'HUMAN',
              'mechanism': 'MANUAL_CANCELLATION',
              'reasonCode': 'MANUALLY_CANCELLED',
              'decidedAt': '2026-08-12T10:31:00Z',
            },
          ),
          outcome: 'Handmatig geannuleerd',
          reason: 'Handmatig geannuleerd',
          source: 'Mens',
        ),
      ];

  for (final fixture in widgetFixtures) {
    testWidgets(
      'bewijsregel toont alle gelabelde waarden voor ${fixture.name}',
      (tester) async {
        await tester.pumpWidget(
          _harness(iteration: fixture.iteration, deliveries: const [{}]),
        );

        expect(_richText('Datum: 12-08-2026 10:30'), findsOneWidget);
        expect(_richText('Cyclusuitkomst: ${fixture.outcome}'), findsOneWidget);
        expect(_richText('Reden: ${fixture.reason}'), findsOneWidget);
        expect(_richText('Beslisbron: ${fixture.source}'), findsOneWidget);
        expect(_richText('Gekoppelde opbrengst: 1'), findsOneWidget);
        expect(find.text('Bekijk bewijs'), findsOneWidget);
      },
    );
  }

  testWidgets(
    'vijf waarden en actie staan zichtbaar in één semantische bewijscontainer',
    (tester) async {
      await tester.pumpWidget(
        _harness(
          deliveries: [
            {'id': 1},
            {'id': 2},
          ],
        ),
      );
      final semantics = tester.ensureSemantics();

      expect(_richText('Datum: 12-08-2026 10:30'), findsOneWidget);
      expect(_richText('Cyclusuitkomst: richting-gekozen'), findsOneWidget);
      expect(
        _richText('Reden: Alle kandidaten zijn leverbaar'),
        findsOneWidget,
      );
      expect(
        _richText('Beslisbron: Evaluatie-agent (Afgeleid)'),
        findsOneWidget,
      );
      expect(_richText('Gekoppelde opbrengst: 2'), findsOneWidget);
      expect(find.text('Bekijk bewijs'), findsOneWidget);
      expect(find.text('Toon opbrengst'), findsNothing);
      expect(find.byType(IterationCycleCard), findsNothing);

      final group = tester
          .getSemantics(find.byType(IterationEvidenceRow))
          .getSemanticsData();
      expect(group.label, contains('product product-factory, cyclus 42'));
      for (final label in [
        'Datum: 12-08-2026 10:30',
        'Cyclusuitkomst: richting-gekozen',
        'Reden: Alle kandidaten zijn leverbaar',
        'Beslisbron: Evaluatie-agent (Afgeleid)',
        'Gekoppelde opbrengst: 2',
      ]) {
        expect(find.bySemanticsLabel(label), findsOneWidget);
      }
      final button = find.byType(OutlinedButton);
      final buttonData = tester.getSemantics(button).getSemanticsData();
      expect(buttonData.label, contains('product product-factory, cyclus 42'));
      expect(buttonData.flagsCollection.isButton, isTrue);
      semantics.dispose();

      for (final forbidden in [
        'GEHEIME RUWE PROMPT',
        'GEHEIME RUWE LOGS',
        'GEHEIME ARTEFACTINHOUD',
      ]) {
        expect(find.textContaining(forbidden), findsNothing);
      }
    },
  );

  testWidgets(
    'ladende en mislukte leveringsbron worden nooit als nul getoond',
    (tester) async {
      await tester.pumpWidget(
        _harness(deliveries: null, deliveriesLoading: true),
      );
      expect(_richText('Gekoppelde opbrengst: laden…'), findsOneWidget);
      expect(_richText('Gekoppelde opbrengst: 0'), findsNothing);

      await tester.pumpWidget(
        _harness(deliveries: null, deliveriesLoading: false),
      );
      await tester.pump();
      expect(
        _richText('Gekoppelde opbrengst: niet beschikbaar'),
        findsOneWidget,
      );
      expect(_richText('Gekoppelde opbrengst: 0'), findsNothing);
    },
  );

  testWidgets(
    'muis, Enter, Spatie, sluitactie en Escape herstellen focus naar dezelfde knop',
    (tester) async {
      Future<void> openDialog() => showDialog<void>(
        context: tester.element(find.byType(IterationEvidenceRow)),
        traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
        builder: (context) => AlertDialog(
          title: const Text('Bestaand cyclusdetail'),
          actions: [
            TextButton(onPressed: () {}, child: const Text('Andere actie')),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Sluiten'),
            ),
          ],
        ),
      );

      await tester.pumpWidget(_harness(onOpenDetails: openDialog));
      final button = find.byType(OutlinedButton);

      await tester.tap(button);
      await tester.pumpAndSettle();
      expect(find.text('Bestaand cyclusdetail'), findsOneWidget);
      for (var tab = 0; tab < 6; tab++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        final focusContext = FocusManager.instance.primaryFocus?.context;
        expect(
          focusContext == null
              ? false
              : find
                    .ancestor(
                      of: find.byElementPredicate(
                        (element) => identical(element, focusContext),
                      ),
                      matching: find.byType(AlertDialog),
                    )
                    .evaluate()
                    .isNotEmpty,
          isTrue,
        );
      }
      await tester.tap(find.text('Sluiten'));
      await tester.pumpAndSettle();
      expect(
        tester.getSemantics(button).flagsCollection.isFocused,
        Tristate.isTrue,
      );
      final focusedSide = tester
          .widget<OutlinedButton>(button)
          .style!
          .side!
          .resolve({WidgetState.focused});
      expect(focusedSide?.width, 3);
      expect(focusedSide?.color, kCycleToggleFocus);

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(find.text('Bestaand cyclusdetail'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('Bestaand cyclusdetail'), findsNothing);
      expect(
        tester.getSemantics(button).flagsCollection.isFocused,
        Tristate.isTrue,
      );

      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();
      expect(find.text('Bestaand cyclusdetail'), findsOneWidget);
    },
  );

  test('tekst-, bedienings- en focuscontrast halen WCAG AA', () {
    expect(
      contrastRatio(kCycleCardText, kCycleCardBackground),
      greaterThanOrEqualTo(4.5),
    );
    expect(
      contrastRatio(kCycleToggleText, kCycleCardBackground),
      greaterThanOrEqualTo(4.5),
    );
    expect(
      contrastRatio(kCycleToggleFocus, kCycleCardBackground),
      greaterThanOrEqualTo(3),
    );
  });

  for (final size in [const Size(320, 1600), const Size(1200, 900)]) {
    testWidgets('${size.width.toInt()}px bij 200% blijft zonder overflow', (
      tester,
    ) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(_harness(textScale: 2));
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(_richText('Datum: 12-08-2026 10:30'), findsOneWidget);
      expect(find.text('Bekijk bewijs'), findsOneWidget);
      final rowRect = tester.getRect(find.byType(IterationEvidenceRow));
      expect(rowRect.left, greaterThanOrEqualTo(0));
      expect(rowRect.right, lessThanOrEqualTo(size.width));
    });
  }
}
