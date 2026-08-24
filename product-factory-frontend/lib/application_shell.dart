import 'dart:async';

import 'package:flutter/material.dart';

import 'build_identity.dart';
import 'configuration.dart';
import 'frontend_version_monitor.dart';
import 'page_reload.dart';
import 'testbed.dart';

class ApplicationShell extends StatefulWidget {
  const ApplicationShell({
    required this.showAcceptanceBanner,
    required this.versionGateway,
    this.stakeholderEmail,
    this.onLogout,
    this.error,
    this.frontendVersionSource,
    this.onReload,
    this.currentBuildIdentity,
    this.testControlGateway,
    super.key,
  });

  final bool showAcceptanceBanner;
  final String? stakeholderEmail;
  final VoidCallback? onLogout;
  final VersionGateway versionGateway;
  final String? error;
  final FrontendVersionSource? frontendVersionSource;
  final VoidCallback? onReload;
  final BuildIdentity? currentBuildIdentity;
  final TestControlGateway? testControlGateway;

  @override
  State<ApplicationShell> createState() => _ApplicationShellState();
}

class _ApplicationShellState extends State<ApplicationShell> {
  int _selectedIndex = 0;
  bool _updateAvailable = false;
  final VersionUpdateTracker _updateTracker = VersionUpdateTracker();
  Timer? _versionTimer;

  @override
  void initState() {
    super.initState();
    unawaited(_checkVersion());
    _versionTimer = Timer.periodic(
      const Duration(minutes: 5),
      (_) => unawaited(_checkVersion()),
    );
  }

  @override
  void dispose() {
    _versionTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Product Factory'),
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 10),
            child: Chip(
              avatar: const Icon(Icons.public, size: 16),
              label: Text(_environmentLabel(AppConfiguration.environment)),
            ),
          ),
          if (widget.onLogout != null)
            IconButton(
              onPressed: widget.onLogout,
              tooltip: 'Uitloggen',
              icon: const Icon(Icons.logout),
            ),
          const SizedBox(width: 8),
        ],
      ),
      body: Column(
        children: [
          if (widget.showAcceptanceBanner) const AcceptanceBanner(),
          if (widget.error != null)
            Container(
              width: double.infinity,
              color: Theme.of(context).colorScheme.errorContainer,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Text(widget.error!, textAlign: TextAlign.center),
            ),
          if (_updateAvailable)
            MaterialBanner(
              content: const Text('Nieuwe versie beschikbaar — vernieuwen'),
              actions: [
                TextButton(
                  onPressed: widget.onReload ?? reloadPage,
                  child: const Text('Vernieuwen'),
                ),
              ],
            ),
          Expanded(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final content = switch (_selectedIndex) {
                  0 => const ProductPage(),
                  1 => ManagementPage(
                    versionGateway: widget.versionGateway,
                    stakeholderEmail: widget.stakeholderEmail,
                  ),
                  _ => AcceptanceTestsPage(
                    gateway:
                        widget.testControlGateway ?? HttpTestControlGateway(),
                  ),
                };
                if (constraints.maxWidth >= 760) {
                  return Row(
                    children: [
                      NavigationRail(
                        selectedIndex: _selectedIndex,
                        onDestinationSelected: _select,
                        labelType: NavigationRailLabelType.all,
                        destinations: [
                          NavigationRailDestination(
                            icon: Icon(Icons.inventory_2_outlined),
                            selectedIcon: Icon(Icons.inventory_2),
                            label: Text('Producten'),
                          ),
                          NavigationRailDestination(
                            icon: Icon(Icons.info_outline),
                            selectedIcon: Icon(Icons.info),
                            label: Text('Beheer'),
                          ),
                          if (widget.showAcceptanceBanner)
                            const NavigationRailDestination(
                              icon: Icon(Icons.science_outlined),
                              selectedIcon: Icon(Icons.science),
                              label: Text('Acceptatietesten'),
                            ),
                        ],
                      ),
                      const VerticalDivider(width: 1),
                      Expanded(child: content),
                    ],
                  );
                }
                return content;
              },
            ),
          ),
        ],
      ),
      bottomNavigationBar: MediaQuery.sizeOf(context).width < 760
          ? NavigationBar(
              selectedIndex: _selectedIndex,
              onDestinationSelected: _select,
              destinations: [
                NavigationDestination(
                  icon: Icon(Icons.inventory_2_outlined),
                  selectedIcon: Icon(Icons.inventory_2),
                  label: 'Producten',
                ),
                NavigationDestination(
                  icon: Icon(Icons.info_outline),
                  selectedIcon: Icon(Icons.info),
                  label: 'Beheer',
                ),
                if (widget.showAcceptanceBanner)
                  const NavigationDestination(
                    icon: Icon(Icons.science_outlined),
                    selectedIcon: Icon(Icons.science),
                    label: 'Acceptatietesten',
                  ),
              ],
            )
          : null,
    );
  }

  void _select(int index) => setState(() => _selectedIndex = index);

  Future<void> _checkVersion() async {
    final source = widget.frontendVersionSource ?? HttpFrontendVersionSource();
    try {
      final latest = await source.latest();
      if (!mounted) return;
      if (_updateTracker.shouldNotify(
        widget.currentBuildIdentity ?? BuildIdentity.frontend(),
        latest,
      )) {
        setState(() => _updateAvailable = true);
      }
    } on VersionFailure {
      // Versiecontrole verstoort de actieve applicatie niet; de volgende controle probeert opnieuw.
    }
  }

  String _environmentLabel(String environment) => switch (environment) {
    'acceptance' => 'Acceptatie',
    'production' => 'Productie',
    _ => 'Lokaal',
  };
}

