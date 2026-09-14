import 'dart:async';

import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';

import 'application_shell.dart';
import 'authentication.dart';
import 'build_identity.dart';
import 'configuration.dart';
import 'google_login_button.dart';
import 'frontend_version_monitor.dart';
import 'memory_ai_management.dart';
import 'navigation_location.dart';
import 'testbed.dart';
import 'product_workspace.dart';
import 'product_factory_theme.dart';

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
    this.productGateway,
    this.memoryAiGateway,
    this.navigationLocation,
  });

  final AuthenticationGateway? authenticationGateway;
  final GoogleLoginButtonBuilder? googleLoginButtonBuilder;
  final Future<void> Function()? federatedSignOut;
  final VersionGateway? versionGateway;
  final FrontendVersionSource? frontendVersionSource;
  final TestControlGateway? testControlGateway;
  final ProductGateway? productGateway;
  final MemoryAiGateway? memoryAiGateway;
  final NavigationLocation? navigationLocation;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Product Factory',
      debugShowCheckedModeBanner: false,
      theme: productFactoryTheme(),
      home: SelectionArea(
        child: AuthenticationGate(
          gateway: authenticationGateway ?? HttpAuthenticationGateway(),
          googleLoginButtonBuilder: googleLoginButtonBuilder,
          federatedSignOut: federatedSignOut,
          versionGateway: versionGateway ?? HttpVersionGateway(),
          frontendVersionSource: frontendVersionSource,
          testControlGateway: testControlGateway,
          productGateway: productGateway,
          memoryAiGateway: memoryAiGateway,
          navigationLocation: navigationLocation,
        ),
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
    this.productGateway,
    this.memoryAiGateway,
    this.navigationLocation,
    super.key,
  });

  final AuthenticationGateway gateway;
  final GoogleLoginButtonBuilder? googleLoginButtonBuilder;
  final Future<void> Function()? federatedSignOut;
  final VersionGateway versionGateway;
  final FrontendVersionSource? frontendVersionSource;
  final TestControlGateway? testControlGateway;
  final ProductGateway? productGateway;
  final MemoryAiGateway? memoryAiGateway;
  final NavigationLocation? navigationLocation;

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

  Future<void> _debugLogin(String token, String? email, String? role) async =>
      _perform(() => widget.gateway.debugLogin(token, email, role));

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

  Future<void> _switchRole(String role) async {
    final csrfToken = _status?.csrfToken;
    await _perform(() async {
      await widget.gateway.setActingRole(role, csrfToken);
      return widget.gateway.session();
    });
  }

  Future<void> _viewAs(String userId, String role) async {
    final csrfToken = _status?.csrfToken;
    await _perform(() async {
      await widget.gateway.viewAs(userId, role, csrfToken);
      return widget.gateway.session();
    });
  }

  Future<void> _clearViewAs() async {
    final csrfToken = _status?.csrfToken;
    await _perform(() async {
      await widget.gateway.clearViewAs(csrfToken);
      return widget.gateway.session();
    });
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
        key: ValueKey(status!.actingRole),
        showAcceptanceBanner:
            !status.authRequired && status.environment == 'acceptance',
        stakeholderEmail: status.stakeholderEmail,
        onLogout: status.authRequired ? _logout : null,
        versionGateway: widget.versionGateway,
        error: _error,
        frontendVersionSource: widget.frontendVersionSource,
        testControlGateway: widget.testControlGateway,
        productGateway: widget.productGateway,
        memoryAiGateway: widget.memoryAiGateway,
        navigationLocation: widget.navigationLocation,
        runtimeEnvironment: status.environment,
        csrfToken: status.csrfToken,
        isFactoryOwner:
            !status.authRequired ||
            status.globalRoles.contains('FACTORY_OWNER'),
        productMemberships: status.productMemberships,
        actingRole: status.actingRole,
        availableRoles: status.availableRoles,
        onSwitchRole: status.canSwitchRole && !_busy ? _switchRole : null,
        viewingAs: status.viewingAs,
        authenticatedEmail: status.authenticatedEmail,
        onViewAs: !_busy ? _viewAs : null,
        onClearViewAs: status.viewingAs && !_busy ? _clearViewAs : null,
      );
    }
    if (Uri.base.path == '/debug-login') {
      return DebugLoginPage(busy: _busy, error: _error, onLogin: _debugLogin);
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

class DebugLoginPage extends StatefulWidget {
  const DebugLoginPage({
    required this.busy,
    required this.onLogin,
    this.error,
    super.key,
  });

  final bool busy;
  final String? error;
  final void Function(String token, String? email, String? role) onLogin;

  @override
  State<DebugLoginPage> createState() => _DebugLoginPageState();
}

class _DebugLoginPageState extends State<DebugLoginPage> {
  final _token = TextEditingController();
  final _email = TextEditingController();
  String? _role;

  @override
  void dispose() {
    _token.dispose();
    _email.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
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
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Icon(Icons.visibility_outlined, size: 46),
                  const SizedBox(height: 16),
                  Text(
                    'Agenttoegang',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Open een afgeschermde beheersessie of bekijk de applicatie als een bestaande gebruiker.',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),
                  TextField(
                    controller: _token,
                    obscureText: true,
                    enableSuggestions: false,
                    autocorrect: false,
                    onChanged: (_) => setState(() {}),
                    decoration: const InputDecoration(labelText: 'Debug-token'),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: _email,
                    keyboardType: TextInputType.emailAddress,
                    decoration: const InputDecoration(
                      labelText: 'Gebruiker (optioneel)',
                      hintText: 'naam@example.com',
                    ),
                  ),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<String?>(
                    initialValue: _role,
                    decoration: const InputDecoration(labelText: 'Rol'),
                    items: const [
                      DropdownMenuItem(value: null, child: Text('Huidige rol')),
                      DropdownMenuItem(
                        value: 'PRODUCT_OWNER',
                        child: Text('Product owner'),
                      ),
                      DropdownMenuItem(
                        value: 'ARCHITECT',
                        child: Text('Architect'),
                      ),
                      DropdownMenuItem(
                        value: 'FACTORY_OWNER',
                        child: Text('Factory owner'),
                      ),
                    ],
                    onChanged: (value) => setState(() => _role = value),
                  ),
                  if (widget.error != null) ...[
                    const SizedBox(height: 16),
                    Text(
                      widget.error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ],
                  const SizedBox(height: 24),
                  FilledButton.icon(
                    onPressed: widget.busy || _token.text.isEmpty
                        ? null
                        : () => widget.onLogin(
                            _token.text,
                            _email.text.trim().isEmpty
                                ? null
                                : _email.text.trim(),
                            _role,
                          ),
                    icon: widget.busy
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.login),
                    label: const Text('Sessie openen'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    ),
  );
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
    this.csrfToken,
    this.productGateway,
    this.memoryAiGateway,
    this.navigationLocation,
    this.isFactoryOwner = true,
    this.productMemberships = const {},
    this.actingRole,
    this.availableRoles = const {},
    this.onSwitchRole,
    this.viewingAs = false,
    this.authenticatedEmail,
    this.onViewAs,
    this.onClearViewAs,
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
  final String? csrfToken;
  final ProductGateway? productGateway;
  final MemoryAiGateway? memoryAiGateway;
  final NavigationLocation? navigationLocation;
  final bool isFactoryOwner;
  final Set<String> productMemberships;
  final String? actingRole;
  final Set<String> availableRoles;
  final ValueChanged<String>? onSwitchRole;
  final bool viewingAs;
  final String? authenticatedEmail;
  final void Function(String userId, String role)? onViewAs;
  final VoidCallback? onClearViewAs;

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
    csrfToken: csrfToken,
    productGateway: productGateway,
    memoryAiGateway: memoryAiGateway,
    navigationLocation: navigationLocation,
    isFactoryOwner: isFactoryOwner,
    productMemberships: productMemberships,
    actingRole: actingRole,
    availableRoles: availableRoles,
    onSwitchRole: onSwitchRole,
    viewingAs: viewingAs,
    authenticatedEmail: authenticatedEmail,
    onViewAs: onViewAs,
    onClearViewAs: onClearViewAs,
  );
}
