import 'package:flutter/material.dart';

import 'api.dart';
import 'formatting.dart';

class MemoryHistoryDialog extends StatefulWidget {
  const MemoryHistoryDialog({
    required this.api,
    required this.productSlug,
    required this.productName,
    this.today,
    super.key,
  });

  final DashboardApi api;
  final String productSlug;
  final String productName;
  final DateTime? today;

  @override
  State<MemoryHistoryDialog> createState() => _MemoryHistoryDialogState();
}

class _MemoryHistoryDialogState extends State<MemoryHistoryDialog> {
  DateTime? _selectedDate;
  late Future<List<dynamic>> _snapshot;
  late Future<List<dynamic>> _history;

  @override
  void initState() {
    super.initState();
    _snapshot = widget.api.memory(widget.productSlug);
    _history = widget.api.memoryHistory(widget.productSlug);
  }

  String get _selectedDateValue {
    final date = _selectedDate!;
    String pad(int value) => value.toString().padLeft(2, '0');
    return '${date.year}-${pad(date.month)}-${pad(date.day)}';
  }

  void _showCurrent() {
    setState(() {
      _selectedDate = null;
      _snapshot = widget.api.memory(widget.productSlug);
    });
  }

  Future<void> _chooseDate() async {
    final now = widget.today ?? DateTime.now();
    final chosen = await showDatePicker(
      context: context,
      initialDate: _selectedDate ?? now,
      firstDate: DateTime(2000),
      lastDate: now,
      helpText: 'Kies een historische peildatum',
    );
    if (chosen == null || !mounted) return;
    setState(() {
      _selectedDate = chosen;
      _snapshot = widget.api.memory(
        widget.productSlug,
        asOf: _selectedDateValue,
      );
    });
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text('Geheugen · ${widget.productName}'),
    content: SizedBox(
      width: 880,
      height: 680,
      child: ListView(
        children: [
          const Text(
            'Normale agents krijgen alleen het actuele geheugen. Kies bewust een peildatum om '
            'te reconstrueren welke informatie toen actief was.',
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              FilledButton.tonalIcon(
                key: const Key('memory-date-picker'),
                onPressed: _chooseDate,
                icon: const Icon(Icons.event_outlined),
                label: Text(
                  _selectedDate == null
                      ? 'Kies peildatum'
                      : 'Peildatum: $_selectedDateValue',
                ),
              ),
              if (_selectedDate != null)
                TextButton.icon(
                  onPressed: _showCurrent,
                  icon: const Icon(Icons.update),
                  label: const Text('Toon actueel'),
                ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            _selectedDate == null
                ? 'Actief geheugen'
                : 'Historische momentopname · $_selectedDateValue',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          if (_selectedDate != null)
            const Padding(
              padding: EdgeInsets.only(top: 4),
              child: Text(
                'Historische inhoud is niet bindend voor huidige agenttaken.',
                style: TextStyle(fontStyle: FontStyle.italic),
              ),
            ),
          const SizedBox(height: 8),
          FutureBuilder<List<dynamic>>(
            future: _snapshot,
            builder: (context, snapshot) {
              if (!snapshot.hasData) {
                if (snapshot.hasError) {
                  return Text('Momentopname kon niet laden: ${snapshot.error}');
                }
                return const LinearProgressIndicator();
              }
              final items = snapshot.data!.cast<Map<String, dynamic>>();
              if (items.isEmpty) {
                return const ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.inbox_outlined),
                  title: Text('Geen actief geheugen op deze peildatum'),
                );
              }
              return Column(
                children: items
                    .map(
                      (item) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.memory_outlined),
                        title: Text('${item['title']}'),
                        subtitle: SelectableText('${item['content']}'),
                      ),
                    )
                    .toList(growable: false),
              );
            },
          ),
          const Divider(height: 32),
          Text(
            'Volledige versiegeschiedenis',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          FutureBuilder<List<dynamic>>(
            future: _history,
            builder: (context, snapshot) {
              if (!snapshot.hasData) {
                if (snapshot.hasError) {
                  return Text(
                    'Versiegeschiedenis kon niet laden: ${snapshot.error}',
                  );
                }
                return const LinearProgressIndicator();
              }
              final versions = snapshot.data!.cast<Map<String, dynamic>>();
              if (versions.isEmpty) {
                return const Text('Er zijn nog geen geheugenitems.');
              }
              return Column(
                children: versions
                    .map((version) => MemoryVersionTile(version: version))
                    .toList(growable: false),
              );
            },
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Sluiten'),
      ),
    ],
  );
}

class MemoryVersionTile extends StatelessWidget {
  const MemoryVersionTile({required this.version, super.key});

  final Map<String, dynamic> version;

  @override
  Widget build(BuildContext context) {
    final status = '${version['status']}';
    final colorScheme = Theme.of(context).colorScheme;
    final statusColor = switch (status) {
      'ACTIVE' => colorScheme.primary,
      'RETRACTED' => colorScheme.error,
      _ => colorScheme.outline,
    };
    final retirementReason = '${version['retirementReason'] ?? ''}'.trim();
    final changeReason = '${version['changeReason'] ?? ''}'.trim();
    return Card(
      child: ExpansionTile(
        leading: Icon(
          status == 'ACTIVE'
              ? Icons.check_circle_outline
              : status == 'RETRACTED'
              ? Icons.cancel_outlined
              : Icons.swap_horiz,
          color: statusColor,
        ),
        title: Text('${version['title']} · versie ${version['versionNumber']}'),
        subtitle: Text(
          '$status · vanaf ${formatDateTime(version['createdAt'])}'
          '${version['effectiveUntil'] == null ? '' : ' tot ${formatDateTime(version['effectiveUntil'])}'}',
        ),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        expandedCrossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText('${version['content']}'),
          const SizedBox(height: 8),
          Text('Vastgelegd door ${version['createdBy']}'),
          if (changeReason.isNotEmpty) Text('Wijzigingsreden: $changeReason'),
          if (retirementReason.isNotEmpty)
            Text('Reden einde geldigheid: $retirementReason'),
          if (version['retiredBy'] != null)
            Text('Beëindigd door ${version['retiredBy']}'),
          Text(
            'Lijn ${version['rootMemoryId']} · item ${version['id']}',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}
