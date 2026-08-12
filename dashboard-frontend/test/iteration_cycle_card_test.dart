import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/main.dart';

Map<String, dynamic> _iteration(String id, int sequence) => {
  'id': id,
  'productSlug': 'product-met-een-lange-naam',
  'sequenceNumber': sequence,
  'mode': 'autonomous',
  'status': 'NEEDS_REVISION',
  'currentRole': null,
  'criticVerdict': 'REVISE',
  'outcomeReason': 'PARTIAL_ACCEPT',
  'errorMessage': null,
  'candidateCount': 17,
  'acceptedCandidateCount': 3,
  'revisionRounds': 2,
  'workspacePullRequestUrl': 'https://example.invalid/pr/1',
  'startedAt': '2026-08-12T10:30:00Z',
  'createdAt': '2026-08-12T10:00:00Z',
  'completedAt': '2026-08-12T10:34:12Z',
};

final _candidates = <Map<String, dynamic>>[
  {
    'title':
        'Een interne kandidaat met een zeer lange titel die leesbaar moet blijven',
    'status': 'ACCEPTED',
  },
];

final _deliveries = <Map<String, dynamic>>[
  {
    'title':
        'Een Software Factory-levering met een zeer lange titel die mag omslaan',
    'status': 'IN_PROGRESS',
  },
];

const _notProvided = Object();

Widget _harness({
  Object? firstCandidates = _notProvided,
  Object? firstDeliveries = _notProvided,
  bool candidatesLoading = false,
  bool deliveriesLoading = false,
  bool twoCards = false,
  double textScale = 1,
}) => MaterialApp(
  theme: ThemeData(useMaterial3: true),
  home: MediaQuery(
    data: MediaQueryData(textScaler: TextScaler.linear(textScale)),
    child: Scaffold(
      body: RepaintBoundary(
        key: const ValueKey('golden-surface'),
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(8),
          child: Column(
            children: [
              IterationCycleCard(
                key: const ValueKey('cycle-one'),
                iteration: _iteration('iter-1', 31),
                candidates: identical(firstCandidates, _notProvided)
                    ? _candidates
                    : firstCandidates as List<Map<String, dynamic>>?,
                deliveries: identical(firstDeliveries, _notProvided)
                    ? _deliveries
                    : firstDeliveries as List<Map<String, dynamic>>?,
                candidatesLoading: candidatesLoading,
                deliveriesLoading: deliveriesLoading,
                onOpenDetails: () async {},
              ),
              if (twoCards)
                IterationCycleCard(
                  key: const ValueKey('cycle-two'),
                  iteration: _iteration('iter-2', 32),
                  candidates: const [],
                  deliveries: const [],
                  candidatesLoading: false,
                  deliveriesLoading: false,
                  onOpenDetails: () async {},
                ),
            ],
          ),
        ),
      ),
    ),
  ),
);

Finder _toggle(String id) =>
    find.byKey(ValueKey('iteration-results-toggle-$id'));

