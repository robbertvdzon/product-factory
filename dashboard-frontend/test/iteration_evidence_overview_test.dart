import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:product_factory_dashboard/product_scope.dart';
import 'package:shared_preferences/shared_preferences.dart';

Map<String, dynamic> _iteration({
  required String id,
  required String productSlug,
  required int sequence,
  required String status,
  String? verdict,
  String? outcomeReason,
}) => {
  'id': id,
  'productSlug': productSlug,
  'sequenceNumber': sequence,
  'mode': 'autonomous',
  'status': status,
  'currentRole': status == 'RUNNING' ? 'critic' : null,
  'criticVerdict': verdict,
  'outcomeReason': outcomeReason,
  'errorMessage': null,
  'candidateCount': 8,
  'acceptedCandidateCount': 4,
  'revisionRounds': 0,
  'workspacePullRequestUrl': null,
  'createdAt': '2026-08-12T10:00:00Z',
  'startedAt': '2026-08-12T10:30:00Z',
  'completedAt': status == 'RUNNING' ? null : '2026-08-12T10:31:00Z',
  'prompt': 'Ruwe prompt die niet in het overzicht hoort',
};

final _terminal = _iteration(
  id: 'pf-terminal',
  productSlug: 'product-factory',
  sequence: 41,
  status: 'ACCEPTED',
  verdict: 'ACCEPT',
  outcomeReason: 'ACCEPT',
);

final _iterations = <Map<String, dynamic>>[
  _terminal,
  _iteration(
    id: 'pf-queued',
    productSlug: 'product-factory',
    sequence: 38,
    status: 'QUEUED',
  ),
  _iteration(
    id: 'pf-running',
    productSlug: 'product-factory',
    sequence: 40,
    status: 'RUNNING',
  ),
  _iteration(
    id: 'other-terminal',
    productSlug: 'ander-product',
    sequence: 39,
    status: 'REJECTED',
    verdict: 'REJECT',
    outcomeReason: 'REJECT',
  ),
  _iteration(
    id: 'other-running',
    productSlug: 'ander-product',
    sequence: 37,
    status: 'RUNNING',
  ),
];

http.Response _json(Object body, {int status = 200}) =>
    http.Response(jsonEncode(body), status);

MockClient _client(List<String> calls, {bool ambiguous = false}) =>
    MockClient((request) async {
      calls.add(request.url.path);
      switch (request.url.path) {
        case '/api/products':
          return _json([
            {'slug': 'product-factory', 'name': 'Product Factory'},
            {'slug': 'ander-product', 'name': 'Ander product'},
          ]);
        case '/api/shadow-iterations':
          return _json(
            ambiguous
                ? [
                    _terminal,
                    _iteration(
                      id: 'pf-terminal',
                      productSlug: 'product-factory',
                      sequence: 42,
                      status: 'ACCEPTED',
                      verdict: 'ACCEPT',
                      outcomeReason: 'ACCEPT',
                    ),
                  ]
                : _iterations,
          );
        case '/api/story-candidates':
          return _json([
            {
              'id': 1,
              'productSlug': 'product-factory',
              'iterationSequenceNumber': 41,
              'title': 'Interne kandidaat telt niet als opbrengst',
              'status': 'ACCEPTED',
            },
          ]);
        case '/api/autonomy/deliveries':
          return _json([
            {
              'id': 1,
              'productSlug': 'product-factory',
              'iterationId': 'pf-terminal',
              'title': 'Exact gekoppelde levering',
              'status': 'DONE',
            },
            {
              'id': 2,
              'productSlug': 'ander-product',
              'iterationId': 'pf-terminal',
              'title': 'Kruisproduct telt niet',
              'status': 'DONE',
            },
            {
              'id': 3,
              'productSlug': 'product-factory',
              'iterationId': 41,
              'title': 'Verkeerd type telt niet',
              'status': 'DONE',
            },
            {
              'id': 4,
              'productSlug': 'product-factory',
              'title': 'Ontbrekende koppeling telt niet',
              'status': 'DONE',
            },
          ]);
        case '/api/shadow-iterations/pf-terminal':
          return _json(_terminal);
        case '/api/shadow-iterations/pf-terminal/steps':
        case '/api/shadow-iterations/pf-terminal/artifacts':
          return _json(<dynamic>[]);
        case '/api/ai-catalog':
          return _json(<String, dynamic>{});
        default:
          return _json(<dynamic>[]);
      }
    });

