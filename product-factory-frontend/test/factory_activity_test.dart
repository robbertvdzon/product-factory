import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/epic_collaboration.dart';
import 'package:product_factory_frontend/product_factory_theme.dart';
import 'package:product_factory_frontend/product_workspace.dart';

void main() {
  testWidgets(
    'factory owner filtert gesprekken op starter en deelnemers en leest exact het gekozen gesprek',
    (tester) async {
      final fixture = ActivityFixture();
      final selections = <String?>[];
      await fixture.mount(tester, onConversationSelected: selections.add);

      expect(find.text('Vraag van Mark'), findsOneWidget);
      expect(find.text('Vraag van Anna'), findsOneWidget);
      await selectMark(tester);
      expect(find.text('Vraag van Mark'), findsOneWidget);
      expect(find.text('Vraag van Anna'), findsNothing);
      expect(find.text('Gezamenlijke bespreking'), findsOneWidget);
      expect(
        tester.getTopLeft(find.text('Vraag van Mark')).dy,
        lessThan(tester.getTopLeft(find.text('Gezamenlijke bespreking')).dy),
      );
      expect(find.textContaining('Gestart door Anna'), findsWidgets);

      await tester.ensureVisible(find.text('Gezamenlijke bespreking'));
      await tester.tap(find.text('Gezamenlijke bespreking'));
      await tester.pumpAndSettle();
      expect(find.text('Mark vraagt naar de planning.'), findsOneWidget);
      expect(find.text('AI legt de planning uit.'), findsOneWidget);
      expect(find.textContaining('Mark'), findsWidgets);
      expect(fixture.paths, contains('/api/conversations/shared/messages'));
      expect(fixture.paths, isNot(contains('/api/epics/epic-anna/messages')));
      expect(find.byType(TextField), findsNothing);
      expect(find.text('Verstuur'), findsNothing);
      expect(selections.last, 'shared');

      await tester.tap(find.text('← Gesprekken en epics'));
      await tester.pumpAndSettle();
      expect(find.text('Vraag van Anna'), findsNothing);
      expect(find.text('Vraag van Mark'), findsOneWidget);
      expect(selections.last, isNull);
      expect(fixture.methods.toSet(), {'GET'});
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'epics worden aan oorspronkelijke aanvrager gekoppeld en blijven alleen-lezen',
    (tester) async {
      final fixture = ActivityFixture();
      await fixture.mount(tester);
      await selectMark(tester);
      await tester.tap(find.byKey(const ValueKey('activity-epics')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Mark bouwt zoekfunctie'), findsOneWidget);
      expect(find.textContaining('Anna maakt overzicht'), findsNothing);
      expect(find.textContaining('Automatische verbetering'), findsNothing);
      expect(find.textContaining('Aangevraagd door Mark'), findsOneWidget);
      await tester.tap(find.textContaining('Mark bouwt zoekfunctie'));
      await tester.pumpAndSettle();
      expect(find.text('Epic verwijderen'), findsNothing);
      expect(find.text('Verstuur'), findsNothing);
      expect(find.byType(TextField), findsNothing);
      await tester.ensureVisible(find.text('Goedkeuring'));
      await tester.tap(find.text('Goedkeuring'));
      await tester.pumpAndSettle();
      expect(find.text('Functioneel akkoord als superuser'), findsNothing);
      expect(find.text('Architectuur akkoord als superuser'), findsNothing);
      expect(find.text('Vraag functionele aanpassing'), findsNothing);
      expect(find.text('Vraag architectuuronderzoek'), findsNothing);
      expect(fixture.methods.toSet(), {'GET'});
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'directe gesprekslink toont auteurs en verbergt retry en voorstelacties op smal scherm',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 850));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final fixture = ActivityFixture();
      await fixture.mount(
        tester,
        initialConversationId: 'mark-question',
        scale: 2,
      );
      await tester.scrollUntilVisible(
        find.text('Mark vraagt naar de planning.'),
        150,
        scrollable: find
            .descendant(
              of: find.byKey(const ValueKey('conversation-scroll')),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      expect(find.text('Mark vraagt naar de planning.'), findsOneWidget);
      expect(find.text('Verstuur'), findsNothing);
      expect(find.text('Opnieuw proberen'), findsNothing);
      expect(find.text('Maak eerste epicversie'), findsNothing);
      expect(find.byType(TextField), findsNothing);
      expect(tester.takeException(), isNull);
      await tester.tap(find.text('← Gesprekken en epics'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const ValueKey('activity-person-filter')),
      );
      await selectMark(tester);
      expect(tester.takeException(), isNull);
      expect(fixture.methods.toSet(), {'GET'});
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'product owner behoudt eigen vragen en vraagt geen beheeridentiteiten op',
    (tester) async {
      final fixture = ActivityFixture();
      await fixture.mount(
        tester,
        role: 'PRODUCT_OWNER',
        section: 'own-questions',
      );
      expect(find.text('Mijn vragen aan AI'), findsOneWidget);
      expect(find.text('Nieuwe vraag'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('activity-person-filter')),
        findsNothing,
      );
      expect(fixture.paths, isNot(contains('/api/admin/users')));
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'factory owner ziet geblokkeerde epicaanvragen bij Mark zonder een bestaande epic te suggereren',
    (tester) async {
      final fixture = ActivityFixture(includePreparations: true);
      await fixture.mount(tester);
      await selectMark(tester);
      await tester.tap(find.byKey(const ValueKey('activity-epics')));
      await tester.pumpAndSettle();
      expect(find.text('Epics in voorbereiding'), findsOneWidget);
      expect(find.text('Nieuwe aanvraag van Mark'), findsOneWidget);
      expect(find.text('Nieuwe aanvraag van Anna'), findsNothing);
      expect(find.text('Ingetrokken aanvraag'), findsNothing);
      expect(find.textContaining('Uitwerking geblokkeerd'), findsOneWidget);
      await tester.ensureVisible(find.text('Nieuwe aanvraag van Mark'));
      await tester.tap(find.text('Nieuwe aanvraag van Mark'));
      await tester.pumpAndSettle();
      expect(find.text('Uitwerking geblokkeerd'), findsOneWidget);
      expect(
        find.textContaining('er is nog geen epic gemaakt'),
        findsOneWidget,
      );
      expect(find.text('Epic wordt uitgewerkt'), findsNothing);
      expect(find.text('Open gekoppelde epic'), findsNothing);
      expect(find.byType(TextField), findsNothing);
      expect(fixture.methods.toSet(), {'GET'});
      fixture.preparationStatus = 'IN_PROGRESS';
      await tester.pump(const Duration(seconds: 9));
      await tester.pumpAndSettle();
      expect(find.text('Uitwerking geblokkeerd'), findsNothing);
      expect(find.text('Epic wordt uitgewerkt'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'product owner ziet waarom de eerste epicversie nog niet beschikbaar is',
    (tester) async {
      final fixture = ActivityFixture(includePreparations: true);
      await fixture.mount(tester, role: 'PRODUCT_OWNER', section: 'home');
      expect(find.textContaining('Uitwerking geblokkeerd'), findsWidgets);
      await tester.ensureVisible(find.text('Nieuwe aanvraag van Mark'));
      await tester.tap(find.text('Nieuwe aanvraag van Mark'));
      await tester.pumpAndSettle();
      expect(find.text('Uitwerking geblokkeerd'), findsOneWidget);
      expect(find.textContaining('De aanvraag is bewaard'), findsOneWidget);
      expect(find.text('Je epic wordt uitgewerkt'), findsNothing);
      expect(find.text('Opnieuw proberen'), findsNothing);
      expect(fixture.methods.toSet(), {'GET'});
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'product wisselen behoudt persoonfilter en negeert een late verversing',
    (tester) async {
      final fixture = ActivityFixture();
      await fixture.mount(tester);
      await selectMark(tester);
      final delayed = Completer<void>();
      fixture.delayNextConversations = delayed;
      await tester.pump(const Duration(seconds: 9));
      await tester.pumpAndSettle();
      expect(fixture.delayNextConversations, isNull);
      await tester.tap(find.byType(DropdownButtonFormField<String>).first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ander product').last);
      await tester.pumpAndSettle();
      expect(find.text('Geen gesprekken binnen dit filter.'), findsOneWidget);
      delayed.complete();
      await tester.pumpAndSettle();
      expect(find.text('Vraag van Mark'), findsNothing);
      expect(find.text('Geen gesprekken binnen dit filter.'), findsOneWidget);
      expect(find.text('Mark'), findsOneWidget);
      expect(fixture.paths.where((p) => p == '/api/admin/users'), hasLength(1));
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('gesprekslink met ander project opent het bijbehorende product', (
    tester,
  ) async {
    final fixture = ActivityFixture();
    await fixture.mount(
      tester,
      initialProductId: 'other',
      initialConversationId: 'mark-question',
    );
    expect(find.text('Vraag van Mark'), findsOneWidget);
    expect(find.text('Mark vraagt naar de planning.'), findsOneWidget);
    expect(fixture.paths, contains('/api/products/pvdd/conversations'));
    expect(fixture.paths, isNot(contains('/api/products/other/conversations')));
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}

Future<void> selectMark(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('activity-person-filter')));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Mark').last);
  await tester.pumpAndSettle();
}

class ActivityFixture {
  ActivityFixture({this.includePreparations = false});
  final bool includePreparations;
  String preparationStatus = 'BLOCKED';
  final paths = <String>[];
  final methods = <String>[];
  Completer<void>? delayNextConversations;
  late final client = MockClient((request) async {
    paths.add(request.url.path);
    methods.add(request.method);
    if (request.method != 'GET') {
      throw StateError('Inzage mag geen mutatie uitvoeren');
    }
    final path = request.url.path;
    Object? result = [];
    if (path == '/api/products') {
      result = [
        {
          'id': 'pvdd',
          'name': 'PvdD',
          'status': 'ACTIVE',
          'dispatchingEnabled': false,
          'version': 1,
        },
        {
          'id': 'other',
          'name': 'Ander product',
          'status': 'ACTIVE',
          'dispatchingEnabled': false,
          'version': 1,
        },
      ];
    } else if (path == '/api/admin/users') {
      result = [
        {
          'id': 'mark',
          'displayName': 'Mark',
          'email': 'mark@example.test',
          'active': true,
        },
        {
          'id': 'anna',
          'displayName': 'Anna',
          'email': 'anna@example.test',
          'active': true,
        },
      ];
    } else if (path == '/api/products/pvdd/conversations') {
      expect(request.url.queryParameters['includeMessages'], 'false');
      final delay = delayNextConversations;
      delayNextConversations = null;
      if (delay != null) await delay.future;
      result = conversations;
    } else if (path == '/api/products/pvdd/epics') {
      result = epics;
    } else if (path == '/api/products/pvdd/product-requests') {
      result = requests;
    } else if (path.endsWith('/governance')) {
      result = {'configured': true, 'version': 1};
    } else if (path.endsWith('/test-configuration')) {
      return http.Response('{}', 404);
    } else if (path.endsWith('/messages')) {
      result = {
        'messages': [
          {
            'id': 'm1',
            'sequence': 1,
            'sender': 'USER',
            'text': 'Mark vraagt naar de planning.',
            'createdBy': 'mark',
            'authorRole': 'PRODUCT_OWNER',
            'createdAt': '2026-10-11T12:00:00Z',
          },
          {
            'id': 'm2',
            'sequence': 2,
            'sender': 'PRODUCT_ADVISOR',
            'text': 'AI legt de planning uit.',
            'createdAt': '2026-10-11T12:00:01Z',
          },
        ],
        'hasMore': false,
        'nextCursor': null,
      };
    } else if (path.startsWith('/api/conversations/')) {
      result = conversations.singleWhere(
        (c) => c['id'] == path.split('/').last,
      );
    } else if (path.endsWith('/discussions')) {
      result = conversations
          .where((c) => (c['request'] as Map?)?['linkedEpicId'] == 'epic-mark')
          .toList();
    } else if (path.endsWith('/history')) {
      result = [epics.first];
    } else if (path.endsWith('/progress')) {
      result = {'steps': [], 'stories': []};
    }
    return http.Response(
      jsonEncode(result),
      200,
      headers: {'content-type': 'application/json'},
    );
  });

  List<Json> get requests => [
    {
      'id': 'request-mark',
      'requestedBy': 'mark',
      'conversationId': 'mark-epic',
      'linkedEpicId': 'epic-mark',
      'status': 'ROUTED',
    },
    {
      'id': 'request-anna',
      'requestedBy': 'anna',
      'conversationId': 'anna-epic',
      'linkedEpicId': 'epic-anna',
      'status': 'ROUTED',
    },
    if (includePreparations) ...[
      preparation('pending-mark', 'mark', 'Nieuwe aanvraag van Mark'),
      preparation('pending-anna', 'anna', 'Nieuwe aanvraag van Anna'),
      {
        ...preparation('cancelled-mark', 'mark', 'Ingetrokken aanvraag'),
        'status': 'CANCELLED',
      },
    ],
  ];

  Json preparation(String id, String user, String title) => {
    'id': id,
    'productId': 'pvdd',
    'requestedBy': user,
    'conversationId': id,
    'status': 'ROUTING',
    'preparationStatus': preparationStatus,
    'content': {
      'title': title,
      'summary': 'De gewenste verbetering',
      'type': 'EPIC_CANDIDATE',
    },
  };

  List<Json> get conversations => [
    chat('mark-question', 'Vraag van Mark', 'mark', status: 'BLOCKED'),
    chat('anna-question', 'Vraag van Anna', 'anna'),
    chat(
      'shared',
      'Gezamenlijke bespreking',
      'anna',
      participants: ['anna', 'mark'],
      epicId: 'epic-anna',
    ),
    chat('mark-epic', 'Eerste idee van Mark', 'mark', request: requests.first),
    chat('anna-epic', 'Eerste idee van Anna', 'anna', request: requests[1]),
    if (includePreparations)
      ...requests
          .skip(2)
          .map(
            (r) => chat(
              r['conversationId'] as String,
              (r['content'] as Map)['title'] as String,
              r['requestedBy'] as String,
              status: 'PROPOSAL_READY',
              request: r,
            ),
          ),
  ];

  Json chat(
    String id,
    String title,
    String author, {
    String status = 'WAITING_FOR_USER',
    List<String>? participants,
    String? epicId,
    Json? request,
  }) => {
    'id': id,
    'productId': 'pvdd',
    'title': title,
    'createdBy': author,
    'participantUserIds': participants ?? [author],
    'audienceRole': 'PRODUCT_OWNER',
    'purpose': epicId != null || request != null ? 'EPIC' : 'QUESTION',
    'status': status,
    'version': 2,
    'createdAt': '2026-10-10T10:00:00Z',
    'updatedAt': id == 'mark-question'
        ? '2026-10-11T12:00:00.500Z'
        : '2026-10-11T12:00:00Z',
    'epicId': epicId,
    'request': request,
  };

  List<Json> get epics => [
    epic('epic-mark', 'Mark bouwt zoekfunctie', 'request-mark'),
    epic('epic-anna', 'Anna maakt overzicht', 'request-anna'),
    epic('epic-auto', 'Automatische verbetering', null),
  ];

  Json epic(String id, String title, String? requestId) => {
    'id': id,
    'productId': 'pvdd',
    'title': title,
    'status': 'COMPLETED',
    'sourceProductRequestId': requestId,
    'version': 1,
    'contentVersion': 1,
    'summary': 'Een verbetering voor het product',
    'problem': 'Zoeken duurt te lang',
    'solution': 'Maak zoeken eenvoudiger',
    'acceptanceCriteria': ['Zoeken werkt'],
    'createdAt': '2026-10-10T10:00:00Z',
    'updatedAt': '2026-10-11T12:00:00Z',
    'review': {
      'productOwnerApproved': false,
      'architectApproved': false,
      'architectRequired': true,
      'records': [],
      'blockers': [],
    },
  };

  Future<void> mount(
    WidgetTester tester, {
    String role = 'FACTORY_OWNER',
    String section = 'activity',
    String initialProductId = 'pvdd',
    String? initialConversationId,
    ValueChanged<String?>? onConversationSelected,
    double scale = 1,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: productFactoryTheme(),
        home: Builder(
          builder: (context) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(scale)),
            child: Scaffold(
              body: EpicCollaborationPage(
                products: HttpProductGateway(client: client),
                role: role,
                section: section,
                initialProductId: initialProductId,
                initialConversationId: initialConversationId,
                onConversationSelected: onConversationSelected,
                api: CollaborationApi(null, client: client),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }
}
