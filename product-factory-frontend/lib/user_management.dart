import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'configuration.dart';
import 'http_client_factory.dart';
import 'product_workspace.dart';

class UserManagementPage extends StatefulWidget {
  const UserManagementPage({required this.products, this.csrfToken, super.key});

  final ProductGateway products;
  final String? csrfToken;

  @override
  State<UserManagementPage> createState() => _UserManagementPageState();
}

class _UserManagementPageState extends State<UserManagementPage> {
  late final _UserGateway _gateway = _UserGateway(widget.csrfToken);
  List<Map<String, Object?>> _users = const [];
  List<Map<String, Object?>> _history = const [];
  List<ProductSummary> _products = const [];
  bool _busy = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final values = await Future.wait([
        _gateway.users(),
        _gateway.history(),
        widget.products.products(),
      ]);
      if (!mounted) return;
      setState(() {
        _users = values[0] as List<Map<String, Object?>>;
        _history = values[1] as List<Map<String, Object?>>;
        _products = values[2] as List<ProductSummary>;
        _busy = false;
        _error = null;
      });
    } on Object {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = 'Leden konden niet worden geladen.';
        });
      }
    }
  }

  Future<void> _addUser() async {
    final controller = TextEditingController();
    final email = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Gebruiker toevoegen'),
        content: TextField(
          controller: controller,
          autofocus: true,
          keyboardType: TextInputType.emailAddress,
          decoration: const InputDecoration(labelText: 'Google-e-mailadres'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Toevoegen'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (email == null || email.isEmpty) return;
    await _mutate(() => _gateway.create(email));
  }

  Future<void> _grant(Map<String, Object?> user) async {
    String? productId = _products.firstOrNull?.id;
    final selected = await showDialog<String>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: Text('${_value(user['email'])} koppelen'),
          content: DropdownButtonFormField<String>(
            initialValue: productId,
            decoration: const InputDecoration(labelText: 'Product'),
            items: _products
                .map(
                  (product) => DropdownMenuItem(
                    value: product.id,
                    child: Text(product.name),
                  ),
                )
                .toList(),
            onChanged: (value) => update(() => productId = value),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Annuleren'),
            ),
            FilledButton(
              onPressed: productId == null
                  ? null
                  : () => Navigator.pop(context, productId),
              child: const Text('Koppelen'),
            ),
          ],
        ),
      ),
    );
    if (selected == null) return;
    final memberships = (user['memberships'] as List? ?? const [])
        .whereType<Map>()
        .where((membership) => _value(membership['productId']) == selected)
        .toList();
    final expectedVersion = memberships.isEmpty
        ? 0
        : (memberships.single['version'] as num).toInt();
    await _mutate(
      () => _gateway.grant(_value(user['id']), selected, expectedVersion),
    );
  }

  Future<void> _revoke(
    Map<String, Object?> user,
    Map<String, Object?> membership,
  ) async {
    final controller = TextEditingController();
    var confirmed = false;
    final reason = await showDialog<String>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: const Text('Producttoegang intrekken'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: controller,
                minLines: 2,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: 'Verplichte reden',
                ),
              ),
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                value: confirmed,
                onChanged: (value) => update(() => confirmed = value == true),
                title: const Text(
                  'Ik bevestig dat nieuwe toegang direct wordt ingetrokken.',
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Annuleren'),
            ),
            FilledButton(
              onPressed: !confirmed
                  ? null
                  : () => Navigator.pop(context, controller.text.trim()),
              child: const Text('Intrekken'),
            ),
          ],
        ),
      ),
    );
    controller.dispose();
    if (reason == null || reason.isEmpty) return;
    await _mutate(
      () => _gateway.revoke(
        _value(user['id']),
        _value(membership['productId']),
        reason,
        (membership['version'] as num).toInt(),
      ),
    );
  }

  Future<void> _mutate(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
      await _load();
    } on Object {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = 'De wijziging kon niet worden opgeslagen.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    padding: EdgeInsets.all(MediaQuery.sizeOf(context).width < 600 ? 20 : 42),
    child: Align(
      alignment: Alignment.topLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1180),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Leden', style: Theme.of(context).textTheme.displaySmall),
            const SizedBox(height: 8),
            const Text('Beheer product owners en hun directe producttoegang.'),
            const SizedBox(height: 20),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                onPressed: _busy ? null : _addUser,
                icon: const Icon(Icons.person_add_alt),
                label: const Text('Gebruiker toevoegen'),
              ),
            ),
            if (_busy) const LinearProgressIndicator(),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  _error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            ..._users.map((user) {
              final memberships = (user['memberships'] as List? ?? const [])
                  .map((value) => (value as Map).cast<String, Object?>())
                  .toList();
              return Card(
                child: ExpansionTile(
                  leading: const Icon(Icons.person_outline),
                  title: Text(_value(user['email'])),
                  subtitle: Text(
                    (user['globalRoles'] as List? ?? const []).contains(
                          'FACTORY_OWNER',
                        )
                        ? 'Factory owner'
                        : 'Product owner',
                  ),
                  childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  expandedCrossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (memberships.isEmpty)
                      const Text('Nog geen productlidmaatschap.'),
                    ...memberships.map(
                      (membership) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                          _productName(_value(membership['productId'])),
                        ),
                        subtitle: Text(
                          '${membership['role']} · ${membership['status']}${membership['reason'] == null ? '' : '\nReden: ${membership['reason']}'}',
                        ),
                        trailing: membership['status'] == 'ACTIVE'
                            ? TextButton(
                                onPressed: () => _revoke(user, membership),
                                child: const Text('Intrekken'),
                              )
                            : null,
                      ),
                    ),
                    Align(
                      alignment: Alignment.centerRight,
                      child: OutlinedButton.icon(
                        onPressed: _products.isEmpty
                            ? null
                            : () => _grant(user),
                        icon: const Icon(Icons.add),
                        label: const Text('Product koppelen'),
                      ),
                    ),
                  ],
                ),
              );
            }),
            const SizedBox(height: 24),
            Text('Historie', style: Theme.of(context).textTheme.titleLarge),
            if (_history.isEmpty)
              const Text('Nog geen lidmaatschapswijzigingen.'),
            ..._history.map(
              (entry) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(
                  entry['action'] == 'GRANTED'
                      ? Icons.person_add_alt
                      : Icons.person_remove_outlined,
                ),
                title: Text(
                  '${entry['action'] == 'GRANTED' ? 'Toegekend' : 'Ingetrokken'} · ${_productName(_value(entry['productId']))}',
                ),
                subtitle: Text(
                  '${entry['occurredAt']}${entry['reason'] == null ? '' : '\n${entry['reason']}'}',
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );

  String _productName(String id) =>
      _products.where((product) => product.id == id).firstOrNull?.name ?? id;
}

