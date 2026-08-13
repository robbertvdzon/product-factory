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
import 'iteration_evidence.dart';
import 'iteration_results.dart';
import 'limited_list.dart';
import 'meeting_dialog.dart';
import 'roadmap.dart';
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

class DashboardSource<T> {
  const DashboardSource._({this.value, this.error, required this.loading});

  factory DashboardSource.loading() => const DashboardSource._(loading: true);

  factory DashboardSource.loaded(T value) =>
      DashboardSource._(value: value, loading: false);

  factory DashboardSource.failure(Object error) =>
      DashboardSource._(error: error, loading: false);

  final T? value;
  final Object? error;
  final bool loading;

  bool get loaded => value != null;
  bool get failed => error != null;
}

Future<DashboardSource<T>> _captureSource<T>(Future<T> future) async {
  try {
    return DashboardSource.loaded(await future);
  } catch (error) {
    return DashboardSource.failure(error);
  }
}

class _OverviewResultsBuilder extends StatelessWidget {
  const _OverviewResultsBuilder({
    required this.iterationFuture,
    required this.candidateFuture,
    required this.deliveryFuture,
    required this.builder,
  });

  final Future<DashboardSource<List<dynamic>>> iterationFuture;
  final Future<DashboardSource<List<dynamic>>> candidateFuture;
  final Future<DashboardSource<List<dynamic>>> deliveryFuture;
  final Widget Function(
    BuildContext context,
    DashboardSource<List<dynamic>> iterations,
    DashboardSource<List<dynamic>> candidates,
    DashboardSource<List<dynamic>> deliveries,
  )
  builder;

