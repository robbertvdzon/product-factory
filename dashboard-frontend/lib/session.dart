import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:google_sign_in/google_sign_in.dart';
import 'package:http/http.dart' as http;

class DashboardSession {
  DashboardSession({required this.apiBaseUrl, required this.clientId})
      : _google = GoogleSignIn(
          clientId: kIsWeb ? clientId : null,
          serverClientId: kIsWeb ? null : clientId,
          scopes: const ['email'],
        ) {
    _subscription = _google.onCurrentUserChanged.listen((account) async {
      if (account != null) changes.add(await authenticate(account));
    });
  }
  final String apiBaseUrl;
  final String clientId;
  final GoogleSignIn _google;
  final changes = StreamController<AuthenticatedSession>.broadcast();
  StreamSubscription<GoogleSignInAccount?>? _subscription;

  Future<AuthenticatedSession?> bootstrap() async {
    final account = await _google.signInSilently();
    return account == null ? null : authenticate(account);
  }
  Future<AuthenticatedSession?> signIn() async {
    final account = await _google.signIn();
    return account == null ? null : authenticate(account);
  }
  Future<AuthenticatedSession> authenticate(GoogleSignInAccount account) async {
    final token = (await account.authentication).idToken;
    if (token == null) throw StateError('Google leverde geen ID-token.');
    final response = await http.get(Uri.parse('$apiBaseUrl/api/session'), headers: {'Authorization': 'Bearer $token'});
    if (response.statusCode != 200) throw StateError('Account heeft geen toegang.');
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return AuthenticatedSession(json['email'] as String, token);
  }
  Future<void> signOut() => _google.signOut();
  void dispose() { _subscription?.cancel(); changes.close(); }
}

class AuthenticatedSession {
  const AuthenticatedSession(this.email, this.token);
  final String email;
  final String token;
}
