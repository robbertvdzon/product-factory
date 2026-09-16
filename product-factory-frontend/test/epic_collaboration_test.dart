import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/epic_collaboration.dart';
import 'package:product_factory_frontend/product_workspace.dart';

void main() {

  testWidgets('wachtende epic behoudt het oorspronkelijke gesprek zonder dubbele voorbereiding', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1500, 1100));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final epic = {'id':'epic-7','productId':'pvdd','title':'Eerdere vergaderingen terugzien','status':'NEEDS_REFINEMENT','version':1,'contentVersion':1,'review':{'blockers':[]}};
    final conversation = {'id':'chat-7','productId':'pvdd','title':'Mijn oorspronkelijke wens','purpose':'EPIC','epicId':'epic-7','status':'PROPOSAL_READY','version':3,'request':{'status':'ROUTING','linkedEpicId':'epic-7'}};
    final responses = <String,Object>{
      '/api/products':[{'id':'pvdd','name':'PvdD','status':'ACTIVE','dispatchingEnabled':true,'version':1}],
      '/api/products/pvdd/epics':[epic],
      '/api/products/pvdd/conversations':[conversation],
      '/api/epics/epic-7/discussions':[conversation],
      '/api/products/pvdd/questions':[{'id':'question-7','epicLinkId':'epic-7','status':'OPEN','question':'Wil je alle eerdere vergaderingen zien?','agentRole':'PRODUCT_DESIGNER_MVP'}],
      '/api/epics/epic-7/messages':{'messages':[
        {'id':'m1','sender':'USER','text':'Ik wil ook de oude agenda kunnen zien.'},
        {'id':'m2','sender':'PRODUCT_ADVISOR','text':'De eerdere agenda en adviezen zijn bewaard.'},
      ],'hasMore':false},
      '/api/epics/epic-7/progress':{'steps':[],'stories':[]},
    };
    final client = MockClient((request) async => http.Response(jsonEncode(responses[request.url.path] ?? []),200,headers:{'content-type':'application/json'}));
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:EpicCollaborationPage(
      products:HttpProductGateway(client:client),role:'PRODUCT_OWNER',initialProductId:'pvdd',api:CollaborationApi('csrf',client:client),
    ))));
    await tester.pumpAndSettle();
    expect(find.text('Epics in voorbereiding'),findsNothing);
    expect(find.text('Mijn oorspronkelijke wens'),findsNothing);
    await tester.tap(find.text('Eerdere vergaderingen terugzien'));
    await tester.pumpAndSettle();
    expect(find.text('Ik wil ook de oude agenda kunnen zien.'),findsOneWidget);
    expect(find.text('De eerdere agenda en adviezen zijn bewaard.'),findsOneWidget);
    expect(find.textContaining('Wil je alle eerdere vergaderingen zien?'),findsOneWidget);
    expect(tester.takeException(),isNull);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('los gesprek verwijderen bevestigt de titel en blijft weg na verversen', (tester) async {
    var deleted = false;
    var deletes = 0;
    final client = MockClient((request) async {
      Object body = [];
      if (request.method == 'DELETE') {
        expect(request.url.path, '/api/conversations/chat-1');
        expect(request.headers['X-PF-CSRF'], 'csrf');
        expect(jsonDecode(request.body)['expectedVersion'], 4);
        deleted = true;
        deletes++;
        return http.Response('', 204);
      }
      if (request.url.path == '/api/products') {
        body = [{'id':'hkh','name':'HKH','status':'ACTIVE','dispatchingEnabled':true,'version':1}];
      } else if (request.url.path.endsWith('/conversations') && !deleted) {
        body = [{'id':'chat-1','productId':'hkh','title':'Hoe werkt zoeken?','status':'PROCESSING','purpose':'QUESTION','version':4,'updatedAt':'2026-09-14T12:30:00'}];
      }
      return http.Response(jsonEncode(body), 200, headers: {'content-type':'application/json'});
    });
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: EpicCollaborationPage(
      products: HttpProductGateway(client:client), role:'PRODUCT_OWNER', section:'own-questions',
      initialProductId:'hkh', api:CollaborationApi('csrf',client:client),
    ))));
    await tester.pumpAndSettle();
    expect(find.textContaining('Laatste activiteit: 14 sep 2026 · 12:30'), findsOneWidget);
    await tester.tap(find.byTooltip('Gesprek verwijderen'));
    await tester.pumpAndSettle();
    expect(find.textContaining('“Hoe werkt zoeken?”'), findsOneWidget);
    await tester.tap(find.text('Annuleren'));
    await tester.pumpAndSettle();
    expect(deletes,0);
    expect(find.text('Hoe werkt zoeken?'),findsOneWidget);
    await tester.tap(find.byTooltip('Gesprek verwijderen'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Verwijderen'));
    await tester.pumpAndSettle();
    expect(deletes,1);
    expect(find.text('Hoe werkt zoeken?'),findsNothing);
    await tester.pump(const Duration(seconds:9));
    await tester.pumpAndSettle();
    expect(find.text('Hoe werkt zoeken?'),findsNothing);
    expect(tester.takeException(),isNull);
  });

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
          'architectApproved': role == 'PRODUCT_OWNER',
          'architectRequired': role == 'ARCHITECT',
          'policyVersion': 3,
          'records': [
            {'role':'PRODUCT_OWNER','contentVersion':0,'policyVersion':3,'reason':'Oud akkoord','createdAt':'2026-09-13T10:00:00'},
            {'role':'PRODUCT_OWNER','contentVersion':1,'policyVersion':3,'reason':'Nieuwe beoordeling','createdAt':'2026-09-14T10:00:00'},
          ],
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
      var deleted = false;
      var deletes = 0;
      final requestedImages = <String>[];
      final client = MockClient((request) async {
        if (request.method == 'DELETE') {
          expect(request.url.path, '/api/epics/epic-1');
          expect(request.headers['X-PF-CSRF'], 'csrf');
          expect(jsonDecode(request.body)['expectedVersion'], 1);
          deleted = true;
          deletes++;
          return http.Response('', 204);
        }
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
        if (path.endsWith('/epics')) value = deleted ? [] : [epic];
        if (path.endsWith('/governance')) {
          value = {'configured': true, 'version': 1};
        }
        if (path.endsWith('/test-configuration')) {
          return http.Response('{}', 404);
        }
        if (path.endsWith('/history')) value = [epic];
        if (path.endsWith('/progress')) value = {'steps': [], 'stories': [for (final status in ['IN_PROGRESS','DONE','TODO']) {'id':status,'title':'Story $status','status':status}]};
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
                api: CollaborationApi('csrf', client: client),
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
      expect(find.text('Epic verwijderen'), role == 'PRODUCT_OWNER' ? findsOneWidget : findsNothing);
      if (role == 'ARCHITECT') {
        expect(
          find.textContaining('AI wordt iedere tien minuten aangeroepen'),
          findsOneWidget,
        );
      }
      if (role == 'PRODUCT_OWNER') {
        await tester.ensureVisible(find.text('Goedkeuring'));
        await tester.tap(find.text('Goedkeuring'));
        await tester.pumpAndSettle();
        expect(find.text('Architect: Niet vereist voor deze epic'), findsOneWidget);
        expect(find.textContaining('Oud akkoord'), findsNothing);
        expect(find.textContaining('Productafspraken versie'), findsNothing);
        expect(find.textContaining('Nieuwe beoordeling'), findsOneWidget);
        await tester.ensureVisible(find.text('Voortgang'));
        await tester.tap(find.text('Voortgang'));
        await tester.pumpAndSettle();
        expect(find.text('1 · In ontwikkeling'), findsOneWidget);
        expect(find.text('1 · Afgerond'), findsOneWidget);
        expect(find.text('1 · Nog niet opgepakt'), findsOneWidget);
        expect(tester.takeException(), isNull);
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
      if (role == 'PRODUCT_OWNER') {
        await tester.tap(find.text('Epic'));
        await tester.pumpAndSettle();
        await tester.ensureVisible(find.text('Epic verwijderen'));
        await tester.tap(find.text('Epic verwijderen'));
        await tester.pumpAndSettle();
        expect(find.textContaining('“Betere meldingen”'), findsOneWidget);
        await tester.tap(find.text('Annuleren'));
        await tester.pumpAndSettle();
        expect(deletes, 0);
        await tester.tap(find.text('Epic verwijderen'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Verwijderen'));
        await tester.pumpAndSettle();
        expect(deletes, 1);
        expect(find.text('Betere meldingen'), findsNothing);
        await tester.pump(const Duration(seconds: 9));
        await tester.pumpAndSettle();
        expect(find.text('Betere meldingen'), findsNothing);
        expect(tester.takeException(), isNull);
      }
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
                'text':
                    'De epic is bijgewerkt naar voorstelversie 2. Mijn dossiers toegevoegd.',
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
      expect(find.textContaining('Mijn dossiers toegevoegd.'), findsOneWidget);
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
