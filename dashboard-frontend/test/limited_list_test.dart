import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/formatting.dart';
import 'package:product_factory_dashboard/limited_list.dart';

/// Minimale nabootsing van hoe het dashboard [LimitedListSection] gebruikt: de teller zit in de state
/// van de omliggende pagina, niet in de sectie zelf. Zo kan de test ook een refresh nadoen.
class _Harness extends StatefulWidget {
  const _Harness({required this.labels});

  final List<String> labels;

  @override
  State<_Harness> createState() => _HarnessState();
}

class _HarnessState extends State<_Harness> {
  int visibleCount = kInitialVisibleItems;
  List<String> labels = const [];

  @override
  void initState() {
    super.initState();
    labels = widget.labels;
  }

  /// Bootst de auto-refresh na: nieuwe data, dezelfde teller.
  void refreshWith(List<String> next) => setState(() => labels = next);

  @override
  Widget build(BuildContext context) => MaterialApp(
    home: Scaffold(
      body: ListView(
        children: [
          LimitedListSection(
            itemCount: labels.length,
            visibleCount: visibleCount,
            itemBuilder: (_, index) => ListTile(title: Text(labels[index])),
            onShowMore: () => setState(() => visibleCount += kShowMoreStep),
          ),
        ],
      ),
    ),
  );
}

List<String> _labels(int count) =>
    List.generate(count, (index) => 'item-$index');

/// Zet een hoog testvenster neer zodat ook een volledig uitgeklapte lijst binnen beeld valt en de
/// 'Meer'-knop altijd aantikbaar is.
Future<void> _pumpHarness(WidgetTester tester, List<String> labels) async {
  tester.view.physicalSize = const Size(1200, 3000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(_Harness(labels: labels));
}

void main() {
  testWidgets('toont standaard 5 items met het resterende aantal op de knop', (
    tester,
  ) async {
    await _pumpHarness(tester, _labels(17));

    expect(find.text('item-0'), findsOneWidget);
    expect(find.text('item-4'), findsOneWidget);
    expect(find.text('item-5'), findsNothing);
    expect(find.text('Meer (nog 12)'), findsOneWidget);
  });

  testWidgets(
    'toont 10 items extra per klik en verbergt de knop aan het eind',
    (tester) async {
      await _pumpHarness(tester, _labels(17));

      await tester.tap(find.text('Meer (nog 12)'));
      await tester.pump();

      expect(find.text('item-14'), findsOneWidget);
      expect(find.text('Meer (nog 2)'), findsOneWidget);

      await tester.tap(find.text('Meer (nog 2)'));
      await tester.pump();

      expect(find.text('item-16'), findsOneWidget);
      expect(find.textContaining('Meer ('), findsNothing);
    },
  );

  testWidgets('toont geen knop bij vijf items of minder', (tester) async {
    await _pumpHarness(tester, _labels(5));

    expect(find.text('item-4'), findsOneWidget);
    expect(find.textContaining('Meer ('), findsNothing);
  });

  testWidgets('houdt de uitklapstand vast over een refresh heen', (
    tester,
  ) async {
    await _pumpHarness(tester, _labels(17));
    await tester.tap(find.text('Meer (nog 12)'));
    await tester.pump();

    final state = tester.state<_HarnessState>(find.byType(_Harness));
    state.refreshWith(['nieuw', ..._labels(17)]);
    await tester.pump();

    // Nieuw item bovenaan, teller onveranderd: nog steeds 15 zichtbaar.
    expect(find.text('nieuw'), findsOneWidget);
    expect(find.text('item-13'), findsOneWidget);
    expect(find.text('item-14'), findsNothing);
    expect(find.text('Meer (nog 3)'), findsOneWidget);
  });

  testWidgets('toont de doorlooptijd van cycli in de lijst', (tester) async {
    final now = DateTime(2026, 8, 8, 20, 0);
    final iterations = [
      {
        'createdAt': DateTime(2026, 8, 8, 16, 40).toIso8601String(),
        'startedAt': DateTime(2026, 8, 8, 16, 47).toIso8601String(),
        'completedAt': DateTime(2026, 8, 8, 19, 0).toIso8601String(),
      },
      {
        'createdAt': DateTime(2026, 8, 8, 19, 50).toIso8601String(),
        'startedAt': DateTime(2026, 8, 8, 19, 55, 48).toIso8601String(),
        'completedAt': null,
      },
      {
        'createdAt': DateTime(2026, 8, 8, 19, 13).toIso8601String(),
        'startedAt': null,
        'completedAt': null,
      },
    ];
    final labels = iterations.map((iteration) {
      final timing = iterationTiming(iteration, now: now);
      return [
        'gestart ${timing.startLabel}',
        if (timing.durationLabel != null) timing.durationLabel!,
      ].join(' · ');
    }).toList();

    await _pumpHarness(tester, labels);

    expect(find.text('gestart 08-08-2026 16:47 · 2u 13m'), findsOneWidget);
    expect(
      find.text('gestart 08-08-2026 19:55 · loopt nog: 4m 12s'),
      findsOneWidget,
    );
    expect(find.text('gestart 08-08-2026 19:13'), findsOneWidget);
  });
}
