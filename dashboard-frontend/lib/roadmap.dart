import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'api.dart';

class RoadmapEpic {
  const RoadmapEpic({
    required this.id,
    required this.productSlug,
    required this.title,
    required this.description,
    required this.status,
    required this.customerRank,
    required this.processRank,
    required this.priorityScore,
    required this.roadmapRank,
    required this.dependencyIds,
    required this.blockedByIds,
  });

  factory RoadmapEpic.fromJson(Map<String, dynamic> json) => RoadmapEpic(
    id: '${json['id']}',
    productSlug: '${json['productSlug']}',
    title: '${json['title']}',
    description: '${json['description']}',
    status: '${json['status']}',
    customerRank: json['customerRank'] as int? ?? 1,
    processRank: json['processRank'] as int? ?? 1,
    priorityScore: json['priorityScore'] as int? ?? 0,
    roadmapRank: json['roadmapRank'] as int? ?? 1,
    dependencyIds: (json['dependencyIds'] as List<dynamic>? ?? const [])
        .map((value) => '$value')
        .toList(),
    blockedByIds: (json['blockedByIds'] as List<dynamic>? ?? const [])
        .map((value) => '$value')
        .toList(),
  );

  final String id;
  final String productSlug;
  final String title;
  final String description;
  final String status;
  final int customerRank;
  final int processRank;
  final int priorityScore;
  final int roadmapRank;
  final List<String> dependencyIds;
  final List<String> blockedByIds;
}

class RoadmapBoard extends StatelessWidget {
  const RoadmapBoard({
    required this.products,
    required this.epics,
    required this.stories,
    required this.deliveries,
    required this.api,
    required this.onChanged,
    super.key,
  });

  final List<Map<String, dynamic>> products;
  final List<Map<String, dynamic>> epics;
  final List<dynamic> stories;
  final List<dynamic> deliveries;
  final DashboardApi api;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    if (products.isEmpty) {
      return const ListTile(title: Text('Maak eerst een product aan.'));
    }
    return Column(
      children: products.map((product) {
        final slug = '${product['slug']}';
        final productEpics =
            epics
                .where((epic) => epic['productSlug'] == slug)
                .map(RoadmapEpic.fromJson)
                .toList()
              ..sort(
                (left, right) => left.roadmapRank.compareTo(right.roadmapRank),
              );
        return Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Card.outlined(
            clipBehavior: Clip.antiAlias,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 14, 12, 10),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Roadmap · ${product['name']}',
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                            Text(
                              'Klant 75% · proces 25% · dependencies zijn leidend',
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ],
                        ),
                      ),
                      TextButton.icon(
                        key: ValueKey('create-epic-$slug'),
                        onPressed: () => _createEpic(context, slug),
                        icon: const Icon(Icons.add),
                        label: const Text('Epic'),
                      ),
                    ],
                  ),
                ),
                if (productEpics.isEmpty)
                  const Padding(
                    padding: EdgeInsets.fromLTRB(20, 8, 20, 24),
                    child: Text('Nog geen epics op deze roadmap.'),
                  )
                else
                  _EpicGraph(
                    epics: productEpics,
                    onTap: (epic) => _editEpic(
                      context,
                      epic,
                      productEpics,
                      stories,
                      deliveries,
                    ),
                  ),
              ],
            ),
          ),
        );
      }).toList(),
    );
  }

  Future<void> _createEpic(BuildContext context, String slug) async {
    final changed = await showDialog<bool>(
      context: context,
      builder: (_) => _CreateEpicDialog(api: api, productSlug: slug),
    );
    if (changed == true) onChanged();
  }

  Future<void> _editEpic(
    BuildContext context,
    RoadmapEpic epic,
    List<RoadmapEpic> productEpics,
    List<dynamic> stories,
    List<dynamic> deliveries,
  ) async {
    final changed = await showDialog<bool>(
      context: context,
      builder: (_) => _EpicDetailDialog(
        api: api,
        epic: epic,
        productEpics: productEpics,
        stories: stories,
        deliveries: deliveries,
      ),
    );
    if (changed == true) onChanged();
  }
}

class _EpicGraph extends StatelessWidget {
  const _EpicGraph({required this.epics, required this.onTap});

  static const cardWidth = 226.0;
  static const cardHeight = 198.0;
  static const step = 266.0;
  static const graphHeight = 276.0;

