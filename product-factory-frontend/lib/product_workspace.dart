import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'configuration.dart';
import 'http_client_factory.dart';
import 'page_refresh.dart';
import 'product_factory_theme.dart';

part 'product_workspace_insights.dart';

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
    this.live,
    this.epicProgress = const {},
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

  /// Samenvatting van lopende sessies, schema's en de laatste 24 uur per proces.
  final Map<String, Object?>? live;

  /// Voortgang (fase, wacht op, tijdlijn) van open epics, op epic-id.
  final Map<String, Map<String, Object?>> epicProgress;
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
  'signals': data.signals,
  'questions': data.questions,
  'meetings': data.meetings,
  'decisions': data.decisions,
  'decisionArchive': data.decisionArchive,
  'epics': data.epics,
  'designSessions': data.designSessions,
  'stories': data.stories,
  'backlog': data.backlog,
  'planningWorkItems': data.planningWorkItems,
  'planningSessions': data.planningSessions,
  'qualitySnapshot': data.qualitySnapshot,
  'bugs': data.bugs,
  'verifications': data.verifications,
  'qualityWorkItems': data.qualityWorkItems,
  'qualitySessions': data.qualitySessions,
  'dispatcherStatus': data.dispatcherStatus,
  'deliveryAttempts': data.deliveryAttempts,
  // generatedAt verandert bij iedere aanroep en mag geen herbouw forceren.
  'live': data.live == null ? null : ({...data.live!}..remove('generatedAt')),
  'epicProgress': data.epicProgress,
});

