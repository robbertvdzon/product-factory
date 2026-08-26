import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/product_workspace.dart';

void main() {
  test('productclient leest value-class ids en stuurt CSRF bij mutaties', () async {
    http.Request? mutation;
    final client = MockClient((request) async {
      if (request.method == 'GET') {
        return http.Response(
          jsonEncode([
            {
              'id': {'value': 'hkh-autopilot'},
              'name': 'HKH Autopilot',
              'status': 'ACTIVE',
              'dispatchingEnabled': false,
              'version': 1,
            },
          ]),
          200,
          headers: {'content-type': 'application/json'},
        );
      }
      mutation = request;
      return http.Response('', 201);
    });
    final gateway = HttpProductGateway(
      client: client,
      backendUrl: 'https://factory.example',
      csrfToken: 'csrf-from-session',
    );

    final products = await gateway.products();
    expect(products.single.id, 'hkh-autopilot');
    await gateway.createProduct('Nieuw product', 'nieuw-product');

    expect(mutation?.headers['X-PF-CSRF'], 'csrf-from-session');
    final body = jsonDecode(mutation!.body) as Map<String, Object?>;
    expect(body['name'], 'Nieuw product');
    expect(body['actor'], isNull);
    expect(body['idempotencyKey'], startsWith('ui-product-'));
  });

  test('versieconflict blijft herkenbaar voor de UI', () async {
    final gateway = HttpProductGateway(
      client: MockClient((_) async => http.Response(
        jsonEncode({'code': 'CONFLICT', 'message': 'Product is intussen gewijzigd.'}),
        409,
      )),
      backendUrl: 'https://factory.example',
    );

    await expectLater(
      gateway.setStatus(
        const ProductSummary(id: 'p', name: 'P', status: 'ACTIVE', dispatchingEnabled: false, version: 2),
        'INACTIVE',
      ),
      throwsA(isA<ProductFailure>().having((e) => e.status, 'status', 409)),
    );
  });
}
