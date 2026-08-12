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
}
