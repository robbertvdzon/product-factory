import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/memory_history_dialog.dart';

class _FakeMemoryApi extends DashboardApi {
  _FakeMemoryApi() : super('http://localhost', null);

  String? requestedAsOf;

  @override
  Future<List<dynamic>> memory(String slug, {String? asOf}) async {
    requestedAsOf = asOf;
    return [
      {
        'id': asOf == null ? 2 : 1,
        'productSlug': slug,
        'title': 'Database',
        'content': asOf == null ? 'Gebruik MongoDB.' : 'Gebruik PostgreSQL.',
        'createdAt': asOf == null
            ? '2026-05-01T09:00:00Z'
            : '2026-03-01T10:00:00Z',
      },
    ];
  }

  @override
  Future<List<dynamic>> memoryHistory(String slug) async => [
    {
      'id': 2,
      'productSlug': slug,
      'rootMemoryId': 1,
      'versionNumber': 2,
      'title': 'Database',
      'content': 'Gebruik MongoDB.',
      'status': 'ACTIVE',
      'createdAt': '2026-05-01T09:00:00Z',
      'effectiveUntil': null,
      'createdBy': 'meeting:migration',
      'changeReason': 'Database gemigreerd.',
    },
    {
      'id': 1,
      'productSlug': slug,
      'rootMemoryId': 1,
      'versionNumber': 1,
      'title': 'Database',
      'content': 'Gebruik PostgreSQL.',
      'status': 'SUPERSEDED',
      'createdAt': '2026-03-01T10:00:00Z',
      'effectiveUntil': '2026-05-01T09:00:00Z',
      'createdBy': 'system',
      'retirementReason': 'Database gemigreerd.',
      'retiredBy': 'meeting:migration',
    },
  ];
}

void main() {
  testWidgets(
    'toont alleen actuele memory bovenaan en de volledige versiegeschiedenis',
    (tester) async {
      tester.view.physicalSize = const Size(1200, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final api = _FakeMemoryApi();

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: MemoryHistoryDialog(
              api: api,
              productSlug: 'demo',
              productName: 'Demo',
              today: DateTime(2026, 8, 16),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Actief geheugen'), findsOneWidget);
      expect(find.text('Gebruik MongoDB.'), findsOneWidget);
      expect(find.text('Database · versie 2'), findsOneWidget);
      expect(find.text('Database · versie 1'), findsOneWidget);
      expect(api.requestedAsOf, isNull);

      await tester.tap(find.byKey(const Key('memory-date-picker')));
      await tester.pumpAndSettle();
      expect(find.text('Kies een historische peildatum'), findsOneWidget);
      await tester.tap(find.text('1').last);
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
      expect(api.requestedAsOf, '2026-08-01');
      expect(
        find.text('Historische momentopname · 2026-08-01'),
        findsOneWidget,
      );
      expect(
        find.text(
          'Historische inhoud is niet bindend voor huidige agenttaken.',
        ),
        findsOneWidget,
      );
      expect(find.text('Gebruik PostgreSQL.'), findsOneWidget);

      await tester.tap(find.text('Database · versie 1'));
      await tester.pumpAndSettle();
      expect(
        find.text('Reden einde geldigheid: Database gemigreerd.'),
        findsOneWidget,
      );
      expect(find.text('Beëindigd door meeting:migration'), findsOneWidget);

      await tester.pumpWidget(const SizedBox());
    },
  );
}
