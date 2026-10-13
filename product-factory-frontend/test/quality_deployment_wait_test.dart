import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/product_workspace.dart';

import 'widget_test.dart' show appTextContaining;

void main() {
  for (final earlierAttempts in [0, 4]) {
    testWidgets(
      'wachten op uitrol toont automatisch vervolg zonder nieuwe testpoging of retryknop ($earlierAttempts eerdere pogingen)',
      (tester) async {
        await tester.binding.setSurfaceSize(const Size(390, 1000));
        addTearDown(() => tester.binding.setSurfaceSize(null));
        final client = MockClient((request) async {
          final path = request.url.path;
          final Object response;
          if (path == '/api/products') {
            response = [
              {
                'id': 'pvdd',
                'name': 'PvdD',
                'status': 'ACTIVE',
                'dispatchingEnabled': true,
                'version': 1,
              },
            ];
          } else if (path.endsWith('/quality/work-items')) {
            response = [
              {
                'id': 'wait-for-deployment',
                'type': 'VERIFY_EPIC',
                'status': 'PENDING',
                'errorCode': 'WAITING_FOR_DEPLOYMENT',
                'blockedReason':
                    'Story hkh-348 is klaar; acceptatie draait nog de vorige versie.',
                'attemptCount': earlierAttempts,
                'retryable': true,
              },
            ];
          } else if (path.endsWith('/quality/current') ||
              path.endsWith('/assignment')) {
            return http.Response('', 404);
          } else if (path.endsWith('/live') ||
              path.endsWith('/dispatcher/status')) {
            response = <String, Object?>{};
          } else {
            response = [];
          }
          return http.Response(
            jsonEncode(response),
            200,
            headers: {'content-type': 'application/json'},
          );
        });
        await tester.pumpWidget(
          MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(1.5)),
              child: child!,
            ),
            home: Scaffold(
              body: ProductWorkspacePage(
                gateway: HttpProductGateway(client: client),
                section: ProductWorkspaceSection.quality,
                initialProductId: 'pvdd',
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.ensureVisible(appTextContaining('Wacht op uitrol'));

        expect(appTextContaining('Wacht op uitrol'), findsOneWidget);
        expect(appTextContaining('Story hkh-348 is klaar'), findsOneWidget);
        expect(
          appTextContaining('start automatisch na de uitrol'),
          findsOneWidget,
        );
        expect(appTextContaining('Er draait nu geen test'), findsOneWidget);
        expect(
          appTextContaining(
            earlierAttempts == 0
                ? 'Nog geen testpoging.'
                : 'Eerdere testpogingen: 4.',
          ),
          findsOneWidget,
        );
        expect(appTextContaining('Poging 0'), findsNothing);
        expect(find.text('Na herstel hervatten'), findsNothing);
        expect(find.text('Nu opnieuw proberen'), findsNothing);
        expect(appTextContaining('Aandacht nodig'), findsNothing);
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
}
