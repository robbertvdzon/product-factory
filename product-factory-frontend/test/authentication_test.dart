import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_frontend/authentication.dart';

void main() {
  test('centrale client wisselt token om en stuurt CSRF bij logout', () async {
    final requests = <http.Request>[];
    final gateway = HttpAuthenticationGateway(
      backendUrl: 'https://api.example.test',
      client: MockClient((request) async {
        requests.add(request);
        if (request.url.path == '/api/auth/google') {
          return http.Response(
            jsonEncode({
              'authenticated': true,
              'authRequired': true,
              'stakeholderEmail': 'stakeholder@example.com',
              'csrfToken': 'csrf-token',
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }
        return http.Response('', 204);
      }),
    );

    final status = await gateway.googleLogin('google-id-token');
    await gateway.logout(status.csrfToken);

    expect(status.stakeholderEmail, 'stakeholder@example.com');
    expect(jsonDecode(requests.first.body), {'idToken': 'google-id-token'});
    expect(requests.last.headers['X-PF-CSRF'], 'csrf-token');
  });

  test('401 wordt een begrijpelijke verlopen-sessiemelding', () async {
    final gateway = HttpAuthenticationGateway(
      backendUrl: 'https://api.example.test',
      client: MockClient((_) async => http.Response('', 401)),
    );

    expect(
      gateway.session,
      throwsA(
        isA<AuthenticationFailure>().having(
          (failure) => failure.message,
          'message',
          'De sessie is verlopen. Log opnieuw in.',
        ),
      ),
    );
  });

  test('netwerkfout wordt zonder technische details getoond', () async {
    final gateway = HttpAuthenticationGateway(
      backendUrl: 'https://api.example.test',
      client: MockClient(
        (_) async => throw Exception('internal network detail'),
      ),
    );

    expect(
      gateway.session,
      throwsA(
        isA<AuthenticationFailure>().having(
          (failure) => failure.message,
          'message',
          'Product Factory is tijdelijk niet bereikbaar.',
        ),
      ),
    );
  });
}
