import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/bugs.dart';

void main() {
  test('bugs and test sessions stay inside the selected product scope', () {
    final bugs = [
      {'id': 1, 'productSlug': 'alpha'},
      {'id': 2, 'productSlug': 'beta'},
    ];
    final sessions = [
      {'id': 'test-alpha', 'productSlug': 'alpha'},
      {'id': 'test-beta', 'productSlug': 'beta'},
    ];
    expect(bugsForProduct(bugs, 'alpha').single['id'], 1);
    expect(testSessionsForProduct(sessions, 'beta').single['id'], 'test-beta');
  });

  testWidgets('section navigation exposes the separate product views', (
    tester,
  ) async {
    var selected = DashboardSection.overview;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DashboardSectionNavigation(
            value: selected,
            onChanged: (value) => selected = value,
          ),
        ),
      ),
    );

    expect(find.text('Overzicht'), findsOneWidget);
    expect(find.text('Roadmap'), findsOneWidget);
    expect(find.text('Productsessies'), findsOneWidget);
    expect(find.text('Stories'), findsOneWidget);
    expect(find.text('Epics'), findsOneWidget);
    expect(find.text('Bugs'), findsOneWidget);
    expect(find.text('Testsessies'), findsOneWidget);
    expect(find.text('Overleggen'), findsOneWidget);
  });

  testWidgets('important bugs visibly block feature work', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: ImportantBugSummary(
            bugs: [
              {
                'id': 7,
                'priority': 'P1',
                'status': 'OPEN',
                'title': 'Opslaan werkt niet',
              },
            ],
          ),
        ),
      ),
    );
    expect(
      find.textContaining('blokkeren nieuwe functionaliteit'),
      findsOneWidget,
    );
    expect(find.textContaining('P1 · Opslaan werkt niet'), findsOneWidget);
  });

  testWidgets('empty bug list explains where bugs come from', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: BugList(
            bugs: [],
            api: DashboardApi('http://localhost', null),
            onChanged: _noop,
          ),
        ),
      ),
    );
    expect(find.text('Nog geen bugs geregistreerd'), findsOneWidget);
    expect(find.textContaining('Roadmap- en testsessies'), findsOneWidget);
  });
}

void _noop() {}
