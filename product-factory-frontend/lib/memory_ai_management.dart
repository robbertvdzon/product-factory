import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'configuration.dart';
import 'http_client_factory.dart';

String _v(Object? value) {
  if (value is String) return value;
  if (value is Map && value['value'] is String) return value['value'] as String;
  return value?.toString() ?? '';
}

abstract interface class MemoryAiGateway {
  Future<List<Map<String, Object?>>> products();
  Future<List<Map<String, Object?>>> roles(String productId);
  Future<Map<String, Object?>> budget(String productId, String role);
  Future<List<Map<String, Object?>>> items(
    String productId,
    String role, {
    DateTime? date,
  });
  Future<List<Map<String, Object?>>> history(
    String productId,
    String role,
    String itemId,
  );
  Future<void> add(
    String productId,
    String role,
    String title,
    String content,
    String reason,
  );
  Future<void> replace(
    String productId,
    String role,
    Map<String, Object?> item,
    String title,
    String content,
    String reason,
  );
  Future<void> retract(
    String productId,
    String role,
    Map<String, Object?> item,
    String reason,
  );
  Future<List<Map<String, Object?>>> aiSettings();
  Future<void> updateAi(
    Map<String, Object?> setting,
    String provider,
    String model,
    bool enabled,
  );
}

abstract interface class AgentRuntimeGateway {
  Future<List<Map<String, Object?>>> aiTasks();
  Future<List<Map<String, Object?>>> environmentCatalog(String projectPrefix);
  Future<List<Map<String, Object?>>> productEnvironmentKeys(String productId);
  Future<void> refreshEnvironmentCatalog(String projectPrefix);
  Future<void> setProductEnvironmentKey(
    String productId,
    String name,
    bool active,
    int expectedVersion,
  );
  Future<void> setAgentEnvironmentGrant(
    String productId,
    String name,
    String role,
    bool granted,
  );
  Future<void> cancelAiTask(String taskId, String reason);
}

class HttpMemoryAiGateway implements MemoryAiGateway, AgentRuntimeGateway {
  HttpMemoryAiGateway({this.csrfToken, http.Client? client, String? backendUrl})
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
  Future<List<Map<String, Object?>>> products() => _list('/api/products');
  @override
  Future<List<Map<String, Object?>>> roles(String productId) => _list(
    '/api/products/${Uri.encodeComponent(productId)}/agent-memory/roles',
  );
  @override
  Future<Map<String, Object?>> budget(String productId, String role) => _map(
    '/api/products/${Uri.encodeComponent(productId)}/agent-memory/roles/${Uri.encodeComponent(role)}/budget',
  );
  @override
  Future<List<Map<String, Object?>>> items(
    String productId,
    String role, {
    DateTime? date,
  }) {
    final suffix = date == null
        ? ''
        : '?date=${date.toIso8601String().substring(0, 10)}';
    return _list(
      '/api/products/${Uri.encodeComponent(productId)}/agent-memory/roles/${Uri.encodeComponent(role)}/items$suffix',
    );
  }

  @override
  Future<List<Map<String, Object?>>> history(
    String productId,
    String role,
    String itemId,
  ) => _list(
    '/api/products/${Uri.encodeComponent(productId)}/agent-memory/roles/${Uri.encodeComponent(role)}/items/${Uri.encodeComponent(itemId)}/history',
  );
  @override
  Future<void> add(
    String productId,
    String role,
    String title,
    String content,
    String reason,
  ) => _send(
    'POST',
    '/api/products/${Uri.encodeComponent(productId)}/agent-memory/roles/${Uri.encodeComponent(role)}/items',
    {
      'title': title,
      'content': content,
      'reason': reason,
      'idempotencyKey': _key('memory-add'),
    },
  );
  @override
  Future<void> replace(
    String productId,
    String role,
    Map<String, Object?> item,
    String title,
    String content,
    String reason,
  ) => _send(
    'POST',
    '/api/products/${Uri.encodeComponent(productId)}/agent-memory/roles/${Uri.encodeComponent(role)}/items/${Uri.encodeComponent(_v(item['id']))}/replace',
    {
      'expectedVersionId': _v(item['activeVersionId']),
      'title': title,
      'content': content,
      'reason': reason,
      'idempotencyKey': _key('memory-replace'),
    },
  );
  @override
  Future<void> retract(
    String productId,
    String role,
    Map<String, Object?> item,
    String reason,
  ) => _send(
    'POST',
    '/api/products/${Uri.encodeComponent(productId)}/agent-memory/roles/${Uri.encodeComponent(role)}/items/${Uri.encodeComponent(_v(item['id']))}/retract',
    {
      'expectedVersionId': _v(item['activeVersionId']),
      'reason': reason,
      'idempotencyKey': _key('memory-retract'),
    },
  );
  @override
  Future<List<Map<String, Object?>>> aiSettings() =>
      _list('/api/ai/job-configurations');
  @override
  Future<void> updateAi(
    Map<String, Object?> setting,
    String provider,
    String model,
    bool enabled,
  ) => _send(
    'PUT',
    '/api/ai/job-configurations/${Uri.encodeComponent(_v(setting['jobKey']))}',
    {
      'provider': provider,
      'model': model,
      'enabled': enabled,
      'expectedVersion': (setting['version'] as num?)?.toInt() ?? 0,
      'idempotencyKey': _key('ai-settings'),
    },
  );

