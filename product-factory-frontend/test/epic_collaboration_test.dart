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
        find.text('+ Nieuw idee'),
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
      await tester.pumpWidget(const SizedBox());
      await tester.pump();
    });
  }
}
