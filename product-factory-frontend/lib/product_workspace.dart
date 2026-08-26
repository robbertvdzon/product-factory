import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'configuration.dart';
import 'http_client_factory.dart';

String _value(Object? value) {
  if (value is String) return value;
  if (value is Map && value['value'] is String) return value['value'] as String;
  return value?.toString() ?? '';
}

class ProductSummary {
  const ProductSummary({
    required this.id,
    required this.name,
    required this.status,
    required this.dispatchingEnabled,
    required this.version,
  });
  factory ProductSummary.fromJson(Map<String, Object?> json) => ProductSummary(
    id: _value(json['id']),
    name: _value(json['name']),
    status: _value(json['status']),
    dispatchingEnabled: json['dispatchingEnabled'] == true,
    version: (json['version'] as num?)?.toInt() ?? 0,
  );
  final String id;
  final String name;
  final String status;
  final bool dispatchingEnabled;
  final int version;
}

class ProductWorkspaceData {
  const ProductWorkspaceData({
    required this.product,
    required this.assignment,
    required this.testConfiguration,
    required this.schedules,
    required this.signals,
    required this.questions,
    required this.meetings,
    required this.decisions,
    required this.decisionArchive,
    required this.epics,
    required this.designSessions,
    required this.epicHistories,
  });
  final ProductSummary product;
  final Map<String, Object?>? assignment;
  final Map<String, Object?>? testConfiguration;
  final List<Map<String, Object?>> schedules;
  final List<Map<String, Object?>> signals;
  final List<Map<String, Object?>> questions;
  final List<Map<String, Object?>> meetings;
  final List<Map<String, Object?>> decisions;
  final List<Map<String, Object?>> decisionArchive;
  final List<Map<String, Object?>> epics;
  final List<Map<String, Object?>> designSessions;
  final Map<String, List<Map<String, Object?>>> epicHistories;
}

abstract interface class ProductGateway {
  Future<List<ProductSummary>> products();
  Future<ProductWorkspaceData> workspace(ProductSummary product);
  Future<void> createProduct(String name, String? requestedId);
  Future<void> setStatus(ProductSummary product, String status);
  Future<void> setDispatching(ProductSummary product, bool enabled);
  Future<void> saveAssignment(String productId, Map<String, Object?> body);
  Future<void> saveTestConfiguration(
    String productId,
    Map<String, Object?> body,
  );
  Future<void> saveSchedule(
    String productId,
    String process,
    Map<String, Object?> body,
  );
  Future<void> createSignal(String productId, String text);
  Future<void> reviewSignal(String signalId, int version);
  Future<void> completeSignal(String signalId, int version, String outcome);
  Future<void> createMeeting(String productId, String reason);
  Future<void> addMeetingMessage(String meetingId, int version, String text);
  Future<void> closeMeeting(
    String meetingId,
    int version,
    String minutes,
    String? openOutcome,
  );
  Future<void> answerQuestion(
    String questionId,
    int version,
    String meetingId,
    String messageId,
    String answer,
  );
  Future<void> createDecision(String productId, String decision);
  Future<void> reviseDecision(
    String productId,
    String decisionId,
    int version,
    String decision,
  );
  Future<void> withdrawDecision(
    String productId,
    String decisionId,
    int version,
    String reason,
  );
  Future<void> supersedeDecision(
    String productId,
    String decisionId,
    int version,
    String replacement,
  );
  Future<void> runProductDesign(String productId);
  Future<void> withdrawEpic(String epicId, int version, String reason);
  Future<void> cancelEpic(String epicId, int version, String reason);
}

class HttpProductGateway implements ProductGateway {
  HttpProductGateway({this.csrfToken, http.Client? client, String? backendUrl})
    : _client = client ?? createHttpClient(),
      _backendUrl = (backendUrl ?? AppConfiguration.backendUrl).replaceAll(
        RegExp(r'/$'),
        '',
      );
  final String? csrfToken;
  final http.Client _client;
  final String _backendUrl;
  int _sequence = 0;

  @override
  Future<List<ProductSummary>> products() async =>
      ((await _get('/api/products')) as List)
          .map(
            (e) => ProductSummary.fromJson((e as Map).cast<String, Object?>()),
          )
          .toList();

  @override
  Future<ProductWorkspaceData> workspace(ProductSummary product) async {
    final id = Uri.encodeComponent(product.id);
    final values = await Future.wait([
      _optional('/api/products/$id/assignment'),
      _optional('/api/products/$id/test-configuration'),
      _get('/api/products/$id/schedules'),
      _get('/api/products/$id/signals'),
      _get('/api/products/$id/questions'),
      _get('/api/products/$id/meetings'),
      _get('/api/products/$id/decisions'),
      _get('/api/products/$id/decisions/archive'),
      _get('/api/products/$id/epics'),
      _get('/api/products/$id/design/sessions'),
    ]);
    List<Map<String, Object?>> list(int index) =>
        ((values[index] as List?) ?? const [])
            .map((e) => (e as Map).cast<String, Object?>())
            .toList();
    final epics = list(8);
    final historyEntries = await Future.wait(
      epics.map((epic) async {
        final epicId = Uri.encodeComponent(_value(epic['id']));
        final versions = ((await _get('/api/epics/$epicId/history')) as List)
            .map((e) => (e as Map).cast<String, Object?>())
            .toList();
        return MapEntry(_value(epic['id']), versions);
      }),
    );
    return ProductWorkspaceData(
      product: product,
      assignment: (values[0] as Map?)?.cast<String, Object?>(),
      testConfiguration: (values[1] as Map?)?.cast<String, Object?>(),
      schedules: list(2),
      signals: list(3),
      questions: list(4),
      meetings: list(5),
      decisions: list(6),
      decisionArchive: list(7),
      epics: epics,
      designSessions: list(9),
      epicHistories: Map.fromEntries(historyEntries),
    );
  }

