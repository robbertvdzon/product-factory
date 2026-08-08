import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:google_sign_in/google_sign_in.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class DashboardSession {
  DashboardSession({required this.apiBaseUrl, required this.clientId})
    : _google = GoogleSignIn(
        clientId: kIsWeb ? clientId : null,
        serverClientId: kIsWeb ? null : clientId,
        scopes: const ['email'],
      ) {
    _subscription = _google.onCurrentUserChanged.listen((account) async {
      if (account != null) changes.add(await _authenticate(account));
    });
  }
  final String apiBaseUrl;
  final String clientId;
  final GoogleSignIn _google;
  final changes = StreamController<AuthenticatedSession>.broadcast();
  StreamSubscription<GoogleSignInAccount?>? _subscription;

  static const _tokenPrefsKey = 'pf_session_token';
  static const _emailPrefsKey = 'pf_session_email';

  /// Herstelt eerst een eerder bewaard token (geen Google-round trip nodig, dus geen herhaalde
  /// inlogprompt bij elke pagina-ververs) en valt alleen terug op Google's eigen stille sign-in
  /// als er niets bewaard is of het bewaarde token niet langer geldig blijkt.
  Future<AuthenticatedSession?> bootstrap() async {
    final stored = await _restoreStoredSession();
    if (stored != null) return stored;
    final account = await _google.signInSilently();
    return account == null ? null : _authenticate(account);
  }

  Future<AuthenticatedSession?> signIn() async {
    final account = await _google.signIn();
    return account == null ? null : _authenticate(account);
  }

  Future<AuthenticatedSession?> _restoreStoredSession() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString(_tokenPrefsKey);
    final email = prefs.getString(_emailPrefsKey);
    if (token == null || email == null) return null;
    if (await _isValid(token)) return AuthenticatedSession(email, token);
    await prefs.remove(_tokenPrefsKey);
    await prefs.remove(_emailPrefsKey);
    return null;
  }

  Future<bool> _isValid(String token) async {
    final response = await http.get(
      Uri.parse('$apiBaseUrl/api/session'),
      headers: {'Authorization': 'Bearer $token'},
    );
    return response.statusCode == 200;
  }

  Future<AuthenticatedSession> _authenticate(GoogleSignInAccount account) async {
    final token = (await account.authentication).idToken;
    if (token == null) throw StateError('Google leverde geen ID-token.');
    final response = await http.get(
      Uri.parse('$apiBaseUrl/api/session'),
      headers: {'Authorization': 'Bearer $token'},
    );
    if (response.statusCode != 200) {
      throw StateError('Account heeft geen toegang.');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final session = AuthenticatedSession(json['email'] as String, token);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_tokenPrefsKey, session.token);
    await prefs.setString(_emailPrefsKey, session.email);
    return session;
  }

  Future<void> signOut() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_tokenPrefsKey);
    await prefs.remove(_emailPrefsKey);
    await _google.signOut();
  }

  void dispose() {
    _subscription?.cancel();
    changes.close();
  }
}

class AuthenticatedSession {
  const AuthenticatedSession(this.email, this.token);
  final String email;
  final String token;
}
