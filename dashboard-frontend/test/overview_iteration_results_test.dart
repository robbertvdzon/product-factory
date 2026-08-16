import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

Map<String, dynamic> _iteration(int sequence) => {
  'id': 'iteration-$sequence',
  'productSlug': 'demo',
  'sequenceNumber': sequence,
  'mode': 'autonomous',
  'status': 'ACCEPTED',
  'currentRole': null,
  'criticVerdict': 'ACCEPT',
  'outcomeReason': 'ACCEPT',
  'errorMessage': null,
  'candidateCount': 99,
  'acceptedCandidateCount': 50,
  'revisionRounds': 0,
  'workspacePullRequestUrl': null,
  'createdAt': '2026-08-${sequence.toString().padLeft(2, '0')}T10:00:00Z',
  'startedAt': '2026-08-${sequence.toString().padLeft(2, '0')}T10:00:00Z',
  'completedAt': '2026-08-${sequence.toString().padLeft(2, '0')}T10:01:00Z',
};

http.Response _json(Object body, {int status = 200}) =>
    http.Response(jsonEncode(body), status);

MockClient _client({
  Completer<http.Response>? iterationResponse,
  Completer<http.Response>? candidateResponse,
  Completer<http.Response>? deliveryResponse,
  bool duplicateIterations = false,
}) {
  final iterations = [
    for (var sequence = 1; sequence <= 6; sequence++) _iteration(sequence),
  ];
  if (duplicateIterations) {
    iterations[4] = Map<String, dynamic>.of(iterations[5]);
  }
  return MockClient((request) async {
    switch (request.url.path) {
      case '/api/products':
        return _json([
          {'slug': 'demo', 'name': 'Demo'},
        ]);
      case '/api/shadow-iterations':
        if (iterationResponse != null) return iterationResponse.future;
        return _json(iterations);
      case '/api/story-candidates':
        if (candidateResponse != null) return candidateResponse.future;
        return _json([
          {
            'id': 1,
            'productSlug': 'demo',
            'iterationSequenceNumber': 1,
            'title': 'Kandidaat van de aanvankelijk verborgen cyclus',
            'status': 'ACCEPTED',
          },
          {
            'id': 2,
            'productSlug': 'demo',
            'iterationSequenceNumber': 999,
            'title': 'Niet koppelbare kandidaat',
            'status': 'OPEN',
          },
        ]);
      case '/api/autonomy/deliveries':
        if (deliveryResponse != null) return deliveryResponse.future;
        return _json([
          {
            'id': 3,
            'candidateId': 3,
            'productSlug': 'ander-product',
            'iterationId': 'iteration-2',
            'title': 'Kruisproductlevering',
            'status': 'DONE',
            'createdAt': '2026-08-12T10:00:00Z',
          },
          if (duplicateIterations)
            {
              'id': 4,
              'candidateId': 4,
              'productSlug': 'demo',
              'iterationId': 'iteration-6',
              'title': 'Levering met ambigue cyclus-id',
              'status': 'DONE',
              'createdAt': '2026-08-12T11:00:00Z',
            },
        ]);
      case '/api/ai-catalog':
        return _json(<String, dynamic>{});
      default:
        return _json(<dynamic>[]);
    }
  });
}

