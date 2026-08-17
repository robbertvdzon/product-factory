import 'package:flutter/material.dart';

import 'api.dart';

enum DashboardSection {
  overview('Overzicht', Icons.dashboard_outlined),
  roadmap('Roadmap', Icons.map_outlined),
  productSessions('Productsessies', Icons.loop),
  stories('Stories', Icons.view_kanban_outlined),
  epics('Epics', Icons.account_tree_outlined),
  bugs('Bugs', Icons.bug_report_outlined),
  testSessions('Testsessies', Icons.fact_check_outlined),
  meetings('Overleggen', Icons.forum_outlined);

  const DashboardSection(this.label, this.icon);
  final String label;
  final IconData icon;
}

const List<DashboardSection> mobileDashboardSections = [
  DashboardSection.overview,
  DashboardSection.productSessions,
  DashboardSection.stories,
  DashboardSection.roadmap,
  DashboardSection.bugs,
  DashboardSection.epics,
  DashboardSection.testSessions,
  DashboardSection.meetings,
];

String mobileDashboardSectionLabel(DashboardSection section) =>
    switch (section) {
      DashboardSection.productSessions => 'Productcycli',
      _ => section.label,
    };

class DashboardSectionNavigation extends StatelessWidget {
  const DashboardSectionNavigation({
    required this.value,
    required this.onChanged,
    super.key,
  });

  final DashboardSection value;
  final ValueChanged<DashboardSection> onChanged;

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    label: 'Productsecties',
    child: SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: SegmentedButton<DashboardSection>(
        segments: [
          for (final section in DashboardSection.values)
            ButtonSegment(
              value: section,
              icon: Icon(section.icon),
              label: Text(section.label),
            ),
        ],
        selected: {value},
        showSelectedIcon: false,
        onSelectionChanged: (selection) => onChanged(selection.single),
      ),
    ),
  );
}

/// Native sectiekeuze voor viewports waarop de horizontale segmenten niet
/// zonder horizontaal scrollen passen. De expliciete volgorde staat los van de
/// enumvolgorde, zodat het bestaande desktopgedrag ongewijzigd blijft.
class MobileDashboardSectionNavigation extends StatefulWidget {
  const MobileDashboardSectionNavigation({
    required this.value,
    required this.onChanged,
    super.key,
  });

  final DashboardSection value;
  final ValueChanged<DashboardSection> onChanged;

  @override
  State<MobileDashboardSectionNavigation> createState() =>
      _MobileDashboardSectionNavigationState();
}

class _MobileDashboardSectionNavigationState
    extends State<MobileDashboardSectionNavigation> {
  final FocusNode _focusNode = FocusNode(debugLabel: 'mobiele-sectiekeuze');

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    label: 'Sectie kiezen',
    value: mobileDashboardSectionLabel(widget.value),
    child: DropdownButtonFormField<DashboardSection>(
      focusNode: _focusNode,
      initialValue: widget.value,
      isExpanded: true,
      decoration: InputDecoration(
        labelText: 'Sectie kiezen',
        border: const OutlineInputBorder(),
        focusedBorder: OutlineInputBorder(
          borderSide: BorderSide(
            color: Theme.of(context).colorScheme.primary,
            width: 3,
          ),
        ),
      ),
      items: [
        for (final section in mobileDashboardSections)
          DropdownMenuItem(
            value: section,
            child: Text(mobileDashboardSectionLabel(section)),
          ),
      ],
      onChanged: (section) {
        if (section == null) return;
        widget.onChanged(section);
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _focusNode.requestFocus();
        });
      },
    ),
  );
}

List<Map<String, dynamic>> bugsForProduct(
  List<dynamic> bugs,
  String productSlug,
) => bugs
    .whereType<Map<String, dynamic>>()
    .where((bug) => bug['productSlug'] == productSlug)
    .toList(growable: false);

List<Map<String, dynamic>> testSessionsForProduct(
  List<dynamic> sessions,
  String productSlug,
) => sessions
    .whereType<Map<String, dynamic>>()
    .where((session) => session['productSlug'] == productSlug)
    .toList(growable: false);

bool isActiveBug(Map<String, dynamic> bug) => const {
  'OPEN',
  'IN_PROGRESS',
  'READY_FOR_VERIFICATION',
}.contains(bug['status']);

class ImportantBugSummary extends StatelessWidget {
  const ImportantBugSummary({required this.bugs, super.key});
  final List<Map<String, dynamic>> bugs;

  @override
  Widget build(BuildContext context) {
    final important = bugs
        .where(
          (bug) =>
              isActiveBug(bug) && const {'P0', 'P1'}.contains(bug['priority']),
        )
        .toList();
    if (important.isEmpty) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.check_circle_outline),
          title: Text('Geen belangrijke open bugs'),
          subtitle: Text(
            'Nieuwe functionaliteit blokkeert momenteel niet op P0/P1-bugs.',
          ),
        ),
      );
    }
    return Card(
      color: const Color(0xFFFFF2F2),
      child: ListTile(
        leading: const Icon(Icons.priority_high, color: Color(0xFF781D24)),
        title: Text(
          '${important.length} belangrijke bug${important.length == 1 ? '' : 's'} blokkeren nieuwe functionaliteit',
        ),
        subtitle: Text(
          important
              .take(3)
              .map((bug) => '${bug['priority']} · ${bug['title']}')
              .join('\n'),
        ),
      ),
    );
  }
}

class BugList extends StatelessWidget {
  const BugList({
    required this.bugs,
    required this.api,
    required this.onChanged,
    super.key,
  });

