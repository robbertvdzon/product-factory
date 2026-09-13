part of 'product_workspace.dart';

// Bouwstenen voor "nu en wacht op": epic-reis, wachtmelding, live sessies,
// automatiseringsritme, epicdetail en de gefilterde operatielijst.

const _terminalEpicStatuses = {
  'COMPLETED',
  'NOT_SUCCESSFUL',
  'SUPERSEDED',
  'WITHDRAWN',
  'CANCELLED',
};

const _scheduledProcesses = [
  'PRODUCT_DESIGN',
  'PRODUCT_PLANNING',
  'QUALITY_ASSURANCE',
  'SOFTWARE_FACTORY_DISPATCHER',
];

Map<String, Object?>? _asMap(Object? value) =>
    value is Map ? value.cast<String, Object?>() : null;

List<Map<String, Object?>> _asMaps(Object? value) =>
    (value as List? ?? const [])
        .whereType<Map>()
        .map((entry) => entry.cast<String, Object?>())
        .toList();

String _two(int value) => value.toString().padLeft(2, '0');

String _shortDateTime(DateTime value) {
  final now = DateTime.now();
  final time = '${_two(value.hour)}:${_two(value.minute)}';
  if (value.year == now.year &&
      value.month == now.month &&
      value.day == now.day) {
    return 'vandaag $time';
  }
  return '${_two(value.day)}-${_two(value.month)} $time';
}

String _shortInstant(Object? value, {String fallback = 'onbekend'}) {
  final parsed = _parseInstant(value);
  return parsed == null ? fallback : _shortDateTime(parsed);
}

final RegExp _isoInstantPattern = RegExp(
  r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:\d{2})',
);

/// Vervangt ISO-tijdstippen in vrije tekst door lokale, korte tijden.
String _localizeInstants(String text) => text.replaceAllMapped(
  _isoInstantPattern,
  (match) => _shortInstant(match.group(0), fallback: match.group(0)!),
);

String _compactDuration(Duration value) {
  if (value.isNegative) return 'net';
  if (value.inDays > 0) return '${value.inDays} d ${value.inHours % 24} u';
  if (value.inHours > 0) return '${value.inHours} u ${value.inMinutes % 60} m';
  if (value.inMinutes > 0) return '${value.inMinutes} min';
  return 'net';
}

String _processLabel(String process) => switch (process) {
  'PRODUCT_DESIGN' => 'Ontwerp',
  'PRODUCT_PLANNING' => 'Planning',
  'QUALITY_ASSURANCE' => 'Kwaliteit',
  'SOFTWARE_FACTORY_DISPATCHER' => 'Dispatcher',
  _ => process,
};

String _storyStatusLabel(String status) => switch (status) {
  'TODO' => 'Te doen',
  'IN_PROGRESS' => 'In uitvoering',
  'DONE' => 'Opgeleverd',
  'CANCELLED' => 'Geannuleerd',
  'BLOCKED' => 'Geblokkeerd',
  _ => status,
};

String _sessionStatusLabel(String status) => switch (status) {
  'RUNNING' => 'Bezig',
  'WAITING_FOR_AI' => 'Wacht op AI',
  'BLOCKED' => 'Geblokkeerd',
  'SUCCEEDED' => 'Geslaagd',
  'FAILED' => 'Mislukt',
  'CANCELLED' => 'Geannuleerd',
  _ => status,
};

String _waitingActorLabel(String actor) => switch (actor) {
  'PRODUCT_DESIGN' => 'Productontwerp',
  'FACTORY_OWNER' => 'factory owner',
  'PRODUCT_OWNER' => 'product owner',
  'PRODUCT_PLANNING' => 'Productplanning',
  'DISPATCHER' => 'dispatcher',
  'SOFTWARE_FACTORY' => 'Software Factory',
  'QUALITY' => 'Kwaliteit',
  _ => actor,
};

bool _isNoOpSession(Map<String, Object?> session) =>
    session['noOp'] == true ||
    (session['status'] == 'SUCCEEDED' &&
        _value(session['resultSummary']).contains('succesvolle no-op'));

