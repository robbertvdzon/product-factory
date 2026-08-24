import 'dart:async';

import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';

import 'application_shell.dart';
import 'authentication.dart';
import 'build_identity.dart';
import 'configuration.dart';
import 'google_login_button.dart';
import 'frontend_version_monitor.dart';
import 'testbed.dart';

void main() {
  runApp(ProductFactoryApp(federatedSignOut: GoogleSignIn.instance.signOut));
}

typedef GoogleLoginButtonBuilder =
    Widget Function(ValueChanged<String> onIdToken);

class ProductFactoryApp extends StatelessWidget {
  const ProductFactoryApp({
    super.key,
    this.authenticationGateway,
    this.googleLoginButtonBuilder,
    this.federatedSignOut,
    this.versionGateway,
    this.frontendVersionSource,
    this.testControlGateway,
  });

  final AuthenticationGateway? authenticationGateway;
  final GoogleLoginButtonBuilder? googleLoginButtonBuilder;
  final Future<void> Function()? federatedSignOut;
  final VersionGateway? versionGateway;
  final FrontendVersionSource? frontendVersionSource;
  final TestControlGateway? testControlGateway;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Product Factory',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff155e75)),
        scaffoldBackgroundColor: const Color(0xfff6f8f8),
        useMaterial3: true,
      ),
      home: AuthenticationGate(
        gateway: authenticationGateway ?? HttpAuthenticationGateway(),
        googleLoginButtonBuilder: googleLoginButtonBuilder,
        federatedSignOut: federatedSignOut,
        versionGateway: versionGateway ?? HttpVersionGateway(),
        frontendVersionSource: frontendVersionSource,
        testControlGateway: testControlGateway,
      ),
    );
  }
}

class AuthenticationGate extends StatefulWidget {
  const AuthenticationGate({
    required this.gateway,
    this.googleLoginButtonBuilder,
    this.federatedSignOut,
    required this.versionGateway,
    this.frontendVersionSource,
    this.testControlGateway,
    super.key,
  });

  final AuthenticationGateway gateway;
  final GoogleLoginButtonBuilder? googleLoginButtonBuilder;
  final Future<void> Function()? federatedSignOut;
  final VersionGateway versionGateway;
  final FrontendVersionSource? frontendVersionSource;
  final TestControlGateway? testControlGateway;

  @override
  State<AuthenticationGate> createState() => _AuthenticationGateState();
}

class _AuthenticationGateState extends State<AuthenticationGate> {
  AuthenticationStatus? _status;
  String? _error;
  bool _busy = true;

  @override
  void initState() {
    super.initState();
    unawaited(_loadSession());
  }

  Future<void> _loadSession() async => _perform(widget.gateway.session);

  Future<void> _login(String idToken) async =>
      _perform(() => widget.gateway.googleLogin(idToken));

  Future<void> _logout() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.gateway.logout(_status?.csrfToken);
      await widget.federatedSignOut?.call();
      if (!mounted) return;
      setState(
        () => _status = const AuthenticationStatus(
          authenticated: false,
          authRequired: true,
        ),
      );
    } on AuthenticationFailure catch (failure) {
      if (!mounted) return;
      setState(() => _error = failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _perform(
    Future<AuthenticationStatus> Function() operation,
  ) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final status = await operation();
      if (!mounted) return;
      setState(() => _status = status);
    } on AuthenticationFailure catch (failure) {
      if (!mounted) return;
      setState(() => _error = failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_busy && _status == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final status = _status;
    if (status == null && _error != null) {
      return Scaffold(
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.cloud_off, size: 48),
                const SizedBox(height: 16),
                Text(_error!, textAlign: TextAlign.center),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: _loadSession,
                  child: const Text('Opnieuw proberen'),
                ),
              ],
            ),
          ),
        ),
      );
    }
    if (status?.authenticated == true) {
      return FoundationPage(
        showAcceptanceBanner:
            !status!.authRequired && status.environment == 'acceptance',
        stakeholderEmail: status.stakeholderEmail,
        onLogout: status.authRequired ? _logout : null,
        versionGateway: widget.versionGateway,
        error: _error,
        frontendVersionSource: widget.frontendVersionSource,
        testControlGateway: widget.testControlGateway,
        runtimeEnvironment: status.environment,
      );
    }
    return LoginPage(
      busy: _busy,
      error: _error,
      onIdToken: _login,
      googleLoginButtonBuilder: widget.googleLoginButtonBuilder,
      googleClientId: status?.googleClientId,
    );
  }
}

class LoginPage extends StatelessWidget {
  const LoginPage({
    required this.busy,
    required this.onIdToken,
    this.error,
    this.googleLoginButtonBuilder,
    this.googleClientId,
    super.key,
  });

  final bool busy;
  final String? error;
  final ValueChanged<String> onIdToken;
  final GoogleLoginButtonBuilder? googleLoginButtonBuilder;
  final String? googleClientId;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.factory_outlined,
                      size: 48,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(height: 20),
                    Text(
                      'Product Factory',
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      'Log in met het toegestane Google-account om verder te gaan.',
                    ),
                    const SizedBox(height: 24),
                    if (busy)
                      const CircularProgressIndicator()
                    else
                      (googleLoginButtonBuilder?.call(onIdToken) ??
                          GoogleLoginButton(
                            clientId:
                                googleClientId ??
                                AppConfiguration.googleClientId,
                            onIdToken: onIdToken,
                          )),
                    if (error != null) ...[
                      const SizedBox(height: 20),
                      Text(
                        error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class FoundationPage extends StatelessWidget {
  const FoundationPage({
    this.showAcceptanceBanner = false,
    this.stakeholderEmail,
    this.onLogout,
    this.versionGateway,
    this.error,
    this.frontendVersionSource,
    this.onReload,
    this.currentBuildIdentity,
    this.testControlGateway,
    this.runtimeEnvironment,
    super.key,
  });

  final bool showAcceptanceBanner;
  final String? stakeholderEmail;
  final VoidCallback? onLogout;
  final VersionGateway? versionGateway;
  final String? error;
  final FrontendVersionSource? frontendVersionSource;
  final VoidCallback? onReload;
  final BuildIdentity? currentBuildIdentity;
  final TestControlGateway? testControlGateway;
  final String? runtimeEnvironment;

  @override
  Widget build(BuildContext context) => ApplicationShell(
    showAcceptanceBanner: showAcceptanceBanner,
    stakeholderEmail: stakeholderEmail,
    onLogout: onLogout,
    versionGateway: versionGateway ?? HttpVersionGateway(),
    error: error,
    frontendVersionSource: frontendVersionSource,
    onReload: onReload,
    currentBuildIdentity: currentBuildIdentity,
    testControlGateway: testControlGateway,
    runtimeEnvironment: runtimeEnvironment,
  );
}
