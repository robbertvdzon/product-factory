import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'external_link.dart';
import 'image_download.dart';

/// An immutable image belonging to an AI reply, fetched through the chat's access check.
class ChatAnswerImage extends StatelessWidget {
  const ChatAnswerImage({super.key, required this.image, required this.bytes});

  final Map<String, Object?> image;
  final Future<Uint8List> bytes;

  String get caption => '${image['caption'] ?? ''}';
  String get label => switch (image['kind']) {
    'SCREENSHOT' =>
      'Screenshot · ${image['environment'] == 'PRODUCTION' ? 'Productie' : 'Acceptatie'}',
    'DESIGN' => 'Ontwerpschets',
    _ => 'Illustratie',
  };

  void enlarge(BuildContext context, Uint8List data) {
    showDialog<void>(
      context: context,
      builder: (context) => Dialog(
        insetPadding: const EdgeInsets.all(16),
        child: SizedBox(
          width: 1200,
          height: MediaQuery.sizeOf(context).height * .9,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        caption,
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    IconButton(
                      tooltip: 'Afbeelding downloaden',
                      onPressed: () =>
                          downloadImage(data, '${image['filename']}'),
                      icon: const Icon(Icons.download_outlined),
                    ),
                    IconButton(
                      tooltip: 'Sluiten',
                      onPressed: () => Navigator.pop(context),
                      icon: const Icon(Icons.close),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: InteractiveViewer(
                  minScale: .5,
                  maxScale: 5,
                  child: Center(child: Image.memory(data, fit: BoxFit.contain)),
                ),
              ),
              Padding(padding: const EdgeInsets.all(8), child: Text(label)),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: 6),
        FutureBuilder<Uint8List>(
          future: bytes,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return const Text('Afbeelding niet beschikbaar.');
            }
            if (!snapshot.hasData) return const LinearProgressIndicator();
            final data = snapshot.data!;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Semantics(
                  label: 'Vergroot: $caption',
                  button: true,
                  child: InkWell(
                    onTap: () => enlarge(context, data),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxHeight: 280),
                      child: Image.memory(
                        data,
                        fit: BoxFit.contain,
                        errorBuilder: (_, _, _) => const Text(
                          'Afbeelding kon niet worden weergegeven.',
                        ),
                      ),
                    ),
                  ),
                ),
                Wrap(
                  spacing: 8,
                  children: [
                    TextButton.icon(
                      onPressed: () => enlarge(context, data),
                      icon: const Icon(Icons.zoom_in),
                      label: const Text('Vergroten'),
                    ),
                    TextButton.icon(
                      onPressed: () =>
                          downloadImage(data, '${image['filename']}'),
                      icon: const Icon(Icons.download_outlined),
                      label: const Text('Downloaden'),
                    ),
                  ],
                ),
              ],
            );
          },
        ),
        SelectableText(caption),
        if (image['kind'] == 'SCREENSHOT' && image['sourceUrl'] is String)
          TextButton(
            onPressed: () => openExternalLink(image['sourceUrl'] as String),
            child: Text('Bron: ${image['sourceUrl']}', softWrap: true),
          ),
      ],
    ),
  );
}
