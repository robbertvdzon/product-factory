import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/memory_ai_management.dart';

void main() {
  test(
    'geheugenclient leest value-class ids en bewaart stakeholderactor server-side',
    () async {
      http.Request? mutation;
      final gateway = HttpMemoryAiGateway(
        backendUrl: 'https://factory.example',
        csrfToken: 'csrf-session',
        client: MockClient((request) async {
          if (request.method == 'GET') {
            return http.Response(
              jsonEncode([
                {
                  'key': {'value': 'PRODUCT_DESIGNER_MVP'},
                  'displayName': 'Productontwerper',
                  'capability': 'product-design',
                },
              ]),
              200,
            );
          }
          mutation = request;
          return http.Response(jsonEncode({'id': 'memory-1'}), 201);
        }),
      );

      final roles = await gateway.roles('hkh-autopilot');
      expect((roles.single['key'] as Map)['value'], 'PRODUCT_DESIGNER_MVP');
      await gateway.add(
        'hkh-autopilot',
        'PRODUCT_DESIGNER_MVP',
        'Richting',
        'Begin klein',
        'Stakeholderkeuze',
      );

      expect(mutation?.headers['X-PF-CSRF'], 'csrf-session');
      expect(
        mutation?.url.path,
        contains('/agent-memory/roles/PRODUCT_DESIGNER_MVP/items'),
      );
      final body = jsonDecode(mutation!.body) as Map<String, Object?>;
      expect(body['actor'], isNull);
      expect(body['expectedVersionId'], isNull);
      expect(body['idempotencyKey'], startsWith('ui-memory-add-'));
    },
  );

  test('AI-modelupdate stuurt globale jobkey en verwachte versie', () async {
    http.Request? mutation;
    final gateway = HttpMemoryAiGateway(
      backendUrl: 'https://factory.example',
      client: MockClient((request) async {
        mutation = request;
        return http.Response('{}', 200);
      }),
    );

    await gateway.updateAi(
      {
        'jobKey': {'value': 'MEETING.CONVERSE'},
        'version': 3,
      },
      'CLAUDE',
      'claude-sonnet-4-5',
      true,
    );

    expect(mutation?.url.path, '/api/ai/job-configurations/MEETING.CONVERSE');
    final body = jsonDecode(mutation!.body) as Map<String, Object?>;
    expect(body['expectedVersion'], 3);
    expect(body['provider'], 'CLAUDE');
    expect(body['idempotencyKey'], startsWith('ui-ai-settings-'));
  });

  test('409 blijft herkenbaar als versieconflict', () async {
    final gateway = HttpMemoryAiGateway(
      backendUrl: 'https://factory.example',
      client: MockClient(
        (_) async => http.Response(
          jsonEncode({'message': 'Geheugenitem is intussen gewijzigd.'}),
          409,
        ),
      ),
    );

    await expectLater(
      gateway.retract('product', 'TESTER_MVP', {
        'id': {'value': 'item'},
        'activeVersionId': {'value': 'version'},
      }, 'Niet meer geldig'),
      throwsA(
        isA<MemoryAiFailure>().having((error) => error.status, 'status', 409),
      ),
    );
  });
}