  @override
  Widget build(BuildContext context) =>
      FutureBuilder<DashboardSource<List<dynamic>>>(
    future: iterationFuture,
    builder: (context, iterationSnapshot) =>
        FutureBuilder<DashboardSource<List<dynamic>>>(
      future: candidateFuture,
      builder: (context, candidateSnapshot) =>
          FutureBuilder<DashboardSource<List<dynamic>>>(
        future: deliveryFuture,
        builder: (context, deliverySnapshot) => builder(
          context,
          iterationSnapshot.data ?? DashboardSource.loading(),
          candidateSnapshot.data ?? DashboardSource.loading(),
          deliverySnapshot.data ?? DashboardSource.loading(),
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
  late Future<DashboardSource<List<dynamic>>> iterationData;
  late Future<DashboardSource<List<dynamic>>> candidateData;
  late Future<DashboardSource<List<dynamic>>> deliveryData;
  late final DashboardApi api;
  Timer? refreshTimer;
  bool managementView = false;

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

  // Blokvorm (i.p.v. `=>`) is bewust: een expressie-body geeft de Future van Future.wait terug
  // aan de aanroeper, en setState() gooit dan 'setState() callback argument returned a Future'
  // zodra _reload via setState(_reload) wordt doorgegeven (bv. in _changeStatus).
  void _reload() {
    iterationData = _captureSource(api.shadowIterations());
    candidateData = _captureSource(api.stories());
    deliveryData = _captureSource(api.deliveries());
    data = Future.wait<dynamic>([
      api.products(),
      api.publications(),
      api.humanActions(),
      api.aiCatalog(),
      api.meetings(),
      api.roadmapEpics(),
      api.roadmapSettledQuestions(),
      api.roadmapSessions(),
    ]);
  }

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
    // traversalEdgeBehavior.closedLoop dwingt de tab-focus-trap af: zonder deze parameter
    // laat het framework (routes.dart _ModalScope) Tab bij de laatste/eerste focusbare widget
    // standaard de dialoogscope verlaten (parentScope/leaveFlutterView).
    final settings = await showDialog<Map<String, dynamic>>(
      context: context,
      traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
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
      traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
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

  Future<void> _startRoadmapSession(String slug) async {
    try {
      await api.startRoadmapSession(slug);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Roadmap-sessie voor $slug is gestart.')),
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
    // showDialog rondt zijn Future af zodra de route popt, terwijl de sluitanimatie de TextField
    // nog één frame kan renderen. Stel disposal uit tot die widget uit de boom is.
    WidgetsBinding.instance.addPostFrameCallback((_) => controller.dispose());
    if (result == null || result.trim().isEmpty) return;
    await api.completeHumanAction(action['id'] as int, result.trim());
    if (mounted) setState(_reload);
  }

  Widget _managementBody() => _OverviewResultsBuilder(
    iterationFuture: iterationData,
    candidateFuture: candidateData,
    deliveryFuture: deliveryData,
    builder: (context, _, candidateSource, deliverySource) {
      final stories = sortedByNewestFirst(
        candidateSource.value ?? const <dynamic>[],
        ['createdAt'],
      );
      final deliveries = deliverySource.value ?? const <dynamic>[];
      final sortedDeliveries = sortedByNewestFirst(deliveries, ['createdAt']);

      return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: DashboardNavigationLink(
              label: 'Terug naar overzicht',
              onPressed: () => setState(() => managementView = false),
            ),
          ),
          const SizedBox(height: 12),
          Text('Beheer', style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 24),
          Text(
            'Software Factory-stories',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          if (deliverySource.loading)
            const SourceNotice(
              icon: Icons.hourglass_top,
              text: 'Software Factory-leveringen worden geladen.',
            )
          else if (deliverySource.failed)
            const SourceNotice(
              icon: Icons.error_outline,
              text: 'Software Factory-leveringen zijn niet beschikbaar.',
              error: true,
            )
          else if (deliveries.isEmpty)
            const ListTile(
              leading: Icon(Icons.hourglass_empty),
              title: Text('Nog geen stories naar de Software Factory gestuurd'),
            ),
          if (deliverySource.loaded)
            _limitedSection('deliveries', sortedDeliveries, (delivery) {
              return SoftwareFactoryDeliveryTile(delivery: delivery);
            }),
          const SizedBox(height: 24),
          Text('Storywachtrij', style: Theme.of(context).textTheme.titleLarge),
          if (candidateSource.loading)
            const SourceNotice(
              icon: Icons.hourglass_top,
              text: 'Storykandidaten voor de storywachtrij worden geladen.',
            )
          else if (candidateSource.failed)
            const SourceNotice(
              icon: Icons.error_outline,
              text:
                  'Storykandidaten voor de storywachtrij zijn niet beschikbaar.',
              error: true,
            )
          else if (deliverySource.loading)
            SourceNotice(
              icon: Icons.hourglass_top,
              text:
                  '${stories.length} storykandidaten geladen. Storywachtrij is onvolledig zolang Software Factory-leveringen worden geladen.',
            )
          else if (deliverySource.failed)
            SourceNotice(
              icon: Icons.error_outline,
              text:
                  '${stories.length} storykandidaten geladen. Storywachtrij is onvolledig omdat Software Factory-leveringen niet beschikbaar zijn.',
              error: true,
            )
          else
            ..._buildStoryQueueSections(
              context,
              stories,
              deliveries,
              visibleCount: _visibleCount,
              onShowMore: _showMore,
            ),
        ],
      );
    },
  );

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
    body: managementView
        ? _managementBody()
        : FutureBuilder<List<dynamic>>(
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
        // Workspace-publicaties hebben geen tijdstempel in de contracts; daar geldt alleen de beperking.
        final publications = (snapshot.data![1] as List<dynamic>)
            .cast<Map<String, dynamic>>();
        final humanActions = sortedByNewestFirst(
          snapshot.data![2] as List<dynamic>,
          ['createdAt'],
        );
        final aiCatalog = snapshot.data![3] as Map<String, dynamic>;
        final meetings = sortedByNewestFirst(
          snapshot.data![4] as List<dynamic>,
          ['closedAt', 'createdAt'],
        );
        final roadmapEpics = (snapshot.data![5] as List<dynamic>)
            .cast<Map<String, dynamic>>();
        final settledQuestions = sortedByNewestFirst(
          snapshot.data![6] as List<dynamic>,
          ['createdAt'],
        );
        final roadmapSessions = sortedByNewestFirst(
          snapshot.data![7] as List<dynamic>,
          ['completedAt', 'createdAt'],
        );
        return _OverviewResultsBuilder(
          iterationFuture: iterationData,
          candidateFuture: candidateData,
          deliveryFuture: deliveryData,
          builder: (context, iterationSource, candidateSource, deliverySource) {
            final iterations = sortedByNewestFirst(
              iterationSource.value ?? const <dynamic>[],
              ['startedAt', 'createdAt'],
            );
            final stories = candidateSource.value ?? const <dynamic>[];
            final deliveries = deliverySource.value ?? const <dynamic>[];
            final grouping = iterationSource.loaded
                ? groupIterationResults(
                    iterations: iterations,
                    candidates: candidateSource.value ?? const <dynamic>[],
                    deliveries: deliverySource.value ?? const <dynamic>[],
                  )
                : null;
            return ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Productoverzicht',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 8),
                Align(
                  alignment: Alignment.centerRight,
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      DashboardNavigationLink(
                        label: 'Beheer',
                        onPressed: () =>
                            setState(() => managementView = true),
                      ),
                      FilledButton.icon(
                        onPressed: () => _addProduct(aiCatalog),
                        icon: const Icon(Icons.add),
                        label: const Text('Product toevoegen'),
                      ),
                      IconButton(
                        onPressed: () => setState(_reload),
                        tooltip: 'Vernieuwen',
                        icon: const Icon(Icons.refresh),
                      ),
                    ],
                  ),
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
                  value: _sourceCount(candidateSource, stories.length),
                  icon: Icons.lightbulb_outline,
                ),
                MetricCard(
                  label: 'Workspace-publicaties',
                  value: '${publications.length}',
                  icon: Icons.folder_open,
                ),
                MetricCard(
                  label: 'Shadow-iteraties',
                  value: _sourceCount(iterationSource, iterations.length),
                  icon: Icons.science_outlined,
                ),
                MetricCard(
                  label: 'Software Factory-stories',
                  value: _sourceCount(deliverySource, deliveries.length),
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
                                avatar: const Icon(
                                  Icons.forum_outlined,
                                  size: 18,
                                ),
                                label: const Text('Overleg gevraagd'),
                                onPressed: () => _startMeeting(slug),
                              ),
                            ),
                          ],
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
                      Row(
                        children: [
                          StartCycleButton(
                            onPressed:
                                active &&
                                    product['workspaceOwnership'] ==
                                        'product-factory'
                                ? () => _startCycle(slug)
                                : null,
                          ),
                        ],
                      ),
                      // Extra ruimte + verlaagde visuele dichtheid van de secundaire knoppen houdt
                      // de CTA hierboven visueel dominant zonder de kaart per saldo hoger te maken.
                      const SizedBox(height: 12),
                      Theme(
                        data: Theme.of(
                          context,
                        ).copyWith(visualDensity: VisualDensity.compact),
                        child: Align(
                          alignment: Alignment.centerRight,
                          child: Wrap(
                            spacing: 8,
                            children: [
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
                              SettingsButton(
                                onPressed: () =>
                                    _editProductSettings(product, aiCatalog),
                              ),
                              OutlinedButton.icon(
                                onPressed: active
                                    ? () => _startMeeting(slug)
                                    : null,
                                icon: const Icon(Icons.forum_outlined),
                                label: const Text('Start overleg'),
                              ),
                              OutlinedButton.icon(
                                onPressed: active
                                    ? () => _startRoadmapSession(slug)
                                    : null,
                                icon: const Icon(Icons.map_outlined),
                                label: const Text('Start roadmap-sessie nu'),
                              ),
                            ],
                          ),
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
            if (iterationSource.loading)
              const SourceNotice(
                icon: Icons.hourglass_top,
                text: 'Productcycli uit geladen gegevens worden geladen.',
              )
            else if (iterationSource.failed)
              const SourceNotice(
                icon: Icons.error_outline,
                text: 'Productcycli uit geladen gegevens zijn niet beschikbaar.',
                error: true,
              )
            else if (iterations.isEmpty)
              const ListTile(
                leading: Icon(Icons.hourglass_empty),
                title: Text('Nog geen productcycli of onderzoekssessies'),
              ),
            if (iterationSource.loaded)
              _limitedSection('iterations', iterations, (iteration) {
                final linked = grouping!.resultsFor(iteration);
                if (shouldShowIterationEvidence(iteration)) {
                  return IterationEvidenceRow(
                    key: ValueKey(
                      _iterationCardIdentity(iterations, iteration),
                    ),
                    iteration: iteration,
                    deliveries: deliverySource.loaded
                        ? linked.deliveries
                        : null,
                    deliveriesLoading: deliverySource.loading,
                    onOpenDetails: () => _showIteration(iteration),
                  );
                }
                return IterationCycleCard(
                  key: ValueKey(
                    _iterationCardIdentity(iterations, iteration),
                  ),
                  iteration: iteration,
                  candidates: candidateSource.loaded
                      ? linked.candidates
                      : null,
                  deliveries: deliverySource.loaded
                      ? linked.deliveries
                      : null,
                  candidatesLoading: candidateSource.loading,
                  deliveriesLoading: deliverySource.loading,
                  onOpenDetails: () => _showIteration(iteration),
                );
              }),
            if (iterationSource.loaded &&
                candidateSource.loaded &&
                deliverySource.loaded &&
                grouping!.unlinkedCount > 0)
              SourceNotice(
                key: const ValueKey('unlinked-iteration-results'),
                icon: Icons.link_off,
                text:
                    'Niet aan een cyclus te koppelen in geladen gegevens: ${grouping.unlinkedCount}',
                error: true,
              )
            else if (iterationSource.loaded &&
                (candidateSource.failed || deliverySource.failed))
              const SourceNotice(
                icon: Icons.info_outline,
                text:
                    'Niet-koppelbare opbrengst is onvolledig doordat niet alle opbrengstbronnen beschikbaar zijn.',
                error: true,
              )
            else if (iterationSource.loaded &&
                (candidateSource.loading || deliverySource.loading))
              const SourceNotice(
                icon: Icons.hourglass_top,
                text:
                    'Niet-koppelbare opbrengst wordt berekend zodra alle opbrengstbronnen geladen zijn.',
              ),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Epic-roadmap',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                const Tooltip(
                  message:
                      'De score combineert klant-rank (75%) en process-rank (25%). Dependencies bepalen de uitvoerbare volgorde.',
                  child: Icon(Icons.info_outline),
                ),
              ],
            ),
            if (candidateSource.loaded && deliverySource.loaded)
              RoadmapBoard(
                products: products,
                epics: roadmapEpics,
                stories: stories,
                deliveries: deliveries,
                api: api,
                onChanged: () => setState(_reload),
              )
            else
              const SourceNotice(
                icon: Icons.info_outline,
                text:
                    'Epic-roadmap is onvolledig totdat kandidaten en leveringen beschikbaar zijn.',
              ),
            if (settledQuestions.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                'Afgehandelde onderzoeksvragen',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              _limitedSection('roadmapSettledQuestions', settledQuestions, (
                question,
              ) {
                return ListTile(
                  dense: true,
                  leading: const Icon(Icons.check, size: 18),
                  title: Text(
                    '${question['productSlug']} · ${question['content']}',
                  ),
                );
              }),
            ],
            const SizedBox(height: 24),
            Text(
              'Roadmap-sessies',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            if (roadmapSessions.isEmpty)
              const ListTile(
                leading: Icon(Icons.hourglass_empty),
                title: Text('Nog geen roadmap-sessies'),
              ),
            _limitedSection('roadmapSessions', roadmapSessions, (session) {
              final sessionStatus = '${session['status']}';
              final summary = session['summary'] as String?;
              final workspaceRunId = session['workspaceRunId'] as String?;
              return Card(
                child: ListTile(
                  leading: const Icon(Icons.map_outlined),
                  title: Text(
                    '${session['productSlug']} · roadmap-sessie ${session['sequenceNumber']}',
                  ),
                  subtitle: Text(
                    [
                      sessionStatus,
                      if (summary != null && summary.isNotEmpty)
                        summary.length > 150
                            ? '${summary.substring(0, 150)}…'
                            : summary,
                    ].join(' · '),
                  ),
                  trailing: workspaceRunId == null
                      ? null
                      : IconButton(
                          tooltip: 'Verslag bekijken',
                          icon: const Icon(Icons.description_outlined),
                          onPressed: () => _showMeetingMinutes(
                            '${session['productSlug']}',
                            workspaceRunId,
                          ),
                        ),
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
        );
      },
    ),
  );
}

/// Interne dashboardnavigatie met expliciete linksemantiek en een zichtbare
/// focusrand, zonder een nieuwe route of browser-URL te introduceren.
class DashboardNavigationLink extends StatefulWidget {
  const DashboardNavigationLink({
    required this.label,
    required this.onPressed,
    super.key,
  });

  final String label;
  final VoidCallback onPressed;

  @override
  State<DashboardNavigationLink> createState() =>
      _DashboardNavigationLinkState();
}

class _DashboardNavigationLinkState extends State<DashboardNavigationLink> {
  final FocusNode _focusNode = FocusNode();

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Semantics(
    // Houd webfocus en Flutter-focus op dezelfde node. MergeSemantics rond een
    // interactieve knop kan ze bij een DOM-focusactie kort uit elkaar laten lopen.
    container: true,
    excludeSemantics: true,
    label: widget.label,
    link: true,
    focusable: true,
    focused: _focusNode.hasFocus,
    onFocus: _focusNode.requestFocus,
    onTap: widget.onPressed,
    child: TextButton(
      focusNode: _focusNode,
      onFocusChange: (_) => setState(() {}),
      onPressed: widget.onPressed,
      style: ButtonStyle(
        textStyle: const WidgetStatePropertyAll(
          TextStyle(decoration: TextDecoration.underline),
        ),
        side: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.focused)) {
            return BorderSide(
              color: Theme.of(context).colorScheme.primary,
              width: 3,
            );
          }
          return BorderSide.none;
        }),
      ),
      child: Text(widget.label),
    ),
  );
}