  @override
  Future<void> createProduct(String name, String? requestedId) =>
      _send('POST', '/api/products', {
        'name': name,
        if (requestedId?.trim().isNotEmpty == true)
          'requestedId': requestedId!.trim(),
        'idempotencyKey': _key('product'),
      });
  @override
  Future<void> setStatus(ProductSummary product, String status) =>
      _send('PATCH', '/api/products/${product.id}/status', {
        'status': status,
        'expectedVersion': product.version,
        'idempotencyKey': _key('status'),
      });
  @override
  Future<void> setDispatching(ProductSummary product, bool enabled) =>
      _send('PATCH', '/api/products/${product.id}/dispatching', {
        'enabled': enabled,
        'expectedVersion': product.version,
        'idempotencyKey': _key('dispatch'),
      });
  @override
  Future<void> saveAssignment(String productId, Map<String, Object?> body) =>
      _send('PUT', '/api/products/$productId/assignment', {
        ...body,
        'idempotencyKey': _key('assignment'),
      });
  @override
  Future<void> saveTestConfiguration(
    String productId,
    Map<String, Object?> body,
  ) => _send('PUT', '/api/products/$productId/test-configuration', {
    ...body,
    'idempotencyKey': _key('test-config'),
  });
  @override
  Future<void> saveSchedule(
    String productId,
    String process,
    Map<String, Object?> body,
  ) => _send('PUT', '/api/products/$productId/schedules/$process', {
    ...body,
    'idempotencyKey': _key('schedule'),
  });
  @override
  Future<void> createSignal(String productId, String text) =>
      _send('POST', '/api/products/$productId/signals', {
        'category': 'FEEDBACK',
        'urgency': 'NORMAL',
        'source': 'stakeholder-ui',
        'text': text,
        'idempotencyKey': _key('signal'),
      });
  @override
  Future<void> reviewSignal(String signalId, int version) => _send(
    'POST',
    '/api/products/signals/$signalId/review',
    {'expectedVersion': version, 'idempotencyKey': _key('signal-review')},
  );
  @override
  Future<void> completeSignal(String signalId, int version, String outcome) =>
      _send('POST', '/api/products/signals/$signalId/investigation', {
        'verificationId':
            'stakeholder-${DateTime.now().microsecondsSinceEpoch}',
        'outcome': outcome,
        'expectedVersion': version,
        'idempotencyKey': _key('signal-complete'),
      });
  @override
  Future<void> createMeeting(String productId, String reason) =>
      _send('POST', '/api/products/$productId/meetings', {
        'reason': reason,
        'agenda': <String>[],
        'linkedObjects': <Object>[],
        'idempotencyKey': _key('meeting'),
      });
  @override
  Future<void> addMeetingMessage(String meetingId, int version, String text) =>
      _send('POST', '/api/products/meetings/$meetingId/messages', {
        'text': text,
        'expectedVersion': version,
        'idempotencyKey': _key('message'),
        'targetAgentRole': 'MEETING_AGENT',
      });
  @override
  Future<void> closeMeeting(
    String meetingId,
    int version,
    String minutes,
    String? openOutcome,
  ) => _send('POST', '/api/products/meetings/$meetingId/close', {
    'expectedVersion': version,
    'idempotencyKey': _key('close'),
  });
  @override
  Future<void> answerQuestion(
    String questionId,
    int version,
    String meetingId,
    String messageId,
    String answer,
  ) => _send('POST', '/api/products/questions/$questionId/answer', {
    'meetingId': meetingId,
    'messageId': messageId,
    'answer': answer,
    'expectedVersion': version,
    'idempotencyKey': _key('answer'),
  });
  @override
  Future<void> createDecision(String productId, String decision) => _send(
    'POST',
    '/api/products/$productId/decisions',
    {'decision': decision, 'idempotencyKey': _key('decision')},
  );
  @override
  Future<void> reviseDecision(
    String productId,
    String decisionId,
    int version,
    String decision,
  ) => _send('POST', '/api/products/$productId/decisions/$decisionId/revise', {
    'decision': decision,
    'expectedVersion': version,
    'idempotencyKey': _key('revise'),
  });
  @override
  Future<void> withdrawDecision(
    String productId,
    String decisionId,
    int version,
    String reason,
  ) =>
      _send('POST', '/api/products/$productId/decisions/$decisionId/withdraw', {
        'reason': reason,
        'expectedVersion': version,
        'idempotencyKey': _key('withdraw'),
      });
  @override
  Future<void> supersedeDecision(
    String productId,
    String decisionId,
    int version,
    String replacement,
  ) => _send('POST', '/api/products/$productId/decisions/supersede', {
    'supersededIds': [decisionId],
    'replacementDecision': replacement,
    'expectedVersions': {decisionId: version},
    'idempotencyKey': _key('supersede'),
  });