Future<void> _pumpDashboard(WidgetTester tester) async {
  tester.view.physicalSize = const Size(1400, 7000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(const ProductFactoryDashboard());
  for (var pump = 0; pump < 5; pump++) {
    await tester.pump();
  }
}

Future<void> _openSection(WidgetTester tester, String label) async {
  final target = find.text(label);
  await tester.ensureVisible(target);
  await tester.pump();
  await tester.tap(target);
  await tester.pump();
}

String _metricValue(WidgetTester tester, String label) {
  final card = find.ancestor(
    of: find.text(label),
    matching: find.byType(MetricCard),
  );
  return tester
      .widgetList<Text>(find.descendant(of: card, matching: find.byType(Text)))
      .map((text) => text.data)
      .whereType<String>()
      .firstWhere((text) => text != label);
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets(
    'volledig dubbele cycli renderen defensief met unieke kaartkeys',
    (tester) async {
      await http.runWithClient(() async {
        await _pumpDashboard(tester);
        await _openSection(tester, 'Productsessies');

        expect(tester.takeException(), isNull);
        expect(find.text('demo · iteratie 6'), findsNWidgets(2));
        expect(find.byType(IterationEvidenceRow), findsNWidgets(5));
        expect(find.byType(IterationCycleCard), findsNothing);
        expect(
          find.byKey(const ValueKey('unlinked-iteration-results')),
          findsNothing,
        );
        expect(
          find.text('Gekoppelde opbrengst: 0', findRichText: true),
          findsNWidgets(5),
        );
        await tester.pumpWidget(const SizedBox.shrink());
      }, () => _client(duplicateIterations: true));
    },
  );

  testWidgets(
    'scope sluit niet-koppelbare records uit en lijstbeperking overleeft verversen',
    (tester) async {
      await http.runWithClient(() async {
        await _pumpDashboard(tester);
        await _openSection(tester, 'Productsessies');

        expect(
          find.byKey(const ValueKey('unlinked-iteration-results')),
          findsNothing,
        );
        final hiddenCycleTitle = find.descendant(
          of: find.byType(IterationEvidenceRow),
          matching: find.text('demo · iteratie 1'),
        );
        expect(hiddenCycleTitle, findsNothing);
        expect(
          find.text('Gekoppelde opbrengst: 0', findRichText: true),
          findsNWidgets(5),
        );

        await tester.tap(find.text('Meer (nog 1)'));
        await tester.pump();
        expect(hiddenCycleTitle, findsOneWidget);
        expect(find.byType(IterationEvidenceRow), findsNWidgets(6));
        expect(find.text('Toon opbrengst'), findsNothing);

        await tester.tap(find.byTooltip('Vernieuwen'));
        for (var pump = 0; pump < 5; pump++) {
          await tester.pump();
        }
        expect(find.byType(IterationEvidenceRow), findsNWidgets(6));
        expect(hiddenCycleTitle, findsOneWidget);
        await tester.pumpWidget(const SizedBox.shrink());
      }, () => _client());
    },
  );

  testWidgets(
    'vertraagde en mislukte kandidaatbron wordt nooit als nul of compleet totaal getoond',
    (tester) async {
      final candidateResponse = Completer<http.Response>();
      await http.runWithClient(() async {
        await _pumpDashboard(tester);

        expect(find.textContaining('Interne kandidaten:'), findsNothing);
        expect(_metricValue(tester, 'Interne storykandidaten'), 'Laden…');
        expect(_metricValue(tester, 'Software Factory-stories'), 'Laden…');
        await _openSection(tester, 'Productsessies');
        expect(
          find.text('Gekoppelde opbrengst: 0', findRichText: true),
          findsNWidgets(5),
        );
        expect(
          find.textContaining(
            'Niet aan een cyclus te koppelen in geladen gegevens:',
          ),
          findsNothing,
        );

        candidateResponse.complete(
          _json({'error': 'synthetisch'}, status: 500),
        );
        await tester.pump();
        await tester.pump();

        expect(find.textContaining('Interne kandidaten:'), findsNothing);
        await _openSection(tester, 'Overzicht');
        expect(
          _metricValue(tester, 'Interne storykandidaten'),
          'Niet beschikbaar',
        );
        expect(
          _metricValue(tester, 'Software Factory-stories'),
          'Niet beschikbaar',
        );
        expect(
          find.text('Gekoppelde opbrengst: 0', findRichText: true),
          findsNothing,
        );
        await _openSection(tester, 'Stories');
        expect(
          find.text('Gekoppelde stories zijn niet beschikbaar.'),
          findsOneWidget,
        );
        expect(
          find.textContaining(
            'Niet aan een cyclus te koppelen in geladen gegevens:',
          ),
          findsNothing,
        );
        await tester.pumpWidget(const SizedBox.shrink());
      }, () => _client(candidateResponse: candidateResponse));
    },
  );

  testWidgets(
    'geladen kandidaten wachten voor hun scopetelling op de cyclusbron',
    (tester) async {
      final iterationResponse = Completer<http.Response>();
      await http.runWithClient(() async {
        await _pumpDashboard(tester);

        expect(_metricValue(tester, 'Interne storykandidaten'), 'Laden…');
        await _openSection(tester, 'Stories');
        expect(
          find.text('Nog geen eenduidig gekoppelde stories'),
          findsNothing,
        );

        iterationResponse.complete(
          _json({'error': 'synthetisch'}, status: 500),
        );
        await tester.pump();
        await tester.pump();

        await _openSection(tester, 'Overzicht');
        expect(
          _metricValue(tester, 'Interne storykandidaten'),
          'Niet beschikbaar',
        );
        await _openSection(tester, 'Stories');
        expect(
          find.text('Gekoppelde stories zijn niet beschikbaar.'),
          findsOneWidget,
        );
        await tester.pumpWidget(const SizedBox.shrink());
      }, () => _client(iterationResponse: iterationResponse));
    },
  );

  testWidgets(
    'geladen kandidaten wachten voor hun leveringstelling op de leveringsbron',
    (tester) async {
      final deliveryResponse = Completer<http.Response>();
      await http.runWithClient(() async {
        await _pumpDashboard(tester);

        expect(_metricValue(tester, 'Software Factory-stories'), 'Laden…');

        deliveryResponse.complete(_json({'error': 'synthetisch'}, status: 500));
        await tester.pump();
        await tester.pump();

        expect(
          _metricValue(tester, 'Software Factory-stories'),
          'Niet beschikbaar',
        );
        await tester.pumpWidget(const SizedBox.shrink());
      }, () => _client(deliveryResponse: deliveryResponse));
    },
  );
}
