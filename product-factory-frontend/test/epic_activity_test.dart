import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/application_shell.dart';
import 'package:product_factory_frontend/epic_collaboration.dart';
import 'package:product_factory_frontend/product_workspace.dart';
import 'widget_test.dart' show FakeVersionGateway, FakeNavigationLocation;

void main() {
  final epic = <String, Object?>{
    'id': 'epic-7',
    'productId': 'pvdd',
    'title': 'Eerdere vergaderingen terugzien',
    'status': 'NEEDS_REFINEMENT',
    'version': 1,
    'contentVersion': 1,
    'readiness': {'readyForPlanning': false},
    'review': {'blockers': []},
  };
  final responses = <String, Object>{
    '/api/products': [
      {
        'id': 'pvdd',
        'name': 'PvdD',
        'status': 'ACTIVE',
        'dispatchingEnabled': true,
        'version': 1,
      },
    ],
    '/api/products/pvdd/epics': [epic],
    '/api/epics/epic-7/progress': {'steps': [], 'stories': []},
    '/api/epics/epic-7/messages': {'messages': [], 'hasMore': false},
  };
  for (final scenario in [
    {
      'state': 'WORKING',
      'label': 'Product Factory werkt de epic uit',
      'detail': 'De AI onderzoekt de uitwerking.',
    },
    {
      'state': 'QUEUED',
      'label': 'Wacht op de Product Factory',
      'detail': 'De volgende stap moet nog worden opgepakt.',
    },
    {
      'state': 'WAITING_FOR_HUMAN',
      'label': 'Wacht op antwoord van de architect',
      'waitingRole': 'ARCHITECT',
      'action': 'ANSWER',
      'pendingApprovalRoles': ['PRODUCT_OWNER'],
      'detail': 'Een antwoord is nodig.',
    },
    {
      'state': 'WAITING_FOR_HUMAN',
      'label': 'Wacht op goedkeuring of feedback van de PO',
      'waitingRole': 'PRODUCT_OWNER',
      'action': 'REVIEW',
      'pendingApprovalRoles': ['PRODUCT_OWNER'],
      'detail': 'De uitwerking ligt klaar voor beoordeling.',
    },
  ]) {
    testWidgets(
      'activiteit ${scenario['label']} blijft duidelijk op smal scherm en ververst',
      (tester) async {
        await tester.binding.setSurfaceSize(const Size(390, 1000));
        addTearDown(() => tester.binding.setSurfaceSize(null));
        var activity = scenario;
        final client = MockClient(
          (request) async => http.Response(
            jsonEncode(
              request.url.path.endsWith('/epic-activities')
                  ? {'epic-7': activity}
                  : responses[request.url.path] ?? [],
            ),
            200,
            headers: {'content-type': 'application/json'},
          ),
        );
        await tester.pumpWidget(
          MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(1.5)),
              child: child!,
            ),
            home: Scaffold(
              body: EpicCollaborationPage(
                products: HttpProductGateway(client: client),
                role: 'PRODUCT_OWNER',
                initialProductId: 'pvdd',
                api: CollaborationApi('csrf', client: client),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.textContaining(scenario['label']! as String), findsWidgets);
        if (scenario.containsKey('pendingApprovalRoles')) {
          expect(
            find.textContaining('Jouw goedkeuring ontbreekt nog'),
            findsOneWidget,
          );
          expect(find.textContaining('geen actie van jou nodig'), findsNothing);
          await tester.ensureVisible(find.text('Mijn aandacht nodig'));
          await tester.tap(find.text('Mijn aandacht nodig'));
          await tester.pumpAndSettle();
          expect(find.text('Eerdere vergaderingen terugzien'), findsOneWidget);
        }
        await tester.ensureVisible(
          find.text('Eerdere vergaderingen terugzien'),
        );
        await tester.tap(find.text('Eerdere vergaderingen terugzien'));
        await tester.pumpAndSettle();
        if (scenario['waitingRole'] == 'ARCHITECT') {
          expect(find.text('De architect is aan zet.'), findsOneWidget);
        }
        if (scenario['waitingRole'] == 'PRODUCT_OWNER') {
          expect(find.text('Jij bent aan zet.'), findsOneWidget);
        }
        expect(find.text('Wordt uitgewerkt'), findsNothing);
        expect(tester.takeException(), isNull);
        activity = {
          'state': 'WORKING',
          'label': 'Product Factory verwerkt je antwoord',
          'detail': 'De AI werkt verder.',
        };
        await tester.pump(const Duration(seconds: 9));
        await tester.pumpAndSettle();
        expect(
          find.text('Product Factory verwerkt je antwoord'),
          findsOneWidget,
        );
        expect(find.text('De architect is aan zet.'), findsNothing);
        expect(find.text('Jij bent aan zet.'), findsNothing);
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox());
      },
    );
  }

  testWidgets(
    'Mijn epics sluit het detail ook bij herhaald klikken en behoudt het project',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1500, 1100));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await http.runWithClient(
        () async {
          final location = FakeNavigationLocation(Uri.parse('/?product=pvdd'));
          await tester.pumpWidget(
            MaterialApp(
              home: ApplicationShell(
                showAcceptanceBanner: false,
                versionGateway: FakeVersionGateway(),
                isFactoryOwner: false,
                actingRole: 'PRODUCT_OWNER',
                navigationLocation: location,
              ),
            ),
          );
          await tester.pumpAndSettle();
          for (var i = 0; i < 2; i++) {
            await tester.tap(find.text('Eerdere vergaderingen terugzien'));
            await tester.pumpAndSettle();
            expect(find.text('Gesprek bij deze epic'), findsOneWidget);
            await tester.tap(find.text('Mijn epics'));
            await tester.pumpAndSettle();
            expect(find.text('Gesprek bij deze epic'), findsNothing);
            expect(find.text('Nieuwe epic'), findsOneWidget);
            expect(
              find.text('Eerdere vergaderingen terugzien'),
              findsOneWidget,
            );
            expect(location.current.queryParameters['product'], 'pvdd');
          }
          expect(tester.takeException(), isNull);
          await tester.pumpWidget(const SizedBox());
        },
        () => MockClient(
          (request) async => http.Response(
            jsonEncode(responses[request.url.path] ?? []),
            200,
            headers: {'content-type': 'application/json'},
          ),
        ),
      );
    },
  );
}
