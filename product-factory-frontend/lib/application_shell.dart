import 'dart:async';

import 'package:flutter/material.dart';

import 'build_identity.dart';
import 'configuration.dart';
import 'frontend_version_monitor.dart';
import 'memory_ai_management.dart';
import 'page_reload.dart';
import 'product_workspace.dart';
import 'product_factory_theme.dart';
import 'testbed.dart';

enum _Destination {
  overview,
  design,
  planning,
  quality,
  signals,
  meetings,
  management,
  settings,
  decisions,
  memory,
  operation,
  system,
  acceptance,
}

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
    this.runtimeEnvironment,
    this.productGateway,
    this.memoryAiGateway,
    this.csrfToken,
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
  final String? runtimeEnvironment;
  final ProductGateway? productGateway;
  final MemoryAiGateway? memoryAiGateway;
  final String? csrfToken;

  @override
  State<ApplicationShell> createState() => _ApplicationShellState();
}

class _ApplicationShellState extends State<ApplicationShell> {
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  _Destination _selected = _Destination.overview;
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
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final desktop = constraints.maxWidth >= 900;
      return Scaffold(
        key: _scaffoldKey,
        drawer: desktop
            ? null
            : Drawer(
                width: 278,
                backgroundColor: ProductFactoryColors.sidebar,
                child: SafeArea(child: _sidebar(closeAfterSelection: true)),
              ),
        body: Row(
          children: [
            if (desktop) SizedBox(width: 260, child: _sidebar()),
            Expanded(
              child: Column(
                children: [
                  _topbar(desktop),
                  if (widget.showAcceptanceBanner) const AcceptanceBanner(),
                  if (widget.error != null)
                    Container(
                      width: double.infinity,
                      color: Theme.of(context).colorScheme.errorContainer,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 10,
                      ),
                      child: Text(widget.error!, textAlign: TextAlign.center),
                    ),
                  if (_updateAvailable)
                    MaterialBanner(
                      content: const Text(
                        'Nieuwe versie beschikbaar — vernieuwen',
                      ),
                      actions: [
                        TextButton(
                          onPressed: widget.onReload ?? reloadPage,
                          child: const Text('Vernieuwen'),
                        ),
                      ],
                    ),
                  Expanded(child: _content()),
                ],
              ),
            ),
          ],
        ),
      );
    },
  );

  Widget _content() {
    final products =
        widget.productGateway ??
        HttpProductGateway(csrfToken: widget.csrfToken);
    final memory =
        widget.memoryAiGateway ??
        HttpMemoryAiGateway(csrfToken: widget.csrfToken);
    return switch (_selected) {
      _Destination.overview => ProductWorkspacePage(gateway: products),
      _Destination.design => ProductWorkspacePage(
        gateway: products,
        section: ProductWorkspaceSection.design,
      ),
      _Destination.planning => ProductWorkspacePage(
        gateway: products,
        section: ProductWorkspaceSection.planning,
      ),
      _Destination.quality => ProductWorkspacePage(
        gateway: products,
        section: ProductWorkspaceSection.quality,
      ),
      _Destination.signals => ProductWorkspacePage(
        gateway: products,
        section: ProductWorkspaceSection.signals,
      ),
      _Destination.meetings => ProductWorkspacePage(
        gateway: products,
        section: ProductWorkspaceSection.meetings,
      ),
      _Destination.management => _ManagementHub(onSelect: _select),
      _Destination.settings => ProductWorkspacePage(
        gateway: products,
        section: ProductWorkspaceSection.settings,
        trailingContent: MemoryAiManagementPanel(
          gateway: memory,
          view: MemoryAiView.settings,
        ),
      ),
      _Destination.decisions => ProductWorkspacePage(
        gateway: products,
        section: ProductWorkspaceSection.decisions,
      ),
      _Destination.memory => ManagementPage(
        versionGateway: widget.versionGateway,
        stakeholderEmail: widget.stakeholderEmail,
        runtimeEnvironment: widget.runtimeEnvironment,
        memoryAiGateway: memory,
        title: 'Agentgeheugen',
        subtitle: 'Geheugen per rol controleren en corrigeren.',
        showIdentity: false,
        memoryView: MemoryAiView.memory,
      ),
      _Destination.operation => ProductWorkspacePage(
        gateway: products,
        section: ProductWorkspaceSection.operation,
        trailingContent: MemoryAiManagementPanel(
          gateway: memory,
          view: MemoryAiView.operation,
        ),
      ),
      _Destination.system => ManagementPage(
        versionGateway: widget.versionGateway,
        stakeholderEmail: widget.stakeholderEmail,
        runtimeEnvironment: widget.runtimeEnvironment,
        memoryAiGateway: memory,
        showMemory: false,
      ),
      _Destination.acceptance => AcceptanceTestsPage(
        gateway: widget.testControlGateway ?? HttpTestControlGateway(),
      ),
    };
  }

  Widget _topbar(bool desktop) {
    return Container(
      height: 76,
      padding: EdgeInsets.symmetric(horizontal: desktop ? 30 : 14),
      decoration: const BoxDecoration(
        color: Color(0xfffbfcfa),
        border: Border(bottom: BorderSide(color: ProductFactoryColors.outline)),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 480;
          return Row(
            children: [
              if (!desktop) ...[
                IconButton(
                  onPressed: () => _scaffoldKey.currentState?.openDrawer(),
                  tooltip: 'Menu openen',
                  icon: const Icon(Icons.menu),
                ),
                const SizedBox(width: 6),
              ],
              OutlinedButton.icon(
                onPressed: () => _select(_Destination.overview),
                icon: Container(
                  width: 30,
                  height: 30,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: ProductFactoryColors.sidebarSelected,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(
                    Icons.inventory_2,
                    size: 16,
                    color: Colors.white,
                  ),
                ),
                label: Text(compact ? 'PF' : 'Product Factory'),
              ),
              const Spacer(),
              if (desktop)
                Chip(
                  avatar: const Icon(
                    Icons.circle,
                    size: 9,
                    color: Color(0xff59b792),
                  ),
                  label: Text(
                    _environmentLabel(
                      widget.runtimeEnvironment ?? AppConfiguration.environment,
                    ),
                  ),
                ),
              if (!compact) ...[
                const SizedBox(width: 10),
                FilledButton.icon(
                  onPressed: () => _select(_Destination.signals),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Signaal'),
                ),
              ],
              if (!desktop && widget.onLogout != null) ...[
                const SizedBox(width: 4),
                IconButton(
                  onPressed: widget.onLogout,
                  tooltip: 'Uitloggen',
                  icon: const Icon(Icons.logout),
                ),
              ],
            ],
          );
        },
      ),
    );
  }

  Widget _sidebar({bool closeAfterSelection = false}) => Container(
    height: double.infinity,
    color: ProductFactoryColors.sidebar,
    padding: const EdgeInsets.fromLTRB(16, 22, 16, 16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _Brand(),
        const SizedBox(height: 24),
        _navItem(
          _Destination.overview,
          Icons.home_outlined,
          'Overzicht',
          closeAfterSelection,
        ),
        _navItem(
          _Destination.design,
          Icons.auto_awesome_outlined,
          'Ontwerp',
          closeAfterSelection,
        ),
        _navItem(
          _Destination.planning,
          Icons.format_list_bulleted,
          'Planning',
          closeAfterSelection,
        ),
        _navItem(
          _Destination.quality,
          Icons.diamond_outlined,
          'Kwaliteit',
          closeAfterSelection,
        ),
        _navItem(
          _Destination.signals,
          Icons.radio_button_unchecked,
          'Signalen',
          closeAfterSelection,
        ),
        _navItem(
          _Destination.meetings,
          Icons.forum_outlined,
          'Overleggen',
          closeAfterSelection,
        ),
        const Padding(
          padding: EdgeInsets.fromLTRB(12, 24, 12, 8),
          child: Text(
            'BEHEER',
            style: TextStyle(
              color: Color(0xff6f918a),
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: 1.4,
            ),
          ),
        ),
        _navItem(
          _Destination.management,
          Icons.tune,
          'Beheer',
          closeAfterSelection,
        ),
        if (widget.showAcceptanceBanner)
          _navItem(
            _Destination.acceptance,
            Icons.science_outlined,
            'Acceptatietesten',
            closeAfterSelection,
          ),
        const Spacer(),
        const Divider(color: Color(0xff294547)),
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Row(
            children: [
              CircleAvatar(
                radius: 18,
                backgroundColor: const Color(0xffd8f3e7),
                child: Text(
                  _initials(widget.stakeholderEmail),
                  style: const TextStyle(
                    color: ProductFactoryColors.sidebar,
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.stakeholderEmail?.split('@').first ??
                          'Stakeholder',
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const Text(
                      'Stakeholder',
                      style: TextStyle(color: Color(0xff90aaa4), fontSize: 12),
                    ),
                  ],
                ),
              ),
              if (widget.onLogout != null)
                IconButton(
                  onPressed: widget.onLogout,
                  tooltip: 'Uitloggen',
                  color: const Color(0xffb9ccc7),
                  icon: const Icon(Icons.logout, size: 19),
                ),
            ],
          ),
        ),
      ],
    ),
  );

  Widget _navItem(
    _Destination destination,
    IconData icon,
    String label,
    bool closeAfterSelection,
  ) {
    final selected = _isSelected(destination);
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color: selected
            ? ProductFactoryColors.sidebarSelected
            : Colors.transparent,
        borderRadius: BorderRadius.circular(11),
        child: ListTile(
          dense: true,
          minTileHeight: 46,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(11),
          ),
          leading: Icon(
            icon,
            color: selected
                ? ProductFactoryColors.mint
                : const Color(0xff91aaa5),
            size: 20,
          ),
          title: Text(
            label,
            style: TextStyle(
              color: selected ? Colors.white : const Color(0xffb9ccc7),
              fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            ),
          ),
          trailing: destination == _Destination.management
              ? const Icon(
                  Icons.circle,
                  size: 8,
                  color: ProductFactoryColors.warning,
                )
              : null,
          onTap: () {
            _select(destination);
            if (closeAfterSelection) Navigator.of(context).pop();
          },
        ),
      ),
    );
  }

  bool _isSelected(_Destination destination) {
    if (destination != _Destination.management) return _selected == destination;
    return const {
      _Destination.management,
      _Destination.settings,
      _Destination.decisions,
      _Destination.memory,
      _Destination.operation,
      _Destination.system,
    }.contains(_selected);
  }

  void _select(_Destination destination) =>
      setState(() => _selected = destination);

  String _initials(String? email) {
    final local = email?.split('@').first.trim();
    if (local == null || local.isEmpty) return 'PF';
    final parts = local
        .split(RegExp(r'[._-]+'))
        .where((part) => part.isNotEmpty);
    return parts.take(2).map((part) => part[0].toUpperCase()).join();
  }

  Future<void> _checkVersion() async {
    final source = widget.frontendVersionSource ?? HttpFrontendVersionSource();
    try {
      final latest = await source.latest();
      if (!mounted) return;
      if (_updateTracker.shouldNotify(
        widget.currentBuildIdentity ??
            BuildIdentity.frontend(
              runtimeEnvironment: widget.runtimeEnvironment,
            ),
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

class _Brand extends StatelessWidget {
  const _Brand();

  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.symmetric(horizontal: 8),
    child: Row(
      children: [
        _BrandMark(),
        SizedBox(width: 11),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Product Factory',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Text(
                'Van inzicht naar verbetering',
                style: TextStyle(color: Color(0xff90aaa4), fontSize: 11),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _BrandMark extends StatelessWidget {
  const _BrandMark();

  @override
  Widget build(BuildContext context) => Container(
    width: 34,
    height: 34,
    padding: const EdgeInsets.all(7),
    decoration: BoxDecoration(
      color: ProductFactoryColors.mint,
      borderRadius: BorderRadius.circular(10),
    ),
    child: const Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Expanded(child: _BrandBar(height: 8)),
        SizedBox(width: 3),
        Expanded(child: _BrandBar(height: 19)),
        SizedBox(width: 3),
        Expanded(child: _BrandBar(height: 13)),
      ],
    ),
  );
}

class _BrandBar extends StatelessWidget {
  const _BrandBar({required this.height});
  final double height;

  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.bottomCenter,
    child: Container(
      height: height,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(3),
      ),
    ),
  );
}

class _ManagementHub extends StatelessWidget {
  const _ManagementHub({required this.onSelect});
  final ValueChanged<_Destination> onSelect;

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    padding: EdgeInsets.fromLTRB(
      MediaQuery.sizeOf(context).width < 600 ? 20 : 42,
      MediaQuery.sizeOf(context).width < 600 ? 28 : 44,
      MediaQuery.sizeOf(context).width < 600 ? 20 : 42,
      64,
    ),
    child: Align(
      alignment: Alignment.topLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1180),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'BEHEER',
              style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.w800,
                letterSpacing: 1.5,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Richting, geheugen en techniek',
              style: Theme.of(context).textTheme.displaySmall,
            ),
            const SizedBox(height: 8),
            const Text(
              'Onderdelen die je minder vaak nodig hebt, zonder het dagelijks werk druk te maken.',
            ),
            const SizedBox(height: 30),
            LayoutBuilder(
              builder: (context, constraints) {
                final width = constraints.maxWidth >= 760
                    ? (constraints.maxWidth - 16) / 2
                    : constraints.maxWidth;
                return Wrap(
                  spacing: 16,
                  runSpacing: 16,
                  children: [
                    _HubCard(
                      width: width,
                      icon: Icons.tune,
                      title: 'Instellingen',
                      subtitle:
                          'Product, omgevingen, automatisering en levering',
                      onTap: () => onSelect(_Destination.settings),
                    ),
                    _HubCard(
                      width: width,
                      icon: Icons.check,
                      title: 'Besluiten',
                      subtitle: 'Actuele keuzes, peildatum en historie',
                      onTap: () => onSelect(_Destination.decisions),
                    ),
                    _HubCard(
                      width: width,
                      icon: Icons.menu_book_outlined,
                      title: 'Agentgeheugen',
                      subtitle: 'Geheugen per rol controleren en corrigeren',
                      onTap: () => onSelect(_Destination.memory),
                    ),
                    _HubCard(
                      width: width,
                      icon: Icons.monitor_heart_outlined,
                      title: 'Operatie',
                      subtitle: 'Runs, queues, AI-uitvoering en dispatcher',
                      badge: 'Techniek',
                      onTap: () => onSelect(_Destination.operation),
                    ),
                    _HubCard(
                      width: width,
                      icon: Icons.info_outline,
                      title: 'Release-informatie',
                      subtitle: 'Frontend-, backend- en omgevingsidentiteit',
                      onTap: () => onSelect(_Destination.system),
                    ),
                  ],
                );
              },
            ),
          ],
        ),
      ),
    ),
  );
}