  @override
  Future<List<Map<String, Object?>>> aiTasks() => _list('/api/ai/tasks');
  @override
  Future<List<Map<String, Object?>>> environmentCatalog(
    String projectPrefix,
  ) => _list(
    '/api/ai/environment-catalog?projectPrefix=${Uri.encodeQueryComponent(projectPrefix)}',
  );
  @override
  Future<List<Map<String, Object?>>> productEnvironmentKeys(
    String productId,
  ) => _list(
    '/api/products/${Uri.encodeComponent(productId)}/agent-environment-keys',
  );
  @override
  Future<void> refreshEnvironmentCatalog(String projectPrefix) => _send(
    'POST',
    '/api/ai/environment-catalog/refresh',
    {'projectPrefix': projectPrefix},
  );
  @override
  Future<void> setProductEnvironmentKey(
    String productId,
    String name,
    bool active,
    int expectedVersion,
  ) => _send(
    'PUT',
    '/api/products/${Uri.encodeComponent(productId)}/agent-environment-keys/${Uri.encodeComponent(name)}',
    {
      'active': active,
      'expectedVersion': expectedVersion,
      'idempotencyKey': _key('product-environment-key'),
    },
  );
  @override
  Future<void> setAgentEnvironmentGrant(
    String productId,
    String name,
    String role,
    bool granted,
  ) => _send(
    'PUT',
    '/api/products/${Uri.encodeComponent(productId)}/agent-environment-keys/${Uri.encodeComponent(name)}/roles/${Uri.encodeComponent(role)}',
    {'granted': granted, 'idempotencyKey': _key('agent-key-grant')},
  );
  @override
  Future<void> cancelAiTask(String taskId, String reason) => _send(
    'POST',
    '/api/ai/tasks/${Uri.encodeComponent(taskId)}/cancel',
    {'reason': reason},
  );

  String _key(String prefix) =>
      'ui-$prefix-${DateTime.now().microsecondsSinceEpoch}-${_sequence++}';
  Uri _uri(String path) => Uri.parse('$_backendUrl$path');
  Future<List<Map<String, Object?>>> _list(String path) async =>
      ((_decode(await _client.get(_uri(path)))) as List)
          .map((value) => (value as Map).cast<String, Object?>())
          .toList();
  Future<Map<String, Object?>> _map(String path) async =>
      ((_decode(await _client.get(_uri(path)))) as Map).cast<String, Object?>();
  Future<void> _send(
    String method,
    String path,
    Map<String, Object?> body,
  ) async {
    final headers = <String, String>{'Content-Type': 'application/json'};
    if (csrfToken != null) headers['X-PF-CSRF'] = csrfToken!;
    final request = http.Request(method, _uri(path))
      ..headers.addAll(headers)
      ..body = jsonEncode(body);
    _decode(await http.Response.fromStream(await _client.send(request)));
  }

  Object? _decode(http.Response response) {
    Object? value;
    try {
      value = response.bodyBytes.isEmpty
          ? null
          : jsonDecode(utf8.decode(response.bodyBytes));
    } on FormatException {
      value = null;
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final message = value is Map && value['message'] is String
          ? value['message'] as String
          : 'De aanvraag kon niet worden uitgevoerd.';
      throw MemoryAiFailure(response.statusCode, message);
    }
    return value;
  }
}