bool _isFailedSession(Map<String, Object?> session) =>
    const {'FAILED', 'BLOCKED'}.contains(session['status']);

enum _Tone { neutral, ok, warn, crit, live }

class _ToneColors {
  const _ToneColors(this.background, this.foreground);
  final Color background;
  final Color foreground;

  static _ToneColors of(_Tone tone) => switch (tone) {
    _Tone.neutral => const _ToneColors(Color(0xffedf2ed), Color(0xff31494a)),
    _Tone.ok => const _ToneColors(Color(0xffe3f3ea), Color(0xff2c6f59)),
    _Tone.warn => const _ToneColors(Color(0xfffdf0de), Color(0xff95540e)),
    _Tone.crit => const _ToneColors(Color(0xfff9e4de), Color(0xffa23a27)),
    _Tone.live => const _ToneColors(Color(0xffd7f6ec), Color(0xff0d6b52)),
  };
}

_Tone _sessionTone(Map<String, Object?> session) {
  final status = _value(session['status']);
  if (_activeProcessStatuses.contains(status)) return _Tone.live;
  if (_isFailedSession(session)) return _Tone.crit;
  if (_isNoOpSession(session)) return _Tone.neutral;
  if (status == 'CANCELLED') return _Tone.warn;
  return _Tone.ok;
}

_Tone _storyTone(String status) => switch (status) {
  'DONE' => _Tone.ok,
  'CANCELLED' => _Tone.crit,
  'IN_PROGRESS' => _Tone.warn,
  _ => _Tone.neutral,
};

_Tone _epicTone(String status) => switch (status) {
  'COMPLETED' => _Tone.ok,
  'NOT_SUCCESSFUL' || 'CANCELLED' || 'WITHDRAWN' => _Tone.crit,
  'SUPERSEDED' => _Tone.neutral,
  'AWAITING_APPROVAL' ||
  'AWAITING_PRODUCT_OWNER_APPROVAL' ||
  'AWAITING_FACTORY_OWNER_APPROVAL' => _Tone.live,
  _ => _Tone.warn,
};

class _ToneChip extends StatelessWidget {
  const _ToneChip(this.label, {this.tone = _Tone.neutral});

  final String label;
  final _Tone tone;

  @override
  Widget build(BuildContext context) {
    final colors = _ToneColors.of(tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3),
      decoration: BoxDecoration(
        color: colors.background,
        borderRadius: BorderRadius.circular(99),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: colors.foreground,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _Eyebrow extends StatelessWidget {
  const _Eyebrow(this.text, {this.trailing});

  final String text;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: Row(
      children: [
        Expanded(
          child: Text(
            text.toUpperCase(),
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: ProductFactoryColors.muted,
              fontWeight: FontWeight.w800,
              letterSpacing: 1.3,
            ),
          ),
        ),
        ?trailing,
      ],
    ),
  );
}

class _InsightCard extends StatelessWidget {
  const _InsightCard({required this.children, this.borderColor, this.color});

  final List<Widget> children;
  final Color? borderColor;
  final Color? color;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    margin: const EdgeInsets.only(bottom: 14),
    padding: const EdgeInsets.all(20),
    decoration: BoxDecoration(
      color: color ?? ProductFactoryColors.surface,
      borderRadius: BorderRadius.circular(14),
      border: Border.all(color: borderColor ?? ProductFactoryColors.outline),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: children,
    ),
  );
}

/// Toont "5 u 23 m" en loopt rustig mee (twee keer per minuut).
class _ElapsedSince extends StatefulWidget {
  const _ElapsedSince(this.since, {this.style});

  final DateTime since;
  final TextStyle? style;

  @override
  State<_ElapsedSince> createState() => _ElapsedSinceState();
}

class _ElapsedSinceState extends State<_ElapsedSince> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 30), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Text(
    _compactDuration(DateTime.now().difference(widget.since)),
    style: widget.style,
  );
}

class _JourneyView extends StatelessWidget {
  const _JourneyView({required this.steps});

