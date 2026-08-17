import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/config.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

http.Response _json(Object body) => http.Response(jsonEncode(body), 200);

MockClient _emptyDashboardClient() => MockClient((request) async {
  if (request.url.path == '/api/ai-catalog') {
    return _json(<String, dynamic>{});
  }
  return _json(<dynamic>[]);
});

Future<void> _pumpOverview(
  WidgetTester tester, {
  required bool acceptanceDataset,
  Size size = const Size(1200, 2400),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      home: OverviewPage(session: null, acceptanceDataset: acceptanceDataset),
    ),
  );
  for (var pump = 0; pump < 5; pump++) {
    await tester.pump();
  }
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'productie- en previewbuild hebben de acceptatiemarkering standaard uit',
    () {
      expect(AppConfig.acceptanceDataset, isFalse);
    },
  );

  testWidgets('acceptatiemelding staat semantisch voor de metrics', (
    tester,
  ) async {
    await http.runWithClient(() async {
      await _pumpOverview(tester, acceptanceDataset: true);

      final notice = find.byType(AcceptanceDatasetNotice);
      expect(notice, findsOneWidget);
      expect(find.text('Synthetische acceptatiedata'), findsOneWidget);
      for (final text in [
        '1 actief',
        '3 terminaal',
        'expliciet',
        'afgeleid',
        'onbekend',
      ]) {
        expect(find.textContaining(text), findsOneWidget);
      }
      expect(
        tester.getTopLeft(notice).dy,
        lessThan(tester.getTopLeft(find.byType(MetricCard).first).dy),
      );

      final semantics = tester.getSemantics(notice).getSemanticsData().label;
      expect(semantics, contains('Synthetische acceptatiedata'));
      expect(semantics, contains('1 actief'));
      expect(semantics, contains('3 terminaal'));
    }, _emptyDashboardClient);
  });

  testWidgets('melding ontbreekt buiten de acceptatievariant', (tester) async {
    await http.runWithClient(() async {
      await _pumpOverview(tester, acceptanceDataset: false);
      expect(find.byType(AcceptanceDatasetNotice), findsNothing);
      expect(find.text('Synthetische acceptatiedata'), findsNothing);
    }, _emptyDashboardClient);
  });

  testWidgets(
    'mobiel zonder producten behoudt acceptatiemelding en vijf ingeklapte metrieken',
    (tester) async {
      await http.runWithClient(() async {
        await _pumpOverview(
          tester,
          acceptanceDataset: true,
          size: const Size(320, 900),
        );

        expect(
          find.byKey(const ValueKey('empty-product-scope')),
          findsOneWidget,
        );
        expect(find.byType(OperationalSummary), findsOneWidget);
        expect(find.byType(AcceptanceDatasetNotice), findsOneWidget);
        expect(find.byType(MetricCard), findsNothing);

        await tester.tap(find.text('Operationele samenvatting'));
        await tester.pump();
        expect(find.byType(MetricCard), findsNWidgets(5));
        for (final label in [
          'Producten',
          'Interne storykandidaten',
          'Workspace-publicaties',
          'Shadow-iteraties',
          'Software Factory-stories',
        ]) {
          expect(find.text(label), findsOneWidget);
        }
      }, _emptyDashboardClient);
    },
  );

  testWidgets(
    'melding blijft bruikbaar op 320 CSS-pixels en 200 procent tekst',
    (tester) async {
      tester.view.physicalSize = const Size(320, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(
              size: Size(320, 900),
              textScaler: TextScaler.linear(2),
            ),
            child: const Scaffold(
              body: SingleChildScrollView(
                padding: EdgeInsets.all(16),
                child: AcceptanceDatasetNotice(),
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      final rect = tester.getRect(find.byType(AcceptanceDatasetNotice));
      expect(rect.left, greaterThanOrEqualTo(0));
      expect(rect.right, lessThanOrEqualTo(320));
      expect(find.text('Synthetische acceptatiedata'), findsOneWidget);
      expect(find.textContaining('1 actief'), findsOneWidget);
    },
  );
}
