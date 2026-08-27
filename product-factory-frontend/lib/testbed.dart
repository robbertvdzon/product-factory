import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'configuration.dart';
import 'http_client_factory.dart';
import 'page_refresh.dart';

class TestScenarioSummary {
  const TestScenarioSummary({
    required this.key,
    required this.version,
    required this.title,
    required this.description,
  });

  factory TestScenarioSummary.fromJson(Map<String, Object?> json) =>
      TestScenarioSummary(
        key: json['key'] as String? ?? '',
        version: json['version'] as String? ?? '',
        title: json['title'] as String? ?? '',
        description: json['description'] as String? ?? '',
      );

  final String key;
  final String version;
  final String title;
  final String description;
}

class TestScenarioDetails {
  const TestScenarioDetails({
    required this.scenario,
    required this.datasetVersion,
    required this.testbedVersion,
    required this.currentStep,
    this.lockOwner,
  });

  factory TestScenarioDetails.fromJson(Map<String, Object?> json) {
    final scenario = json['scenario'];
    final lock = json['lock'];
    if (scenario is! Map<String, Object?>) throw const TestbedFailure();
    return TestScenarioDetails(
      scenario: TestScenarioSummary.fromJson(scenario),
      datasetVersion: json['datasetVersion'] as String? ?? 'Onbekend',
      testbedVersion: json['testbedVersion'] as String? ?? 'Onbekend',
      currentStep: json['currentStep'] as int? ?? 0,
      lockOwner: lock is Map<String, Object?>
          ? lock['browserSessionId'] as String?
          : null,
    );
  }

  final TestScenarioSummary scenario;
  final String datasetVersion;
  final String testbedVersion;
  final int currentStep;
  final String? lockOwner;
}

class TestbedSnapshot {
  const TestbedSnapshot({required this.active, required this.scenarios});
  final TestScenarioDetails active;
  final List<TestScenarioSummary> scenarios;
}

abstract interface class TestControlGateway {
  Future<TestbedSnapshot> load();
  Future<void> reset(String scenarioKey, String browserSessionId);
  Future<void> activate(String scenarioKey, String browserSessionId);
}

class HttpTestControlGateway implements TestControlGateway {
  HttpTestControlGateway({http.Client? client, String? backendUrl})
    : _client = client ?? createHttpClient(),
      _backendUrl = (backendUrl ?? AppConfiguration.backendUrl).replaceAll(
        RegExp(r'/$'),
        '',
      );

  final http.Client _client;
  final String _backendUrl;

  @override
  Future<TestbedSnapshot> load() async {
    try {
      final responses = await Future.wait([
        _client.get(_uri('/api/test-control/scenario')),
        _client.get(_uri('/api/test-control/scenarios')),
      ]);
      if (responses.any((response) => response.statusCode != 200)) {
        throw const TestbedFailure();
      }
      final active = jsonDecode(utf8.decode(responses[0].bodyBytes));
      final scenarios = jsonDecode(utf8.decode(responses[1].bodyBytes));
      if (active is! Map<String, Object?> || scenarios is! List<Object?>) {
        throw const TestbedFailure();
      }
      return TestbedSnapshot(
        active: TestScenarioDetails.fromJson(active),
        scenarios: scenarios
            .whereType<Map<String, Object?>>()
            .map(TestScenarioSummary.fromJson)
            .toList(growable: false),
      );
    } on TestbedFailure {
      rethrow;
    } on Object {
      throw const TestbedFailure();
    }
  }

  @override
  Future<void> reset(String scenarioKey, String browserSessionId) =>
      _command('/api/test-control/reset', scenarioKey, browserSessionId);

  @override
  Future<void> activate(String scenarioKey, String browserSessionId) =>
      _command('/api/test-control/scenario', scenarioKey, browserSessionId);

  Future<void> _command(
    String path,
    String scenarioKey,
    String browserSessionId,
  ) async {
    try {
      final response = await _client.post(
        _uri(path),
        headers: const {'Content-Type': 'application/json'},
        body: jsonEncode({
          'scenarioKey': scenarioKey,
          'browserSessionId': browserSessionId,
        }),
      );
      if (response.statusCode != 204) {
        String? message;
        try {
          final body = jsonDecode(utf8.decode(response.bodyBytes));
          if (body is Map<String, Object?>) {
            message = body['message'] as String?;
          }
        } on FormatException {
          // De vaste Nederlandstalige foutmelding hieronder blijft de veilige fallback.
        }
        throw TestbedFailure(
          message ?? 'De Testbed-opdracht is niet uitgevoerd.',
        );
      }
    } on TestbedFailure {
      rethrow;
    } on Object {
      throw const TestbedFailure();
    }
  }

  Uri _uri(String path) => Uri.parse('$_backendUrl$path');
}

class TestbedFailure implements Exception {
  const TestbedFailure([
    this.message = 'Product Factory Testbed is tijdelijk niet bereikbaar.',
  ]);
  final String message;
}

class AcceptanceTestsPage extends StatefulWidget {
  const AcceptanceTestsPage({
    required this.gateway,
    this.refreshController,
    super.key,
  });
  final TestControlGateway gateway;
  final PageRefreshController? refreshController;

  @override
  State<AcceptanceTestsPage> createState() => _AcceptanceTestsPageState();
}

class _AcceptanceTestsPageState extends State<AcceptanceTestsPage> {
  late Future<TestbedSnapshot> _snapshot;
  late final String _browserSessionId;
  String? _selectedScenario;
  String? _commandError;
  bool _busy = false;
  bool _refreshing = false;
  String? _fingerprint;

  @override
  void initState() {
    super.initState();
    _browserSessionId = 'browser-${DateTime.now().microsecondsSinceEpoch}';
    widget.refreshController?.addListener(_refreshSilently);
    _snapshot = _loadSnapshot();
  }