  final List<Map<String, Object?>> steps;

  @override
  Widget build(BuildContext context) {
    if (steps.isEmpty) return const SizedBox.shrink();
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth / steps.length;
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var i = 0; i < steps.length; i++)
              SizedBox(
                width: width,
                child: _JourneyStep(
                  step: steps[i],
                  first: i == 0,
                  previousReached:
                      i > 0 && _value(steps[i]['state']) != 'PENDING',
                ),
              ),
          ],
        );
      },
    );
  }
}

class _JourneyStep extends StatelessWidget {
  const _JourneyStep({
    required this.step,
    required this.first,
    required this.previousReached,
  });

  final Map<String, Object?> step;
  final bool first;
  final bool previousReached;

  @override
  Widget build(BuildContext context) {
    final state = _value(step['state']);
    final (fill, border, icon, iconColor) = switch (state) {
      'DONE' => (
        ProductFactoryColors.primary,
        ProductFactoryColors.primary,
        Icons.check,
        Colors.white,
      ),
      'FAILED' => (
        const Color(0xffb2432f),
        const Color(0xffb2432f),
        Icons.close,
        Colors.white,
      ),
      'CURRENT' => (
        Colors.white,
        ProductFactoryColors.mint,
        Icons.circle,
        const Color(0xff0d6b52),
      ),
      _ => (
        Colors.white,
        ProductFactoryColors.outline,
        null,
        ProductFactoryColors.muted,
      ),
    };
    final rawDetail = step['detail'];
    final parsed = _parseInstant(rawDetail);
    final detail = parsed == null
        ? _value(rawDetail)
        : '${_two(parsed.day)}-${_two(parsed.month)}';
    return Column(
      children: [
        SizedBox(
          height: 26,
          child: Stack(
            alignment: Alignment.center,
            children: [
              if (!first)
                Positioned(
                  left: 0,
                  right: 0,
                  child: FractionallySizedBox(
                    alignment: Alignment.centerLeft,
                    widthFactor: .5,
                    child: Container(
                      height: 2,
                      color: previousReached
                          ? ProductFactoryColors.primary
                          : ProductFactoryColors.outline,
                    ),
                  ),
                ),
              Container(
                width: 24,
                height: 24,
                decoration: BoxDecoration(
                  color: fill,
                  shape: BoxShape.circle,
                  border: Border.all(color: border, width: 2),
                  boxShadow: state == 'CURRENT'
                      ? const [
                          BoxShadow(color: Color(0xffc9f3e5), spreadRadius: 4),
                        ]
                      : null,
                ),
                child: icon == null
                    ? null
                    : Icon(
                        icon,
                        size: state == 'CURRENT' ? 9 : 14,
                        color: iconColor,
                      ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 6),
        Text(
          _value(step['label']),
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600),
        ),
        if (detail.isNotEmpty)
          Text(
            detail,
            textAlign: TextAlign.center,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 11,
              color: ProductFactoryColors.muted,
            ),
          ),
      ],
    );
  }
}

class _WaitingCallout extends StatelessWidget {
  const _WaitingCallout({required this.waitingOn});

  final Map<String, Object?> waitingOn;

  @override
  Widget build(BuildContext context) {
    final since = _parseInstant(waitingOn['since']);
    final next = _value(waitingOn['next']);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(top: 14),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xfffff8ee),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xfff1dcc0)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: const Color(0xfffdf0de),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(
              Icons.hourglass_top,
              color: Color(0xff95540e),
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SelectableText(
                  _value(waitingOn['title']).isEmpty
                      ? 'Wacht op ${_waitingActorLabel(_value(waitingOn['actor']))}'
                      : _value(waitingOn['title']),
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
                if (_value(waitingOn['detail']).isNotEmpty)
                  SelectableText(_value(waitingOn['detail'])),
                if (since != null || next.isNotEmpty)
                  SelectableText(
                    [
                      if (since != null) 'Sinds ${_shortDateTime(since)}',
                      if (next.isNotEmpty) next,
                    ].join(' · '),
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: ProductFactoryColors.muted,
                    ),
                  ),
              ],
            ),
          ),
          if (since != null) ...[
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                _ElapsedSince(
                  since,
                  style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: Color(0xff95540e),
                  ),
                ),
                const Text(
                  'wachttijd',
                  style: TextStyle(
                    fontSize: 11,
                    color: ProductFactoryColors.muted,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _LiveSessionBanner extends StatelessWidget {
  const _LiveSessionBanner({required this.process, required this.session});

  final String process;
  final Map<String, Object?> session;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    margin: const EdgeInsets.only(bottom: 14),
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
    decoration: BoxDecoration(
      gradient: const LinearGradient(
        colors: [Color(0xffe9fbf4), ProductFactoryColors.surface],
      ),
      borderRadius: BorderRadius.circular(12),
      border: Border.all(color: const Color(0xffa9e8d3)),
    ),
    child: Row(
      children: [
        const Icon(Icons.play_circle_outline, color: Color(0xff0d6b52)),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SelectableText(
                '${_processLabel(process)}sessie loopt',
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              _ProcessTiming(session: session),
            ],
          ),
        ),
        _ToneChip(
          _sessionStatusLabel(_value(session['status'])),
          tone: _Tone.live,
        ),
      ],
    ),
  );
}

