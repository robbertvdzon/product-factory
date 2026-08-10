import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'api.dart';
import 'classification.dart';
import 'config.dart';
import 'formatting.dart';
import 'google_button_stub.dart'
    if (dart.library.html) 'google_button_web.dart'
    as google_button;
import 'limited_list.dart';
import 'meeting_dialog.dart';
import 'session.dart';

void main() => runApp(const ProductFactoryDashboard());

class ProductFactoryDashboard extends StatelessWidget {
  const ProductFactoryDashboard({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Product Factory',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff325d4d)),
      useMaterial3: true,
    ),
    home: const DashboardGate(),
  );
}

class DashboardGate extends StatefulWidget {
  const DashboardGate({super.key});
  @override
  State<DashboardGate> createState() => _DashboardGateState();
}

class _DashboardGateState extends State<DashboardGate> {
  DashboardSession? source;
  AuthenticatedSession? session;
  StreamSubscription<AuthenticatedSession>? subscription;
  bool loading = true;
  String? error;
  @override
  void initState() {
    super.initState();
    _start();
  }

  Future<void> _start() async {
    if (!AppConfig.authRequired) {
      setState(() => loading = false);
      return;
    }
    if (AppConfig.googleClientId.isEmpty) {
      setState(() {
        loading = false;
        error = 'Google-login is niet geconfigureerd.';
      });
      return;
    }
    source = DashboardSession(
      apiBaseUrl: AppConfig.apiBaseUrl,
      clientId: AppConfig.googleClientId,
    );
    subscription = source!.changes.stream.listen(
      (value) => setState(() {
        session = value;
        loading = false;
        error = null;
      }),
      onError: (Object value) => setState(() {
        loading = false;
        error = '$value';
      }),
    );
    try {
      final value = await source!.bootstrap();
      if (mounted) {
        setState(() {
          session = value;
          loading = false;
        });
      }
    } catch (exception) {
      if (mounted) {
        setState(() {
          loading = false;
          error = '$exception';
        });
      }
    }
  }

  Future<void> _signIn() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final value = await source!.signIn();
      if (mounted) {
        setState(() {
          session = value;
          loading = false;
        });
      }
    } catch (exception) {
      if (mounted) {
        setState(() {
          loading = false;
          error = '$exception';
        });
      }
    }
  }

  @override
  void dispose() {
    subscription?.cancel();
    source?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (AppConfig.authRequired && session == null) {
      return LoginPage(error: error, signIn: _signIn);
    }
    return OverviewPage(session: session);
  }
}

class LoginPage extends StatelessWidget {
  const LoginPage({required this.error, required this.signIn, super.key});
  final String? error;
  final VoidCallback signIn;
  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.auto_awesome, size: 56),
                const SizedBox(height: 16),
                Text(
                  'Product Factory',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 8),
                const Text('Log in met een toegestaan Google-account.'),
                const SizedBox(height: 24),
                if (kIsWeb)
                  SizedBox(
                    height: 42,
                    child: google_button.renderGoogleButton(),
                  )
                else
                  FilledButton.icon(
                    onPressed: signIn,
                    icon: const Icon(Icons.login),
                    label: const Text('Inloggen met Google'),
                  ),
                if (error != null) ...[
                  const SizedBox(height: 16),
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
  );
}

class OverviewPage extends StatefulWidget {
  const OverviewPage({required this.session, super.key});
  final AuthenticatedSession? session;
  @override
  State<OverviewPage> createState() => _OverviewPageState();
}

class _OverviewPageState extends State<OverviewPage> {
  late Future<List<dynamic>> data;
  late final DashboardApi api;
  Timer? refreshTimer;

  /// Hoeveel items er per sectie zichtbaar zijn. Deze tellers staan bewust in de state en niet in de
  /// FutureBuilder, zodat de auto-refresh (elke 5 s) een uitgeklapte lijst uitgeklapt laat.
  final Map<String, int> visibleCounts = {};

  int _visibleCount(String section) =>
      visibleCounts[section] ?? kInitialVisibleItems;

  void _showMore(String section, int itemCount) => setState(
    () => visibleCounts[section] = nextVisibleCount(
      _visibleCount(section),
      itemCount,
    ),
  );

  /// Bouwt een overzichtslijst met de standaardbeperking (5 items, +10 per klik) en een eigen teller per sectie.
  Widget _limitedSection(
    String section,
    List<Map<String, dynamic>> items,
    Widget Function(Map<String, dynamic> item) itemBuilder,
  ) => LimitedListSection(
    itemCount: items.length,
    visibleCount: _visibleCount(section),
    itemBuilder: (_, index) => itemBuilder(items[index]),
    onShowMore: () => _showMore(section, items.length),
  );

