import 'dart:convert';

import 'package:http/http.dart' as http;

import 'configuration.dart';
import 'http_client_factory.dart';

class AuthenticationStatus {
  const AuthenticationStatus({
    required this.authenticated,
    required this.authRequired,
    this.stakeholderEmail,
    this.csrfToken,
    this.environment = 'local',
    this.googleClientId,
  });

  factory AuthenticationStatus.fromJson(Map<String, Object?> json) =>
      AuthenticationStatus(
        authenticated: json['authenticated'] == true,
        authRequired: json['authRequired'] == true,
        stakeholderEmail: json['stakeholderEmail'] as String?,
        csrfToken: json['csrfToken'] as String?,
        environment: json['environment'] as String? ?? 'local',
        googleClientId: json['googleClientId'] as String?,
      );

  final bool authenticated;
  final bool authRequired;
  final String? stakeholderEmail;
  final String? csrfToken;
  final String environment;
  final String? googleClientId;
}

abstract interface class AuthenticationGateway {
  Future<AuthenticationStatus> session();
  Future<AuthenticationStatus> googleLogin(String idToken);
  Future<void> logout(String? csrfToken);
}

class HttpAuthenticationGateway implements AuthenticationGateway {
  HttpAuthenticationGateway({http.Client? client, String? backendUrl})
    : _client = client ?? createHttpClient(),
      _backendUrl = (backendUrl ?? AppConfiguration.backendUrl).replaceAll(
        RegExp(r'/$'),
        '',
      );

  final http.Client _client;
  final String _backendUrl;

  @override
  Future<AuthenticationStatus> session() async {
    final response = await _send(() => _client.get(_uri('/api/auth/session')));
    return _statusResponse(response);
  }

  @override
  Future<AuthenticationStatus> googleLogin(String idToken) async {
    final response = await _send(
      () => _client.post(
        _uri('/api/auth/google'),
        headers: const {'Content-Type': 'application/json'},
        body: jsonEncode({'idToken': idToken}),
      ),
    );
    return _statusResponse(response);
  }

  @override
  Future<void> logout(String? csrfToken) async {
    final response = await _send(
      () => _client.post(
        _uri('/api/auth/logout'),
        headers: csrfToken == null ? const {} : {'X-PF-CSRF': csrfToken},
      ),
    );
    if (response.statusCode != 204) throw _failure(response);
  }

  Uri _uri(String path) => Uri.parse('$_backendUrl$path');

  Future<http.Response> _send(Future<http.Response> Function() request) async {
    try {
      return await request();
    } on Object {
      throw const AuthenticationFailure(
        'Product Factory is tijdelijk niet bereikbaar.',
      );
    }
  }

  AuthenticationStatus _statusResponse(http.Response response) {
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw _failure(response);
    }
    final Object? decoded;
    try {
      decoded = jsonDecode(utf8.decode(response.bodyBytes));
    } on Object {
      throw const AuthenticationFailure('Ongeldig antwoord van de server.');
    }
    if (decoded is! Map<String, Object?>) {
      throw const AuthenticationFailure('Ongeldig antwoord van de server.');
    }
    return AuthenticationStatus.fromJson(decoded);
  }

  AuthenticationFailure _failure(http.Response response) {
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      if (decoded is Map<String, Object?> && decoded['message'] is String) {
        return AuthenticationFailure(decoded['message']! as String);
      }
    } on FormatException {
      // De generieke, niet-gevoelige foutmelding hieronder blijft leidend.
    }
    if (response.statusCode == 401) {
      return const AuthenticationFailure(
        'De sessie is verlopen. Log opnieuw in.',
      );
    }
    return const AuthenticationFailure(
      'Product Factory is tijdelijk niet bereikbaar.',
    );
  }
}

class AuthenticationFailure implements Exception {
  const AuthenticationFailure(this.message);
  final String message;
}