List<Widget> _liveBanners(
  Map<String, Object?>? live, {
  Set<String> processes = const {..._scheduledProcesses},
}) => [
  for (final process in _asMaps(live?['processes']))
    if (processes.contains(_value(process['process'])) &&
        _asMap(process['running']) != null)
      _LiveSessionBanner(
        process: _value(process['process']),
        session: _asMap(process['running'])!,
      ),
];

Map<String, Object?>? _liveProcess(
  Map<String, Object?>? live,
  String process,
) => _asMaps(
  live?['processes'],
).where((entry) => _value(entry['process']) == process).firstOrNull;

class _RunTicks extends StatelessWidget {
  const _RunTicks({required this.hourly});

  final List<Map<String, Object?>> hourly;

  @override
  Widget build(BuildContext context) {
    if (hourly.isEmpty) return const SizedBox(height: 16);
    return Tooltip(
      message:
          'Runs per uur, laatste 24 uur (groen = met resultaat, rood = fout)',
      child: SizedBox(
        height: 16,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            for (final bucket in hourly)
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: .8),
                  child: Builder(
                    builder: (context) {
                      final total = (bucket['total'] as num?)?.toInt() ?? 0;
                      final noOps = (bucket['noOps'] as num?)?.toInt() ?? 0;
                      final failed = (bucket['failed'] as num?)?.toInt() ?? 0;
                      final meaningful = total - noOps - failed;
                      final color = failed > 0
                          ? const Color(0xffb2432f)
                          : meaningful > 0
                          ? ProductFactoryColors.primary
                          : total > 0
                          ? const Color(0xffcfe3d9)
                          : const Color(0xffe8ede9);
                      return Container(
                        height: failed > 0 || meaningful > 0 ? 16 : 6,
                        decoration: BoxDecoration(
                          color: color,
                          borderRadius: BorderRadius.circular(1),
                        ),
                      );
                    },
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

String _scheduleRhythm(Map<String, Object?> process) {
  if (process['enabled'] != true) return 'Schema uit';
  final interval = (process['intervalMinutes'] as num?)?.toInt();
  return interval == null ? 'Weekschema' : 'Elke $interval min';
}

String _lastRunLine(Map<String, Object?> process) {
  final last = _asMap(process['lastSession']);
  if (last == null) return 'Nog geen run';
  final when = _shortInstant(last['startedAt']);
  if (_activeProcessStatuses.contains(last['status'])) return '$when · bezig';
  if (_isNoOpSession(last)) return '$when · niets te doen';
  if (_isFailedSession(last)) {
    return '$when · ${_sessionStatusLabel(_value(last['status'])).toLowerCase()}';
  }
  return '$when · met resultaat';
}

String _last24hLine(Map<String, Object?> process) {
  final counts = _asMap(process['last24h']) ?? const {};
  final meaningful = (counts['meaningful'] as num?)?.toInt() ?? 0;
  final failed = (counts['failed'] as num?)?.toInt() ?? 0;
  final noOps = (counts['noOps'] as num?)?.toInt() ?? 0;
  return '$meaningful met resultaat · $noOps niets te doen · $failed fout';
}

class _AutomationStrip extends StatelessWidget {
  const _AutomationStrip({required this.live});

  final Map<String, Object?> live;

  @override
  Widget build(BuildContext context) {
    final processes = _asMaps(live['processes']);
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth >= 900
            ? 4
            : constraints.maxWidth >= 480
            ? 2
            : 1;
        final width = (constraints.maxWidth - (columns - 1) * 10) / columns;
        return Wrap(
          spacing: 10,
          runSpacing: 10,
          children: [
            for (final process in processes)
              Container(
                width: width,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: ProductFactoryColors.outline),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            _processLabel(_value(process['process'])),
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                        ),
                        _ToneChip(
                          _scheduleRhythm(process),
                          tone: process['enabled'] == true
                              ? _Tone.ok
                              : _Tone.neutral,
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _lastRunLine(process),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 6),
                    _RunTicks(hourly: _asMaps(process['hourly'])),
                    const SizedBox(height: 6),
                    Text(
                      _last24hLine(process),
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: ProductFactoryColors.muted,
                      ),
                    ),
                  ],
                ),
              ),
          ],
        );
      },
    );
  }
}

