import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/epic_collaboration.dart';
import 'package:product_factory_frontend/product_workspace.dart';

void main() {
  test(
    'conflicten en ontbrekende optionele omgeving blijven onderscheiden',
    () async {
      final api = CollaborationApi(
        'csrf',
        client: MockClient((request) async {
          expect(request.headers['X-PF-CSRF'], 'csrf');
          return http.Response(
            '{"message":"Gewijzigd"}',
            request.url.path == '/missing' ? 404 : 409,
          );
        }),
      );
      expect(await api.request('/missing', optional: true), isNull);
      await expectLater(
        api.request('/conflict', method: 'POST', body: {'reason': 'Akkoord'}),
        throwsA(isA<StateError>()),
      );
      api.close();
    },
  );

  for (final role in ['PRODUCT_OWNER', 'ARCHITECT']) {
    testWidgets('$role kan op 320px en 200 procent de epic en acties lezen', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(320, 850));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final epic = {
        'id': 'epic-1',
        'title': 'Betere meldingen',
        'status': 'AWAITING_PRODUCT_OWNER_APPROVAL',
        'version': 1,
        'contentVersion': 1,
        'summary': 'Meldingen overzichtelijk tonen',
        'problem': 'Gebruikers missen updates',
        'solution': 'Toon een overzicht met meldingen',
        'acceptanceCriteria': ['Nieuwe meldingen worden getoond.'],
        'uxScreens': [
          {
            'screenKey': 'overzicht',
            'state': 'MAIN',
            'purpose': 'Het overzicht',
            'artifacts': {'DESKTOP': 'ux-01', 'MOBILE': 'ux-02'},
          },
        ],
        'uxArtifacts': [
          {'name': 'ux-01', 'uri': '/private/desktop'},
          {'name': 'ux-02', 'uri': '/private/mobile'},
        ],
        'review': {
          'productOwnerApproved': false,
          'architectApproved': false,
          'blockers': ['Functioneel akkoord ontbreekt.'],
        },
        'impact': {
          'items': [
            {
              'category': 'PRODUCT_AI',
              'level': 'MATERIAL',
              'summary': 'AI wordt iedere tien minuten aangeroepen',
              'evidence': ['Planner wordt periodiek gestart'],
            },
          ],
          'productAi': {'changed': true, 'frequency': 'Iedere tien minuten'},
        },
      };
      final requestedImages = <String>[];
      final client = MockClient((request) async {
        if (request.url.path.endsWith('/ux-artifacts')) {
          requestedImages.add(request.url.queryParameters['name']!);
          return http.Response.bytes(
            base64Decode(
              'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==',
            ),
            200,
          );
        }
        final path = request.url.path;
        Object? value = [];
        if (path == '/api/products') {
          value = [
            {
              'id': 'pvdd',
              'name': 'PvdD',
              'status': 'ACTIVE',
              'dispatchingEnabled': false,
              'version': 1,
            },
          ];
        }
        if (path.endsWith('/epics')) value = [epic];
        if (path.endsWith('/governance')) {
          value = {'configured': true, 'version': 1};
        }
        if (path.endsWith('/test-configuration')) {
          return http.Response('{}', 404);
        }
        if (path.endsWith('/history')) value = [epic];
        if (path.endsWith('/progress')) value = {'steps': [], 'stories': []};
        return http.Response(
          jsonEncode(value),
          200,
          headers: {'content-type': 'application/json'},
        );
      });
      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(
              size: Size(320, 850),
              textScaler: TextScaler.linear(2),
            ),
            child: Scaffold(
              body: EpicCollaborationPage(
                products: HttpProductGateway(client: client),
                role: role,
                api: CollaborationApi(null, client: client),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Betere meldingen'), findsOneWidget);
      expect(
        find.text('Nieuwe epic'),
        role == 'PRODUCT_OWNER' ? findsOneWidget : findsNothing,
      );
      await tester.ensureVisible(find.text('Betere meldingen'));
      await tester.tap(find.text('Betere meldingen'));
      await tester.pumpAndSettle();
      expect(
        find.text('Inhoudsversie 1 · Functioneel akkoord nodig'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
      if (role == 'ARCHITECT') {
        expect(
          find.textContaining('AI wordt iedere tien minuten aangeroepen'),
          findsOneWidget,
        );
      }
      if (role == 'PRODUCT_OWNER') {
        await tester.ensureVisible(find.text('Schermen'));
        await tester.tap(find.text('Schermen'));
        await tester.pumpAndSettle();
        expect(find.text('Desktop'), findsOneWidget);
        expect(find.text('Mobiel'), findsOneWidget);
        expect(requestedImages, ['ux-01']);
        await tester.ensureVisible(find.text('Mobiel'));
        await tester.tap(find.text('Mobiel'));
        await tester.pumpAndSettle();
        expect(requestedImages, ['ux-01', 'ux-02']);
        expect(tester.takeException(), isNull);
      }
      await tester.tap(find.text('Gesprek'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Mijn conceptbericht');
      await tester.tap(find.text('Epic'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Gesprek'));
      await tester.pumpAndSettle();
      expect(find.text('Mijn conceptbericht'), findsOneWidget);
      await tester.ensureVisible(find.text('Verstuur'));
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      await tester.pump();
    });
  }

  testWidgets(
    'PO verwerkt een wijziging via het gedeelde epicgesprek met de actuele versie',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1500, 1100));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final submittedFeedback = <String, Object?>{};
      final submittedUndo = <String, Object?>{};
      final epic = <String, Object?>{
        'id': 'epic-1',
        'productId': 'hkh',
        'title': 'Rustige homepage',
        'status': 'AWAITING_PRODUCT_OWNER_APPROVAL',
        'version': 2,
        'contentVersion': 2,
        'summary': 'Een rustige homepage.',
        'problem': 'De homepage is druk.',
        'solution': 'Breng een duidelijke hiërarchie aan.',
        'acceptanceCriteria': ['De homepage is overzichtelijk.'],
        'uxScreens': [],
        'uxArtifacts': [],
        'review': {
          'productOwnerApproved': false,
          'architectApproved': true,
          'blockers': ['Functioneel akkoord ontbreekt.'],
        },
        'impact': {'items': [], 'productAi': <String, Object?>{}},
      };
      final conversationSummary = {
        'id': 'conversation-1',
        'title': 'Homepage bespreken',
        'status': 'PROPOSAL_READY',
        'epicId': null,
      };
      final conversation = {
        ...conversationSummary,
        'version': 7,
        'changeProposal': {
          'id': 'turn',
          'status': 'READY',
          'beforeContentVersion': 1,
          'afterContentVersion': 2,
          'summary': 'Mijn dossiers toegevoegd.',
        },
        'messages': [
          {
            'sender': 'USER',
            'text': 'Mijn dossiers ontbreekt in de ontwerpen.',
          },
          {
            'sender': 'PRODUCT_ADVISOR',
            'text': 'Geef Mijn dossiers een zichtbare plek.',
          },
        ],
        'request': {
          'id': 'request-1',
          'status': 'ROUTED',
          'linkedEpicId': 'epic-1',
          'content': {
            'title': 'Rustige homepage',
            'summary': 'Het oorspronkelijke voorstel.',
          },
        },
      };
      http.Response jsonOk(Object value) => http.Response(
        jsonEncode(value),
        200,
        headers: {'content-type': 'application/json; charset=utf-8'},
      );
      final client = MockClient((request) async {
        final path = request.url.path;
        if (request.method == 'POST' &&
            path == '/api/conversations/conversation-1/revert-epic-change') {
          submittedUndo.addAll(
            (jsonDecode(request.body) as Map).cast<String, Object?>(),
          );
          return http.Response('', 204);
        }
        if (request.method == 'POST' &&
            path == '/api/conversations/conversation-1/messages') {
          submittedFeedback.addAll(
            (jsonDecode(request.body) as Map).cast<String, Object?>(),
          );
          return http.Response('', 202);
        }
        if (path == '/api/products') {
          return jsonOk([
            {
              'id': 'hkh',
              'name': 'HKH',
              'status': 'ACTIVE',
              'dispatchingEnabled': false,
              'version': 1,
            },
          ]);
        }
        if (path == '/api/products/hkh/epics') {
          return jsonOk([epic]);
        }
        if (path == '/api/products/hkh/conversations') {
          return jsonOk([conversation]);
        }
        if (path == '/api/epics/epic-1/messages') {
          return jsonOk({
            'messages': [
              {
                'id': 'm1',
                'sender': 'USER',
                'text': 'Mijn dossiers ontbreekt in de ontwerpen.',
              },
              {
                'id': 'm2',
                'sender': 'USER',
                'authorRole': 'ARCHITECT',
                'text': 'Dit past binnen de architectuur.',
              },
              {
                'id': 'm3',
                'sender': 'SYSTEM',
                'text': 'De epic is bijgewerkt naar voorstelversie 2.',
              },
            ],
            'hasMore': false,
          });
        }
        if (path == '/api/epics/epic-1/discussions') {
          return jsonOk([
            conversation,
            {
              'id': 'old-architect-chat',
              'status': 'CLOSED',
              'audienceRole': 'ARCHITECT',
              'messages': [
                {'sender': 'USER', 'text': 'Dit past binnen de architectuur.'},
              ],
            },
          ]);
        }
        if (path == '/api/conversations/conversation-1') {
          return jsonOk(conversation);
        }
        if (path.endsWith('/test-configuration')) {
          return http.Response('{}', 404);
        }
        if (path.endsWith('/governance')) {
          return jsonOk({'configured': true, 'version': 1});
        }
        if (path.endsWith('/progress')) {
          return jsonOk({'steps': [], 'stories': []});
        }
        return jsonOk([]);
      });

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: EpicCollaborationPage(
              products: HttpProductGateway(client: client),
              role: 'PRODUCT_OWNER',
              initialProductId: 'hkh',
              api: CollaborationApi('csrf', client: client),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Rustige homepage'));
      await tester.pumpAndSettle();
      expect(
        find.text('Mijn dossiers ontbreekt in de ontwerpen.'),
        findsOneWidget,
      );
      expect(find.text('Epic aanpassen'), findsNothing);
      expect(find.text('Architect'), findsOneWidget);
      await tester.enterText(
        find.byType(TextField).last,
        'Voeg Mijn dossiers toe aan desktop en mobiel.',
      );
      await tester.ensureVisible(find.text('Verstuur'));
      await tester.tap(find.text('Verstuur'));
      await tester.pumpAndSettle();
      expect(submittedFeedback['expectedVersion'], 7);
      expect(submittedFeedback['expectedEpicVersion'], 2);
      expect(submittedFeedback['intent'], 'AUTO');
      expect(
        submittedFeedback['text'],
        'Voeg Mijn dossiers toe aan desktop en mobiel.',
      );
      await tester.ensureVisible(find.text('Voorstel terugdraaien'));
      await tester.tap(find.text('Voorstel terugdraaien'));
      await tester.pumpAndSettle();
      expect(submittedUndo['expectedVersion'], 7);
      expect(submittedUndo['expectedEpicVersion'], 2);
      expect(find.text('Rustige homepage'), findsWidgets);
      expect(tester.takeException(), isNull);
    },
  );
}