abstract interface class ProductGateway {
  Future<List<ProductSummary>> products();
  Future<ProductWorkspaceData> workspace(
    ProductSummary product, {
    ProductWorkspaceSection section = ProductWorkspaceSection.overview,
  });
  Future<Map<String, Object?>?> epicProgress(String epicId);
  Future<List<Map<String, Object?>>> processSessions(
    String productId,
    String process, {
    int limit = 25,
    String? before,
    bool excludeNoOps = true,
  });
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
  Future<void> answerQuestionDirectly(
    String questionId,
    int version,
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
  Future<void> approveProductRequestEpic(String epicId, int version);
  Future<void> approveProductRequestEpicAsFactoryOwner(
    String epicId,
    int version,
  );
  Future<void> refineProductRequestEpic(
    String epicId,
    int version,
    String reason,
  );
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

  static const _sessionPage = '?limit=20&excludeNoOps=true';

  static Set<String> _endpointsFor(ProductWorkspaceSection section) => {
    'assignment',
    'epics',
    'stories',
    'backlog',
    'dispatcherStatus',
    'live',
    ...switch (section) {
      ProductWorkspaceSection.overview => const {
        'signals',
        'questions',
        'bugs',
        'qualityWorkItems',
        'epicProgress',
      },
      ProductWorkspaceSection.design => const {
        'designSessions',
        'epicProgress',
      },
      ProductWorkspaceSection.planning => const {
        'planningWorkItems',
        'planningSessions',
        'deliveryAttempts',
        'bugs',
      },
      ProductWorkspaceSection.quality => const {
        'qualitySnapshot',
        'bugs',
        'verifications',
        'qualityWorkItems',
        'qualitySessions',
      },
      ProductWorkspaceSection.signals => const {'signals'},
      ProductWorkspaceSection.meetings => const {'questions', 'meetings'},
      ProductWorkspaceSection.decisions => const {
        'decisions',
        'decisionArchive',
      },
      ProductWorkspaceSection.settings => const {
        'testConfiguration',
        'schedules',
      },
      ProductWorkspaceSection.operation => const {'deliveryAttempts'},
    },
  };

  @override
  Future<ProductWorkspaceData> workspace(
    ProductSummary product, {
    ProductWorkspaceSection section = ProductWorkspaceSection.overview,
  }) async {
    final id = product.id;
    final wanted = _endpointsFor(section);
    final loaders = <String, Future<Object?> Function()>{
      'assignment': () => _optional('/api/products/$id/assignment'),
      'testConfiguration': () =>
          _optional('/api/products/$id/test-configuration'),
      'schedules': () => _get('/api/products/$id/schedules'),
      'signals': () => _get('/api/products/$id/signals'),
      'questions': () => _get('/api/products/$id/questions'),
      'meetings': () => _get('/api/products/$id/meetings'),
      'decisions': () => _get('/api/products/$id/decisions'),
      'decisionArchive': () => _get('/api/products/$id/decisions/archive'),
      'epics': () => _get('/api/products/$id/epics'),
      'designSessions': () =>
          _get('/api/products/$id/design/sessions$_sessionPage'),
      'stories': () => _get('/api/products/$id/stories'),
      'backlog': () => _get('/api/products/$id/backlog'),
      'planningWorkItems': () => _get('/api/products/$id/planning/work-items'),
      'planningSessions': () =>
          _get('/api/products/$id/planning/sessions$_sessionPage'),
      'qualitySnapshot': () => _optional('/api/products/$id/quality/current'),
      'bugs': () => _get('/api/products/$id/bugs'),
      'verifications': () => _get('/api/products/$id/verifications'),
      'qualityWorkItems': () => _get('/api/products/$id/quality/work-items'),
      'qualitySessions': () =>
          _get('/api/products/$id/quality/sessions$_sessionPage'),
      'dispatcherStatus': () => _get('/api/products/$id/dispatcher/status'),
      'deliveryAttempts': () => _get('/api/products/$id/dispatcher/attempts'),
      // Het live-overzicht is aanvullend: een storing mag de pagina niet breken.
      'live': () async {
        try {
          return await _get('/api/products/$id/live');
        } catch (_) {
          return null;
        }
      },
    };
    final keys = loaders.keys.where(wanted.contains).toList();
    final values = await Future.wait(keys.map((key) => loaders[key]!()));
    final loaded = Map.fromIterables(keys, values);
    List<Map<String, Object?>> list(String key) =>
        (loaded[key] as List? ?? const [])
            .map((e) => (e as Map).cast<String, Object?>())
            .toList();
    Map<String, Object?>? map(String key) =>
        (loaded[key] as Map?)?.cast<String, Object?>();

    final epics = list('epics');
    final progress = <String, Map<String, Object?>>{};
    if (wanted.contains('epicProgress')) {
      final open = epics
          .where((epic) => !_terminalEpicStatuses.contains(epic['status']))
          .take(5)
          .map((epic) => _value(epic['id']))
          .toList();
      final entries = await Future.wait(
        open.map(
          (epicId) async => MapEntry(epicId, await epicProgress(epicId)),
        ),
      );
      for (final entry in entries) {
        if (entry.value != null) progress[entry.key] = entry.value!;
      }
    }

    return ProductWorkspaceData(
      product: product,
      assignment: map('assignment'),
      testConfiguration: map('testConfiguration'),
      schedules: list('schedules'),
      signals: list('signals'),
      questions: list('questions'),
      meetings: list('meetings'),
      decisions: list('decisions'),
      decisionArchive: list('decisionArchive'),
      epics: epics,
      designSessions: list('designSessions'),
      epicHistories: const {},
      stories: list('stories'),
      backlog: list('backlog'),
      planningWorkItems: list('planningWorkItems'),
      planningSessions: list('planningSessions'),
      qualitySnapshot: map('qualitySnapshot'),
      qualityHistory: const [],
      bugs: list('bugs'),
      verifications: list('verifications'),
      qualityWorkItems: list('qualityWorkItems'),
      qualitySessions: list('qualitySessions'),
      dispatcherStatus: map('dispatcherStatus') ?? const {},
      deliveryAttempts: list('deliveryAttempts'),
      live: map('live'),
      epicProgress: progress,
    );
  }

  @override
  Future<Map<String, Object?>?> epicProgress(String epicId) async {
    try {
      return ((await _get('/api/epics/${Uri.encodeComponent(epicId)}/progress'))
              as Map?)
          ?.cast<String, Object?>();
    } catch (_) {
      return null;
    }
  }

  @override
  Future<List<Map<String, Object?>>> processSessions(
    String productId,
    String process, {
    int limit = 25,
    String? before,
    bool excludeNoOps = true,
  }) async {
    final path = switch (process) {
      'PRODUCT_DESIGN' => 'design',
      'PRODUCT_PLANNING' => 'planning',
      'QUALITY_ASSURANCE' => 'quality',
      'SOFTWARE_FACTORY_DISPATCHER' => 'dispatcher',
      _ => throw const ProductFailure(400, 'Onbekend proces.'),
    };
    final query = Uri(
      queryParameters: {
        'limit': '$limit',
        'excludeNoOps': '$excludeNoOps',
        if (before != null && before.isNotEmpty) 'before': before,
      },
    ).query;
    return ((await _get('/api/products/$productId/$path/sessions?$query'))
                as List? ??
            const [])
        .map((e) => (e as Map).cast<String, Object?>())
        .toList();
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
  Future<void> answerQuestionDirectly(
    String questionId,
    int version,
    String answer,
  ) => _send('POST', '/api/products/questions/$questionId/answer-directly', {
    'answer': answer,
    'expectedVersion': version,
    'idempotencyKey': _key('answer-directly'),
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
  Future<void> approveProductRequestEpic(String epicId, int version) => _send(
    'POST',
    '/api/epics/$epicId/product-owner-approval',
    {'expectedVersion': version, 'idempotencyKey': _key('product-approval')},
  );

  @override
  Future<void> approveProductRequestEpicAsFactoryOwner(
    String epicId,
    int version,
  ) => _send('POST', '/api/epics/$epicId/factory-owner-approval', {
    'expectedVersion': version,
    'idempotencyKey': _key('factory-approval'),
  });

  @override
  Future<void> refineProductRequestEpic(
    String epicId,
    int version,
    String reason,
  ) => _send('POST', '/api/epics/$epicId/product-request-refinement', {
    'reason': reason,
    'expectedVersion': version,
    'idempotencyKey': _key('product-request-refinement'),
  });

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
  'AWAITING_PRODUCT_OWNER_APPROVAL' =>
    'Wacht op productinhoudelijke goedkeuring',
  'AWAITING_FACTORY_OWNER_APPROVAL' => 'Wacht op eindgoedkeuring',
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

/// Bekende Software Factory-modellen per supplier — bewust hier gedupliceerd,
/// zelfde patroon als Software Factory's eigen `dashboard-frontend/lib/ai_catalog.dart`
/// naast zijn `AiRouting.kt`. Nieuwe modelversie? Ook hier toevoegen.
const Map<String, List<String>> _softwareFactoryModelsBySupplier = {
  'claude': [
    'claude-opus-5',
    'claude-opus-4-8',
    'claude-opus-4-7',
    'claude-opus-4-6',
    'claude-opus-4-5',
    'claude-sonnet-5',
    'claude-sonnet-4-6',
    'claude-haiku-4-5',
  ],
  'copilot': [
    'claude-opus-4.5',
    'claude-sonnet-4.5',
    'claude-haiku-4.5',
    'gpt-4.1',
  ],
  'openai': ['gpt-5.6-sol', 'gpt-5.6-terra', 'gpt-5.6-luna'],
};

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
  String? _aiSupplier;
  String? _aiModel;

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
    _aiSupplier = assignment?['aiSupplier'] as String?;
    _aiModel = assignment?['aiModel'] as String?;
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
      'aiSupplier': _aiSupplier,
      'aiModel': _aiModel,
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
      const SizedBox(height: 24),
      SelectableText(
        'AI voor Software Factory',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      const SizedBox(height: 6),
      const SelectableText(
        'Standaard laat Software Factory zelf de supplier/model kiezen (Claude, Opus 5). Kies hier expliciet een andere combinatie voor dit product.',
      ),
      const SizedBox(height: 12),
      DropdownButtonFormField<String?>(
        key: const ValueKey('assignment-ai-supplier'),
        initialValue: _aiSupplier,
        decoration: const InputDecoration(labelText: 'AI-supplier'),
        items: [
          const DropdownMenuItem(
            value: null,
            child: Text('Standaard van Software Factory'),
          ),
          for (final supplier in _softwareFactoryModelsBySupplier.keys)
            DropdownMenuItem(value: supplier, child: Text(supplier)),
        ],
        onChanged: (value) => setState(() {
          _aiSupplier = value;
          _aiModel = null;
        }),
      ),
      const SizedBox(height: 16),
      DropdownButtonFormField<String?>(
        key: const ValueKey('assignment-ai-model'),
        initialValue: _aiModel,
        decoration: const InputDecoration(labelText: 'AI-model'),
        items: [
          const DropdownMenuItem(
            value: null,
            child: Text('Standaard van Software Factory'),
          ),
          for (final model
              in _softwareFactoryModelsBySupplier[_aiSupplier] ??
                  const <String>[])
            DropdownMenuItem(value: model, child: Text(model)),
        ],
        onChanged: _aiSupplier == null
            ? null
            : (value) => setState(() => _aiModel = value),
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
    this.isFactoryOwner = true,
    this.productMemberships = const {},
    super.key,
  });
  final ProductGateway gateway;
  final ProductWorkspaceSection section;
  final Widget? trailingContent;
  final String? initialProductId;
  final ValueChanged<String>? onProductSelected;
  final PageRefreshController? refreshController;
  final bool isFactoryOwner;
  final Set<String> productMemberships;
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
    } else if (oldWidget.section != widget.section) {
      // Iedere pagina laadt alleen zijn eigen gegevens.
      _data = null;
      unawaited(_loadProducts(_selected?.id ?? widget.initialProductId));
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
          : await widget.gateway.workspace(
              refreshedSelected,
              section: widget.section,
            );
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
          : await widget.gateway.workspace(selected, section: widget.section);
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
                if (widget.isFactoryOwner &&
                    (widget.section == ProductWorkspaceSection.overview ||
                        widget.section == ProductWorkspaceSection.settings))
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

  Map<String, Object?>? _currentEpic(ProductWorkspaceData data) {
    const order = [
      'VERIFYING',
      'ACTIVE',
      'IN_PLANNING',
      'AVAILABLE',
      'AWAITING_FACTORY_OWNER_APPROVAL',
      'AWAITING_PRODUCT_OWNER_APPROVAL',
      'AWAITING_APPROVAL',
      'NEEDS_REFINEMENT',
      'NEEDS_RESEARCH',
    ];
    for (final status in order) {
      final epic = data.epics
          .where((candidate) => candidate['status'] == status)
          .firstOrNull;
      if (epic != null) return epic;
    }
    return null;
  }

  Future<void> _openEpic(
    ProductWorkspaceData data,
    Map<String, Object?> epic,
  ) => showDialog<void>(
    context: context,
    builder: (_) => _EpicDetailDialog(
      epic: epic,
      gateway: widget.gateway,
      initialProgress: data.epicProgress[_value(epic['id'])],
      contentBuilder: (dialogContext) =>
          _epicContent(data, epic, dialogContext),
    ),
  );

  /// Laatste afgekeurde verificatie uit de epic-tijdlijn: het "waarom niet klaar".
  Map<String, Object?>? _latestFailure(Map<String, Object?>? progress) =>
      _asMaps(
        progress?['timeline'],
      ).where((event) => event['kind'] == 'VERIFICATION_FAILED').firstOrNull;

  Widget _overview(ProductWorkspaceData data) {
    final goal = data.assignment?['goal']?.toString().trim();
    final currentEpic = _currentEpic(data);
    final progress = currentEpic == null
        ? null
        : data.epicProgress[_value(currentEpic['id'])];
    final waiting = _asMap(progress?['waitingOn']);
    final failure = _latestFailure(progress);
    final currentStory = data.backlog
        .where((story) => story['status'] == 'IN_PROGRESS')
        .firstOrNull;
    final epicStories = currentEpic == null
        ? const <Map<String, Object?>>[]
        : data.stories
              .where(
                (story) =>
                    _value(story['epicId']) == _value(currentEpic['id']) &&
                    story['status'] != 'CANCELLED',
              )
              .toList();
    final done = epicStories.where((story) => story['status'] == 'DONE').length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ..._liveBanners(data.live),
        _InsightCard(
          children: [
            _Eyebrow(
              'Nu',
              trailing: currentEpic == null
                  ? null
                  : _ToneChip(
                      _epicStatusLabel(_value(currentEpic['status'])),
                      tone: _epicTone(_value(currentEpic['status'])),
                    ),
            ),
            if (currentEpic == null)
              SelectableText(
                'Nog geen actieve epic',
                style: Theme.of(context).textTheme.titleLarge,
              )
            else ...[
              InkWell(
                borderRadius: BorderRadius.circular(8),
                onTap: () => _openEpic(data, currentEpic),
                child: Text.rich(
                  TextSpan(
                    children: [
                      TextSpan(
                        text: _value(currentEpic['title']),
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      const TextSpan(
                        text: '  details →',
                        style: TextStyle(
                          color: ProductFactoryColors.primary,
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 14),
              if (progress != null)
                _JourneyView(steps: _asMaps(progress['steps']))
              else ...[
                SelectableText(
                  '$done van ${epicStories.length} stories opgeleverd',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 10),
                ClipRRect(
                  borderRadius: BorderRadius.circular(99),
                  child: LinearProgressIndicator(
                    value: epicStories.isEmpty ? 0 : done / epicStories.length,
                    minHeight: 7,
                    backgroundColor: const Color(0xffe8ede9),
                  ),
                ),
              ],
              if (waiting != null) _WaitingCallout(waitingOn: waiting),
              if (failure != null) ...[
                const SizedBox(height: 10),
                SelectableText.rich(
                  TextSpan(
                    children: [
                      const TextSpan(
                        text: 'Waarom nog niet klaar: ',
                        style: TextStyle(fontWeight: FontWeight.w700),
                      ),
                      TextSpan(
                        text:
                            '${_value(failure['title'])} (${_shortInstant(failure['at'])}). ${_localizeInstants(_value(failure['detail']))}',
                      ),
                    ],
                  ),
                  style: const TextStyle(
                    fontSize: 13,
                    color: ProductFactoryColors.muted,
                  ),
                ),
              ],
            ],
            if (currentStory != null && waiting == null) ...[
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
                            '#${currentStory['sequenceNumber']} · ${currentStory['title']}',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                        ],
                      ),
                    ),
                    const _ToneChip('In uitvoering', tone: _Tone.warn),
                  ],
                ),
              ),
            ],
          ],
        ),
        if (goal?.isNotEmpty == true || data.assignment != null)
          _InsightCard(
            color: const Color(0xfff6fbf7),
            borderColor: const Color(0xffd3e7da),
            children: [
              const _Eyebrow('Productdoel'),
              _ExpandableText(
                goal?.isNotEmpty == true
                    ? goal!
                    : 'Leg het productdoel vast bij Instellingen.',
              ),
            ],
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
        .where(
          (epic) => const {
            'AWAITING_APPROVAL',
            'AWAITING_PRODUCT_OWNER_APPROVAL',
            'AWAITING_FACTORY_OWNER_APPROVAL',
          }.contains(epic['status']),
        )
        .toList();
    final openBugs = data.bugs.where(_isOpenBug).toList();
    final total =
        openSignals.length +
        openQuestions.length +
        blockedWork.length +
        approvalEpics.length +
        openBugs.length;
    final completed = data.epics
        .where((epic) => epic['status'] == 'COMPLETED')
        .take(3)
        .toList();
    final attention = _InsightCard(
      children: [
        _Eyebrow('Vraagt aandacht', trailing: Text('$total')),
        if (total == 0)
          const SelectableText(
            'Er zijn nu geen blokkades, vragen, bugs of open signalen.',
          )
        else ...[
          ...approvalEpics.map(
            (epic) => ListTile(
              contentPadding: EdgeInsets.zero,
              onTap: () => _openEpic(data, epic),
              leading: const Icon(Icons.approval_outlined),
              title: SelectableText('${epic['title']}'),
              subtitle: SelectableText(
                _epicStatusLabel(_value(epic['status'])),
              ),
              trailing: const _ToneChip('Wacht op jou', tone: _Tone.live),
            ),
          ),
          ...openBugs
              .take(3)
              .map(
                (bug) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: _ToneChip(_value(bug['severity']), tone: _Tone.crit),
                  title: SelectableText(_value(bug['title'])),
                  subtitle: SelectableText(
                    'Open bug · sinds ${_shortInstant(bug['createdAt'])}',
                  ),
                ),
              ),
          ...blockedWork
              .take(3)
              .map(
                (item) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.error_outline),
                  title: SelectableText('${item['type']} · ${item['status']}'),
                  subtitle: SelectableText(
                    _value(item['blockedReason'] ?? item['result']),
                  ),
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
                  title: SelectableText(_value(signal['text']), maxLines: 3),
                  subtitle: const SelectableText('Open gebruikerssignaal'),
                ),
              ),
        ],
      ],
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LayoutBuilder(
          builder: (context, constraints) {
            final recent = _InsightCard(
              children: [
                const _Eyebrow('Recent afgerond'),
                if (completed.isEmpty)
                  const SelectableText('Nog geen afgeronde epics.')
                else
                  ...completed.map(
                    (epic) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      onTap: () => _openEpic(data, epic),
                      leading: const _ToneChip('✓', tone: _Tone.ok),
                      title: Text(
                        _value(epic['title']),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(_shortInstant(epic['updatedAt'])),
                    ),
                  ),
              ],
            );
            if (constraints.maxWidth < 860) {
              return Column(children: [attention, recent]);
            }
            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(flex: 3, child: attention),
                const SizedBox(width: 14),
                Expanded(flex: 2, child: recent),
              ],
            );
          },
        ),
        if (data.live != null)
          _InsightCard(
            children: [
              const _Eyebrow('Automatisering · laatste 24 uur'),
              _AutomationStrip(live: data.live!),
            ],
          ),
      ],
    );
  }

  bool _isOpenBug(Map<String, Object?> bug) => !const {
    'RESOLVED',
    'CLOSED',
    'REJECTED',
    'DUPLICATE',
    'WONT_FIX',
  }.contains(bug['status']);

  /// Procesblok onderaan een pagina: ritme, onopgeloste blokkades en ingeklapte historie.
  List<Widget> _processBlock(
    ProductWorkspaceData data,
    String process,
    List<Map<String, Object?>> sessions,
    Widget Function(Map<String, Object?> session) tile,
  ) {
    final live = _liveProcess(data.live, process);
    final runningShownAbove = _asMap(live?['running']) != null;
    final lastSucceeded = sessions.indexWhere(
      (session) => session['status'] == 'SUCCEEDED',
    );
    final pinned = <Map<String, Object?>>[];
    final history = <Map<String, Object?>>[];
    for (var i = 0; i < sessions.length; i++) {
      final session = sessions[i];
      final active = _activeProcessStatuses.contains(session['status']);
      final unresolvedFailure =
          _isFailedSession(session) &&
          (lastSucceeded == -1 || i < lastSucceeded);
      if (active && runningShownAbove) continue;
      if (active || unresolvedFailure) {
        pinned.add(session);
      } else {
        history.add(session);
      }
    }
    return [
      const Divider(height: 32),
      Row(
        children: [
          Expanded(
            child: SelectableText(
              '${_processLabel(process)}sproces',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
          if (live != null)
            _ToneChip(
              _scheduleRhythm(live),
              tone: live['enabled'] == true ? _Tone.ok : _Tone.neutral,
            ),
        ],
      ),
      if (live != null) ...[
        const SizedBox(height: 4),
        SelectableText(
          '${_lastRunLine(live)} · laatste 24 uur: ${_last24hLine(live)}',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 6),
        _RunTicks(hourly: _asMaps(live['hourly'])),
      ],
      const SizedBox(height: 8),
      ...pinned.map(tile),
      _CollapsibleHistory(
        title: 'Sessies met resultaat',
        subtitle: history.isEmpty
            ? 'nog geen'
            : '${history.length} recent · “niets te doen” verborgen',
        childrenBuilder: () => history.isEmpty
            ? const [SelectableText('Nog geen sessies met resultaat.')]
            : history.map(tile).toList(),
      ),
    ];
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

  Widget _operation(ProductWorkspaceData data) =>
      _section('Operatie', Icons.monitor_heart_outlined, [
        ..._liveBanners(data.live),
        if (data.live != null) ...[
          _AutomationStrip(live: data.live!),
          const SizedBox(height: 14),
        ],
        _OperationSessionsPanel(
          key: ValueKey('operation-${data.product.id}'),
          gateway: widget.gateway,
          productId: data.product.id,
          live: data.live,
          refreshController: widget.refreshController,
        ),
        ..._dispatcherOverview(data),
      ]);

  List<Widget> _dispatcherOverview(ProductWorkspaceData data) {
    final open = data.deliveryAttempts
        .where(
          (attempt) =>
              !const {'COMPLETED', 'CANCELLED'}.contains(attempt['status']),
        )
        .toList();
    final closed = data.deliveryAttempts
        .where((attempt) => !open.contains(attempt))
        .toList();
    return [
      const Divider(height: 32),
      SelectableText(
        'Software Factory-dispatcher',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      const SizedBox(height: 8),
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
      ...open.map(_deliveryAttemptTile),
      if (closed.isNotEmpty)
        _CollapsibleHistory(
          title: 'Afgeronde leveringen',
          subtitle: '${closed.length}',
          childrenBuilder: () => closed.map(_deliveryAttemptTile).toList(),
        ),
    ];
  }

  Widget _deliveryAttemptTile(Map<String, Object?> attempt) => ExpansionTile(
    leading: const Icon(Icons.local_shipping_outlined),
    title: Text(
      '${attempt['externalStoryId'] ?? 'nog geen storyKey'} · ${attempt['status']}',
    ),
    subtitle: Text(
      'Poging ${attempt['attemptCount']} · lokaal ${attempt['localCommandStatus']} · ${_shortInstant(attempt['createdAt'])}',
    ),
    childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
    expandedCrossAxisAlignment: CrossAxisAlignment.start,
    children: [
      SelectableText('Story ${_value(attempt['storyId'])}'),
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
  );

  Widget _planningSessionTile(Map<String, Object?> session) {
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
  }

  List<Widget> _inFlightStories(ProductWorkspaceData data) {
    final inProgress = data.backlog
        .where((story) => story['status'] == 'IN_PROGRESS')
        .toList();
    return inProgress.map((story) {
      final attempt = data.deliveryAttempts
          .where(
            (candidate) =>
                _value(candidate['storyId']) == _value(story['id']) &&
                !const {'COMPLETED', 'CANCELLED'}.contains(candidate['status']),
          )
          .firstOrNull;
      final since = _parseInstant(attempt?['createdAt']);
      final epic = data.epics
          .where(
            (candidate) => _value(candidate['id']) == _value(story['epicId']),
          )
          .firstOrNull;
      final externalStatus = _value(
        attempt?['externalStatus'] ?? data.dispatcherStatus['externalStatus'],
      );
      return _InsightCard(
        borderColor: const Color(0xfff1dcc0),
        children: [
          _Eyebrow(
            'Nu bij Software Factory',
            trailing: since == null
                ? null
                : Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text(
                        'wacht ',
                        style: TextStyle(color: Color(0xff95540e)),
                      ),
                      _ElapsedSince(
                        since,
                        style: const TextStyle(
                          color: Color(0xff95540e),
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
          ),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              CircleAvatar(
                backgroundColor: const Color(0xffe3f3ea),
                child: Text('${story['sequenceNumber']}'),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SelectableText(
                      _value(story['title']),
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    SelectableText(
                      [
                        if (story['type'] == 'BUGFIX') 'Bugfix',
                        if (epic != null) 'epic “${epic['title']}”',
                      ].join(' · '),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 24,
                      runSpacing: 6,
                      children: [
                        _labelValue(
                          'Software Factory',
                          _value(
                                story['externalStoryId'] ??
                                    attempt?['externalStoryId'],
                              ).isEmpty
                              ? 'nog niet aangemaakt'
                              : _value(
                                  story['externalStoryId'] ??
                                      attempt?['externalStoryId'],
                                ),
                        ),
                        _labelValue(
                          'Status daar',
                          externalStatus == 'OPEN'
                              ? 'OPEN · nog niet opgeleverd'
                              : externalStatus.isEmpty
                              ? 'onbekend'
                              : externalStatus,
                        ),
                        if (since != null)
                          _labelValue('Verstuurd', _shortDateTime(since)),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      );
    }).toList();
  }

  Widget _labelValue(String label, String value) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        label,
        style: const TextStyle(fontSize: 12, color: ProductFactoryColors.muted),
      ),
      SelectableText(
        value,
        style: const TextStyle(fontWeight: FontWeight.w600),
      ),
    ],
  );

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
    ..._liveBanners(
      data.live,
      processes: const {'PRODUCT_PLANNING', 'SOFTWARE_FACTORY_DISPATCHER'},
    ),
    ..._inFlightStories(data),
    if (data.dispatcherStatus['blocked'] == true)
      Card(
        color: Theme.of(context).colorScheme.errorContainer,
        child: ListTile(
          leading: const Icon(Icons.error_outline),
          title: const SelectableText('Dispatch geblokkeerd'),
          subtitle: SelectableText(
            '${data.dispatcherStatus['blockedReason'] ?? 'Onbekende blokkade.'}',
          ),
        ),
      ),
    SelectableText(
      'Productbrede backlog',
      style: Theme.of(context).textTheme.titleMedium,
    ),
    if (data.backlog.isEmpty)
      const SelectableText('De backlog is leeg.')
    else
      ..._groupedBacklog(data),
    ..._finishedStoriesByEpic(data),
    ..._planningWork(data),
    ..._processBlock(
      data,
      'PRODUCT_PLANNING',
      data.planningSessions,
      _planningSessionTile,
    ),
    ..._dispatcherOverview(data),
  ]);

  List<Widget> _finishedStoriesByEpic(ProductWorkspaceData data) {
    final finished = data.stories
        .where((story) => const {'DONE', 'CANCELLED'}.contains(story['status']))
        .toList();
    if (finished.isEmpty) return const [];
    final byEpic = <String, List<Map<String, Object?>>>{};
    for (final story in finished) {
      byEpic.putIfAbsent(_value(story['epicId']), () => []).add(story);
    }
    final done = finished.where((story) => story['status'] == 'DONE').length;
    return [
      const Divider(height: 32),
      SelectableText(
        'Afgerond en geannuleerd',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      SelectableText(
        '$done opgeleverd · ${finished.length - done} geannuleerd',
        style: Theme.of(context).textTheme.bodySmall,
      ),
      ...byEpic.entries.map((entry) {
        final epic = data.epics
            .where((candidate) => _value(candidate['id']) == entry.key)
            .firstOrNull;
        final delivered = entry.value
            .where((story) => story['status'] == 'DONE')
            .length;
        final stories = [...entry.value]
          ..sort(
            (a, b) => ((b['sequenceNumber'] as num?) ?? 0).compareTo(
              (a['sequenceNumber'] as num?) ?? 0,
            ),
          );
        return _CollapsibleHistory(
          title: epic == null ? 'Epic ${entry.key}' : _value(epic['title']),
          subtitle:
              '$delivered opgeleverd · ${entry.value.length - delivered} geannuleerd',
          childrenBuilder: () => stories
              .map(
                (story) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      SizedBox(
                        width: 40,
                        child: Text(
                          '#${story['sequenceNumber']}',
                          style: const TextStyle(
                            color: ProductFactoryColors.muted,
                          ),
                        ),
                      ),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            SelectableText(_value(story['title'])),
                            SelectableText(
                              story['status'] == 'DONE'
                                  ? [
                                      _value(story['externalStoryId']),
                                      _value(story['deliveredCommitSha']),
                                    ].where((v) => v.isNotEmpty).join(' · ')
                                  : _value(story['cancellationReason']),
                              maxLines: 2,
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      _ToneChip(
                        _storyStatusLabel(_value(story['status'])),
                        tone: _storyTone(_value(story['status'])),
                      ),
                    ],
                  ),
                ),
              )
              .toList(),
        );
      }),
    ];
  }

  List<Widget> _planningWork(ProductWorkspaceData data) {
    final open = data.planningWorkItems
        .where((item) => item['status'] != 'DONE')
        .toList();
    final done = data.planningWorkItems.length - open.length;
    Widget tile(Map<String, Object?> item) => ListTile(
      dense: true,
      contentPadding: EdgeInsets.zero,
      leading: const Icon(Icons.playlist_add_check),
      title: SelectableText('${item['type']} · ${item['status']}'),
      subtitle: SelectableText('${item['explanation']}'),
    );
    return [
      const Divider(height: 32),
      SelectableText(
        'Planningswerk',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      if (open.isEmpty)
        SelectableText(
          'Geen open planningswerk${done > 0 ? ' · $done afgerond' : ''}.',
        )
      else
        ...open.map(tile),
      if (done > 0 && open.isNotEmpty)
        _CollapsibleHistory(
          title: 'Afgerond planningswerk',
          subtitle: '$done',
          childrenBuilder: () => data.planningWorkItems
              .where((item) => item['status'] == 'DONE')
              .map(tile)
              .toList(),
        ),
    ];
  }

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
    title: Text(
      '${story['title']} · ${_storyStatusLabel(_value(story['status']))}',
    ),
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

  String _verificationSubject(
    ProductWorkspaceData data,
    Map<String, Object?> verification,
  ) {
    final targetId = _value(verification['targetId']);
    final story = data.stories
        .where((candidate) => _value(candidate['id']) == targetId)
        .firstOrNull;
    if (story != null) return '#${story['sequenceNumber']} ${story['title']}';
    final epic = data.epics
        .where((candidate) => _value(candidate['id']) == targetId)
        .firstOrNull;
    if (epic != null) return 'Epic · ${epic['title']}';
    final bug = data.bugs
        .where((candidate) => _value(candidate['id']) == targetId)
        .firstOrNull;
    if (bug != null) return 'Bug · ${bug['title']}';
    return '${verification['targetType']} $targetId';
  }

  Widget _verificationTile(
    ProductWorkspaceData data,
    Map<String, Object?> verification,
  ) {
    final passed = verification['outcome'] == 'PASSED';
    return ExpansionTile(
      tilePadding: EdgeInsets.zero,
      leading: _ToneChip(
        passed
            ? 'Geslaagd'
            : _value(verification['outcome']) == 'FAILED'
            ? 'Afgekeurd'
            : _value(verification['outcome']),
        tone: passed ? _Tone.ok : _Tone.crit,
      ),
      title: Text(
        _verificationSubject(data, verification),
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        '${_shortInstant(verification['createdAt'])} · ${verification['environment']} · revisie ${_value(verification['testedRevision']).isEmpty ? 'onbekend' : _value(verification['testedRevision']).substring(0, _value(verification['testedRevision']).length.clamp(0, 7))}',
      ),
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      expandedCrossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SelectableText(
          'Verificatie ${_value(verification['id'])} · ${verification['targetType']} v${verification['targetVersion']}',
        ),
        ...(verification['checks'] as List? ?? const []).map(
          (check) => SelectableText('• $check'),
        ),
        SelectableText(
          'Bewijs: ${(verification['evidence'] as Map?)?['description'] ?? 'Geen publiek bewijs'}',
        ),
        ...(verification['missingCoverage'] as List? ?? const []).map(
          (gap) => SelectableText('Niet gedekt: $gap'),
        ),
        if (verification['blockedReason'] != null)
          SelectableText('Blokkade: ${verification['blockedReason']}'),
      ],
    );
  }

  Widget _bugCard(ProductWorkspaceData data, Map<String, Object?> bug) {
    final fix = data.stories
        .where((story) => _value(story['bugId']) == _value(bug['id']))
        .lastOrNull;
    return _InsightCard(
      borderColor: const Color(0xffefc9bf),
      children: [
        _Eyebrow(
          'Open bug · sinds ${_shortInstant(bug['createdAt'])}',
          trailing: _ToneChip(_value(bug['severity']), tone: _Tone.crit),
        ),
        SelectableText(
          _value(bug['title']),
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        SelectableText('Werkelijk: ${bug['actualBehaviour']}'),
        SelectableText('Verwacht: ${bug['expectedBehaviour']}'),
        if ((bug['reproductionSteps'] as List? ?? const []).isNotEmpty)
          SelectableText(
            'Reproduceren: ${(bug['reproductionSteps'] as List).join(' → ')}',
          ),
        const SizedBox(height: 8),
        SelectableText(
          fix == null
              ? 'Nog geen bugfix-story gepland.'
              : 'Fix: #${fix['sequenceNumber']} ${fix['title']} · ${_storyStatusLabel(_value(fix['status']))}${_value(fix['externalStoryId']).isEmpty ? '' : ' · ${fix['externalStoryId']}'}',
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
      ],
    );
  }

  Widget _quality(ProductWorkspaceData data) {
    final snapshot = data.qualitySnapshot;
    final openBugs = data.bugs.where(_isOpenBug).toList();
    final resolvedBugs = data.bugs.where((bug) => !_isOpenBug(bug)).toList();
    final openWork = data.qualityWorkItems
        .where((item) => item['status'] != 'DONE')
        .toList();
    final doneWork = data.qualityWorkItems
        .where((item) => item['status'] == 'DONE')
        .toList();
    final verifications = [
      ...data.verifications,
    ]..sort((a, b) => _value(b['createdAt']).compareTo(_value(a['createdAt'])));
    Widget workTile(Map<String, Object?> item) => ListTile(
      contentPadding: EdgeInsets.zero,
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
                () => widget.gateway.retryQualityWorkItem(_value(item['id'])),
              ),
              child: const Text('Retry now'),
            )
          : null,
      isThreeLine: true,
    );
    return _section('Kwaliteitsbewaking', Icons.verified_outlined, [
      Align(
        alignment: Alignment.centerRight,
        child: FilledButton.icon(
          onPressed: () =>
              _mutate(() => widget.gateway.runQuality(data.product.id)),
          icon: const Icon(Icons.play_arrow),
          label: const Text('Kwaliteit starten of hervatten'),
        ),
      ),
      const SizedBox(height: 8),
      ..._liveBanners(data.live, processes: const {'QUALITY_ASSURANCE'}),
      if (snapshot == null)
        const SelectableText('Nog geen werkelijk getest kwaliteitsbeeld.')
      else
        _InsightCard(
          children: [
            Wrap(
              spacing: 32,
              runSpacing: 12,
              children: [
                _labelValue('Omgeving', _value(snapshot['environment'])),
                _labelValue(
                  'Laatst getest',
                  _shortInstant(snapshot['capturedAt']),
                ),
                _labelValue(
                  'Revisie',
                  _value(snapshot['productRevision']).length > 7
                      ? _value(snapshot['productRevision']).substring(0, 7)
                      : _value(snapshot['productRevision']),
                ),
                _labelValue(
                  'Open bugs',
                  openBugs.isEmpty
                      ? 'geen'
                      : openBugs
                            .map((bug) => _value(bug['severity']))
                            .join(', '),
                ),
              ],
            ),
            if ((snapshot['risks'] as List? ?? const []).isNotEmpty) ...[
              const SizedBox(height: 10),
              SelectableText(
                'Risico’s: ${(snapshot['risks'] as List).join(', ')}',
              ),
            ],
          ],
        ),
      ...openBugs.map((bug) => _bugCard(data, bug)),
      const Divider(height: 28),
      SelectableText(
        'Werk en retries',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      if (openWork.isEmpty)
        SelectableText(
          'Geen kwaliteitswerk in de queue${doneWork.isEmpty ? '' : ' · ${doneWork.length} afgerond'}.',
        )
      else
        ...openWork.map(workTile),
      if (doneWork.isNotEmpty)
        _CollapsibleHistory(
          title: 'Afgerond kwaliteitswerk',
          subtitle: '${doneWork.length}',
          childrenBuilder: () => doneWork.map(workTile).toList(),
        ),
      const Divider(height: 28),
      SelectableText(
        'Verificaties',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      if (verifications.isEmpty)
        const SelectableText('Nog geen verificaties.')
      else
        ...verifications.take(5).map((v) => _verificationTile(data, v)),
      if (verifications.length > 5)
        _CollapsibleHistory(
          title: 'Oudere verificaties',
          subtitle: '${verifications.length - 5}',
          childrenBuilder: () => verifications
              .skip(5)
              .map((v) => _verificationTile(data, v))
              .toList(),
        ),
      if (resolvedBugs.isNotEmpty)
        _CollapsibleHistory(
          title: 'Opgeloste bugs',
          subtitle: '${resolvedBugs.length}',
          childrenBuilder: () => resolvedBugs
              .map(
                (bug) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: _ToneChip(_value(bug['severity'])),
                  title: SelectableText(_value(bug['title'])),
                  subtitle: SelectableText('${bug['summary']}'),
                ),
              )
              .toList(),
        ),
      ..._processBlock(
        data,
        'QUALITY_ASSURANCE',
        data.qualitySessions,
        (session) => _ProcessSessionTile(
          session: session,
          icon: _isFailedSession(session)
              ? Icons.error_outline
              : Icons.science_outlined,
          details:
              '${session['resultSummary'] ?? session['blockedReason'] ?? 'Tester-AI wordt duurzaam gevolgd.'}\n'
              '${(session['aiTaskIds'] as List? ?? const []).length} AI-taak/taken · Git ${session['repositoryCommitSha'] ?? 'nog niet bevroren'}',
        ),
      ),
    ]);
  }

  String? _designTab;

  static const _approvalStatuses = {
    'AWAITING_APPROVAL',
    'AWAITING_PRODUCT_OWNER_APPROVAL',
    'AWAITING_FACTORY_OWNER_APPROVAL',
  };

  String _epicTabOf(Map<String, Object?> epic) {
    final status = _value(epic['status']);
    if (_approvalStatuses.contains(status)) return 'approval';
    if (_terminalEpicStatuses.contains(status)) return 'closed';
    return 'open';
  }

  Widget _epicCard(ProductWorkspaceData data, Map<String, Object?> epic) {
    final id = _value(epic['id']);
    final stories = data.stories
        .where((story) => _value(story['epicId']) == id)
        .toList();
    final delivered = stories
        .where((story) => story['status'] == 'DONE')
        .length;
    final active = stories
        .where(
          (story) => const {'TODO', 'IN_PROGRESS'}.contains(story['status']),
        )
        .length;
    final waiting = _asMap(data.epicProgress[id]?['waitingOn']);
    final since = _parseInstant(waiting?['since']);
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => _openEpic(data, epic),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      _value(epic['title']),
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  _ToneChip(
                    _epicStatusLabel(_value(epic['status'])),
                    tone: _epicTone(_value(epic['status'])),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                _value(epic['summary']),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Color(0xff4d6663)),
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 18,
                runSpacing: 4,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  Text(
                    stories.isEmpty
                        ? 'Nog geen stories'
                        : '$delivered opgeleverd${active > 0 ? ' · $active open' : ''}',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  Text(
                    'versie ${epic['version']} · ${_shortInstant(epic['updatedAt'])}',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  if (waiting != null)
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.hourglass_top,
                          size: 15,
                          color: Color(0xff95540e),
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${_value(waiting['title'])}${since == null ? '' : ' · '}',
                          style: const TextStyle(
                            color: Color(0xff95540e),
                            fontWeight: FontWeight.w600,
                            fontSize: 12.5,
                          ),
                        ),
                        if (since != null)
                          _ElapsedSince(
                            since,
                            style: const TextStyle(
                              color: Color(0xff95540e),
                              fontWeight: FontWeight.w600,
                              fontSize: 12.5,
                            ),
                          ),
                      ],
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _design(ProductWorkspaceData data) {
    final counts = <String, int>{'open': 0, 'approval': 0, 'closed': 0};
    for (final epic in data.epics) {
      counts[_epicTabOf(epic)] = counts[_epicTabOf(epic)]! + 1;
    }
    final tab =
        _designTab ??
        (counts['open']! > 0
            ? 'open'
            : counts['approval']! > 0
            ? 'approval'
            : 'closed');
    final visible = data.epics.where((epic) => _epicTabOf(epic) == tab);
    const labels = {
      'open': 'Lopend',
      'approval': 'Wacht op goedkeuring',
      'closed': 'Afgerond en gestopt',
    };
    return _section('Ontwerp', Icons.architecture_outlined, [
      if (widget.isFactoryOwner)
        Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            FilledButton.icon(
              onPressed: () => _mutate(
                () => widget.gateway.runProductDesign(data.product.id),
              ),
              icon: const Icon(Icons.play_arrow),
              label: const Text('Productontwerp starten of hervatten'),
            ),
          ],
        ),
      const SizedBox(height: 12),
      ..._liveBanners(data.live, processes: const {'PRODUCT_DESIGN'}),
      SelectableText('Epics', style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 8),
      if (data.epics.isEmpty)
        const SelectableText('Nog geen epics gepubliceerd.')
      else ...[
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final entry in labels.entries)
              ChoiceChip(
                label: Text('${entry.value} · ${counts[entry.key]}'),
                selected: tab == entry.key,
                onSelected: (_) => setState(() => _designTab = entry.key),
              ),
          ],
        ),
        const SizedBox(height: 12),
        if (visible.isEmpty)
          SelectableText(switch (tab) {
            'approval' => 'Er wachten geen epics op goedkeuring.',
            'open' => 'Er loopt nu geen epic.',
            _ => 'Nog geen afgeronde of gestopte epics.',
          })
        else
          ...visible.map((epic) => _epicCard(data, epic)),
      ],
      ..._processBlock(
        data,
        'PRODUCT_DESIGN',
        data.designSessions,
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
  }

  /// Volledige epicinhoud en acties; getoond in het epicdetailvenster.
  List<Widget> _epicContent(
    ProductWorkspaceData data,
    Map<String, Object?> epic,
    BuildContext dialogContext,
  ) {
    VoidCallback closeThen(Future<void> Function() action) => () {
      Navigator.pop(dialogContext);
      unawaited(action());
    };
    return [
      SelectableText('Epic ${_value(epic['id'])} · versie ${epic['version']}'),
      if (epic['sourceProductRequestId'] != null)
        SelectableText(
          'Bronverzoek ${epic['sourceProductRequestId']} · requestversie ${epic['sourceProductRequestVersion']}',
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
      if (epic['status'] == 'AWAITING_PRODUCT_OWNER_APPROVAL') ...[
        const SizedBox(height: 8),
        Card(
          color: Theme.of(context).colorScheme.primaryContainer,
          child: const ListTile(
            leading: Icon(Icons.fact_check_outlined),
            title: SelectableText('Productinhoudelijke beoordeling nodig'),
            subtitle: SelectableText(
              'De aangewezen product owner controleert deze exacte epicversie eerst. Daarna volgt de eindgoedkeuring.',
            ),
          ),
        ),
      ],
      if (epic['status'] == 'AWAITING_FACTORY_OWNER_APPROVAL') ...[
        const SizedBox(height: 8),
        Card(
          color: Theme.of(context).colorScheme.secondaryContainer,
          child: const ListTile(
            leading: Icon(Icons.verified_user_outlined),
            title: SelectableText('Eindgoedkeuring nodig'),
            subtitle: SelectableText(
              'De product owner heeft deze versie goedgekeurd. De factory owner beoordeelt nu de technische en bredere gevolgen.',
            ),
          ),
        ),
      ],
      const SizedBox(height: 8),
      SelectableText('Probleem', style: Theme.of(context).textTheme.labelLarge),
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
          final variants = ((screen['artifacts'] as Map?)?.keys ?? const [])
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
        (epic['uxArtifacts'] as List? ?? const <Object?>[]).cast<Object?>(),
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
      ...((epic['readiness'] as Map?)?['unmetConditions'] as List? ?? const [])
          .map((condition) => SelectableText('• $condition')),
      ...((epic['readiness'] as Map?)?['openQuestions'] as List? ?? const [])
          .map((question) => SelectableText('Open vraag: $question')),
      const SizedBox(height: 8),
      SelectableText(
        'Onderzochte bronnen',
        style: Theme.of(context).textTheme.labelLarge,
      ),
      if ((epic['researchSources'] as List? ?? const []).isEmpty)
        const SelectableText('Nog geen concrete externe bronnen onderzocht.')
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
            title: SelectableText('${source['name']} · ${source['status']}'),
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
      const SizedBox(height: 16),
      Wrap(
        alignment: WrapAlignment.end,
        spacing: 8,
        runSpacing: 8,
        children: [
          if (widget.isFactoryOwner && epic['status'] == 'AWAITING_APPROVAL')
            FilledButton.icon(
              onPressed: closeThen(() => _approveEpic(epic)),
              icon: const Icon(Icons.check),
              label: const Text('Goedkeuren voor planning'),
            ),
          if (epic['status'] == 'AWAITING_PRODUCT_OWNER_APPROVAL' &&
              widget.productMemberships.contains(data.product.id))
            FilledButton.icon(
              onPressed: closeThen(
                () => _approveProductRequestEpic(epic, factoryOwner: false),
              ),
              icon: const Icon(Icons.fact_check_outlined),
              label: const Text('Productinhoud goedkeuren'),
            ),
          if (widget.isFactoryOwner &&
              epic['status'] == 'AWAITING_FACTORY_OWNER_APPROVAL')
            FilledButton.icon(
              onPressed: closeThen(
                () => _approveProductRequestEpic(epic, factoryOwner: true),
              ),
              icon: const Icon(Icons.verified_user_outlined),
              label: const Text('Eindgoedkeuring geven'),
            ),
          if (const {
            'AWAITING_PRODUCT_OWNER_APPROVAL',
            'AWAITING_FACTORY_OWNER_APPROVAL',
          }.contains(epic['status']))
            OutlinedButton.icon(
              onPressed: closeThen(
                () => _requestProductRequestEpicRefinement(epic),
              ),
              icon: const Icon(Icons.undo_outlined),
              label: const Text('Terugsturen'),
            ),
          if (widget.isFactoryOwner &&
              const {
                'AWAITING_APPROVAL',
                'AVAILABLE',
                'IN_PLANNING',
                'ACTIVE',
                'VERIFYING',
                'COMPLETED',
                'NOT_SUCCESSFUL',
              }.contains(epic['status']))
            OutlinedButton.icon(
              onPressed: closeThen(() => _requestEpicRefinement(epic)),
              icon: const Icon(Icons.undo_outlined),
              label: const Text('Terugsturen voor verdere uitwerking'),
            ),
          if (widget.isFactoryOwner &&
              const {
                'NEEDS_RESEARCH',
                'NEEDS_REFINEMENT',
                'AWAITING_APPROVAL',
                'AVAILABLE',
              }.contains(epic['status']))
            TextButton.icon(
              onPressed: closeThen(
                () => _epicReasonAction(epic, cancel: false),
              ),
              icon: const Icon(Icons.archive_outlined),
              label: const Text('Epic intrekken'),
            ),
          if (widget.isFactoryOwner &&
              const {
                'IN_PLANNING',
                'ACTIVE',
                'VERIFYING',
              }.contains(epic['status']))
            TextButton.icon(
              onPressed: closeThen(() => _epicReasonAction(epic, cancel: true)),
              icon: const Icon(Icons.cancel_outlined),
              label: const Text('Epic annuleren'),
            ),
          if (widget.isFactoryOwner &&
              const {
                'AVAILABLE',
                'IN_PLANNING',
                'ACTIVE',
              }.contains(epic['status']))
            TextButton.icon(
              onPressed: closeThen(() => _reprioritizeEpic(epic)),
              icon: const Icon(Icons.priority_high),
              label: const Text('Voorrang geven'),
            ),
        ],
      ),
    ];
  }

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

  Future<void> _approveProductRequestEpic(
    Map<String, Object?> epic, {
    required bool factoryOwner,
  }) => _mutate(
    () => factoryOwner
        ? widget.gateway.approveProductRequestEpicAsFactoryOwner(
            _value(epic['id']),
            (epic['version'] as num).toInt(),
          )
        : widget.gateway.approveProductRequestEpic(
            _value(epic['id']),
            (epic['version'] as num).toInt(),
          ),
  );

  Future<void> _requestProductRequestEpicRefinement(
    Map<String, Object?> epic,
  ) => _textAction(
    'Epic terugsturen',
    'Wat moet in een nieuwe versie worden aangepast?',
    (reason) => widget.gateway.refineProductRequestEpic(
      _value(epic['id']),
      (epic['version'] as num).toInt(),
      reason,
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
                  if (q['status'] == 'OPEN')
                    IconButton(
                      tooltip: 'Direct beantwoorden',
                      onPressed: () => _textAction(
                        'Vraag direct beantwoorden',
                        'Antwoord',
                        (answer) => widget.gateway.answerQuestionDirectly(
                          _value(q['id']),
                          (q['version'] as num).toInt(),
                          answer,
                        ),
                      ),
                      icon: const Icon(Icons.edit_outlined),
                    ),
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
    ...data.schedules.map((s) {
      final live = _liveProcess(data.live, _value(s['process']));
      final next = _parseInstant(s['nextRunAt']);
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: ListTile(
            title: SelectableText(_processLabel(_value(s['process']))),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SelectableText(
                  '${_humanPattern((s['pattern'] as Map?)?.cast<String, Object?>())} · ${s['timezone']} · '
                  'volgende start ${next == null ? 'uitgeschakeld' : _shortDateTime(next)}',
                ),
                if (live != null) ...[
                  SelectableText(
                    '${_lastRunLine(live)} · laatste 24 uur: ${_last24hLine(live)}',
                  ),
                  const SizedBox(height: 6),
                  SizedBox(
                    width: 320,
                    child: _RunTicks(hourly: _asMaps(live['hourly'])),
                  ),
                ],
              ],
            ),
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
      );
    }),
    const SizedBox(height: 8),
    const SelectableText(
      'Alle afzonderlijke runs en sessies staan onder Beheer → Operatie.',
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
        scrollable: true,
        title: SelectableText(title),
        content: SizedBox(
          width: 640,
          child: TextField(
            key: const ValueKey('long-text-dialog-input'),
            controller: text,
            autofocus: true,
            minLines: 6,
            maxLines: 16,
            keyboardType: TextInputType.multiline,
            textInputAction: TextInputAction.newline,
            decoration: InputDecoration(
              labelText: label,
              alignLabelWithHint: true,
            ),
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
    final value = ok == true ? text.text.trim() : '';
    text.dispose();
    if (value.isNotEmpty) {
      await _mutate(() => operation(value));
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
