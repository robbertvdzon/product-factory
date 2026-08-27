import 'dart:async';
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

String _backendResourceUrl(Object? value) {
  final path = _value(value);
  if (path.startsWith('http://') || path.startsWith('https://')) return path;
  final base = AppConfiguration.backendUrl.replaceAll(RegExp(r'/$'), '');
  return '$base${path.startsWith('/') ? path : '/$path'}';
}

Future<void> _openUxArtifact(
  BuildContext context,
  Map<String, Object?> artifact,
) => showDialog<void>(
  context: context,
  builder: (_) => _ZoomableUxArtifactDialog(artifact: artifact),
);

class _ZoomableUxArtifactDialog extends StatefulWidget {
  const _ZoomableUxArtifactDialog({required this.artifact});

  final Map<String, Object?> artifact;

  @override
  State<_ZoomableUxArtifactDialog> createState() =>
      _ZoomableUxArtifactDialogState();
}

class _ZoomableUxArtifactDialogState extends State<_ZoomableUxArtifactDialog> {
  final TransformationController _transformation = TransformationController();
  double _scale = 1;

  @override
  void dispose() {
    _transformation.dispose();
    super.dispose();
  }

  void _setScale(double scale) {
    final bounded = scale.clamp(.5, 8.0);
    _transformation.value = Matrix4.diagonal3Values(bounded, bounded, 1);
    setState(() => _scale = bounded);
  }