class MemoryAiFailure implements Exception {
  const MemoryAiFailure(this.status, this.message);
  final int status;
  final String message;
}

enum MemoryAiView { all, memory, settings, operation }

class MemoryAiManagementPanel extends StatefulWidget {
  const MemoryAiManagementPanel({
    required this.gateway,
    this.view = MemoryAiView.all,
    super.key,
  });
  final MemoryAiGateway gateway;
  final MemoryAiView view;
  @override
  State<MemoryAiManagementPanel> createState() =>
      _MemoryAiManagementPanelState();
}

class _MemoryAiManagementPanelState extends State<MemoryAiManagementPanel> {
  List<Map<String, Object?>> _products = const [];
  List<Map<String, Object?>> _roles = const [];
  List<Map<String, Object?>> _items = const [];
  List<Map<String, Object?>> _ai = const [];
  List<Map<String, Object?>> _tasks = const [];
  List<Map<String, Object?>> _catalog = const [];
  List<Map<String, Object?>> _productKeys = const [];
  Map<String, Object?>? _budget;
  String? _productId;
  String? _role;
  String _projectPrefix = 'HKH';
  String? _projectPrefixError;
  DateTime? _date;
  String? _error;
  bool _busy = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    _start();
    try {
      final showMemory =
          widget.view == MemoryAiView.all || widget.view == MemoryAiView.memory;
      final showSettings =
          widget.view == MemoryAiView.all ||
          widget.view == MemoryAiView.settings;
      final showOperation =
          widget.view == MemoryAiView.all ||
          widget.view == MemoryAiView.operation;
      final values = await Future.wait([
        if (showMemory || showSettings)
          widget.gateway.products()
        else
          Future.value(<Map<String, Object?>>[]),
        if (showSettings)
          widget.gateway.aiSettings()
        else
          Future.value(<Map<String, Object?>>[]),
        if (showOperation)
          if (widget.gateway case final AgentRuntimeGateway runtime)
            runtime.aiTasks(),
      ]);
      final products = values[0];
      final selected =
          _productId ?? (products.isEmpty ? null : _v(products.first['id']));
      final roles = selected == null || (!showMemory && !showSettings)
          ? <Map<String, Object?>>[]
          : await widget.gateway.roles(selected);
      final role = roles.any((r) => _v(r['key']) == _role)
          ? _role
          : (roles.isEmpty ? null : _v(roles.first['key']));
      final scoped = showMemory
          ? await _loadScope(selected, role)
          : (null, const <Map<String, Object?>>[]);
      final runtimeValues =
          showSettings &&
              widget.gateway is AgentRuntimeGateway &&
              selected != null
          ? await Future.wait([
              (widget.gateway as AgentRuntimeGateway).environmentCatalog(
                _projectPrefix,
              ),
              (widget.gateway as AgentRuntimeGateway).productEnvironmentKeys(
                selected,
              ),
            ])
          : const <List<Map<String, Object?>>>[];
      if (!mounted) return;
      setState(() {
        _products = products;
        _ai = values[1];
        _tasks = values.length > 2 ? values[2] : const [];
        _catalog = runtimeValues.isEmpty ? const [] : runtimeValues[0];
        _productKeys = runtimeValues.isEmpty ? const [] : runtimeValues[1];
        _productId = selected;
        _roles = roles;
        _role = role;
        _budget = scoped.$1;
        _items = scoped.$2;
      });
    } catch (error) {
      _showError(error);
    } finally {
      _finish();
    }
  }

  Future<(Map<String, Object?>?, List<Map<String, Object?>>)> _loadScope(
    String? product,
    String? role,
  ) async {
    if (product == null || role == null) {
      return (null, const <Map<String, Object?>>[]);
    }
    final values = await Future.wait([
      widget.gateway.budget(product, role),
      widget.gateway.items(product, role, date: _date),
    ]);
    return (
      values[0] as Map<String, Object?>,
      values[1] as List<Map<String, Object?>>,
    );
  }

  Future<void> _selectProduct(String? value) async {
    if (value == null) return;
    _start();
    try {
      final roles = await widget.gateway.roles(value);
      final role = roles.isEmpty ? null : _v(roles.first['key']);
      final scoped =
          widget.view == MemoryAiView.all || widget.view == MemoryAiView.memory
          ? await _loadScope(value, role)
          : (null, const <Map<String, Object?>>[]);
      final productKeys =
          (widget.view == MemoryAiView.all ||
                  widget.view == MemoryAiView.settings) &&
              widget.gateway is AgentRuntimeGateway
          ? await (widget.gateway as AgentRuntimeGateway)
                .productEnvironmentKeys(value)
          : <Map<String, Object?>>[];
      if (mounted) {
        setState(() {
          _productId = value;
          _roles = roles;
          _role = role;
          _budget = scoped.$1;
          _items = scoped.$2;
          _productKeys = productKeys;
        });
      }
    } catch (error) {
      _showError(error);
    } finally {
      _finish();
    }
  }

  Future<void> _selectRole(String? value) async {
    if (value == null || _productId == null) return;
    if (widget.view == MemoryAiView.settings) {
      setState(() => _role = value);
      return;
    }
    _start();
    try {
      final scoped = await _loadScope(_productId, value);
      if (mounted) {
        setState(() {
          _role = value;
          _budget = scoped.$1;
          _items = scoped.$2;
        });
      }
    } catch (error) {
      _showError(error);
    } finally {
      _finish();
    }
  }

  @override
  Widget build(BuildContext context) {
    final showMemory =
        widget.view == MemoryAiView.all || widget.view == MemoryAiView.memory;
    final showSettings =
        widget.view == MemoryAiView.all || widget.view == MemoryAiView.settings;
    final showOperation =
        widget.view == MemoryAiView.all ||
        widget.view == MemoryAiView.operation;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (showMemory) ...[
          const Divider(height: 48),
          Text(
            'Agentgeheugen',
            style: Theme.of(context).textTheme.headlineMedium,
          ),
          const SizedBox(height: 6),
          const Text(
            'Beheer actuele kennis en de volledige, append-only historie per product, capability en vertrouwde agentrol.',
          ),
          const SizedBox(height: 16),
        ],
        if (_error != null)
          Card(
            color: Theme.of(context).colorScheme.errorContainer,
            child: ListTile(
              title: Text(_error!),
              trailing: TextButton(
                onPressed: _load,
                child: const Text('Opnieuw'),
              ),
            ),
          ),
        if (_busy) const LinearProgressIndicator(),
        if (showMemory) ...[const SizedBox(height: 12), _scopeSelector()],
        if (showMemory && _roleDefinition != null) ...[
          const SizedBox(height: 12),
          Card(
            child: ListTile(
              leading: const Icon(Icons.smart_toy_outlined),
              title: Text(
                '${_roleDefinition!['displayName']} · ${_roleDefinition!['implementationVariant']}',
              ),
              subtitle: Text(
                '${_roleDefinition!['purpose']}\nGrenzen: ${(_roleDefinition!['boundaries'] as List? ?? const []).join(' · ')}',
              ),
            ),
          ),
        ],
        if (showMemory && _budget != null) ...[
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              Chip(
                label: Text(
                  '${_budget!['usedItems']}/${_budget!['maximumActiveItems']} actieve items',
                ),
              ),
              Chip(
                label: Text(
                  '${_budget!['usedCharacters']}/${_budget!['maximumTotalCharacters']} tekens',
                ),
              ),
              Chip(
                label: Text(
                  'max. ${_budget!['maximumItemCharacters']} tekens per item',
                ),
              ),
            ],
          ),
        ],
        if (showMemory) const SizedBox(height: 12),
        if (showMemory)
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton.icon(
                onPressed: _pickDate,
                icon: const Icon(Icons.history),
                label: Text(
                  _date == null
                      ? 'Actuele toestand'
                      : 'Geldig op ${_date!.toIso8601String().substring(0, 10)}',
                ),
              ),
              if (_date != null)
                TextButton(
                  onPressed: () {
                    setState(() => _date = null);
                    _selectRole(_role);
                  },
                  child: const Text('Terug naar actueel'),
                ),
              FilledButton.icon(
                onPressed: _role == null || _date != null ? null : _add,
                icon: const Icon(Icons.add),
                label: const Text('Geheugenitem toevoegen'),
              ),
            ],
          ),
        if (showMemory) const SizedBox(height: 8),
        if (showMemory && _items.isEmpty && !_busy)
          const Card(
            child: ListTile(
              title: Text('Geen geheugenitems voor deze selectie.'),
            ),
          ),
        if (showMemory) ..._items.map(_memoryCard),
        if (showSettings) ...[
          const Divider(height: 48),
          Text(
            'AI-modellen',
            style: Theme.of(context).textTheme.headlineMedium,
          ),
          const SizedBox(height: 6),
          const Text(
            'Geldt voor alle producten. Iedere taak loopt duurzaam via de gedeelde Agent Runtime.',
          ),
          const SizedBox(height: 12),
          ..._ai.map(_aiCard),
        ],
        if (showSettings && widget.gateway is AgentRuntimeGateway) ...[
          const Divider(height: 48),
          Text(
            'Agenttoegang · $_projectPrefix',
            style: Theme.of(context).textTheme.headlineMedium,
          ),
          const SizedBox(height: 6),
          const Text(
            'Koppel uitsluitend bekende Runtime-keynamen aan dit product en geef per agentrol expliciet toegang.',
          ),
          const SizedBox(height: 12),
          _scopeSelector(),
          const SizedBox(height: 12),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              SizedBox(
                width: 320,
                child: TextFormField(
                  initialValue: _projectPrefix,
                  enabled: !_busy,
                  decoration: InputDecoration(
                    labelText: 'Runtime-projectprefix',
                    helperText: 'Bijvoorbeeld HKH of HKH_AUTOPILOT',
                    errorText: _projectPrefixError,
                    border: const OutlineInputBorder(),
                  ),
                  textCapitalization: TextCapitalization.characters,
                  onChanged: (value) => setState(() {
                    _projectPrefix = value;
                    _projectPrefixError = null;
                  }),
                  onFieldSubmitted: (_) => _refreshCatalog(),
                ),
              ),
              OutlinedButton.icon(
                onPressed: _busy ? null : _refreshCatalog,
                icon: const Icon(Icons.sync),
                label: const Text('Runtime-catalogus verversen'),
              ),
            ],
          ),
          const SizedBox(height: 6),
          const Text(
            'Alleen namen en beschikbaarheid worden getoond. Waarden verlaten Agent Runtime nooit.',
          ),
          const SizedBox(height: 12),
          if (_catalog.isEmpty)
            const Card(
              child: ListTile(
                leading: Icon(Icons.key_off_outlined),
                title: Text('Nog geen Runtime-keynamen geladen'),
              ),
            ),
          ..._catalog.map(_environmentKeyCard),
        ],
        if (showOperation && widget.gateway is AgentRuntimeGateway) ...[
          const Divider(height: 48),
          Text(
            'AI-taakoperatie',
            style: Theme.of(context).textTheme.headlineMedium,
          ),
          const SizedBox(height: 6),
          const Text(
            'Domeincorrelatie en veilige voortgang staan hier; workers en technische attempts staan in de Runtime-monitor.',
          ),
          const SizedBox(height: 12),
          if (_tasks.isEmpty)
            const Card(child: ListTile(title: Text('Nog geen AI-taken.'))),
          ..._tasks.map(_taskCard),
        ],
      ],
    );
  }

  Widget _scopeSelector() => Wrap(
    spacing: 16,
    runSpacing: 12,
    children: [
      SizedBox(
        width: 360,
        child: DropdownButtonFormField<String>(
          isExpanded: true,
          initialValue: _productId,
          decoration: const InputDecoration(
            labelText: 'Product',
            border: OutlineInputBorder(),
          ),
          items: _products
              .map(
                (p) => DropdownMenuItem(
                  value: _v(p['id']),
                  child: Text('${p['name']} · ${_v(p['id'])}'),
                ),
              )
              .toList(),
          onChanged: _busy ? null : _selectProduct,
        ),
      ),
      SizedBox(
        width: 360,
        child: DropdownButtonFormField<String>(
          isExpanded: true,
          initialValue: _role,
          decoration: const InputDecoration(
            labelText: 'Capability · agentrol',
            border: OutlineInputBorder(),
          ),
          items: _roles
              .map(
                (r) => DropdownMenuItem(
                  value: _v(r['key']),
                  child: Text('${r['capability']} · ${r['displayName']}'),
                ),
              )
              .toList(),
          onChanged: _busy ? null : _selectRole,
        ),
      ),
    ],
  );

  Map<String, Object?>? get _roleDefinition =>
      _roles.where((r) => _v(r['key']) == _role).firstOrNull;

  Widget _memoryCard(Map<String, Object?> item) {
    final actor = (item['actor'] as Map?)?.cast<String, Object?>() ?? const {};
    final reads = item['readBy'] as List? ?? const [];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${item['title']}',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 6),
                      Text('${item['content']}'),
                      const SizedBox(height: 8),
                      Text(
                        'Versie ${_v(item['activeVersionId'])} · geldig vanaf ${item['validFrom']}',
                      ),
                      Text(
                        'Bron: ${actor['type'] ?? 'ONBEKEND'} · ${actor['id'] ?? '-'} · reden: ${item['reason']}',
                      ),
                      Text(
                        item['sourceMeetingId'] == null
                            ? 'Geen bronoverleg · ${reads.length} geregistreerde lezing(en)'
                            : 'Bronoverleg ${_v(item['sourceMeetingId'])} · ${reads.length} geregistreerde lezing(en)',
                      ),
                    ],
                  ),
                ),
                PopupMenuButton<String>(
                  onSelected: (value) {
                    if (value == 'history') _history(item);
                    if (value == 'replace') _replace(item);
                    if (value == 'retract') _retract(item);
                  },
                  itemBuilder: (_) => [
                    const PopupMenuItem(
                      value: 'history',
                      child: Text('Volledige historie'),
                    ),
                    if (_date == null)
                      const PopupMenuItem(
                        value: 'replace',
                        child: Text('Vervangen'),
                      ),
                    if (_date == null)
                      const PopupMenuItem(
                        value: 'retract',
                        child: Text('Intrekken'),
                      ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _aiCard(Map<String, Object?> setting) {
    final updatedBy =
        (setting['updatedBy'] as Map?)?.cast<String, Object?>() ?? const {};
    return Card(
      child: ListTile(
        leading: Icon(
          setting['enabled'] == true ? Icons.toggle_on : Icons.toggle_off,
          size: 36,
        ),
        title: Text('${setting['displayName']} · ${_v(setting['jobKey'])}'),
        subtitle: Text(
          '${setting['provider']} / ${setting['model']} · versie ${setting['version']}\nGewijzigd door ${updatedBy['type'] ?? 'SYSTEM'} · ${updatedBy['id'] ?? 'trusted-default'} op ${setting['updatedAt']}',
        ),
        trailing: IconButton(
          onPressed: () => _editAi(setting),
          tooltip: 'AI-model wijzigen',
          icon: const Icon(Icons.edit_outlined),
        ),
      ),
    );
  }

  Widget _environmentKeyCard(Map<String, Object?> catalogKey) {
    final name = '${catalogKey['name']}';
    final configured = _productKeys
        .where((key) => key['name'] == name)
        .firstOrNull;
    final active = configured?['active'] == true;
    final grants = (configured?['grantedAgentRoles'] as List? ?? const [])
        .map((value) => '$value')
        .toSet();
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SwitchListTile(
              value: active,
              onChanged: _productId == null
                  ? null
                  : (value) => _setProductKey(
                      name,
                      value,
                      (configured?['version'] as num?)?.toInt() ?? 0,
                    ),
              title: Text(name),
              subtitle: Text(
                catalogKey['available'] == true
                    ? '${catalogKey['matchingOnlineWorkers']} passende online worker(s)'
                    : 'Bekend, maar nu niet beschikbaar bij een online worker',
              ),
              secondary: Icon(
                catalogKey['available'] == true
                    ? Icons.key_outlined
                    : Icons.key_off_outlined,
              ),
            ),
            if (active)
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: _roles.map((role) {
                  final key = _v(role['key']);
                  final granted = grants.contains(key);
                  return FilterChip(
                    selected: granted,
                    label: Text('${role['displayName']}'),
                    onSelected: (value) => _setGrant(name, key, value),
                  );
                }).toList(),
              ),
          ],
        ),
      ),
    );
  }

  Widget _taskCard(Map<String, Object?> task) {
    final status = '${task['status']}';
    final terminal = const {
      'SUCCEEDED',
      'FAILED',
      'CANCELLED',
    }.contains(status);
    return Card(
      child: ListTile(
        leading: Icon(
          status == 'SUCCEEDED'
              ? Icons.check_circle_outline
              : status == 'FAILED'
              ? Icons.error_outline
              : status == 'CANCELLED'
              ? Icons.cancel_outlined
              : Icons.pending_outlined,
        ),
        title: Text('${_v(task['jobKey'])} · $status'),
        subtitle: Text(
          'Product ${_v(task['productId']).isEmpty ? '—' : _v(task['productId'])} · rol ${task['agentRole']}\n'
          'Runtime ${task['runtimeJobId'] ?? 'nog niet ingediend'} · fase ${task['runtimePhase'] ?? 'outbox'} · attemptprojectie ${task['runtimeAttemptCount']}\n'
          '${task['safeProgress'] ?? task['errorCode'] ?? 'Geen aanvullende veilige voortgang'}',
        ),
        trailing: terminal
            ? null
            : IconButton(
                tooltip: 'Taak annuleren',
                icon: const Icon(Icons.stop_circle_outlined),
                onPressed: () => _cancelTask(_v(task['id'])),
              ),
      ),
    );
  }

  Future<void> _refreshCatalog() async {
    final prefix = _projectPrefix.trim();
    if (!RegExp(r'^[A-Z][A-Z0-9_]*$').hasMatch(prefix)) {
      setState(
        () => _projectPrefixError =
            'Gebruik alleen hoofdletters, cijfers en underscores.',
      );
      return;
    }
    setState(() {
      _projectPrefix = prefix;
      _projectPrefixError = null;
    });
    await _mutate(
      () => (widget.gateway as AgentRuntimeGateway).refreshEnvironmentCatalog(
        prefix,
      ),
    );
  }

  Future<void> _setProductKey(String name, bool active, int expectedVersion) =>
      _mutate(
        () => (widget.gateway as AgentRuntimeGateway).setProductEnvironmentKey(
          _productId!,
          name,
          active,
          expectedVersion,
        ),
      );

  Future<void> _setGrant(String name, String role, bool granted) => _mutate(
    () => (widget.gateway as AgentRuntimeGateway).setAgentEnvironmentGrant(
      _productId!,
      name,
      role,
      granted,
    ),
  );

  Future<void> _cancelTask(String taskId) async {
    final reason = await _reasonDialog(
      'AI-taak annuleren',
      'Waarom wordt deze taak geannuleerd?',
    );
    if (reason == null) return;
    await _mutate(
      () =>
          (widget.gateway as AgentRuntimeGateway).cancelAiTask(taskId, reason),
    );
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime.now(),
      initialDate: _date ?? DateTime.now(),
    );
    if (picked == null) return;
    setState(() => _date = picked);
    await _selectRole(_role);
  }

  Future<void> _add() async {
    final input = await _memoryDialog(title: 'Geheugenitem toevoegen');
    if (input == null || _productId == null || _role == null) return;
    await _mutate(
      () =>
          widget.gateway.add(_productId!, _role!, input.$1, input.$2, input.$3),
    );
  }

  Future<void> _replace(Map<String, Object?> item) async {
    final input = await _memoryDialog(
      title: 'Geheugenitem vervangen',
      initialTitle: '${item['title']}',
      initialContent: '${item['content']}',
    );
    if (input == null) return;
    await _mutate(
      () => widget.gateway.replace(
        _productId!,
        _role!,
        item,
        input.$1,
        input.$2,
        input.$3,
      ),
    );
  }

  Future<void> _retract(Map<String, Object?> item) async {
    final input = await _reasonDialog(
      'Geheugenitem intrekken',
      'Waarom is dit item niet meer geldig?',
    );
    if (input == null) return;
    await _mutate(
      () => widget.gateway.retract(_productId!, _role!, item, input),
    );
  }

  Future<void> _history(Map<String, Object?> item) async {
    try {
      final rows = await widget.gateway.history(
        _productId!,
        _role!,
        _v(item['id']),
      );
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text('Historie · ${item['title']}'),
          content: SizedBox(
            width: 680,
            child: ListView(
              shrinkWrap: true,
              children: rows.map((row) {
                final actor =
                    (row['actor'] as Map?)?.cast<String, Object?>() ?? const {};
                return ListTile(
                  isThreeLine: true,
                  title: Text(
                    'Versie ${row['versionNumber']} · ${row['status']}',
                  ),
                  subtitle: Text(
                    '${row['title']}\n${row['content']}\n${row['validFrom']} — ${row['validUntil'] ?? 'heden'} · ${actor['type']}/${actor['id']} · ${row['reason']}',
                  ),
                );
              }).toList(),
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
    } catch (error) {
      _showError(error);
    }
  }

  Future<(String, String, String)?> _memoryDialog({
    required String title,
    String initialTitle = '',
    String initialContent = '',
  }) async {
    final titleController = TextEditingController(text: initialTitle);
    final contentController = TextEditingController(text: initialContent);
    final reasonController = TextEditingController();
    final result = await showDialog<(String, String, String)>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 600,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: titleController,
                decoration: const InputDecoration(labelText: 'Titel'),
              ),
              TextField(
                controller: contentController,
                maxLines: 5,
                decoration: const InputDecoration(labelText: 'Inhoud'),
              ),
              TextField(
                controller: reasonController,
                maxLines: 2,
                decoration: const InputDecoration(labelText: 'Wijzigingsreden'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () {
              if (titleController.text.trim().isEmpty ||
                  contentController.text.trim().isEmpty ||
                  reasonController.text.trim().isEmpty) {
                return;
              }
              Navigator.pop(context, (
                titleController.text.trim(),
                contentController.text.trim(),
                reasonController.text.trim(),
              ));
            },
            child: const Text('Opslaan'),
          ),
        ],
      ),
    );
    titleController.dispose();
    contentController.dispose();
    reasonController.dispose();
    return result;
  }

  Future<String?> _reasonDialog(String title, String label) async {
    final controller = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          maxLines: 3,
          decoration: InputDecoration(labelText: label),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () {
              if (controller.text.trim().isNotEmpty) {
                Navigator.pop(context, controller.text.trim());
              }
            },
            child: const Text('Intrekken'),
          ),
        ],
      ),
    );
    controller.dispose();
    return result;
  }

  Future<void> _editAi(Map<String, Object?> setting) async {
    var provider = '${setting['provider']}';
    var model = '${setting['model']}';
    var enabled = setting['enabled'] == true;
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text('${setting['displayName']} wijzigen'),
          content: SizedBox(
            width: 520,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Align(
                  alignment: Alignment.centerLeft,
                  child: Text('Geldt voor alle producten.'),
                ),
                DropdownButtonFormField<String>(
                  initialValue: provider,
                  decoration: const InputDecoration(labelText: 'Provider'),
                  items: const ['CODEX', 'CLAUDE', 'MOCKED']
                      .map(
                        (value) =>
                            DropdownMenuItem(value: value, child: Text(value)),
                      )
                      .toList(),
                  onChanged: (value) {
                    if (value != null) setDialogState(() => provider = value);
                  },
                ),
                TextFormField(
                  initialValue: model,
                  decoration: const InputDecoration(
                    labelText: 'Model of mockprofiel',
                  ),
                  onChanged: (value) => model = value,
                ),
                SwitchListTile(
                  value: enabled,
                  title: const Text('Ingeschakeld'),
                  onChanged: (value) => setDialogState(() => enabled = value),
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
      ),
    );
    if (accepted == true) {
      await _mutate(
        () => widget.gateway.updateAi(setting, provider, model.trim(), enabled),
      );
    }
  }

  Future<void> _mutate(Future<void> Function() operation) async {
    _start();
    try {
      await operation();
      await _load();
    } catch (error) {
      _showError(error);
    } finally {
      _finish();
    }
  }

  void _start() {
    if (mounted) {
      setState(() {
        _busy = true;
        _error = null;
      });
    }
  }

  void _finish() {
    if (mounted) setState(() => _busy = false);
  }

  void _showError(Object error) {
    if (!mounted) return;
    setState(
      () => _error = error is MemoryAiFailure
          ? (error.status == 409
                ? '${error.message} Vernieuw de gegevens en probeer opnieuw.'
                : error.message)
          : 'Beheergegevens konden niet worden geladen.',
    );
  }
}
