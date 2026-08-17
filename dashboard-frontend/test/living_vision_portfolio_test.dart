import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/roadmap.dart';

class _FakeDashboardApi extends DashboardApi {
  const _FakeDashboardApi() : super('https://dashboard.test', null);

  @override
  Future<List<dynamic>> livingVisionIdeaHistory(
    String slug,
    String ideaKey,
  ) async => [
    {
      'version': 1,
      'promise': 'De eerste productbelofte.',
      'changeReason': 'Ontstaan uit een herleidbare bron.',
      'createdByRole': 'vision-curator',
    },
  ];

  @override
  Future<Map<String, dynamic>> livingVisionPortfolio(String slug) async => {
    'ideas': [
      {
        'ideaKey': 'stabiel-idee',
        'status': 'UX_DESIGNED',
        'currentVersion': 3,
        'promise': 'Een concrete productbelofte voor de primaire gebruiker.',
        'statusReason': 'Deze sessie maakte de flow concreter.',
      },
    ],
    'conceptVersions': [
      {
        'viewport': 'MOBILE',
        'flowPosition': 1,
        'version': 2,
        'userGoal': 'De gebruiker voltooit zelfstandig het kerndoel.',
        'interaction': 'Open, kies en bevestig.',
        'assets': const [],
      },
    ],
    'inspiration': [
      {
        'title': 'Herleidbare externe bron',
        'sourceUrl': 'https://example.test/bron',
        'observation': 'Een feitelijke observatie.',
        'interpretation': 'Een afzonderlijke AI-interpretatie.',
      },
    ],
    'research': [
      {
        'researchType': 'ACCESSIBILITY',
        'status': 'TESTING',
        'question': 'Is de volledige flow toetsenbordbedienbaar?',
        'conclusion':
            'De hoofdroute werkt; hersteltoestanden vragen nog bewijs.',
      },
    ],
  };
}

void main() {
  testWidgets(
    'toont portfolio flow bronnen en onderzoek met verschillende labels',
    (tester) async {
      tester.view.physicalSize = const Size(1200, 1800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: LivingVisionPortfolioPanel(
                api: _FakeDashboardApi(),
                productSlug: 'product-a',
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Levende productvisie'), findsOneWidget);
      expect(
        find.textContaining('1 ideeën · 1 conceptversies'),
        findsOneWidget,
      );
      expect(find.text('stabiel-idee'), findsOneWidget);
      expect(find.textContaining('UX_DESIGNED · versie 3'), findsOneWidget);
      expect(find.text('UX-concepten en flows'), findsOneWidget);
      expect(find.textContaining('MOBILE · flowstap 1'), findsOneWidget);
      expect(find.text('Externe inspiratie'), findsOneWidget);
      expect(
        find.textContaining('Bronfeit: Een feitelijke observatie.'),
        findsOneWidget,
      );
      expect(find.textContaining('AI-interpretatie:'), findsOneWidget);
      expect(find.textContaining('https://example.test/bron'), findsOneWidget);
      expect(find.text('Onderzoek en conclusies'), findsOneWidget);
      expect(find.textContaining('Technische conclusie:'), findsOneWidget);

      await tester.tap(find.text('Versiegeschiedenis'));
      await tester.pumpAndSettle();
      expect(find.text('Versiegeschiedenis · stabiel-idee'), findsOneWidget);
      expect(find.text('De eerste productbelofte.'), findsOneWidget);
      expect(
        find.textContaining('Ontstaan uit een herleidbare bron.'),
        findsOneWidget,
      );
    },
  );

  testWidgets('blijft bruikbaar op een viewport van 320 pixels', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: LivingVisionPortfolioPanel(
              api: _FakeDashboardApi(),
              productSlug: 'product-a',
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('stabiel-idee'), findsOneWidget);
    expect(find.text('UX-concepten en flows'), findsOneWidget);
  });
}