class _HubCard extends StatelessWidget {
  const _HubCard({
    required this.width,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.badge,
  });
  final double width;
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  final String? badge;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: width,
    child: Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(22),
          child: Row(
            children: [
              Container(
                width: 46,
                height: 46,
                decoration: BoxDecoration(
                  color: const Color(0xffeaf5ee),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: ProductFactoryColors.primary),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              if (badge != null) Chip(label: Text(badge!)),
              const SizedBox(width: 8),
              const Icon(Icons.arrow_forward, size: 18),
            ],
          ),
        ),
      ),
    ),
  );
}

class ManagementPage extends StatefulWidget {
  const ManagementPage({
    required this.versionGateway,
    required this.memoryAiGateway,
    this.stakeholderEmail,
    this.runtimeEnvironment,
    this.title = 'Release-informatie',
    this.subtitle = 'Technische identiteit van deze Product Factory-release.',
    this.showIdentity = true,
    this.showMemory = true,
    this.memoryView = MemoryAiView.all,
    super.key,
  });

  final VersionGateway versionGateway;
  final MemoryAiGateway memoryAiGateway;
  final String? stakeholderEmail;
  final String? runtimeEnvironment;
  final String title;
  final String subtitle;
  final bool showIdentity;
  final bool showMemory;
  final MemoryAiView memoryView;

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
    final frontend = BuildIdentity.frontend(
      runtimeEnvironment: widget.runtimeEnvironment,
    );
    return _PageFrame(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            widget.title,
            style: Theme.of(
              context,
            ).textTheme.displaySmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          Text(widget.subtitle),
          const SizedBox(height: 24),
          if (widget.showIdentity) ...[
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
                          title: Text(
                            'Frontend en backend zijn niet compatibel',
                          ),
                        ),
                      ),
                    _IdentityCard(title: 'Backend', identity: backend),
                  ],
                );
              },
            ),
          ],
          if (widget.showMemory)
            MemoryAiManagementPanel(
              gateway: widget.memoryAiGateway,
              view: widget.memoryView,
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
