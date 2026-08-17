import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/bugs.dart';
import 'package:product_factory_dashboard/classification.dart';

class _MobileNavigationHarness extends StatefulWidget {
  const _MobileNavigationHarness();

  @override
  State<_MobileNavigationHarness> createState() =>
      _MobileNavigationHarnessState();
}

class _MobileNavigationHarnessState extends State<_MobileNavigationHarness> {
  DashboardSection value = DashboardSection.overview;

  @override
  Widget build(BuildContext context) => MobileDashboardSectionNavigation(
    value: value,
    onChanged: (section) => setState(() => value = section),
  );
}

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

  testWidgets(
    'mobiele native sectiekeuze heeft exact de vaste opties en activeert ze met toetsenbord',
    (tester) async {
      tester.view.physicalSize = const Size(320, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final semanticsHandle = tester.ensureSemantics();

      await tester.pumpWidget(
        const MaterialApp(home: Scaffold(body: _MobileNavigationHarness())),
      );

      expect(
        mobileDashboardSections
            .map(mobileDashboardSectionLabel)
            .toList(growable: false),
        const [
          'Overzicht',
          'Productcycli',
          'Stories',
          'Roadmap',
          'Bugs',
          'Epics',
          'Testsessies',
          'Overleggen',
        ],
      );
      expect(find.byType(SingleChildScrollView), findsNothing);
      final navigation = find.byType(MobileDashboardSectionNavigation);
      expect(tester.getRect(navigation).right, lessThanOrEqualTo(320));
      final semantics = tester.getSemantics(navigation).getSemanticsData();
      expect(semantics.label, contains('Sectie kiezen'));
      expect(semantics.value, contains('Overzicht'));

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      for (final expected in mobileDashboardSections.skip(1)) {
        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pumpAndSettle();
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
        await tester.pump();
        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pumpAndSettle();

        expect(
          tester
              .state<_MobileNavigationHarnessState>(
                find.byType(_MobileNavigationHarness),
              )
              .value,
          expected,
        );
      }

      final dropdown = tester.widget<DropdownButtonFormField<DashboardSection>>(
        find.byType(DropdownButtonFormField<DashboardSection>),
      );
      final focusedBorder =
          dropdown.decoration.focusedBorder! as OutlineInputBorder;
      expect(focusedBorder.borderSide.width, 3);
      expect(
        contrastRatio(
          focusedBorder.borderSide.color,
          ThemeData().colorScheme.surface,
        ),
        greaterThanOrEqualTo(3),
      );
      semanticsHandle.dispose();
    },
  );

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
