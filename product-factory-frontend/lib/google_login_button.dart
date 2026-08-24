import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';

import 'google_login_web.dart' as web;

class GoogleLoginButton extends StatefulWidget {
  const GoogleLoginButton({
    required this.clientId,
    required this.onIdToken,
    super.key,
  });

  final String clientId;
  final ValueChanged<String> onIdToken;

  @override
  State<GoogleLoginButton> createState() => _GoogleLoginButtonState();
}

class _GoogleLoginButtonState extends State<GoogleLoginButton> {
  StreamSubscription<GoogleSignInAuthenticationEvent>? _events;
  String? _error;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    unawaited(_initialize());
  }

  Future<void> _initialize() async {
    if (widget.clientId.isEmpty) {
      setState(() => _error = 'Google-login is nog niet geconfigureerd.');
      return;
    }
    final signIn = GoogleSignIn.instance;
    try {
      await signIn.initialize(clientId: widget.clientId);
      if (!mounted) return;
      setState(() => _initialized = true);
      _events = signIn.authenticationEvents.listen(
        (event) {
          if (event is GoogleSignInAuthenticationEventSignIn) {
            final token = event.user.authentication.idToken;
            if (token == null || token.isEmpty) {
              setState(
                () => _error = 'Google heeft geen geldig loginbewijs geleverd.',
              );
            } else {
              widget.onIdToken(token);
            }
          }
        },
        onError: (_) => setState(() => _error = 'Google-login is niet gelukt.'),
      );
      unawaited(signIn.attemptLightweightAuthentication());
    } on Object {
      if (mounted) {
        setState(() => _error = 'Google-login kon niet worden gestart.');
      }
    }
  }

  @override
  void dispose() {
    unawaited(_events?.cancel());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final error = _error;
    if (kIsWeb) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_initialized) web.renderGoogleButton(),
          if (error != null) ...[
            if (_initialized) const SizedBox(height: 12),
            Text(
              error,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
              textAlign: TextAlign.center,
            ),
          ],
        ],
      );
    }
    if (error != null) {
      return Text(
        error,
        style: TextStyle(color: Theme.of(context).colorScheme.error),
      );
    }
    return FilledButton(
      onPressed: () => GoogleSignIn.instance.authenticate(),
      child: const Text('Inloggen met Google'),
    );
  }
}
