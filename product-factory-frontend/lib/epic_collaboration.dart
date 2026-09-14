import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'external_link.dart';
import 'conversation_images.dart';
import 'chat_answer_image.dart';
import 'conversation_timeline.dart';
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
  List<Json> epics = [], conversations = [], questions = [];
  Json policy = {}, progress = {}, environment = {};
  List<Json> versions = [], discussions = [], pendingImages = [];
  Json? epic, conversation;
  String? productId, error;
  bool loading = true, saving = false, creating = false;
  String tab = 'Uitwerking',
      viewport = 'DESKTOP',
      epicFilter = 'active',
      questionFilter = 'OPEN';
  int loadSequence = 0;
  String? draftProductId;
  bool mobileConversation = false;
  bool get ownQuestions => widget.section == 'own-questions';
  bool get allProducts => productId == '__all__';
  bool closed(Json e) =>
      ['COMPLETED', 'CANCELLED', 'WITHDRAWN'].contains(e['status']);
  bool needsAttention(Json e) =>
      !closed(e) &&
      (([
                'AWAITING_APPROVAL',
                'AWAITING_PRODUCT_OWNER_APPROVAL',
                'AWAITING_FACTORY_OWNER_APPROVAL',
                'AVAILABLE',
              ].contains(e['status']) &&
              _map(e['review'])[architect
                      ? 'architectApproved'
                      : 'productOwnerApproved'] !=
                  true) ||
          questions.any(
            (q) =>
                q['status'] == 'OPEN' &&
                _text(q['epicLinkId']) == _text(e['id']),
          ));
  String productName(Object? id) =>
      products.where((p) => p.id == _text(id)).firstOrNull?.name ?? _text(id);
  String? screenKey;
  Timer? timer;
  final message = TextEditingController(), idea = TextEditingController();
  bool get architect => widget.role == 'ARCHITECT';
  bool get factory => widget.role == 'FACTORY_OWNER';
  String get actionRole => architect ? 'ARCHITECT' : 'PRODUCT_OWNER';
  List<Json> get openQuestions =>
      questions.where((q) => q['status'] == 'OPEN').toList();
  @override
  void initState() {
    super.initState();
    epicFilter = architect ? 'attention' : 'active';
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
    final sequence = ++loadSequence;
    try {
      final nextProducts = await widget.products.products();
      var id =
          productId ??
          widget.initialProductId ??
          (nextProducts.length > 1 ? '__all__' : nextProducts.firstOrNull?.id);
      if (widget.section == 'policy' && id == '__all__') {
        id = nextProducts.firstOrNull?.id;
      }
      if (id != '__all__' && !nextProducts.any((p) => p.id == id)) {
        id = nextProducts.firstOrNull?.id;
      }
      final selected = id == '__all__'
          ? nextProducts
          : nextProducts.where((p) => p.id == id).toList();
      final values = await Future.wait(
        selected.map(
          (p) async => await Future.wait([
            api.request('/api/products/${p.id}/epics'),
            api.request(
              '/api/products/${p.id}/conversations?includeMessages=false',
            ),
            api.request('/api/products/${p.id}/questions'),
          ]),
        ),
      );
      final nextEpics = values.expand((v) => _maps(v[0])).toList();
      final nextConversations = values.expand((v) => _maps(v[1])).toList();
      Json? nextConversation = conversation == null
          ? null
          : nextConversations
                .where((c) => _text(c['id']) == _text(conversation!['id']))
                .firstOrNull;
      final targetId = epic != null
          ? _text(epic!['id'])
          : !ownQuestions && nextConversation != null
          ? _text(
              nextConversation['epicId'] ??
                  _map(nextConversation['request'])['linkedEpicId'],
            )
          : '';
      final nextEpic = nextEpics
          .where((e) => _text(e['id']) == targetId)
          .firstOrNull;
      final currentProduct =
          nextEpic?['productId'] ?? (id == '__all__' ? null : id);
      Json nextPolicy = {}, nextEnvironment = {}, nextProgress = {};
      List<Json> nextDiscussions = [], nextVersions = [];
      if (currentProduct != null) {
        final config = await Future.wait([
          api.request('/api/products/${_text(currentProduct)}/governance'),
          api.request(
            '/api/products/${_text(currentProduct)}/test-configuration',
            optional: true,
          ),
        ]);
        nextPolicy = _map(config[0]);
        nextEnvironment = _map(config[1]);
      }
      if (nextEpic != null) {
        final detail = await Future.wait([
          api.request(
            '/api/epics/${_text(nextEpic['id'])}/discussions?includeMessages=false',
          ),
          api.request('/api/epics/${_text(nextEpic['id'])}/history'),
          api.request('/api/epics/${_text(nextEpic['id'])}/progress'),
        ]);
        nextDiscussions = _maps(detail[0]);
        nextVersions = _maps(detail[1]);
        nextProgress = _map(detail[2]);
        nextConversation = nextDiscussions
            .where((c) => c['status'] != 'CLOSED')
            .firstOrNull;
      }
      if (!mounted || sequence != loadSequence) return;
      setState(() {
        products = nextProducts;
        productId = id;
        epics = nextEpics;
        conversations = nextConversations;
        questions = values.expand((v) => _maps(v[2])).toList();
        policy = nextPolicy;
        environment = nextEnvironment;
        progress = nextProgress;
        epic = nextEpic;
        conversation = nextConversation;
        discussions = nextDiscussions;
        versions = nextVersions;
        loading = false;
        if (!silent) error = null;
      });
    } catch (e) {
      if (mounted && sequence == loadSequence) {
        setState(() {
          loading = false;
          error = '$e';
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
      mobileConversation = false;
      tab = architect ? 'Impact' : 'Uitwerking';
      conversation = null;
      discussions = [];
      creating = false;
      pendingImages = [];
      message.clear();
    });
    await load();
  }

  Future<void> addImages() async {
    try {
      final selected = await pickConversationImages();
      if (!mounted) return;
      if (pendingImages.length + selected.length > 6) {
        throw StateError('Voeg maximaal zes afbeeldingen per bericht toe.');
      }
      final total = [...pendingImages, ...selected].fold<int>(
        0,
        (sum, image) => sum + base64Decode(_text(image['base64'])).length,
      );
      if (total > 8 * 1024 * 1024) {
        throw StateError('Voeg maximaal 8 MB afbeeldingen per bericht toe.');
      }
      setState(() => pendingImages.addAll(selected));
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  Widget imagePicker({bool compact = false}) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      if (!compact) ...[
        button('Afbeeldingen toevoegen', addImages),
        const SizedBox(height: 8),
        const Text('PNG, JPEG of WebP · maximaal 6 beelden, 4 MB per beeld'),
      ],
      Wrap(
        spacing: 8,
        children: pendingImages
            .map(
              (f) => InputChip(
                avatar: Image.memory(
                  base64Decode(_text(f['base64'])),
                  width: 28,
                  height: 28,
                  fit: BoxFit.cover,
                ),
                label: Text(_text(f['filename'])),
                onDeleted: () => setState(() => pendingImages.remove(f)),
              ),
            )
            .toList(),
      ),
    ],
  );

  Future<void> send({String? preset}) => mutate(() async {
    final text = preset ?? message.text.trim();
    if (text.isEmpty) throw StateError('Beschrijf je wens of vraag.');
    final targetProduct = draftProductId ?? (allProducts ? null : productId);
    if (conversation == null) {
      if (epic == null && targetProduct == null) {
        throw StateError('Kies het project voor dit gesprek.');
      }
      final response = _map(
        await api.request(
          epic == null
              ? '/api/products/$targetProduct/conversations'
              : '/api/epics/${_text(epic!['id'])}/discussions',
          method: 'POST',
          body: {
            'title': text.substring(0, text.length.clamp(0, 150)),
            if (epic != null) 'role': actionRole,
            if (epic == null) 'purpose': ownQuestions ? 'QUESTION' : 'EPIC',
          },
        ),
      );
      conversation = _map(
        await api.request(
          '/api/conversations/${_text(response['id'])}?includeMessages=false',
        ),
      );
    }
    await api.request(
      '/api/conversations/${_text(conversation!['id'])}/messages',
      method: 'POST',
      body: {
        'text': text,
        'expectedVersion': conversation!['version'],
        'intent': 'AUTO',
        if (epic != null) 'expectedEpicVersion': epic!['version'],
        'images': pendingImages,
      },
    );
    message.clear();
    idea.clear();
    pendingImages = [];
    creating = false;
  });
  Future<String?> ask(String title, String label, {String initial = ''}) async {
    var draft = initial;
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 560,
          child: TextFormField(
            initialValue: initial,
            autofocus: true,
            minLines: 3,
            maxLines: 8,
            decoration: InputDecoration(labelText: label),
            onChanged: (value) => draft = value,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () {
              if (draft.trim().isNotEmpty) {
                Navigator.pop(context, draft.trim());
              }
            },
            child: const Text('Bevestigen'),
          ),
        ],
      ),
    );
    return value;
  }

  Future<void> review(String decision, {String? asRole}) async {
    if (decision == 'REQUEST_CHANGE') {
      setState(() {
        message.text = 'Pas deze epic aan: ';
        mobileConversation = true;
      });
      return;
    }
    final reviewRole = asRole ?? actionRole;
    final reviewingAsArchitect = reviewRole == 'ARCHITECT';
    final text = await ask(
      decision == 'APPROVE'
          ? reviewingAsArchitect
                ? 'Architectuur akkoord'
                : 'Functioneel akkoord'
          : decision == 'REQUEST_CHANGE'
          ? 'Aanpassing vragen'
          : 'Onderzoek vragen',
      'Toelichting',
      initial: decision == 'APPROVE'
          ? reviewingAsArchitect
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
          'role': reviewRole,
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
      setState(() {
        tab = 'Impact';
      });
      await send(
        preset:
            'Onderzoek voor de architect, zonder deze epic goed te keuren: $text',
      );
    }
  }

  Json? linkedEpic(Json? sourceConversation) {
    final linkedValue =
        sourceConversation?['epicId'] ??
        _map(sourceConversation?['request'])['linkedEpicId'];
    final linkedId = linkedValue == null ? '' : _text(linkedValue);
    if (linkedId.isEmpty) return null;
    return epics
        .where((candidate) => _text(candidate['id']) == linkedId)
        .firstOrNull;
  }

  Future<void> feedback({
    String? text,
    Json? screen,
    Json? artifact,
    Json? targetEpic,
    Json? contextConversation,
    bool openAfterSubmit = false,
  }) async {
    final target = targetEpic ?? epic;
    final value =
        text ??
        await ask(
          'Samen verder uitwerken',
          'Welke besproken wijzigingen wil je verwerken?',
          initial: message.text.trim(),
        );
    if (value == null || target == null) return;
    final sourceConversation = contextConversation ?? conversation;
    final transcript = _maps(sourceConversation?['messages']).reversed
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
        '/api/epics/${_text(target['id'])}/feedback',
        method: 'POST',
        body: {
          'text':
              '$value${contextText.isEmpty ? '' : '\n\nBesproken context (te onderzoeken, geen zelfstandig besluit):\n$contextText'}',
          'role': actionRole,
          'expectedVersion': target['version'],
          'screenKey': screen?['screenKey'],
          'viewport': screen == null ? null : viewport,
          'artifactName': artifact?['name'],
        },
      );
    });
    if (openAfterSubmit && error == null && mounted) {
      final currentTarget = epics
          .where((candidate) => _text(candidate['id']) == _text(target['id']))
          .firstOrNull;
      await openEpic(currentTarget ?? target);
    }
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
    contentPadding: const EdgeInsets.symmetric(vertical: 10),
    title: Text(_text(e['title'])),
    subtitle: Text(
      '${productName(e['productId'])} · ${label(e['status'])}\n${needsAttention(e) ? 'Jouw aandacht nodig' : 'Geen actie van jou nodig'} · inhoudsversie ${e['contentVersion'] ?? e['version']}',
    ),
    trailing: const Icon(Icons.chevron_right),
    onTap: () => openEpic(e),
  );
  @override
  Widget build(BuildContext context) {
    if (loading) return const Center(child: CircularProgressIndicator());
    if ((epic != null || conversation != null) && !creating) {
      return conversationWorkspace();
    }
    return SingleChildScrollView(
      padding: EdgeInsets.all(MediaQuery.sizeOf(context).width < 600 ? 16 : 30),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1200),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (products.isNotEmpty) projectSelector(),
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
              else if (widget.section == 'questions' && epic == null)
                questionPanel()
              else if (creating) ...[
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton(
                    onPressed: saving
                        ? null
                        : () => setState(() {
                            creating = false;
                          }),
                    child: Text(
                      ownQuestions ? '← Mijn vragen aan AI' : '← Mijn epics',
                    ),
                  ),
                ),
                Text(
                  ownQuestions ? 'Nieuwe vraag aan AI' : 'Nieuwe epic',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 16),
                panel(
                  ownQuestions
                      ? 'Wat wil je weten over de applicatie?'
                      : 'Beschrijf je wensen. AI maakt de eerste versie.',
                  [
                    DropdownButtonFormField<String>(
                      initialValue:
                          draftProductId ?? (allProducts ? null : productId),
                      decoration: const InputDecoration(
                        labelText: 'Project voor dit gesprek',
                      ),
                      items: products
                          .map(
                            (p) => DropdownMenuItem(
                              value: p.id,
                              child: Text(p.name),
                            ),
                          )
                          .toList(),
                      onChanged: (v) => setState(() => draftProductId = v),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: idea,
                      minLines: 5,
                      maxLines: 10,
                      decoration: const InputDecoration(
                        labelText: 'Beschrijf je wens of vraag',
                      ),
                    ),
                    const SizedBox(height: 14),
                    imagePicker(),
                    const SizedBox(height: 14),
                    button(
                      ownQuestions ? 'Stel vraag aan AI' : 'Maak eerste versie',
                      () => send(preset: idea.text.trim()),
                      primary: true,
                    ),
                  ],
                ),
              ] else if (epic != null)
                ...epicPanel()
              else if (conversation != null)
                chatPanel()
              else if (ownQuestions)
                ...ownQuestionHome()
              else
                ...home(),
            ],
          ),
        ),
      ),
    );
  }

  Widget projectSelector() => Padding(
    padding: const EdgeInsets.only(bottom: 20),
    child: DropdownButtonFormField<String>(
      key: ValueKey(productId),
      initialValue: productId,
      isExpanded: true,
      decoration: const InputDecoration(labelText: 'Project'),
      items: [
        if (products.length > 1 && widget.section != 'policy')
          const DropdownMenuItem(
            value: '__all__',
            child: Text('Alle projecten'),
          ),
        ...products.map(
          (p) => DropdownMenuItem(value: p.id, child: Text(p.name)),
        ),
      ],
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
  );

  void startConversation() => setState(() {
    creating = true;
    epic = null;
    conversation = null;
    discussions = [];
    idea.clear();
    message.clear();
    pendingImages = [];
    draftProductId = allProducts ? null : productId;
  });

  List<Widget> ownQuestionHome() => [
    Text(
      'Mijn vragen aan AI',
      style: Theme.of(context).textTheme.headlineMedium,
    ),
    text('Losse gesprekken over de werking van je applicatie.'),
    Align(
      alignment: Alignment.centerLeft,
      child: button('Nieuwe vraag', startConversation, primary: true),
    ),
    const SizedBox(height: 18),
    panel(
      'Gesprekken',
      conversations
          .where(
            (c) =>
                c['epicId'] == null &&
                _map(c['request'])['linkedEpicId'] == null &&
                c['purpose'] != 'EPIC',
          )
          .map(conversationRow)
          .toList(),
    ),
  ];
  Future<void> deleteConversation(Json c) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Gesprek verwijderen?'),
        content: Text('“${_text(c['title'])}” verdwijnt uit je gesprekken. Je kunt het daarna niet meer openen.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Annuleren')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Verwijderen')),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await mutate(() async {
      await api.request('/api/conversations/${_text(c['id'])}', method: 'DELETE', body: {'expectedVersion': c['version']});
      loadSequence++;
      conversations.removeWhere((item) => _text(item['id']) == _text(c['id']));
      if (_text(conversation?['id']) == _text(c['id'])) conversation = null;
    });
  }

  Widget conversationRow(Json c) => ListTile(
    title: Text(_text(c['title'])),
    subtitle: Text(
      '${productName(c['productId'])} · ${c['status'] == 'PROCESSING'
          ? 'AI werkt aan je bericht'
          : c['status'] == 'BLOCKED'
          ? 'Verwerking vraagt aandacht'
          : _map(c['request'])['status'] == 'ROUTING_FAILED'
          ? 'Uitwerking wordt opnieuw geprobeerd'
          : _map(c['request'])['status'] == 'ROUTING' || _map(c['request'])['status'] == 'APPROVED'
          ? 'AI maakt de epic'
          : 'Open gesprek'}',
    ),
    trailing: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (ownQuestions && c['request'] == null && c['epicId'] == null && c['purpose'] != 'EPIC')
          IconButton(
            tooltip: 'Gesprek verwijderen',
            onPressed: saving ? null : () => deleteConversation(c),
            icon: const Icon(Icons.delete_outline),
          ),
        const Icon(Icons.chevron_right),
      ],
    ),
    onTap: () => mutate(() async {
      conversation = _map(
        await api.request(
          '/api/conversations/${_text(c['id'])}?includeMessages=false',
        ),
      );
      discussions = [];
    }),
  );
  List<Widget> home() {
    final visible = epics
        .where(
          (e) =>
              epicFilter == 'all' ||
              (epicFilter == 'done'
                  ? closed(e)
                  : epicFilter == 'attention'
                  ? needsAttention(e)
                  : !closed(e)),
        )
        .toList();
    final drafts = conversations
        .where(
          (c) =>
              c['epicId'] == null &&
              _map(c['request'])['linkedEpicId'] == null &&
              (c['purpose'] == 'EPIC' || _map(c['request']).isNotEmpty),
        )
        .toList();
    return [
      Text(
        architect ? 'Epics' : 'Mijn epics',
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      text(
        architect
            ? 'Epics die een architectuurbeoordeling of jouw antwoord nodig hebben.'
            : 'Van eerste idee tot opgeleverde verbetering.',
      ),
      if (!architect)
        Align(
          alignment: Alignment.centerLeft,
          child: button('Nieuwe epic', startConversation, primary: true),
        ),
      const SizedBox(height: 18),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children:
            {
                  'active': 'Niet afgerond',
                  'attention': 'Mijn aandacht nodig',
                  'done': 'Afgerond',
                  'all': 'Alle epics',
                }.entries
                .map(
                  (entry) => ChoiceChip(
                    label: Text(entry.value),
                    selected: epicFilter == entry.key,
                    onSelected: (_) => setState(() => epicFilter = entry.key),
                  ),
                )
                .toList(),
      ),
      const SizedBox(height: 18),
      if (drafts.isNotEmpty && !architect && !['done'].contains(epicFilter))
        panel('Epics in voorbereiding', drafts.map(conversationRow).toList()),
      panel(
        'Epics',
        visible.isEmpty
            ? [text('Geen epics binnen dit filter.')]
            : visible.map(epicRow).toList(),
      ),
    ];
  }

  Widget storedImages(List<Json> images) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: images
        .map(
          (image) => Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(_text(image['filename'])),
                FutureBuilder<Uint8List>(
                  future: api.image(
                    '${AppConfiguration.backendUrl.replaceAll(RegExp(r'/$'), '')}/api/conversation-images/${_text(image['id'])}',
                  ),
                  builder: (context, snapshot) => snapshot.hasData
                      ? ConstrainedBox(
                          constraints: const BoxConstraints(maxHeight: 280),
                          child: Image.memory(
                            snapshot.data!,
                            fit: BoxFit.contain,
                          ),
                        )
                      : snapshot.hasError
                      ? const Text(
                          'Afbeelding niet beschikbaar. Ververs om opnieuw te proberen.',
                        )
                      : const LinearProgressIndicator(),
                ),
              ],
            ),
          ),
        )
        .toList(),
  );
  Widget chatPanel() {
    final c = conversation;
    final request = _map(c?['request']);
    final proposal = _map(c?['changeProposal']);
    final scope = epic != null
        ? '/api/epics/${_text(epic!['id'])}/messages'
        : '/api/conversations/${_text(c?['id'])}/messages';
    bool currentProposalMessage(Json m) =>
        epic != null &&
        proposal['status'] == 'READY' &&
        proposal['afterContentVersion'] == epic!['contentVersion'] &&
        m['sender'] == 'SYSTEM' &&
        _text(m['text']).contains(
          'bijgewerkt naar voorstelversie ${proposal['afterContentVersion']}.',
        );
    return ConversationTimeline(
      key: ValueKey(scope),
      revision:
          '${c?['version']}-${discussions.map((d) => d['version']).join('-')}',
      fetch: ({String? before, String? after}) async => _map(
        await api.request(
          '$scope?limit=30${before == null ? '' : '&before=${Uri.encodeQueryComponent(before)}'}${after == null ? '' : '&after=${Uri.encodeQueryComponent(after)}'}',
        ),
      ),
      title: epic != null
          ? 'Gesprek bij deze epic'
          : ownQuestions
          ? 'Mijn vraag aan AI'
          : 'Nieuwe epic uitwerken',
      status: c?['status'] == 'PROCESSING' || proposal['status'] == 'WORKING'
          ? const Text('AI werkt aan je bericht…')
          : const SizedBox.shrink(),
      introduction: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ...questions
              .where(
                (q) =>
                    epic != null &&
                    _text(q['epicLinkId']) == _text(epic!['id']),
              )
              .map(
                (q) => notice(
                  'Vraag van ${_text(q['agentRole'])}',
                  '${_text(q['question'])}\n${q['answer'] == null ? 'Beantwoord deze vraag onder Vragen voor mij.' : 'Antwoord: ${_text(q['answer'])}'}',
                ),
              ),
        ],
      ),
      messageBuilder: (m) => Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              m['sender'] == 'USER'
                  ? m['authorRole'] == 'ARCHITECT'
                        ? 'Architect'
                        : 'Product owner'
                  : m['sender'] == 'SYSTEM'
                  ? 'Product Factory'
                  : 'AI',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            const SizedBox(height: 5),
            if (!currentProposalMessage(m)) SelectableText(_text(m['text'])),
            storedImages(_maps(m['attachments'])),
            for (final image in _maps(m['images']))
              ChatAnswerImage(
                key: ValueKey('answer-image-${_text(image['id'])}'),
                image: image,
                bytes: api.image(
                  '${AppConfiguration.backendUrl.replaceAll(RegExp(r'/$'), '')}/api/advisor-images/${_text(image['id'])}',
                ),
              ),
            if (m['sender'] == 'SYSTEM' &&
                _text(m['text']).contains(
                  'bijgewerkt naar voorstelversie ${proposal['afterContentVersion']}.',
                )) ...[
              if (epic != null &&
                  proposal['status'] == 'READY' &&
                  proposal['afterContentVersion'] ==
                      epic!['contentVersion']) ...[
                notice(
                  'AI stelt versie ${proposal['afterContentVersion']} voor',
                  '${_text(proposal['summary']).isEmpty ? 'Bekijk de bijgewerkte inhoud en schermen.' : _text(proposal['summary'])} Je kunt via de chat verder bijstellen of vragen dit terug te draaien. Uitvoering blijft afhankelijk van de vereiste goedkeuringen.',
                ),
                if (c?['status'] != 'PROCESSING' &&
                    [
                      'AWAITING_APPROVAL',
                      'AWAITING_PRODUCT_OWNER_APPROVAL',
                      'AWAITING_FACTORY_OWNER_APPROVAL',
                      'AVAILABLE',
                      'NEEDS_RESEARCH',
                      'NEEDS_REFINEMENT',
                    ].contains(epic!['status']))
                  button(
                    'Voorstel terugdraaien',
                    () => mutate(() async {
                      await api.request(
                        '/api/conversations/${_text(c!['id'])}/revert-epic-change',
                        method: 'POST',
                        body: {
                          'expectedVersion': c['version'],
                          'expectedEpicVersion': epic!['version'],
                        },
                      );
                    }),
                  ),
              ],
            ],
          ],
        ),
      ),
      latest: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (c?['status'] == 'BLOCKED')
            notice(
              'Je bericht kon niet worden verwerkt',
              'Je bericht en beelden zijn bewaard. Probeer opnieuw. Als de epic intussen is veranderd, bekijk de huidige versie en verstuur je wens opnieuw.',
            ),
          if (c?['status'] == 'BLOCKED')
            button(
              'Opnieuw proberen',
              () => mutate(() async {
                await api.request(
                  '/api/conversations/${_text(c!['id'])}/retry',
                  method: 'POST',
                  body: {'expectedVersion': c['version']},
                );
              }),
            ),
          if (request.isNotEmpty && epic == null) ...[
            const Divider(height: 28),
            text(_text(_map(request['content'])['title'])),
            text(_text(_map(request['content'])['summary'])),
            if (['APPROVED', 'ROUTING'].contains(request['status']))
              notice(
                'Je epic wordt uitgewerkt',
                'AI maakt de inhoud, schermen en architectuurimpact. De epic verschijnt hier vanzelf.',
              ),
            if (request['status'] == 'ROUTING_FAILED')
              notice(
                'Uitwerking wordt opnieuw geprobeerd',
                'Je wens en afbeeldingen zijn bewaard. Je hoeft ze niet opnieuw in te voeren.',
              ),
            if (request['status'] == 'PROPOSED')
              button(
                'Maak eerste epicversie',
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
          ],
        ],
      ),
      composer: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (epic == null)
            TextButton(
              onPressed: () => setState(() {
                conversation = null;
                discussions = [];
              }),
              child: Text(
                ownQuestions ? '← Mijn vragen aan AI' : '← Mijn epics',
              ),
            ),
          if (c?['status'] != 'CLOSED' &&
              !(epic == null &&
                  ['APPROVED', 'ROUTING'].contains(request['status']))) ...[
            const SizedBox(height: 12),
            TextField(
              controller: message,
              enabled: !saving && c?['status'] != 'PROCESSING',
              minLines: 2,
              maxLines: 4,
              maxLength: epic == null ? 20000 : 8000,
              decoration: const InputDecoration(
                labelText: 'Stel een vraag of beschrijf je wens',
                counterText: '',
              ),
            ),
            const SizedBox(height: 12),
            imagePicker(compact: true),
            const SizedBox(height: 12),
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              spacing: 8,
              runSpacing: 8,
              children: [
                IconButton(
                  tooltip:
                      'Afbeeldingen toevoegen · PNG, JPEG of WebP · maximaal 6 beelden, 4 MB per beeld',
                  onPressed: saving || c?['status'] == 'PROCESSING'
                      ? null
                      : addImages,
                  icon: const Icon(Icons.attach_file),
                ),
                FilledButton(
                  onPressed: saving || c?['status'] == 'PROCESSING'
                      ? null
                      : () => send(),
                  child: const Text('Verstuur'),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  List<Widget> epicPanel() {
    final e = epic!;
    final review = _map(e['review']);
    final tabs = [
      'Uitwerking',
      'Schermen',
      'Impact',
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
            discussions = [];
          }),
          child: Text(
            widget.section == 'questions'
                ? '← Vragen voor mij'
                : architect
                ? '← Epics'
                : '← Mijn epics',
          ),
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
        Card(
          child: ExpansionTile(
            title: const Text('Wijziging in deze versie'),
            childrenPadding: const EdgeInsets.fromLTRB(18, 0, 18, 12),
            expandedCrossAxisAlignment: CrossAxisAlignment.start,
            children: [text(_text(_map(e['impact'])['changeSummary']))],
          ),
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
                label: Text(
                  t == 'Impact'
                      ? 'Architectuur'
                      : t == 'Uitwerking'
                      ? 'Inhoud'
                      : t,
                ),
                selected: tab == t,
                onSelected: (_) => setState(() => tab = t),
              ),
            )
            .toList(),
      ),
      const SizedBox(height: 20),
      if (tab == 'Uitwerking')
        dossier()
      else if (tab == 'Schermen')
        Column(
          children: [
            storedImages(
              discussions.expand((c) => _maps(c['referenceImages'])).toList(),
            ),
            uxPanel(),
          ],
        )
      else if (tab == 'Impact')
        impactPanel()
      else if (tab == 'Goedkeuring')
        reviewPanel()
      else
        progressPanel(),
    ];
  }

  Widget conversationWorkspace() => LayoutBuilder(
    builder: (context, constraints) {
      final narrow =
          constraints.maxWidth < 900 ||
          MediaQuery.textScalerOf(context).scale(16) > 24;
      final dossierView = SingleChildScrollView(
        primary: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: epic == null ? [] : epicPanel(),
        ),
      );
      return Padding(
        padding: EdgeInsets.all(narrow ? 12 : 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (products.isNotEmpty) projectSelector(),
            if (error != null)
              Text(
                error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            if (saving) const LinearProgressIndicator(),
            if (narrow && epic != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Wrap(
                  spacing: 8,
                  children: [
                    ChoiceChip(
                      label: const Text('Epic'),
                      selected: !mobileConversation,
                      onSelected: (_) =>
                          setState(() => mobileConversation = false),
                    ),
                    ChoiceChip(
                      label: const Text('Gesprek'),
                      selected: mobileConversation,
                      onSelected: (_) =>
                          setState(() => mobileConversation = true),
                    ),
                  ],
                ),
              ),
            Expanded(
              child: epic == null
                  ? chatPanel()
                  : narrow
                  ? IndexedStack(
                      index: mobileConversation ? 1 : 0,
                      children: [dossierView, chatPanel()],
                    )
                  : Row(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Expanded(flex: 3, child: dossierView),
                        const SizedBox(width: 20),
                        Expanded(flex: 2, child: chatPanel()),
                      ],
                    ),
            ),
          ],
        ),
      );
    },
  );

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
    const categories = {
      'DATABASE',
      'MIGRATION',
      'EXTERNAL_SYSTEM',
      'FRONTEND',
      'ACCESS',
      'PRODUCT_AI',
      'INFRASTRUCTURE',
    };
    final incomplete =
        !categories.every((c) => items.any((i) => i['category'] == c)) ||
        items.any(
          (i) => !['NONE', 'COMPATIBLE', 'MATERIAL'].contains(i['level']),
        );
    final changes = items
        .where(
          (i) =>
              i['level'] == 'MATERIAL' ||
              (i['level'] == 'COMPATIBLE' && i['category'] != 'FRONTEND'),
        )
        .toList();
    final aiChanged = ai['changed'] == true;
    final summary = incomplete
        ? 'De architectuurimpact is nog niet volledig bepaald.'
        : changes.any((i) => i['level'] == 'MATERIAL')
        ? 'Er zijn architectuurwijzigingen.'
        : changes.isNotEmpty || aiChanged
        ? 'Er zijn wijzigingen binnen de bestaande architectuur.'
        : 'Er is geen architectuurverandering.';
    return panel('Architectuur in één oogopslag', [
      Text(summary, style: Theme.of(context).textTheme.titleMedium),
      if (incomplete)
        text(
          'Niet alle onderdelen zijn beoordeeld. De onderbouwing vraagt nog uitwerking.',
        ),
      ...changes.map(
        (i) => text('${category(i['category'])}: ${_text(i['summary'])}'),
      ),
      if (aiChanged && !changes.any((i) => i['category'] == 'PRODUCT_AI'))
        text('Het AI-gebruik van het product verandert.'),
      if (items.isNotEmpty)
        ExpansionTile(
          key: ValueKey(
            'architecture-details-${epic!['id']}-${epic!['contentVersion']}',
          ),
          tilePadding: EdgeInsets.zero,
          title: const Text('Onderbouwing per onderdeel'),
          children: [
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
                      setState(() {
                        tab = 'Impact';
                      });
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
          ],
        ),
    ]);
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
    // The public EpicUxScreen contract serializes its viewport map as an object.
    final variants = _map(selected['artifacts']);
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
        () => setState(() {
          mobileConversation = true;
          message.text =
              'Pas scherm ${selected['screenKey']} ($viewport), inhoudsversie ${epic!['contentVersion']}, aan: ';
        }),
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
      if (!factory &&
          !closed(epic!) &&
          _map(epic!['review'])[architect
                  ? 'architectApproved'
                  : 'productOwnerApproved'] !=
              true &&
          !['NEEDS_REFINEMENT', 'NEEDS_RESEARCH'].contains(epic!['status']))
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
      if (factory)
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            if (r['productOwnerApproved'] != true)
              button(
                'Functioneel akkoord als superuser',
                () => review('APPROVE', asRole: 'PRODUCT_OWNER'),
                primary: true,
              ),
            if (r['architectApproved'] != true)
              button(
                'Architectuur akkoord als superuser',
                () => review('APPROVE', asRole: 'ARCHITECT'),
                primary: true,
              ),
            button(
              'Vraag functionele aanpassing',
              () => review('REQUEST_CHANGE', asRole: 'PRODUCT_OWNER'),
            ),
            button(
              'Vraag architectuuronderzoek',
              () => review('REQUEST_RESEARCH', asRole: 'ARCHITECT'),
            ),
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

  Widget questionPanel() {
    final visible = questions
        .where(
          (q) =>
              q['status'] == questionFilter &&
              (epic == null || _text(q['epicLinkId']) == _text(epic!['id'])),
        )
        .toList();
    return panel('Vragen voor mij', [
      if (epic == null)
        Wrap(
          spacing: 8,
          children: ['OPEN', 'ANSWERED']
              .map(
                (state) => ChoiceChip(
                  label: Text(state == 'OPEN' ? 'Openstaand' : 'Beantwoord'),
                  selected: questionFilter == state,
                  onSelected: (_) => setState(() => questionFilter = state),
                ),
              )
              .toList(),
        ),
      if (visible.isEmpty)
        text(
          questionFilter == 'OPEN'
              ? 'Je bent bij. AI heeft op dit moment geen vragen voor jou.'
              : 'Nog geen beantwoorde vragen.',
        ),
      ...visible.map(
        (q) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${productName(q['productId'])} · Van ${_text(q['agentRole'])}',
                style: Theme.of(context).textTheme.labelMedium,
              ),
              const SizedBox(height: 6),
              Text(
                _text(q['question']),
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              text(_text(q['context'])),
              if (q['epicLinkId'] != null && epic == null)
                TextButton(
                  onPressed: () async {
                    final target = epics
                        .where((e) => _text(e['id']) == _text(q['epicLinkId']))
                        .firstOrNull;
                    if (target != null) await openEpic(target);
                  },
                  child: Text(
                    'Open epic: ${_text(epics.where((e) => _text(e['id']) == _text(q['epicLinkId'])).firstOrNull?['title'])}',
                  ),
                ),
              if (q['answer'] != null)
                text('Jouw antwoord: ${_text(q['answer'])}'),
              if (q['status'] == 'OPEN')
                button('Beantwoord vraag', () async {
                  final answer = await ask('Je antwoord', _text(q['question']));
                  if (answer == null) return;
                  await mutate(() async {
                    await api.request(
                      '/api/products/questions/${_text(q['id'])}/answer-directly',
                      method: 'POST',
                      body: {'answer': answer, 'expectedVersion': q['version']},
                    );
                  });
                }, primary: true),
            ],
          ),
        ),
      ),
    ]);
  }

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
        final title = _text(epic!['title']);
        final project = _text(epic!['productId']);
        startConversation();
        idea.text = 'Vervolg op $title: ';
        setState(() {
          draftProductId = project;
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
                ? 'Beheer als factory owner de rollen, architectuurafspraken en afspraken over AI-gebruik van het product.'
                : 'Deze afspraken betreffen architectuur en AI-gebruik van het product.',
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
          ],
          if (widget.factory) const SizedBox(height: 16),
          ...[
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
                      if ((jobs.text.isNotEmpty &&
                              int.tryParse(jobs.text) == null) ||
                          (growth.text.isNotEmpty &&
                              int.tryParse(growth.text) == null) ||
                          (budget.text.isNotEmpty &&
                              double.tryParse(budget.text) == null)) {
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
                          },
                          'architectureRules': architecture.text,
                          'productAiRules': ai.text,
                          'maximumAdditionalJobsPerDay': int.tryParse(
                            jobs.text,
                          ),
                          'monthlyProductBudgetEuro': budget.text.trim().isEmpty
                              ? null
                              : budget.text.trim(),
                          'maximumGrowthPercent': int.tryParse(growth.text),
                          'automaticCategories': categories.toList(),
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