String _value(Object? value) =>
    value is Map ? '${value['value'] ?? ''}' : '${value ?? ''}';

class _UserGateway {
  _UserGateway(this.csrfToken) : _client = createHttpClient();
  final String? csrfToken;
  final http.Client _client;
  int _sequence = 0;
  String get _base => AppConfiguration.backendUrl.replaceAll(RegExp(r'/$'), '');
  Future<List<Map<String, Object?>>> users() => _list('/api/admin/users');
  Future<List<Map<String, Object?>>> history() =>
      _list('/api/admin/users/membership-history');
  Future<void> create(String email) => _send('POST', '/api/admin/users', {
    'email': email,
    'idempotencyKey': _key('create-user'),
  });
  Future<void> grant(
    String userId,
    String productId,
    int expectedVersion,
  ) => _send(
    'PUT',
    '/api/admin/users/$userId/memberships/${Uri.encodeComponent(productId)}',
    {
      'expectedVersion': expectedVersion,
      'idempotencyKey': _key('grant-membership'),
    },
  );
  Future<void> revoke(
    String userId,
    String productId,
    String reason,
    int expectedVersion,
  ) => _send(
    'DELETE',
    '/api/admin/users/$userId/memberships/${Uri.encodeComponent(productId)}',
    {
      'reason': reason,
      'confirmation': true,
      'expectedVersion': expectedVersion,
      'idempotencyKey': _key('revoke-membership'),
    },
  );
  String _key(String prefix) =>
      '$prefix-${DateTime.now().microsecondsSinceEpoch}-${_sequence++}';
  Future<List<Map<String, Object?>>> _list(String path) async =>
      ((await _decode(await _client.get(Uri.parse('$_base$path')))) as List)
          .map((value) => (value as Map).cast<String, Object?>())
          .toList();
  Future<void> _send(
    String method,
    String path,
    Map<String, Object?> body,
  ) async {
    final request = http.Request(method, Uri.parse('$_base$path'))
      ..headers['Content-Type'] = 'application/json'
      ..body = jsonEncode(body);
    if (csrfToken != null) request.headers['X-PF-CSRF'] = csrfToken!;
    await _decode(await http.Response.fromStream(await _client.send(request)));
  }

  Future<Object?> _decode(http.Response response) async {
    final value = response.bodyBytes.isEmpty
        ? null
        : jsonDecode(utf8.decode(response.bodyBytes));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw StateError('HTTP ${response.statusCode}');
    }
    return value;
  }
}