  @override
  Future<void> runProductDesign(String productId) =>
      _send('POST', '/api/products/$productId/design/sessions/run', const {});

  @override
  Future<void> withdrawEpic(String epicId, int version, String reason) =>
      _send('POST', '/api/epics/$epicId/withdraw', {
        'reason': reason,
        'expectedVersion': version,
        'idempotencyKey': _key('epic-withdraw'),
      });

  @override
  Future<void> cancelEpic(String epicId, int version, String reason) =>
      _send('POST', '/api/epics/$epicId/cancel', {
        'reason': reason,
        'expectedVersion': version,
        'idempotencyKey': _key('epic-cancel'),
      });

  String _key(String prefix) =>
      'ui-$prefix-${DateTime.now().microsecondsSinceEpoch}-${_sequence++}';
  Uri _uri(String path) => Uri.parse('$_backendUrl$path');
  Future<Object?> _optional(String path) async {
    try {
      return await _get(path);
    } on ProductFailure catch (e) {
      if (e.status == 404) return null;
      rethrow;
    }
  }

  Future<Object?> _get(String path) async =>
      _decode(await _client.get(_uri(path)));
  Future<void> _send(
    String method,
    String path,
    Map<String, Object?> body,
  ) async {
    final headers = <String, String>{'Content-Type': 'application/json'};
    final token = csrfToken;
    if (token != null) headers['X-PF-CSRF'] = token;
    final request = http.Request(method, _uri(path))
      ..headers.addAll(headers)
      ..body = jsonEncode(body);
    final streamed = await _client.send(request);
    _decode(await http.Response.fromStream(streamed));
  }

  Object? _decode(http.Response response) {
    Object? value;
    if (response.bodyBytes.isNotEmpty) {
      try {
        value = jsonDecode(utf8.decode(response.bodyBytes));
      } on FormatException {
        value = null;
      }
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final message = value is Map && value['message'] is String
          ? value['message'] as String
          : 'De aanvraag kon niet worden uitgevoerd.';
      throw ProductFailure(response.statusCode, message);
    }
    return value;
  }
}

class ProductFailure implements Exception {
  const ProductFailure(this.status, this.message);
  final int status;
  final String message;
}

class ProductWorkspacePage extends StatefulWidget {
  const ProductWorkspacePage({required this.gateway, super.key});
  final ProductGateway gateway;
  @override
  State<ProductWorkspacePage> createState() => _ProductWorkspacePageState();
}