/// Uitklapbare, begrensde historie. De inhoud wordt pas opgebouwd als hij open is.
class _CollapsibleHistory extends StatefulWidget {
  const _CollapsibleHistory({
    required this.title,
    required this.subtitle,
    required this.childrenBuilder,
  });

  final String title;
  final String subtitle;
  final List<Widget> Function() childrenBuilder;

  @override
  State<_CollapsibleHistory> createState() => _CollapsibleHistoryState();
}

class _CollapsibleHistoryState extends State<_CollapsibleHistory> {
  bool _open = false;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const Divider(height: 20),
      InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: () => setState(() => _open = !_open),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            children: [
              Expanded(
                child: Text.rich(
                  TextSpan(
                    children: [
                      TextSpan(
                        text: widget.title,
                        style: const TextStyle(
                          color: ProductFactoryColors.primary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      if (widget.subtitle.isNotEmpty)
                        TextSpan(
                          text: ' · ${widget.subtitle}',
                          style: const TextStyle(
                            color: ProductFactoryColors.muted,
                          ),
                        ),
                    ],
                  ),
                ),
              ),
              Text(
                _open ? 'Verberg' : 'Toon',
                style: const TextStyle(color: ProductFactoryColors.primary),
              ),
              Icon(
                _open ? Icons.expand_less : Icons.expand_more,
                color: ProductFactoryColors.primary,
              ),
            ],
          ),
        ),
      ),
      if (_open) ...widget.childrenBuilder(),
    ],
  );
}

class _SessionRow extends StatelessWidget {
  const _SessionRow({required this.session, this.process});

  final Map<String, Object?> session;
  final String? process;

