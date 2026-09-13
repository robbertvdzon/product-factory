import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'external_link.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'configuration.dart';
import 'http_client_factory.dart';
import 'product_workspace.dart';

typedef Json = Map<String, Object?>;
Json _map(Object? v) => v is Map ? v.cast<String, Object?>() : {};
List<Json> _maps(Object? v) => v is List
    ? v.whereType<Map>().map((e) => e.cast<String, Object?>()).toList()
    : [];
String _text(Object? v) => v is Map ? '${v['value'] ?? ''}' : '${v ?? ''}';
List<String> _strings(Object? v) => v is List ? v.map(_text).toList() : [];

class CollaborationApi {
  CollaborationApi(this.csrfToken, {http.Client? client})
    : client = client ?? createHttpClient();
  final http.Client client;
  final String? csrfToken;
  int sequence = 0;
  String key() =>
      'collaboration-${DateTime.now().microsecondsSinceEpoch}-${sequence++}';
  Future<Object?> request(
    String path, {
    String method = 'GET',
    Json? body,
    bool optional = false,
  }) async {
    final req = http.Request(
      method,
      Uri.parse(
        '${AppConfiguration.backendUrl.replaceAll(RegExp(r'/$'), '')}$path',
      ),
    );
    req.headers['Content-Type'] = 'application/json';
    if (csrfToken != null) req.headers['X-PF-CSRF'] = csrfToken!;
    if (body != null) req.body = jsonEncode({'idempotencyKey': key(), ...body});
    final response = await http.Response.fromStream(await client.send(req));
    if (optional && response.statusCode == 404) return null;
    final decoded = response.bodyBytes.isEmpty
        ? null
        : jsonDecode(utf8.decode(response.bodyBytes));
    if (response.statusCode >= 400) {
      final message = _text(_map(decoded)['message']);
      throw StateError(
        response.statusCode == 409
            ? 'Deze versie is gewijzigd. Ververs en bekijk de verschillen.'
            : message.isEmpty
            ? 'De actie is niet beschikbaar (${response.statusCode}).'
            : message,
      );
    }
    return decoded;
  }

  final Map<String, Future<Uint8List>> images = {};
  Future<Uint8List> image(String url) => images.putIfAbsent(url, () async {
    final response = await client.get(Uri.parse(url));
    if (response.statusCode != 200) {
      throw StateError('Ontwerp niet beschikbaar');
    }
    return response.bodyBytes;
  });
  void close() => client.close();
}

class EpicCollaborationPage extends StatefulWidget {
  const EpicCollaborationPage({
    required this.products,
    required this.role,
    this.section = 'home',
    this.initialProductId,
    this.csrfToken,
    this.onProductSelected,
    this.api,
    super.key,
  });
  final ProductGateway products;
  final CollaborationApi? api;
  final String role;
  final String section;
  final String? initialProductId;
  final String? csrfToken;
  final ValueChanged<String>? onProductSelected;
  @override
  State<EpicCollaborationPage> createState() => _EpicCollaborationPageState();
}

class _EpicCollaborationPageState extends State<EpicCollaborationPage> {
  late final api = widget.api ?? CollaborationApi(widget.csrfToken);
  List<ProductSummary> products = [];
  List<Json> epics = [], conversations = [], questions = [], notifications = [];
  Json policy = {}, progress = {}, environment = {};
  List<Json> versions = [];
  Json? epic, conversation;
  String? productId, error;
  bool loading = true, saving = false, creating = false;
  String tab = 'Uitwerking', viewport = 'DESKTOP';
  String? screenKey;
  Timer? timer;
  final message = TextEditingController(), idea = TextEditingController();
  bool get architect => widget.role == 'ARCHITECT';
  bool get factory => widget.role == 'FACTORY_OWNER';
  List<Json> get openQuestions =>
      questions.where((q) => q['status'] == 'OPEN').toList();
  @override
  void initState() {
    super.initState();
    unawaited(load());
    timer = Timer.periodic(const Duration(seconds: 8), (_) {
      if (!saving) unawaited(load(silent: true));
    });
  }

  @override
  void dispose() {
    timer?.cancel();
    message.dispose();
    idea.dispose();
    api.close();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant EpicCollaborationPage old) {
    super.didUpdateWidget(old);
    if (old.section != widget.section) {
      epic = null;
      conversation = null;
      creating = false;
    }
    if (old.initialProductId != widget.initialProductId) {
      productId = widget.initialProductId;
      epic = null;
      conversation = null;
      unawaited(load());
    }
  }