  @override
  Widget build(BuildContext context) => Dialog.fullscreen(
    child: Scaffold(
      appBar: AppBar(
        title: Text(_value(widget.artifact['name'])),
        actions: [
          IconButton(
            tooltip: 'Uitzoomen',
            onPressed: _scale > .5 ? () => _setScale(_scale / 1.5) : null,
            icon: const Icon(Icons.zoom_out),
          ),
          IconButton(
            tooltip: 'Zoom herstellen',
            onPressed: () => _setScale(1),
            icon: const Icon(Icons.center_focus_strong),
          ),
          IconButton(
            tooltip: 'Inzoomen',
            onPressed: _scale < 8 ? () => _setScale(_scale * 1.5) : null,
            icon: const Icon(Icons.zoom_in),
          ),
          IconButton(
            tooltip: 'Sluiten',
            onPressed: () => Navigator.pop(context),
            icon: const Icon(Icons.close),
          ),
        ],
      ),
      body: ColoredBox(
        color: Theme.of(context).colorScheme.surfaceContainerLowest,
        child: InteractiveViewer(
          transformationController: _transformation,
          minScale: .5,
          maxScale: 8,
          boundaryMargin: const EdgeInsets.all(200),
          trackpadScrollCausesScale: true,
          onInteractionUpdate: (_) => setState(
            () => _scale = _transformation.value.getMaxScaleOnAxis(),
          ),
          child: Center(
            child: Image.network(
              _backendResourceUrl(widget.artifact['uri']),
              fit: BoxFit.contain,
              errorBuilder: (context, error, stackTrace) => const Center(
                child: Text('UX-afbeelding kon niet worden geladen.'),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

List<Widget> _uxArtifactGallery(
  BuildContext context,
  List<Object?> rawArtifacts, {
  String title = 'UX-modellen',
}) {
  if (rawArtifacts.isEmpty) return const [];
  return [
    const SizedBox(height: 12),
    Text(title, style: Theme.of(context).textTheme.labelLarge),
    const SizedBox(height: 6),
    Wrap(
      spacing: 12,
      runSpacing: 12,
      children: rawArtifacts.map((raw) {
        final artifact = (raw as Map).cast<String, Object?>();
        return SizedBox(
          width: 380,
          child: Card(
            clipBehavior: Clip.antiAlias,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Tooltip(
                  message: 'Open UX-model en zoom in',
                  child: InkWell(
                    onTap: () => _openUxArtifact(context, artifact),
                    child: Stack(
                      alignment: Alignment.topRight,
                      children: [
                        Image.network(
                          _backendResourceUrl(artifact['uri']),
                          height: 240,
                          width: double.infinity,
                          fit: BoxFit.contain,
                          errorBuilder: (context, error, stackTrace) =>
                              const SizedBox(
                                height: 160,
                                child: Center(
                                  child: Text(
                                    'UX-afbeelding kon niet worden geladen.',
                                  ),
                                ),
                              ),
                        ),
                        const Padding(
                          padding: EdgeInsets.all(8),
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: Colors.black54,
                              shape: BoxShape.circle,
                            ),
                            child: Padding(
                              padding: EdgeInsets.all(7),
                              child: Icon(
                                Icons.open_in_full,
                                color: Colors.white,
                                size: 18,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(10),
                  child: SelectableText(_value(artifact['name'])),
                ),
              ],
            ),
          ),
        );
      }).toList(),
    ),
  ];
}

final RegExp _productIdPattern = RegExp(r'^[a-z0-9][a-z0-9-]{1,98}[a-z0-9]$');

String? _productIdError(String value) {
  final productId = value.trim();
  if (productId.isEmpty || _productIdPattern.hasMatch(productId)) return null;
  return 'Gebruik 3–100 kleine letters, cijfers of koppeltekens; begin en eindig zonder koppelteken.';
}

class _CreateProductDialog extends StatefulWidget {
  const _CreateProductDialog();

  @override
  State<_CreateProductDialog> createState() => _CreateProductDialogState();
}

class _CreateProductDialogState extends State<_CreateProductDialog> {
  final TextEditingController _name = TextEditingController();
  final TextEditingController _id = TextEditingController();
  String? _nameError;
  String? _idError;

  @override
  void dispose() {
    _name.dispose();
    _id.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Product aanmaken'),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        TextField(
          controller: _name,
          onChanged: (_) => setState(() => _nameError = null),
          decoration: InputDecoration(labelText: 'Naam', errorText: _nameError),
        ),
        TextField(
          controller: _id,
          onChanged: (_) => setState(() => _idError = null),
          decoration: InputDecoration(
            labelText: 'Stabiel ID (optioneel)',
            helperText: 'Bijvoorbeeld: hkh-autopilot',
            errorText: _idError,
          ),
        ),
      ],
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Annuleren'),
      ),
      FilledButton(onPressed: _submit, child: const Text('Aanmaken')),
    ],
  );

  void _submit() {
    final name = _name.text.trim();
    final id = _id.text.trim();
    final nameError = name.isEmpty ? 'Vul een productnaam in.' : null;
    final idError = _productIdError(id);
    if (nameError != null || idError != null) {
      setState(() {
        _nameError = nameError;
        _idError = idError;
      });
      return;
    }
    Navigator.pop(context, (name, id));
  }
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
    this.scheduleRuns = const [],
    required this.signals,
    required this.questions,
    required this.meetings,
    required this.decisions,
    required this.decisionArchive,
    required this.epics,
    required this.designSessions,
    required this.epicHistories,
    required this.stories,
    required this.backlog,
    required this.planningWorkItems,
    required this.planningSessions,
    required this.qualitySnapshot,
    required this.qualityHistory,
    required this.bugs,
    required this.verifications,
    required this.qualityWorkItems,
    required this.qualitySessions,
    this.dispatcherStatus = const {},
    this.deliveryAttempts = const [],
    this.dispatcherSessions = const [],
  });
  final ProductSummary product;
  final Map<String, Object?>? assignment;
  final Map<String, Object?>? testConfiguration;
  final List<Map<String, Object?>> schedules;
  final List<Map<String, Object?>> scheduleRuns;
  final List<Map<String, Object?>> signals;
  final List<Map<String, Object?>> questions;
  final List<Map<String, Object?>> meetings;
  final List<Map<String, Object?>> decisions;
  final List<Map<String, Object?>> decisionArchive;
  final List<Map<String, Object?>> epics;
  final List<Map<String, Object?>> designSessions;
  final Map<String, List<Map<String, Object?>>> epicHistories;
  final List<Map<String, Object?>> stories;
  final List<Map<String, Object?>> backlog;
  final List<Map<String, Object?>> planningWorkItems;
  final List<Map<String, Object?>> planningSessions;
  final Map<String, Object?>? qualitySnapshot;
  final List<Map<String, Object?>> qualityHistory;
  final List<Map<String, Object?>> bugs;
  final List<Map<String, Object?>> verifications;
  final List<Map<String, Object?>> qualityWorkItems;
  final List<Map<String, Object?>> qualitySessions;
  final Map<String, Object?> dispatcherStatus;
  final List<Map<String, Object?>> deliveryAttempts;
  final List<Map<String, Object?>> dispatcherSessions;
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
  Future<void> runProductPlanning(String productId);
  Future<void> requestManualReplan(String productId, String reason);
  Future<void> reprioritizeEpic(String productId, String epicId, String reason);
  Future<void> runQuality(String productId);
  Future<void> retryQualityWorkItem(String workItemId);
  Future<void> runDispatcher(String productId);
  Future<void> runScheduledProcess(String productId, String process);
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
      _get('/api/products/$id/stories'),
      _get('/api/products/$id/backlog'),
      _get('/api/products/$id/planning/work-items'),
      _get('/api/products/$id/planning/sessions'),
      _optional('/api/products/$id/quality/current'),
      _get('/api/products/$id/quality/history'),
      _get('/api/products/$id/bugs'),
      _get('/api/products/$id/verifications'),
      _get('/api/products/$id/quality/work-items'),
      _get('/api/products/$id/quality/sessions'),
      _get('/api/products/$id/dispatcher/status'),
      _get('/api/products/$id/dispatcher/attempts'),
      _get('/api/products/$id/dispatcher/sessions'),
      _get('/api/products/$id/schedule-runs'),
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
      stories: list(10),
      backlog: list(11),
      planningWorkItems: list(12),
      planningSessions: list(13),
      qualitySnapshot: (values[14] as Map?)?.cast<String, Object?>(),
      qualityHistory: list(15),
      bugs: list(16),
      verifications: list(17),
      qualityWorkItems: list(18),
      qualitySessions: list(19),
      dispatcherStatus: (values[20] as Map).cast<String, Object?>(),
      deliveryAttempts: list(21),
      dispatcherSessions: list(22),
      scheduleRuns: list(23),
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

  @override
  Future<void> runProductPlanning(String productId) =>
      _send('POST', '/api/products/$productId/planning/sessions/run', const {});

  @override
  Future<void> requestManualReplan(String productId, String reason) =>
      _send('POST', '/api/products/$productId/planning/replan', {
        'reason': reason,
        'linkedObjects': const <Object>[],
        'idempotencyKey': _key('manual-replan'),
      });

  @override
  Future<void> reprioritizeEpic(
    String productId,
    String epicId,
    String reason,
  ) => _send(
    'POST',
    '/api/products/$productId/planning/epics/$epicId/reprioritize',
    {
      'reason': reason,
      'priority': 90,
      'idempotencyKey': _key('epic-reprioritize'),
    },
  );

  @override
  Future<void> runQuality(String productId) =>
      _send('POST', '/api/products/$productId/quality/sessions/run', const {});

  @override
  Future<void> retryQualityWorkItem(String workItemId) =>
      _send('POST', '/api/quality/work-items/$workItemId/retry', const {});

  @override
  Future<void> runDispatcher(String productId) => _send(
    'POST',
    '/api/products/$productId/dispatcher/sessions/run',
    const {},
  );

  @override
  Future<void> runScheduledProcess(String productId, String process) =>
      switch (process) {
        'PRODUCT_DESIGN' => runProductDesign(productId),
        'PRODUCT_PLANNING' => runProductPlanning(productId),
        'QUALITY_ASSURANCE' => runQuality(productId),
        'SOFTWARE_FACTORY_DISPATCHER' => runDispatcher(productId),
        _ => throw const ProductFailure(400, 'Onbekend uitvoerend proces.'),
      };

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

enum ProductWorkspaceSection {
  overview,
  design,
  planning,
  quality,
  signals,
  meetings,
  decisions,
  settings,
  operation,
}

String _sectionEyebrow(ProductWorkspaceSection section) => switch (section) {
  ProductWorkspaceSection.overview => 'PRODUCT',
  ProductWorkspaceSection.design => 'PRODUCTONTWERP',
  ProductWorkspaceSection.planning => 'PRODUCTPLANNING',
  ProductWorkspaceSection.quality => 'KWALITEITSBEWAKING',
  ProductWorkspaceSection.signals => 'STAKEHOLDER',
  ProductWorkspaceSection.meetings => 'SAMENWERKING',
  ProductWorkspaceSection.decisions => 'RICHTING',
  ProductWorkspaceSection.settings => 'INSTELLINGEN',
  ProductWorkspaceSection.operation => 'OPERATIE',
};

String _sectionTitle(ProductWorkspaceSection section) => switch (section) {
  ProductWorkspaceSection.overview => 'Overzicht',
  ProductWorkspaceSection.design => 'Verbeteringen als complete epics',
  ProductWorkspaceSection.planning => 'Geprioriteerde backlog',
  ProductWorkspaceSection.quality => 'Kwaliteit',
  ProductWorkspaceSection.signals => 'Signalen',
  ProductWorkspaceSection.meetings => 'Overleggen',
  ProductWorkspaceSection.decisions => 'Besluiten',
  ProductWorkspaceSection.settings => 'Instellingen',
  ProductWorkspaceSection.operation => 'Runs, queues en leveringen',
};

String _sectionDescription(
  ProductWorkspaceSection section,
) => switch (section) {
  ProductWorkspaceSection.overview =>
    'Wat er nu gebeurt en wat aandacht vraagt.',
  ProductWorkspaceSection.design =>
    'Iedere epic bevat één duidelijke gebruikersverbetering, scope, succescriteria en UX.',
  ProductWorkspaceSection.planning =>
    'Alle open stories in de volgorde waarin Software Factory ze kan oppakken.',
  ProductWorkspaceSection.quality =>
    'Testwerk, bewezen bugs en controleerbare resultaten op één plek.',
  ProductWorkspaceSection.signals =>
    'Gebruikerssignalen en hun zichtbare verwerking.',
  ProductWorkspaceSection.meetings =>
    'Vragen van agents, gesprekken, notulen en expliciete uitkomsten.',
  ProductWorkspaceSection.decisions =>
    'Actuele richting, peildatum en volledige historie.',
  ProductWorkspaceSection.settings =>
    'Product, omgevingen, levering en automatisering op één plek.',
  ProductWorkspaceSection.operation =>
    'Processessies, AI-uitvoering en Software Factory-dispatch.',
};

class ProductWorkspacePage extends StatefulWidget {
  const ProductWorkspacePage({
    required this.gateway,
    this.section = ProductWorkspaceSection.overview,
    this.trailingContent,
    super.key,
  });
  final ProductGateway gateway;
  final ProductWorkspaceSection section;
  final Widget? trailingContent;
  @override
  State<ProductWorkspacePage> createState() => _ProductWorkspacePageState();
}

class _ProductWorkspacePageState extends State<ProductWorkspacePage> {
  List<ProductSummary> _products = const [];
  ProductSummary? _selected;
  ProductWorkspaceData? _data;
  bool _busy = true;
  bool _refreshingPlanning = false;
  Timer? _planningRefreshTimer;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadProducts();
    _planningRefreshTimer = Timer.periodic(
      const Duration(seconds: 4),
      (_) => _refreshPlanningProgress(),
    );
  }

  @override
  void dispose() {
    _planningRefreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _refreshPlanningProgress() async {
    final selected = _selected;
    final data = _data;
    final active = data?.planningSessions.any(
      (session) =>
          const {'RUNNING', 'WAITING_FOR_AI'}.contains(session['status']),
    );
    if (widget.section != ProductWorkspaceSection.planning ||
        selected == null ||
        active != true ||
        _busy ||
        _refreshingPlanning) {
      return;
    }
    _refreshingPlanning = true;
    try {
      final refreshed = await widget.gateway.workspace(selected);
      if (mounted) setState(() => _data = refreshed);
    } finally {
      _refreshingPlanning = false;
    }
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
    padding: EdgeInsets.fromLTRB(
      MediaQuery.sizeOf(context).width < 600 ? 20 : 42,
      MediaQuery.sizeOf(context).width < 600 ? 28 : 44,
      MediaQuery.sizeOf(context).width < 600 ? 20 : 42,
      64,
    ),
    child: Align(
      alignment: Alignment.topLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1180),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 24,
              runSpacing: 12,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _sectionEyebrow(widget.section),
                      style: Theme.of(context).textTheme.labelMedium?.copyWith(
                        color: Theme.of(context).colorScheme.primary,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 1.5,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      _sectionTitle(widget.section),
                      style: Theme.of(context).textTheme.displaySmall,
                    ),
                    const SizedBox(height: 8),
                    ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 680),
                      child: Text(_sectionDescription(widget.section)),
                    ),
                  ],
                ),
                if (widget.section == ProductWorkspaceSection.overview ||
                    widget.section == ProductWorkspaceSection.settings)
                  FilledButton.icon(
                    onPressed: _createProduct,
                    icon: const Icon(Icons.add),
                    label: const Text('Nieuw product'),
                  ),
              ],
            ),
            const SizedBox(height: 28),
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
            if (!_busy && _products.isEmpty) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(28),
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final copy = Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: const [
                          Text(
                            'Nog geen producten',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          SizedBox(height: 4),
                          Text(
                            'Maak het eerste product aan om richting, ontwerp, planning en kwaliteit te volgen.',
                          ),
                        ],
                      );
                      final action = FilledButton.icon(
                        onPressed: _createProduct,
                        icon: const Icon(Icons.add),
                        label: const Text('Product aanmaken'),
                      );
                      if (constraints.maxWidth < 520) {
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(Icons.inventory_2_outlined, size: 32),
                            const SizedBox(height: 16),
                            copy,
                            const SizedBox(height: 20),
                            action,
                          ],
                        );
                      }
                      return Row(
                        children: [
                          const Icon(Icons.inventory_2_outlined, size: 32),
                          const SizedBox(width: 18),
                          Expanded(child: copy),
                          const SizedBox(width: 18),
                          action,
                        ],
                      );
                    },
                  ),
                ),
              ),
              if (widget.trailingContent != null) ...[
                const SizedBox(height: 20),
                widget.trailingContent!,
              ],
            ],
            if (_products.isNotEmpty) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 18,
                    vertical: 12,
                  ),
                  child: DropdownButtonFormField<String>(
                    isExpanded: true,
                    initialValue: _selected?.id,
                    decoration: const InputDecoration(
                      labelText: 'Product',
                      prefixIcon: Icon(Icons.inventory_2_outlined),
                      border: InputBorder.none,
                      enabledBorder: InputBorder.none,
                      focusedBorder: InputBorder.none,
                      filled: false,
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
                ),
              ),
              const SizedBox(height: 22),
              if (_data != null) _sectionContent(_data!),
            ],
          ],
        ),
      ),
    ),
  );

  Widget _sectionContent(ProductWorkspaceData data) => switch (widget.section) {
    ProductWorkspaceSection.overview => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _overview(data),
        const SizedBox(height: 20),
        _overviewAttention(data),
      ],
    ),
    ProductWorkspaceSection.design => _design(data),
    ProductWorkspaceSection.planning => _planning(data),
    ProductWorkspaceSection.quality => _quality(data),
    ProductWorkspaceSection.signals => _signals(data),
    ProductWorkspaceSection.meetings => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [_questions(data), const SizedBox(height: 20), _meetings(data)],
    ),
    ProductWorkspaceSection.decisions => _decisions(data),
    ProductWorkspaceSection.settings => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _productControls(data),
        const SizedBox(height: 20),
        _assignment(data),
        const SizedBox(height: 20),
        _schedules(data),
        if (widget.trailingContent != null) ...[
          const SizedBox(height: 20),
          widget.trailingContent!,
        ],
      ],
    ),
    ProductWorkspaceSection.operation => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _operation(data),
        if (widget.trailingContent != null) ...[
          const SizedBox(height: 20),
          widget.trailingContent!,
        ],
      ],
    ),
  };

  Widget _overview(ProductWorkspaceData data) {
    final assignment = data.assignment;
    final goal = assignment?['goal']?.toString().trim();
    final currentEpic = data.epics.where((epic) {
      return const {
        'ACTIVE',
        'IN_PLANNING',
        'VERIFYING',
      }.contains(epic['status']);
    }).firstOrNull;
    final currentStory = data.backlog.where((story) {
      return story['status'] == 'IN_PROGRESS';
    }).firstOrNull;
    final epicStories = currentEpic == null
        ? const <Map<String, Object?>>[]
        : data.stories
              .where(
                (story) => _value(story['epicId']) == _value(currentEpic['id']),
              )
              .toList();
    final done = epicStories.where((story) => story['status'] == 'DONE').length;
    final progress = epicStories.isEmpty ? 0.0 : done / epicStories.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xffedf8f1), Color(0xfff8fbf7)],
            ),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xffd3e7da)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'PRODUCTDOEL',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: Theme.of(context).colorScheme.primary,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.4,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                goal?.isNotEmpty == true
                    ? goal!
                    : 'Leg het productdoel vast bij Instellingen.',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
            ],
          ),
        ),
        const SizedBox(height: 18),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
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
                            'NU',
                            style: Theme.of(context).textTheme.labelSmall
                                ?.copyWith(
                                  color: Theme.of(context).colorScheme.primary,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 1.4,
                                ),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            currentEpic?['title']?.toString() ??
                                'Nog geen actieve epic',
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                        ],
                      ),
                    ),
                    if (currentEpic != null)
                      Chip(label: Text('${currentEpic['status']}')),
                  ],
                ),
                if (currentEpic != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    '${_value(currentEpic['id'])} · $done van ${epicStories.length} stories opgeleverd',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 14),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(99),
                    child: LinearProgressIndicator(
                      value: progress,
                      minHeight: 7,
                      backgroundColor: const Color(0xffe8ede9),
                    ),
                  ),
                ],
                if (currentStory != null) ...[
                  const SizedBox(height: 18),
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: const Color(0xfff2f6f2),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.north_east, size: 20),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Software Factory bouwt',
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                              Text(
                                '${_value(currentStory['id'])} · ${currentStory['title']}',
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                            ],
                          ),
                        ),
                        const Chip(label: Text('In uitvoering')),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _overviewAttention(ProductWorkspaceData data) {
    final openSignals = data.signals
        .where((signal) => signal['status'] != 'PROCESSED')
        .toList();
    final openQuestions = data.questions
        .where((question) => question['status'] == 'OPEN')
        .toList();
    final blockedWork = data.qualityWorkItems.where((item) {
      return const {'BLOCKED', 'FAILED'}.contains(item['status']);
    }).toList();
    final total =
        openSignals.length + openQuestions.length + blockedWork.length;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('AANDACHT', style: Theme.of(context).textTheme.labelSmall),
            const SizedBox(height: 6),
            Text(
              total == 0 ? 'Geen open punten' : '$total open punten',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            if (total == 0)
              const Text('Er zijn nu geen blokkades, vragen of open signalen.')
            else ...[
              ...blockedWork
                  .take(3)
                  .map(
                    (item) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.error_outline),
                      title: Text('${item['type']} · ${item['status']}'),
                      subtitle: Text('${item['explanation']}'),
                    ),
                  ),
              ...openQuestions
                  .take(3)
                  .map(
                    (question) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.question_answer_outlined),
                      title: Text(_value(question['question'])),
                      subtitle: const Text('Vraag van een agent'),
                    ),
                  ),
              ...openSignals
                  .take(3)
                  .map(
                    (signal) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.radio_button_unchecked),
                      title: Text(_value(signal['text'])),
                      subtitle: const Text('Open gebruikerssignaal'),
                    ),
                  ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _productControls(ProductWorkspaceData data) => Card(
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
            label: Text('Ontwerp, planning en kwaliteit actief'),
          ),
        ],
      ),
    ),
  );

  Widget _operation(
    ProductWorkspaceData data,
  ) => _section('Operatie', Icons.monitor_heart_outlined, [
    Text('Processessies', style: Theme.of(context).textTheme.titleMedium),
    const SizedBox(height: 8),
    ...[
      ...data.designSessions.map((session) => ('Ontwerp', session)),
      ...data.planningSessions.map((session) => ('Planning', session)),
      ...data.qualitySessions.map((session) => ('Kwaliteit', session)),
      ...data.dispatcherSessions.map((session) => ('Dispatcher', session)),
    ].map(
      (entry) => ListTile(
        contentPadding: EdgeInsets.zero,
        leading: const Icon(Icons.play_circle_outline),
        title: Text('${entry.$1} · ${entry.$2['status']}'),
        subtitle: Text(
          '${entry.$2['resultSummary'] ?? entry.$2['blockedReason'] ?? _value(entry.$2['id'])}',
        ),
      ),
    ),
    const Divider(height: 32),
    Text(
      'Software Factory-dispatch',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    const SizedBox(height: 8),
    ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(
        data.dispatcherStatus['blocked'] == true
            ? Icons.error_outline
            : Icons.sync_alt,
      ),
      title: Text(
        data.dispatcherStatus['blocked'] == true
            ? 'Dispatch geblokkeerd'
            : 'Dispatcher gereed',
      ),
      subtitle: Text(
        '${data.dispatcherStatus['blockedReason'] ?? 'Geen blijvende technische blokkade.'}',
      ),
    ),
    ...data.deliveryAttempts.map(
      (attempt) => ExpansionTile(
        tilePadding: EdgeInsets.zero,
        title: Text(
          '${attempt['status']} · ${attempt['externalStoryId'] ?? _value(attempt['storyId'])}',
        ),
        subtitle: Text('Poging ${attempt['attemptCount']}'),
        childrenPadding: const EdgeInsets.only(bottom: 16),
        expandedCrossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText('Reservering ${attempt['reservationId']}'),
          Text('Externe status ${attempt['externalStatus'] ?? 'onbekend'}'),
          Text('Retry ${attempt['retryAfter'] ?? 'niet gepland'}'),
          if (attempt['lastErrorCode'] != null)
            Text('${attempt['lastErrorCode']}: ${attempt['lastErrorMessage']}'),
        ],
      ),
    ),
  ]);

  Widget _planning(
    ProductWorkspaceData data,
  ) => _section('Planning', Icons.account_tree_outlined, [
    Wrap(
      alignment: WrapAlignment.end,
      spacing: 8,
      runSpacing: 8,
      children: [
        OutlinedButton.icon(
          onPressed: () => _planningReasonAction(data.product.id),
          icon: const Icon(Icons.reorder),
          label: const Text('Handmatige herplanning'),
        ),
        FilledButton.icon(
          onPressed: () => _runPlanning(data.product.id),
          icon: const Icon(Icons.play_arrow),
          label: const Text('Planning starten of hervatten'),
        ),
        FilledButton.icon(
          onPressed: () => _dispatchNow(data),
          icon: const Icon(Icons.send_outlined),
          label: Text(
            data.product.dispatchingEnabled
                ? 'Nu versturen of bijwerken'
                : 'Dispatching aanzetten en versturen',
          ),
        ),
      ],
    ),
    const SizedBox(height: 12),
    Text(
      'Productbrede backlog',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    if (data.backlog.isEmpty)
      const Text('De backlog is leeg.')
    else
      ...data.backlog.map(
        (story) => ExpansionTile(
          leading: CircleAvatar(child: Text('${story['sequenceNumber']}')),
          title: Text('${story['title']} · ${story['status']}'),
          subtitle: Text('${story['summary']}'),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SelectableText(
              'Story ${_value(story['id'])} · versie ${story['version']}',
            ),
            Text(
              'Type: ${story['type']} · Epic ${_value(story['epicId'])} v${story['epicVersion']}',
            ),
            const SizedBox(height: 8),
            Text('${story['content']}'),
            const SizedBox(height: 8),
            Text(
              'Acceptatiecriteria',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            ...(story['acceptanceCriteria'] as List? ?? const []).map(
              (criterion) => Text('• $criterion'),
            ),
            if (story['uxDesign'] != null) Text('UX: ${story['uxDesign']}'),
            ..._uxArtifactGallery(
              context,
              (story['uxArtifacts'] as List? ?? const <Object?>[])
                  .cast<Object?>(),
              title: 'UX-modellen bij deze story',
            ),
            Text(
              'Dependencies: ${(story['dependencies'] as List? ?? const []).map(_value).join(', ')}',
            ),
            Text(
              'Prioriteit: ${story['priorityReason'] ?? 'Planner-volgorde'}',
            ),
            if (story['dispatchReservationId'] != null)
              Text(
                'Wordt verstuurd · reservering ${story['dispatchReservationId']}',
              ),
            if (story['externalStoryId'] != null)
              SelectableText(
                'Software Factory: ${story['externalStoryId']} · reservering ${story['dispatchReservationStatus']}',
              ),
            if (story['deliveredCommitSha'] != null)
              SelectableText('Oplevercommit ${story['deliveredCommitSha']}'),
            if (story['deliveredCommitSha'] != null &&
                story['verificationId'] == null)
              const Text('Wacht op deployment of kwaliteitscontrole'),
            if (story['verificationId'] != null)
              Text(
                'Getest: ${story['verificationPassed'] == true ? 'geslaagd' : 'afgekeurd'} · verificatie ${_value(story['verificationId'])}',
              ),
          ],
        ),
      ),
    const Divider(height: 28),
    Text(
      'Geannuleerd en opgeleverd',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    ...data.stories
        .where(
          (story) => !const {'TODO', 'IN_PROGRESS'}.contains(story['status']),
        )
        .map(
          (story) => ListTile(
            leading: Icon(
              story['status'] == 'DONE'
                  ? Icons.done_all
                  : Icons.cancel_outlined,
            ),
            title: Text('${story['title']} · ${story['status']}'),
            subtitle: Text(
              '${story['cancellationReason'] ?? story['deliveredCommitSha'] ?? story['summary']}',
            ),
          ),
        ),
    const Divider(height: 28),
    Text(
      'Werkqueue en sessies',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    ...data.planningWorkItems.map(
      (item) => ListTile(
        dense: true,
        leading: const Icon(Icons.playlist_add_check),
        title: Text('${item['type']} · ${item['status']}'),
        subtitle: Text('${item['explanation']}'),
      ),
    ),
    ...data.planningSessions.map(
      (session) => ListTile(
        dense: true,
        leading: Icon(
          session['status'] == 'BLOCKED'
              ? Icons.error_outline
              : Icons.schema_outlined,
        ),
        title: Text('${session['status']} · ${_value(session['id'])}'),
        subtitle: Text(
          '${session['resultSummary'] ?? session['blockedReason'] ?? 'Planner-AI wordt duurzaam gevolgd.'}\n'
          '${(session['aiTaskIds'] as List? ?? const []).length} AI-taak/taken · Git ${session['repositoryCommitSha'] ?? 'nog niet bevroren'}',
        ),
      ),
    ),
    const Divider(height: 28),
    Text(
      'Software Factory-dispatcher',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    Card(
      color: data.dispatcherStatus['blocked'] == true
          ? Theme.of(context).colorScheme.errorContainer
          : null,
      child: ListTile(
        leading: Icon(
          data.dispatcherStatus['blocked'] == true
              ? Icons.error_outline
              : Icons.sync_alt,
        ),
        title: Text(
          data.dispatcherStatus['blocked'] == true
              ? 'Dispatch geblokkeerd'
              : 'Dispatcher gereed',
        ),
        subtitle: Text(
          '${data.dispatcherStatus['blockedReason'] ?? 'Geen blijvende technische blokkade.'}\n'
          'Extern ${data.dispatcherStatus['externalStoryId'] ?? 'geen story'} · ${data.dispatcherStatus['externalStatus'] ?? 'geen status'} · retry ${data.dispatcherStatus['retryAfter'] ?? 'niet gepland'}',
        ),
        isThreeLine: true,
      ),
    ),
    ...data.deliveryAttempts.map(
      (attempt) => ExpansionTile(
        leading: const Icon(Icons.local_shipping_outlined),
        title: Text(
          '${attempt['status']} · ${attempt['externalStoryId'] ?? 'nog geen storyKey'}',
        ),
        subtitle: Text(
          'Story ${_value(attempt['storyId'])} · poging ${attempt['attemptCount']} · lokaal ${attempt['localCommandStatus']}',
        ),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        expandedCrossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText('Reservering ${attempt['reservationId']}'),
          SelectableText('Idempotentiesleutel ${attempt['idempotencyKey']}'),
          SelectableText('Pakkethash ${attempt['packageHash']}'),
          Text('Externe status ${attempt['externalStatus'] ?? 'onbekend'}'),
          Text('Retry ${attempt['retryAfter'] ?? 'niet gepland'}'),
          if (attempt['lastErrorCode'] != null)
            Text('${attempt['lastErrorCode']}: ${attempt['lastErrorMessage']}'),
          if (attempt['deliveredCommitSha'] != null)
            SelectableText('Oplevercommit ${attempt['deliveredCommitSha']}'),
        ],
      ),
    ),
    ...data.dispatcherSessions.map(
      (session) => ListTile(
        dense: true,
        leading: const Icon(Icons.history),
        title: Text('${session['status']} · ${_value(session['id'])}'),
        subtitle: Text(
          '${session['resultSummary'] ?? session['blockedReason'] ?? 'Dispatchersessie actief.'}\n'
          '${(session['implementation'] as Map?)?['artifact'] ?? 'software-factory-dispatcher-impl'}',
        ),
      ),
    ),
  ]);

  Future<void> _planningReasonAction(String productId) async {
    final controller = TextEditingController();
    final reason = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Handmatige herplanning'),
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
            child: const Text('Werk klaarzetten'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (reason == null || reason.isEmpty) return;
    await _mutate(() => widget.gateway.requestManualReplan(productId, reason));
  }

  Future<void> _runPlanning(String productId) async {
    final accepted = await _mutate(
      () => widget.gateway.runProductPlanning(productId),
    );
    if (accepted && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Planning is gestart of hervat. De voortgang wordt automatisch bijgewerkt.',
          ),
        ),
      );
    }
  }

  Future<void> _dispatchNow(ProductWorkspaceData data) async {
    if (!data.product.dispatchingEnabled) {
      final enable = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Dispatching staat uit'),
          content: const Text(
            'Wil je dispatching voor dit product aanzetten en de eerste uitvoerbare story nu naar Software Factory versturen?',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Annuleren'),
            ),
            FilledButton.icon(
              onPressed: () => Navigator.pop(context, true),
              icon: const Icon(Icons.send_outlined),
              label: const Text('Aanzetten en versturen'),
            ),
          ],
        ),
      );
      if (enable != true) return;
    }
    final sent = await _mutate(() async {
      if (!data.product.dispatchingEnabled) {
        await widget.gateway.setDispatching(data.product, true);
      }
      await widget.gateway.runDispatcher(data.product.id);
    });
    if (sent && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Dispatcher uitgevoerd. De externe story en status zijn bijgewerkt.',
          ),
        ),
      );
    }
  }

  Widget _quality(
    ProductWorkspaceData data,
  ) => _section('Kwaliteitsbewaking', Icons.verified_outlined, [
    Align(
      alignment: Alignment.centerRight,
      child: FilledButton.icon(
        onPressed: () =>
            _mutate(() => widget.gateway.runQuality(data.product.id)),
        icon: const Icon(Icons.play_arrow),
        label: const Text('Kwaliteit starten of hervatten'),
      ),
    ),
    if (data.qualitySnapshot == null)
      const Text('Nog geen werkelijk getest kwaliteitsbeeld.')
    else
      Card(
        child: ListTile(
          leading: const Icon(Icons.fact_check_outlined),
          title: Text(
            '${data.qualitySnapshot!['environment']} · ${data.qualitySnapshot!['capturedAt']}',
          ),
          subtitle: Text(
            'Geteste revision ${data.qualitySnapshot!['productRevision']}\n'
            'Open bugs: ${data.qualitySnapshot!['openBugsBySeverity']}\n'
            'Risico’s: ${(data.qualitySnapshot!['risks'] as List? ?? const []).join(', ')}',
          ),
          isThreeLine: true,
        ),
      ),
    const Divider(height: 28),
    Text('Werk en retries', style: Theme.of(context).textTheme.titleMedium),
    if (data.qualityWorkItems.isEmpty)
      const Text('Geen kwaliteitswerk in de queue.')
    else
      ...data.qualityWorkItems.map(
        (item) => ListTile(
          leading: Icon(
            item['status'] == 'DONE'
                ? Icons.check_circle_outline
                : item['status'] == 'BLOCKED' || item['status'] == 'FAILED'
                ? Icons.warning_amber_outlined
                : Icons.hourglass_top,
          ),
          title: Text(
            '${item['type']} · ${item['status']}${item['attentionNeeded'] == true ? ' · Aandacht nodig' : ''}',
          ),
          subtitle: Text(
            '${item['blockedReason'] ?? item['result'] ?? 'Gericht testwerk staat klaar.'}\n'
            'Poging ${item['attemptCount']} · retry ${item['retryAfter'] ?? 'niet gepland'}',
          ),
          trailing: item['retryable'] == true
              ? TextButton(
                  onPressed: () => _mutate(
                    () =>
                        widget.gateway.retryQualityWorkItem(_value(item['id'])),
                  ),
                  child: const Text('Retry now'),
                )
              : null,
          isThreeLine: true,
        ),
      ),
    const Divider(height: 28),
    Text('Verificaties', style: Theme.of(context).textTheme.titleMedium),
    ...data.verifications.map(
      (verification) => ExpansionTile(
        title: Text(
          '${verification['targetType']} · ${verification['outcome']}',
        ),
        subtitle: Text(
          '${_value(verification['targetId'])} v${verification['targetVersion']} · ${verification['environment']}',
        ),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        expandedCrossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText(
            'Verificatie ${_value(verification['id'])} · geteste revision ${verification['testedRevision'] ?? 'niet beschikbaar'}',
          ),
          Text(
            'Controles: ${(verification['checks'] as List? ?? const []).join(', ')}',
          ),
          Text(
            'Bewijs: ${(verification['evidence'] as Map?)?['description'] ?? 'Geen publiek bewijs'}',
          ),
          if (verification['blockedReason'] != null)
            Text('Blokkade: ${verification['blockedReason']}'),
        ],
      ),
    ),
    const Divider(height: 28),
    Text('Bugs', style: Theme.of(context).textTheme.titleMedium),
    ...data.bugs.map(
      (bug) => ExpansionTile(
        title: Text('${bug['title']} · ${bug['severity']} · ${bug['status']}'),
        subtitle: Text('${bug['summary']}'),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        expandedCrossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Werkelijk: ${bug['actualBehaviour']}'),
          Text('Verwacht: ${bug['expectedBehaviour']}'),
          Text(
            'Reproduceren: ${(bug['reproductionSteps'] as List? ?? const []).join(' → ')}',
          ),
          Text(
            'Bewijs: ${(bug['evidence'] as Map?)?['description'] ?? 'Geen publiek bewijs'}',
          ),
        ],
      ),
    ),
    const Divider(height: 28),
    Text('Processessies', style: Theme.of(context).textTheme.titleMedium),
    ...data.qualitySessions.map(
      (session) => ListTile(
        leading: const Icon(Icons.science_outlined),
        title: Text('${session['status']} · ${_value(session['id'])}'),
        subtitle: Text(
          '${session['resultSummary'] ?? session['blockedReason'] ?? 'Tester-AI wordt duurzaam gevolgd.'}\n'
          '${(session['aiTaskIds'] as List? ?? const []).length} AI-taak/taken · Git ${session['repositoryCommitSha'] ?? 'nog niet bevroren'}',
        ),
      ),
    ),
  ]);

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
            if (epic['status'] == 'NEEDS_RESEARCH') ...[
              const SizedBox(height: 8),
              Card(
                color: Theme.of(context).colorScheme.tertiaryContainer,
                child: const ListTile(
                  leading: Icon(Icons.manage_search_outlined),
                  title: Text('Nog niet klaar voor planning'),
                  subtitle: Text(
                    'Productontwerp werkt eerst bronnen, open vragen en UX-modellen uit. Productplanning kan deze epic nog niet claimen.',
                  ),
                ),
              ),
            ],
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
            ..._uxArtifactGallery(
              context,
              (epic['uxArtifacts'] as List? ?? const <Object?>[])
                  .cast<Object?>(),
            ),
            const SizedBox(height: 8),
            Text('Gereedheid', style: Theme.of(context).textTheme.labelLarge),
            Text(
              (epic['readiness'] as Map?)?['readyForPlanning'] == true
                  ? 'Gereed voor Productplanning'
                  : 'Nog niet gereed voor Productplanning',
            ),
            ...((epic['readiness'] as Map?)?['unmetConditions'] as List? ??
                    const [])
                .map((condition) => Text('• $condition')),
            ...((epic['readiness'] as Map?)?['openQuestions'] as List? ??
                    const [])
                .map((question) => Text('Open vraag: $question')),
            const SizedBox(height: 8),
            Text(
              'Onderzochte bronnen',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            if ((epic['researchSources'] as List? ?? const []).isEmpty)
              const Text('Nog geen concrete externe bronnen onderzocht.')
            else
              ...(epic['researchSources'] as List).map((raw) {
                final source = (raw as Map).cast<String, Object?>();
                return ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    source['status'] == 'VALIDATED'
                        ? Icons.verified_outlined
                        : source['status'] == 'BLOCKED'
                        ? Icons.block_outlined
                        : Icons.travel_explore_outlined,
                  ),
                  title: Text('${source['name']} · ${source['status']}'),
                  subtitle: SelectableText(
                    '${source['provider']}\n${source['coverage']}\n'
                    'Toegang: ${source['accessMethod']} · Licentie: ${source['license']}\n'
                    '${source['validationEvidence']}\n${source['uri']}',
                  ),
                );
              }),
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
            if (const {'NEEDS_RESEARCH', 'AVAILABLE'}.contains(epic['status']))
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
            if (const {
              'AVAILABLE',
              'IN_PLANNING',
              'ACTIVE',
            }.contains(epic['status']))
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: () => _reprioritizeEpic(epic),
                  icon: const Icon(Icons.priority_high),
                  label: const Text('Voorrang geven'),
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

  Future<void> _reprioritizeEpic(Map<String, Object?> epic) async {
    await _textAction(
      'Epic voorrang geven',
      'Verplichte reden',
      (reason) => widget.gateway.reprioritizeEpic(
        _selected!.id,
        _value(epic['id']),
        reason,
      ),
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
      'Ieder proces heeft een eigen ritme. Uitgeschakeld betekent alleen dat het niet automatisch start; Nu starten blijft beschikbaar.',
    ),
    const SizedBox(height: 8),
    ...data.schedules.map(
      (s) => Card(
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: ListTile(
            title: Text(_value(s['process']).replaceAll('_', ' ')),
            subtitle: Text(
              '${_humanPattern((s['pattern'] as Map?)?.cast<String, Object?>())}\n'
              '${s['timezone']} · volgende start ${s['nextRunAt'] ?? 'uitgeschakeld'} · versie ${s['version']}',
            ),
            isThreeLine: true,
            trailing: Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                TextButton(
                  onPressed: () => _mutate(
                    () => widget.gateway.runScheduledProcess(
                      data.product.id,
                      _value(s['process']),
                    ),
                  ),
                  child: const Text('Nu starten'),
                ),
                Switch(
                  value: s['enabled'] == true,
                  onChanged: (enabled) =>
                      _schedule(data.product.id, s, enabled),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
    const Divider(height: 28),
    Text(
      'Recente automatische starts',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    if (data.scheduleRuns.isEmpty)
      const Text('Nog geen automatische start geclaimd.')
    else
      ...data.scheduleRuns.map(
        (run) => ListTile(
          leading: Icon(
            run['status'] == 'SUCCEEDED'
                ? Icons.check_circle_outline
                : run['status'] == 'SKIPPED'
                ? Icons.skip_next_outlined
                : Icons.error_outline,
          ),
          title: Text('${run['process']} · ${run['status']}'),
          subtitle: Text(
            'Gepland ${run['scheduledFor']} · ${run['resultSummary'] ?? run['errorCode'] ?? 'geclaimd'}',
          ),
        ),
      ),
  ]);

  String _humanPattern(Map<String, Object?>? pattern) {
    if (pattern == null) return 'Geen ritme ingesteld';
    final interval = pattern['intervalMinutes'];
    if (interval != null) return 'Elke $interval minuten';
    final rules = (pattern['weeklyRules'] as List? ?? const [])
        .whereType<Map>();
    if (rules.isEmpty) return 'Geen ritme ingesteld';
    return rules
        .map((rule) {
          final days = (rule['days'] as List? ?? const []).join(', ');
          final times = (rule['times'] as List? ?? const []).join(', ');
          return '$days om $times';
        })
        .join(' · ');
  }

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
    final product = await showDialog<(String, String)>(
      context: context,
      builder: (_) => const _CreateProductDialog(),
    );
    if (product != null) {
      await _mutate(() => widget.gateway.createProduct(product.$1, product.$2));
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

  Future<bool> _mutate(Future<void> Function() operation) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await operation();
      await _loadProducts(_selected?.id);
      return true;
    } on ProductFailure catch (e) {
      if (mounted) {
        setState(() {
          _error = e.status == 409
              ? '${e.message} Ververs het product en probeer opnieuw.'
              : e.message;
          _busy = false;
        });
      }
      return false;
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = 'De opdracht kon niet worden uitgevoerd.';
          _busy = false;
        });
      }
      return false;
    }
  }
}