  final List<RoadmapEpic> epics;
  final ValueChanged<RoadmapEpic> onTap;

  @override
  Widget build(BuildContext context) {
    final width = math.max(620.0, 28 + epics.length * step);
    return Scrollbar(
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: SizedBox(
          width: width,
          height: graphHeight,
          child: Stack(
            children: [
              Positioned.fill(
                child: CustomPaint(painter: _DependencyPainter(epics: epics)),
              ),
              for (var index = 0; index < epics.length; index++)
                Positioned(
                  left: 20 + index * step,
                  top: index.isEven ? 14 : 52,
                  width: cardWidth,
                  height: cardHeight,
                  child: _EpicCard(epic: epics[index], onTap: onTap),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EpicCard extends StatelessWidget {
  const _EpicCard({required this.epic, required this.onTap});

  final RoadmapEpic epic;
  final ValueChanged<RoadmapEpic> onTap;

  @override
  Widget build(BuildContext context) {
    final blocked = epic.blockedByIds.isNotEmpty;
    final done = epic.status == 'DONE';
    final color = done
        ? Colors.green
        : blocked
        ? Colors.orange
        : Theme.of(context).colorScheme.primary;
    return Card(
      key: ValueKey('epic-card-${epic.id}'),
      elevation: 3,
      color: Color.alphaBlend(color.withValues(alpha: 0.08), Colors.white),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: color.withValues(alpha: 0.5), width: 1.5),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () => onTap(epic),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    radius: 17,
                    backgroundColor: color,
                    foregroundColor: Colors.white,
                    child: Text('#${epic.roadmapRank}'),
                  ),
                  const Spacer(),
                  _ScoreBadge(score: epic.priorityScore),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                epic.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const Spacer(),
              Text(
                'Klant #${epic.customerRank}  ·  Proces #${epic.processRank}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 5),
              Row(
                children: [
                  Icon(
                    done
                        ? Icons.check_circle_outline
                        : blocked
                        ? Icons.lock_clock_outlined
                        : Icons.play_circle_outline,
                    size: 16,
                    color: color,
                  ),
                  const SizedBox(width: 5),
                  Expanded(
                    child: Text(
                      done
                          ? 'Afgerond'
                          : blocked
                          ? 'Wacht op ${epic.blockedByIds.length} epic(s)'
                          : 'Uitvoerbaar',
                      style: Theme.of(context).textTheme.labelSmall,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ScoreBadge extends StatelessWidget {
  const _ScoreBadge({required this.score});

  final int score;

  @override
  Widget build(BuildContext context) => Tooltip(
    message: 'Prioriteitsscore: 75% klant-rank en 25% process-rank',
    child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text('$score', style: Theme.of(context).textTheme.labelLarge),
    ),
  );
}

class _DependencyPainter extends CustomPainter {
  const _DependencyPainter({required this.epics});

  final List<RoadmapEpic> epics;

  @override
  void paint(Canvas canvas, Size size) {
    final indexById = <String, int>{
      for (var index = 0; index < epics.length; index++) epics[index].id: index,
    };
    final paint = Paint()
      ..color = const Color(0xff64748b)
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke;
    for (var targetIndex = 0; targetIndex < epics.length; targetIndex++) {
      for (final dependencyId in epics[targetIndex].dependencyIds) {
        final sourceIndex = indexById[dependencyId];
        if (sourceIndex == null) continue;
        final source = Offset(
          20 + sourceIndex * _EpicGraph.step + _EpicGraph.cardWidth,
          (sourceIndex.isEven ? 14 : 52) + _EpicGraph.cardHeight / 2,
        );
        final target = Offset(
          20 + targetIndex * _EpicGraph.step,
          (targetIndex.isEven ? 14 : 52) + _EpicGraph.cardHeight / 2,
        );
        final control = math.max(18.0, (target.dx - source.dx) * 0.48);
        final path = Path()
          ..moveTo(source.dx, source.dy)
          ..cubicTo(
            source.dx + control,
            source.dy,
            target.dx - control,
            target.dy,
            target.dx - 7,
            target.dy,
          );
        canvas.drawPath(path, paint);
        canvas.drawLine(
          Offset(target.dx - 14, target.dy - 6),
          Offset(target.dx - 7, target.dy),
          paint,
        );
        canvas.drawLine(
          Offset(target.dx - 14, target.dy + 6),
          Offset(target.dx - 7, target.dy),
          paint,
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant _DependencyPainter oldDelegate) =>
      oldDelegate.epics != epics;
}

class _CreateEpicDialog extends StatefulWidget {
  const _CreateEpicDialog({required this.api, required this.productSlug});

  final DashboardApi api;
  final String productSlug;

  @override
  State<_CreateEpicDialog> createState() => _CreateEpicDialogState();
}

class _CreateEpicDialogState extends State<_CreateEpicDialog> {
  final title = TextEditingController();
  final description = TextEditingController();
  bool saving = false;
  String? error;

  @override
  void dispose() {
    title.dispose();
    description.dispose();
    super.dispose();
  }

  Future<void> save() async {
    if (title.text.trim().isEmpty || description.text.trim().isEmpty) {
      setState(() => error = 'Titel en beschrijving zijn verplicht.');
      return;
    }
    setState(() {
      saving = true;
      error = null;
    });
    try {
      await widget.api.createRoadmapEpic(
        widget.productSlug,
        title.text.trim(),
        description.text.trim(),
      );
      if (mounted) Navigator.pop(context, true);
    } catch (exception) {
      if (mounted) setState(() => error = '$exception');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Nieuwe epic'),
    content: SizedBox(
      width: 560,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            key: const ValueKey('epic-title'),
            controller: title,
            maxLength: 80,
            autofocus: true,
            decoration: const InputDecoration(
              labelText: 'Korte titel',
              hintText: 'Bijvoorbeeld: Zoeken verbeteren',
            ),
          ),
          TextField(
            key: const ValueKey('epic-description'),
            controller: description,
            minLines: 3,
            maxLines: 7,
            decoration: const InputDecoration(labelText: 'Beschrijving'),
          ),
          if (error != null) ...[
            const SizedBox(height: 12),
            Text(
              error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ],
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: saving ? null : () => Navigator.pop(context),
        child: const Text('Annuleren'),
      ),
      FilledButton(
        onPressed: saving ? null : save,
        child: Text(saving ? 'Opslaan…' : 'Toevoegen'),
      ),
    ],
  );
}

class _EpicDetailDialog extends StatefulWidget {
  const _EpicDetailDialog({
    required this.api,
    required this.epic,
    required this.productEpics,
    required this.stories,
    required this.deliveries,
  });

  final DashboardApi api;
  final RoadmapEpic epic;
  final List<RoadmapEpic> productEpics;
  final List<dynamic> stories;
  final List<dynamic> deliveries;

  @override
  State<_EpicDetailDialog> createState() => _EpicDetailDialogState();
}

class _EpicDetailDialogState extends State<_EpicDetailDialog> {
  late final TextEditingController title;
  late final TextEditingController description;
  late final TextEditingController customerRank;
  late String status;
  late Set<String> dependencyIds;
  bool saving = false;
  String? error;

  @override
  void initState() {
    super.initState();
    title = TextEditingController(text: widget.epic.title);
    description = TextEditingController(text: widget.epic.description);
    customerRank = TextEditingController(text: '${widget.epic.customerRank}');
    status = widget.epic.status;
    dependencyIds = widget.epic.dependencyIds.toSet();
  }

  @override
  void dispose() {
    title.dispose();
    description.dispose();
    customerRank.dispose();
    super.dispose();
  }

  Future<void> save() async {
    final rank = int.tryParse(customerRank.text);
    if (rank == null || rank < 1) {
      setState(() => error = 'Klant-rank moet een positief heel getal zijn.');
      return;
    }
    setState(() {
      saving = true;
      error = null;
    });
    try {
      await widget.api
          .updateRoadmapEpic(widget.epic.productSlug, widget.epic.id, {
            'title': title.text.trim(),
            'description': description.text.trim(),
            'customerRank': rank,
            'dependencyIds': dependencyIds.toList(),
            'status': status,
          });
      if (mounted) Navigator.pop(context, true);
    } catch (exception) {
      if (mounted) setState(() => error = '$exception');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final linkedStories = widget.stories.cast<Map<String, dynamic>>().where(
      (story) => story['themeId'] == widget.epic.id,
    );
    final deliveryByCandidate = <int, Map<String, dynamic>>{
      for (final item in widget.deliveries.cast<Map<String, dynamic>>())
        if (item['candidateId'] is int) item['candidateId'] as int: item,
    };
    return AlertDialog(
      title: const Text('Epicdetails'),
      content: SizedBox(
        width: 760,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  Chip(label: Text('Roadmap #${widget.epic.roadmapRank}')),
                  Chip(label: Text('Score ${widget.epic.priorityScore}')),
                  Chip(label: Text('Proces #${widget.epic.processRank}')),
                  if (widget.epic.blockedByIds.isNotEmpty)
                    Chip(
                      label: Text(
                        'Geblokkeerd door ${widget.epic.blockedByIds.length}',
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 14),
              TextField(
                key: const ValueKey('edit-epic-title'),
                controller: title,
                maxLength: 80,
                decoration: const InputDecoration(labelText: 'Korte titel'),
              ),
              TextField(
                controller: description,
                minLines: 3,
                maxLines: 8,
                decoration: const InputDecoration(labelText: 'Beschrijving'),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      key: const ValueKey('customer-rank'),
                      controller: customerRank,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      decoration: InputDecoration(
                        labelText: 'Jouw klant-rank',
                        helperText:
                            '1 is het belangrijkst; maximaal ${widget.productEpics.length}',
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      initialValue: status,
                      decoration: const InputDecoration(labelText: 'Status'),
                      items: const [
                        DropdownMenuItem(value: 'OPEN', child: Text('Open')),
                        DropdownMenuItem(
                          value: 'IN_PROGRESS',
                          child: Text('Bezig'),
                        ),
                        DropdownMenuItem(
                          value: 'DONE',
                          child: Text('Afgerond'),
                        ),
                      ],
                      onChanged: saving
                          ? null
                          : (value) => setState(() => status = value!),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Text(
                'Afhankelijk van',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              Text(
                'Deze epics moeten eerst klaar zijn. Circulaire afhankelijkheden worden geweigerd.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              for (final candidate in widget.productEpics.where(
                (item) => item.id != widget.epic.id,
              ))
                CheckboxListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: Text('#${candidate.roadmapRank} · ${candidate.title}'),
                  value: dependencyIds.contains(candidate.id),
                  onChanged: saving
                      ? null
                      : (selected) => setState(() {
                          if (selected == true) {
                            dependencyIds.add(candidate.id);
                          } else {
                            dependencyIds.remove(candidate.id);
                          }
                        }),
                ),
              const SizedBox(height: 16),
              Text(
                'Gekoppelde stories (${linkedStories.length})',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              if (linkedStories.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8),
                  child: Text('Nog geen stories aan deze epic gekoppeld.'),
                )
              else
                ...linkedStories.map((story) {
                  final delivery = deliveryByCandidate[story['id']];
                  return ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(
                      delivery?['confirmedDeployed'] == true
                          ? Icons.cloud_done_outlined
                          : Icons.task_alt_outlined,
                    ),
                    title: Text('${story['title']}'),
                    subtitle: Text(
                      '${story['status']}${delivery == null ? '' : ' · ${delivery['status']}'}',
                    ),
                  );
                }),
              const SizedBox(height: 16),
              Text(
                'Opleverchecker-rapporten',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              FutureBuilder<List<dynamic>>(
                future: widget.api.roadmapEpicVerifications(
                  widget.epic.productSlug,
                  widget.epic.id,
                ),
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done) {
                    return const LinearProgressIndicator();
                  }
                  if (snapshot.hasError) {
                    return Text(
                      'Rapporten konden niet worden geladen: ${snapshot.error}',
                    );
                  }
                  final reports = snapshot.data ?? [];
                  if (reports.isEmpty) {
                    return const Text('Nog geen opleverchecker-rapporten.');
                  }
                  return Column(
                    children: reports.map((item) {
                      final report = item as Map<String, dynamic>;
                      return ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        title: Text('${report['candidateTitle']}'),
                        subtitle: Text('${report['report']}'),
                        trailing: Chip(label: Text('${report['verdict']}')),
                      );
                    }).toList(),
                  );
                },
              ),
              if (error != null) ...[
                const SizedBox(height: 12),
                Text(
                  error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: saving ? null : () => Navigator.pop(context),
          child: const Text('Annuleren'),
        ),
        FilledButton(
          onPressed: saving ? null : save,
          child: Text(saving ? 'Opslaan…' : 'Opslaan'),
        ),
      ],
    );
  }
}