class _ProductWorkspacePageState extends State<ProductWorkspacePage>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;
  List<ProductSummary> _products = const [];
  ProductSummary? _selected;
  ProductWorkspaceData? _data;
  bool _busy = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 7, vsync: this);
    _loadProducts();
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _loadProducts([String? selectId]) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final products = await widget.gateway.products();
      final selected =
          products
              .where((p) => p.id == (selectId ?? _selected?.id))
              .firstOrNull ??
          (products.isEmpty ? null : products.first);
      final data = selected == null
          ? null
          : await widget.gateway.workspace(selected);
      if (mounted) {
        setState(() {
          _products = products;
          _selected = selected;
          _data = data;
        });
      }
    } on ProductFailure catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'Productgegevens konden niet worden geladen.');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

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
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 24,
              runSpacing: 12,
              children: [
                Text(
                  'Producten',
                  style: Theme.of(context).textTheme.displaySmall?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                FilledButton.icon(
                  onPressed: _createProduct,
                  icon: const Icon(Icons.add),
                  label: const Text('Product aanmaken'),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (_error != null)
              Card(
                color: Theme.of(context).colorScheme.errorContainer,
                child: ListTile(
                  title: Text(_error!),
                  trailing: TextButton(
                    onPressed: _loadProducts,
                    child: const Text('Opnieuw'),
                  ),
                ),
              ),
            if (_busy) const LinearProgressIndicator(),
            if (!_busy && _products.isEmpty)
              const Card(
                child: ListTile(
                  leading: Icon(Icons.inventory_2_outlined),
                  title: Text('Nog geen producten'),
                  subtitle: Text(
                    'Maak het eerste product aan via hetzelfde publieke command als ieder volgend product.',
                  ),
                ),
              ),
            if (_products.isNotEmpty) ...[
              DropdownButtonFormField<String>(
                initialValue: _selected?.id,
                decoration: const InputDecoration(
                  labelText: 'Actief product',
                  border: OutlineInputBorder(),
                ),
                items: _products
                    .map(
                      (p) => DropdownMenuItem(
                        value: p.id,
                        child: Text('${p.name} · ${p.status}'),
                      ),
                    )
                    .toList(),
                onChanged: (id) => _loadProducts(id),
              ),
              const SizedBox(height: 20),
              if (_data != null) _overview(_data!),
              const SizedBox(height: 20),
              TabBar(
                controller: _tabs,
                isScrollable: true,
                tabs: const [
                  Tab(text: 'Opdracht'),
                  Tab(text: 'Ontwerp'),
                  Tab(text: 'Signalen'),
                  Tab(text: 'Vragen van agents'),
                  Tab(text: 'Overleggen'),
                  Tab(text: 'Besluiten'),
                  Tab(text: 'Automatisering'),
                ],
              ),
              SizedBox(
                height: 520,
                child: TabBarView(
                  controller: _tabs,
                  children: [
                    _assignment(_data!),
                    _design(_data!),
                    _signals(_data!),
                    _questions(_data!),
                    _meetings(_data!),
                    _decisions(_data!),
                    _schedules(_data!),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    ),
  );

  Widget _overview(ProductWorkspaceData data) => Card(
    child: Padding(
      padding: const EdgeInsets.all(20),
      child: Wrap(
        spacing: 20,
        runSpacing: 12,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          SizedBox(
            width: 320,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  data.product.name,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                SelectableText(data.product.id),
                Text(
                  'Status: ${data.product.status} · versie ${data.product.version}',
                ),
              ],
            ),
          ),
          OutlinedButton(
            onPressed: () => _mutate(
              () => widget.gateway.setStatus(
                data.product,
                data.product.status == 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
              ),
            ),
            child: Text(
              data.product.status == 'ACTIVE' ? 'Deactiveren' : 'Activeren',
            ),
          ),
          FilterChip(
            label: const Text('Dispatching'),
            selected: data.product.dispatchingEnabled,
            onSelected: (value) => _mutate(
              () => widget.gateway.setDispatching(data.product, value),
            ),
          ),
          const Chip(
            avatar: Icon(Icons.auto_awesome_outlined),
            label: Text('Productontwerp actief'),
          ),
        ],
      ),
    ),
  );

  Widget _design(
    ProductWorkspaceData data,
  ) => _section('Ontwerp', Icons.architecture_outlined, [
    Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        FilledButton.icon(
          onPressed: () =>
              _mutate(() => widget.gateway.runProductDesign(data.product.id)),
          icon: const Icon(Icons.play_arrow),
          label: const Text('Productontwerp starten of hervatten'),
        ),
      ],
    ),
    const SizedBox(height: 12),
    Text('Epics', style: Theme.of(context).textTheme.titleMedium),
    if (data.epics.isEmpty)
      const Text('Nog geen epics gepubliceerd.')
    else
      ...data.epics.map(
        (epic) => ExpansionTile(
          leading: const Icon(Icons.view_agenda_outlined),
          title: Text('${epic['title']} · ${epic['status']}'),
          subtitle: Text('${epic['summary']}'),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SelectableText(
              'Epic ${_value(epic['id'])} · versie ${epic['version']}',
            ),
            const SizedBox(height: 8),
            Text('Probleem', style: Theme.of(context).textTheme.labelLarge),
            Text('${epic['problem']}'),
            const SizedBox(height: 8),
            Text('Oplossing', style: Theme.of(context).textTheme.labelLarge),
            Text('${epic['solution']}'),
            if (epic['uxDesign'] != null) ...[
              const SizedBox(height: 8),
              Text('UX-ontwerp', style: Theme.of(context).textTheme.labelLarge),
              Text('${epic['uxDesign']}'),
            ],
            const SizedBox(height: 8),
            Text(
              'Acceptatiecriteria',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            ...(epic['acceptanceCriteria'] as List? ?? const []).map(
              (criterion) => Text('• $criterion'),
            ),
            const SizedBox(height: 8),
            Text('Behapbaarheid: ${epic['slicabilityRationale']}'),
            Text(
              'Bronnen: ${(epic['directionReferences'] as List? ?? const []).length} richtingsreferentie(s)',
            ),
            const SizedBox(height: 8),
            Text(
              'Versiehistorie',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            ...(data.epicHistories[_value(epic['id'])] ?? const []).map(
              (version) => Text(
                'v${version['version']} · ${version['status']} · ${version['title']}',
              ),
            ),
            if (epic['status'] == 'AVAILABLE')
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: () => _epicReasonAction(epic, cancel: false),
                  icon: const Icon(Icons.archive_outlined),
                  label: const Text('Epic intrekken'),
                ),
              ),
            if (const {
              'IN_PLANNING',
              'ACTIVE',
              'VERIFYING',
            }.contains(epic['status']))
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: () => _epicReasonAction(epic, cancel: true),
                  icon: const Icon(Icons.cancel_outlined),
                  label: const Text('Epic annuleren'),
                ),
              ),
          ],
        ),
      ),
    const Divider(height: 28),
    Text('Processessies', style: Theme.of(context).textTheme.titleMedium),
    if (data.designSessions.isEmpty)
      const Text('Nog geen ontwerpsessies gestart.')
    else
      ...data.designSessions.map(
        (session) => ListTile(
          leading: Icon(
            session['status'] == 'SUCCEEDED'
                ? Icons.check_circle_outline
                : session['status'] == 'BLOCKED'
                ? Icons.error_outline
                : Icons.hourglass_top,
          ),
          title: Text('${session['status']} · ${_value(session['id'])}'),
          subtitle: Text(
            '${session['resultSummary'] ?? session['blockedReason'] ?? 'AI-taak wordt duurzaam gevolgd.'}\n'
            '${(session['implementation'] as Map?)?['artifact'] ?? 'product-design-impl-mvp'} · '
            '${(session['aiTaskIds'] as List? ?? const []).length} AI-taak/taken\n'
            'Git ${session['repositoryCommitSha'] ?? 'nog niet bevroren'}',
          ),
          isThreeLine: true,
        ),
      ),
  ]);

  Widget _assignment(ProductWorkspaceData data) {
    final a = data.assignment;
    final t = data.testConfiguration;
    return _section(
      'Productopdracht en testomgevingen',
      Icons.assignment_outlined,
      [
        if (a == null)
          const Text('Productopdracht nog niet vastgelegd.')
        else ...[
          Text('Doelgroep: ${a['audience']}'),
          Text('Doel: ${a['goal']}'),
          Text(
            'Harde grenzen: ${(a['hardBoundaries'] as List? ?? const []).join(', ')}',
          ),
          SelectableText('Git: ${a['publicGitUrl']}'),
          Text('Versie ${a['version']}'),
        ],
        Align(
          alignment: Alignment.centerRight,
          child: TextButton.icon(
            onPressed: () => _editAssignment(data),
            icon: const Icon(Icons.edit),
            label: const Text('Opdracht bewerken'),
          ),
        ),
        const Divider(),
        Text('Testomgevingen', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 6),
        if (t == null)
          const Text('Nog niet geconfigureerd.')
        else ...[
          Text('Acceptatie: ${(t['acceptance'] as Map?)?['baseUrl']}'),
          Text(
            'Productie: ${(t['production'] as Map?)?['baseUrl'] ?? 'niet ingesteld'}',
          ),
          Text('Versie ${t['version']}'),
        ],
        Align(
          alignment: Alignment.centerRight,
          child: TextButton.icon(
            onPressed: () => _editTestConfiguration(data),
            icon: const Icon(Icons.settings_outlined),
            label: const Text('Omgevingen beheren'),
          ),
        ),
      ],
    );
  }

  Widget _signals(ProductWorkspaceData data) => _section(
    'Signalen',
    Icons.feedback_outlined,
    data.signals.isEmpty
        ? const [Text('Geen signalen.')]
        : data.signals
              .map(
                (s) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(_value(s['text'])),
                  subtitle: Text(
                    '${s['source']} · ${s['category']} · versie ${s['version']}',
                  ),
                  trailing: Wrap(
                    children: [
                      if (s['status'] == 'OPEN')
                        IconButton(
                          tooltip: 'In behandeling nemen',
                          onPressed: () => _mutate(
                            () => widget.gateway.reviewSignal(
                              _value(s['id']),
                              (s['version'] as num).toInt(),
                            ),
                          ),
                          icon: const Icon(Icons.playlist_add_check),
                        ),
                      if (s['status'] != 'PROCESSED')
                        IconButton(
                          tooltip: 'Onderzoek afronden',
                          onPressed: () => _textAction(
                            'Onderzoek afronden',
                            'Uitkomst',
                            (text) => widget.gateway.completeSignal(
                              _value(s['id']),
                              (s['version'] as num).toInt(),
                              text,
                            ),
                          ),
                          icon: const Icon(Icons.fact_check_outlined),
                        ),
                      Chip(label: Text(_value(s['status']))),
                    ],
                  ),
                ),
              )
              .toList(),
    action: TextButton.icon(
      onPressed: () => _textAction(
        'Nieuw signaal',
        'Signaal',
        (text) => widget.gateway.createSignal(data.product.id, text),
      ),
      icon: const Icon(Icons.add),
      label: const Text('Toevoegen'),
    ),
  );

  Future<void> _epicReasonAction(
    Map<String, Object?> epic, {
    required bool cancel,
  }) async {
    final controller = TextEditingController();
    final reason = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(cancel ? 'Epic annuleren' : 'Epic intrekken'),
        content: TextField(
          controller: controller,
          maxLength: 1000,
          minLines: 2,
          maxLines: 4,
          decoration: const InputDecoration(
            labelText: 'Reden',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Terug'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Bevestigen'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (reason == null || reason.isEmpty) return;
    final id = _value(epic['id']);
    final version = (epic['version'] as num).toInt();
    await _mutate(
      () => cancel
          ? widget.gateway.cancelEpic(id, version, reason)
          : widget.gateway.withdrawEpic(id, version, reason),
    );
  }

  Widget _questions(ProductWorkspaceData data) => _section(
    'Vragen van agents',
    Icons.question_answer_outlined,
    data.questions.isEmpty
        ? const [Text('Geen vragen.')]
        : data.questions.map((q) {
            final source = _stakeholderMessage(data.meetings);
            return ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(_value(q['question'])),
              subtitle: Text(
                '${q['agentRole']} · processessie ${_value(q['processSessionId'])}\n${q['context']}',
              ),
              isThreeLine: true,
              trailing: Wrap(
                children: [
                  if (q['status'] == 'OPEN' && source != null)
                    IconButton(
                      tooltip: 'Beantwoorden met laatste Stakeholderbericht',
                      onPressed: () => _mutate(
                        () => widget.gateway.answerQuestion(
                          _value(q['id']),
                          (q['version'] as num).toInt(),
                          source.$1,
                          source.$2,
                          source.$3,
                        ),
                      ),
                      icon: const Icon(Icons.reply),
                    ),
                  Chip(label: Text(_value(q['status']))),
                ],
              ),
            );
          }).toList(),
  );
  Widget _meetings(ProductWorkspaceData data) => _section(
    'Overleggen',
    Icons.forum_outlined,
    data.meetings.isEmpty
        ? const [Text('Geen overleggen.')]
        : data.meetings
              .map(
                (m) => Card(
                  child: ExpansionTile(
                    title: Text(_value(m['reason'])),
                    subtitle: Text(
                      '${(m['messages'] as List? ?? const []).length} berichten · versie ${m['version']} · ${_value(m['status'])}',
                    ),
                    trailing: Wrap(
                      children: [
                        if (m['status'] != 'CLOSED')
                          IconButton(
                            tooltip:
                                'Bericht toevoegen en Meeting Agent laten reageren',
                            onPressed: () => _textAction(
                              'Stakeholderbericht',
                              'Bericht',
                              (text) => widget.gateway.addMeetingMessage(
                                _value(m['id']),
                                (m['version'] as num).toInt(),
                                text,
                              ),
                            ),
                            icon: const Icon(Icons.chat_outlined),
                          ),
                        if (m['status'] != 'CLOSED')
                          IconButton(
                            tooltip: 'Notulenagent starten',
                            onPressed: () => _closeMeeting(m),
                            icon: const Icon(Icons.summarize_outlined),
                          ),
                      ],
                    ),
                    childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    children: [
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          'Agenda: ${(m['agenda'] as List? ?? const []).join(', ')}',
                        ),
                      ),
                      const SizedBox(height: 8),
                      ...(m['messages'] as List? ?? const []).map((message) {
                        final row = (message as Map).cast<String, Object?>();
                        return ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: Icon(
                            row['senderRole'] == 'STAKEHOLDER'
                                ? Icons.person_outline
                                : Icons.smart_toy_outlined,
                          ),
                          title: Text(
                            '${row['senderRole']}${row['representedAgentRole'] == null ? '' : ' · ${row['representedAgentRole']}'}',
                          ),
                          subtitle: Text('${row['text']}\n${row['createdAt']}'),
                        );
                      }),
                      if (m['minutes'] != null)
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.description_outlined),
                          title: const Text('Brongetrouwe notulen'),
                          subtitle: Text('${m['minutes']}'),
                        ),
                      ...(m['outcomes'] as List? ?? const []).map((outcome) {
                        final row = (outcome as Map).cast<String, Object?>();
                        return ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.account_tree_outlined),
                          title: Text('${row['description']}'),
                          subtitle: Text(
                            '${row['commandType']} · ${row['status']}${row['errorCode'] == null ? '' : ' · ${row['errorCode']}'}',
                          ),
                        );
                      }),
                    ],
                  ),
                ),
              )
              .toList(),
    action: TextButton.icon(
      onPressed: () => _textAction(
        'Overleg starten',
        'Reden',
        (text) => widget.gateway.createMeeting(data.product.id, text),
      ),
      icon: const Icon(Icons.add),
      label: const Text('Starten'),
    ),
  );
  Widget _decisions(ProductWorkspaceData data) => _section(
    'Besluitenregister',
    Icons.gavel_outlined,
    [
      if (data.decisions.isEmpty) const Text('Geen actuele besluiten.'),
      ...data.decisions.map(
        (d) => Card(
          child: ListTile(
            title: Text(_value(d['decision'])),
            subtitle: Text(
              'Geldig vanaf ${d['validFrom']} · versie ${d['version']}',
            ),
            trailing: PopupMenuButton<String>(
              onSelected: (action) {
                final fn = action == 'revise'
                    ? widget.gateway.reviseDecision
                    : action == 'withdraw'
                    ? widget.gateway.withdrawDecision
                    : widget.gateway.supersedeDecision;
                _textAction(
                  action == 'withdraw'
                      ? 'Besluit intrekken'
                      : action == 'revise'
                      ? 'Besluit herzien'
                      : 'Besluit vervangen',
                  action == 'withdraw' ? 'Reden' : 'Nieuwe besluittekst',
                  (text) => fn(
                    data.product.id,
                    _value(d['id']),
                    (d['version'] as num).toInt(),
                    text,
                  ),
                );
              },
              itemBuilder: (_) => const [
                PopupMenuItem(value: 'revise', child: Text('Herzien')),
                PopupMenuItem(value: 'withdraw', child: Text('Intrekken')),
                PopupMenuItem(value: 'supersede', child: Text('Vervangen')),
              ],
            ),
          ),
        ),
      ),
      const Divider(),
      Text(
        'Volledig archief (${data.decisionArchive.length})',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      ...data.decisionArchive.map(
        (d) => ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text('${d['state']} · ${_value(d['id'])}'),
          subtitle: Text(
            '${(d['history'] as List? ?? const []).length} versie(s)${d['withdrawalReason'] == null ? '' : ' · ${d['withdrawalReason']}'}',
          ),
        ),
      ),
    ],
    action: TextButton.icon(
      onPressed: () => _textAction(
        'Besluit vastleggen',
        'Blijvende keuze',
        (text) => widget.gateway.createDecision(data.product.id, text),
      ),
      icon: const Icon(Icons.add),
      label: const Text('Vastleggen'),
    ),
  );
  Widget _schedules(
    ProductWorkspaceData data,
  ) => _section('Instellingen · Automatisering', Icons.schedule_outlined, [
    const Text(
      'Schedules worden nu alleen opgeslagen en getoond; automatische starts worden pas in stap 9 geactiveerd.',
    ),
    const SizedBox(height: 8),
    ...data.schedules.map(
      (s) => Card(
        child: ListTile(
          title: Text(_value(s['process']).replaceAll('_', ' ')),
          subtitle: Text(
            '${s['timezone']} · nextRunAt: ${s['nextRunAt'] ?? '—'} · versie ${s['version']}',
          ),
          trailing: Switch(
            value: s['enabled'] == true,
            onChanged: (enabled) => _schedule(data.product.id, s, enabled),
          ),
        ),
      ),
    ),
  ]);

  Widget _section(
    String title,
    IconData icon,
    List<Widget> children, {
    Widget? action,
  }) => SingleChildScrollView(
    padding: const EdgeInsets.symmetric(vertical: 16),
    child: Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    title,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                ?action,
              ],
            ),
            const SizedBox(height: 12),
            ...children,
          ],
        ),
      ),
    ),
  );

  (String, String, String)? _stakeholderMessage(
    List<Map<String, Object?>> meetings,
  ) {
    for (final meeting in meetings.reversed) {
      final messages = (meeting['messages'] as List? ?? const [])
          .whereType<Map>()
          .toList();
      for (final message in messages.reversed) {
        if (message['senderRole'] == 'STAKEHOLDER') {
          return (
            _value(meeting['id']),
            _value(message['id']),
            _value(message['text']),
          );
        }
      }
    }
    return null;
  }

  Future<void> _createProduct() async {
    final name = TextEditingController();
    final id = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Product aanmaken'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: name,
              decoration: const InputDecoration(labelText: 'Naam'),
            ),
            TextField(
              controller: id,
              decoration: const InputDecoration(
                labelText: 'Stabiel ID (optioneel)',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Aanmaken'),
          ),
        ],
      ),
    );
    if (ok == true && name.text.trim().isNotEmpty) {
      await _mutate(
        () => widget.gateway.createProduct(name.text.trim(), id.text),
      );
    }
  }

  Future<void> _editAssignment(ProductWorkspaceData data) async {
    final a = data.assignment;
    final audience = TextEditingController(text: _value(a?['audience']));
    final goal = TextEditingController(text: _value(a?['goal']));
    final boundaries = TextEditingController(
      text: (a?['hardBoundaries'] as List? ?? const []).join('\n'),
    );
    final git = TextEditingController(text: _value(a?['publicGitUrl']));
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Productopdracht'),
        content: SizedBox(
          width: 560,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: audience,
                decoration: const InputDecoration(labelText: 'Doelgroep'),
              ),
              TextField(
                controller: goal,
                decoration: const InputDecoration(labelText: 'Productdoel'),
              ),
              TextField(
                controller: boundaries,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: 'Harde grenzen (één per regel)',
                ),
              ),
              TextField(
                controller: git,
                decoration: const InputDecoration(
                  labelText: 'Publieke Git-URL',
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Opslaan'),
          ),
        ],
      ),
    );
    if (ok == true) {
      await _mutate(
        () => widget.gateway.saveAssignment(data.product.id, {
          'audience': audience.text,
          'goal': goal.text,
          'hardBoundaries': boundaries.text.split('\n'),
          'publicGitUrl': git.text,
          'expectedVersion': (a?['version'] as num?)?.toInt() ?? 0,
        }),
      );
    }
  }

  Future<void> _editTestConfiguration(ProductWorkspaceData data) async {
    final t = data.testConfiguration;
    final acceptance = (t?['acceptance'] as Map?)?.cast<String, Object?>();
    final production = (t?['production'] as Map?)?.cast<String, Object?>();
    final acceptanceUrl = TextEditingController(
      text: _value(acceptance?['baseUrl']),
    );
    final productionUrl = TextEditingController(
      text: _value(production?['baseUrl']),
    );
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Testomgevingen beheren'),
        content: SizedBox(
          width: 560,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: acceptanceUrl,
                decoration: const InputDecoration(
                  labelText: 'Acceptatie-URL (HTTPS)',
                ),
              ),
              TextField(
                controller: productionUrl,
                decoration: const InputDecoration(
                  labelText: 'Productie-URL (optioneel)',
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'Veilige routes: / en /api/version · revision JSON-pad: commit',
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Opslaan'),
          ),
        ],
      ),
    );
    if (ok == true) {
      Map<String, Object?> environment(String name, String url) => {
        'name': name,
        'baseUrl': url,
        'allowedRoutes': ['/', '/api/version'],
        'revisionEndpoint': '/api/version',
        'revisionJsonPath': 'commit',
        'dataBoundaries': ['Geen productiegegevens wijzigen'],
        'accessBoundaries': ['Alleen geautoriseerde browsertests'],
      };
      await _mutate(
        () => widget.gateway.saveTestConfiguration(data.product.id, {
          'acceptance': environment('Acceptatie', acceptanceUrl.text),
          if (productionUrl.text.trim().isNotEmpty)
            'production': environment('Productie', productionUrl.text),
          'expectedVersion': (t?['version'] as num?)?.toInt() ?? 0,
        }),
      );
    }
  }

  Future<void> _schedule(
    String productId,
    Map<String, Object?> schedule,
    bool enabled,
  ) async {
    final interval = TextEditingController(
      text: _value(((schedule['pattern'] as Map?)?['intervalMinutes']) ?? 60),
    );
    final timezone = TextEditingController(
      text: _value(schedule['timezone']).isEmpty
          ? 'Europe/Amsterdam'
          : _value(schedule['timezone']),
    );
    final day = TextEditingController(text: 'MONDAY');
    final time = TextEditingController(text: '09:00');
    var weekly =
        ((schedule['pattern'] as Map?)?['weeklyRules'] as List?)?.isNotEmpty ==
        true;
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text('${schedule['process']} instellen'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: timezone,
                decoration: const InputDecoration(labelText: 'IANA-tijdzone'),
              ),
              const SizedBox(height: 12),
              SegmentedButton<bool>(
                segments: const [
                  ButtonSegment(value: false, label: Text('Interval')),
                  ButtonSegment(value: true, label: Text('Week/dag/tijd')),
                ],
                selected: {weekly},
                onSelectionChanged: (value) =>
                    setDialogState(() => weekly = value.single),
              ),
              if (weekly) ...[
                TextField(
                  controller: day,
                  decoration: const InputDecoration(
                    labelText: 'Weekdag (bijv. MONDAY)',
                  ),
                ),
                TextField(
                  controller: time,
                  decoration: const InputDecoration(
                    labelText: 'Lokale tijd (HH:mm)',
                  ),
                ),
              ] else
                TextField(
                  controller: interval,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Interval in hele minuten',
                  ),
                ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Annuleren'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Opslaan'),
            ),
          ],
        ),
      ),
    );
    if (ok == true) {
      await _mutate(
        () => widget.gateway.saveSchedule(
          productId,
          _value(schedule['process']),
          {
            'enabled': enabled,
            'timezone': timezone.text,
            'pattern': weekly
                ? {
                    'weeklyRules': [
                      {
                        'days': [day.text.trim().toUpperCase()],
                        'times': [time.text.trim()],
                      },
                    ],
                  }
                : {
                    'weeklyRules': <Object>[],
                    'intervalMinutes': int.tryParse(interval.text) ?? 60,
                  },
            'expectedVersion': (schedule['version'] as num).toInt(),
          },
        ),
      );
    }
  }

  Future<void> _closeMeeting(Map<String, Object?> meeting) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Notulenagent starten'),
        content: const Text(
          'De notulenagent verwerkt de volledige overlegcontext via Agent Runtime. Het overleg sluit pas nadat notulen, antwoorden, besluiten en de geheugenbatch geldig zijn toegepast.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Starten'),
          ),
        ],
      ),
    );
    if (ok == true) {
      await _mutate(
        () => widget.gateway.closeMeeting(
          _value(meeting['id']),
          (meeting['version'] as num).toInt(),
          '',
          null,
        ),
      );
    }
  }

  Future<void> _textAction(
    String title,
    String label,
    Future<void> Function(String) operation,
  ) async {
    final text = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: text,
          minLines: 2,
          maxLines: 8,
          decoration: InputDecoration(labelText: label),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Opslaan'),
          ),
        ],
      ),
    );
    if (ok == true && text.text.trim().isNotEmpty) {
      await _mutate(() => operation(text.text.trim()));
    }
  }

  Future<void> _mutate(Future<void> Function() operation) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await operation();
      await _loadProducts(_selected?.id);
    } on ProductFailure catch (e) {
      if (mounted) {
        setState(() {
          _error = e.status == 409
              ? '${e.message} Ververs het product en probeer opnieuw.'
              : e.message;
          _busy = false;
        });
      }
    }
  }
}