class AcceptanceBanner extends StatelessWidget {
  const AcceptanceBanner({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      color: const Color(0xffffe08a),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: const Text(
        'Acceptatie — synthetische tijdelijke data — authenticatie uit',
        textAlign: TextAlign.center,
        style: TextStyle(fontWeight: FontWeight.w700),
      ),
    );
  }
}

class ProductPage extends StatelessWidget {
  const ProductPage({super.key});

  @override
  Widget build(BuildContext context) {
    return _PageFrame(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Producten',
            style: Theme.of(
              context,
            ).textTheme.displaySmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 16),
          Text(
            'De technische fundering is beschikbaar.',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 32),
          const _EmptyState(
            icon: Icons.inventory_2_outlined,
            title: 'Nog geen producten',
            message:
                'Functionele productprocessen worden in een volgende release toegevoegd.',
          ),
        ],
      ),
    );
  }
}

class ManagementPage extends StatefulWidget {
  const ManagementPage({
    required this.versionGateway,
    this.stakeholderEmail,
    super.key,
  });

  final VersionGateway versionGateway;
  final String? stakeholderEmail;

  @override
  State<ManagementPage> createState() => _ManagementPageState();
}

class _ManagementPageState extends State<ManagementPage> {
  late Future<BuildIdentity> _backend;

  @override
  void initState() {
    super.initState();
    _backend = widget.versionGateway.backendIdentity();
  }

  @override
  Widget build(BuildContext context) {
    final frontend = BuildIdentity.frontend();
    return _PageFrame(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Beheer',
            style: Theme.of(
              context,
            ).textTheme.displaySmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          const Text('Technische identiteit van deze Product Factory-release.'),
          const SizedBox(height: 24),
          _IdentityCard(title: 'Frontend', identity: frontend),
          const SizedBox(height: 16),
          FutureBuilder<BuildIdentity>(
            future: _backend,
            builder: (context, snapshot) {
              if (snapshot.connectionState != ConnectionState.done) {
                return const Card(
                  child: Padding(
                    padding: EdgeInsets.all(24),
                    child: Row(
                      children: [
                        CircularProgressIndicator(),
                        SizedBox(width: 16),
                        Text('Backendidentiteit laden…'),
                      ],
                    ),
                  ),
                );
              }
              if (snapshot.hasError || snapshot.data == null) {
                return _ErrorState(onRetry: _retry);
              }
              final backend = snapshot.data!;
              return Column(
                children: [
                  if (backend.apiVersion != frontend.apiVersion)
                    Card(
                      color: Theme.of(context).colorScheme.errorContainer,
                      child: const ListTile(
                        leading: Icon(Icons.error_outline),
                        title: Text('Frontend en backend zijn niet compatibel'),
                      ),
                    ),
                  _IdentityCard(title: 'Backend', identity: backend),
                ],
              );
            },
          ),
          if (widget.stakeholderEmail != null) ...[
            const SizedBox(height: 16),
            Text('Ingelogd als ${widget.stakeholderEmail}'),
          ],
        ],
      ),
    );
  }

  void _retry() => setState(() {
    _backend = widget.versionGateway.backendIdentity();
  });
}

class _IdentityCard extends StatelessWidget {
  const _IdentityCard({required this.title, required this.identity});

  final String title;
  final BuildIdentity identity;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            _IdentityRow(label: 'Versie', value: identity.applicationVersion),
            _IdentityRow(label: 'Omgeving', value: identity.environment),
            _IdentityRow(label: 'Commit', value: identity.gitRevision),
            _IdentityRow(label: 'Gebouwd', value: identity.buildTime),
            _IdentityRow(label: 'Build', value: identity.buildIdentity),
          ],
        ),
      ),
    );
  }
}

class _IdentityRow extends StatelessWidget {
  const _IdentityRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 92, child: Text(label)),
          Expanded(child: SelectableText(value)),
        ],
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({
    required this.icon,
    required this.title,
    required this.message,
  });
  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: Theme.of(context).colorScheme.primary),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Text(message),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.onRetry});
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.errorContainer,
      child: ListTile(
        leading: const Icon(Icons.cloud_off),
        title: const Text('Backendinformatie kon niet worden geladen'),
        subtitle: const Text(
          'Controleer de verbinding en probeer het opnieuw.',
        ),
        trailing: TextButton(onPressed: onRetry, child: const Text('Opnieuw')),
      ),
    );
  }
}

class _PageFrame extends StatelessWidget {
  const _PageFrame({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: EdgeInsets.symmetric(
        horizontal: MediaQuery.sizeOf(context).width < 600 ? 20 : 48,
        vertical: 32,
      ),
      child: Align(
        alignment: Alignment.topLeft,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1120),
          child: child,
        ),
      ),
    );
  }
}
