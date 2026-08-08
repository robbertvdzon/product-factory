import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'api.dart';
import 'config.dart';
import 'google_button_stub.dart'
    if (dart.library.html) 'google_button_web.dart'
    as google_button;
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
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Productcyclus voor $slug is gestart.')));
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
        final products = snapshot.data![0] as List<dynamic>;
        final stories = snapshot.data![1] as List<dynamic>;
        final publications = snapshot.data![2] as List<dynamic>;
        final iterations = snapshot.data![3] as List<dynamic>;
        final deliveries = snapshot.data![4] as List<dynamic>;
        final humanActions = snapshot.data![5] as List<dynamic>;
        final aiCatalog = snapshot.data![6] as Map<String, dynamic>;
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
            ...products.map((item) {
              final product = item as Map<String, dynamic>;
              final slug = '${product['slug']}';
              final status = '${product['status']}';
              final active = status == 'active';
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
                          Text('Max stories per cyclus: ${product['maxStoriesPerCycle']}'),
                          Text('WIP: ${product['wipLimit']}'),
                          Text('AI: ${product['aiProvider']} · ${product['aiModel']}'),
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
            ...iterations.map((item) {
              final iteration = item as Map<String, dynamic>;
              final status = '${iteration['status']}';
              final role = iteration['currentRole'];
              final pr = iteration['workspacePullRequestUrl'];
              return Card(
                child: ListTile(
                  leading: Icon(
                    iteration['mode'] == 'autonomous'
                        ? Icons.auto_awesome
                        : Icons.science_outlined,
                  ),
                  title: Text(
                    '${iteration['productSlug']} · iteratie ${iteration['sequenceNumber']}',
                  ),
                  subtitle: Text(
                    [
                      status,
                      if (role != null) 'bezig: ${_roleLabel('$role')}',
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
            ...deliveries.map((item) {
              final delivery = item as Map<String, dynamic>;
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
            if (humanActions.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text(
                'Benodigde access tokens',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              ...humanActions.map((item) {
                final action = item as Map<String, dynamic>;
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
              'Storykandidaten',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            ...stories.map((item) {
              final story = item as Map<String, dynamic>;
              return ListTile(
                title: Text('${story['title']}'),
                subtitle: Text('${story['productSlug']} · ${story['status']}'),
                leading: const Icon(Icons.notes),
              );
            }),
            const SizedBox(height: 24),
            Text('Workspace', style: Theme.of(context).textTheme.titleLarge),
            ...publications.map((item) {
              final publication = item as Map<String, dynamic>;
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

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text('Productcyclus ${widget.iterationId}'),
    content: SizedBox(
      width: 900,
      height: 680,
      child: FutureBuilder<Map<String, dynamic>>(
        future: session,
        builder: (context, snapshot) {
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
          return ListView(
            children: [
              Wrap(
                spacing: 8,
                runSpacing: 8,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  Chip(label: Text(status)),
                  Chip(label: Text(_deliveryLabel('${iteration['mode']}'))),
                  if (currentRole != null)
                    Chip(label: Text('bezig: $currentRole')),
                  Text('${iteration['createdAt']}'),
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
                              style: Theme.of(context).textTheme.titleMedium,
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
              Text('Opdracht', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 4),
              SelectableText('${iteration['focus']}'),
              const SizedBox(height: 20),
              Text('Voortgang', style: Theme.of(context).textTheme.titleMedium),
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
                        'start ${step['startedAt']}',
                      if (step['completedAt'] != null)
                        'klaar ${step['completedAt']}',
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
                return ExpansionTile(
                  tilePadding: EdgeInsets.zero,
                  title: Text(_roleLabel('${artifact['artifactType']}')),
                  subtitle: Text('${artifact['createdAt']}'),
                  children: [
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
                  SelectableText('Commit: ${iteration['workspaceCommitSha']}'),
              ],
            ],
          );
        },
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Sluiten'),
      ),
    ],
  );
}

String _prettyJson(String value) {
  try {
    return const JsonEncoder.withIndent('  ').convert(jsonDecode(value));
  } catch (_) {
    return value;
  }
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
  late String aiModel = (widget.aiCatalog[aiProvider] as List<dynamic>).first as String;
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
              decoration: const InputDecoration(labelText: 'Workspace-eigenaar'),
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
                helperText: 'Hoeveel storykandidaten de story writer per cyclus mag schrijven.',
              ),
            ),
            TextField(
              controller: wipLimit,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'WIP-limiet',
                helperText: 'Hoeveel stories er tegelijk in levering mogen zijn.',
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
    final models = (aiCatalog[provider] as List<dynamic>?)?.cast<String>() ?? const <String>[];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('AI-engine', style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 4),
        Row(
          children: [
            Expanded(
              child: DropdownButtonFormField<String>(
                initialValue: providers.contains(provider) ? provider : providers.first,
                decoration: const InputDecoration(labelText: 'Provider'),
                items: providers
                    .map((value) => DropdownMenuItem(value: value, child: Text(value)))
                    .toList(),
                onChanged: (value) {
                  if (value == null) return;
                  final nextModels = (aiCatalog[value] as List<dynamic>).cast<String>();
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
                    .map((value) => DropdownMenuItem(value: value, child: Text(value)))
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
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(value, style: Theme.of(context).textTheme.headlineMedium),
                Text(label),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}