  @override
  Widget build(BuildContext context) {
    final summary = _value(
      session['resultSummary'] ?? session['blockedReason'],
    );
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 104,
            child: Text(
              _shortInstant(session['startedAt']),
              style: const TextStyle(
                color: ProductFactoryColors.muted,
                fontSize: 12.5,
                fontFeatures: [FontFeature.tabularFigures()],
              ),
            ),
          ),
          if (process != null)
            SizedBox(
              width: 82,
              child: Text(
                _processLabel(process!),
                style: const TextStyle(fontSize: 13),
              ),
            ),
          SizedBox(
            width: 104,
            child: Align(
              alignment: Alignment.centerLeft,
              child: _ToneChip(
                _isNoOpSession(session)
                    ? 'Niets te doen'
                    : _sessionStatusLabel(_value(session['status'])),
                tone: _sessionTone(session),
              ),
            ),
          ),
          Expanded(
            child: SelectableText(
              summary.isEmpty ? 'Geen resultaatsamenvatting.' : summary,
              maxLines: 3,
              style: const TextStyle(fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }
}

class _EpicDetailDialog extends StatefulWidget {
  const _EpicDetailDialog({
    required this.epic,
    required this.gateway,
    required this.contentBuilder,
    this.initialProgress,
  });

  final Map<String, Object?> epic;
  final ProductGateway gateway;
  final Map<String, Object?>? initialProgress;
  final List<Widget> Function(BuildContext dialogContext) contentBuilder;

  @override
  State<_EpicDetailDialog> createState() => _EpicDetailDialogState();
}

class _EpicDetailDialogState extends State<_EpicDetailDialog> {
  Map<String, Object?>? _progress;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _progress = widget.initialProgress;
    unawaited(_load());
  }

  Future<void> _load() async {
    try {
      final progress = await widget.gateway.epicProgress(
        _value(widget.epic['id']),
      );
      if (mounted) {
        setState(() {
          _progress = progress ?? _progress;
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final epic = widget.epic;
    final progress = _progress;
    final waiting = _asMap(progress?['waitingOn']);
    final timeline = _asMaps(progress?['timeline']);
    final stories = _asMaps(progress?['stories']);
    final size = MediaQuery.sizeOf(context);
    return Dialog(
      insetPadding: const EdgeInsets.all(24),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 980,
          maxHeight: size.height * .92,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 18, 12, 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const _Eyebrow('Epic'),
                        SelectableText(
                          _value(epic['title']),
                          style: Theme.of(context).textTheme.titleLarge
                              ?.copyWith(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 6),
                        Wrap(
                          spacing: 8,
                          children: [
                            _ToneChip(
                              _epicStatusLabel(_value(epic['status'])),
                              tone: _epicTone(_value(epic['status'])),
                            ),
                            _ToneChip('versie ${epic['version']}'),
                          ],
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: 'Epic sluiten',
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            Flexible(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(24, 16, 24, 28),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (_loading && progress == null)
                      const LinearProgressIndicator(),
                    if (progress != null) ...[
                      _JourneyView(steps: _asMaps(progress['steps'])),
                      if (waiting != null) _WaitingCallout(waitingOn: waiting),
                      const SizedBox(height: 18),
                      _EpicTimelineAndStories(
                        timeline: timeline,
                        stories: stories,
                        cancelledStoryCount:
                            (progress['cancelledStoryCount'] as num?)
                                ?.toInt() ??
                            0,
                      ),
                      const Divider(height: 36),
                    ],
                    const _Eyebrow('Inhoud, bronnen en acties'),
                    ...widget.contentBuilder(context),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EpicTimelineAndStories extends StatelessWidget {
  const _EpicTimelineAndStories({
    required this.timeline,
    required this.stories,
    required this.cancelledStoryCount,
  });

  final List<Map<String, Object?>> timeline;
  final List<Map<String, Object?>> stories;
  final int cancelledStoryCount;

  @override
  Widget build(BuildContext context) {
    final timelineView = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _Eyebrow('Tijdlijn'),
        if (timeline.isEmpty)
          const SelectableText('Nog geen gebeurtenissen vastgelegd.')
        else
          for (var i = 0; i < timeline.length; i++)
            _TimelineEntry(event: timeline[i], last: i == timeline.length - 1),
      ],
    );
    final storiesView = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Eyebrow(
          'Stories',
          trailing: cancelledStoryCount > 0
              ? Text(
                  '$cancelledStoryCount geannuleerd',
                  style: const TextStyle(
                    fontSize: 12,
                    color: ProductFactoryColors.muted,
                  ),
                )
              : null,
        ),
        if (stories.isEmpty)
          const SelectableText('Nog geen stories gepland.')
        else
          for (final story in stories)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 5),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: 38,
                    child: Text(
                      '#${story['sequenceNumber']}',
                      style: const TextStyle(color: ProductFactoryColors.muted),
                    ),
                  ),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SelectableText(_value(story['title'])),
                        if (_value(story['externalStoryId']).isNotEmpty ||
                            _value(story['deliveredCommitSha']).isNotEmpty)
                          Text(
                            [
                              if (_value(story['externalStoryId']).isNotEmpty)
                                _value(story['externalStoryId']),
                              if (_value(
                                story['deliveredCommitSha'],
                              ).isNotEmpty)
                                _value(story['deliveredCommitSha']).substring(
                                  0,
                                  _value(story['deliveredCommitSha']).length > 7
                                      ? 7
                                      : _value(
                                          story['deliveredCommitSha'],
                                        ).length,
                                ),
                            ].join(' · '),
                            style: const TextStyle(
                              fontSize: 12,
                              color: ProductFactoryColors.muted,
                            ),
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
      ],
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 760) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [timelineView, const SizedBox(height: 20), storiesView],
          );
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(flex: 3, child: timelineView),
            const SizedBox(width: 28),
            Expanded(flex: 2, child: storiesView),
          ],
        );
      },
    );
  }
}

class _TimelineEntry extends StatelessWidget {
  const _TimelineEntry({required this.event, required this.last});