/// Verliesvrije, niet-interactieve leveringsrij die ook bij lange teksten en
/// sterke tekstvergroting verticaal kan meegroeien.
class SoftwareFactoryDeliveryTile extends StatelessWidget {
  const SoftwareFactoryDeliveryTile({required this.delivery, super.key});

  final Map<String, dynamic> delivery;

  @override
  Widget build(BuildContext context) => MergeSemantics(
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: 2),
            child: Icon(Icons.precision_manufacturing_outlined),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${delivery['externalStoryKey'] ?? 'wordt verstuurd'} · ${delivery['title']}',
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
                const SizedBox(height: 2),
                Text(
                  '${delivery['productSlug']} · ${delivery['status']} · ${delivery['remotePhase'] ?? 'nog geen fase'}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

String _sourceCount(DashboardSource<List<dynamic>> source, int count) {
  if (source.loading) return 'Laden…';
  if (source.failed) return 'Niet beschikbaar';
  return '$count';
}

/// Bouwt een stabiele widgetidentiteit zonder te veronderstellen dat backend-id's
/// uniek zijn. De duplicaatpositie is alleen onderscheidend wanneer alle drie de
/// geladen cyclusvelden gelijk zijn; bij normale refreshes blijven de keys gelijk.
(Object?, Object?, Object?, int) _iterationCardIdentity(
  List<Map<String, dynamic>> iterations,
  Map<String, dynamic> iteration,
) {
  var duplicateIndex = 0;
  for (final other in iterations) {
    if (identical(other, iteration)) break;
    if (other['productSlug'] == iteration['productSlug'] &&
        other['id'] == iteration['id'] &&
        other['sequenceNumber'] == iteration['sequenceNumber']) {
      duplicateIndex++;
    }
  }
  return (
    iteration['productSlug'],
    iteration['id'],
    iteration['sequenceNumber'],
    duplicateIndex,
  );
}

const Color kCycleCardBackground = Color(0xFFFFFFFF);
const Color kCycleCardText = Color(0xFF202124);
const Color kCycleCardSecondaryText = Color(0xFF4B4F52);
const Color kCycleToggleText = Color(0xFF174C3C);
const Color kCycleToggleFocus = Color(0xFF005A9C);
const Color kCycleErrorBackground = Color(0xFFFFF2F2);
const Color kCycleErrorText = Color(0xFF781D24);

class SourceNotice extends StatelessWidget {
  const SourceNotice({
    required this.icon,
    required this.text,
    this.error = false,
    super.key,
  });

  final IconData icon;
  final String text;
  final bool error;

  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.symmetric(vertical: 6),
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: error ? kCycleErrorBackground : kCycleCardBackground,
      border: Border.all(
        color: error ? kCycleErrorText : kCycleCardSecondaryText,
      ),
      borderRadius: BorderRadius.circular(8),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          icon,
          color: error ? kCycleErrorText : kCycleCardSecondaryText,
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            text,
            style: TextStyle(
              color: error ? kCycleErrorText : kCycleCardSecondaryText,
            ),
          ),
        ),
      ],
    ),
  );
}

/// Niet-uitklapbare bewijsregel voor afgeronde Product Factory-cycli. De vijf
/// gelabelde waarden en de detailactie delen één expliciete semanticsgroep.
class IterationEvidenceRow extends StatelessWidget {
  const IterationEvidenceRow({
    required this.iteration,
    required this.deliveries,
    required this.deliveriesLoading,
    required this.onOpenDetails,
    super.key,
  });

  final Map<String, dynamic> iteration;
  final List<Map<String, dynamic>>? deliveries;
  final bool deliveriesLoading;
  final Future<void> Function() onOpenDetails;