  @override
  void didUpdateWidget(covariant AcceptanceTestsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.refreshController != widget.refreshController) {
      oldWidget.refreshController?.removeListener(_refreshSilently);
      widget.refreshController?.addListener(_refreshSilently);
    }
  }

  @override
  void dispose() {
    widget.refreshController?.removeListener(_refreshSilently);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _TestbedPageFrame(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Acceptatietesten',
            style: Theme.of(
              context,
            ).textTheme.displaySmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          const Text(
            'Kies een vast scenario en herstel de synthetische testdata naar een bekende toestand.',
          ),
          const SizedBox(height: 24),
          FutureBuilder<TestbedSnapshot>(
            future: _snapshot,
            builder: (context, snapshot) {
              if (snapshot.connectionState != ConnectionState.done) {
                return const Center(child: CircularProgressIndicator());
              }
              if (snapshot.hasError || snapshot.data == null) {
                return _TestbedError(onRetry: _reload);
              }
              return _controls(snapshot.data!);
            },
          ),
        ],
      ),
    );
  }

  Widget _controls(TestbedSnapshot snapshot) {
    final selected = _selectedScenario ?? snapshot.active.scenario.key;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Actief scenario',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 12),
                Text(snapshot.active.scenario.title),
                const SizedBox(height: 8),
                Text('Dataset: ${snapshot.active.datasetVersion}'),
                Text('Testbed: ${snapshot.active.testbedVersion}'),
                Text('Scenarioversie: ${snapshot.active.scenario.version}'),
                Text('Stap: ${snapshot.active.currentStep}'),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<String>(
          initialValue: selected,
          decoration: const InputDecoration(
            labelText: 'Scenario',
            border: OutlineInputBorder(),
          ),
          items: snapshot.scenarios
              .map(
                (scenario) => DropdownMenuItem(
                  value: scenario.key,
                  child: Text(scenario.title),
                ),
              )
              .toList(growable: false),
          onChanged: _busy
              ? null
              : (value) => setState(() => _selectedScenario = value),
        ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            FilledButton(
              onPressed: _busy ? null : () => _activate(selected),
              child: const Text('Scenario activeren'),
            ),
            OutlinedButton.icon(
              onPressed: _busy ? null : () => _confirmReset(selected),
              icon: const Icon(Icons.restart_alt),
              label: const Text('Omgeving resetten'),
            ),
          ],
        ),
        if (_busy) ...[
          const SizedBox(height: 16),
          const LinearProgressIndicator(),
        ],
        if (_commandError != null) ...[
          const SizedBox(height: 16),
          Text(
            _commandError!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        ],
      ],
    );
  }

  Future<void> _confirmReset(String scenarioKey) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Acceptatieomgeving resetten?'),
        content: const Text(
          'Alle tijdelijke acceptatiewijzigingen verdwijnen. De vaste synthetische data wordt opnieuw geladen.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Resetten'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await _run(() => widget.gateway.reset(scenarioKey, _browserSessionId));
    }
  }

  Future<void> _activate(String scenarioKey) =>
      _run(() => widget.gateway.activate(scenarioKey, _browserSessionId));

  Future<void> _run(Future<void> Function() command) async {
    setState(() {
      _busy = true;
      _commandError = null;
    });
    try {
      await command();
      if (mounted) _reload();
    } on TestbedFailure catch (failure) {
      if (mounted) setState(() => _commandError = failure.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<TestbedSnapshot> _loadSnapshot() async {
    final snapshot = await widget.gateway.load();
    _fingerprint = _snapshotFingerprint(snapshot);
    return snapshot;
  }

  Future<void> _refreshSilently() async {
    if (_refreshing) return;
    _refreshing = true;
    try {
      final snapshot = await widget.gateway.load();
      if (!mounted) return;
      final fingerprint = _snapshotFingerprint(snapshot);
      if (fingerprint != _fingerprint) {
        setState(() {
          _fingerprint = fingerprint;
          _snapshot = Future.value(snapshot);
          _commandError = null;
        });
      }
    } on TestbedFailure {
      if (widget.refreshController?.userInitiated == true && mounted) {
        setState(
          () => _commandError =
              'Product Factory Testbed kon niet worden vernieuwd.',
        );
      }
    } finally {
      _refreshing = false;
    }
  }

  String _snapshotFingerprint(TestbedSnapshot snapshot) => jsonEncode({
    'active': {
      'key': snapshot.active.scenario.key,
      'version': snapshot.active.scenario.version,
      'title': snapshot.active.scenario.title,
      'description': snapshot.active.scenario.description,
      'datasetVersion': snapshot.active.datasetVersion,
      'testbedVersion': snapshot.active.testbedVersion,
      'currentStep': snapshot.active.currentStep,
      'lockOwner': snapshot.active.lockOwner,
    },
    'scenarios': snapshot.scenarios
        .map(
          (scenario) => {
            'key': scenario.key,
            'version': scenario.version,
            'title': scenario.title,
            'description': scenario.description,
          },
        )
        .toList(),
  });

  void _reload() => setState(() {
    _snapshot = _loadSnapshot();
  });
}

class _TestbedError extends StatelessWidget {
  const _TestbedError({required this.onRetry});
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Card(
    color: Theme.of(context).colorScheme.errorContainer,
    child: ListTile(
      leading: const Icon(Icons.cloud_off),
      title: const Text('Testbed kon niet worden geladen'),
      trailing: TextButton(onPressed: onRetry, child: const Text('Opnieuw')),
    ),
  );
}

class _TestbedPageFrame extends StatelessWidget {
  const _TestbedPageFrame({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
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