  final Map<String, Object?> event;
  final bool last;

  @override
  Widget build(BuildContext context) {
    final severity = _value(event['severity']);
    final color = switch (severity) {
      'ERROR' => const Color(0xffb2432f),
      'WARNING' => const Color(0xffc9771f),
      'SUCCESS' => ProductFactoryColors.primary,
      _ => const Color(0xff8aa39d),
    };
    final detail = _localizeInstants(_value(event['detail']));
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 92,
            child: Padding(
              padding: const EdgeInsets.only(top: 1),
              child: Text(
                _shortInstant(event['at'], fallback: ''),
                textAlign: TextAlign.right,
                style: const TextStyle(
                  fontSize: 12,
                  color: ProductFactoryColors.muted,
                  fontFeatures: [FontFeature.tabularFigures()],
                ),
              ),
            ),
          ),
          SizedBox(
            width: 28,
            child: Column(
              children: [
                const SizedBox(height: 4),
                Container(
                  width: 11,
                  height: 11,
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                  ),
                ),
                if (!last)
                  Expanded(
                    child: Container(
                      width: 2,
                      color: ProductFactoryColors.outline,
                    ),
                  ),
              ],
            ),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(bottom: 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText(
                    _value(event['title']),
                    style: TextStyle(
                      fontWeight: severity == 'ERROR' || severity == 'WARNING'
                          ? FontWeight.w700
                          : FontWeight.w500,
                    ),
                  ),
                  if (detail.isNotEmpty)
                    SelectableText(
                      detail,
                      style: const TextStyle(
                        fontSize: 12.5,
                        color: ProductFactoryColors.muted,
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Operatie: sessies van alle processen, server-side begrensd en gefilterd.
class _OperationSessionsPanel extends StatefulWidget {
  const _OperationSessionsPanel({
    super.key,
    required this.gateway,
    required this.productId,
    required this.live,
    this.refreshController,
  });

  final ProductGateway gateway;
  final String productId;
  final Map<String, Object?>? live;
  final PageRefreshController? refreshController;

  @override
  State<_OperationSessionsPanel> createState() =>
      _OperationSessionsPanelState();
}

class _OperationSessionsPanelState extends State<_OperationSessionsPanel> {
  static const _pageSize = 25;
  String? _process;
  bool _showNoOps = false;
  bool _loading = true;
  bool _hasMore = false;
  String? _error;
  List<(String, Map<String, Object?>)> _sessions = const [];

  @override
  void initState() {
    super.initState();
    widget.refreshController?.addListener(_reload);
    unawaited(_load());
  }

  @override
  void didUpdateWidget(covariant _OperationSessionsPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.refreshController != widget.refreshController) {
      oldWidget.refreshController?.removeListener(_reload);
      widget.refreshController?.addListener(_reload);
    }
    if (oldWidget.productId != widget.productId) unawaited(_load());
  }

  @override
  void dispose() {
    widget.refreshController?.removeListener(_reload);
    super.dispose();
  }

  void _reload() => unawaited(_load());

  Future<void> _load({bool more = false}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final before = more && _sessions.isNotEmpty
        ? _value(_sessions.last.$2['startedAt'])
        : null;
    final processes = _process == null ? _scheduledProcesses : [_process!];
    try {
      final pages = await Future.wait(
        processes.map(
          (process) async => (
            process,
            await widget.gateway.processSessions(
              widget.productId,
              process,
              limit: _pageSize,
              before: before,
              excludeNoOps: !_showNoOps,
            ),
          ),
        ),
      );
      final merged =
          [
            for (final (process, sessions) in pages)
              for (final session in sessions) (process, session),
          ]..sort(
            (a, b) =>
                _value(b.$2['startedAt']).compareTo(_value(a.$2['startedAt'])),
          );
      if (!mounted) return;
      setState(() {
        final page = merged.take(_pageSize).toList();
        _sessions = more ? [..._sessions, ...page] : page;
        _hasMore =
            merged.length > _pageSize ||
            pages.any((entry) => entry.$2.length == _pageSize);
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = 'Sessies konden niet worden geladen.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final processes = _asMaps(widget.live?['processes']);
    int sum(String key) => processes.fold(
      0,
      (total, process) =>
          total + ((_asMap(process['last24h'])?[key] as num?)?.toInt() ?? 0),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (processes.isNotEmpty)
          _InsightCard(
            children: [
              const _Eyebrow('Laatste 24 uur'),
              Wrap(
                spacing: 36,
                runSpacing: 12,
                children: [
                  _Figure('Sessies', sum('total')),
                  _Figure('Met resultaat', sum('meaningful')),
                  _Figure('Niets te doen', sum('noOps'), muted: true),
                  _Figure('Fout of geblokkeerd', sum('failed'), critical: true),
                ],
              ),
            ],
          ),
        _InsightCard(
          children: [
            Wrap(
              spacing: 8,
              runSpacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                ChoiceChip(
                  label: const Text('Alle processen'),
                  selected: _process == null,
                  onSelected: (_) {
                    setState(() => _process = null);
                    unawaited(_load());
                  },
                ),
                for (final process in _scheduledProcesses)
                  ChoiceChip(
                    label: Text(_processLabel(process)),
                    selected: _process == process,
                    onSelected: (_) {
                      setState(() => _process = process);
                      unawaited(_load());
                    },
                  ),
                const SizedBox(width: 12),
                FilterChip(
                  label: const Text('Toon “niets te doen”'),
                  selected: _showNoOps,
                  onSelected: (value) {
                    setState(() => _showNoOps = value);
                    unawaited(_load());
                  },
                ),
              ],
            ),
            const SizedBox(height: 10),
            if (_error != null) SelectableText(_error!),
            if (_loading && _sessions.isEmpty) const LinearProgressIndicator(),
            if (!_loading && _sessions.isEmpty && _error == null)
              const SelectableText('Geen sessies voor dit filter.'),
            for (final (process, session) in _sessions)
              _SessionRow(session: session, process: process),
            if (_hasMore)
              Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton(
                  onPressed: _loading ? null : () => _load(more: true),
                  child: const Text('Oudere laden'),
                ),
              ),
          ],
        ),
      ],
    );
  }
}

class _Figure extends StatelessWidget {
  const _Figure(
    this.label,
    this.value, {
    this.muted = false,
    this.critical = false,
  });

  final String label;
  final int value;
  final bool muted;
  final bool critical;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        '$value',
        style: TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.w800,
          color: critical && value > 0
              ? const Color(0xffb2432f)
              : muted
              ? ProductFactoryColors.muted
              : ProductFactoryColors.ink,
        ),
      ),
      Text(
        label,
        style: const TextStyle(fontSize: 12, color: ProductFactoryColors.muted),
      ),
    ],
  );
}
