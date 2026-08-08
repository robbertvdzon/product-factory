import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/classification.dart';
import 'package:product_factory_dashboard/main.dart';

/// Bouwt dezelfde rijstructuur na als de iteratierij in `main.dart` ('Productcycli en
/// onderzoekssessies'): status QUEUED/RUNNING toont [IterationProgressIndicator], elke andere
/// status toont exact één [ClassificationBadge] (zie main.dart, `_limitedSection('iterations', ...)`
/// rond regel 558-590).
class _IterationListHarness extends StatelessWidget {
  const _IterationListHarness({required this.iterations});

  final List<Map<String, dynamic>> iterations;

  @override
  Widget build(BuildContext context) => MaterialApp(
    home: Scaffold(
      body: ListView(
        children: [
          for (final iteration in iterations)
            Builder(
              builder: (context) {
                final status = '${iteration['status']}';
                final running = status == 'QUEUED' || status == 'RUNNING';
                final classification = classifyIterationOutcome(
                  status: iteration['status'] as String?,
                  criticVerdict: iteration['criticVerdict'] as String?,
                  errorMessage: iteration['errorMessage'] as String?,
                );
                return Card(
                  child: ListTile(
                    title: Wrap(
                      spacing: 8,
                      children: [
                        Text('iteratie ${iteration['sequenceNumber']}'),
                        running
                            ? const IterationProgressIndicator()
                            : ClassificationBadge(
                                classification: classification,
                              ),
                      ],
                    ),
                  ),
                );
              },
            ),
        ],
      ),
    ),
  );
}

void main() {
  final lopendeIteraties = <Map<String, dynamic>>[
    {'sequenceNumber': 1, 'status': 'QUEUED'},
    {'sequenceNumber': 2, 'status': 'RUNNING'},
  ];

  final afgerondeIteraties = <Map<String, dynamic>>[
    {'sequenceNumber': 3, 'status': 'ACCEPTED', 'criticVerdict': 'ACCEPT'},
    {
      'sequenceNumber': 4,
      'status': 'NEEDS_REVISION',
      'criticVerdict': 'REVISE',
    },
    {'sequenceNumber': 5, 'status': 'REJECTED', 'criticVerdict': 'REJECT'},
    {
      'sequenceNumber': 6,
      'status': 'FAILED',
      'errorMessage': 'Workspace-publicatie tijdelijk niet beschikbaar.',
    },
  ];

  testWidgets(
    'lopende iteraties (QUEUED/RUNNING) tonen uitsluitend een voortgangsindicator, nooit een '
    'conclusion-badge; afgeronde iteraties tonen uitsluitend exact één conclusion-badge, nooit '
    'een voortgangsindicator',
    (tester) async {
      // Groot testvenster zodat alle rijen binnen de sliver-cache-extent vallen; anders bouwt
      // ListView geen Elements voor rijen buiten beeld en mist find.byType() ze.
      tester.view.physicalSize = const Size(1200, 3000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      final iterations = [...lopendeIteraties, ...afgerondeIteraties];
      await tester.pumpWidget(_IterationListHarness(iterations: iterations));
      await tester.pump();

      expect(
        find.byType(IterationProgressIndicator),
        findsNWidgets(lopendeIteraties.length),
      );
      expect(
        find.byType(ClassificationBadge),
        findsNWidgets(afgerondeIteraties.length),
      );

      for (final iteration in lopendeIteraties) {
        final row = find.ancestor(
          of: find.text('iteratie ${iteration['sequenceNumber']}'),
          matching: find.byType(Card),
        );
        expect(
          find.descendant(
            of: row,
            matching: find.byType(IterationProgressIndicator),
          ),
          findsOneWidget,
        );
        expect(
          find.descendant(of: row, matching: find.byType(ClassificationBadge)),
          findsNothing,
        );
      }

      for (final iteration in afgerondeIteraties) {
        final row = find.ancestor(
          of: find.text('iteratie ${iteration['sequenceNumber']}'),
          matching: find.byType(Card),
        );
        expect(
          find.descendant(of: row, matching: find.byType(ClassificationBadge)),
          findsOneWidget,
        );
        expect(
          find.descendant(
            of: row,
            matching: find.byType(IterationProgressIndicator),
          ),
          findsNothing,
        );
      }
    },
  );

  testWidgets(
    'de voortgangsindicator gebruikt liveRegion (aria-live="polite"-equivalent), zodat een '
    'schermlezer de statuswijziging meekrijgt',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(home: Scaffold(body: IterationProgressIndicator())),
      );
      await tester.pump();

      final semantics = tester.getSemantics(
        find.byType(IterationProgressIndicator),
      );
      expect(semantics.flagsCollection.isLiveRegion, isTrue);
    },
  );
}