  @override
  void initState() {
    super.initState();
    api = DashboardApi(AppConfig.apiBaseUrl, widget.session?.token);
    _reload();
    refreshTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      if (mounted) setState(_reload);
    });
  }

  @override
  void dispose() {
    refreshTimer?.cancel();
    super.dispose();
  }

  void _reload() => data = Future.wait<dynamic>([
    api.products(),
    api.stories(),
    api.publications(),
    api.shadowIterations(),
    api.deliveries(),
    api.humanActions(),
    api.aiCatalog(),
    api.meetings(),
  ]);
  Future<void> _changeStatus(String slug, String action) async {
    await api.changeProductStatus(slug, action);
    if (mounted) setState(_reload);
  }

  Future<void> _addProduct(Map<String, dynamic> aiCatalog) async {
    final created = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (_) => AddProductDialog(aiCatalog: aiCatalog),
    );
    if (created == null) return;
    await api.createProduct(created);
    if (mounted) setState(_reload);
  }

  Future<void> _editProductSettings(
    Map<String, dynamic> product,
    Map<String, dynamic> aiCatalog,
  ) async {
    final settings = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (_) =>
          ProductSettingsDialog(product: product, aiCatalog: aiCatalog),
    );
    if (settings == null) return;
    try {
      await api.updateProductSettings('${product['slug']}', settings);
      if (mounted) setState(_reload);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> _startCycle(String slug) async {
    try {
      await api.startCycle(slug);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Productcyclus voor $slug is gestart.')),
        );
        setState(_reload);
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> _showIteration(Map<String, dynamic> iteration) async {
    await showDialog<void>(
      context: context,
      builder: (_) => IterationSessionDialog(
        api: api,
        productSlug: '${iteration['productSlug']}',
        iterationId: '${iteration['id']}',
      ),
    );
    if (mounted) setState(_reload);
  }

  Future<void> _openMeeting(String productSlug, String meetingId) async {
    await showDialog<void>(
      context: context,
      builder: (_) => MeetingDialog(
        api: api,
        productSlug: productSlug,
        meetingId: meetingId,
      ),
    );
    if (mounted) setState(_reload);
  }

  Future<void> _showMeetingMinutes(String productSlug, String runId) async {
    try {
      final content = await api.artifact(productSlug, runId);
      if (mounted) {
        showDialog<void>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Notulen'),
            content: SizedBox(
              width: 720,
              child: SingleChildScrollView(child: SelectableText(content)),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Sluiten'),
              ),
            ],
          ),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> _startMeeting(String slug) async {
    try {
      final meeting = await api.startMeeting(slug);
      if (mounted) setState(_reload);
      await _openMeeting(slug, '${meeting['id']}');
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> _completeHumanAction(Map<String, dynamic> action) async {
    final controller = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('${action['title']}'),
        content: TextField(
          controller: controller,
          minLines: 3,
          maxLines: 8,
          decoration: const InputDecoration(
            labelText:
                'Welke secret is ingesteld? Plak het access token zelf hier niet.',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text),
            child: const Text('Gereed melden'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (result == null || result.trim().isEmpty) return;
    await api.completeHumanAction(action['id'] as int, result.trim());
    if (mounted) setState(_reload);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Product Factory'),
      actions: [
        if (widget.session != null)
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(widget.session!.email),
          ),
      ],
    ),
    body: FutureBuilder<List<dynamic>>(
      future: data,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done &&
            !snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(
            child: Text('Dashboard kon niet laden: ${snapshot.error}'),
          );
        }
        final products = (snapshot.data![0] as List<dynamic>)
            .cast<Map<String, dynamic>>();
        final stories = snapshot.data![1] as List<dynamic>;
        // Workspace-publicaties hebben geen tijdstempel in de contracts; daar geldt alleen de beperking.
        final publications = (snapshot.data![2] as List<dynamic>)
            .cast<Map<String, dynamic>>();
        final iterations = sortedByNewestFirst(
          snapshot.data![3] as List<dynamic>,
          ['startedAt', 'createdAt'],
        );
        final deliveries = snapshot.data![4] as List<dynamic>;
        final sortedDeliveries = sortedByNewestFirst(deliveries, ['createdAt']);
        final humanActions = sortedByNewestFirst(
          snapshot.data![5] as List<dynamic>,
          ['createdAt'],
        );
        final aiCatalog = snapshot.data![6] as Map<String, dynamic>;
        final meetings = sortedByNewestFirst(
          snapshot.data![7] as List<dynamic>,
          ['closedAt', 'createdAt'],
        );
        return ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Productoverzicht',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                ),
                FilledButton.icon(
                  onPressed: () => _addProduct(aiCatalog),
                  icon: const Icon(Icons.add),
                  label: const Text('Product toevoegen'),
                ),
                const SizedBox(width: 8),
                IconButton(
                  onPressed: () => setState(_reload),
                  tooltip: 'Vernieuwen',
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 16,
              runSpacing: 16,
              children: [
                MetricCard(
                  label: 'Producten',
                  value: '${products.length}',
                  icon: Icons.apps,
                ),
                MetricCard(
                  label: 'Interne storykandidaten',
                  value: '${stories.length}',
                  icon: Icons.lightbulb_outline,
                ),
                MetricCard(
                  label: 'Workspace-publicaties',
                  value: '${publications.length}',
                  icon: Icons.folder_open,
                ),
                MetricCard(
                  label: 'Shadow-iteraties',
                  value: '${iterations.length}',
                  icon: Icons.science_outlined,
                ),
                MetricCard(
                  label: 'Software Factory-stories',
                  value: '${deliveries.length}',
                  icon: Icons.precision_manufacturing_outlined,
                ),
              ],
            ),
            const SizedBox(height: 32),
            Text('Producten', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            _limitedSection('products', products, (product) {
              final slug = '${product['slug']}';
              final status = '${product['status']}';
              final active = status == 'active';
              final pendingMeetingTopics =
                  ((product['meetingRequestedTopics'] as List<dynamic>?) ??
                          const [])
                      .cast<String>();
              final meetingRequested = product['meetingRequestedAt'] != null;
              return Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.inventory_2_outlined),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              '${product['name']}',
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                          ),
                          Chip(label: Text(status)),
                          const SizedBox(width: 8),
                          Chip(label: Text('${product['developmentMode']}')),
                          if (meetingRequested) ...[
                            const SizedBox(width: 8),
                            Tooltip(
                              message: pendingMeetingTopics.isEmpty
                                  ? 'Het product wil overleg'
                                  : pendingMeetingTopics
                                        .map((topic) => '• $topic')
                                        .join('\n'),
                              child: ActionChip(
                                avatar: const Icon(Icons.forum_outlined, size: 18),
                                label: const Text('Overleg gevraagd'),
                                onPressed: () => _startMeeting(slug),
                              ),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text('${product['mission']}'),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 16,
                        runSpacing: 8,
                        children: [
                          Text(
                            'Project: ${product['softwareFactoryProjectKey']}',
                          ),
                          Text('Repo: ${product['targetRepositoryName']}'),
                          Text('Workspace: ${product['workspaceOwnership']}'),
                          Text(
                            'Max stories per cyclus: ${product['maxStoriesPerCycle']}',
                          ),
                          Text('WIP: ${product['wipLimit']}'),
                          Text(
                            'AI: ${product['aiProvider']} · ${product['aiModel']}',
                          ),
                          Text(
                            'Cyclustijden: ${((product['iterationTimes'] as List<dynamic>?) ?? const []).join(', ')}',
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        active
                            ? 'Active: geplande productcycli en leveringen mogen draaien.'
                            : 'Gepauzeerd: er starten geen nieuwe agents, stories of automatische antwoorden; extern lopend werk wordt niet afgebroken.',
                      ),
                      Text(
                        product['developmentMode'] == 'autonomous'
                            ? 'Autonomous: de Product Factory mag geaccepteerde stories zelfstandig naar de Software Factory sturen.'
                            : 'Niet-autonoom: de Product Factory mag geen stories zelfstandig publiceren.',
                      ),
                      Align(
                        alignment: Alignment.centerRight,
                        child: Wrap(
                          spacing: 8,
                          children: [
                            FilledButton.icon(
                              onPressed:
                                  active &&
                                      product['workspaceOwnership'] ==
                                          'product-factory'
                                  ? () => _startCycle(slug)
                                  : null,
                              icon: const Icon(Icons.auto_awesome),
                              label: const Text('Start productcyclus nu'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () => _changeStatus(
                                slug,
                                active ? 'pause' : 'resume',
                              ),
                              icon: Icon(
                                active ? Icons.pause : Icons.play_arrow,
                              ),
                              label: Text(active ? 'Pauzeren' : 'Hervatten'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () =>
                                  _editProductSettings(product, aiCatalog),
                              icon: const Icon(Icons.tune),
                              label: const Text('Instellingen'),
                            ),
                            OutlinedButton.icon(
                              onPressed: active
                                  ? () => _startMeeting(slug)
                                  : null,
                              icon: const Icon(Icons.forum_outlined),
                              label: const Text('Start overleg'),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            }),
            const SizedBox(height: 24),
            Text(
              'Productcycli en onderzoekssessies',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            if (iterations.isEmpty)
              const ListTile(
                leading: Icon(Icons.hourglass_empty),
                title: Text('Nog geen productcycli of onderzoekssessies'),
              ),
            _limitedSection('iterations', iterations, (iteration) {
              final status = '${iteration['status']}';
              // Zelfde onderscheid als hierboven bij het detaildialoog (regel ~767): QUEUED/RUNNING
              // is nog lopend en heeft dus geen afgeronde uitkomst om te classificeren.
              final running = status == 'QUEUED' || status == 'RUNNING';
              final role = iteration['currentRole'];
              final pr = iteration['workspacePullRequestUrl'];
              final timing = iterationTiming(iteration);
              final classification = classifyIterationOutcome(
                status: iteration['status'] as String?,
                criticVerdict: iteration['criticVerdict'] as String?,
                errorMessage: iteration['errorMessage'] as String?,
              );
              return Card(
                child: ListTile(
                  leading: Icon(
                    iteration['mode'] == 'autonomous'
                        ? Icons.auto_awesome
                        : Icons.science_outlined,
                  ),
                  title: Wrap(
                    spacing: 8,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Text(
                        '${iteration['productSlug']} · iteratie ${iteration['sequenceNumber']}',
                      ),
                      running
                          ? const IterationProgressIndicator()
                          : ClassificationBadge(classification: classification),
                    ],
                  ),
                  subtitle: Text(
                    [
                      status,
                      if (role != null) 'bezig: ${_roleLabel('$role')}',
                      'gestart ${timing.startLabel}',
                      if (timing.durationLabel != null) timing.durationLabel!,
                      '${iteration['candidateCount']} kandidaten',
                      if (iteration['criticVerdict'] != null)
                        'criticus: ${iteration['criticVerdict']}',
                      _deliveryLabel('${iteration['mode']}'),
                    ].join(' · '),
                  ),
                  trailing: pr == null
                      ? const Icon(Icons.chevron_right)
                      : const Icon(Icons.call_merge_outlined),
                  onTap: () => _showIteration(iteration),
                ),
              );
            }),
            const SizedBox(height: 24),
            Text(
              'Software Factory-stories',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            if (deliveries.isEmpty)
              const ListTile(
                leading: Icon(Icons.hourglass_empty),
                title: Text(
                  'Nog geen stories naar de Software Factory gestuurd',
                ),
              ),
            _limitedSection('deliveries', sortedDeliveries, (delivery) {
              return ListTile(
                leading: const Icon(Icons.precision_manufacturing_outlined),
                title: Text(
                  '${delivery['externalStoryKey'] ?? 'wordt verstuurd'} · ${delivery['title']}',
                ),
                subtitle: Text(
                  '${delivery['productSlug']} · ${delivery['status']} · ${delivery['remotePhase'] ?? 'nog geen fase'}',
                ),
              );
            }),
            const SizedBox(height: 24),
            Text('Overleggen', style: Theme.of(context).textTheme.titleLarge),
            if (meetings.isEmpty)
              const ListTile(
                leading: Icon(Icons.hourglass_empty),
                title: Text('Nog geen overleggen'),
              ),
            _limitedSection('meetings', meetings, (meeting) {
              final meetingStatus = '${meeting['status']}';
              final outcome = meeting['outcomeSummary'] as String?;
              final workspaceRunId = meeting['workspaceRunId'] as String?;
              return Card(
                child: ListTile(
                  leading: Icon(
                    meeting['initiator'] == 'product'
                        ? Icons.forum
                        : Icons.forum_outlined,
                  ),
                  title: Text(
                    '${meeting['productSlug']} · overleg ${meeting['sequenceNumber']}',
                  ),
                  subtitle: Text(
                    [
                      meetingStatus,
                      '${meeting['initiator']} gestart',
                      if (outcome != null && outcome.isNotEmpty)
                        outcome.length > 150
                            ? '${outcome.substring(0, 150)}…'
                            : outcome,
                    ].join(' · '),
                  ),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (workspaceRunId != null)
                        IconButton(
                          tooltip: 'Notulen bekijken',
                          icon: const Icon(Icons.description_outlined),
                          onPressed: () => _showMeetingMinutes(
                            '${meeting['productSlug']}',
                            workspaceRunId,
                          ),
                        ),
                      const Icon(Icons.chevron_right),
                    ],
                  ),
                  onTap: () => _openMeeting(
                    '${meeting['productSlug']}',
                    '${meeting['id']}',
                  ),
                ),
              );
            }),
            if (humanActions.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text(
                'Benodigde access tokens',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              _limitedSection('humanActions', humanActions, (action) {
                return ListTile(
                  leading: const Icon(Icons.warning_amber_outlined),
                  title: Text('${action['title']}'),
                  subtitle: Text('${action['category']} · ${action['reason']}'),
                  trailing: action['status'] == 'OPEN'
                      ? FilledButton(
                          onPressed: () => _completeHumanAction(action),
                          child: const Text('Gereed melden'),
                        )
                      : null,
                );
              }),
            ],
            const SizedBox(height: 24),
            Text(
              'Storywachtrij',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            ..._buildStoryQueueSections(
              context,
              sortedByNewestFirst(stories, ['createdAt']),
              deliveries,
              visibleCount: _visibleCount,
              onShowMore: _showMore,
            ),
            const SizedBox(height: 24),
            Text('Workspace', style: Theme.of(context).textTheme.titleLarge),
            _limitedSection('publications', publications, (publication) {
              final runId = '${publication['runId']}';
              final productSlug = '${publication['productSlug']}';
              return ListTile(
                title: Text('${publication['artifactPath']}'),
                subtitle: Text(
                  '$productSlug · $runId · ${publication['status']}',
                ),
                leading: const Icon(Icons.description_outlined),
                trailing: const Icon(Icons.open_in_new),
                onTap: () async {
                  final content = await api.artifact(productSlug, runId);
                  if (context.mounted) {
                    showDialog<void>(
                      context: context,
                      builder: (context) => AlertDialog(
                        title: Text('${publication['artifactPath']}'),
                        content: SizedBox(
                          width: 720,
                          child: SingleChildScrollView(
                            child: SelectableText(content),
                          ),
                        ),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(context),
                            child: const Text('Sluiten'),
                          ),
                        ],
                      ),
                    );
                  }
                },
              );
            }),
          ],
        );
      },
    ),
  );
}

class IterationSessionDialog extends StatefulWidget {
  const IterationSessionDialog({
    required this.api,
    required this.productSlug,
    required this.iterationId,
    super.key,
  });

  final DashboardApi api;
  final String productSlug;
  final String iterationId;

  @override
  State<IterationSessionDialog> createState() => _IterationSessionDialogState();
}

class _IterationSessionDialogState extends State<IterationSessionDialog> {
  late Future<Map<String, dynamic>> session;
  Timer? refreshTimer;
  bool _cancelling = false;

  @override
  void initState() {
    super.initState();
    _reload();
    refreshTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (mounted) setState(_reload);
    });
  }

  void _reload() {
    session = widget.api.shadowIterationSession(
      widget.productSlug,
      widget.iterationId,
    );
  }

  @override
  void dispose() {
    refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _confirmCancel() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cyclus annuleren?'),
        content: const Text(
          'De cyclus wordt gemarkeerd als mislukt en het product is meteen weer vrij voor een nieuwe '
          'cyclus. Een agentstap die nog bezig is, wordt niet hard afgebroken, maar de uitkomst ervan '
          'telt niet meer mee.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Nee'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Ja, annuleren'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _cancelling = true);
    try {
      await widget.api.cancelIteration(widget.productSlug, widget.iterationId);
      if (mounted) {
        setState(() {
          _cancelling = false;
          _reload();
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() => _cancelling = false);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<Map<String, dynamic>>(
    future: session,
    builder: (context, snapshot) {
      final iteration = snapshot.data?['iteration'] as Map<String, dynamic>?;
      final status = iteration == null ? null : '${iteration['status']}';
      final running = status == 'QUEUED' || status == 'RUNNING';
      return AlertDialog(
        title: Text('Productcyclus ${widget.iterationId}'),
        content: SizedBox(
          width: 900,
          height: 680,
          child: Builder(
            builder: (context) {
              if (!snapshot.hasData) {
                if (snapshot.hasError) {
                  return Center(
                    child: Text('Sessie kon niet laden: ${snapshot.error}'),
                  );
                }
                return const Center(child: CircularProgressIndicator());
              }
              final result = snapshot.data!;
              final iteration = result['iteration'] as Map<String, dynamic>;
              final steps = result['steps'] as List<dynamic>;
              final artifacts = result['artifacts'] as List<dynamic>;
              final status = '${iteration['status']}';
              final running = status == 'QUEUED' || status == 'RUNNING';
              final currentRole = iteration['currentRole'];
              final dossier = result['dossier'] as String?;
              final summary = iteration['summary'] as String?;
              final timing = iterationTiming(iteration);
              return ListView(
                children: [
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      ClassificationBadge(
                        classification: classifyIterationOutcome(
                          status: iteration['status'] as String?,
                          criticVerdict: iteration['criticVerdict'] as String?,
                          errorMessage: iteration['errorMessage'] as String?,
                        ),
                      ),
                      Chip(label: Text(_deliveryLabel('${iteration['mode']}'))),
                      if (currentRole != null)
                        Chip(label: Text('bezig: $currentRole')),
                      Text('gestart ${timing.startLabel}'),
                      if (timing.durationLabel != null)
                        Text(timing.durationLabel!),
                    ],
                  ),
                  if (running) ...[
                    const SizedBox(height: 12),
                    const LinearProgressIndicator(),
                    const SizedBox(height: 8),
                    Text(
                      currentRole == null
                          ? 'De cyclus staat klaar om te beginnen.'
                          : 'De agent $currentRole is nu bezig. Dit scherm wordt automatisch vernieuwd.',
                    ),
                  ],
                  if (summary != null && summary.trim().isNotEmpty) ...[
                    const SizedBox(height: 16),
                    Card(
                      color: Theme.of(context).colorScheme.secondaryContainer,
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                const Icon(Icons.summarize_outlined),
                                const SizedBox(width: 8),
                                Text(
                                  'Samenvatting voor jou',
                                  style: Theme.of(
                                    context,
                                  ).textTheme.titleMedium,
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            SelectableText(summary),
                          ],
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 16),
                  Text(
                    'Opdracht',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 4),
                  SelectableText('${iteration['focus']}'),
                  if (status == 'FAILED') ...[
                    const SizedBox(height: 16),
                    Builder(
                      builder: (context) {
                        final rawErrorMessage =
                            '${iteration['errorMessage'] ?? ''}'.trim();
                        final errorText = rawErrorMessage.isEmpty
                            ? 'Geen foutreden beschikbaar'
                            : rawErrorMessage;
                        return Semantics(
                          label: 'Foutreden: $errorText',
                          child: ExcludeSemantics(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Foutreden',
                                  style: Theme.of(
                                    context,
                                  ).textTheme.titleMedium,
                                ),
                                const SizedBox(height: 4),
                                SelectableText(errorText),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ],
                  const SizedBox(height: 20),
                  Text(
                    'Voortgang',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  if (steps.isEmpty)
                    const ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(Icons.schedule),
                      title: Text('Nog geen agentstappen gestart'),
                    ),
                  ...steps.map((item) {
                    final step = item as Map<String, dynamic>;
                    final stepStatus = '${step['status']}';
                    return ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(
                        stepStatus == 'COMPLETED'
                            ? Icons.check_circle_outline
                            : stepStatus == 'FAILED'
                            ? Icons.error_outline
                            : Icons.pending_outlined,
                      ),
                      title: Text(
                        '${_roleLabel('${step['role']}')} · poging ${step['attempt']}',
                      ),
                      subtitle: Text(
                        [
                          stepStatus,
                          if (step['startedAt'] != null)
                            'start ${formatDateTime(step['startedAt'])}',
                          if (step['completedAt'] != null)
                            'klaar ${formatDateTime(step['completedAt'])}',
                          if (step['errorMessage'] != null)
                            '${step['errorMessage']}',
                        ].join(' · '),
                      ),
                    );
                  }),
                  const SizedBox(height: 12),
                  Text(
                    'Resultaat en onderbouwing',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  if (dossier != null)
                    ExpansionTile(
                      tilePadding: EdgeInsets.zero,
                      title: const Text('Volledig productdossier'),
                      subtitle: const Text(
                        'Onderzoek, productbesluit, UX, criticus en geaccepteerde stories',
                      ),
                      children: [
                        Align(
                          alignment: Alignment.centerLeft,
                          child: SelectableText(dossier),
                        ),
                      ],
                    ),
                  if (artifacts.isEmpty)
                    const ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(Icons.hourglass_empty),
                      title: Text('Nog geen agentresultaten beschikbaar'),
                    ),
                  ...artifacts.map((item) {
                    final artifact = item as Map<String, dynamic>;
                    final readableFields = _readableArtifactFields(
                      context,
                      '${artifact['artifactType']}',
                      '${artifact['contentJson']}',
                    );
                    return ExpansionTile(
                      tilePadding: EdgeInsets.zero,
                      title: Text(_roleLabel('${artifact['artifactType']}')),
                      subtitle: Text(formatDateTime(artifact['createdAt'])),
                      children: [
                        if (readableFields.isNotEmpty)
                          Align(
                            alignment: Alignment.centerLeft,
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: readableFields,
                            ),
                          ),
                        if (readableFields.isNotEmpty)
                          TechnicalDetailsToggle(
                            key: const Key('technicalDetailsToggle'),
                            content: _prettyJson('${artifact['contentJson']}'),
                          ),
                        if (readableFields.isEmpty)
                          Align(
                            alignment: Alignment.centerLeft,
                            child: SelectableText(
                              _prettyJson('${artifact['contentJson']}'),
                            ),
                          ),
                      ],
                    );
                  }),
                  if (iteration['workspacePullRequestUrl'] != null) ...[
                    const SizedBox(height: 16),
                    Text(
                      'Workspace-publicatie',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    SelectableText('${iteration['workspacePullRequestUrl']}'),
                    if (iteration['workspaceCommitSha'] != null)
                      SelectableText(
                        'Commit: ${iteration['workspaceCommitSha']}',
                      ),
                  ],
                ],
              );
            },
          ),
        ),
        actions: [
          if (running)
            TextButton(
              onPressed: _cancelling ? null : _confirmCancel,
              child: _cancelling
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Cyclus annuleren'),
            ),
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Sluiten'),
          ),
        ],
      );
    },
  );
}

String _prettyJson(String value) {
  try {
    return const JsonEncoder.withIndent('  ').convert(jsonDecode(value));
  } catch (_) {
    return value;
  }
}

/// Standaard ingeklapte, toetsenbord- en schermlezerbedienbare toggle voor de ruwe-JSON-weergave
/// van een agentresultaat (product-85). `InkWell` is zelf al bereikbaar met Tab/Shift+Tab en
/// activeerbaar met Enter/Spatie; `Semantics(expanded: ...)` is het Flutter-web-equivalent van
/// `aria-expanded` en wordt via `MergeSemantics` samengevoegd met de knop-semantiek (focus/rol)
/// van de onderliggende `InkWell`.
class TechnicalDetailsToggle extends StatefulWidget {
  const TechnicalDetailsToggle({required this.content, super.key});

  final String content;

  @override
  State<TechnicalDetailsToggle> createState() => _TechnicalDetailsToggleState();
}

class _TechnicalDetailsToggleState extends State<TechnicalDetailsToggle> {
  bool _expanded = false;

  void _toggle() => setState(() => _expanded = !_expanded);

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: Semantics(
        expanded: _expanded,
        button: true,
        label: 'Toon technische details',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            InkWell(
              onTap: _toggle,
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(_expanded ? Icons.expand_less : Icons.expand_more),
                    const SizedBox(width: 4),
                    const Text('Toon technische details'),
                  ],
                ),
              ),
            ),
            if (_expanded)
              Align(
                alignment: Alignment.centerLeft,
                child: SelectableText(widget.content),
              ),
          ],
        ),
      ),
    );
  }
}

/// Bekende `contentJson`-velden per basisrol, bevestigd tegen
/// `productfactory/.../iteration/ShadowSchemas.kt` (`additionalProperties:false`, dus deze lijst is
/// autoritatief zolang dat schema niet wijzigt):
/// - `researcher`: summary, findings[].{title,finding,sourceUrls[]}, currentState.{purpose,gaps[]},
///   improvementOpportunities[], sources[].{url,rationale}, inspiration[].{name,url,relevance}.
/// - `product_owner`: productDirection, rationale, priorities[],
///   decisions[].{decision,rationale,sourceUrls[]}, rejectedOptions[].
/// - `ux_designer`: flowName, userGoal, steps[], wireframe, hypotheses[], accessibility[],
///   privacyConsiderations[].
/// - `story_writer`: candidates[].{title,description,acceptanceCriteria[],dependsOn[],risks[]}.
/// - `critic`: overallVerdict, summary, issues[].{severity,category,description},
///   candidateReviews[].{verdict,reason}, requiredChanges[].
///
/// `artifact['artifactType']` draagt bij een retrypoging een `-2`/`-3`-suffix (zie
/// `ShadowIterationApi.kt`); die wordt hier gestript zodat retries dezelfde leesbare velden
/// gebruiken als de eerste poging.
List<Widget> _readableArtifactFields(
  BuildContext context,
  String artifactType,
  String contentJson,
) {
  final Map<String, dynamic> data;
  try {
    final decoded = jsonDecode(contentJson);
    if (decoded is! Map) return const [];
    data = decoded.cast<String, dynamic>();
  } catch (_) {
    return const [];
  }

  final baseRole = artifactType.replaceAll(RegExp(r'-\d+$'), '');
  return switch (baseRole) {
    'researcher' => [
      ..._readableText(context, 'Samenvatting', data['summary']),
      ..._readableObjectList(
        context,
        'Bevindingen',
        data['findings'],
        (context, item) => [
          ..._readableSelectableText(item['title'], bold: true),
          ..._readableSelectableText(item['finding']),
          ..._bulletLines(item['sourceUrls']),
        ],
      ),
      ..._readableObject(
        context,
        'Huidige situatie',
        data['currentState'],
        (context, item) => [
          ..._readableSelectableText(item['purpose']),
          ..._bulletLines(item['gaps']),
        ],
      ),
      ..._readableBulletList(
        context,
        'Verbetermogelijkheden',
        data['improvementOpportunities'],
      ),
      ..._readableObjectList(
        context,
        'Bronnen',
        data['sources'],
        (context, item) => [
          ..._readableSelectableText(item['url'], bold: true),
          ..._readableSelectableText(item['rationale']),
        ],
      ),
      ..._readableObjectList(
        context,
        'Inspiratie',
        data['inspiration'],
        (context, item) => [
          ..._readableSelectableText(item['name'], bold: true),
          ..._readableSelectableText(item['url']),
          ..._readableSelectableText(item['relevance']),
        ],
      ),
    ],
    'product_owner' => [
      ..._readableText(context, 'Productrichting', data['productDirection']),
      ..._readableText(context, 'Onderbouwing', data['rationale']),
      ..._readableBulletList(context, 'Prioriteiten', data['priorities']),
      ..._readableObjectList(
        context,
        'Besluiten',
        data['decisions'],
        (context, item) => [
          ..._readableSelectableText(item['decision'], bold: true),
          ..._readableSelectableText(item['rationale']),
          ..._bulletLines(item['sourceUrls']),
        ],
      ),
      ..._readableBulletList(
        context,
        'Afgewezen opties',
        data['rejectedOptions'],
      ),
    ],
    'ux_designer' => [
      ..._readableText(context, 'Flow', data['flowName']),
      ..._readableText(context, 'Gebruikersdoel', data['userGoal']),
      ..._readableBulletList(context, 'Stappen', data['steps']),
      ..._readableText(context, 'Wireframe', data['wireframe']),
      ..._readableBulletList(context, 'Hypotheses', data['hypotheses']),
      ..._readableBulletList(
        context,
        'Toegankelijkheid',
        data['accessibility'],
      ),
      ..._readableBulletList(
        context,
        'Privacyoverwegingen',
        data['privacyConsiderations'],
      ),
    ],
    'story_writer' => [
      ..._readableObjectList(
        context,
        'Storykandidaten',
        data['candidates'],
        (context, item) => [
          ..._readableSelectableText(item['title'], bold: true),
          ..._readableSelectableText(item['description']),
          ..._bulletLines(item['acceptanceCriteria']),
          ..._bulletLines(item['dependsOn']),
          ..._bulletLines(item['risks']),
        ],
      ),
    ],
    'critic' => [
      ..._readableText(context, 'Eindoordeel', data['overallVerdict']),
      ..._readableText(context, 'Samenvatting', data['summary']),
      ..._readableObjectList(
        context,
        'Aandachtspunten',
        data['issues'],
        (context, item) => [
          ..._readableSelectableText(
            [
              '${item['severity'] ?? ''}'.trim(),
              '${item['category'] ?? ''}'.trim(),
            ].where((value) => value.isNotEmpty).join(' · '),
            bold: true,
          ),
          ..._readableSelectableText(item['description']),
        ],
      ),
      ..._readableObjectList(
        context,
        'Beoordeling per kandidaat',
        data['candidateReviews'],
        (context, item) => [
          ..._readableSelectableText(item['verdict'], bold: true),
          ..._readableSelectableText(item['reason']),
        ],
      ),
      ..._readableBulletList(
        context,
        'Vereiste wijzigingen',
        data['requiredChanges'],
      ),
    ],
    _ => _readableGenericFields(context, data),
  };
}

/// Generieke vangnet-weergave (product-97) voor wanneer geen van de rolspecifieke branches
/// hierboven matcht: toont top-level string-velden en top-level lijsten die uitsluitend uit
/// primitieve waarden (String/num/bool) bestaan alsnog leesbaar, met een label via
/// [humanizeFieldKey]. Geneste objecten, arrays van objecten en lijsten met gemengde of
/// niet-primitieve elementen worden overgeslagen; leveren die als enige data op, dan blijft de
/// bestaande rauwe-JSON-fallback (`readableFields.isEmpty`, geen toggle) ongewijzigd.
List<Widget> _readableGenericFields(
  BuildContext context,
  Map<String, dynamic> data,
) {
  final widgets = <Widget>[];
  for (final entry in data.entries) {
    final value = entry.value;
    if (value is String) {
      widgets.addAll(
        _readableText(context, humanizeFieldKey(entry.key), value),
      );
    } else if (value is List &&
        value.every((item) => item is String || item is num || item is bool)) {
      widgets.addAll(
        _readableBulletList(context, humanizeFieldKey(entry.key), value),
      );
    }
  }
  return widgets;
}

/// Eén regel platte tekst binnen een objectitem (findings/decisions/etc.); leeg/null wordt
/// weggelaten zodat er geen lege regels of het woord "null" verschijnen.
List<Widget> _readableSelectableText(dynamic value, {bool bold = false}) {
  final text = value == null ? '' : '$value'.trim();
  if (text.isEmpty) return const [];
  return [
    SelectableText(
      text,
      style: bold ? const TextStyle(fontWeight: FontWeight.w600) : null,
    ),
  ];
}

/// Opsommingsregels ('• item') zonder eigen kopje, voor gebruik binnen een objectitem.
List<Widget> _bulletLines(dynamic value) {
  if (value is! List) return const [];
  return value
      .map((entry) => '$entry'.trim())
      .where((entry) => entry.isNotEmpty)
      .map((entry) => SelectableText('• $entry'))
      .toList();
}

/// Kopje + tekstblok voor een scalar veld; lege/null waarden leveren niets op.
List<Widget> _readableText(BuildContext context, String label, dynamic value) {
  final text = value == null ? '' : '$value'.trim();
  if (text.isEmpty) return const [];
  return [
    Text(label, style: Theme.of(context).textTheme.titleSmall),
    const SizedBox(height: 4),
    SelectableText(text),
    const SizedBox(height: 12),
  ];
}

/// Kopje + opsomming voor een array van strings; lege/ontbrekende arrays leveren niets op.
List<Widget> _readableBulletList(
  BuildContext context,
  String label,
  dynamic value,
) {
  final lines = _bulletLines(value);
  if (lines.isEmpty) return const [];
  return [
    Text(label, style: Theme.of(context).textTheme.titleSmall),
    const SizedBox(height: 4),
    ...lines,
    const SizedBox(height: 12),
  ];
}

/// Kopje + lijst van objecten (bv. findings, decisions); elk object wordt via [itemBuilder] naar
/// zijn eigen regels omgezet, objecten zonder inhoud worden overgeslagen.
List<Widget> _readableObjectList(
  BuildContext context,
  String label,
  dynamic value,
  List<Widget> Function(BuildContext context, Map<String, dynamic> item)
  itemBuilder,
) {
  if (value is! List) return const [];
  final blocks = <Widget>[];
  for (final entry in value) {
    if (entry is! Map) continue;
    final itemWidgets = itemBuilder(context, entry.cast<String, dynamic>());
    if (itemWidgets.isEmpty) continue;
    blocks.add(
      Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: itemWidgets,
        ),
      ),
    );
  }
  if (blocks.isEmpty) return const [];
  return [
    Text(label, style: Theme.of(context).textTheme.titleSmall),
    const SizedBox(height: 4),
    ...blocks,
    const SizedBox(height: 12),
  ];
}

/// Kopje + regels voor één enkel genest object (bv. `currentState`); lege objecten leveren niets op.
List<Widget> _readableObject(
  BuildContext context,
  String label,
  dynamic value,
  List<Widget> Function(BuildContext context, Map<String, dynamic> item)
  itemBuilder,
) {
  if (value is! Map) return const [];
  final itemWidgets = itemBuilder(context, value.cast<String, dynamic>());
  if (itemWidgets.isEmpty) return const [];
  return [
    Text(label, style: Theme.of(context).textTheme.titleSmall),
    const SizedBox(height: 4),
    ...itemWidgets,
    const SizedBox(height: 12),
  ];
}

/// De onderliggende `mode` ('autonomous'/'shadow') is een door de backend afgeleide waarde, geen keuze van de
/// gebruiker — deze tekst maakt duidelijk wat dat concreet betekent, in plaats van het jargon zelf te tonen.
String _deliveryLabel(String mode) => mode == 'autonomous'
    ? 'kan doorgezet worden'
    : 'niet doorgezet (product staat niet op autonoom)';

String _roleLabel(String value) => switch (value.toLowerCase()) {
  'researcher' => 'Onderzoeker',
  'product_owner' => 'Product owner',
  'ux_designer' => 'UX-ontwerp',
  'story_writer' => 'Story writer',
  'story_writer-2' => 'Story writer · revisie 2',
  'story_writer-3' => 'Story writer · revisie 3',
  'critic' => 'Criticus',
  'critic-2' => 'Criticus · revisie 2',
  'critic-3' => 'Criticus · revisie 3',
  _ => value.replaceAll('_', ' '),
};

/// Zet een JSON-veldnaam (zoals gebruikt in shadow-iteratie-artefacten, zie
/// `_readableArtifactFields` hierboven, main.dart regel ~1143) om naar een leesbaar label.
/// Puur en side-effectvrij: geen state, widgets of netwerkverkeer. Nog niet gekoppeld aan
/// bestaande rendercode ([_readableArtifactFields]/[_roleLabel]) — dat is scope van een
/// vervolgstory.
///
/// Bewust top-level en publiek (geen `_`-prefix): Dart-privacy is per bestand, dus een private
/// functie zou vanuit een los testbestand niet aanroepbaar zijn. Volgt hiermee hetzelfde patroon
/// als de publieke pure functies in `formatting.dart`.
String humanizeFieldKey(String key) {
  const knownLabels = {
    'findings': 'Bevindingen',
    'decision': 'Besluit',
    'story': 'Story',
    'verdict': 'Eindoordeel',
    'reason': 'Reden',
  };
  final knownLabel = knownLabels[key];
  if (knownLabel != null) return knownLabel;

  final withSpaces = key
      .replaceAll('_', ' ')
      .replaceAllMapped(RegExp(r'(?<=[a-z0-9])(?=[A-Z])'), (match) => ' ');
  return withSpaces
      .split(' ')
      .where((word) => word.isNotEmpty)
      .map((word) => word[0].toUpperCase() + word.substring(1))
      .join(' ');
}

/// Bouwt de wachtrij-secties (Fout / Bezig / In wachtrij / Klaar) voor de storykandidaten die de backend al
/// filtert op niet-afgekeurd (zie `StoryCandidateController.list`). Elke kandidaat wordt gekoppeld aan zijn
/// Software Factory-levering (indien aanwezig) om de fase te bepalen; zonder levering staat hij nog in de wachtrij.
///
/// Elke subsectie heeft zijn eigen 5/+10-teller; die wordt via [visibleCount]/[onShowMore] uit de
/// paginastate aangereikt zodat de auto-refresh de uitklapstand niet weggooit.
List<Widget> _buildStoryQueueSections(
  BuildContext context,
  List<dynamic> stories,
  List<dynamic> deliveries, {
  required int Function(String section) visibleCount,
  required void Function(String section, int itemCount) onShowMore,
}) {
  final deliveryByCandidate = <int, Map<String, dynamic>>{};
  for (final item in deliveries) {
    final delivery = item as Map<String, dynamic>;
    final candidateId = delivery['candidateId'];
    if (candidateId is int) deliveryByCandidate[candidateId] = delivery;
  }

  final failed = <Map<String, dynamic>>[];
  final inProgress = <Map<String, dynamic>>[];
  final queued = <Map<String, dynamic>>[];
  final done = <Map<String, dynamic>>[];
  for (final item in stories) {
    final story = item as Map<String, dynamic>;
    final delivery = deliveryByCandidate[story['id']];
    switch (delivery?['status']) {
      case null:
        queued.add(story);
      case 'ERROR':
        failed.add(story);
      case 'DONE':
        done.add(story);
      default:
        inProgress.add(story);
    }
  }

  Widget section(
    String key,
    String title,
    IconData icon,
    List<Map<String, dynamic>> items,
  ) {
    if (items.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Text(
              '$title (${items.length})',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
          LimitedListSection(
            itemCount: items.length,
            visibleCount: visibleCount(key),
            onShowMore: () => onShowMore(key, items.length),
            itemBuilder: (context, index) {
              final story = items[index];
              final delivery = deliveryByCandidate[story['id']];
              final iteration = story['iterationSequenceNumber'];
              final subtitleParts = [
                '${story['productSlug']}',
                if (iteration != null) 'iteratie $iteration',
                if (delivery?['externalStoryKey'] != null)
                  '${delivery!['externalStoryKey']}',
                if (delivery?['remotePhase'] != null)
                  '${delivery!['remotePhase']}',
                if (delivery?['status'] == 'ERROR' &&
                    delivery?['errorMessage'] != null)
                  '${delivery!['errorMessage']}',
              ];
              final blockedReason = '${story['blockedReason'] ?? ''}'.trim();
              final isBlocked =
                  story['blocked'] == true && blockedReason.isNotEmpty;
              final blockedColors = kClassificationColors[kGuardrailConflict]!;
              // MergeSemantics zorgt dat titel/subtitle/blokkeerlabel als één toegankelijke
              // naam/beschrijving van de kaart opvraagbaar zijn, zonder de bestaande
              // Card/ListTile-elementen of het tap-gedrag te wijzigen.
              return MergeSemantics(
                child: Card(
                  child: ListTile(
                    leading: Icon(icon),
                    title: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text('${story['title']}'),
                        if (isBlocked)
                          Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                color: blockedColors.background,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    Icons.block,
                                    size: 14,
                                    color: blockedColors.foreground,
                                  ),
                                  const SizedBox(width: 4),
                                  Flexible(
                                    child: Text(
                                      'Geblokkeerd: $blockedReason',
                                      style: TextStyle(
                                        color: blockedColors.foreground,
                                        fontWeight: FontWeight.w600,
                                        fontSize: 12,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ),
                    subtitle: Text(subtitleParts.join(' · ')),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        _showStoryCandidateDetails(context, story, delivery),
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  return [
    section('queue-failed', 'Fout', Icons.error_outline, failed),
    section(
      'queue-in-progress',
      'Bezig',
      Icons.precision_manufacturing_outlined,
      inProgress,
    ),
    section('queue-queued', 'In wachtrij', Icons.hourglass_empty, queued),
    section('queue-done', 'Klaar', Icons.check_circle_outline, done),
    if (failed.isEmpty && inProgress.isEmpty && queued.isEmpty && done.isEmpty)
      const ListTile(
        leading: Icon(Icons.hourglass_empty),
        title: Text('Nog geen storykandidaten'),
      ),
  ];
}

void _showStoryCandidateDetails(
  BuildContext context,
  Map<String, dynamic> story,
  Map<String, dynamic>? delivery,
) {
  showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text('${story['title']}'),
      content: SizedBox(
        width: 720,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (delivery != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Wrap(
                    spacing: 8,
                    children: [
                      Chip(label: Text('${delivery['status']}')),
                      if (delivery['externalStoryKey'] != null)
                        Chip(label: Text('${delivery['externalStoryKey']}')),
                      if (delivery['remotePhase'] != null)
                        Chip(label: Text('${delivery['remotePhase']}')),
                    ],
                  ),
                ),
              SelectableText('${story['description']}'),
              if ('${story['acceptanceCriteria'] ?? ''}'.trim().isNotEmpty) ...[
                const SizedBox(height: 16),
                Text(
                  'Acceptatiecriteria',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                SelectableText('${story['acceptanceCriteria']}'),
              ],
              if ('${story['criticReason'] ?? ''}'.trim().isNotEmpty) ...[
                const SizedBox(height: 16),
                Text(
                  'Beoordeling criticus',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                SelectableText('${story['criticReason']}'),
              ],
              if (delivery?['errorMessage'] != null) ...[
                const SizedBox(height: 16),
                Text(
                  'Foutmelding',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                SelectableText('${delivery!['errorMessage']}'),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Sluiten'),
        ),
      ],
    ),
  );
}

class AddProductDialog extends StatefulWidget {
  const AddProductDialog({required this.aiCatalog, super.key});
  final Map<String, dynamic> aiCatalog;
  @override
  State<AddProductDialog> createState() => _AddProductDialogState();
}

class _AddProductDialogState extends State<AddProductDialog> {
  final slug = TextEditingController();
  final name = TextEditingController();
  final mission = TextEditingController();
  final maxStoriesPerCycle = TextEditingController(text: '3');
  String developmentMode = 'manual';
  String workspaceOwnership = 'owner';
  late String aiProvider = widget.aiCatalog.keys.first;
  late String aiModel =
      (widget.aiCatalog[aiProvider] as List<dynamic>).first as String;
  List<String> iterationTimes = ['03:00'];

  @override
  void dispose() {
    slug.dispose();
    name.dispose();
    mission.dispose();
    maxStoriesPerCycle.dispose();
    super.dispose();
  }

  void _submit() {
    final normalizedSlug = slug.text.trim().toLowerCase();
    final stories = int.tryParse(maxStoriesPerCycle.text.trim());
    if (normalizedSlug.isEmpty ||
        name.text.trim().isEmpty ||
        mission.text.trim().isEmpty ||
        stories == null ||
        iterationTimes.isEmpty) {
      return;
    }
    Navigator.pop(context, <String, dynamic>{
      'slug': normalizedSlug,
      'name': name.text.trim(),
      'mission': mission.text.trim(),
      'softwareFactoryProjectKey': normalizedSlug,
      'targetRepositoryName': normalizedSlug,
      'developmentMode': developmentMode,
      'workspaceOwnership': workspaceOwnership,
      'status': 'draft',
      'aiProvider': aiProvider,
      'aiModel': aiModel,
      'maxStoriesPerCycle': stories,
      'iterationTimes': iterationTimes,
    });
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Product toevoegen'),
    content: SizedBox(
      width: 520,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: slug,
              decoration: const InputDecoration(
                labelText: 'Slug',
                hintText: 'mijn-product',
              ),
            ),
            TextField(
              controller: name,
              decoration: const InputDecoration(labelText: 'Naam'),
            ),
            TextField(
              controller: mission,
              minLines: 2,
              maxLines: 4,
              decoration: const InputDecoration(labelText: 'Missie'),
            ),
            DropdownButtonFormField<String>(
              initialValue: developmentMode,
              decoration: const InputDecoration(labelText: 'Ontwikkelmodus'),
              items: const [
                DropdownMenuItem(value: 'manual', child: Text('Handmatig')),
                DropdownMenuItem(value: 'autonomous', child: Text('Autonoom')),
                DropdownMenuItem(
                  value: 'observe-only',
                  child: Text('Alleen observeren'),
                ),
              ],
              onChanged: (value) => setState(() => developmentMode = value!),
            ),
            DropdownButtonFormField<String>(
              initialValue: workspaceOwnership,
              decoration: const InputDecoration(
                labelText: 'Workspace-eigenaar',
              ),
              items: const [
                DropdownMenuItem(value: 'owner', child: Text('Eigenaar')),
                DropdownMenuItem(
                  value: 'product-factory',
                  child: Text('Product Factory'),
                ),
              ],
              onChanged: (value) => setState(() => workspaceOwnership = value!),
            ),
            TextField(
              controller: maxStoriesPerCycle,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Max stories per cyclus',
              ),
            ),
            const SizedBox(height: 8),
            AiProviderModelFields(
              aiCatalog: widget.aiCatalog,
              provider: aiProvider,
              model: aiModel,
              onChanged: (provider, model) => setState(() {
                aiProvider = provider;
                aiModel = model;
              }),
            ),
            const SizedBox(height: 8),
            IterationTimesField(
              times: iterationTimes,
              onChanged: (value) => setState(() => iterationTimes = value),
            ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Annuleren'),
      ),
      FilledButton(onPressed: _submit, child: const Text('Toevoegen')),
    ],
  );
}

class ProductSettingsDialog extends StatefulWidget {
  const ProductSettingsDialog({
    required this.product,
    required this.aiCatalog,
    super.key,
  });
  final Map<String, dynamic> product;
  final Map<String, dynamic> aiCatalog;
  @override
  State<ProductSettingsDialog> createState() => _ProductSettingsDialogState();
}

class _ProductSettingsDialogState extends State<ProductSettingsDialog> {
  late String developmentMode = '${widget.product['developmentMode']}';
  late final maxStoriesPerCycle = TextEditingController(
    text: '${widget.product['maxStoriesPerCycle']}',
  );
  late final wipLimit = TextEditingController(
    text: '${widget.product['wipLimit']}',
  );
  late String aiProvider = '${widget.product['aiProvider']}';
  late String aiModel = '${widget.product['aiModel']}';
  late List<String> iterationTimes = List<String>.from(
    (widget.product['iterationTimes'] as List<dynamic>?) ?? const ['03:00'],
  );

  @override
  void dispose() {
    maxStoriesPerCycle.dispose();
    wipLimit.dispose();
    super.dispose();
  }

  void _submit() {
    final stories = int.tryParse(maxStoriesPerCycle.text.trim());
    final wip = int.tryParse(wipLimit.text.trim());
    if (stories == null || wip == null || iterationTimes.isEmpty) return;
    Navigator.pop(context, <String, dynamic>{
      'developmentMode': developmentMode,
      'maxStoriesPerCycle': stories,
      'wipLimit': wip,
      'aiProvider': aiProvider,
      'aiModel': aiModel,
      'iterationTimes': iterationTimes,
    });
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text('Instellingen · ${widget.product['name']}'),
    content: SizedBox(
      width: 520,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            DropdownButtonFormField<String>(
              initialValue: developmentMode,
              decoration: const InputDecoration(labelText: 'Ontwikkelmodus'),
              items: const [
                DropdownMenuItem(value: 'manual', child: Text('Handmatig')),
                DropdownMenuItem(value: 'autonomous', child: Text('Autonoom')),
                DropdownMenuItem(
                  value: 'observe-only',
                  child: Text('Alleen observeren'),
                ),
              ],
              onChanged: (value) => setState(() => developmentMode = value!),
            ),
            TextField(
              controller: maxStoriesPerCycle,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Max stories per cyclus',
                helperText:
                    'Hoeveel storykandidaten de story writer per cyclus mag schrijven.',
              ),
            ),
            TextField(
              controller: wipLimit,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'WIP-limiet',
                helperText:
                    'Hoeveel stories er tegelijk in levering mogen zijn.',
              ),
            ),
            const SizedBox(height: 8),
            AiProviderModelFields(
              aiCatalog: widget.aiCatalog,
              provider: aiProvider,
              model: aiModel,
              onChanged: (provider, model) => setState(() {
                aiProvider = provider;
                aiModel = model;
              }),
            ),
            const SizedBox(height: 8),
            IterationTimesField(
              times: iterationTimes,
              onChanged: (value) => setState(() => iterationTimes = value),
            ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Annuleren'),
      ),
      FilledButton(onPressed: _submit, child: const Text('Opslaan')),
    ],
  );
}

/// Providerdropdown + bijbehorende modeldropdown; het modelaanbod verandert mee met de gekozen provider.
class AiProviderModelFields extends StatelessWidget {
  const AiProviderModelFields({
    required this.aiCatalog,
    required this.provider,
    required this.model,
    required this.onChanged,
    super.key,
  });
  final Map<String, dynamic> aiCatalog;
  final String provider;
  final String model;
  final void Function(String provider, String model) onChanged;

  @override
  Widget build(BuildContext context) {
    final providers = aiCatalog.keys.toList();
    final models =
        (aiCatalog[provider] as List<dynamic>?)?.cast<String>() ??
        const <String>[];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('AI-engine', style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 4),
        Row(
          children: [
            Expanded(
              child: DropdownButtonFormField<String>(
                initialValue: providers.contains(provider)
                    ? provider
                    : providers.first,
                decoration: const InputDecoration(labelText: 'Provider'),
                items: providers
                    .map(
                      (value) =>
                          DropdownMenuItem(value: value, child: Text(value)),
                    )
                    .toList(),
                onChanged: (value) {
                  if (value == null) return;
                  final nextModels = (aiCatalog[value] as List<dynamic>)
                      .cast<String>();
                  onChanged(value, nextModels.first);
                },
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: DropdownButtonFormField<String>(
                initialValue: models.contains(model) ? model : models.first,
                decoration: const InputDecoration(labelText: 'Model'),
                items: models
                    .map(
                      (value) =>
                          DropdownMenuItem(value: value, child: Text(value)),
                    )
                    .toList(),
                onChanged: (value) {
                  if (value == null) return;
                  onChanged(provider, value);
                },
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// Bewerkbare lijst met dagelijkse cyclustijden (bv. 03:00, 08:00, 21:00), elk als eigen chip.
class IterationTimesField extends StatelessWidget {
  const IterationTimesField({
    required this.times,
    required this.onChanged,
    super.key,
  });
  final List<String> times;
  final ValueChanged<List<String>> onChanged;

  Future<void> _addTime(BuildContext context) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 3, minute: 0),
    );
    if (picked == null) return;
    final formatted =
        '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
    if (times.contains(formatted)) return;
    onChanged([...times, formatted]..sort());
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('Cyclustijden', style: Theme.of(context).textTheme.titleSmall),
      const SizedBox(height: 4),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final time in times)
            Chip(
              label: Text(time),
              onDeleted: times.length > 1
                  ? () => onChanged(times.where((t) => t != time).toList())
                  : null,
            ),
          ActionChip(
            avatar: const Icon(Icons.add, size: 18),
            label: const Text('Tijd toevoegen'),
            onPressed: () => _addTime(context),
          ),
        ],
      ),
    ],
  );
}

/// Neutrale voortgangsindicator voor een lopende/wachtende iteratie (`status` QUEUED/RUNNING),
/// getoond in plaats van de [ClassificationBadge]: er is dan nog geen afgeronde uitkomst om te
/// classificeren (zie ook regel ~767, waar hetzelfde status-onderscheid al gebruikt wordt in het
/// detaildialoog). `Semantics(liveRegion: true)` is het Flutter-web-equivalent van
/// `aria-live="polite"`, zodat een schermlezer meekrijgt wanneer deze indicator verschijnt of weer
/// plaatsmaakt voor een badge na de volgende auto-refresh.
class IterationProgressIndicator extends StatelessWidget {
  const IterationProgressIndicator({super.key});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      liveRegion: true,
      label: 'bezig: iteratie loopt nog',
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(12),
        ),
        child: const ExcludeSemantics(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                width: 12,
                height: 12,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              SizedBox(width: 6),
              Text(
                'bezig',
                style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class MetricCard extends StatelessWidget {
  const MetricCard({
    required this.label,
    required this.value,
    required this.icon,
    super.key,
  });
  final String label, value;
  final IconData icon;
  @override
  Widget build(BuildContext context) => SizedBox(
    width: 260,
    child: Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Icon(icon, size: 36),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    value,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  Text(label),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
