import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'configuration.dart';
import 'http_client_factory.dart';
import 'page_refresh.dart';

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
        title: SelectableText(_value(widget.artifact['name'])),
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
                child: SelectableText('UX-afbeelding kon niet worden geladen.'),
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
    SelectableText(title, style: Theme.of(context).textTheme.labelLarge),
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
                                  child: SelectableText(
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
    title: const SelectableText('Product aanmaken'),
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
    this.epicApprovalMode = 'AUTOMATIC',
  });
  factory ProductSummary.fromJson(Map<String, Object?> json) => ProductSummary(
    id: _value(json['id']),
    name: _value(json['name']),
    status: _value(json['status']),
    dispatchingEnabled: json['dispatchingEnabled'] == true,
    version: (json['version'] as num?)?.toInt() ?? 0,
    epicApprovalMode: _value(json['epicApprovalMode']).isEmpty
        ? 'AUTOMATIC'
        : _value(json['epicApprovalMode']),
  );
  final String id;
  final String name;
  final String status;
  final bool dispatchingEnabled;
  final int version;
  final String epicApprovalMode;
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

String _fingerprintProducts(List<ProductSummary> products) => jsonEncode(
  products
      .map(
        (product) => {
          'id': product.id,
          'name': product.name,
          'status': product.status,
          'dispatchingEnabled': product.dispatchingEnabled,
          'version': product.version,
          'epicApprovalMode': product.epicApprovalMode,
        },
      )
      .toList(),
);

String _fingerprintWorkspace(ProductWorkspaceData data) => jsonEncode({
  'product': {
    'id': data.product.id,
    'name': data.product.name,
    'status': data.product.status,
    'dispatchingEnabled': data.product.dispatchingEnabled,
    'version': data.product.version,
    'epicApprovalMode': data.product.epicApprovalMode,
  },
  'assignment': data.assignment,
  'testConfiguration': data.testConfiguration,
  'schedules': data.schedules,
  'scheduleRuns': data.scheduleRuns,
  'signals': data.signals,
  'questions': data.questions,
  'meetings': data.meetings,
  'decisions': data.decisions,
  'decisionArchive': data.decisionArchive,
  'epics': data.epics,
  'designSessions': data.designSessions,
  'epicHistories': data.epicHistories,
  'stories': data.stories,
  'backlog': data.backlog,
  'planningWorkItems': data.planningWorkItems,
  'planningSessions': data.planningSessions,
  'qualitySnapshot': data.qualitySnapshot,
  'qualityHistory': data.qualityHistory,
  'bugs': data.bugs,
  'verifications': data.verifications,
  'qualityWorkItems': data.qualityWorkItems,
  'qualitySessions': data.qualitySessions,
  'dispatcherStatus': data.dispatcherStatus,
  'deliveryAttempts': data.deliveryAttempts,
  'dispatcherSessions': data.dispatcherSessions,
});