void main() {
  testWidgets(
    'gesloten kaart toont kernvelden, beslisbron en geladen aantallen',
    (tester) async {
      await tester.pumpWidget(_harness());
      final semantics = tester.ensureSemantics();

      expect(
        find.text('product-met-een-lange-naam · iteratie 31'),
        findsOneWidget,
      );
      expect(find.textContaining('Status: NEEDS_REVISION'), findsOneWidget);
      expect(find.textContaining('gestart 12-08-2026 10:30'), findsOneWidget);
      expect(
        find.textContaining('Kernreden: Minstens één kandidaat is leverbaar'),
        findsOneWidget,
      );
      expect(
        find.text('Beslisbron: Evaluatie-agent (Afgeleid)'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Interne kandidaten: 1 · geladen gegevens'),
        findsOneWidget,
      );
      expect(
        find.textContaining(
          'Software Factory-leveringen: 1 · geladen gegevens',
        ),
        findsOneWidget,
      );
      expect(find.text('Toon opbrengst'), findsOneWidget);
      expect(find.text('Kandidaatstatus: ACCEPTED'), findsNothing);

      final data = tester.getSemantics(_toggle('iter-1')).getSemanticsData();
      expect(data.label, contains('cyclus 31'));
      expect(data.flagsCollection.isExpanded, Tristate.isFalse);
      semantics.dispose();
    },
  );

  testWidgets('muis, Enter en Spatie schakelen native knop en behouden focus', (
    tester,
  ) async {
    await tester.pumpWidget(_harness());
    final toggle = _toggle('iter-1');

    await tester.tap(toggle);
    await tester.pump();
    expect(find.text('Verberg opbrengst'), findsOneWidget);
    expect(find.text('Interne kandidaten'), findsOneWidget);
    expect(find.text('Software Factory-leveringen'), findsOneWidget);
    expect(find.textContaining('Kandidaatstatus: ACCEPTED'), findsOneWidget);
    expect(find.textContaining('Leveringsstatus: IN_PROGRESS'), findsOneWidget);
    expect(FocusManager.instance.primaryFocus?.debugLabel, contains('results'));
    final focusedButton = tester.widget<OutlinedButton>(toggle);
    final focusedSide = focusedButton.style!.side!.resolve({
      WidgetState.focused,
    });
    expect(focusedSide?.color, kCycleToggleFocus);
    expect(focusedSide?.width, 3);

    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    expect(find.text('Toon opbrengst'), findsOneWidget);
    expect(find.textContaining('Kandidaatstatus: ACCEPTED'), findsNothing);
    expect(FocusManager.instance.primaryFocus?.debugLabel, contains('results'));

    await tester.sendKeyEvent(LogicalKeyboardKey.space);
    await tester.pump();
    expect(find.text('Verberg opbrengst'), findsOneWidget);
    expect(find.textContaining('Kandidaatstatus: ACCEPTED'), findsOneWidget);
  });

  testWidgets(
    'meerdere kaarten openen onafhankelijk en behouden state bij refresh',
    (tester) async {
      tester.view.physicalSize = const Size(1200, 5000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(_harness(twoCards: true));
      await tester.tap(_toggle('iter-1'));
      await tester.pump();

      expect(find.text('Verberg opbrengst'), findsOneWidget);
      expect(find.text('Toon opbrengst'), findsOneWidget);
      await tester.tap(_toggle('iter-2'));
      await tester.pump();
      expect(find.text('Verberg opbrengst'), findsNWidgets(2));

      await tester.pumpWidget(_harness(twoCards: true));
      await tester.pump();
      expect(find.text('Verberg opbrengst'), findsNWidgets(2));

      await tester.tap(_toggle('iter-1'));
      await tester.pump();
      expect(find.text('Verberg opbrengst'), findsOneWidget);
      expect(find.text('Toon opbrengst'), findsOneWidget);
    },
  );

  testWidgets(
    'lege, ladende en mislukte bronnen worden niet als nul gepresenteerd',
    (tester) async {
      await tester.pumpWidget(
        _harness(
          firstCandidates: const <Map<String, dynamic>>[],
          firstDeliveries: null,
          deliveriesLoading: true,
        ),
      );
      expect(
        find.textContaining('Interne kandidaten: 0 · geladen gegevens'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Software Factory-leveringen: laden…'),
        findsOneWidget,
      );
      await tester.tap(_toggle('iter-1'));
      await tester.pump();
      expect(
        find.text('Geen resultaten in de geladen gegevens.'),
        findsOneWidget,
      );
      expect(
        find.text('Resultaten uit geladen gegevens worden geladen.'),
        findsOneWidget,
      );

      await tester.pumpWidget(
        _harness(firstCandidates: null, firstDeliveries: _deliveries),
      );
      await tester.pump();
      expect(
        find.textContaining('Interne kandidaten: niet beschikbaar'),
        findsOneWidget,
      );
      expect(find.textContaining('Interne kandidaten: 0'), findsNothing);
      expect(
        find.text('Resultaten uit geladen gegevens zijn niet beschikbaar.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    '320 CSS-pixels en 200% tekst veroorzaken geen horizontale overflow',
    (tester) async {
      tester.view.physicalSize = const Size(320, 7000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(_harness(textScale: 2));
      await tester.ensureVisible(_toggle('iter-1'));
      await tester.pump();
      await tester.tap(_toggle('iter-1'));
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(find.textContaining('zeer lange titel'), findsNWidgets(2));
      expect(find.textContaining('Kandidaatstatus: ACCEPTED'), findsOneWidget);
      expect(
        find.textContaining('Leveringsstatus: IN_PROGRESS'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'golden dekt gesloten, geopend, leeg en onbeschikbaar bij smalle viewport',
    (tester) async {
      tester.view.physicalSize = const Size(320, 5000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        _harness(
          firstCandidates: const <Map<String, dynamic>>[],
          firstDeliveries: null,
          twoCards: true,
          textScale: 2,
        ),
      );
      await tester.ensureVisible(_toggle('iter-1'));
      await tester.pump();
      await tester.tap(_toggle('iter-1'));
      await tester.pump();

      await expectLater(
        find.byKey(const ValueKey('golden-surface')),
        matchesGoldenFile('goldens/iteration_cycle_card_states.png'),
      );
    },
  );

  test('nieuwe kleuren halen WCAG AA tegen hun gerenderde achtergrond', () {
    double luminance(Color color) {
      double channel(int value) {
        final normalized = value / 255;
        return normalized <= 0.04045
            ? normalized / 12.92
            : ((normalized + 0.055) / 1.055) *
                  ((normalized + 0.055) / 1.055) *
                  ((normalized + 0.055) / 1.055);
      }

      final argb = color.toARGB32();
      return 0.2126 * channel((argb >> 16) & 0xff) +
          0.7152 * channel((argb >> 8) & 0xff) +
          0.0722 * channel(argb & 0xff);
    }

    double contrast(Color foreground, Color background) {
      final first = luminance(foreground);
      final second = luminance(background);
      final lighter = first > second ? first : second;
      final darker = first > second ? second : first;
      return (lighter + 0.05) / (darker + 0.05);
    }

    for (final color in [
      kCycleCardText,
      kCycleCardSecondaryText,
      kCycleToggleText,
    ]) {
      expect(contrast(color, kCycleCardBackground), greaterThanOrEqualTo(4.5));
    }
    expect(
      contrast(kCycleErrorText, kCycleErrorBackground),
      greaterThanOrEqualTo(4.5),
    );
    expect(
      contrast(kCycleToggleFocus, kCycleCardBackground),
      greaterThanOrEqualTo(3),
    );
  });
}
