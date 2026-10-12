import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/epic_collaboration.dart';
import 'package:product_factory_frontend/product_workspace.dart';

void main() {
  for (final link in ['epic', 'request', 'linkedObject']) {
    testWidgets(
      'architect beantwoordt $link-vraag in de epic voordat goedkeuring beschikbaar is',
      (tester) async {
        final fixture = QuestionFixture(link: link);
        await fixture.mount(tester);
        expect(find.textContaining('Jouw antwoord is nodig'), findsOneWidget);
        expect(
          find.textContaining('Jouw goedkeuring ontbreekt nog'),
          findsNothing,
        );
        await fixture.openEpic(tester);
        expect(find.text(QuestionFixture.questionText), findsOneWidget);
        expect(find.text('Beantwoord vraag'), findsOneWidget);

        await tester.ensureVisible(find.text('Goedkeuring'));
        await tester.tap(find.text('Goedkeuring'));
        await tester.pumpAndSettle();
        expect(find.text('Akkoord op deze versie'), findsNothing);
        expect(
          find.textContaining('Beantwoord eerst de open vraag hierboven.'),
          findsOneWidget,
        );

        await tester.ensureVisible(find.text('Beantwoord vraag'));
        await tester.tap(find.text('Beantwoord vraag'));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byType(TextFormField),
          'Maximaal twee extra runs per dag.',
        );
        await tester.tap(find.text('Bevestigen'));
        await tester.pumpAndSettle();
        expect(fixture.posts, hasLength(1));
        expect(
          fixture.posts.single.url.path,
          '/api/products/questions/question-1/answer-directly',
        );
        expect(fixture.posts.single.headers['X-PF-CSRF'], 'csrf');
        final body = jsonDecode(fixture.posts.single.body) as Map;
        expect(body['answer'], 'Maximaal twee extra runs per dag.');
        expect(body['expectedVersion'], 4);
        expect(body['idempotencyKey'], isNotEmpty);
        expect(find.text('Beantwoord vraag'), findsNothing);
        expect(find.text('Akkoord op deze versie'), findsNothing);
        fixture.ready = true;
        await tester.pump(const Duration(seconds: 9));
        await tester.pumpAndSettle();
        expect(find.text('Akkoord op deze versie'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }

  testWidgets(
    'vragen van een andere rol blijven verborgen met een route naar eigen vragen',
    (tester) async {
      final fixture = QuestionFixture(questionRole: 'PRODUCT_OWNER');
      var openedQuestions = false;
      await fixture.mount(
        tester,
        onOpenQuestions: () => openedQuestions = true,
      );
      await fixture.openEpic(tester);
      expect(find.text(QuestionFixture.questionText), findsNothing);
      expect(find.text('Beantwoord vraag'), findsNothing);
      await tester.ensureVisible(find.text('Open Vragen voor mij'));
      await tester.tap(find.text('Open Vragen voor mij'));
      await tester.pumpAndSettle();
      expect(openedQuestions, isTrue);
      expect(fixture.posts, isEmpty);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'factory owner ziet de open vraag in het activiteitsoverzicht alleen-lezen',
    (tester) async {
      final fixture = QuestionFixture();
      await fixture.mount(tester, role: 'FACTORY_OWNER', section: 'activity');
      await tester.tap(find.byKey(const ValueKey('activity-epics')));
      await tester.pumpAndSettle();
      await fixture.openEpic(tester);
      expect(find.text(QuestionFixture.questionText), findsOneWidget);
      expect(find.text('Beantwoord vraag'), findsNothing);
      await tester.ensureVisible(find.text('Goedkeuring'));
      await tester.tap(find.text('Goedkeuring'));
      await tester.pumpAndSettle();
      expect(find.text('Architectuur akkoord als superuser'), findsNothing);
      expect(find.text('Functioneel akkoord als superuser'), findsNothing);
      expect(fixture.posts, isEmpty);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}

class QuestionFixture {
  QuestionFixture({this.link = 'epic', this.questionRole = 'ARCHITECT'});
  static const title = 'Meerdere vergaderingen analyseren';
  static const questionText = 'Welk budget mag de extra AI-analyse gebruiken?';
  final String link;
  final String questionRole;
  final posts = <http.Request>[];
  bool answered = false;
  bool ready = false;

  Map<String, Object?> get epic => {
    'id': 'epic-9',
    'productId': 'pvdd',
    'sourceProductRequestId': 'request-1',
    'title': title,
    'status': ready ? 'AWAITING_APPROVAL' : 'NEEDS_REFINEMENT',
    'contentVersion': 1,
    'version': 1,
    'readiness': {'readyForPlanning': ready},
    'review': {
      'productOwnerApproved': false,
      'architectApproved': false,
      'architectRequired': true,
      'blockers': [],
    },
  };

  Future<void> mount(
    WidgetTester tester, {
    String role = 'ARCHITECT',
    String section = 'home',
    VoidCallback? onOpenQuestions,
  }) async {
    await tester.binding.setSurfaceSize(const Size(700, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final client = MockClient((request) async {
      if (request.method == 'POST') {
        posts.add(request);
        answered = true;
        return http.Response('', 204);
      }
      final responses = <String, Object>{
        '/api/products': [
          {
            'id': 'pvdd',
            'name': 'PvdD',
            'status': 'ACTIVE',
            'version': 1,
            'dispatchingEnabled': true,
          },
        ],
        '/api/products/pvdd/epics': [epic],
        '/api/products/pvdd/questions': [
          {
            'id': 'question-1',
            'productId': 'pvdd',
            'question': questionText,
            'context': 'Het huidige budget is nul.',
            'agentRole': 'PRODUCT_DESIGNER_MVP',
            'requestedRole': questionRole,
            'status': answered ? 'ANSWERED' : 'OPEN',
            'version': 4,
            if (link == 'epic') 'epicLinkId': 'epic-9',
            if (link == 'request') 'productRequestId': 'request-1',
            if (link == 'linkedObject')
              'linkedObjects': [
                {'type': 'EPIC', 'id': 'epic-9'},
              ],
          },
        ],
        '/api/products/pvdd/epic-activities': {
          'epic-9': {
            'state': answered && !ready ? 'WORKING' : 'WAITING_FOR_HUMAN',
            if (!answered || ready) 'waitingRole': 'ARCHITECT',
            if (!answered || ready) 'action': ready ? 'REVIEW' : 'ANSWER',
            'label': ready
                ? 'Wacht op goedkeuring van de architect'
                : answered
                ? 'Product Factory verwerkt je antwoord'
                : 'Wacht op antwoord van de architect',
            'detail': ready
                ? 'De uitwerking is gereed.'
                : answered
                ? 'De AI werkt verder.'
                : 'Een antwoord is nodig.',
            'pendingApprovalRoles': ['PRODUCT_OWNER', 'ARCHITECT'],
          },
        },
        '/api/products/pvdd/governance': {'configured': true, 'version': 1},
        '/api/epics/epic-9/history': [epic],
        '/api/epics/epic-9/progress': {'steps': [], 'stories': []},
        '/api/epics/epic-9/messages': {'messages': [], 'hasMore': false},
      };
      return http.Response(
        jsonEncode(responses[request.url.path] ?? []),
        200,
        headers: {'content-type': 'application/json'},
      );
    });
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: EpicCollaborationPage(
            products: HttpProductGateway(client: client),
            role: role,
            section: section,
            initialProductId: 'pvdd',
            onOpenQuestions: onOpenQuestions,
            api: CollaborationApi('csrf', client: client),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> openEpic(WidgetTester tester) async {
    await tester.ensureVisible(find.text(title));
    await tester.tap(find.text(title));
    await tester.pumpAndSettle();
  }
}
