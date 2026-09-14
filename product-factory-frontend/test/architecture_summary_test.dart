import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/epic_collaboration.dart';
import 'package:product_factory_frontend/product_workspace.dart';

void main() {
  for (final (databaseLevel, missing, summary) in [
    ('NONE', false, 'Er is geen architectuurverandering.'),
    (
      'COMPATIBLE',
      false,
      'Er zijn wijzigingen binnen de bestaande architectuur.',
    ),
    ('MATERIAL', false, 'Er zijn architectuurwijzigingen.'),
    ('UNKNOWN', false, 'De architectuurimpact is nog niet volledig bepaald.'),
    ('NONE', true, 'De architectuurimpact is nog niet volledig bepaald.'),
  ]) {
    testWidgets('architectuursamenvatting $databaseLevel ontbrekend=$missing', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(1400, 1800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final epic = {
        'id': 'epic',
        'productId': 'p',
        'title': 'Homepage',
        'status': 'AWAITING_PRODUCT_OWNER_APPROVAL',
        'version': 1,
        'contentVersion': 1,
        'review': {'architectApproved': false},
        'impact': {
          'productAi': {'changed': false},
          'items': [
            for (final category in [
              'DATABASE',
              'MIGRATION',
              'EXTERNAL_SYSTEM',
              'FRONTEND',
              'ACCESS',
              'PRODUCT_AI',
              if (!missing) 'INFRASTRUCTURE',
            ])
              {
                'category': category,
                'level': category == 'DATABASE'
                    ? databaseLevel
                    : category == 'FRONTEND'
                    ? 'COMPATIBLE'
                    : 'NONE',
                'summary': 'Onderbouwing $category',
                'evidence': ['Bewijs uit code'],
              },
          ],
        },
      };
      final client = MockClient((request) async {
        final path = request.url.path;
        final Object value = path == '/api/products'
            ? [
                {
                  'id': 'p',
                  'name': 'Project',
                  'status': 'ACTIVE',
                  'version': 1,
                  'dispatchingEnabled': false,
                },
              ]
            : path.endsWith('/epics')
            ? [epic]
            : path.endsWith('/governance')
            ? {'configured': true, 'version': 1}
            : path.endsWith('/progress')
            ? {'steps': [], 'stories': []}
            : [];
        if (path.endsWith('/test-configuration')) {
          return http.Response('{}', 404);
        }
        return http.Response(jsonEncode(value), 200);
      });
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: EpicCollaborationPage(
              role: 'ARCHITECT',
              products: HttpProductGateway(client: client),
              api: CollaborationApi(null, client: client),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Homepage'));
      await tester.pumpAndSettle();
      expect(find.text(summary), findsOneWidget);
      expect(find.textContaining('Onderbouwing FRONTEND'), findsNothing);
      if (['COMPATIBLE', 'MATERIAL'].contains(databaseLevel)) {
        expect(find.text('Database: Onderbouwing DATABASE'), findsOneWidget);
      }
      await tester.tap(find.text('Onderbouwing per onderdeel'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Onderbouwing FRONTEND'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