Future<void> _pumpDashboard(WidgetTester tester) async {
  tester.view.physicalSize = const Size(1200, 4000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(const ProductFactoryDashboard());
  for (var pump = 0; pump < 5; pump++) {
    await tester.pump();
  }
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets(
    'overzicht gebruikt terminale bewijsregels en veilige actieve kaarten',
    (tester) async {
      final calls = <String>[];
      await http.runWithClient(() async {
        await _pumpDashboard(tester);

        expect(find.byType(IterationEvidenceRow), findsOneWidget);
        expect(find.byType(IterationProgressCard), findsNWidgets(2));
        expect(find.byType(IterationCycleCard), findsNothing);
        expect(
          find.descendant(
            of: find.byType(IterationEvidenceRow),
            matching: find.text('Gekoppelde opbrengst: 1', findRichText: true),
          ),
          findsOneWidget,
        );
        expect(
          find.descendant(
            of: find.byType(IterationEvidenceRow),
            matching: find.textContaining('Interne kandidaat'),
          ),
          findsNothing,
        );

        final runningCard = find.ancestor(
          of: find.text('product-factory · iteratie 40'),
          matching: find.byType(IterationProgressCard),
        );
        expect(runningCard, findsOneWidget);
        expect(
          find.descendant(
            of: runningCard,
            matching: find.text('Status: Bezig', findRichText: true),
          ),
          findsOneWidget,
        );
        expect(
          find.descendant(
            of: runningCard,
            matching: find.text('Huidige stap: Criticus', findRichText: true),
          ),
          findsOneWidget,
        );
        final queuedCard = find.ancestor(
          of: find.text('product-factory · iteratie 38'),
          matching: find.byType(IterationProgressCard),
        );
        expect(queuedCard, findsOneWidget);
        expect(
          find.descendant(
            of: queuedCard,
            matching: find.text('Status: In wachtrij', findRichText: true),
          ),
          findsOneWidget,
        );
        expect(find.textContaining('ander-product · iteratie'), findsNothing);

        await tester.tap(
          find.descendant(
            of: find.byType(IterationEvidenceRow),
            matching: find.text('Bekijk cyclusdetail'),
          ),
        );
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 300));
        expect(find.text('Productcyclus 41'), findsOneWidget);
        expect(
          calls.where(
            (path) =>
                path == '/api/shadow-iterations/pf-terminal' ||
                path == '/api/shadow-iterations/pf-terminal/steps' ||
                path == '/api/shadow-iterations/pf-terminal/artifacts',
          ),
          hasLength(3),
        );
        expect(
          find.text('Ruwe prompt die niet in het overzicht hoort'),
          findsNothing,
        );
      }, () => _client(calls));
    },
  );

  testWidgets(
    'een ander product gebruikt exact hetzelfde terminale bewijscomponent',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        activeProductSlugPreferenceKey: 'ander-product',
      });
      final calls = <String>[];
      await http.runWithClient(() async {
        await _pumpDashboard(tester);

        expect(find.byType(IterationEvidenceRow), findsOneWidget);
        expect(find.byType(IterationProgressCard), findsOneWidget);
        expect(find.byType(IterationCycleCard), findsNothing);
        expect(
          find.descendant(
            of: find.byType(IterationEvidenceRow),
            matching: find.text(
              'Cyclusuitkomst: richting-verworpen',
              findRichText: true,
            ),
          ),
          findsOneWidget,
        );
        expect(
          find.bySemanticsLabel(
            'Cyclusgeschiedenis voor product ander-product',
          ),
          findsOneWidget,
        );
      }, () => _client(calls));
    },
  );

  testWidgets('ambigue cyclus-id levert voor geen bewijsregel opbrengst op', (
    tester,
  ) async {
    final calls = <String>[];
    await http.runWithClient(() async {
      await _pumpDashboard(tester);

      expect(find.byType(IterationEvidenceRow), findsNWidgets(2));
      expect(
        find.text('Gekoppelde opbrengst: 0', findRichText: true),
        findsNWidgets(2),
      );
      expect(
        find.text('Gekoppelde opbrengst: 1', findRichText: true),
        findsNothing,
      );
    }, () => _client(calls, ambiguous: true));
  });
}
