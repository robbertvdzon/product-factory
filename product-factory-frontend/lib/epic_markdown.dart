import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'external_link.dart';

/// Presentation only: stored content and approval versions remain unchanged.
String readableEpicMarkdown(String source) {
  if (RegExp(r'(^|\n)\s*(#{1,6} |[-*+] |\d+\. |```|>|\|)').hasMatch(source) || source.contains('**')) return source;
  var text = source;
  if (RegExp(r'\(1\)').hasMatch(text) && RegExp(r'\(2\)').hasMatch(text)) {
    text = text.replaceAllMapped(RegExp(r'\s*\((\d+)\)\s+'), (m) => '\n\n${m[1]}. ');
  }
  return text.split('\n\n').map((paragraph) {
    if (paragraph.length < 500 || RegExp(r'^\d+\. ').hasMatch(paragraph)) return paragraph;
    final sentences = paragraph.split(RegExp(r'(?<=[.!?])\s+(?=[A-ZÀ-Ý])'));
    return [for (var i = 0; i < sentences.length; i += 2) sentences.skip(i).take(2).join(' ')].join('\n\n');
  }).join('\n\n');
}

class EpicMarkdown extends StatelessWidget {
  const EpicMarkdown(this.data, {super.key});
  final String data;
  @override
  Widget build(BuildContext context) => MarkdownBody(
    data: readableEpicMarkdown(data),
    selectable: true,
    fitContent: false,
    imageBuilder: (uri, title, alt) => Text(alt ?? title ?? 'Afbeelding: zie Schermen'),
    onTapLink: (text, href, title) { if (href != null) openExternalLink(href); },
    styleSheet: MarkdownStyleSheet.fromTheme(Theme.of(context)).copyWith(
      p: Theme.of(context).textTheme.bodyLarge?.copyWith(height: 1.65),
      h2: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
      h3: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
      blockSpacing: 18,
      listIndent: 24,
      code: const TextStyle(fontFamily: 'monospace', backgroundColor: Color(0x0C000000)),
    ),
  );
}