  @override
  Widget build(BuildContext context) {
    final presentation = iterationEvidencePresentation(iteration);
    final productSlug = iteration['productSlug'] is String
        ? iteration['productSlug'] as String
        : kEvidenceUnknown;
    final cycleReference = iteration['sequenceNumber'] ?? iteration['id'];
    final cycleLabel = cycleReference == null
        ? kEvidenceUnknown
        : '$cycleReference';
    late final String linkedYield;
    if (deliveriesLoading) {
      linkedYield = 'laden…';
    } else if (deliveries == null) {
      linkedYield = 'niet beschikbaar';
    } else {
      linkedYield = '${deliveries!.length}';
    }

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: 'Bewijs voor product $productSlug, cyclus $cycleLabel',
      child: Card(
        color: kCycleCardBackground,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final columnCount = constraints.maxWidth >= 900
                  ? 3
                  : constraints.maxWidth >= 560
                  ? 2
                  : 1;
              final fieldWidth =
                  (constraints.maxWidth - (columnCount - 1) * 16) /
                  columnCount;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    '$productSlug · iteratie $cycleLabel',
                    style: const TextStyle(
                      color: kCycleCardText,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 16,
                    runSpacing: 10,
                    children: [
                      SizedBox(
                        width: fieldWidth,
                        child: _EvidenceValue(
                          label: 'Datum',
                          value: presentation.date,
                        ),
                      ),
                      SizedBox(
                        width: fieldWidth,
                        child: _EvidenceValue(
                          label: 'Cyclusuitkomst',
                          value: presentation.outcome,
                        ),
                      ),
                      SizedBox(
                        width: fieldWidth,
                        child: _EvidenceValue(
                          label: 'Reden',
                          value: presentation.reason,
                        ),
                      ),
                      SizedBox(
                        width: fieldWidth,
                        child: _EvidenceValue(
                          label: 'Beslisbron',
                          value: presentation.decisionSource,
                        ),
                      ),
                      SizedBox(
                        width: fieldWidth,
                        child: _EvidenceValue(
                          label: 'Gekoppelde opbrengst',
                          value: linkedYield,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: IterationEvidenceButton(
                      key: ValueKey('iteration-evidence-${iteration['id']}'),
                      productSlug: productSlug,
                      cycleLabel: cycleLabel,
                      onOpenDetails: onOpenDetails,
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _EvidenceValue extends StatelessWidget {
  const _EvidenceValue({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    label: '$label: $value',
    excludeSemantics: true,
    child: Text.rich(
      TextSpan(
        style: const TextStyle(color: kCycleCardText),
        children: [
          TextSpan(
            text: '$label: ',
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
          TextSpan(text: value),
        ],
      ),
    ),
  );
}

/// Native bewijsactie met een eigen focusnode, zodat sluiten via de zichtbare
/// actie én Escape naar exact dezelfde bewijsregel terugkeert.
class IterationEvidenceButton extends StatefulWidget {
  const IterationEvidenceButton({
    required this.productSlug,
    required this.cycleLabel,
    required this.onOpenDetails,
    super.key,
  });

  final String productSlug;
  final String cycleLabel;
  final Future<void> Function() onOpenDetails;

  @override
  State<IterationEvidenceButton> createState() =>
      _IterationEvidenceButtonState();
}

class _IterationEvidenceButtonState extends State<IterationEvidenceButton> {
  final FocusNode _focusNode = FocusNode(debugLabel: 'iteration-evidence');

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  Future<void> _openDetails() async {
    try {
      await widget.onOpenDetails();
    } finally {
      if (mounted) _focusNode.requestFocus();
    }
  }

  @override
  Widget build(BuildContext context) => OutlinedButton(
    focusNode: _focusNode,
    onPressed: _openDetails,
    style: ButtonStyle(
      foregroundColor: const WidgetStatePropertyAll(kCycleToggleText),
      side: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.focused)) {
          return const BorderSide(color: kCycleToggleFocus, width: 3);
        }
        return const BorderSide(color: kCycleToggleText);
      }),
    ),
    child: Semantics(
      label:
          'Bekijk bewijs voor product ${widget.productSlug}, cyclus ${widget.cycleLabel}',
      excludeSemantics: true,
      child: const Text('Bekijk bewijs'),
    ),
  );
}

/// Compacte cycluskaart die uitsluitend haar gekoppelde opbrengst uitklapt. De
/// detailbediening blijft een afzonderlijke native button en de kaart zelf is
/// niet interactief, zodat bedieningen nooit in elkaar genest zijn.
class IterationCycleCard extends StatefulWidget {
  const IterationCycleCard({
    required this.iteration,
    required this.candidates,
    required this.deliveries,
    required this.candidatesLoading,
    required this.deliveriesLoading,
    required this.onOpenDetails,
    super.key,
  });

  final Map<String, dynamic> iteration;
  final List<Map<String, dynamic>>? candidates;
  final List<Map<String, dynamic>>? deliveries;
  final bool candidatesLoading;
  final bool deliveriesLoading;
  final Future<void> Function() onOpenDetails;

  @override
  State<IterationCycleCard> createState() => _IterationCycleCardState();
}

class _IterationCycleCardState extends State<IterationCycleCard> {
  final FocusNode _toggleFocusNode = FocusNode(
    debugLabel: 'iteration-results-toggle',
  );
  bool _expanded = false;

  @override
  void dispose() {
    _toggleFocusNode.dispose();
    super.dispose();
  }

  void _toggle() {
    setState(() => _expanded = !_expanded);
    _toggleFocusNode.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    final iteration = widget.iteration;
    final sequenceNumber = '${iteration['sequenceNumber']}';
    final status = '${iteration['status']}';
    final running = status == 'QUEUED' || status == 'RUNNING';
    final role = iteration['currentRole'];
    final timing = iterationTiming(iteration);
    final classification = classifyIterationOutcome(
      status: iteration['status'] as String?,
      criticVerdict: iteration['criticVerdict'] as String?,
      errorMessage: iteration['errorMessage'] as String?,
    );
    final decision = iterationDecisionPresentation(iteration);
    final reason = decision.derived && iteration['outcomeReason'] != null
        ? outcomeReasonLabel('${iteration['outcomeReason']}')
        : null;

    String countLabel({
      required String label,
      required List<Map<String, dynamic>>? records,
      required bool loading,
    }) {
      if (loading) return '$label: laden…';
      if (records == null) return '$label: niet beschikbaar';
      return '$label: ${records.length} · geladen gegevens';
    }

    return Card(
      color: kCycleCardBackground,
      child: DefaultTextStyle.merge(
        style: const TextStyle(color: kCycleCardText),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ListTile(
              isThreeLine: true,
              leading: Icon(
                iteration['mode'] == 'autonomous'
                    ? Icons.auto_awesome
                    : Icons.science_outlined,
                color: kCycleCardSecondaryText,
              ),
              title: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${iteration['productSlug']} · iteratie $sequenceNumber',
                    style: const TextStyle(
                      color: kCycleCardText,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 8,
                    runSpacing: 6,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      if (running)
                        const IterationProgressIndicator()
                      else if (decision.derived)
                        ClassificationBadge(classification: classification),
                      IterationDecisionSourceButton(
                        key: ValueKey(
                          'iteration-decision-source-${iteration['id']}',
                        ),
                        decision: decision,
                        onOpenDetails: widget.onOpenDetails,
                      ),
                    ],
                  ),
                ],
              ),
              subtitle: Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      [
                        'Status: $status',
                        if (role != null) 'bezig: ${_roleLabel('$role')}',
                        'gestart ${timing.startLabel}',
                        if (timing.durationLabel != null)
                          timing.durationLabel!,
                      ].join(' · '),
                      style: const TextStyle(color: kCycleCardSecondaryText),
                    ),
                    if (reason != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        'Kernreden: $reason',
                        style: const TextStyle(color: kCycleCardText),
                      ),
                    ],
                    const SizedBox(height: 4),
                    Text(
                      [
                        countLabel(
                          label: 'Interne kandidaten',
                          records: widget.candidates,
                          loading: widget.candidatesLoading,
                        ),
                        countLabel(
                          label: 'Software Factory-leveringen',
                          records: widget.deliveries,
                          loading: widget.deliveriesLoading,
                        ),
                      ].join('\n'),
                      style: const TextStyle(
                        color: kCycleCardText,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      [
                        'Backendcyclus: ${iteration['candidateCount']} kandidaten',
                        '${iteration['acceptedCandidateCount'] ?? 0} leverbaar',
                        if ((iteration['revisionRounds'] ?? 0) != 0)
                          '${iteration['revisionRounds']} revisierondes',
                        if (iteration['criticVerdict'] != null)
                          'criticus: ${iteration['criticVerdict']}',
                        _deliveryLabel('${iteration['mode']}'),
                      ].join(' · '),
                      style: const TextStyle(color: kCycleCardSecondaryText),
                    ),
                    if (iteration['workspacePullRequestUrl'] != null)
                      const Padding(
                        padding: EdgeInsets.only(top: 4),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.call_merge_outlined,
                              size: 18,
                              color: kCycleCardSecondaryText,
                            ),
                            SizedBox(width: 4),
                            Flexible(child: Text('Pull request beschikbaar')),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: Align(
                alignment: Alignment.centerLeft,
                child: MergeSemantics(
                  child: Semantics(
                    button: true,
                    expanded: _expanded,
                    label:
                        '${_expanded ? 'Verberg' : 'Toon'} opbrengst voor cyclus $sequenceNumber',
                    child: OutlinedButton.icon(
                      key: ValueKey(
                        'iteration-results-toggle-${iteration['id']}',
                      ),
                      focusNode: _toggleFocusNode,
                      onPressed: _toggle,
                      style: ButtonStyle(
                        foregroundColor: const WidgetStatePropertyAll(
                          kCycleToggleText,
                        ),
                        side: WidgetStateProperty.resolveWith((states) {
                          if (states.contains(WidgetState.focused)) {
                            return const BorderSide(
                              color: kCycleToggleFocus,
                              width: 3,
                            );
                          }
                          return const BorderSide(color: kCycleToggleText);
                        }),
                      ),
                      icon: Icon(
                        _expanded ? Icons.expand_less : Icons.expand_more,
                      ),
                      label: Text(
                        _expanded ? 'Verberg opbrengst' : 'Toon opbrengst',
                      ),
                    ),
                  ),
                ),
              ),
            ),
            if (_expanded) ...[
              const Divider(height: 1),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _LinkedResultsGroup(
                      title: 'Interne kandidaten',
                      records: widget.candidates,
                      loading: widget.candidatesLoading,
                      statusLabel: 'Kandidaatstatus',
                    ),
                    const SizedBox(height: 16),
                    _LinkedResultsGroup(
                      title: 'Software Factory-leveringen',
                      records: widget.deliveries,
                      loading: widget.deliveriesLoading,
                      statusLabel: 'Leveringsstatus',
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _LinkedResultsGroup extends StatelessWidget {
  const _LinkedResultsGroup({
    required this.title,
    required this.records,
    required this.loading,
    required this.statusLabel,
  });

  final String title;
  final List<Map<String, dynamic>>? records;
  final bool loading;
  final String statusLabel;

  @override
  Widget build(BuildContext context) {
    final sourceDescription = loading
        ? 'Resultaten uit geladen gegevens worden geladen.'
        : records == null
        ? 'Resultaten uit geladen gegevens zijn niet beschikbaar.'
        : records!.isEmpty
        ? 'Geen resultaten in de geladen gegevens.'
        : 'Resultaten uit de geladen gegevens: ${records!.length}.';
    return Semantics(
      container: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: kCycleCardText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            sourceDescription,
            style: const TextStyle(color: kCycleCardSecondaryText),
          ),
          if (records != null)
            for (final record in records!)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text.rich(
                  TextSpan(
                    style: const TextStyle(color: kCycleCardText),
                    children: [
                      TextSpan(
                        text: '${record['title'] ?? 'Zonder titel'}',
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      TextSpan(
                        text:
                            '\n$statusLabel: ${record['status'] ?? 'Onbekend'}',
                      ),
                    ],
                  ),
                ),
              ),
        ],
      ),
    );
  }
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
  bool _resuming = false;

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

  Future<void> _resumeRevision() async {
    setState(() => _resuming = true);
    try {
      await widget.api.resumeIteration(widget.productSlug, widget.iterationId);
      if (mounted) Navigator.pop(context);
    } catch (error) {
      if (mounted) {
        setState(() => _resuming = false);
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
      final sequenceNumber = iteration?['sequenceNumber'];
      return AlertDialog(
        title: Text(
          sequenceNumber == null
              ? 'Productcyclus ${widget.iterationId}'
              : 'Productcyclus $sequenceNumber',
        ),
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
              final decision = iterationDecisionPresentation(iteration);
              return ListView(
                children: [
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      if (decision.derived)
                        ClassificationBadge(
                          classification: classifyIterationOutcome(
                            status: iteration['status'] as String?,
                            criticVerdict:
                                iteration['criticVerdict'] as String?,
                            errorMessage: iteration['errorMessage'] as String?,
                          ),
                        ),
                      Chip(label: Text(_deliveryLabel('${iteration['mode']}'))),
                      if (currentRole != null)
                        Chip(label: Text('bezig: $currentRole')),
                      Text('gestart ${timing.startLabel}'),
                      if (timing.durationLabel != null)
                        Text(timing.durationLabel!),
                      Chip(
                        label: Text(
                          '${iteration['acceptedCandidateCount'] ?? 0} van ${iteration['candidateCount']} leverbaar',
                        ),
                      ),
                      if ((iteration['revisionRounds'] ?? 0) != 0)
                        Chip(
                          label: Text(
                            '${iteration['revisionRounds']} revisierondes',
                          ),
                        ),
                      Text(decision.sourceText),
                      if (decision.reasonText != null)
                        Text(decision.reasonText!),
                      if (decision.mechanism != null)
                        Text('Mechanisme: ${decision.mechanism}'),
                      if (decision.decidedAt != null)
                        Text(
                          'Beslist op: ${formatDateTime(decision.decidedAt)}',
                        ),
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
                  if (iteration['resumedFromIterationId'] != null) ...[
                    const SizedBox(height: 8),
                    Text(
                      'Hervat vanuit ${iteration['resumedFromIterationId']}',
                    ),
                  ],
                  if (decision.derived &&
                      iteration['outcomeReason'] != null) ...[
                    const SizedBox(height: 16),
                    Text(
                      'Uitkomstreden',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    SelectableText(
                      outcomeReasonLabel('${iteration['outcomeReason']}'),
                    ),
                  ],
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
                  if (status == 'NEEDS_REVISION' || status == 'REJECTED') ...[
                    const SizedBox(height: 16),
                    Builder(
                      builder: (context) {
                        final criticArtifact = latestCriticArtifact(artifacts);
                        final criticVerdict =
                            iteration['criticVerdict'] as String?;
                        final missingCriticContext =
                            status == 'NEEDS_REVISION' &&
                            criticVerdict == null &&
                            criticArtifact == null;
                        final reasonText = criticArtifact != null
                            ? criticReasonSummary(
                                '${criticArtifact['contentJson']}',
                              )
                            : criticVerdict != null
                            ? criticVerdictWithoutArtifactText(criticVerdict)
                            : missingCriticContext
                            ? missingCriticReasonText(steps, artifacts)
                            : '';
                        final baseDisplayText = reasonText.trim().isEmpty
                            ? 'Criticus-oordeel ontbreekt voor deze cyclus'
                            : reasonText;
                        final isGuardrailRejection =
                            status == 'REJECTED' && criticVerdict == 'ACCEPT';
                        const guardrailNote =
                            'Let op: Alle voorgestelde kandidaten zijn geblokkeerd '
                            '(duplicaat of guardrail), waardoor deze cyclus niet '
                            'doorgaat ondanks een positief criticusoordeel.';
                        final displayText = isGuardrailRejection
                            ? '$baseDisplayText\n\n$guardrailNote'
                            : baseDisplayText;
                        return Semantics(
                          label: 'Reden: $displayText',
                          child: ExcludeSemantics(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Reden',
                                  style: Theme.of(
                                    context,
                                  ).textTheme.titleMedium,
                                ),
                                const SizedBox(height: 4),
                                SelectableText(displayText),
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
          if (canResumeIteration(
            status: status,
            outcomeReason: iteration?['outcomeReason'] as String?,
          ))
            FilledButton.icon(
              onPressed: _resuming ? null : _resumeRevision,
              icon: _resuming
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.restart_alt),
              label: Text(
                status == 'NEEDS_REVISION'
                    ? 'Hervat revisie'
                    : 'Herstel levering',
              ),
            ),
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

/// Native detailbutton voor één cyclus. De [FocusNode] leeft even lang als de rijwidget, zodat
/// zowel de zichtbare sluitactie als Escape na het sluiten van de dialoog de focus betrouwbaar
/// terugbrengen naar precies de knop die de dialoog opende.
class IterationDecisionSourceButton extends StatefulWidget {
  const IterationDecisionSourceButton({
    required this.decision,
    required this.onOpenDetails,
    super.key,
  });

  final IterationDecisionPresentation decision;
  final Future<void> Function() onOpenDetails;

  @override
  State<IterationDecisionSourceButton> createState() =>
      _IterationDecisionSourceButtonState();
}

class _IterationDecisionSourceButtonState
    extends State<IterationDecisionSourceButton> {
  final FocusNode _focusNode = FocusNode(
    debugLabel: 'iteration-decision-source',
  );

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  Future<void> _openDetails() async {
    try {
      await widget.onOpenDetails();
    } finally {
      if (mounted) _focusNode.requestFocus();
    }
  }

  @override
  Widget build(BuildContext context) {
    final reasonText = widget.decision.reasonText;
    final accessibleName = [
      widget.decision.sourceText,
      if (reasonText != null) reasonText,
    ].join('. ');
    return OutlinedButton(
      focusNode: _focusNode,
      onPressed: _openDetails,
      child: Semantics(
        label: accessibleName,
        excludeSemantics: true,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.decision.sourceText),
            if (reasonText != null) Text(reasonText),
          ],
        ),
      ),
    );
  }
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
/// Zoekt in [artifacts] naar het criticus-artefact (`artifactType` `critic` of `critic-<n>`)
/// voor de huidige iteratie en geeft de meest recente terug (hoogste retry-suffix; bij een
/// gelijk of hoger suffix wint de laatst voorkomende in de lijst). Geeft `null` als er geen
/// criticus-artefact aanwezig is.
Map<String, dynamic>? latestCriticArtifact(List<dynamic> artifacts) =>
    latestArtifactForRole(artifacts, 'critic');

/// Zoekt in [artifacts] het meest recente artefact (hoogste retry-suffix; bij een gelijk of hoger
/// suffix wint het laatst voorkomende exemplaar in de lijst) voor de gegeven [role] (bv.
/// 'researcher', 'product_owner', 'critic'). Algemene vorm van wat voorheen alleen
/// [latestCriticArtifact] deed. Geeft `null` als er geen artefact voor die rol aanwezig is.
Map<String, dynamic>? latestArtifactForRole(
  List<dynamic> artifacts,
  String role,
) {
  Map<String, dynamic>? latest;
  var latestAttempt = -1;
  for (final item in artifacts) {
    final artifact = item as Map<String, dynamic>;
    final artifactType = '${artifact['artifactType']}';
    final baseRole = artifactType.replaceAll(RegExp(r'-\d+$'), '');
    if (baseRole != role) continue;
    final match = RegExp(r'-(\d+)$').firstMatch(artifactType);
    final attempt = match != null ? int.parse(match.group(1)!) : 1;
    if (attempt >= latestAttempt) {
      latestAttempt = attempt;
      latest = artifact;
    }
  }
  return latest;
}

/// Bepaalt de laatst voltooide (`status == 'COMPLETED'`) stap uit [steps], gesorteerd op
/// `completedAt` (nieuwste eerst); ontbreekt `completedAt`, of zijn er gelijke tijdstippen, dan
/// wint de laatst voorkomende COMPLETED-stap in de lijst — analoog aan de tie-break in
/// [latestArtifactForRole]. Geeft `null` als geen enkele stap COMPLETED is.
Map<String, dynamic>? lastCompletedStep(List<dynamic> steps) {
  Map<String, dynamic>? latest;
  DateTime? latestCompletedAt;
  for (final item in steps) {
    final step = item as Map<String, dynamic>;
    if ('${step['status']}' != 'COMPLETED') continue;
    final completedAtRaw = step['completedAt'];
    final completedAt = completedAtRaw == null
        ? null
        : DateTime.tryParse('$completedAtRaw');
    final isFirstMatch = latest == null;
    final winsTie =
        completedAt == null ||
        latestCompletedAt == null ||
        !completedAt.isBefore(latestCompletedAt);
    if (isFirstMatch || winsTie) {
      latest = step;
      latestCompletedAt = completedAt;
    }
  }
  return latest;
}

/// Bouwt een pure-tekst (geen widgets, geen rauwe JSON) resultaatsamenvatting voor [role] uit
/// [contentJson], voor gebruik in het Reden-blok bij `NEEDS_REVISION` zonder `criticVerdict` en
/// zonder criticus-artefact (product-132). Rollen met een `summary`-veld in hun
/// `ShadowSchemas.kt`-schema (researcher, critic, summary) gebruiken dat veld; de rollen zonder
/// `summary`-veld (product_owner, ux_designer, story_writer) krijgen een samenvatting opgebouwd
/// uit hun belangrijkste velden — dezelfde velden als in [_readableArtifactFields] — met labels
/// via [humanizeFieldKey], nooit rauwe JSON. Geeft een lege string terug als [contentJson] niet
/// parseerbaar is, de rol onbekend is, of geen van de relevante velden bruikbare inhoud bevat.
String roleResultSummaryText(String role, String contentJson) {
  final Map<String, dynamic> data;
  try {
    final decoded = jsonDecode(contentJson);
    if (decoded is! Map) return '';
    data = decoded.cast<String, dynamic>();
  } catch (_) {
    return '';
  }

  String field(String key) => '${data[key] ?? ''}'.trim();
  String bulletLine(String key) {
    final value = data[key];
    if (value is! List) return '';
    final items = value
        .map((entry) => '$entry'.trim())
        .where((entry) => entry.isNotEmpty)
        .toList();
    return items.isEmpty ? '' : '${humanizeFieldKey(key)}: ${items.join('; ')}';
  }

  final lines = <String>[];
  switch (role) {
    case 'researcher':
    case 'critic':
    case 'summary':
      final summary = field('summary');
      if (summary.isNotEmpty) lines.add(summary);
      break;
    case 'product_owner':
      final direction = field('productDirection');
      if (direction.isNotEmpty) lines.add(direction);
      final rationale = field('rationale');
      if (rationale.isNotEmpty) lines.add(rationale);
      final priorities = bulletLine('priorities');
      if (priorities.isNotEmpty) lines.add(priorities);
      break;
    case 'ux_designer':
      final flow = field('flowName');
      if (flow.isNotEmpty) lines.add(flow);
      final goal = field('userGoal');
      if (goal.isNotEmpty) lines.add(goal);
      final steps = bulletLine('steps');
      if (steps.isNotEmpty) lines.add(steps);
      break;
    case 'story_writer':
      final candidates = data['candidates'];
      if (candidates is List) {
        final titles = candidates
            .whereType<Map>()
            .map((item) => '${item['title'] ?? ''}'.trim())
            .where((title) => title.isNotEmpty)
            .toList();
        if (titles.isNotEmpty) {
          lines.add('${humanizeFieldKey('candidates')}: ${titles.join('; ')}');
        }
      }
      break;
    default:
      break;
  }
  return lines.join('\n');
}

/// Reden-tekst voor de deelcasus `NEEDS_REVISION` zonder `criticVerdict` en zonder
/// criticus-artefact (product-132): toont de laatst voltooide rol (via [_roleLabel]) plus een
/// leesbare resultaatsamenvatting ([roleResultSummaryText]), of — als geen enkele rol `COMPLETED`
/// is — een aparte, expliciete fallbacktekst die dat meldt.
///
/// Onderzoek naar bewuste stop vs. timeout/technische fout (product-132): `steps`/`artifacts`
/// geven hiervoor GEEN betrouwbaar onderscheid. Een rol die nooit gestart is, levert domweg geen
/// step-record op — er bestaat geen expliciet 'overgeslagen'/'timeout'-statuswaarde of -veld die
/// het stoppen van de pipeline na de laatste COMPLETED-stap verklaart. `errorMessage` staat alleen
/// op stappen die zelf gestart en gefaald zijn, en zulke fouten leiden normaliter tot status
/// `FAILED` (met het bestaande Foutreden-blok), niet tot deze `NEEDS_REVISION`-zonder-
/// `criticVerdict`-casus. Zonder een veld dat de daadwerkelijke stopoorzaak vastlegt, blijft de
/// tekst hieronder daarom beperkt tot rolnaam + resultaatsamenvatting, zonder gegokte oorzaak.
String missingCriticReasonText(List<dynamic> steps, List<dynamic> artifacts) {
  final lastStep = lastCompletedStep(steps);
  if (lastStep == null) {
    return 'Geen enkele agentrol is voltooid voor deze cyclus; er is geen '
        'resultaat om te tonen.';
  }
  final role = '${lastStep['role']}';
  final artifact = latestArtifactForRole(artifacts, role);
  final contentJson = artifact == null ? '' : '${artifact['contentJson']}';
  final summaryText = roleResultSummaryText(role, contentJson);
  final summaryLine = summaryText.isEmpty
      ? 'Geen leesbare samenvatting beschikbaar voor deze rol.'
      : summaryText;
  return 'Laatst voltooide rol: ${_roleLabel(role)}\n\n$summaryLine';
}

/// Reden-tekst voor de deelcasus waarbij `iteration['criticVerdict']` wél gezet is, maar er geen
/// onderliggend criticus-artefact (meer) beschikbaar is (product-144). Benoemt de letterlijke
/// verdict-waarde expliciet, zodat deze tekst niet in tegenspraak is met een elders in de UI
/// getoonde criticus-badge die uitsluitend op `criticVerdict != null` is gebaseerd.
String criticVerdictWithoutArtifactText(String criticVerdict) =>
    'Criticusoordeel $criticVerdict geregistreerd, maar geen onderliggend '
    'criticus-artefact beschikbaar.';

/// Bouwt leesbare, lopende tekst (géén rauwe JSON) uit een criticus-artefact's `contentJson`,
/// volgens het schema uit `ShadowSchemas.kt` (`overallVerdict`, `summary`, `requiredChanges[]`).
/// Geeft een lege string terug als het artefact niet parseerbaar is of geen van deze velden
/// bruikbare inhoud bevat.
String criticReasonSummary(String contentJson) {
  final Map<String, dynamic> data;
  try {
    final decoded = jsonDecode(contentJson);
    if (decoded is! Map) return '';
    data = decoded.cast<String, dynamic>();
  } catch (_) {
    return '';
  }

  final lines = <String>[];
  final verdict = '${data['overallVerdict'] ?? ''}'.trim();
  if (verdict.isNotEmpty) lines.add('Eindoordeel: $verdict');
  final summary = '${data['summary'] ?? ''}'.trim();
  if (summary.isNotEmpty) lines.add(summary);
  final requiredChanges = data['requiredChanges'];
  if (requiredChanges is List) {
    for (final entry in requiredChanges) {
      final text = '$entry'.trim();
      if (text.isNotEmpty) lines.add('• $text');
    }
  }
  return lines.join('\n');
}

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
  final fieldEntries = _roleSpecificFieldEntries(context, baseRole, data);
  if (fieldEntries.isEmpty) {
    return _readableGenericFields(context, data);
  }

  final widgets = <Widget>[];
  final handledKeys = <String>{};
  for (final entry in fieldEntries) {
    handledKeys.add(entry.key);
    if (entry.value.isNotEmpty) {
      widgets.addAll(entry.value);
    } else if (data.containsKey(entry.key)) {
      widgets.addAll(
        _readableGenericFieldEntry(context, entry.key, data[entry.key]),
      );
    }
  }
  for (final entry in data.entries) {
    if (handledKeys.contains(entry.key)) continue;
    widgets.addAll(_readableGenericFieldEntry(context, entry.key, entry.value));
  }
  return widgets;
}

/// Rolspecifieke top-level velden voor een bekende [baseRole], elk als een key/widgets-paar zodat
/// [_readableArtifactFields] per veld kan beoordelen of de rolspecifieke branch iets opleverde en,
/// zo niet, de generieke fallback ([_readableGenericFieldEntry]) voor precies dat veld kan
/// toepassen (product-138). Geeft een lege lijst terug voor een onbekende rol, waarna de aanroeper
/// volledig op [_readableGenericFields] terugvalt (ongewijzigd gedrag van vóór deze wijziging).
List<MapEntry<String, List<Widget>>> _roleSpecificFieldEntries(
  BuildContext context,
  String baseRole,
  Map<String, dynamic> data,
) {
  switch (baseRole) {
    case 'researcher':
      return [
        MapEntry(
          'summary',
          _readableText(context, 'Samenvatting', data['summary']),
        ),
        MapEntry(
          'findings',
          _readableObjectList(
            context,
            'Bevindingen',
            data['findings'],
            (context, item) => [
              ..._readableSelectableText(item['title'], bold: true),
              ..._readableSelectableText(item['finding']),
              ..._bulletLines(item['sourceUrls']),
            ],
          ),
        ),
        MapEntry(
          'currentState',
          _readableObject(
            context,
            'Huidige situatie',
            data['currentState'],
            (context, item) => [
              ..._readableSelectableText(item['purpose']),
              ..._bulletLines(item['gaps']),
            ],
          ),
        ),
        MapEntry(
          'improvementOpportunities',
          _readableBulletList(
            context,
            'Verbetermogelijkheden',
            data['improvementOpportunities'],
          ),
        ),
        MapEntry(
          'sources',
          _readableObjectList(
            context,
            'Bronnen',
            data['sources'],
            (context, item) => [
              ..._readableSelectableText(item['url'], bold: true),
              ..._readableSelectableText(item['rationale']),
            ],
          ),
        ),
        MapEntry(
          'inspiration',
          _readableObjectList(
            context,
            'Inspiratie',
            data['inspiration'],
            (context, item) => [
              ..._readableSelectableText(item['name'], bold: true),
              ..._readableSelectableText(item['url']),
              ..._readableSelectableText(item['relevance']),
            ],
          ),
        ),
      ];
    case 'product_owner':
      return [
        MapEntry(
          'productDirection',
          _readableText(context, 'Productrichting', data['productDirection']),
        ),
        MapEntry(
          'rationale',
          _readableText(context, 'Onderbouwing', data['rationale']),
        ),
        MapEntry(
          'priorities',
          _readableBulletList(context, 'Prioriteiten', data['priorities']),
        ),
        MapEntry(
          'decisions',
          _readableObjectList(
            context,
            'Besluiten',
            data['decisions'],
            (context, item) => [
              ..._readableSelectableText(item['decision'], bold: true),
              ..._readableSelectableText(item['rationale']),
              ..._bulletLines(item['sourceUrls']),
            ],
          ),
        ),
        MapEntry(
          'rejectedOptions',
          _readableBulletList(
            context,
            'Afgewezen opties',
            data['rejectedOptions'],
          ),
        ),
      ];
    case 'ux_designer':
      return [
        MapEntry('flowName', _readableText(context, 'Flow', data['flowName'])),
        MapEntry(
          'userGoal',
          _readableText(context, 'Gebruikersdoel', data['userGoal']),
        ),
        MapEntry(
          'steps',
          _readableBulletList(context, 'Stappen', data['steps']),
        ),
        MapEntry(
          'wireframe',
          _readableText(context, 'Wireframe', data['wireframe']),
        ),
        MapEntry(
          'hypotheses',
          _readableBulletList(context, 'Hypotheses', data['hypotheses']),
        ),
        MapEntry(
          'accessibility',
          _readableBulletList(
            context,
            'Toegankelijkheid',
            data['accessibility'],
          ),
        ),
        MapEntry(
          'privacyConsiderations',
          _readableBulletList(
            context,
            'Privacyoverwegingen',
            data['privacyConsiderations'],
          ),
        ),
      ];
    case 'story_writer':
      return [
        MapEntry(
          'candidates',
          _readableObjectList(
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
        ),
      ];
    case 'critic':
      return [
        MapEntry(
          'overallVerdict',
          _readableText(context, 'Eindoordeel', data['overallVerdict']),
        ),
        MapEntry(
          'summary',
          _readableText(context, 'Samenvatting', data['summary']),
        ),
        MapEntry(
          'issues',
          _readableObjectList(
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
        ),
        MapEntry(
          'candidateReviews',
          _readableObjectList(
            context,
            'Beoordeling per kandidaat',
            data['candidateReviews'],
            (context, item) => [
              ..._readableSelectableText(item['verdict'], bold: true),
              ..._readableSelectableText(item['reason']),
            ],
          ),
        ),
        MapEntry(
          'requiredChanges',
          _readableBulletList(
            context,
            'Vereiste wijzigingen',
            data['requiredChanges'],
          ),
        ),
      ];
    default:
      return const [];
  }
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
    widgets.addAll(_readableGenericFieldEntry(context, entry.key, entry.value));
  }
  return widgets;
}

/// Genereert leesbare widgets voor één top-level veld ([key]/[value]), gebruikt zowel door
/// [_readableGenericFields] (onbekende rol, alle velden) als door [_readableArtifactFields]
/// (bekende rol, alleen velden waarvoor de rolspecifieke branch niets opleverde — product-138).
/// Levert alleen iets op voor een string-waarde of een lijst van uitsluitend primitieve waarden;
/// geneste objecten/arrays-van-objecten leveren bewust niets op (blijven buiten scope).
List<Widget> _readableGenericFieldEntry(
  BuildContext context,
  String key,
  dynamic value,
) {
  if (value is String) {
    return _readableText(context, humanizeFieldKey(key), value);
  } else if (value is List &&
      value.every((item) => item is String || item is num || item is bool)) {
    return _readableBulletList(context, humanizeFieldKey(key), value);
  }
  return const [];
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
  List<Map<String, String>> roadmapSchedule = [];

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
      'roadmapSchedule': roadmapSchedule,
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
            const SizedBox(height: 16),
            WeeklyRoadmapScheduleField(
              schedule: roadmapSchedule,
              onChanged: (value) => setState(() => roadmapSchedule = value),
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

/// Achtergrond-/tekstkleur van [StartCycleButton]. Losstaand van het thema-kleurenschema (dat via
/// `seedColor` kan wijzigen) zodat de AA-contrastverhouding (>=4.5:1, zie
/// `test/start_cycle_button_test.dart`) niet afhankelijk is van toekomstige themawijzigingen —
/// zelfde aanpak als `kClassificationColors` in `classification.dart`.
const Color kStartCycleButtonBackground = Color(0xFF1B4332);
const Color kStartCycleButtonForeground = Colors.white;

/// Primaire CTA 'Start productcyclus nu' op de productkaart: een eigen, losstaand knop-widget met
/// een zichtbare rand (onderscheid door rand, niet uitsluitend kleur, t.o.v. de secundaire
/// `OutlinedButton`-knoppen eronder) en een expliciete `FocusNode` voor een zichtbare focusring,
/// naar analogie van het bestaande [SettingsButton]-patroon. `onPressed`/icoon/label komen
/// ongewijzigd uit de aanroepende `_OverviewPageState`.
class StartCycleButton extends StatefulWidget {
  const StartCycleButton({required this.onPressed, super.key});
  final VoidCallback? onPressed;

  @override
  State<StartCycleButton> createState() => _StartCycleButtonState();
}

class _StartCycleButtonState extends State<StartCycleButton> {
  final FocusNode _focusNode = FocusNode(
    debugLabel: 'Start-productcyclus-knop',
  );

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => FilledButton.icon(
    focusNode: _focusNode,
    onPressed: widget.onPressed,
    style: FilledButton.styleFrom(
      backgroundColor: kStartCycleButtonBackground,
      foregroundColor: kStartCycleButtonForeground,
      side: const BorderSide(color: kStartCycleButtonForeground, width: 2),
      textStyle: const TextStyle(fontWeight: FontWeight.bold),
    ),
    icon: const Icon(Icons.auto_awesome),
    label: const Text('Start productcyclus nu'),
  );
}

/// Instellingen-knop op de productkaart die zijn focus expliciet bewaart: na het sluiten van
/// [ProductSettingsDialog] (via Opslaan, Annuleren, klik op de barrier, of Escape) krijgt de knop
/// zelf de focus terug, ongeacht of hij die vóór het openen al had (bv. bij muisklik).
class SettingsButton extends StatefulWidget {
  const SettingsButton({required this.onPressed, super.key});
  final Future<void> Function() onPressed;

  @override
  State<SettingsButton> createState() => _SettingsButtonState();
}

class _SettingsButtonState extends State<SettingsButton> {
  final FocusNode _focusNode = FocusNode(debugLabel: 'Instellingen-knop');

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  Future<void> _handlePressed() async {
    await widget.onPressed();
    if (mounted) _focusNode.requestFocus();
  }

  @override
  Widget build(BuildContext context) => OutlinedButton.icon(
    focusNode: _focusNode,
    onPressed: _handlePressed,
    icon: const Icon(Icons.tune),
    label: const Text('Instellingen'),
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
  late final targetRepositoryName = TextEditingController(
    text: '${widget.product['targetRepositoryName']}',
  );
  late String aiProvider = '${widget.product['aiProvider']}';
  late String aiModel = '${widget.product['aiModel']}';
  late List<String> iterationTimes = List<String>.from(
    (widget.product['iterationTimes'] as List<dynamic>?) ?? const ['03:00'],
  );
  late List<Map<String, String>> roadmapSchedule =
      ((widget.product['roadmapSchedule'] as List<dynamic>?) ?? const [])
          .map(
            (entry) => Map<String, String>.from(
              Map<String, dynamic>.from(entry as Map),
            ),
          )
          .toList();

  @override
  void dispose() {
    maxStoriesPerCycle.dispose();
    wipLimit.dispose();
    targetRepositoryName.dispose();
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
      'roadmapSchedule': roadmapSchedule,
      'targetRepositoryName': targetRepositoryName.text.trim(),
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
            Text(
              'Software Factory-koppeling',
              style: Theme.of(context).textTheme.titleSmall,
            ),
            const SizedBox(height: 4),
            TextFormField(
              readOnly: true,
              initialValue: '${widget.product['mission']}',
              maxLines: null,
              decoration: const InputDecoration(
                labelText: 'Missie',
                helperText:
                    'Gekoppeld aan de Software Factory-integratie; hier niet bewerkbaar.',
              ),
            ),
            TextFormField(
              readOnly: true,
              initialValue: '${widget.product['softwareFactoryProjectKey']}',
              decoration: const InputDecoration(
                labelText: 'Software Factory-project',
                helperText:
                    'Gekoppeld aan de Software Factory-integratie; hier niet bewerkbaar.',
              ),
            ),
            TextFormField(
              readOnly: true,
              initialValue: '${widget.product['workspaceOwnership']}',
              decoration: const InputDecoration(
                labelText: 'Workspace',
                helperText:
                    'Gekoppeld aan de Software Factory-integratie; hier niet bewerkbaar.',
              ),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              initialValue: developmentMode,
              autofocus: true,
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
              controller: targetRepositoryName,
              decoration: const InputDecoration(
                labelText: 'Doelrepository',
                helperText:
                    'De Git-repository waarnaar de Software Factory wijzigingen publiceert.',
              ),
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
            const SizedBox(height: 16),
            WeeklyRoadmapScheduleField(
              schedule: roadmapSchedule,
              onChanged: (value) => setState(() => roadmapSchedule = value),
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

/// Optionele weekplanning voor automatische roadmap-sessies. Anders dan productcycli zijn dit
/// combinaties van weekdag en tijd; een lege lijst betekent bewust alleen handmatig starten.
class WeeklyRoadmapScheduleField extends StatefulWidget {
  const WeeklyRoadmapScheduleField({
    required this.schedule,
    required this.onChanged,
    super.key,
  });

  final List<Map<String, String>> schedule;
  final ValueChanged<List<Map<String, String>>> onChanged;

  @override
  State<WeeklyRoadmapScheduleField> createState() =>
      _WeeklyRoadmapScheduleFieldState();
}

class _WeeklyRoadmapScheduleFieldState
    extends State<WeeklyRoadmapScheduleField> {
  String selectedDay = 'MONDAY';

  static const days = <String, String>{
    'MONDAY': 'Maandag',
    'TUESDAY': 'Dinsdag',
    'WEDNESDAY': 'Woensdag',
    'THURSDAY': 'Donderdag',
    'FRIDAY': 'Vrijdag',
    'SATURDAY': 'Zaterdag',
    'SUNDAY': 'Zondag',
  };

  Future<void> _addMoment(BuildContext context) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 10, minute: 0),
    );
    if (picked == null) return;
    final time =
        '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
    final moment = {'dayOfWeek': selectedDay, 'time': time};
    if (widget.schedule.any(
      (entry) => entry['dayOfWeek'] == selectedDay && entry['time'] == time,
    )) {
      return;
    }
    final updated = [...widget.schedule, moment]
      ..sort((a, b) {
        final dayCompare = days.keys
            .toList()
            .indexOf(a['dayOfWeek']!)
            .compareTo(days.keys.toList().indexOf(b['dayOfWeek']!));
        return dayCompare != 0 ? dayCompare : a['time']!.compareTo(b['time']!);
      });
    widget.onChanged(updated);
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('Roadmapplanning', style: Theme.of(context).textTheme.titleSmall),
      const SizedBox(height: 4),
      const Text(
        'Automatische roadmap-sessies per week. Zonder momenten start je ze alleen handmatig.',
      ),
      const SizedBox(height: 8),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          for (final moment in widget.schedule)
            Chip(
              label: Text(
                '${days[moment['dayOfWeek']] ?? moment['dayOfWeek']} ${moment['time']}',
              ),
              onDeleted: () => widget.onChanged(
                widget.schedule.where((entry) => entry != moment).toList(),
              ),
            ),
          SizedBox(
            width: 210,
            child: DropdownButtonFormField<String>(
              key: const Key('roadmap-weekday'),
              initialValue: selectedDay,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Weekdag'),
              items: days.entries
                  .map(
                    (entry) => DropdownMenuItem(
                      value: entry.key,
                      child: Text(entry.value),
                    ),
                  )
                  .toList(),
              onChanged: (value) => setState(() => selectedDay = value!),
            ),
          ),
          ActionChip(
            avatar: const Icon(Icons.add, size: 18),
            label: const Text('Moment toevoegen'),
            onPressed: () => _addMoment(context),
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
