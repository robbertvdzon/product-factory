import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/classification.dart';

/// Bouwt dezelfde rijstructuur na als de iteratierij in `main.dart` ('Productcycli en
/// onderzoekssessies'): een titel plus additief een [ClassificationBadge], zonder de subtitle,
/// trailing of onTap van de echte rij aan te raken (die blijven ongewijzigd, zie main.dart).
class _IterationListHarness extends StatelessWidget {
  const _IterationListHarness({required this.iterations});

  final List<Map<String, dynamic>> iterations;

  @override
  Widget build(BuildContext context) => MaterialApp(
    home: Scaffold(
      body: ListView(
        children: [
          for (final iteration in iterations)
            Card(
              child: ListTile(
                title: Wrap(
                  spacing: 8,
                  children: [
                    Text('iteratie ${iteration['sequenceNumber']}'),
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
        ],
      ),
    ),
  );
}

void main() {
  final sampleIterations = <Map<String, dynamic>>[
    {'sequenceNumber': 1, 'status': 'ACCEPTED', 'criticVerdict': 'ACCEPT'},
    {
      'sequenceNumber': 2,
      'status': 'NEEDS_REVISION',
      'criticVerdict': 'REVISE',
    },
    {'sequenceNumber': 3, 'status': 'REJECTED', 'criticVerdict': 'REJECT'},
    {
      'sequenceNumber': 4,
      'status': 'FAILED',
      'errorMessage': 'Workspace-publicatie tijdelijk niet beschikbaar.',
    },
    {'sequenceNumber': 5, 'status': 'QUEUED'},
    {'sequenceNumber': 6, 'status': 'RUNNING'},
    {'sequenceNumber': 7, 'status': 'ONVOORZIEN_TOEKOMSTIG_LABEL'},
    {'sequenceNumber': 8, 'status': null},
  ];

  testWidgets(
    'elke iteratierij toont precies één van de vijf toegestane badge-teksten, nooit een lege statuscel',
    (tester) async {
      // Groot testvenster zodat alle 8 rijen binnen de sliver-cache-extent vallen; anders bouwt
      // ListView geen Elements voor rijen buiten beeld en mist find.text() ze.
      tester.view.physicalSize = const Size(1200, 3000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        _IterationListHarness(iterations: sampleIterations),
      );
      await tester.pump();

      for (final classification in kIterationClassifications) {
        final matches = find.text(classification);
        // Sommige classificaties komen in de sample meerdere keren voor (bijv. de fallback voor
        // QUEUED/RUNNING); tel daarom alleen of het aantal treffers overeenkomt met het verwachte
        // aantal rijen per classificatie in plaats van 'findsOneWidget' te eisen.
        final expectedCount = sampleIterations
            .where(
              (iteration) =>
                  classifyIterationOutcome(
                    status: iteration['status'] as String?,
                    criticVerdict: iteration['criticVerdict'] as String?,
                    errorMessage: iteration['errorMessage'] as String?,
                  ) ==
                  classification,
            )
            .length;
        expect(matches, findsNWidgets(expectedCount));
      }

      // Geen enkele rij toont vrije tekst of een andere waarde dan de vijf toegestane badgeteksten,
      // en dus ook nooit een lege statuscel.
      final badgeFinder = find.byType(ClassificationBadge);
      expect(badgeFinder, findsNWidgets(sampleIterations.length));
      for (final element in badgeFinder.evaluate()) {
        final badge = element.widget as ClassificationBadge;
        expect(kIterationClassifications, contains(badge.classification));
      }
    },
  );

  testWidgets(
    'de badgetekst is via Semantics programmatisch leesbaar, niet uitsluitend via kleur',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: ClassificationBadge(classification: kRichtingGekozen),
          ),
        ),
      );
      await tester.pump();

      final semantics = tester.getSemantics(find.byType(ClassificationBadge));
      expect(semantics.label, contains(kRichtingGekozen));
      expect(find.text(kRichtingGekozen), findsOneWidget);
    },
  );
}