abstract interface class ProductGateway {
  Future<List<ProductSummary>> products();
  Future<ProductWorkspaceData> workspace(ProductSummary product);
  Future<void> createProduct(String name, String? requestedId);
  Future<void> setStatus(ProductSummary product, String status);
  Future<void> setDispatching(ProductSummary product, bool enabled);
  Future<void> setEpicApprovalMode(ProductSummary product, String mode);
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
  Future<void> approveEpic(String epicId, int version);
  Future<void> requestEpicRefinement(String epicId, int version, String reason);
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
  Future<void> setEpicApprovalMode(ProductSummary product, String mode) =>
      _send('PATCH', '/api/products/${product.id}/epic-approval-mode', {
        'mode': mode,
        'expectedVersion': product.version,
        'idempotencyKey': _key('epic-approval-mode'),
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
  Future<void> approveEpic(String epicId, int version) => _send(
    'POST',
    '/api/epics/$epicId/approve',
    {'expectedVersion': version, 'idempotencyKey': _key('epic-approve')},
  );

  @override
  Future<void> requestEpicRefinement(
    String epicId,
    int version,
    String reason,
  ) => _send('POST', '/api/epics/$epicId/request-refinement', {
    'reason': reason,
    'expectedVersion': version,
    'idempotencyKey': _key('epic-refinement'),
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

String _epicStatusLabel(String status) => switch (status) {
  'NEEDS_RESEARCH' => 'Onderzoek nodig',
  'NEEDS_REFINEMENT' => 'Meer uitwerking nodig',
  'AWAITING_APPROVAL' => 'Wacht op goedkeuring',
  'AVAILABLE' => 'Klaar voor planning',
  'IN_PLANNING' => 'Wordt gepland',
  'ACTIVE' => 'In uitvoering',
  'VERIFYING' => 'Wordt gecontroleerd',
  'COMPLETED' => 'Afgerond',
  'NOT_SUCCESSFUL' => 'Niet geslaagd',
  'SUPERSEDED' => 'Vervangen',
  'WITHDRAWN' => 'Ingetrokken',
  'CANCELLED' => 'Geannuleerd',
  _ => status,
};

const _activeProcessStatuses = {'RUNNING', 'WAITING_FOR_AI'};

DateTime? _parseInstant(Object? value) {
  final raw = _value(value);
  return raw.isEmpty ? null : DateTime.tryParse(raw)?.toLocal();
}

String _dateTimeLabel(DateTime value) =>
    '${value.day.toString().padLeft(2, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.year} '
    '${value.hour.toString().padLeft(2, '0')}:'
    '${value.minute.toString().padLeft(2, '0')}:'
    '${value.second.toString().padLeft(2, '0')}';

String _durationLabel(Duration value) {
  final seconds = value.isNegative ? 0 : value.inSeconds;
  final hours = seconds ~/ 3600;
  final minutes = (seconds % 3600) ~/ 60;
  final remainder = seconds % 60;
  if (hours > 0) return '$hours uur $minutes min $remainder sec';
  if (minutes > 0) return '$minutes min $remainder sec';
  return '$remainder sec';
}

class _ProcessSessionTile extends StatelessWidget {
  const _ProcessSessionTile({
    required this.session,
    required this.icon,
    this.label,
    this.details,
    this.dense = false,
  });

  final Map<String, Object?> session;
  final IconData icon;
  final String? label;
  final String? details;
  final bool dense;

  @override
  Widget build(BuildContext context) => ListTile(
    dense: dense,
    contentPadding: EdgeInsets.zero,
    leading: Icon(icon),
    title: SelectableText(
      '${label == null ? '' : '$label · '}${session['status']} · ${_value(session['id'])}',
    ),
    subtitle: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (details?.trim().isNotEmpty == true) SelectableText(details!),
        _ProcessTiming(session: session),
      ],
    ),
  );
}

class _ProcessTiming extends StatefulWidget {
  const _ProcessTiming({required this.session});

  final Map<String, Object?> session;

  @override
  State<_ProcessTiming> createState() => _ProcessTimingState();
}

class _ProcessTimingState extends State<_ProcessTiming> {
  Timer? _timer;
  DateTime _now = DateTime.now();

  bool get _active =>
      _activeProcessStatuses.contains(_value(widget.session['status']));

  @override
  void initState() {
    super.initState();
    _configureTimer();
  }

  @override
  void didUpdateWidget(covariant _ProcessTiming oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_value(oldWidget.session['status']) !=
            _value(widget.session['status']) ||
        _value(oldWidget.session['startedAt']) !=
            _value(widget.session['startedAt'])) {
      _configureTimer();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _configureTimer() {
    _timer?.cancel();
    _now = DateTime.now();
    if (_active) {
      _timer = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() => _now = DateTime.now());
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final started = _parseInstant(widget.session['startedAt']);
    final finished = _parseInstant(widget.session['finishedAt']);
    if (started == null) {
      return const SelectableText('Starttijd niet beschikbaar');
    }
    final duration = (finished ?? _now).difference(started);
    if (_active) {
      return SelectableText(
        'Gestart ${_dateTimeLabel(started)} · actief: ja · loopt ${_durationLabel(duration)}',
        style: Theme.of(context).textTheme.bodySmall,
      );
    }
    return SelectableText(
      'Gestart ${_dateTimeLabel(started)} · '
      '${finished == null ? 'niet meer actief' : 'geëindigd ${_dateTimeLabel(finished)}'} · '
      'duur ${_durationLabel(duration)}',
      style: Theme.of(context).textTheme.bodySmall,
    );
  }
}

class _AssignmentEditor extends StatefulWidget {
  const _AssignmentEditor({
    required this.assignment,
    required this.onCancel,
    required this.onSave,
    super.key,
  });

  final Map<String, Object?>? assignment;
  final VoidCallback onCancel;
  final Future<bool> Function(Map<String, Object?> values) onSave;

  @override
  State<_AssignmentEditor> createState() => _AssignmentEditorState();
}

class _AssignmentEditorState extends State<_AssignmentEditor> {
  late final TextEditingController _audience;
  late final TextEditingController _goal;
  late final TextEditingController _git;
  late final List<TextEditingController> _boundaries;
  bool _saving = false;
  String? _validationError;

  @override
  void initState() {
    super.initState();
    final assignment = widget.assignment;
    _audience = TextEditingController(text: _value(assignment?['audience']));
    _goal = TextEditingController(text: _value(assignment?['goal']));
    _git = TextEditingController(text: _value(assignment?['publicGitUrl']));
    _boundaries = (assignment?['hardBoundaries'] as List? ?? const [])
        .map((boundary) => TextEditingController(text: boundary.toString()))
        .toList();
    if (_boundaries.isEmpty) _boundaries.add(TextEditingController());
  }

  @override
  void dispose() {
    _audience.dispose();
    _goal.dispose();
    _git.dispose();
    for (final boundary in _boundaries) {
      boundary.dispose();
    }
    super.dispose();
  }

  void _addBoundary() {
    setState(() {
      _boundaries.add(TextEditingController());
      _validationError = null;
    });
  }

  void _removeBoundary(int index) {
    if (_boundaries.length == 1) return;
    final removed = _boundaries.removeAt(index);
    removed.dispose();
    setState(() => _validationError = null);
  }

  Future<void> _save() async {
    final boundaries = _boundaries
        .map((controller) => controller.text.trim())
        .where((boundary) => boundary.isNotEmpty)
        .toList();
    if (_audience.text.trim().isEmpty ||
        _goal.text.trim().isEmpty ||
        _git.text.trim().isEmpty ||
        boundaries.isEmpty) {
      setState(
        () => _validationError =
            'Vul doelgroep, productdoel, minimaal één harde grens en de Git-URL in.',
      );
      return;
    }
    setState(() {
      _saving = true;
      _validationError = null;
    });
    final saved = await widget.onSave({
      'audience': _audience.text.trim(),
      'goal': _goal.text.trim(),
      'hardBoundaries': boundaries,
      'publicGitUrl': _git.text.trim(),
    });
    if (!saved && mounted) setState(() => _saving = false);
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const SelectableText(
        'Werk de productopdracht hier op volledige paginabreedte bij. Iedere harde grens is één zelfstandig item en mag meerdere regels tekst bevatten.',
      ),
      const SizedBox(height: 20),
      TextField(
        key: const ValueKey('assignment-audience'),
        controller: _audience,
        decoration: const InputDecoration(labelText: 'Doelgroep'),
      ),
      const SizedBox(height: 16),
      TextField(
        key: const ValueKey('assignment-goal'),
        controller: _goal,
        minLines: 4,
        maxLines: 12,
        keyboardType: TextInputType.multiline,
        decoration: const InputDecoration(labelText: 'Productdoel'),
      ),
      const SizedBox(height: 24),
      Wrap(
        alignment: WrapAlignment.spaceBetween,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 16,
        runSpacing: 8,
        children: [
          SelectableText(
            'Harde grenzen',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          OutlinedButton.icon(
            key: const ValueKey('add-hard-boundary'),
            onPressed: _saving ? null : _addBoundary,
            icon: const Icon(Icons.add),
            label: const Text('Grens toevoegen'),
          ),
        ],
      ),
      const SizedBox(height: 6),
      const SelectableText(
        'Gebruik een nieuwe grens voor een afzonderlijke, niet-onderhandelbare regel. Regeleinden binnen een grens blijven behouden.',
      ),
      const SizedBox(height: 12),
      for (var index = 0; index < _boundaries.length; index++) ...[
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(16, 12, 8, 16),
          decoration: BoxDecoration(
            border: Border.all(
              color: Theme.of(context).colorScheme.outlineVariant,
            ),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: SelectableText(
                      'Grens ${index + 1}',
                      style: Theme.of(context).textTheme.labelLarge,
                    ),
                  ),
                  IconButton(
                    key: ValueKey('remove-hard-boundary-$index'),
                    tooltip: 'Grens ${index + 1} verwijderen',
                    onPressed: _saving || _boundaries.length == 1
                        ? null
                        : () => _removeBoundary(index),
                    icon: const Icon(Icons.delete_outline),
                  ),
                ],
              ),
              TextField(
                key: ValueKey('hard-boundary-$index'),
                controller: _boundaries[index],
                minLines: 2,
                maxLines: 8,
                keyboardType: TextInputType.multiline,
                textInputAction: TextInputAction.newline,
                decoration: const InputDecoration(
                  labelText: 'Niet-onderhandelbare regel',
                  alignLabelWithHint: true,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
      ],
      const SizedBox(height: 8),
      TextField(
        key: const ValueKey('assignment-git-url'),
        controller: _git,
        decoration: const InputDecoration(labelText: 'Publieke Git-URL'),
      ),
      if (_validationError != null) ...[
        const SizedBox(height: 12),
        SelectableText(
          _validationError!,
          style: TextStyle(color: Theme.of(context).colorScheme.error),
        ),
      ],
      const SizedBox(height: 20),
      Align(
        alignment: Alignment.centerRight,
        child: Wrap(
          spacing: 12,
          runSpacing: 8,
          children: [
            TextButton(
              onPressed: _saving ? null : widget.onCancel,
              child: const Text('Annuleren'),
            ),
            FilledButton.icon(
              key: const ValueKey('save-assignment'),
              onPressed: _saving ? null : _save,
              icon: _saving
                  ? const SizedBox.square(
                      dimension: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.save_outlined),
              label: Text(_saving ? 'Opslaan…' : 'Opslaan'),
            ),
          ],
        ),
      ),
    ],
  );
}

class ProductWorkspacePage extends StatefulWidget {
  const ProductWorkspacePage({
    required this.gateway,
    this.section = ProductWorkspaceSection.overview,
    this.trailingContent,
    this.initialProductId,
    this.onProductSelected,
    this.refreshController,
    super.key,
  });
  final ProductGateway gateway;
  final ProductWorkspaceSection section;
  final Widget? trailingContent;
  final String? initialProductId;
  final ValueChanged<String>? onProductSelected;
  final PageRefreshController? refreshController;
  @override
  State<ProductWorkspacePage> createState() => _ProductWorkspacePageState();
}

class _ProductWorkspacePageState extends State<ProductWorkspacePage> {
  List<ProductSummary> _products = const [];
  ProductSummary? _selected;
  ProductWorkspaceData? _data;
  bool _busy = true;
  bool _refreshing = false;
  bool _editingAssignment = false;
  String? _productsFingerprint;
  String? _workspaceFingerprint;
  String? _error;

  @override
  void initState() {
    super.initState();
    widget.refreshController?.addListener(_onRefreshRequested);
    _loadProducts(widget.initialProductId);
  }

  @override
  void dispose() {
    widget.refreshController?.removeListener(_onRefreshRequested);
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant ProductWorkspacePage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.refreshController != widget.refreshController) {
      oldWidget.refreshController?.removeListener(_onRefreshRequested);
      widget.refreshController?.addListener(_onRefreshRequested);
    }
    if (oldWidget.initialProductId != widget.initialProductId &&
        widget.initialProductId != _selected?.id) {
      unawaited(_loadProducts(widget.initialProductId));
    }
  }

  void _onRefreshRequested() => unawaited(
    _refreshCurrent(
      showErrors: widget.refreshController?.userInitiated == true,
    ),
  );

  Future<void> _refreshCurrent({bool showErrors = false}) async {
    final selected = _selected;
    if (_busy || _refreshing) return;
    _refreshing = true;
    try {
      final products = await widget.gateway.products();
      final refreshedSelected = selected == null
          ? null
          : products.where((product) => product.id == selected.id).firstOrNull;
      final refreshedData = refreshedSelected == null
          ? null
          : await widget.gateway.workspace(refreshedSelected);
      if (!mounted) return;
      final productsFingerprint = _fingerprintProducts(products);
      final workspaceFingerprint = refreshedData == null
          ? null
          : _fingerprintWorkspace(refreshedData);
      if (productsFingerprint != _productsFingerprint ||
          workspaceFingerprint != _workspaceFingerprint) {
        setState(() {
          _products = products;
          _selected = refreshedSelected;
          _data = refreshedData;
          _productsFingerprint = productsFingerprint;
          _workspaceFingerprint = workspaceFingerprint;
          _error = null;
        });
      }
    } on ProductFailure catch (error) {
      if (showErrors && mounted) setState(() => _error = error.message);
    } catch (_) {
      if (showErrors && mounted) {
        setState(
          () => _error = 'Productgegevens konden niet worden vernieuwd.',
        );
      }
    } finally {
      _refreshing = false;
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
          _productsFingerprint = _fingerprintProducts(products);
          _workspaceFingerprint = data == null
              ? null
              : _fingerprintWorkspace(data);
        });
        if (selected != null) widget.onProductSelected?.call(selected.id);
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
                    SelectableText(
                      _sectionEyebrow(widget.section),
                      style: Theme.of(context).textTheme.labelMedium?.copyWith(
                        color: Theme.of(context).colorScheme.primary,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 1.5,
                      ),
                    ),
                    const SizedBox(height: 6),
                    SelectableText(
                      _sectionTitle(widget.section),
                      style: Theme.of(context).textTheme.displaySmall,
                    ),
                    const SizedBox(height: 8),
                    ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 680),
                      child: SelectableText(
                        _sectionDescription(widget.section),
                      ),
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
                  title: SelectableText(_error!),
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
                          SelectableText(
                            'Nog geen producten',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          SizedBox(height: 4),
                          SelectableText(
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
                    key: ValueKey(_selected?.id),
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
                    onChanged: (id) {
                      if (id != null) unawaited(_loadProducts(id));
                    },
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
              SelectableText(
                'PRODUCTDOEL',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: Theme.of(context).colorScheme.primary,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.4,
                ),
              ),
              const SizedBox(height: 8),
              SelectableText(
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
                          SelectableText(
                            'NU',
                            style: Theme.of(context).textTheme.labelSmall
                                ?.copyWith(
                                  color: Theme.of(context).colorScheme.primary,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 1.4,
                                ),
                          ),
                          const SizedBox(height: 6),
                          SelectableText(
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
                  SelectableText(
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
                              SelectableText(
                                'Software Factory bouwt',
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                              SelectableText(
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
    final approvalEpics = data.epics
        .where((epic) => epic['status'] == 'AWAITING_APPROVAL')
        .toList();
    final total =
        openSignals.length +
        openQuestions.length +
        blockedWork.length +
        approvalEpics.length;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SelectableText(
              'AANDACHT',
              style: Theme.of(context).textTheme.labelSmall,
            ),
            const SizedBox(height: 6),
            SelectableText(
              total == 0 ? 'Geen open punten' : '$total open punten',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            if (total == 0)
              const SelectableText(
                'Er zijn nu geen blokkades, vragen of open signalen.',
              )
            else ...[
              ...approvalEpics.map(
                (epic) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.approval_outlined),
                  title: SelectableText('${epic['title']}'),
                  subtitle: const SelectableText(
                    'Epic wacht op jouw goedkeuring voordat de planner stories maakt.',
                  ),
                  trailing: const Chip(label: Text('Wacht op jou')),
                ),
              ),
              ...blockedWork
                  .take(3)
                  .map(
                    (item) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.error_outline),
                      title: SelectableText(
                        '${item['type']} · ${item['status']}',
                      ),
                      subtitle: SelectableText('${item['explanation']}'),
                    ),
                  ),
              ...openQuestions
                  .take(3)
                  .map(
                    (question) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.question_answer_outlined),
                      title: SelectableText(_value(question['question'])),
                      subtitle: const SelectableText('Vraag van een agent'),
                    ),
                  ),
              ...openSignals
                  .take(3)
                  .map(
                    (signal) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.radio_button_unchecked),
                      title: SelectableText(_value(signal['text'])),
                      subtitle: const SelectableText('Open gebruikerssignaal'),
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
                SelectableText(
                  data.product.name,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                SelectableText(data.product.id),
                SelectableText(
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
          FilterChip(
            label: const Text('Epics handmatig goedkeuren'),
            selected: data.product.epicApprovalMode == 'MANUAL',
            onSelected: (value) => _mutate(
              () => widget.gateway.setEpicApprovalMode(
                data.product,
                value ? 'MANUAL' : 'AUTOMATIC',
              ),
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
    SelectableText(
      'Processessies',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    const SizedBox(height: 8),
    ...[
      ...data.designSessions.map((session) => ('Ontwerp', session)),
      ...data.planningSessions.map((session) => ('Planning', session)),
      ...data.qualitySessions.map((session) => ('Kwaliteit', session)),
      ...data.dispatcherSessions.map((session) => ('Dispatcher', session)),
    ].map(
      (entry) => _ProcessSessionTile(
        session: entry.$2,
        label: entry.$1,
        icon: _activeProcessStatuses.contains(entry.$2['status'])
            ? Icons.play_circle_outline
            : Icons.history,
        details:
            '${entry.$2['resultSummary'] ?? entry.$2['blockedReason'] ?? 'Geen resultaatsamenvatting.'}',
      ),
    ),
    const Divider(height: 32),
    SelectableText(
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
      title: SelectableText(
        data.dispatcherStatus['blocked'] == true
            ? 'Dispatch geblokkeerd'
            : 'Dispatcher gereed',
      ),
      subtitle: SelectableText(
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
          SelectableText(
            'Externe status ${attempt['externalStatus'] ?? 'onbekend'}',
          ),
          SelectableText('Retry ${attempt['retryAfter'] ?? 'niet gepland'}'),
          if (attempt['lastErrorCode'] != null)
            SelectableText(
              '${attempt['lastErrorCode']}: ${attempt['lastErrorMessage']}',
            ),
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
    SelectableText(
      'Productbrede backlog',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    if (data.backlog.isEmpty)
      const SelectableText('De backlog is leeg.')
    else
      ..._groupedBacklog(data),
    const Divider(height: 28),
    SelectableText(
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
            title: SelectableText('${story['title']} · ${story['status']}'),
            subtitle: SelectableText(
              '${story['cancellationReason'] ?? story['deliveredCommitSha'] ?? story['summary']}',
            ),
          ),
        ),
    const Divider(height: 28),
    SelectableText(
      'Werkqueue en sessies',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    ...data.planningWorkItems.map(
      (item) => ListTile(
        dense: true,
        leading: const Icon(Icons.playlist_add_check),
        title: SelectableText('${item['type']} · ${item['status']}'),
        subtitle: SelectableText('${item['explanation']}'),
      ),
    ),
    ...data.planningSessions.map((session) {
      final blocked = session['status'] == 'BLOCKED';
      final code = _value(session['errorCode']);
      final reason = _value(session['blockedReason']);
      final explanation = switch (code) {
        'PLANNING_PUBLICATION_CONFLICT' =>
          'Het AI-plan is wel gemaakt, maar kon nog niet in de backlog worden opgeslagen. Er is niets gedeeltelijk gepubliceerd. Klik op ‘Planning starten of hervatten’ om hetzelfde plan opnieuw te publiceren.',
        'PLANNING_VERSION_CONFLICT' =>
          'De epic of backlog veranderde tijdens het plannen. Er is niets gedeeltelijk gepubliceerd. Hervat de planning zodat de actuele versie opnieuw wordt gecontroleerd.',
        'PLANNING_RESULT_INVALID' =>
          'Het AI-resultaat voldeed niet aan alle veiligheidscontroles en is daarom niet gepubliceerd. Hervat de planning voor een nieuwe poging.',
        _ when blocked =>
          '${reason.isEmpty ? 'De planning kon door een technische fout niet worden afgerond.' : reason} Er is niets gedeeltelijk gepubliceerd. Hervat de planning om het veilig opnieuw te proberen.',
        _ =>
          '${session['resultSummary'] ?? session['blockedReason'] ?? 'Planner-AI wordt duurzaam gevolgd.'}',
      };
      final tile = _ProcessSessionTile(
        session: session,
        dense: true,
        label: blocked ? 'Planning kon niet worden afgerond' : null,
        icon: blocked ? Icons.error_outline : Icons.schema_outlined,
        details:
            '$explanation${blocked && code.isNotEmpty ? '\nFoutcode: $code' : ''}\n'
            '${(session['aiTaskIds'] as List? ?? const []).length} AI-taak/taken · Git ${session['repositoryCommitSha'] ?? 'nog niet bevroren'}',
      );
      if (!blocked) return tile;
      return Card(
        color: Theme.of(context).colorScheme.errorContainer,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: tile,
        ),
      );
    }),
    const Divider(height: 28),
    SelectableText(
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
        title: SelectableText(
          data.dispatcherStatus['blocked'] == true
              ? 'Dispatch geblokkeerd'
              : 'Dispatcher gereed',
        ),
        subtitle: SelectableText(
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
          SelectableText(
            'Externe status ${attempt['externalStatus'] ?? 'onbekend'}',
          ),
          SelectableText('Retry ${attempt['retryAfter'] ?? 'niet gepland'}'),
          if (attempt['lastErrorCode'] != null)
            SelectableText(
              '${attempt['lastErrorCode']}: ${attempt['lastErrorMessage']}',
            ),
          if (attempt['deliveredCommitSha'] != null)
            SelectableText('Oplevercommit ${attempt['deliveredCommitSha']}'),
        ],
      ),
    ),
    ...data.dispatcherSessions.map(
      (session) => _ProcessSessionTile(
        session: session,
        dense: true,
        icon: Icons.history,
        label: 'Dispatcher',
        details:
            '${session['resultSummary'] ?? session['blockedReason'] ?? 'Dispatchersessie actief.'}\n'
            '${(session['implementation'] as Map?)?['artifact'] ?? 'software-factory-dispatcher-impl'}',
      ),
    ),
  ]);

  List<Widget> _groupedBacklog(ProductWorkspaceData data) {
    final storiesByEpic = <String, List<Map<String, Object?>>>{};
    for (final story in data.backlog) {
      storiesByEpic.putIfAbsent(_value(story['epicId']), () => []).add(story);
    }
    return storiesByEpic.entries.map((entry) {
      final epic = data.epics
          .cast<Map<String, Object?>>()
          .where((candidate) => _value(candidate['id']) == entry.key)
          .firstOrNull;
      return Card(
        margin: const EdgeInsets.only(top: 10),
        child: ExpansionTile(
          initiallyExpanded: true,
          leading: const Icon(Icons.view_agenda_outlined),
          title: Text(epic == null ? 'Epic ${entry.key}' : '${epic['title']}'),
          subtitle: Text(
            '${entry.value.length} ${entry.value.length == 1 ? 'story' : 'stories'} · '
            '${epic == null ? entry.key : _epicStatusLabel(_value(epic['status']))}',
          ),
          children: entry.value.map(_planningStoryTile).toList(),
        ),
      );
    }).toList();
  }

  Widget _planningStoryTile(Map<String, Object?> story) => ExpansionTile(
    leading: CircleAvatar(child: Text('${story['sequenceNumber']}')),
    title: Text('${story['title']} · ${story['status']}'),
    subtitle: Text('${story['summary']}'),
    childrenPadding: const EdgeInsets.fromLTRB(72, 0, 16, 16),
    expandedCrossAxisAlignment: CrossAxisAlignment.start,
    children: [
      SelectableText(
        'Story ${_value(story['id'])} · versie ${story['version']}',
      ),
      SelectableText(
        'Type: ${story['type']} · Epic ${_value(story['epicId'])} v${story['epicVersion']}',
      ),
      const SizedBox(height: 8),
      SelectableText('${story['content']}'),
      const SizedBox(height: 8),
      SelectableText(
        'Acceptatiecriteria',
        style: Theme.of(context).textTheme.labelLarge,
      ),
      ...(story['acceptanceCriteria'] as List? ?? const []).map(
        (criterion) => SelectableText('• $criterion'),
      ),
      if (story['uxDesign'] != null) SelectableText('UX: ${story['uxDesign']}'),
      ..._uxArtifactGallery(
        context,
        (story['uxArtifacts'] as List? ?? const <Object?>[]).cast<Object?>(),
        title: 'UX-modellen bij deze story',
      ),
      SelectableText(
        'Dependencies: ${(story['dependencies'] as List? ?? const []).map(_value).join(', ')}',
      ),
      SelectableText(
        'Prioriteit: ${story['priorityReason'] ?? 'Planner-volgorde'}',
      ),
      if (story['dispatchReservationId'] != null)
        SelectableText(
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
        const SelectableText('Wacht op deployment of kwaliteitscontrole'),
      if (story['verificationId'] != null)
        SelectableText(
          'Getest: ${story['verificationPassed'] == true ? 'geslaagd' : 'afgekeurd'} · verificatie ${_value(story['verificationId'])}',
        ),
    ],
  );

  Future<void> _planningReasonAction(String productId) async {
    final controller = TextEditingController();
    final reason = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const SelectableText('Handmatige herplanning'),
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
          content: SelectableText(
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
          title: const SelectableText('Dispatching staat uit'),
          content: const SelectableText(
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
          content: SelectableText(
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
      const SelectableText('Nog geen werkelijk getest kwaliteitsbeeld.')
    else
      Card(
        child: ListTile(
          leading: const Icon(Icons.fact_check_outlined),
          title: SelectableText(
            '${data.qualitySnapshot!['environment']} · ${data.qualitySnapshot!['capturedAt']}',
          ),
          subtitle: SelectableText(
            'Geteste revision ${data.qualitySnapshot!['productRevision']}\n'
            'Open bugs: ${data.qualitySnapshot!['openBugsBySeverity']}\n'
            'Risico’s: ${(data.qualitySnapshot!['risks'] as List? ?? const []).join(', ')}',
          ),
          isThreeLine: true,
        ),
      ),
    const Divider(height: 28),
    SelectableText(
      'Werk en retries',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    if (data.qualityWorkItems.isEmpty)
      const SelectableText('Geen kwaliteitswerk in de queue.')
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
          title: SelectableText(
            '${item['type']} · ${item['status']}${item['attentionNeeded'] == true ? ' · Aandacht nodig' : ''}',
          ),
          subtitle: SelectableText(
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
    SelectableText(
      'Verificaties',
      style: Theme.of(context).textTheme.titleMedium,
    ),
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
          SelectableText(
            'Controles: ${(verification['checks'] as List? ?? const []).join(', ')}',
          ),
          SelectableText(
            'Bewijs: ${(verification['evidence'] as Map?)?['description'] ?? 'Geen publiek bewijs'}',
          ),
          if (verification['blockedReason'] != null)
            SelectableText('Blokkade: ${verification['blockedReason']}'),
        ],
      ),
    ),
    const Divider(height: 28),
    SelectableText('Bugs', style: Theme.of(context).textTheme.titleMedium),
    ...data.bugs.map(
      (bug) => ExpansionTile(
        title: Text('${bug['title']} · ${bug['severity']} · ${bug['status']}'),
        subtitle: Text('${bug['summary']}'),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        expandedCrossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText('Werkelijk: ${bug['actualBehaviour']}'),
          SelectableText('Verwacht: ${bug['expectedBehaviour']}'),
          SelectableText(
            'Reproduceren: ${(bug['reproductionSteps'] as List? ?? const []).join(' → ')}',
          ),
          SelectableText(
            'Bewijs: ${(bug['evidence'] as Map?)?['description'] ?? 'Geen publiek bewijs'}',
          ),
        ],
      ),
    ),
    const Divider(height: 28),
    SelectableText(
      'Processessies',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    ...data.qualitySessions.map(
      (session) => _ProcessSessionTile(
        session: session,
        icon: Icons.science_outlined,
        details:
            '${session['resultSummary'] ?? session['blockedReason'] ?? 'Tester-AI wordt duurzaam gevolgd.'}\n'
            '${(session['aiTaskIds'] as List? ?? const []).length} AI-taak/taken · Git ${session['repositoryCommitSha'] ?? 'nog niet bevroren'}',
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
    SelectableText('Epics', style: Theme.of(context).textTheme.titleMedium),
    if (data.epics.isEmpty)
      const SelectableText('Nog geen epics gepubliceerd.')
    else
      ...data.epics.map(
        (epic) => ExpansionTile(
          leading: const Icon(Icons.view_agenda_outlined),
          title: Text(
            '${epic['title']} · ${_epicStatusLabel(_value(epic['status']))}',
          ),
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
                  title: SelectableText('Nog niet klaar voor planning'),
                  subtitle: SelectableText(
                    'Productontwerp werkt eerst bronnen, open vragen en UX-modellen uit. Productplanning kan deze epic nog niet claimen.',
                  ),
                ),
              ),
            ],
            if (epic['status'] == 'NEEDS_REFINEMENT') ...[
              const SizedBox(height: 8),
              Card(
                color: Theme.of(context).colorScheme.errorContainer,
                child: ListTile(
                  leading: const Icon(Icons.edit_note_outlined),
                  title: const SelectableText(
                    'Teruggestuurd voor verdere uitwerking',
                  ),
                  subtitle: SelectableText(
                    _value(epic['refinementReason']).isEmpty
                        ? 'De ontwerper moet deze epic verder uitwerken.'
                        : _value(epic['refinementReason']),
                  ),
                ),
              ),
            ],
            if (epic['status'] == 'AWAITING_APPROVAL') ...[
              const SizedBox(height: 8),
              Card(
                color: Theme.of(context).colorScheme.primaryContainer,
                child: const ListTile(
                  leading: Icon(Icons.approval_outlined),
                  title: SelectableText('Wacht op jouw goedkeuring'),
                  subtitle: SelectableText(
                    'Controleer vooral of UX, databronnen, toegang en technische haalbaarheid concreet genoeg zijn. Na goedkeuring kan de planner direct stories maken.',
                  ),
                ),
              ),
            ],
            const SizedBox(height: 8),
            SelectableText(
              'Probleem',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            SelectableText('${epic['problem']}'),
            const SizedBox(height: 8),
            SelectableText(
              'Oplossing',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            SelectableText('${epic['solution']}'),
            if (epic['uxDesign'] != null) ...[
              const SizedBox(height: 8),
              SelectableText(
                'UX-ontwerp',
                style: Theme.of(context).textTheme.labelLarge,
              ),
              SelectableText('${epic['uxDesign']}'),
            ],
            if ((epic['uxScreens'] as List? ?? const []).isNotEmpty) ...[
              const SizedBox(height: 8),
              SelectableText(
                'Volledige UX-schermset',
                style: Theme.of(context).textTheme.labelLarge,
              ),
              ...(epic['uxScreens'] as List).map((raw) {
                final screen = (raw as Map).cast<String, Object?>();
                final variants =
                    ((screen['artifacts'] as Map?)?.keys ?? const [])
                        .map((value) => '$value')
                        .join(', ');
                return SelectableText(
                  '• ${screen['screenKey']} · ${screen['state']} · $variants\n'
                  '  ${screen['purpose']}',
                );
              }),
            ],
            ..._uxArtifactGallery(
              context,
              (epic['uxArtifacts'] as List? ?? const <Object?>[])
                  .cast<Object?>(),
            ),
            const SizedBox(height: 8),
            SelectableText(
              'Gereedheid',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            SelectableText(
              (epic['readiness'] as Map?)?['readyForPlanning'] == true
                  ? 'Gereed voor Productplanning'
                  : 'Nog niet gereed voor Productplanning',
            ),
            ...((epic['readiness'] as Map?)?['unmetConditions'] as List? ??
                    const [])
                .map((condition) => SelectableText('• $condition')),
            ...((epic['readiness'] as Map?)?['openQuestions'] as List? ??
                    const [])
                .map((question) => SelectableText('Open vraag: $question')),
            const SizedBox(height: 8),
            SelectableText(
              'Onderzochte bronnen',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            if ((epic['researchSources'] as List? ?? const []).isEmpty)
              const SelectableText(
                'Nog geen concrete externe bronnen onderzocht.',
              )
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
                  title: SelectableText(
                    '${source['name']} · ${source['status']}',
                  ),
                  subtitle: SelectableText(
                    '${source['provider']}\n${source['coverage']}\n'
                    'Toegang: ${source['accessMethod']} · Licentie: ${source['license']}\n'
                    '${source['validationEvidence']}\n${source['uri']}',
                  ),
                );
              }),
            const SizedBox(height: 8),
            SelectableText(
              'Acceptatiecriteria',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            ...(epic['acceptanceCriteria'] as List? ?? const []).map(
              (criterion) => SelectableText('• $criterion'),
            ),
            const SizedBox(height: 8),
            SelectableText('Behapbaarheid: ${epic['slicabilityRationale']}'),
            SelectableText(
              'Bronnen: ${(epic['directionReferences'] as List? ?? const []).length} richtingsreferentie(s)',
            ),
            const SizedBox(height: 8),
            SelectableText(
              'Versiehistorie',
              style: Theme.of(context).textTheme.labelLarge,
            ),
            ...(data.epicHistories[_value(epic['id'])] ?? const []).map(
              (version) => SelectableText(
                'v${version['version']} · ${_epicStatusLabel(_value(version['status']))} · ${version['title']}',
              ),
            ),
            const SizedBox(height: 12),
            if (epic['status'] == 'AWAITING_APPROVAL')
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton.icon(
                  onPressed: () => _approveEpic(epic),
                  icon: const Icon(Icons.check),
                  label: const Text('Goedkeuren voor planning'),
                ),
              ),
            if (const {
              'AWAITING_APPROVAL',
              'AVAILABLE',
              'IN_PLANNING',
              'ACTIVE',
              'VERIFYING',
              'COMPLETED',
              'NOT_SUCCESSFUL',
            }.contains(epic['status']))
              Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton.icon(
                  onPressed: () => _requestEpicRefinement(epic),
                  icon: const Icon(Icons.undo_outlined),
                  label: const Text('Terugsturen voor verdere uitwerking'),
                ),
              ),
            if (const {
              'NEEDS_RESEARCH',
              'NEEDS_REFINEMENT',
              'AWAITING_APPROVAL',
              'AVAILABLE',
            }.contains(epic['status']))
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
    SelectableText(
      'Processessies',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    if (data.designSessions.isEmpty)
      const SelectableText('Nog geen ontwerpsessies gestart.')
    else
      ...data.designSessions.map(
        (session) => _ProcessSessionTile(
          session: session,
          icon: session['status'] == 'SUCCEEDED'
              ? Icons.check_circle_outline
              : session['status'] == 'BLOCKED'
              ? Icons.error_outline
              : Icons.hourglass_top,
          details:
              '${session['resultSummary'] ?? session['blockedReason'] ?? 'AI-taak wordt duurzaam gevolgd.'}\n'
              '${(session['implementation'] as Map?)?['artifact'] ?? 'product-design-impl-mvp'} · '
              '${(session['aiTaskIds'] as List? ?? const []).length} AI-taak/taken\n'
              'Git ${session['repositoryCommitSha'] ?? 'nog niet bevroren'}',
        ),
      ),
  ]);

  Widget _assignment(ProductWorkspaceData data) {
    final a = data.assignment;
    final t = data.testConfiguration;
    final hardBoundaries = (a?['hardBoundaries'] as List? ?? const [])
        .map((boundary) => boundary.toString())
        .toList();
    return _section(
      _editingAssignment
          ? 'Productopdracht bewerken'
          : 'Productopdracht en testomgevingen',
      Icons.assignment_outlined,
      [
        if (_editingAssignment)
          _AssignmentEditor(
            key: ValueKey('assignment-editor-${data.product.id}'),
            assignment: a,
            onCancel: () => setState(() => _editingAssignment = false),
            onSave: (values) => _saveAssignment(data, values),
          )
        else if (a == null)
          const SelectableText('Productopdracht nog niet vastgelegd.')
        else ...[
          SelectableText('Doelgroep: ${a['audience']}'),
          SelectableText('Doel: ${a['goal']}'),
          const SizedBox(height: 8),
          ExpansionTile(
            tilePadding: EdgeInsets.zero,
            childrenPadding: const EdgeInsets.only(bottom: 8),
            title: SelectableText(
              '${hardBoundaries.length} harde ${hardBoundaries.length == 1 ? 'grens' : 'grenzen'}',
            ),
            children: [
              for (var index = 0; index < hardBoundaries.length; index++)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      SizedBox(
                        width: 28,
                        child: SelectableText('${index + 1}.'),
                      ),
                      Expanded(child: SelectableText(hardBoundaries[index])),
                    ],
                  ),
                ),
            ],
          ),
          SelectableText('Git: ${a['publicGitUrl']}'),
          SelectableText('Versie ${a['version']}'),
        ],
        if (!_editingAssignment) ...[
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              onPressed: () => setState(() => _editingAssignment = true),
              icon: const Icon(Icons.edit),
              label: const Text('Opdracht bewerken'),
            ),
          ),
          const Divider(),
          SelectableText(
            'Testomgevingen',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 6),
          if (t == null)
            const SelectableText('Nog niet geconfigureerd.')
          else ...[
            SelectableText(
              'Acceptatie: ${(t['acceptance'] as Map?)?['baseUrl']}',
            ),
            SelectableText(
              'Productie: ${(t['production'] as Map?)?['baseUrl'] ?? 'niet ingesteld'}',
            ),
            SelectableText('Versie ${t['version']}'),
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
      ],
    );
  }

  Widget _signals(ProductWorkspaceData data) => _section(
    'Signalen',
    Icons.feedback_outlined,
    data.signals.isEmpty
        ? const [SelectableText('Geen signalen.')]
        : data.signals
              .map(
                (s) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: SelectableText(_value(s['text'])),
                  subtitle: SelectableText(
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
        title: SelectableText(cancel ? 'Epic annuleren' : 'Epic intrekken'),
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

  Future<void> _approveEpic(Map<String, Object?> epic) => _mutate(
    () => widget.gateway.approveEpic(
      _value(epic['id']),
      (epic['version'] as num).toInt(),
    ),
  );

  Future<void> _requestEpicRefinement(Map<String, Object?> epic) => _textAction(
    'Epic terugsturen voor verdere uitwerking',
    'Wat ontbreekt of moet concreter?',
    (reason) => widget.gateway.requestEpicRefinement(
      _value(epic['id']),
      (epic['version'] as num).toInt(),
      reason,
    ),
  );

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
        ? const [SelectableText('Geen vragen.')]
        : data.questions.map((q) {
            final source = _stakeholderMessage(data.meetings);
            return ListTile(
              contentPadding: EdgeInsets.zero,
              title: SelectableText(_value(q['question'])),
              subtitle: SelectableText(
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
        ? const [SelectableText('Geen overleggen.')]
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
                        child: SelectableText(
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
                          title: SelectableText(
                            '${row['senderRole']}${row['representedAgentRole'] == null ? '' : ' · ${row['representedAgentRole']}'}',
                          ),
                          subtitle: SelectableText(
                            '${row['text']}\n${row['createdAt']}',
                          ),
                        );
                      }),
                      if (m['minutes'] != null)
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.description_outlined),
                          title: const SelectableText('Brongetrouwe notulen'),
                          subtitle: SelectableText('${m['minutes']}'),
                        ),
                      ...(m['outcomes'] as List? ?? const []).map((outcome) {
                        final row = (outcome as Map).cast<String, Object?>();
                        return ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.account_tree_outlined),
                          title: SelectableText('${row['description']}'),
                          subtitle: SelectableText(
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
      if (data.decisions.isEmpty)
        const SelectableText('Geen actuele besluiten.'),
      ...data.decisions.map(
        (d) => Card(
          child: ListTile(
            title: SelectableText(_value(d['decision'])),
            subtitle: SelectableText(
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
      SelectableText(
        'Volledig archief (${data.decisionArchive.length})',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      ...data.decisionArchive.map(
        (d) => ListTile(
          contentPadding: EdgeInsets.zero,
          title: SelectableText('${d['state']} · ${_value(d['id'])}'),
          subtitle: SelectableText(
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
    const SelectableText(
      'Ieder proces heeft een eigen ritme. Uitgeschakeld betekent alleen dat het niet automatisch start; Nu starten blijft beschikbaar.',
    ),
    const SizedBox(height: 8),
    ...data.schedules.map(
      (s) => Card(
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: ListTile(
            title: SelectableText(_value(s['process']).replaceAll('_', ' ')),
            subtitle: SelectableText(
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
    SelectableText(
      'Recente automatische starts',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    if (data.scheduleRuns.isEmpty)
      const SelectableText('Nog geen automatische start geclaimd.')
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
          title: SelectableText('${run['process']} · ${run['status']}'),
          subtitle: SelectableText(
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
                  child: SelectableText(
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

  Future<bool> _saveAssignment(
    ProductWorkspaceData data,
    Map<String, Object?> values,
  ) async {
    final a = data.assignment;
    final saved = await _mutate(
      () => widget.gateway.saveAssignment(data.product.id, {
        ...values,
        'expectedVersion': (a?['version'] as num?)?.toInt() ?? 0,
      }),
    );
    if (saved && mounted) {
      setState(() => _editingAssignment = false);
    }
    return saved;
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
        title: const SelectableText('Testomgevingen beheren'),
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
              const SelectableText(
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
          title: SelectableText('${schedule['process']} instellen'),
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
        title: const SelectableText('Notulenagent starten'),
        content: const SelectableText(
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
        title: SelectableText(title),
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
