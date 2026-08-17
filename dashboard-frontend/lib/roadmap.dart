import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import 'api.dart';

List<Map<String, dynamic>> _livingMaps(Object? value) => value is List
    ? value
          .whereType<Map>()
          .map((item) => Map<String, dynamic>.from(item))
          .toList()
    : const [];

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
    required this.horizon,
    required this.kind,
    required this.capabilityKey,
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
    horizon: '${json['horizon'] ?? 'UNPLACED'}',
    kind: '${json['kind'] ?? 'DELIVERY'}',
    capabilityKey: json['capabilityKey'] as String?,
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
  final String horizon;
  final String kind;
  final String? capabilityKey;
}

class RoadmapBoard extends StatelessWidget {
  const RoadmapBoard({
    required this.products,
    required this.epics,
    required this.visions,
    required this.stories,
    required this.deliveries,
    required this.api,
    required this.onChanged,
    super.key,
  });

  final List<Map<String, dynamic>> products;
  final List<Map<String, dynamic>> epics;
  final List<Map<String, dynamic>> visions;
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
        final productVision = visions
            .where((vision) => vision['productSlug'] == slug)
            .firstOrNull;
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
                if (product['roadmapProcessVersion'] == 'living-vision-v2') ...[
                  LivingVisionPortfolioPanel(api: api, productSlug: slug),
                  const Divider(height: 1),
                ],
                if (productVision != null) ...[
                  _FutureVisionPanel(vision: productVision),
                  const Divider(height: 1),
                ] else
                  const Padding(
                    padding: EdgeInsets.fromLTRB(20, 4, 20, 18),
                    child: Row(
                      children: [
                        Icon(Icons.auto_awesome_outlined, size: 18),
                        SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Start een roadmap-sessie om de verre producthorizon te ontwerpen.',
                          ),
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

class LivingVisionPortfolioPanel extends StatelessWidget {
  const LivingVisionPortfolioPanel({
    required this.api,
    required this.productSlug,
    super.key,
  });

  final DashboardApi api;
  final String productSlug;

  @override
  Widget build(BuildContext context) => FutureBuilder<Map<String, dynamic>>(
    future: api.livingVisionPortfolio(productSlug),
    builder: (context, snapshot) {
      if (snapshot.connectionState != ConnectionState.done) {
        return const Padding(
          padding: EdgeInsets.all(20),
          child: LinearProgressIndicator(semanticsLabel: 'Levende visie laden'),
        );
      }
      if (snapshot.hasError) {
        return Padding(
          padding: const EdgeInsets.all(20),
          child: Text(
            'Levende visie kon niet worden geladen: ${snapshot.error}',
          ),
        );
      }
      final data = snapshot.data ?? const <String, dynamic>{};
      final ideas = _livingMaps(data['ideas']);
      final conceptVersions = _livingMaps(data['conceptVersions']);
      final inspiration = _livingMaps(data['inspiration']);
      final research = _livingMaps(data['research']);
      return Semantics(
        container: true,
        label: 'Levende productvisie',
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Levende productvisie',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 4),
              Text(
                '${ideas.length} ideeën · ${conceptVersions.length} conceptversies · ${inspiration.length} bronnen · ${research.length} onderzoeken',
              ),
              const SizedBox(height: 14),
              Text(
                'Ideeënportfolio',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              if (ideas.isEmpty)
                const Text('Nog geen ideeën gemigreerd of gecureerd.')
              else
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: ideas
                      .map(
                        (idea) => SizedBox(
                          width: math
                              .min(300, MediaQuery.sizeOf(context).width - 40)
                              .toDouble(),
                          child: Card(
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '${idea['ideaKey']}',
                                    style: Theme.of(
                                      context,
                                    ).textTheme.titleSmall,
                                  ),
                                  Text(
                                    '${idea['status']} · versie ${idea['currentVersion']}',
                                  ),
                                  const SizedBox(height: 6),
                                  Text('${idea['promise']}'),
                                  if ('${idea['statusReason'] ?? ''}'
                                      .isNotEmpty)
                                    Text(
                                      'Wijzigingsreden: ${idea['statusReason']}',
                                    ),
                                  Align(
                                    alignment: Alignment.centerLeft,
                                    child: TextButton.icon(
                                      onPressed: () => _showIdeaHistory(
                                        context,
                                        api,
                                        productSlug,
                                        '${idea['ideaKey']}',
                                      ),
                                      icon: const Icon(Icons.history),
                                      label: const Text('Versiegeschiedenis'),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      )
                      .toList(),
                ),
              if (conceptVersions.isNotEmpty) ...[
                const SizedBox(height: 18),
                Text(
                  'UX-concepten en flows',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                ...conceptVersions.map(
                  (version) => _ConceptVersionCard(
                    api: api,
                    productSlug: productSlug,
                    version: version,
                  ),
                ),
              ],
              if (inspiration.isNotEmpty) ...[
                const SizedBox(height: 18),
                Text(
                  'Externe inspiratie',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                ...inspiration
                    .take(8)
                    .map(
                      (source) => ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.link),
                        title: Text('${source['title']}'),
                        subtitle: Text(
                          'Bronfeit: ${source['observation']}\nAI-interpretatie: ${source['interpretation']}\nBronlink: ${source['sourceUrl']}',
                        ),
                        trailing: IconButton(
                          tooltip: 'Open bronlink',
                          onPressed: () => launchUrl(
                            Uri.parse('${source['sourceUrl']}'),
                            mode: LaunchMode.externalApplication,
                          ),
                          icon: const Icon(Icons.open_in_new),
                        ),
                      ),
                    ),
              ],
              if (research.isNotEmpty) ...[
                const SizedBox(height: 18),
                Text(
                  'Onderzoek en conclusies',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                ...research
                    .take(10)
                    .map(
                      (result) => ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                          '${result['researchType']} · ${result['status']}',
                        ),
                        subtitle: Text(
                          'Vraag: ${result['question']}\nTechnische conclusie: ${result['conclusion']}',
                        ),
                      ),
                    ),
              ],
            ],
          ),
        ),
      );
    },
  );
}

Future<void> _showIdeaHistory(
  BuildContext context,
  DashboardApi api,
  String productSlug,
  String ideaKey,
) => showDialog<void>(
  context: context,
  builder: (context) => AlertDialog(
    title: Text('Versiegeschiedenis · $ideaKey'),
    content: SizedBox(
      width: 640,
      child: FutureBuilder<List<dynamic>>(
        future: api.livingVisionIdeaHistory(productSlug, ideaKey),
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const LinearProgressIndicator(
              semanticsLabel: 'Versiegeschiedenis laden',
            );
          }
          if (snapshot.hasError) {
            return Text(
              'Versiegeschiedenis kon niet worden geladen: ${snapshot.error}',
            );
          }
          final versions = _livingMaps(snapshot.data);
          return ListView(
            shrinkWrap: true,
            children: versions
                .map(
                  (version) => ListTile(
                    leading: CircleAvatar(child: Text('${version['version']}')),
                    title: Text('${version['promise']}'),
                    subtitle: Text(
                      'Wijzigingsreden: ${version['changeReason']}\nRol: ${version['createdByRole']}',
                    ),
                  ),
                )
                .toList(),
          );
        },
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

class _ConceptVersionCard extends StatelessWidget {
  const _ConceptVersionCard({
    required this.api,
    required this.productSlug,
    required this.version,
  });

  final DashboardApi api;
  final String productSlug;
  final Map<String, dynamic> version;

  @override
  Widget build(BuildContext context) {
    final assets = _livingMaps(version['assets']);
    return Card.outlined(
      margin: const EdgeInsets.only(top: 10),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${version['viewport']} · flowstap ${version['flowPosition']} · versie ${version['version']}',
              style: Theme.of(context).textTheme.titleSmall,
            ),
            Text('Gebruikersdoel: ${version['userGoal']}'),
            Text('Interactie: ${version['interaction']}'),
            for (final asset in assets)
              Padding(
                padding: const EdgeInsets.only(top: 10),
                child: Semantics(
                  image: true,
                  label: '${asset['altText'] ?? 'UX-conceptbeeld'}',
                  child: Image.network(
                    '${api.baseUrl}/api/products/${Uri.encodeComponent(productSlug)}/roadmap/living-vision/media/${Uri.encodeComponent('${asset['id']}')}',
                    headers: {
                      if (api.token != null)
                        'Authorization': 'Bearer ${api.token}',
                    },
                    fit: BoxFit.contain,
                    errorBuilder: (_, _, _) => Text(
                      '${asset['altText'] ?? 'Conceptbeeld niet beschikbaar'}',
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _FutureVisionPanel extends StatelessWidget {
  const _FutureVisionPanel({required this.vision});

  final Map<String, dynamic> vision;

  @override
  Widget build(BuildContext context) {
    final content = _map(vision['content']);
    final futureNarrative = '${content['futureNarrative'] ?? ''}'.trim();
    final experiences = _maps(content['experiences']);
    final capabilities = _maps(content['capabilities']);
    final screens = _maps(content['conceptScreens']);
    final assumptions = _maps(content['assumptions']);
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      container: true,
      label: 'Toekomstvisie versie ${vision['version']}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            margin: const EdgeInsets.fromLTRB(20, 4, 20, 16),
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  colors.primaryContainer,
                  colors.tertiaryContainer.withValues(alpha: 0.72),
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.auto_awesome, color: colors.primary),
                    const SizedBox(width: 8),
                    Text(
                      'VERRE STIP · VISIE ${vision['version']}',
                      style: Theme.of(context).textTheme.labelLarge?.copyWith(
                        color: colors.primary,
                        letterSpacing: 0.8,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Text(
                  '${content['northStarTitle'] ?? 'Toekomstvisie'}',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  '${content['northStar'] ?? ''}',
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
                if (futureNarrative.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Text(futureNarrative),
                ],
                if (experiences.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: experiences
                        .map(
                          (item) => Chip(
                            avatar: const Icon(
                              Icons.explore_outlined,
                              size: 17,
                            ),
                            label: Text('${item['title']}'),
                          ),
                        )
                        .toList(),
                  ),
                ],
              ],
            ),
          ),
          if (screens.isNotEmpty) ...[
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Text(
                'Conceptschermen van het eindproduct',
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
            const SizedBox(height: 10),
            SizedBox(
              height: 450,
              child: ListView.separated(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                scrollDirection: Axis.horizontal,
                itemCount: screens.length,
                separatorBuilder: (_, _) => const SizedBox(width: 14),
                itemBuilder: (context, index) =>
                    _ConceptScreenCard(screen: screens[index]),
              ),
            ),
          ],
          ExpansionTile(
            tilePadding: const EdgeInsets.symmetric(horizontal: 20),
            leading: const Icon(Icons.route_outlined),
            title: const Text('Route naar de horizon'),
            subtitle: Text(
              '${capabilities.length} capabilities · ${assumptions.length} te toetsen aannames',
            ),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Wrap(
                      spacing: 12,
                      runSpacing: 12,
                      children: [
                        for (final horizon in const [
                          'NOW',
                          'NEXT',
                          'LATER',
                          'HORIZON',
                        ])
                          _HorizonColumn(
                            horizon: horizon,
                            capabilities: capabilities
                                .where((item) => item['horizon'] == horizon)
                                .toList(),
                          ),
                      ],
                    ),
                    if (assumptions.isNotEmpty) ...[
                      const SizedBox(height: 18),
                      Text(
                        'Te toetsen voordat we de ambitie aanpassen',
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      const SizedBox(height: 6),
                      for (final assumption in assumptions)
                        ListTile(
                          dense: true,
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.science_outlined, size: 20),
                          title: Text('${assumption['statement']}'),
                          subtitle: Text(
                            '${assumption['probeType']} · ${assumption['proposedProbe']}',
                          ),
                          trailing: Text('${assumption['feasibility']}'),
                        ),
                    ],
                    const SizedBox(height: 8),
                    Text(
                      'Laatste visiewijziging: ${vision['changeSummary']}',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  static Map<String, dynamic> _map(Object? value) => value is Map
      ? value.map((key, item) => MapEntry('$key', item))
      : const <String, dynamic>{};

  static List<Map<String, dynamic>> _maps(Object? value) => value is List
      ? value.map(_map).where((item) => item.isNotEmpty).toList()
      : const <Map<String, dynamic>>[];
}

class _ConceptScreenCard extends StatelessWidget {
  const _ConceptScreenCard({required this.screen});

  final Map<String, dynamic> screen;

  @override
  Widget build(BuildContext context) {
    final mobile = screen['viewport'] == 'MOBILE';
    final colors = Theme.of(context).colorScheme;
    final highlights = screen['highlights'] is List
        ? (screen['highlights'] as List).map((item) => '$item').toList()
        : const <String>[];
    return Semantics(
      label: 'Conceptscherm ${screen['title']}: ${screen['visualDescription']}',
      child: SizedBox(
        width: mobile ? 270 : 390,
        child: Card(
          clipBehavior: Clip.antiAlias,
          elevation: 4,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(mobile ? 28 : 16),
            side: BorderSide(color: colors.outlineVariant),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Container(
                height: 30,
                color: colors.surfaceContainerHighest,
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: Row(
                  children: [
                    const Icon(Icons.circle, size: 7),
                    const SizedBox(width: 5),
                    const Icon(Icons.circle, size: 7),
                    const Spacer(),
                    Text(
                      '${screen['title']}',
                      style: Theme.of(context).textTheme.labelSmall,
                    ),
                    const Spacer(),
                    Icon(
                      mobile ? Icons.smartphone : Icons.desktop_windows,
                      size: 14,
                    ),
                  ],
                ),
              ),
              Container(
                height: 112,
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [colors.primary, colors.tertiary],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.travel_explore,
                      color: colors.onPrimary,
                      size: 46,
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Text(
                        '${screen['visualDescription']}',
                        maxLines: 4,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: colors.onPrimary,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.all(18),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${screen['eyebrow']}',
                        style: Theme.of(context).textTheme.labelMedium
                            ?.copyWith(color: colors.primary),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '${screen['headline']}',
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '${screen['body']}',
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 10),
                      for (final highlight in highlights.take(3))
                        Padding(
                          padding: const EdgeInsets.only(bottom: 5),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Icon(Icons.auto_awesome, size: 15),
                              const SizedBox(width: 6),
                              Expanded(
                                child: Text(
                                  highlight,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ),
                            ],
                          ),
                        ),
                      const Spacer(),
                      Row(
                        children: [
                          Expanded(
                            child: FilledButton(
                              onPressed: null,
                              child: Text('${screen['primaryAction']}'),
                            ),
                          ),
                          if ('${screen['secondaryAction'] ?? ''}'
                              .isNotEmpty) ...[
                            const SizedBox(width: 8),
                            IconButton(
                              onPressed: null,
                              tooltip: '${screen['secondaryAction']}',
                              icon: const Icon(Icons.arrow_forward),
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HorizonColumn extends StatelessWidget {
  const _HorizonColumn({required this.horizon, required this.capabilities});

  final String horizon;
  final List<Map<String, dynamic>> capabilities;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 245,
    child: Card.outlined(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(horizon, style: Theme.of(context).textTheme.labelLarge),
            const SizedBox(height: 6),
            if (capabilities.isEmpty)
              Text('Nog leeg', style: Theme.of(context).textTheme.bodySmall)
            else
              for (final capability in capabilities)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${capability['title']}',
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      Text(
                        '${capability['outcome']}',
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      Text(
                        '${capability['feasibility']}',
                        style: Theme.of(context).textTheme.labelSmall,
                      ),
                    ],
                  ),
                ),
          ],
        ),
      ),
    ),
  );
}

class _EpicGraph extends StatelessWidget {
  const _EpicGraph({required this.epics, required this.onTap});

  static const cardWidth = 226.0;
  static const cardHeight = 222.0;
  static const step = 266.0;
  static const graphHeight = 304.0;

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
              Wrap(
                spacing: 5,
                children: [
                  _MiniLabel(epic.horizon),
                  if (epic.kind == 'DISCOVERY') const _MiniLabel('PROEF'),
                ],
              ),
              const SizedBox(height: 6),
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

class _MiniLabel extends StatelessWidget {
  const _MiniLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.secondaryContainer,
      borderRadius: BorderRadius.circular(10),
    ),
    child: Text(text, style: Theme.of(context).textTheme.labelSmall),
  );
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
