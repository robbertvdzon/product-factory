import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/epic_collaboration.dart';
import 'package:product_factory_frontend/product_workspace.dart';

void main() {
  for (final section in ['overview', 'own-questions']) {
    testWidgets('$section combineert projecten en start het juiste gesprek', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(1100, 1400));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final writes = <http.Request>[];
      final loaded = <String>{};
      Map<String, dynamic>? conversation;
      final client = MockClient((request) async {
        final path = request.url.path;
        Object result = [];
        loaded.add(path);
        if (request.method == 'POST') {
          writes.add(request);
          if (path.endsWith('/conversations')) {
            final body = jsonDecode(request.body) as Map<String, dynamic>;
            conversation = {
              'id': 'chat',
              'productId': 'two',
              'title': 'Mijn wens',
              'purpose': body['purpose'],
              'status': 'OPEN',
              'version': 1,
              'messages': [],
            };
          }
          result = {'id': 'chat'};
        } else if (path == '/api/products') {
          result = [
            for (final id in ['one', 'two'])
              {
                'id': id,
                'name': 'Project $id',
                'status': 'ACTIVE',
                'dispatchingEnabled': false,
                'version': 1,
              },
          ];
        } else if (path.endsWith('/messages')) {
          result = {'hasMore':false,'messages':[
            {'id':'m1','sender':'USER','text':'Een vraag','createdAt':'2026-09-14T12:30:00'},
            {'id':'m2','sender':'PRODUCT_ADVISOR','text':'Een antwoord','createdAt':'2026-09-14T12:31:00'},
          ]};
        } else if (path.endsWith('/epics')) {
          final id = path.split('/')[3];
          result = [
            for (final status in ['AVAILABLE', 'COMPLETED'])
              {
                'id': '$id-$status',
                'productId': id,
                'title': '$id $status',
                'status': status,
                'version': 1,
                'contentVersion': 1,
              },
          ];
        } else if (path.endsWith('/conversations')) {
          result = conversation == null ? [] : [conversation!];
        } else if (path == '/api/conversations/chat') {
          result = conversation!;
        } else if (path.endsWith('/governance')) {
          result = {'configured': true, 'version': 1};
        } else if (path.endsWith('/test-configuration')) {
          return http.Response('{}', 404);
        }
        return http.Response(
          jsonEncode(result),
          200,
          headers: {'content-type': 'application/json'},
        );
      });
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: EpicCollaborationPage(
              products: HttpProductGateway(client: client),
              role: 'PRODUCT_OWNER',
              section: section,
              api: CollaborationApi(null, client: client),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Alle projecten'), findsOneWidget);
      expect(
        loaded,
        containsAll([
          '/api/products/one/epics',
          '/api/products/two/epics',
          '/api/products/one/questions',
          '/api/products/two/questions',
        ]),
      );
      if (section == 'overview') {
        expect(find.text('one AVAILABLE'), findsOneWidget);
        expect(find.text('two AVAILABLE'), findsOneWidget);
        expect(find.textContaining('COMPLETED'), findsNothing);
      }
      await tester.tap(
        find.text(section == 'overview' ? 'Nieuwe epic' : 'Nieuwe vraag'),
      );
      await tester.pumpAndSettle();
      final projectSelector = find.byType(DropdownButtonFormField<String>).last;
      await tester.tap(projectSelector);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Project two').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byType(TextField),
        'Ik wil de homepage bespreken.',
      );
      await tester.tap(
        find.text(
          section == 'overview' ? 'Maak eerste versie' : 'Stel vraag aan AI',
        ),
      );
      await tester.pumpAndSettle();
      expect(writes.length, 2);
      expect(writes.first.url.path, '/api/products/two/conversations');
      expect(
        jsonDecode(writes.first.body)['purpose'],
        section == 'overview' ? 'EPIC' : 'QUESTION',
      );
      expect(writes.last.url.path, '/api/conversations/chat/messages');
      expect(
        jsonDecode(writes.last.body)['text'],
        'Ik wil de homepage bespreken.',
      );
      expect(find.text('14 sep 2026 · 12:30'), findsOneWidget);
      expect(find.text('14 sep 2026 · 12:31'), findsOneWidget);
      expect(tester.getTopLeft(find.text(section == 'overview' ? '← Mijn epics' : '← Mijn vragen aan AI')).dy,
          lessThan(tester.getTopLeft(find.text('Een antwoord')).dy));
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
