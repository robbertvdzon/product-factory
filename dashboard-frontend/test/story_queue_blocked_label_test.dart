import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/classification.dart';
import 'package:product_factory_dashboard/main.dart';

/// Bouwt een [MockClient] die alle door `OverviewPage` gebruikte `/api/...`-endpoints beantwoordt
/// (conform de projectconventie dat widgettests géén echte HTTP-calls doen) en elk opgevraagde pad
/// logt, zodat een test het aantal calls per endpoint kan tellen (AC5: geen extra netwerkverzoek).
MockClient _buildMockClient(List<dynamic> stories, List<String> callLog) {
  return MockClient((request) async {
    callLog.add(request.url.path);
    switch (request.url.path) {
      case '/api/story-candidates':
        return http.Response(jsonEncode(stories), 200);
      case '/api/ai-catalog':
        return http.Response(jsonEncode(<String, dynamic>{}), 200);
      default:
        return http.Response(jsonEncode(<dynamic>[]), 200);
    }
  });
}

Map<String, dynamic> _story({
  required int id,
  required String title,
  bool blocked = false,
  String? blockedReason,
}) => {
  'id': id,
  'title': title,
  'productSlug': 'demo',
  'iterationSequenceNumber': 1,
  'description': 'omschrijving',
  'blocked': blocked,
  'blockedReason': blockedReason,
  'createdAt': DateTime(2026, 1, 1).toIso8601String(),
};

Future<void> _pumpDashboard(
  WidgetTester tester,
  List<dynamic> stories,
  List<String> callLog,
) async {
  tester.view.physicalSize = const Size(1200, 3000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  final mockClient = _buildMockClient(stories, callLog);
  await http.runWithClient(() async {
    await tester.pumpWidget(const ProductFactoryDashboard());
    await tester.pump();
    await tester.pump();
    await tester.tap(find.text('Beheer'));
    await tester.pump();
    await tester.pump();
  }, () => mockClient);
}

void main() {
  testWidgets(
    'toont het blokkeerlabel als de kandidaat blocked is met een reden',
    (tester) async {
      final callLog = <String>[];
      await _pumpDashboard(tester, [
        _story(
          id: 1,
          title: 'Kandidaat A',
          blocked: true,
          blockedReason: 'wacht op kandidaat 0',
        ),
      ], callLog);

      expect(find.text('Geblokkeerd: wacht op kandidaat 0'), findsOneWidget);
      expect(find.byIcon(Icons.block), findsOneWidget);
    },
  );

  testWidgets('toont geen blokkeerlabel als blocked false is', (tester) async {
    final callLog = <String>[];
    await _pumpDashboard(tester, [
      _story(id: 1, title: 'Kandidaat A', blocked: false),
    ], callLog);

    expect(find.textContaining('Geblokkeerd:'), findsNothing);
    expect(find.byIcon(Icons.block), findsNothing);
  });

  testWidgets('toont geen blokkeerlabel als blockedReason leeg/ontbrekend is', (
    tester,
  ) async {
    final callLog = <String>[];
    await _pumpDashboard(tester, [
      _story(id: 1, title: 'Kandidaat A', blocked: true, blockedReason: ''),
      _story(id: 2, title: 'Kandidaat B', blocked: true),
    ], callLog);

    expect(find.textContaining('Geblokkeerd:'), findsNothing);
    expect(find.byIcon(Icons.block), findsNothing);
  });

  testWidgets(
    'blokkeerlabel is opvraagbaar via de semantics-tree van de kaart',
    (tester) async {
      final handle = tester.ensureSemantics();
      final callLog = <String>[];
      await _pumpDashboard(tester, [
        _story(
          id: 1,
          title: 'Kandidaat A',
          blocked: true,
          blockedReason: 'wacht op kandidaat 0',
        ),
      ], callLog);

      final mergeSemanticsFinder = find.ancestor(
        of: find.text('Geblokkeerd: wacht op kandidaat 0'),
        matching: find.byType(MergeSemantics),
      );
      expect(mergeSemanticsFinder, findsOneWidget);

      final label = tester
          .getSemantics(mergeSemanticsFinder)
          .getSemanticsData()
          .label;
      expect(label, contains('Geblokkeerd: wacht op kandidaat 0'));
      expect(label, contains('Kandidaat A'));

      handle.dispose();
    },
  );

  testWidgets(
    'bestaande kaartelementen en tap-gedrag blijven werken naast het label',
    (tester) async {
      final callLog = <String>[];
      await _pumpDashboard(tester, [
        _story(
          id: 1,
          title: 'Kandidaat A',
          blocked: true,
          blockedReason: 'wacht op kandidaat 0',
        ),
      ], callLog);

      expect(find.text('Kandidaat A'), findsOneWidget);
      expect(find.textContaining('demo'), findsWidgets);
      expect(find.byIcon(Icons.chevron_right), findsWidgets);

      await tester.tap(find.text('Kandidaat A'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('omschrijving'), findsOneWidget);
      expect(find.text('Sluiten'), findsOneWidget);
    },
  );

  testWidgets(
    'het aantal API-calls neemt niet toe bij het renderen van dezelfde kaartenlijst',
    (tester) async {
      final callLog = <String>[];
      await _pumpDashboard(tester, [
        _story(
          id: 1,
          title: 'Kandidaat A',
          blocked: true,
          blockedReason: 'wacht op kandidaat 0',
        ),
      ], callLog);

      // Navigeren naar Beheer hergebruikt de bestaande dashboardbronnen; het label
      // veroorzaakt dus geen extra call en leest alleen 'blocked'/'blockedReason'.
      expect(callLog.where((path) => path == '/api/products').length, 1);
      expect(
        callLog.where((path) => path == '/api/story-candidates').length,
        1,
      );
      expect(
        callLog.where((path) => path == '/api/workspace/publications').length,
        1,
      );
      expect(
        callLog.where((path) => path == '/api/shadow-iterations').length,
        1,
      );
      expect(
        callLog.where((path) => path == '/api/autonomy/deliveries').length,
        1,
      );
      expect(
        callLog.where((path) => path == '/api/autonomy/human-actions').length,
        1,
      );
      expect(callLog.where((path) => path == '/api/ai-catalog').length, 1);
    },
  );

  test('kGuardrailConflict-paar haalt WCAG AA-contrast (>= 4.5:1)', () {
    final colors = kClassificationColors[kGuardrailConflict]!;
    expect(
      contrastRatio(colors.background, colors.foreground),
      greaterThanOrEqualTo(4.5),
    );
  });
}
