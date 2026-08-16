import 'dart:async';
import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'api.dart';
import 'formatting.dart';

/// Interactief overlegvenster: chat tussen de producteigenaar en de AI die het product runt.
/// Elk bericht is een synchrone, blokkerende aanroep (de agentworker ondersteunt geen sessies,
/// dus elke beurt is een verse AI-aanroep met de volledige transcript als context) die tot enkele
/// minuten kan duren — vandaar de expliciete "AI denkt na…"-indicator terwijl de invoer op slot zit.
class MeetingDialog extends StatefulWidget {
  const MeetingDialog({
    required this.api,
    required this.productSlug,
    required this.meetingId,
    super.key,
  });

  final DashboardApi api;
  final String productSlug;
  final String meetingId;

  @override
  State<MeetingDialog> createState() => _MeetingDialogState();
}

class _MeetingDialogState extends State<MeetingDialog> {
  late Future<List<dynamic>> data;
  Timer? refreshTimer;
  final _controller = TextEditingController();
  final _scrollController = ScrollController();
  bool _sending = false;
  bool _uploading = false;
  bool _closing = false;
  String? _error;
  int _lastRenderedMessageCount = 0;
  final List<Map<String, dynamic>> _pendingImages = [];

  @override
  void initState() {
    super.initState();
    _reload();
    refreshTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (mounted && !_sending) setState(_reload);
    });
  }

  void _reload() {
    data = Future.wait<dynamic>([
      widget.api.meeting(widget.productSlug, widget.meetingId),
      widget.api.meetingMessages(widget.productSlug, widget.meetingId),
    ]);
  }

  @override
  void dispose() {
    refreshTimer?.cancel();
    _controller.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scrollController.hasClients) return;
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
      );
    });
  }

  Future<void> _send() async {
    final content = _controller.text.trim();
    if ((content.isEmpty && _pendingImages.isEmpty) || _sending || _uploading) {
      return;
    }
    final imageIds = _pendingImages.map((image) => '${image['id']}').toList();
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      await widget.api.sendMeetingMessage(
        widget.productSlug,
        widget.meetingId,
        content,
        imageAssetIds: imageIds,
      );
      if (mounted) {
        setState(() {
          _sending = false;
          _controller.clear();
          _pendingImages.clear();
          _reload();
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _sending = false;
          _error = '$error';
        });
      }
    }
  }

  Future<void> _pickImages() async {
    if (_sending || _uploading || _pendingImages.length >= 5) return;
    final selection = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['png', 'jpg', 'jpeg', 'webp', 'gif'],
      allowMultiple: true,
      withData: true,
    );
    if (selection == null || !mounted) return;
    final remaining = 5 - _pendingImages.length;
    setState(() {
      _uploading = true;
      _error = null;
    });
    try {
      for (final file in selection.files.take(remaining)) {
        final bytes = file.bytes;
        if (bytes == null) {
          throw StateError('Het gekozen bestand kon niet worden gelezen.');
        }
        final uploaded = await widget.api.uploadMeetingImage(
          widget.productSlug,
          file.name,
          _mediaTypeFor(file.extension),
          bytes,
          altText: 'Screenshot: ${file.name}',
        );
        if (mounted) setState(() => _pendingImages.add(uploaded));
      }
    } catch (error) {
      if (mounted) setState(() => _error = '$error');
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  String _mediaTypeFor(String? extension) => switch (extension?.toLowerCase()) {
    'jpg' || 'jpeg' => 'image/jpeg',
    'webp' => 'image/webp',
    'gif' => 'image/gif',
    _ => 'image/png',
  };

  Future<void> _showMinutes(String runId) async {
    try {
      final content = await widget.api.artifact(widget.productSlug, runId);
      if (!mounted) return;
      showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Notulen'),
          content: SizedBox(
            width: 640,
            child: SingleChildScrollView(child: SelectableText(content)),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Sluiten'),
            ),
          ],
        ),
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> _close() async {
    setState(() => _closing = true);
    try {
      await widget.api.closeMeeting(widget.productSlug, widget.meetingId);
      if (mounted) {
        setState(() {
          _closing = false;
          _reload();
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() => _closing = false);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<List<dynamic>>(
    future: data,
    builder: (context, snapshot) {
      final result = snapshot.data;
      final meeting = result?[0] as Map<String, dynamic>?;
      final messages =
          (result?[1] as List<dynamic>?)?.cast<Map<String, dynamic>>() ??
          const [];
      final status = meeting?['status'] as String?;
      final open = status == 'OPEN';
      final initiator = meeting?['initiator'] as String?;
      final topics =
          ((meeting?['requestedTopics'] as List<dynamic>?) ?? const [])
              .cast<String>();
      final outcomeSummary = meeting?['outcomeSummary'] as String?;
      final workspaceRunId = meeting?['workspaceRunId'] as String?;

      if (messages.length > _lastRenderedMessageCount) {
        _lastRenderedMessageCount = messages.length;
        _scrollToBottom();
      }

      return AlertDialog(
        title: Row(
          children: [
            Expanded(
              child: Text('Overleg ${meeting?['sequenceNumber'] ?? ''}'),
            ),
            if (initiator == 'product')
              Tooltip(
                message: topics.isEmpty
                    ? 'Door het product aangevraagd'
                    : topics.map((topic) => '• $topic').join('\n'),
                child: const Chip(label: Text('Door product aangevraagd')),
              ),
          ],
        ),
        content: SizedBox(
          width: 700,
          height: 640,
          child: Builder(
            builder: (context) {
              if (!snapshot.hasData) {
                return const Center(child: CircularProgressIndicator());
              }
              if (snapshot.hasError) {
                return Center(
                  child: Text('Overleg kon niet laden: ${snapshot.error}'),
                );
              }
              return Column(
                children: [
                  if (!open && outcomeSummary != null) ...[
                    Card(
                      color: Theme.of(context).colorScheme.secondaryContainer,
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    'Samenvatting',
                                    style: Theme.of(
                                      context,
                                    ).textTheme.titleSmall,
                                  ),
                                ),
                                if (workspaceRunId != null)
                                  TextButton.icon(
                                    onPressed: () =>
                                        _showMinutes(workspaceRunId),
                                    icon: const Icon(
                                      Icons.description_outlined,
                                    ),
                                    label: const Text('Volledige notulen'),
                                  ),
                              ],
                            ),
                            Text(outcomeSummary),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                  Expanded(
                    child: messages.isEmpty
                        ? const Center(
                            child: Text(
                              'Nog geen berichten. Typ hieronder je eerste bericht.',
                            ),
                          )
                        : ListView.builder(
                            controller: _scrollController,
                            itemCount: messages.length,
                            itemBuilder: (context, index) => _MessageBubble(
                              message: messages[index],
                              api: widget.api,
                              productSlug: widget.productSlug,
                            ),
                          ),
                  ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Text(
                        _error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                  if (_sending) ...[
                    const LinearProgressIndicator(),
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 4),
                      child: Text('AI denkt na…'),
                    ),
                  ],
                  if (_uploading) ...[
                    const LinearProgressIndicator(),
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 4),
                      child: Text('Afbeelding opslaan…'),
                    ),
                  ],
                  if (open) ...[
                    const SizedBox(height: 8),
                    if (_pendingImages.isNotEmpty)
                      SizedBox(
                        height: 88,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          itemCount: _pendingImages.length,
                          separatorBuilder: (_, _) => const SizedBox(width: 8),
                          itemBuilder: (context, index) {
                            final image = _pendingImages[index];
                            return Stack(
                              children: [
                                _MeetingImage(
                                  api: widget.api,
                                  productSlug: widget.productSlug,
                                  image: image,
                                  width: 112,
                                  height: 80,
                                ),
                                Positioned(
                                  right: 2,
                                  top: 2,
                                  child: IconButton.filledTonal(
                                    tooltip: 'Verwijderen uit bericht',
                                    visualDensity: VisualDensity.compact,
                                    onPressed: _sending
                                        ? null
                                        : () => setState(
                                            () =>
                                                _pendingImages.removeAt(index),
                                          ),
                                    icon: const Icon(Icons.close, size: 16),
                                  ),
                                ),
                              ],
                            );
                          },
                        ),
                      ),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _controller,
                            enabled: !_sending,
                            minLines: 1,
                            maxLines: 4,
                            decoration: const InputDecoration(
                              hintText: 'Typ een bericht…',
                              border: OutlineInputBorder(),
                            ),
                            onSubmitted: (_) => _send(),
                          ),
                        ),
                        const SizedBox(width: 8),
                        IconButton.outlined(
                          tooltip: 'Screenshot of afbeelding toevoegen',
                          onPressed:
                              _sending ||
                                  _uploading ||
                                  _pendingImages.length >= 5
                              ? null
                              : _pickImages,
                          icon: const Icon(Icons.add_photo_alternate_outlined),
                        ),
                        const SizedBox(width: 8),
                        IconButton.filled(
                          onPressed: _sending || _uploading ? null : _send,
                          icon: const Icon(Icons.send),
                        ),
                      ],
                    ),
                  ] else
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 8),
                      child: Text('Dit overleg is afgesloten.'),
                    ),
                ],
              );
            },
          ),
        ),
        actions: [
          if (open)
            FilledButton.icon(
              onPressed: _closing || _sending ? null : _close,
              icon: const Icon(Icons.check_circle_outline),
              label: const Text('Overleg afsluiten'),
            ),
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Sluiten'),
          ),
        ],
      );
    },
  );
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({
    required this.message,
    required this.api,
    required this.productSlug,
  });
  final Map<String, dynamic> message;
  final DashboardApi api;
  final String productSlug;

  @override
  Widget build(BuildContext context) {
    final fromOwner = message['sender'] == 'owner';
    final consultedSources =
        (message['consultedSources'] as List?)
            ?.map((source) => '$source')
            .where((source) => source.isNotEmpty)
            .toList() ??
        const <String>[];
    final memoryChanges =
        (message['memoryChanges'] as List?)
            ?.whereType<Map>()
            .map((change) => Map<String, dynamic>.from(change))
            .toList() ??
        const <Map<String, dynamic>>[];
    final images =
        (message['images'] as List?)
            ?.whereType<Map>()
            .map((image) => Map<String, dynamic>.from(image))
            .toList() ??
        const <Map<String, dynamic>>[];
    return Align(
      alignment: fromOwner ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.all(12),
        constraints: const BoxConstraints(maxWidth: 480),
        decoration: BoxDecoration(
          color: fromOwner
              ? Theme.of(context).colorScheme.primaryContainer
              : Theme.of(context).colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              fromOwner ? 'Jij' : 'AI',
              style: Theme.of(context).textTheme.labelSmall,
            ),
            const SizedBox(height: 4),
            if ('${message['content']}'.trim().isNotEmpty)
              Text('${message['content']}'),
            if (images.isNotEmpty) ...[
              const SizedBox(height: 8),
              for (final image in images)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: _MeetingImage(
                    api: api,
                    productSlug: productSlug,
                    image: image,
                    width: 456,
                    height: 260,
                  ),
                ),
            ],
            if (consultedSources.isNotEmpty) ...[
              const SizedBox(height: 8),
              Material(
                type: MaterialType.transparency,
                child: ExpansionTile(
                  tilePadding: EdgeInsets.zero,
                  childrenPadding: const EdgeInsets.only(bottom: 8),
                  dense: true,
                  title: Text(
                    'Geraadpleegde bronnen (${consultedSources.length})',
                  ),
                  children: [
                    for (final source in consultedSources)
                      Align(
                        alignment: Alignment.centerLeft,
                        child: SelectableText('• $source'),
                      ),
                  ],
                ),
              ),
            ],
            if (memoryChanges.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                'Geheugen aangepast',
                style: Theme.of(context).textTheme.labelLarge,
              ),
              for (final change in memoryChanges)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    '${change['action']}: ${change['productSlug']} / ${change['title']}\n${change['reason']}',
                  ),
                ),
            ],
            const SizedBox(height: 4),
            Text(
              formatDateTime(message['createdAt']),
              style: Theme.of(context).textTheme.labelSmall,
            ),
          ],
        ),
      ),
    );
  }
}