  final List<Map<String, dynamic>> bugs;
  final DashboardApi api;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    if (bugs.isEmpty) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.bug_report_outlined),
          title: Text('Nog geen bugs geregistreerd'),
          subtitle: Text(
            'Roadmap- en testsessies vullen deze lijst automatisch aan.',
          ),
        ),
      );
    }
    return Column(
      children: [
        for (final bug in bugs)
          BugCard(bug: bug, api: api, onChanged: onChanged),
      ],
    );
  }
}

class BugCard extends StatefulWidget {
  const BugCard({
    required this.bug,
    required this.api,
    required this.onChanged,
    super.key,
  });
  final Map<String, dynamic> bug;
  final DashboardApi api;
  final VoidCallback onChanged;

  @override
  State<BugCard> createState() => _BugCardState();
}

class _BugCardState extends State<BugCard> {
  bool saving = false;

  Future<void> _update({String? priority, String? status}) async {
    setState(() => saving = true);
    try {
      await widget.api.updateBug(
        '${widget.bug['productSlug']}',
        widget.bug['id'] as int,
        priority: priority,
        status: status,
      );
      widget.onChanged();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bug = widget.bug;
    return Card(
      child: ExpansionTile(
        leading: CircleAvatar(child: Text('${bug['priority']}')),
        title: Text('BUG-${bug['id']} · ${bug['title']}'),
        subtitle: Text(
          '${_statusLabel('${bug['status']}')} · ${bug['occurrenceCount']}× waargenomen',
        ),
        trailing: saving
            ? const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : null,
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: SelectableText('${bug['description']}'),
          ),
          const SizedBox(height: 12),
          _BugField(
            label: 'Stappen om te reproduceren',
            value: '${bug['reproductionSteps']}',
          ),
          _BugField(label: 'Verwacht', value: '${bug['expectedResult']}'),
          _BugField(label: 'Werkelijk', value: '${bug['actualResult']}'),
          const SizedBox(height: 12),
          Wrap(
            spacing: 12,
            runSpacing: 8,
            children: [
              DropdownButton<String>(
                value: '${bug['priority']}',
                items: const [
                  DropdownMenuItem(value: 'P0', child: Text('P0 · Blokkerend')),
                  DropdownMenuItem(value: 'P1', child: Text('P1 · Hoog')),
                  DropdownMenuItem(value: 'P2', child: Text('P2 · Normaal')),
                  DropdownMenuItem(value: 'P3', child: Text('P3 · Laag')),
                ],
                onChanged: saving
                    ? null
                    : (value) =>
                          value == null ? null : _update(priority: value),
              ),
              DropdownButton<String>(
                value: '${bug['status']}',
                items: const [
                  DropdownMenuItem(value: 'OPEN', child: Text('Open')),
                  DropdownMenuItem(
                    value: 'IN_PROGRESS',
                    child: Text('In uitvoering'),
                  ),
                  DropdownMenuItem(
                    value: 'READY_FOR_VERIFICATION',
                    child: Text('Te verifiëren'),
                  ),
                  DropdownMenuItem(value: 'RESOLVED', child: Text('Opgelost')),
                  DropdownMenuItem(
                    value: 'OBSOLETE',
                    child: Text('Niet meer van toepassing'),
                  ),
                ],
                onChanged: saving
                    ? null
                    : (value) => value == null ? null : _update(status: value),
              ),
              if (bug['linkedCandidateId'] != null)
                Chip(label: Text('Story ${bug['linkedCandidateId']}')),
            ],
          ),
        ],
      ),
    );
  }
}

class _BugField extends StatelessWidget {
  const _BugField({required this.label, required this.value});
  final String label;
  final String value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 8),
    child: Align(
      alignment: Alignment.centerLeft,
      child: Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: '$label: ',
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            TextSpan(text: value),
          ],
        ),
      ),
    ),
  );
}

String _statusLabel(String status) => switch (status) {
  'OPEN' => 'Open',
  'IN_PROGRESS' => 'In uitvoering',
  'READY_FOR_VERIFICATION' => 'Te verifiëren',
  'RESOLVED' => 'Opgelost',
  'OBSOLETE' => 'Niet meer van toepassing',
  _ => status,
};

class TestSessionList extends StatelessWidget {
  const TestSessionList({
    required this.sessions,
    required this.onOpenReport,
    super.key,
  });
  final List<Map<String, dynamic>> sessions;
  final void Function(String productSlug, String runId) onOpenReport;

  @override
  Widget build(BuildContext context) {
    if (sessions.isEmpty) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.fact_check_outlined),
          title: Text('Nog geen testsessies'),
        ),
      );
    }
    return Column(
      children: [
        for (final session in sessions)
          Card(
            child: ListTile(
              leading: const Icon(Icons.fact_check_outlined),
              title: Text('Testsessie ${session['sequenceNumber']}'),
              subtitle: Text(
                [
                  '${session['status']}',
                  '${session['testedAreas'] ?? 0} onderdelen getest',
                  '${session['bugsCreated'] ?? 0} nieuw',
                  '${session['bugsUpdated'] ?? 0} bijgewerkt',
                  '${session['bugsResolved'] ?? 0} gesloten',
                  if ('${session['summary'] ?? ''}'.trim().isNotEmpty)
                    '${session['summary']}',
                ].join(' · '),
              ),
              trailing: session['workspaceRunId'] == null
                  ? null
                  : IconButton(
                      tooltip: 'Testrapport bekijken',
                      icon: const Icon(Icons.description_outlined),
                      onPressed: () => onOpenReport(
                        '${session['productSlug']}',
                        '${session['workspaceRunId']}',
                      ),
                    ),
            ),
          ),
      ],
    );
  }
}
