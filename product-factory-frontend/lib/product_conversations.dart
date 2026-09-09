import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'configuration.dart';
import 'http_client_factory.dart';
import 'product_workspace.dart';

class ProductConversationsPage extends StatefulWidget {
  const ProductConversationsPage({
    required this.products,
    this.initialProductId,
    this.initialConversationId,
    this.onProductSelected,
    this.onConversationSelected,
    this.onOpenDesign,
    this.onOpenQuestions,
    this.csrfToken,
    super.key,
  });

  final ProductGateway products;
  final String? initialProductId;
  final String? initialConversationId;
  final ValueChanged<String>? onProductSelected;
  final ValueChanged<String?>? onConversationSelected;
  final ValueChanged<String>? onOpenDesign;
  final ValueChanged<String>? onOpenQuestions;
  final String? csrfToken;

  @override
  State<ProductConversationsPage> createState() =>
      _ProductConversationsPageState();
}

class _ProductConversationsPageState extends State<ProductConversationsPage> {
  late final _AdvisorGateway _advisor = _AdvisorGateway(widget.csrfToken);
  final _message = TextEditingController();
  List<ProductSummary> _products = const [];
  List<Map<String, Object?>> _conversations = const [];
  List<Map<String, Object?>> _actions = const [];
  List<Map<String, Object?>> _notifications = const [];
  Map<String, Object?>? _selected;
  String? _productId;
  String? _error;
  bool _busy = true;
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    unawaited(_loadProducts());
    _poll = Timer.periodic(const Duration(seconds: 3), (_) {
      if (_selected != null) unawaited(_loadConversation(silent: true));
    });
  }

  @override
  void dispose() {
    _poll?.cancel();
    _message.dispose();
    super.dispose();
  }

  Future<void> _loadProducts() async {
    try {
      final products = await widget.products.products();
      final selected =
          products.where((p) => p.id == widget.initialProductId).firstOrNull ??
          products.firstOrNull;
      if (!mounted) return;
      setState(() {
        _products = products;
        _productId = selected?.id;
        _busy = false;
      });
      if (selected != null) await _loadList();
      final initialConversationId = widget.initialConversationId;
      if (initialConversationId != null && initialConversationId.isNotEmpty) {
        final conversation = await _advisor.conversation(initialConversationId);
        if (mounted) setState(() => _selected = conversation);
      }
      await _loadPersonal();
    } on Object {
      if (mounted) {
        setState(() {
          _error = 'Producten konden niet worden geladen.';
          _busy = false;
        });
      }
    }
  }

  Future<void> _loadPersonal() async {
    try {
      final values = await Future.wait([
        _advisor.actions(),
        _advisor.notifications(),
      ]);
      if (!mounted) return;
      setState(() {
        _actions = values[0];
        _notifications = values[1];
      });
    } on Object {
      // De gesprekken blijven bruikbaar wanneer alleen de persoonlijke projectie tijdelijk faalt.
    }
  }

  Future<void> _loadList() async {
    final productId = _productId;
    if (productId == null) return;
    try {
      final conversations = await _advisor.conversations(productId);
      if (!mounted) return;
      setState(() {
        _conversations = conversations;
        _error = null;
      });
    } on _AdvisorFailure catch (failure) {
      if (mounted) setState(() => _error = failure.message);
    }
  }

  Future<void> _loadConversation({bool silent = false}) async {
    final id = _selected?['id'];
    if (id == null) return;
    try {
      final next = await _advisor.conversation(_value(id));
      if (!mounted ||
          (_selected != null &&
              _value(_selected!['id']) != _value(next['id']))) {
        return;
      }
      if (jsonEncode(next) != jsonEncode(_selected)) {
        setState(() => _selected = next);
      }
      if (!silent) await _loadList();
    } on _AdvisorFailure catch (failure) {
      if (mounted && !silent) setState(() => _error = failure.message);
    }
  }

  Future<void> _newConversation() async {
    final title = await _ask('Nieuw gesprek', 'Waar wil je over overleggen?');
    if (title == null || _productId == null) return;
    final id = await _advisor.createConversation(_productId!, title);
    await _loadList();
    _selected = {'id': id};
    widget.onConversationSelected?.call(id);
    await _loadConversation();
  }

  Future<void> _send() async {
    final text = _message.text.trim();
    final selected = _selected;
    if (text.isEmpty || selected == null) return;
    _message.clear();
    try {
      await _advisor.message(
        _value(selected['id']),
        (selected['version'] as num).toInt(),
        text,
      );
      await _loadConversation();
    } on _AdvisorFailure catch (failure) {
      _message.text = text;
      if (mounted) setState(() => _error = failure.message);
    }
  }

  Future<String?> _ask(String title, String label) async {
    final controller = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 4,
          decoration: InputDecoration(labelText: label),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Verder'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_busy) return const Center(child: CircularProgressIndicator());
    if (_products.isEmpty) {
      return const Center(
        child: SelectableText(
          'Je hebt nog geen product. Vraag de factory owner om toegang.',
        ),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final compact = constraints.maxWidth < 760;
        final list = _conversationList();
        final chat = _selected == null
            ? const Center(
                child: Text('Kies een gesprek of start een nieuw gesprek.'),
              )
            : _chat();
        return Padding(
          padding: EdgeInsets.all(compact ? 12 : 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Gesprekken',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 4),
              const Text(
                'Vraag de Productadviseur hoe het product werkt of onderzoek samen een verandering.',
              ),
              if (_actions.isNotEmpty ||
                  _notifications.any((item) => item['readAt'] == null)) ...[
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  children: [
                    ActionChip(
                      avatar: const Icon(Icons.task_alt, size: 18),
                      label: Text(
                        '${_actions.length} ${_actions.length == 1 ? 'actie' : 'acties'} voor jou',
                      ),
                      onPressed: _showActions,
                    ),
                    ActionChip(
                      avatar: const Icon(Icons.notifications_none, size: 18),
                      label: Text(
                        '${_notifications.where((item) => item['readAt'] == null).length} ongelezen',
                      ),
                      onPressed: _showNotifications,
                    ),
                  ],
                ),
              ],
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    _error!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      initialValue: _productId,
                      items: _products
                          .map(
                            (p) => DropdownMenuItem(
                              value: p.id,
                              child: Text(p.name),
                            ),
                          )
                          .toList(),
                      onChanged: (id) {
                        if (id == null) return;
                        setState(() {
                          _productId = id;
                          _selected = null;
                        });
                        widget.onProductSelected?.call(id);
                        unawaited(_loadList());
                      },
                      decoration: const InputDecoration(labelText: 'Product'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  FilledButton.icon(
                    onPressed: _newConversation,
                    icon: const Icon(Icons.add),
                    label: const Text('Nieuw gesprek'),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Expanded(
                child: compact
                    ? (_selected == null ? list : chat)
                    : Row(
                        children: [
                          SizedBox(width: 310, child: list),
                          const VerticalDivider(width: 24),
                          Expanded(child: chat),
                        ],
                      ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _conversationList() => Card(
    child: ListView(
      padding: const EdgeInsets.all(8),
      children: _conversations.isEmpty
          ? const [
              Padding(
                padding: EdgeInsets.all(16),
                child: Text('Nog geen gesprekken.'),
              ),
            ]
          : _conversations
                .map(
                  (conversation) => ListTile(
                    selected:
                        _value(_selected?['id']) == _value(conversation['id']),
                    title: Text(_value(conversation['title'])),
                    subtitle: Text(
                      '${_statusLabel(_value(conversation['status']))} · ${conversation['updatedAt']}',
                    ),
                    onTap: () {
                      setState(() => _selected = conversation);
                      widget.onConversationSelected?.call(
                        _value(conversation['id']),
                      );
                      unawaited(_loadConversation());
                    },
                  ),
                )
                .toList(),
    ),
  );

  Widget _chat() {
    final conversation = _selected!;
    final messages = (conversation['messages'] as List? ?? const [])
        .cast<Map>();
    final closed = conversation['status'] == 'CLOSED';
    final request = conversation['request'] as Map?;
    return Card(
      child: Column(
        children: [
          ListTile(
            title: Text(_value(conversation['title'])),
            subtitle: Text(_statusLabel(_value(conversation['status']))),
            leading: IconButton(
              onPressed: () {
                setState(() => _selected = null);
                widget.onConversationSelected?.call(null);
              },
              icon: const Icon(Icons.arrow_back),
              tooltip: 'Terug',
            ),
            trailing: closed
                ? const Chip(label: Text('Gesloten'))
                : TextButton(
                    onPressed: () async {
                      await _advisor.close(
                        _value(conversation['id']),
                        (conversation['version'] as num).toInt(),
                      );
                      await _loadConversation();
                    },
                    child: const Text('Sluiten'),
                  ),
          ),
          const Divider(height: 1),
          if (conversation['status'] == 'BLOCKED')
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
              child: Row(
                children: [
                  const Expanded(
                    child: Text(
                      'De Productadviseur is geblokkeerd. Je kunt dezelfde bevroren beurt opnieuw proberen.',
                    ),
                  ),
                  const SizedBox(width: 8),
                  FilledButton.tonal(
                    onPressed: () async {
                      await _advisor.retry(
                        _value(conversation['id']),
                        (conversation['version'] as num).toInt(),
                      );
                      await _loadConversation();
                    },
                    child: const Text('Opnieuw'),
                  ),
                ],
              ),
            ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                ...messages.map((raw) {
                  final message = raw.cast<String, Object?>();
                  final advisor = message['sender'] == 'PRODUCT_ADVISOR';
                  return Align(
                    alignment: advisor
                        ? Alignment.centerLeft
                        : Alignment.centerRight,
                    child: Container(
                      constraints: const BoxConstraints(maxWidth: 640),
                      margin: const EdgeInsets.only(bottom: 10),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: advisor
                            ? const Color(0xffeef3f0)
                            : const Color(0xffd8f3e7),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: SelectableText(_value(message['text'])),
                    ),
                  );
                }),
                if (request != null)
                  _proposalCard(request.cast<String, Object?>()),
              ],
            ),
          ),
          if (!closed)
            Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: TextField(
                      controller: _message,
                      minLines: 1,
                      maxLines: 6,
                      decoration: const InputDecoration(
                        labelText: 'Bericht aan de Productadviseur',
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    onPressed: conversation['status'] == 'PROCESSING'
                        ? null
                        : _send,
                    icon: const Icon(Icons.send),
                    tooltip: 'Versturen',
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _proposalCard(Map<String, Object?> request) {
    final content = (request['content'] as Map).cast<String, Object?>();
    final approved = request['status'] != 'PROPOSED';
    return Card(
      color: const Color(0xfffff7df),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Wijzigingsvoorstel · ${_value(content['type'])}',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            if (content['type'] == 'HOTFIX')
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text(
                  'Deze route gebruikt de verkorte bestaande Software Factory-keten: hotfix, merge en deploy.',
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
            const SizedBox(height: 8),
            SelectableText(_value(content['title'])),
            const SizedBox(height: 4),
            SelectableText(_value(content['summary'])),
            const SizedBox(height: 8),
            Text('Probleem: ${_value(content['problem'])}'),
            Text('Gewenst: ${_value(content['desiredBehavior'])}'),
            const SizedBox(height: 8),
            const Text(
              'Acceptatiecriteria',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
            ...((content['acceptanceCriteria'] as List? ?? const []).map(
              (e) => Text('• $e'),
            )),
            const SizedBox(height: 12),
            if (!approved)
              Wrap(
                spacing: 8,
                children: [
                  OutlinedButton(
                    onPressed: () {
                      _message.text = 'Pas het voorstel als volgt aan: ';
                    },
                    child: const Text('Aanpassen'),
                  ),
                  TextButton(
                    onPressed: () async {
                      await _advisor.cancelRequest(
                        _value(request['id']),
                        (request['version'] as num).toInt(),
                      );
                      await _loadConversation();
                    },
                    child: const Text('Niet uitvoeren'),
                  ),
                  FilledButton(
                    onPressed: () async {
                      await _advisor.approveRequest(
                        _value(request['id']),
                        (request['currentVersion'] as num).toInt(),
                        (request['version'] as num).toInt(),
                      );
                      await _loadConversation();
                    },
                    child: const Text('Bevestigen'),
                  ),
                ],
              )
            else ...[
              Text(
                'Status: ${_value(request['status'])} · uitvoering ${_value(request['deliveryStatus'])}${request['externalStoryKey'] == null ? '' : ' · ${request['externalStoryKey']}'}',
              ),
              if (request['linkedEpicId'] != null)
                Text('Epic: ${request['linkedEpicId']}'),
              if (request['safeErrorCode'] != null)
                Text('Aandacht nodig: ${request['safeErrorCode']}'),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _showActions() async => showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: const Text('Acties voor jou'),
      content: SizedBox(
        width: 620,
        child: ListView(
          shrinkWrap: true,
          children: _actions.isEmpty
              ? const [Text('Geen open acties.')]
              : _actions
                    .map(
                      (action) => ListTile(
                        leading: const Icon(Icons.arrow_forward),
                        title: Text(_value(action['title'])),
                        subtitle: Text(_actionLabel(_value(action['kind']))),
                        onTap: () {
                          Navigator.pop(context);
                          _openTarget(action);
                        },
                      ),
                    )
                    .toList(),
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

  Future<void> _showNotifications() async => showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: const Text('Notificaties'),
      content: SizedBox(
        width: 620,
        child: ListView(
          shrinkWrap: true,
          children: _notifications.isEmpty
              ? const [Text('Geen notificaties.')]
              : _notifications
                    .map(
                      (notification) => ListTile(
                        leading: Icon(
                          notification['readAt'] == null
                              ? Icons.notifications_active_outlined
                              : Icons.notifications_none,
                        ),
                        title: Text(_value(notification['title'])),
                        subtitle: Text(
                          _actionLabel(_value(notification['kind'])),
                        ),
                        onTap: () async {
                          if (notification['readAt'] == null) {
                            await _advisor.readNotification(
                              _value(notification['id']),
                              (notification['version'] as num).toInt(),
                            );
                          }
                          if (context.mounted) Navigator.pop(context);
                          await _openTarget(notification);
                          await _loadPersonal();
                        },
                      ),
                    )
                    .toList(),
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

  Future<void> _openTarget(Map<String, Object?> item) async {
    final productId = _value(item['productId']);
    final type = _value(item['targetType']);
    final target = _value(item['targetId']);
    if (type == 'EPIC') {
      widget.onOpenDesign?.call(productId);
      return;
    }
    if (type == 'QUESTION') {
      widget.onOpenQuestions?.call(productId);
      return;
    }
    final conversationId = type == 'PRODUCT_REQUEST'
        ? _value((await _advisor.request(target))['conversationId'])
        : target;
    if (type == 'CONVERSATION' || type == 'PRODUCT_REQUEST') {
      setState(() => _productId = productId);
      widget.onProductSelected?.call(productId);
      await _loadList();
      final conversation = await _advisor.conversation(conversationId);
      if (mounted) {
        setState(() => _selected = conversation);
        widget.onConversationSelected?.call(conversationId);
      }
    }
  }
}

String _value(Object? value) =>
    value is Map ? '${value['value'] ?? ''}' : '${value ?? ''}';
String _statusLabel(String status) => switch (status) {
  'PROCESSING' => 'Bezig',
  'WAITING_FOR_USER' => 'Wacht op jou',
  'PROPOSAL_READY' => 'Voorstel gereed',
  'BLOCKED' => 'Geblokkeerd',
  'CLOSED' => 'Gesloten',
  _ => 'Open',
};
String _actionLabel(String kind) => switch (kind) {
  'PROPOSAL_READY' => 'Voorstel controleren',
  'ROUTING_FAILED' => 'Uitvoering heeft aandacht nodig',
  'ADVISOR_WAITING_FOR_USER' => 'Productadviseur wacht op jou',
  'ADVISOR_BLOCKED' => 'Opnieuw proberen mogelijk',
  'PRODUCT_APPROVAL_REQUIRED' => 'Productinhoudelijke goedkeuring',
  'FACTORY_APPROVAL_REQUIRED' => 'Eindgoedkeuring',
  _ => kind,
};

class _AdvisorGateway {
  _AdvisorGateway(this.csrfToken) : _client = createHttpClient();
  final String? csrfToken;
  final http.Client _client;
  int _sequence = 0;
  String get _base => AppConfiguration.backendUrl.replaceAll(RegExp(r'/$'), '');
  Future<List<Map<String, Object?>>> conversations(String productId) async =>
      ((await _get(
                '/api/products/${Uri.encodeComponent(productId)}/conversations',
              ))
              as List)
          .map((e) => (e as Map).cast<String, Object?>())
          .toList();
  Future<Map<String, Object?>> conversation(String id) async =>
      ((await _get('/api/conversations/$id')) as Map).cast<String, Object?>();
  Future<Map<String, Object?>> request(String id) async =>
      ((await _get('/api/product-requests/$id')) as Map)
          .cast<String, Object?>();
  Future<List<Map<String, Object?>>> actions() async =>
      ((await _get('/api/my/actions')) as List)
          .map((e) => (e as Map).cast<String, Object?>())
          .toList();
  Future<List<Map<String, Object?>>> notifications() async =>
      ((await _get('/api/my/notifications')) as List)
          .map((e) => (e as Map).cast<String, Object?>())
          .toList();
  Future<void> readNotification(String id, int version) => _send(
    'POST',
    '/api/my/notifications/$id/read',
    {'expectedVersion': version, 'idempotencyKey': _key('read-notification')},
  );
  Future<String> createConversation(String productId, String title) async =>
      '${((await _send('POST', '/api/products/${Uri.encodeComponent(productId)}/conversations', {'title': title, 'idempotencyKey': _key('conversation')})) as Map)['id']}';
  Future<void> message(String id, int version, String text) =>
      _send('POST', '/api/conversations/$id/messages', {
        'text': text,
        'expectedVersion': version,
        'idempotencyKey': _key('message'),
      });
  Future<void> close(String id, int version) => _send(
    'POST',
    '/api/conversations/$id/close',
    {'expectedVersion': version, 'idempotencyKey': _key('close')},
  );
  Future<void> retry(String id, int version) => _send(
    'POST',
    '/api/conversations/$id/retry',
    {'expectedVersion': version, 'idempotencyKey': _key('retry')},
  );
  Future<void> approveRequest(
    String id,
    int requestVersion,
    int expectedVersion,
  ) => _send('POST', '/api/product-requests/$id/approve', {
    'requestVersion': requestVersion,
    'expectedVersion': expectedVersion,
    'idempotencyKey': _key('approve'),
  });
  Future<void> cancelRequest(String id, int expectedVersion) => _send(
    'POST',
    '/api/product-requests/$id/cancel',
    {'expectedVersion': expectedVersion, 'idempotencyKey': _key('cancel')},
  );
  String _key(String prefix) =>
      'ui-$prefix-${DateTime.now().microsecondsSinceEpoch}-${_sequence++}';
  Future<Object?> _get(String path) async =>
      _decode(await _client.get(Uri.parse('$_base$path')));
  Future<dynamic> _send(
    String method,
    String path,
    Map<String, Object?> body,
  ) async {
    final request = http.Request(method, Uri.parse('$_base$path'))
      ..headers['Content-Type'] = 'application/json'
      ..body = jsonEncode(body);
    if (csrfToken != null) request.headers['X-PF-CSRF'] = csrfToken!;
    return _decode(await http.Response.fromStream(await _client.send(request)));
  }

  Object? _decode(http.Response response) {
    final value = response.bodyBytes.isEmpty
        ? null
        : jsonDecode(utf8.decode(response.bodyBytes));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw _AdvisorFailure(
        value is Map && value['message'] is String
            ? value['message'] as String
            : 'De aanvraag kon niet worden uitgevoerd.',
      );
    }
    return value;
  }
}

class _AdvisorFailure implements Exception {
  const _AdvisorFailure(this.message);
  final String message;
}