class _MeetingImage extends StatefulWidget {
  const _MeetingImage({
    required this.api,
    required this.productSlug,
    required this.image,
    required this.width,
    required this.height,
  });

  final DashboardApi api;
  final String productSlug;
  final Map<String, dynamic> image;
  final double width;
  final double height;

  @override
  State<_MeetingImage> createState() => _MeetingImageState();
}

class _MeetingImageState extends State<_MeetingImage> {
  late Future<Uint8List> bytes;

  @override
  void initState() {
    super.initState();
    bytes = widget.api.meetingImage(
      widget.productSlug,
      '${widget.image['id']}',
    );
  }

  @override
  void didUpdateWidget(covariant _MeetingImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.image['id'] != widget.image['id']) {
      bytes = widget.api.meetingImage(
        widget.productSlug,
        '${widget.image['id']}',
      );
    }
  }

  void _open(Uint8List data) {
    showDialog<void>(
      context: context,
      builder: (context) => Dialog(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1100, maxHeight: 820),
          child: InteractiveViewer(
            minScale: 0.5,
            maxScale: 5,
            child: Image.memory(
              data,
              fit: BoxFit.contain,
              semanticLabel: widget.image['altText']?.toString(),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<Uint8List>(
    future: bytes,
    builder: (context, snapshot) {
      if (snapshot.hasError) {
        return SizedBox(
          width: widget.width,
          height: widget.height,
          child: const Center(child: Icon(Icons.broken_image_outlined)),
        );
      }
      final data = snapshot.data;
      if (data == null) {
        return SizedBox(
          width: widget.width,
          height: widget.height,
          child: const Center(child: CircularProgressIndicator()),
        );
      }
      return Semantics(
        label:
            widget.image['altText']?.toString() ??
            widget.image['filename']?.toString(),
        button: true,
        child: InkWell(
          onTap: () => _open(data),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.memory(
              data,
              width: widget.width,
              height: widget.height,
              fit: BoxFit.contain,
              semanticLabel: widget.image['altText']?.toString(),
            ),
          ),
        ),
      );
    },
  );
}
