import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/main.dart';

Map<String, dynamic> _product({String status = 'active'}) => {
  'slug': 'demo',
  'name': 'Demo product',
  'status': status,
  'mission': 'Demo missie tekst uniek 12345',
  'softwareFactoryProjectKey': 'DEMOPROJECT',
  'targetRepositoryName': 'org/demo-repo',
  'workspaceOwnership': 'product-factory',
  'developmentMode': 'autonomous',
  'maxStoriesPerCycle': 3,
  'wipLimit': 2,
  'aiProvider': 'openai',
  'aiModel': 'gpt-4o-mini',
  'iterationTimes': ['03:00', '15:00'],
  'roadmapSchedule': [
    {'dayOfWeek': 'MONDAY', 'time': '10:00'},
  ],
  'meetingRequestedTopics': <String>[],
  'meetingRequestedAt': null,
};

Map<String, dynamic> _aiCatalog() => {
  'openai': ['gpt-4o-mini', 'gpt-4o'],
  'anthropic': ['claude-3-5-sonnet'],
};

/// Bouwt een [MockClient] die alle door `OverviewPage` gebruikte `/api/...`-endpoints beantwoordt
/// (conform de projectconventie dat widgettests géén echte HTTP-calls doen), inclusief de
/// productstatus-actie, zodat de bestaande kaartknoppen getest kunnen worden zonder netwerkverkeer.
MockClient _buildMockClient(
  Map<String, dynamic> product,
  List<Map<String, String>> callLog,
) {
  return MockClient((request) async {
    callLog.add({'method': request.method, 'path': request.url.path});
    switch (request.url.path) {
      case '/api/products':
        return http.Response(jsonEncode([product]), 200);
      case '/api/ai-catalog':
        return http.Response(jsonEncode(_aiCatalog()), 200);
      case '/api/products/demo/pause':
      case '/api/products/demo/resume':
      case '/api/products/demo/settings':
        return http.Response('', 200);
      default:
        return http.Response(jsonEncode(<dynamic>[]), 200);
    }
  });
}

/// Draait `body` volledig (inclusief alle taps/pumps erna) binnen de mocked-HTTP-zone: elke
/// interactie die een nieuwe API-call triggert (bv. de Pauzeren-knop) moet ook gemockt blijven,
/// anders valt de test terug op een echte HttpClient (die in widgettests altijd 400 geeft).
Future<void> _withDashboard(
  WidgetTester tester,
  Map<String, dynamic> product,
  List<Map<String, String>> callLog,
  Future<void> Function() body,
) async {
  tester.view.physicalSize = const Size(1400, 3000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  final mockClient = _buildMockClient(product, callLog);
  await http.runWithClient(() async {
    await tester.pumpWidget(const ProductFactoryDashboard());
    await tester.pump();
    await tester.pump();
    await body();
  }, () => mockClient);
}

void main() {
  testWidgets(
    'missie, project-key, repo, workspace, max-stories, wip, AI-model, cyclustijden en roadmapplanning staan '
    'niet meer standaard op de productkaart',
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, _product(), callLog, () async {
        expect(find.text('Demo missie tekst uniek 12345'), findsNothing);
        expect(find.textContaining('Project: DEMOPROJECT'), findsNothing);
        expect(find.textContaining('Repo: org/demo-repo'), findsNothing);
        expect(find.textContaining('Workspace: product-factory'), findsNothing);
        expect(find.textContaining('Max stories per cyclus: 3'), findsNothing);
        expect(find.textContaining('WIP: 2'), findsNothing);
        expect(find.textContaining('AI: openai · gpt-4o-mini'), findsNothing);
        expect(find.textContaining('Cyclustijden: 03:00, 15:00'), findsNothing);
        expect(find.textContaining('Maandag 10:00'), findsNothing);

        // De kaart zelf (naam + status) blijft wel gewoon zichtbaar.
        expect(find.text('Demo product'), findsOneWidget);
      });
    },
  );

  testWidgets(
    "de knoppen 'Pauzeren'/'Hervatten' en 'Start overleg' op de kaart blijven functioneel "
    'ongewijzigd',
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(
        tester,
        _product(status: 'active'),
        callLog,
        () async {
          expect(
            find.widgetWithText(OutlinedButton, 'Pauzeren'),
            findsOneWidget,
          );
          expect(
            find.widgetWithText(OutlinedButton, 'Start overleg'),
            findsOneWidget,
          );

          await tester.tap(find.widgetWithText(OutlinedButton, 'Pauzeren'));
          await tester.pump();
          await tester.pump();

          expect(
            callLog.any(
              (call) =>
                  call['method'] == 'POST' &&
                  call['path'] == '/api/products/demo/pause',
            ),
            isTrue,
          );
        },
      );
    },
  );

  testWidgets(
    'de Instellingen-knop opent het instellingenscherm met de verplaatste velden',
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, _product(), callLog, () async {
        await tester.tap(find.byType(SettingsButton));
        await tester.pumpAndSettle();

        expect(find.text('Demo missie tekst uniek 12345'), findsOneWidget);
        expect(find.text('DEMOPROJECT'), findsOneWidget);
        expect(find.text('product-factory'), findsOneWidget);
        expect(find.text('org/demo-repo'), findsOneWidget);
      });
    },
  );
}