  Future<void> load({bool silent = false}) async {
    try {
      final nextProducts = await widget.products.products();
      final id =
          productId ?? widget.initialProductId ?? nextProducts.firstOrNull?.id;
      if (id == null) {
        if (mounted) {
          setState(() {
            products = nextProducts;
            loading = false;
          });
        }
        return;
      }
      final values = await Future.wait([
        api.request('/api/products/$id/epics'),
        api.request('/api/products/$id/conversations'),
        api.request('/api/products/$id/questions'),
        api.request('/api/products/$id/governance'),
        api.request('/api/products/$id/test-configuration', optional: true),
        api.request('/api/my/notifications'),
      ]);
      Json? nextConversation;
      if (conversation != null) {
        nextConversation = _map(
          await api.request('/api/conversations/${_text(conversation!['id'])}'),
        );
      }
      final nextEpics = _maps(values[0]);
      final nextEpic = epic == null
          ? null
          : nextEpics
                .where((e) => _text(e['id']) == _text(epic!['id']))
                .firstOrNull;
      Json nextProgress = progress;
      if (nextEpic != null) {
        nextProgress = _map(
          await api.request('/api/epics/${_text(nextEpic['id'])}/progress'),
        );
      }
      if (!mounted || (productId != null && productId != id)) return;
      setState(() {
        products = nextProducts;
        productId = id;
        epics = nextEpics;
        conversations = _maps(values[1]);
        questions = _maps(values[2]);
        policy = _map(values[3]);
        environment = _map(values[4]);
        notifications = _maps(values[5])
            .where((n) => _text(n['productId']) == id && n['readAt'] == null)
            .toList();
        if (epic != null) epic = nextEpic;
        if (nextConversation != null) conversation = nextConversation;
        progress = nextProgress;
        loading = false;
        if (!silent) error = null;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          loading = false;
          if (!silent) error = '$e';
        });
      }
    }
  }

  Future<void> mutate(Future<void> Function() work) async {
    if (saving) return;
    setState(() {
      saving = true;
      error = null;
    });
    try {
      await work();
      await load();
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> openEpic(Json value) async {
    setState(() {
      epic = value;
      tab = architect ? 'Impact' : 'Uitwerking';
      conversation = null;
      creating = false;
      progress = {};
    });
    await mutate(() async {
      final id = _text(value['id']);
      final results = await Future.wait([
        api.request('/api/epics/$id/discussions'),
        api.request('/api/epics/$id/history'),
        api.request('/api/epics/$id/progress'),
      ]);
      if (!mounted) return;
      setState(() {
        conversation = _maps(results[0]).firstOrNull;
        versions = _maps(results[1]);
        progress = _map(results[2]);
      });
    });
  }

  Future<void> send({String? preset}) => mutate(() async {
    final text = preset ?? message.text.trim();
    if (text.isEmpty) return;
    if (conversation == null) {
      final response = _map(
        await api.request(
          epic == null
              ? '/api/products/$productId/conversations'
              : '/api/epics/${_text(epic!['id'])}/discussions',
          method: 'POST',
          body: {
            'title': epic == null
                ? text.substring(0, text.length.clamp(0, 150))
                : '${architect ? 'Architectuur' : 'Uitwerking'}: ${_text(epic!['title']).substring(0, _text(epic!['title']).length.clamp(0, 140))}',
            if (epic != null) 'role': widget.role,
          },
        ),
      );
      conversation = _map(
        await api.request('/api/conversations/${_text(response['id'])}'),
      );
    }
    await api.request(
      '/api/conversations/${_text(conversation!['id'])}/messages',
      method: 'POST',
      body: {'text': text, 'expectedVersion': conversation!['version']},
    );
    message.clear();
    creating = false;
  });
  Future<String?> ask(String title, String label, {String initial = ''}) async {
    final c = TextEditingController(text: initial);
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 560,
          child: TextField(
            controller: c,
            autofocus: true,
            minLines: 3,
            maxLines: 8,
            decoration: InputDecoration(labelText: label),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () {
              if (c.text.trim().isNotEmpty) {
                Navigator.pop(context, c.text.trim());
              }
            },
            child: const Text('Bevestigen'),
          ),
        ],
      ),
    );
    c.dispose();
    return value;
  }

  Future<void> review(String decision) async {
    final text = await ask(
      decision == 'APPROVE'
          ? architect
                ? 'Architectuur akkoord'
                : 'Functioneel akkoord'
          : decision == 'REQUEST_CHANGE'
          ? 'Aanpassing vragen'
          : 'Onderzoek vragen',
      'Toelichting',
      initial: decision == 'APPROVE'
          ? architect
                ? 'De beschreven impact en product-AI-afspraken zijn akkoord.'
                : 'Werking, scope en schermen zijn akkoord.'
          : '',
    );
    if (text == null || epic == null) return;
    await mutate(() async {
      await api.request(
        '/api/epics/${_text(epic!['id'])}/reviews',
        method: 'POST',
        body: {
          'role': widget.role,
          'decision': decision,
          'reason': text,
          'expectedVersion': epic!['version'],
        },
      );
    });
    if (decision == 'REQUEST_CHANGE' && error == null) {
      await feedback(text: text);
    }
    if (decision == 'REQUEST_RESEARCH' && error == null) {
      setState(() => tab = 'Gesprek');
      await send(
        preset:
            'Onderzoek voor de architect, zonder deze epic goed te keuren: $text',
      );
    }
  }

  Future<void> feedback({String? text, Json? screen, Json? artifact}) async {
    final value =
        text ??
        await ask(
          'Samen verder uitwerken',
          'Welke besproken wijzigingen wil je verwerken?',
          initial: message.text.trim(),
        );
    if (value == null || epic == null) return;
    final transcript = _maps(conversation?['messages']).reversed
        .take(6)
        .toList()
        .reversed
        .map((m) => '${m['sender']}: ${_text(m['text'])}')
        .join('\n\n');
    final contextText = transcript.length > 6000
        ? transcript.substring(transcript.length - 6000)
        : transcript;
    await mutate(() async {
      await api.request(
        '/api/epics/${_text(epic!['id'])}/feedback',
        method: 'POST',
        body: {
          'text':
              '$value${contextText.isEmpty ? '' : '\n\nBesproken context (te onderzoeken, geen zelfstandig besluit):\n$contextText'}',
          'role': widget.role,
          'expectedVersion': epic!['version'],
          'screenKey': screen?['screenKey'],
          'viewport': screen == null ? null : viewport,
          'artifactName': artifact?['name'],
        },
      );
    });
  }

  String label(Object? value) =>
      const {
        'NEEDS_RESEARCH': 'Onderzoek nodig',
        'NEEDS_REFINEMENT': 'Wordt uitgewerkt',
        'AWAITING_APPROVAL': 'Architect beoordeelt',
        'AWAITING_PRODUCT_OWNER_APPROVAL': 'Functioneel akkoord nodig',
        'AWAITING_FACTORY_OWNER_APPROVAL': 'Productrollen instellen',
        'AVAILABLE': 'Gereed voor planning',
        'IN_PLANNING': 'Planning',
        'ACTIVE': 'Bouwen & testen',
        'VERIFYING': 'Verificatie',
        'COMPLETED': 'Geverifieerd',
        'NOT_SUCCESSFUL': 'Nog niet geslaagd',
        'CANCELLED': 'Geannuleerd',
        'WITHDRAWN': 'Ingetrokken',
        'TODO': 'Gepland',
        'IN_PROGRESS': 'In uitvoering',
        'DONE': 'Code geleverd',
        'OPEN': 'Open',
        'ANSWERED': 'Beantwoord',
        'NONE': 'Geen wijziging',
        'COMPATIBLE': 'Compatibel',
        'MATERIAL': 'Besluit nodig',
        'UNKNOWN': 'Onderzoek nodig',
        'APPROVE': 'Akkoord',
        'REQUEST_CHANGE': 'Aanpassing gevraagd',
        'REQUEST_RESEARCH': 'Onderzoek gevraagd',
      }[_text(value)] ??
      _text(value);
  String category(Object? value) =>
      const {
        'DATABASE': 'Database',
        'MIGRATION': 'Migratie',
        'EXTERNAL_SYSTEM': 'Externe systemen',
        'FRONTEND': 'Frontend',
        'ACCESS': 'Toegang',
        'PRODUCT_AI': 'AI in het product',
        'INFRASTRUCTURE': 'Infrastructuur',
      }[_text(value)] ??
      _text(value);
  Widget button(String title, VoidCallback action, {bool primary = false}) =>
      primary
      ? FilledButton(onPressed: saving ? null : action, child: Text(title))
      : OutlinedButton(onPressed: saving ? null : action, child: Text(title));
  Widget panel(String title, List<Widget> children) => Card(
    margin: const EdgeInsets.only(bottom: 18),
    child: Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (title.isNotEmpty) ...[
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 14),
          ],
          ...children,
        ],
      ),
    ),
  );
  Widget text(String value) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: SelectableText(value),
  );
  Widget notice(String title, String body) => Card(
    color: Theme.of(context).colorScheme.primaryContainer,
    child: Padding(
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 6),
          Text(body),
        ],
      ),
    ),
  );
  Widget epicRow(Json e) => ListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(_text(e['title'])),
    subtitle: Text('${label(e['status'])}\n${_text(e['summary'])}'),
    isThreeLine: true,
    trailing: const Icon(Icons.chevron_right),
    onTap: () => openEpic(e),
  );
  @override
  Widget build(BuildContext context) {
    if (loading) return const Center(child: CircularProgressIndicator());
    return SingleChildScrollView(
      padding: EdgeInsets.all(MediaQuery.sizeOf(context).width < 600 ? 16 : 30),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1200),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (products.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 20),
                  child: DropdownButtonFormField<String>(
                    initialValue: productId,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Product'),
                    items: products
                        .map(
                          (p) => DropdownMenuItem(
                            value: p.id,
                            child: Text(p.name),
                          ),
                        )
                        .toList(),
                    onChanged: (v) {
                      if (v == null) return;
                      setState(() {
                        productId = v;
                        epic = null;
                        conversation = null;
                      });
                      widget.onProductSelected?.call(v);
                      unawaited(load());
                    },
                  ),
                ),
              if (error != null)
                MaterialBanner(
                  content: Text(error!),
                  actions: [
                    TextButton(
                      onPressed: () => load(),
                      child: const Text('Verversen'),
                    ),
                  ],
                ),
              if (saving) const LinearProgressIndicator(),
              if (products.isEmpty)
                notice(
                  'Nog geen producttoegang',
                  'De factory owner kan je een productrol toewijzen.',
                )
              else if (widget.section == 'policy')
                policyPanel()
              else if (widget.section == 'questions')
                questionPanel()
              else if (creating) ...[
                Text(
                  'Wat wil je verbeteren?',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 16),
                panel('Een idee hoeft nog niet af te zijn', [
                  TextField(
                    controller: idea,
                    minLines: 5,
                    maxLines: 10,
                    decoration: const InputDecoration(
                      labelText: 'Beschrijf je idee',
                    ),
                  ),
                  const SizedBox(height: 14),
                  button(
                    'Samen uitwerken',
                    () => send(preset: idea.text.trim()),
                    primary: true,
                  ),
                ]),
              ] else if (epic != null)
                ...epicPanel()
              else if (conversation != null) ...[
                button(
                  'Terug naar mijn werk',
                  () => setState(() => conversation = null),
                ),
                chatPanel(),
              ] else
                ...home(),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> home() => [
    Text(
      architect
          ? 'Wat vraagt jouw beoordeling?'
          : widget.section == 'epics'
          ? 'Mijn epics'
          : 'Mijn werk',
      style: Theme.of(context).textTheme.headlineMedium,
    ),
    const SizedBox(height: 8),
    text(
      architect
          ? 'Technische impact en het AI-gebruik van het product.'
          : 'Van jouw idee naar een verbetering. Hier zie je wat loopt en wat jouw aandacht nodig heeft.',
    ),
    if (!architect && !factory)
      Align(
        alignment: Alignment.centerLeft,
        child: button(
          '+ Nieuw idee',
          () => setState(() {
            creating = true;
            idea.clear();
          }),
          primary: true,
        ),
      ),
    const SizedBox(height: 18),
    if (policy['configured'] != true)
      notice(
        'Productrollen en afspraken nog instellen',
        'De factory owner wijst de besturing toe. De architect legt de productafspraken vast.',
      ),
    if (notifications.isNotEmpty)
      panel(
        'Nieuw voor jou',
        notifications
            .take(10)
            .map(
              (n) => ListTile(
                title: Text(_text(n['title'])),
                trailing: IconButton(
                  tooltip: 'Gelezen',
                  icon: const Icon(Icons.done),
                  onPressed: () => mutate(() async {
                    await api.request(
                      '/api/my/notifications/${_text(n['id'])}/read',
                      method: 'POST',
                      body: {'expectedVersion': n['version']},
                    );
                  }),
                ),
                onTap: () {
                  final target = epics
                      .where((e) => _text(e['id']) == _text(n['targetId']))
                      .firstOrNull;
                  if (target != null) unawaited(openEpic(target));
                },
              ),
            )
            .toList(),
      ),
    if (openQuestions.isNotEmpty) questionPanel(),
    panel(
      architect ? 'Epics en beoordelingen' : 'Jouw epics',
      epics.isEmpty
          ? [text('Nog geen epics. Bespreek een idee om te beginnen.')]
          : epics.map(epicRow).toList(),
    ),
    if (!architect)
      panel(
        'Gesprekken en ideeën',
        conversations
            .where((c) => c['epicId'] == null)
            .map(
              (c) => ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(_text(c['title'])),
                subtitle: Text(
                  c['status'] == 'PROCESSING'
                      ? 'AI werkt aan je vraag'
                      : c['status'] == 'BLOCKED'
                      ? 'Je gesprek vraagt aandacht'
                      : 'Verder bespreken',
                ),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => mutate(() async {
                  conversation = _map(
                    await api.request('/api/conversations/${_text(c['id'])}'),
                  );
                }),
              ),
            )
            .toList(),
      ),
  ];
  Widget chatPanel() {
    final c = conversation;
    final request = _map(c?['request']);
    final messages = _maps(c?['messages']);
    return panel(architect ? 'Onderzoek met AI' : 'Samen uitwerken', [
      if (messages.isEmpty)
        text(
          architect
              ? 'Vraag wat er precies verandert, waarom dit nodig is en of het eenvoudiger kan.'
              : 'Bespreek de werking, een scherm of een verbetering.',
        ),
      ...messages.map(
        (m) => Padding(
          padding: EdgeInsets.only(
            left: m['sender'] == 'USER' ? 20 : 0,
            bottom: 12,
          ),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: m['sender'] == 'USER'
                  ? Theme.of(context).colorScheme.primaryContainer
                  : Theme.of(context).colorScheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    m['sender'] == 'USER' ? 'Jij' : 'Product Advisor',
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                  const SizedBox(height: 6),
                  SelectableText(_text(m['text'])),
                ],
              ),
            ),
          ),
        ),
      ),
      if (c?['status'] == 'PROCESSING')
        const ListTile(
          leading: CircularProgressIndicator(),
          title: Text(
            'AI werkt aan je vraag. Je kunt dit scherm later opnieuw openen.',
          ),
        ),
      if (c?['status'] == 'BLOCKED')
        button(
          'Gesprek hervatten',
          () => mutate(() async {
            await api.request(
              '/api/conversations/${_text(c!['id'])}/retry',
              method: 'POST',
              body: {'expectedVersion': c['version']},
            );
          }),
        ),
      if (c?['status'] != 'PROCESSING' && c?['status'] != 'CLOSED') ...[
        TextField(
          controller: message,
          minLines: 3,
          maxLines: 8,
          decoration: const InputDecoration(labelText: 'Je bericht'),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            button('Verstuur', () => send(), primary: true),
            if (epic != null)
              button('Feedback in epic verwerken', () => feedback()),
          ],
        ),
      ],
      if (request.isNotEmpty && epic == null) ...[
        const Divider(height: 32),
        text(_text(_map(request['content'])['title'])),
        text(_text(_map(request['content'])['summary'])),
        if (request['status'] == 'PROPOSED')
          button(
            'Laat dit voorstel uitwerken',
            () => mutate(() async {
              await api.request(
                '/api/product-requests/${_text(request['id'])}/approve',
                method: 'POST',
                body: {
                  'expectedVersion': request['version'],
                  'requestVersion': request['currentVersion'],
                },
              );
            }),
            primary: true,
          ),
        if (request['linkedEpicId'] != null)
          button('Open epic', () async {
            final target = epics
                .where((e) => _text(e['id']) == _text(request['linkedEpicId']))
                .firstOrNull;
            if (target != null) await openEpic(target);
          }),
      ],
    ]);
  }

  List<Widget> epicPanel() {
    final e = epic!;
    final review = _map(e['review']);
    final tabs = architect
        ? ['Impact', 'Gesprek', 'Uitwerking', 'Goedkeuring', 'Voortgang']
        : [
            'Uitwerking',
            'Schermen',
            'Aandachtspunten',
            'Goedkeuring',
            'Voortgang',
          ];
    if (!tabs.contains(tab)) tab = tabs.first;
    return [
      Align(
        alignment: Alignment.centerLeft,
        child: TextButton(
          onPressed: () => setState(() {
            epic = null;
            conversation = null;
          }),
          child: const Text('← Mijn werk'),
        ),
      ),
      Text(
        _text(e['title']),
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      const SizedBox(height: 8),
      text(
        'Inhoudsversie ${e['contentVersion'] ?? e['version']} · ${label(e['status'])}',
      ),
      if (_text(_map(e['impact'])['changeSummary']).isNotEmpty)
        notice(
          'Wijziging in deze versie',
          _text(_map(e['impact'])['changeSummary']),
        ),
      if (_strings(review['blockers']).isNotEmpty)
        notice(
          'Nog nodig voor de volgende stap',
          _strings(review['blockers']).join('\n'),
        ),
      const SizedBox(height: 12),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: tabs
            .map(
              (t) => ChoiceChip(
                label: Text(t),
                selected: tab == t,
                onSelected: (_) => setState(() => tab = t),
              ),
            )
            .toList(),
      ),
      const SizedBox(height: 20),
      if (tab == 'Uitwerking')
        LayoutBuilder(
          builder: (context, box) {
            final content = dossier();
            if (architect) return content;
            return box.maxWidth > 850
                ? Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(child: chatPanel()),
                      const SizedBox(width: 18),
                      Expanded(child: content),
                    ],
                  )
                : Column(children: [content, chatPanel()]);
          },
        ),
      if (tab == 'Gesprek') chatPanel(),
      if (tab == 'Impact') impactPanel(),
      if (tab == 'Schermen') uxPanel(),
      if (tab == 'Aandachtspunten') risksPanel(),
      if (tab == 'Goedkeuring') reviewPanel(),
      if (tab == 'Voortgang') progressPanel(),
      if (!factory &&
          !['COMPLETED', 'CANCELLED', 'WITHDRAWN'].contains(e['status']) &&
          tab != 'Goedkeuring')
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            button(
              architect ? 'Architectuur akkoord' : 'Functioneel akkoord',
              () => reviewAction(),
              primary: true,
            ),
            button('Verder uitwerken', () => feedback()),
          ],
        ),
    ];
  }

  void reviewAction() => unawaited(review('APPROVE'));
  Widget dossier() => panel('Dit gaan we verbeteren', [
    text('Probleem'),
    text(_text(epic!['problem'])),
    text('Werking en scope'),
    text(_text(epic!['solution'])),
    text('Klaar als'),
    ..._strings(epic!['acceptanceCriteria']).map((x) => text('✓ $x')),
    if (versions.length > 1)
      ExpansionTile(
        title: const Text('Vorige versies vergelijken'),
        children: versions
            .where((v) => v['version'] != epic!['version'])
            .map(
              (v) => ListTile(
                title: Text('Versie ${v['contentVersion'] ?? v['version']}'),
                subtitle: SelectableText(_text(v['solution'])),
              ),
            )
            .toList(),
      ),
  ]);
  Widget impactPanel() {
    final impact = _map(epic!['impact']);
    final items = _maps(impact['items']);
    final ai = _map(impact['productAi']);
    return panel('Impact in één oogopslag', [
      if (items.isEmpty)
        text(
          'Er is nog geen onderbouwde impactbeoordeling. Vraag verdere uitwerking.',
        ),
      ...items.map(
        (i) => ExpansionTile(
          tilePadding: EdgeInsets.zero,
          title: Text(category(i['category'])),
          subtitle: Text('${label(i['level'])} · ${_text(i['summary'])}'),
          children: [
            text('Onderbouwing'),
            ..._strings(i['evidence']).map(text),
            if (_text(i['alternatives']).isNotEmpty)
              text('Alternatieven: ${i['alternatives']}'),
            if (i['category'] == 'PRODUCT_AI')
              ...[
                'currentBehavior',
                'proposedBehavior',
                'trigger',
                'frequency',
                'volume',
                'oneTimeWork',
                'assumptions',
                'providerAndModel',
                'dataAndValidation',
                'limitsAndRetries',
              ].map(
                (key) => text(
                  '${const {'currentBehavior': 'Nu', 'proposedBehavior': 'Straks', 'trigger': 'Trigger', 'frequency': 'Frequentie', 'volume': 'Volume', 'oneTimeWork': 'Eenmalig', 'assumptions': 'Aannames', 'providerAndModel': 'Provider/model', 'dataAndValidation': 'Gegevens en validatie', 'limitsAndRetries': 'Grenzen en retries'}[key]}: ${ai[key] is List ? _strings(ai[key]).join('; ') : _text(ai[key])}',
                ),
              ),
            if (i['category'] == 'PRODUCT_AI')
              text(
                'Extra jobs/dag: ${ai['estimatedAdditionalJobsPerDay'] ?? 'onbekend'} · maandkosten: ${ai['estimatedMonthlyCostEuro'] ?? 'onbekend'}',
              ),
            Align(
              alignment: Alignment.centerLeft,
              child: button('Onderzoek met AI', () {
                setState(() => tab = 'Gesprek');
                unawaited(
                  send(
                    preset:
                        'Onderzoek ${category(i['category'])}: ${i['summary']}. Wat verandert precies, waarom en welke alternatieven zijn er? Onderbouw met code en architectuurbesluiten.',
                  ),
                );
              }),
            ),
          ],
        ),
      ),
    ]);
  }

  Widget risksPanel() {
    final risks = _maps(_map(epic!['impact'])['risks']);
    return panel(
      'Aandachtspunten',
      risks.isEmpty
          ? [text('Nog geen afzonderlijke risico’s vastgelegd.')]
          : risks
                .map(
                  (r) => ExpansionTile(
                    tilePadding: EdgeInsets.zero,
                    title: Text(_text(r['title'])),
                    subtitle: Text(_text(r['mitigation'])),
                    children: [
                      text('Kans: ${r['likelihood']} · impact: ${r['impact']}'),
                      text('Onzekerheid: ${r['uncertainty']}'),
                      text(
                        'Verantwoordelijk: ${r['responsibleRole'] == 'ARCHITECT' ? 'Architect' : 'Product owner'}',
                      ),
                    ],
                  ),
                )
                .toList(),
    );
  }

  Widget uxPanel() {
    final screens = _maps(epic!['uxScreens']);
    final artifacts = _maps(epic!['uxArtifacts']);
    if (screens.isEmpty) {
      return panel('Schermontwerp', [
        text(
          _text(epic!['uxDesign']).isEmpty
              ? 'Voor deze versie is nog geen schermontwerp beschikbaar.'
              : _text(epic!['uxDesign']),
        ),
      ]);
    }
    final selected =
        screens.where((s) => s['screenKey'] == screenKey).firstOrNull ??
        screens.first;
    final variants = {
      for (final variant in _maps(selected['artifacts']))
        _text(variant['viewport']): variant['artifactName'],
    };
    if (!variants.containsKey(viewport)) {
      viewport = variants.keys.firstOrNull ?? 'DESKTOP';
    }
    final artifact = artifacts
        .where((a) => a['name'] == variants[viewport])
        .firstOrNull;
    final uri = _text(artifact?['uri']);
    final imageUrl = uri.isEmpty
        ? ''
        : '${AppConfiguration.backendUrl.replaceAll(RegExp(r'/$'), '')}/api/epics/${_text(epic!['id'])}/ux-artifacts?name=${Uri.encodeQueryComponent(_text(artifact?['name']))}&version=${epic!['contentVersion']}';
    return panel('Schermontwerp', [
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: screens
            .map(
              (s) => ChoiceChip(
                label: Text(_text(s['purpose'])),
                selected: s['screenKey'] == selected['screenKey'],
                onSelected: (_) =>
                    setState(() => screenKey = _text(s['screenKey'])),
              ),
            )
            .toList(),
      ),
      const SizedBox(height: 12),
      Wrap(
        spacing: 8,
        children: variants.keys
            .map(
              (v) => ChoiceChip(
                label: Text(v == 'MOBILE' ? 'Mobiel' : 'Desktop'),
                selected: viewport == v,
                onSelected: (_) => setState(() => viewport = v),
              ),
            )
            .toList(),
      ),
      const SizedBox(height: 16),
      if (imageUrl.isNotEmpty)
        FutureBuilder<Uint8List>(
          future: api.image(imageUrl),
          builder: (context, snapshot) => snapshot.hasData
              ? InteractiveViewer(
                  child: Image.memory(snapshot.data!, fit: BoxFit.contain),
                )
              : snapshot.hasError
              ? const Text(
                  'Het ontwerp kon niet geladen worden. Probeer het opnieuw.',
                )
              : const Center(child: CircularProgressIndicator()),
        ),
      const SizedBox(height: 12),
      text(
        '${selected['screenKey']} · ${selected['state']} · inhoudsversie ${epic!['contentVersion']}',
      ),
      button(
        'Feedback op dit scherm',
        () => feedback(screen: selected, artifact: artifact),
      ),
    ]);
  }

  Widget reviewPanel() {
    final r = _map(epic!['review']);
    return panel('Goedkeuringen', [
      text(
        'Product owner: ${r['productOwnerApproved'] == true ? 'akkoord' : 'nog nodig'}',
      ),
      text(
        'Architect: ${r['architectApproved'] == true ? 'akkoord / past binnen afspraken' : 'beoordeling nodig'}',
      ),
      text(
        'Productafspraken versie ${r['policyVersion'] ?? policy['version']}',
      ),
      if (!factory)
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            button(
              architect ? 'Akkoord op deze versie' : 'Functioneel akkoord',
              () => review('APPROVE'),
              primary: true,
            ),
            button('Vraag aanpassing', () => review('REQUEST_CHANGE')),
            if (architect)
              button('Vraag onderzoek', () => review('REQUEST_RESEARCH')),
          ],
        ),
      const SizedBox(height: 16),
      ..._maps(r['records']).reversed.map(
        (record) => ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(
            '${record['role'] == 'ARCHITECT' ? 'Architect' : 'Product owner'} · ${label(record['decision'])} · versie ${record['contentVersion']}',
          ),
          subtitle: Text(
            '${record['reason']}\n${record['automatic'] == true ? 'Automatisch binnen mandaat' : 'Menselijk besluit'} · ${record['createdAt']}',
          ),
        ),
      ),
    ]);
  }

  Widget questionPanel() => panel(
    'Vragen voor jou',
    openQuestions.isEmpty
        ? [
            text(
              'Je bent bij. Nieuwe vragen uit ontwerp, planning of bouw verschijnen hier.',
            ),
          ]
        : openQuestions
              .map(
                (q) => Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _text(q['question']),
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      const SizedBox(height: 8),
                      text(_text(q['context'])),
                      text(
                        'Van ${_text(q['agentRole'])}${q['epicLinkId'] == null ? '' : ' · gekoppeld aan een epic'}',
                      ),
                      button('Beantwoord vraag', () async {
                        final answer = await ask(
                          'Je antwoord',
                          _text(q['question']),
                        );
                        if (answer == null) return;
                        await mutate(() async {
                          await api.request(
                            '/api/products/questions/${_text(q['id'])}/answer-directly',
                            method: 'POST',
                            body: {
                              'answer': answer,
                              'expectedVersion': q['version'],
                            },
                          );
                        });
                      }, primary: true),
                    ],
                  ),
                ),
              )
              .toList(),
  );
  Widget progressPanel() => panel('Van idee naar oplevering', [
    text(label(epic!['status'])),
    if (_map(progress['waitingOn']).isNotEmpty)
      text(
        '${_map(progress['waitingOn'])['title']}\n${_text(_map(progress['waitingOn'])['detail'])}',
      ),
    ..._maps(progress['steps']).map(
      (step) => ListTile(
        contentPadding: EdgeInsets.zero,
        leading: Icon(
          step['state'] == 'DONE'
              ? Icons.check_circle_outline
              : Icons.radio_button_unchecked,
        ),
        title: Text(_text(step['label'])),
        subtitle: Text(_text(step['detail'])),
      ),
    ),
    for (final entry in environment.entries.where(
      (e) =>
          ['production', 'acceptance'].contains(e.key) &&
          _text(_map(e.value)['baseUrl']).startsWith('https://'),
    ))
      button(
        'Open ${entry.key == 'production' ? 'productie' : 'acceptatie'}',
        () => openExternalLink(_text(_map(entry.value)['baseUrl'])),
      ),
    if (epic!['status'] == 'COMPLETED')
      notice(
        'De epic is geverifieerd',
        'De controle van de geleverde versie is geslaagd. Beschikbaarheid in productie volgt de deployment van het product.',
      ),
    if (openQuestions.any((q) => _text(q['epicLinkId']) == _text(epic!['id'])))
      questionPanel(),
    Text('Stories', style: Theme.of(context).textTheme.titleMedium),
    ..._maps(progress['stories']).map(
      (story) => ExpansionTile(
        tilePadding: EdgeInsets.zero,
        title: Text(_text(story['title'])),
        subtitle: Text(label(story['status'])),
        children: [
          button(
            'Bekijk werking en acceptatiecriteria',
            () => mutate(() async {
              final detail = _map(
                await api.request('/api/stories/${_text(story['id'])}'),
              );
              if (!mounted) return;
              await showDialog<void>(
                context: context,
                builder: (context) => AlertDialog(
                  title: Text(_text(detail['title'])),
                  content: SizedBox(
                    width: 600,
                    child: SingleChildScrollView(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          text(
                            _text(detail['description'] ?? detail['summary']),
                          ),
                          ..._strings(detail['acceptanceCriteria']).map(text),
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
            }),
          ),
        ],
      ),
    ),
    ..._maps(progress['openBugs']).map(
      (bug) =>
          text('Open bevinding: ${bug['title']} · ${label(bug['status'])}'),
    ),
    ExpansionTile(
      title: const Text('Tijdlijn'),
      children: _maps(progress['timeline']).reversed
          .map(
            (event) => ListTile(
              title: Text(_text(event['title'])),
              subtitle: Text('${_text(event['detail'])}\n${event['at']}'),
            ),
          )
          .toList(),
    ),
    if (epic!['status'] == 'COMPLETED' && !architect)
      button('Bespreek een vervolgidee', () {
        idea.text = 'Vervolg op ${epic!['title']}: ';
        setState(() {
          epic = null;
          conversation = null;
          creating = true;
        });
      }),
  ]);
  Widget policyPanel() => GovernancePolicyEditor(
    key: ValueKey('$productId-${policy['version']}'),
    policy: policy,
    factory: factory,
    saving: saving,
    onSave: (next) => mutate(() async {
      await api.request(
        '/api/products/$productId/governance',
        method: 'PUT',
        body: {'policy': next},
      );
    }),
  );
}

class GovernancePolicyEditor extends StatefulWidget {
  const GovernancePolicyEditor({
    required this.policy,
    required this.factory,
    required this.saving,
    required this.onSave,
    super.key,
  });
  final Json policy;
  final bool factory, saving;
  final Future<void> Function(Json) onSave;
  @override
  State<GovernancePolicyEditor> createState() => _GovernancePolicyEditorState();
}

class _GovernancePolicyEditorState extends State<GovernancePolicyEditor> {
  late final architecture = TextEditingController(
    text: _text(widget.policy['architectureRules']),
  );
  late final ai = TextEditingController(
    text: _text(widget.policy['productAiRules']),
  );
  late final jobs = TextEditingController(
    text: _text(widget.policy['maximumAdditionalJobsPerDay']),
  );
  late final budget = TextEditingController(
    text: _text(widget.policy['monthlyProductBudgetEuro']),
  );
  late final growth = TextEditingController(
    text: _text(widget.policy['maximumGrowthPercent']),
  );
  late String poMode = _text(widget.policy['productOwnerMode']),
      archMode = _text(widget.policy['architectMode']);
  late final categories = _strings(
    widget.policy['automaticCategories'],
  ).toSet();
  @override
  void dispose() {
    for (final c in [architecture, ai, jobs, budget, growth]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            widget.factory ? 'Productbesturing' : 'Productafspraken',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 12),
          Text(
            widget.factory
                ? 'Wijs de menselijke of automatische rollen toe. Personen koppel je via Beheer → Leden. Inhoudelijke afspraken en productbudgetten beheert de architect.'
                : 'Deze afspraken betreffen architectuur en AI-gebruik van het product. Een lege grens geeft geen onbeperkt mandaat.',
          ),
          const SizedBox(height: 20),
          if (widget.factory) ...[
            DropdownButtonFormField<String>(
              initialValue: poMode.isEmpty ? 'HUMAN' : poMode,
              decoration: const InputDecoration(labelText: 'Product owner'),
              items: const [
                DropdownMenuItem(
                  value: 'HUMAN',
                  child: Text('Mens met AI-ondersteuning'),
                ),
                DropdownMenuItem(
                  value: 'AI',
                  child: Text('Automatisch binnen productopdracht'),
                ),
              ],
              onChanged: (v) => setState(() => poMode = v!),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              initialValue: archMode.isEmpty ? 'HUMAN' : archMode,
              decoration: const InputDecoration(labelText: 'Architect'),
              items: const [
                DropdownMenuItem(
                  value: 'HUMAN',
                  child: Text('Mens beoordeelt afwijkingen'),
                ),
                DropdownMenuItem(
                  value: 'AI',
                  child: Text('Automatisch binnen mandaat'),
                ),
              ],
              onChanged: (v) => setState(() => archMode = v!),
            ),
          ] else ...[
            TextField(
              controller: architecture,
              minLines: 3,
              maxLines: 8,
              decoration: const InputDecoration(
                labelText: 'Architectuurafspraken',
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: ai,
              minLines: 3,
              maxLines: 8,
              decoration: const InputDecoration(
                labelText: 'AI-gebruik van het product',
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: jobs,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Maximale extra AI-jobs per dag',
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: budget,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Productbudget per maand (€)',
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: growth,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Maximale groei in AI-gebruik (%)',
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Expliciet mandaat voor materiële impact (overige impact vraagt beoordeling):',
            ),
            ...const {
              'DATABASE': 'Database',
              'MIGRATION': 'Migratie',
              'EXTERNAL_SYSTEM': 'Externe koppelingen',
              'FRONTEND': 'Frontend',
              'ACCESS': 'Toegang',
              'PRODUCT_AI': 'Product-AI',
              'INFRASTRUCTURE': 'Infrastructuur',
            }.entries.map(
              (e) => CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(e.value),
                value: categories.contains(e.key),
                onChanged: (v) => setState(() {
                  if (v == true) {
                    categories.add(e.key);
                  } else {
                    categories.remove(e.key);
                  }
                }),
              ),
            ),
          ],
          const SizedBox(height: 20),
          Align(
            alignment: Alignment.centerLeft,
            child: FilledButton(
              onPressed: widget.saving
                  ? null
                  : () {
                      if (!widget.factory &&
                          ((jobs.text.isNotEmpty &&
                                  int.tryParse(jobs.text) == null) ||
                              (growth.text.isNotEmpty &&
                                  int.tryParse(growth.text) == null) ||
                              (budget.text.isNotEmpty &&
                                  double.tryParse(budget.text) == null))) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text(
                              'Vul geldige getallen in of laat een grens leeg.',
                            ),
                          ),
                        );
                        return;
                      }
                      unawaited(
                        widget.onSave({
                          ...widget.policy,
                          if (widget.factory) ...{
                            'productOwnerMode': poMode.isEmpty
                                ? 'HUMAN'
                                : poMode,
                            'architectMode': archMode.isEmpty
                                ? 'HUMAN'
                                : archMode,
                          } else ...{
                            'architectureRules': architecture.text,
                            'productAiRules': ai.text,
                            'maximumAdditionalJobsPerDay': int.tryParse(
                              jobs.text,
                            ),
                            'monthlyProductBudgetEuro':
                                budget.text.trim().isEmpty
                                ? null
                                : budget.text.trim(),
                            'maximumGrowthPercent': int.tryParse(growth.text),
                            'automaticCategories': categories.toList(),
                          },
                        }),
                      );
                    },
              child: const Text('Opslaan'),
            ),
          ),
        ],
      ),
    ),
  );
}
