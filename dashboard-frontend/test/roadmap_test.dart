import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/roadmap.dart';

Map<String, dynamic> epic({
  required String id,
  required String title,
  required int roadmapRank,
  required int customerRank,
  required int processRank,
  required int score,
  List<String> dependencies = const [],
  List<String> blockedBy = const [],
}) => {
  'id': id,
  'productSlug': 'museum',
  'title': title,
  'description': '$title uitgebreid beschreven',
  'status': 'OPEN',
  'roadmapRank': roadmapRank,
  'customerRank': customerRank,
  'processRank': processRank,
  'priorityScore': score,
  'dependencyIds': dependencies,
  'blockedByIds': blockedBy,
  'horizon': 'NOW',
  'kind': 'DISCOVERY',
  'capabilityKey': 'verbonden-bronnen',
};

void main() {
  test('parses ranks score and dependencies from the epic contract', () {
    final parsed = RoadmapEpic.fromJson(
      epic(
        id: 'epic-museum-0002',
        title: 'Publieke zoekflow',
        roadmapRank: 2,
        customerRank: 1,
        processRank: 3,
        score: 75,
        dependencies: ['epic-museum-0001'],
        blockedBy: ['epic-museum-0001'],
      ),
    );

    expect(parsed.customerRank, 1);
    expect(parsed.processRank, 3);
    expect(parsed.priorityScore, 75);
    expect(parsed.dependencyIds, ['epic-museum-0001']);
    expect(parsed.horizon, 'NOW');
    expect(parsed.kind, 'DISCOVERY');
    expect(parsed.capabilityKey, 'verbonden-bronnen');
  });

  testWidgets('shows graphical epic cards with both ranks and blocking state', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1400, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final epics = [
      epic(
        id: 'epic-museum-0001',
        title: 'Technisch fundament',
        roadmapRank: 1,
        customerRank: 2,
        processRank: 1,
        score: 25,
      ),
      epic(
        id: 'epic-museum-0002',
        title: 'Publieke zoekflow',
        roadmapRank: 2,
        customerRank: 1,
        processRank: 2,
        score: 75,
        dependencies: ['epic-museum-0001'],
        blockedBy: ['epic-museum-0001'],
      ),
    ];

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: RoadmapBoard(
              products: const [
                {'slug': 'museum', 'name': 'Museum'},
              ],
              epics: epics,
              visions: const [],
              stories: const [],
              deliveries: const [],
              api: const DashboardApi('http://localhost', null),
              onChanged: () {},
            ),
          ),
        ),
      ),
    );

    expect(find.text('Technisch fundament'), findsOneWidget);
    expect(find.text('Publieke zoekflow'), findsOneWidget);
    expect(find.text('Klant #1  ·  Proces #2'), findsOneWidget);
    expect(find.text('Wacht op 1 epic(s)'), findsOneWidget);
    expect(find.byType(CustomPaint), findsWidgets);
  });

  testWidgets('shows the north star, concept screen and capability horizons', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1400, 1100);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final vision = {
      'productSlug': 'museum',
      'version': 2,
      'changeSummary': 'De tijdreis is concreet gemaakt.',
      'content': {
        'northStarTitle': 'Het verleden ligt in je hand',
        'northStar': 'Iedere plek opent een betrouwbare reis door de tijd.',
        'futureNarrative':
            'Je richt je camera op een gebouw en ziet de plek, haar bewoners en haar verhalen door de eeuwen heen veranderen.',
        'experiences': [
          {'title': 'Straat door de tijd'},
        ],
        'conceptScreens': [
          {
            'title': 'Tijdmachine',
            'viewport': 'MOBILE',
            'eyebrow': 'Heemskerk · 1926',
            'headline': 'Schuif honderd jaar terug',
            'body': 'Bekijk echte bronnen en een gemarkeerde reconstructie.',
            'primaryAction': 'Start tijdreis',
            'secondaryAction': 'Bronnen',
            'visualDescription': 'Een straatbeeld met een tijdschuif.',
            'highlights': ['Oude foto’s', 'Kaarten met bronnen'],
          },
        ],
        'capabilities': [
          {
            'title': 'Plaats en tijd verbinden',
            'outcome': 'Een adres ontsluit materiaal uit meerdere periodes.',
            'horizon': 'HORIZON',
            'feasibility': 'UNKNOWN',
          },
        ],
        'assumptions': [
          {
            'statement': 'Bronnen hebben voldoende geo-informatie.',
            'probeType': 'DESK_RESEARCH',
            'proposedProbe': 'Controleer drie collecties.',
            'feasibility': 'UNKNOWN',
          },
        ],
      },
    };

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: RoadmapBoard(
              products: const [
                {'slug': 'museum', 'name': 'Museum'},
              ],
              epics: const [],
              visions: [vision],
              stories: const [],
              deliveries: const [],
              api: const DashboardApi('http://localhost', null),
              onChanged: () {},
            ),
          ),
        ),
      ),
    );

    expect(find.text('VERRE STIP · VISIE 2'), findsOneWidget);
    expect(find.text('Het verleden ligt in je hand'), findsOneWidget);
    expect(find.textContaining('Je richt je camera'), findsOneWidget);
    expect(find.text('Conceptschermen van het eindproduct'), findsOneWidget);
    expect(find.text('Schuif honderd jaar terug'), findsOneWidget);
    expect(find.text('Route naar de horizon'), findsOneWidget);

    await tester.tap(find.text('Route naar de horizon'));
    await tester.pumpAndSettle();
    expect(find.text('Plaats en tijd verbinden'), findsOneWidget);
    expect(
      find.text('Bronnen hebben voldoende geo-informatie.'),
      findsOneWidget,
    );
  });
}
