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

/// Splits presentation only; the planner keeps receiving the complete solution.
({String functional, String technical}) splitEpicSolution(String source) {
  final functional = <String>[];
  final technical = <String>[];
  var inTechnical = false;
  String? fence;
  var fenceLength = 0;
  final heading = RegExp(r'^ {0,3}(#{1,2})[ \t]+(.+?)[ \t]*#*[ \t]*$');
  final technicalTitle = RegExp(
    r'^Technische (uitwerking|route|toelichting)(?: \(.*\))?$',
    caseSensitive: false,
  );
  for (final line in source.split('\n')) {
    final codeFence = RegExp(r'^ {0,3}(`{3,}|~{3,})(.*)$').firstMatch(line);
    if (codeFence != null) {
      final delimiter = codeFence[1]!;
      if (fence == null) {
        fence = delimiter[0];
        fenceLength = delimiter.length;
      } else if (delimiter[0] == fence && delimiter.length >= fenceLength && codeFence[2]!.trim().isEmpty) {
        fence = null;
      }
    } else if (fence == null) {
      final match = heading.firstMatch(line);
      if (match != null) {
        inTechnical = match[1] == '##' && technicalTitle.hasMatch(match[2]!.trim());
        if (inTechnical) {
          if (technical.isNotEmpty) technical.add('\n### ${match[2]}');
          continue;
        }
      }
    }
    (inTechnical ? technical : functional).add(line);
  }
  return (functional: functional.join('\n').trim(), technical: technical.join('\n').trim());
}

class EpicTechnicalDetails extends StatelessWidget {
  const EpicTechnicalDetails(this.data, {super.key});
  final String data;

  @override
  Widget build(BuildContext context) => ExpansionTile(
    title: const Text('Technische uitwerking'),
    subtitle: const Text('Voor de planner en uitvoerende agents'),
    childrenPadding: const EdgeInsets.all(16),
    expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
    children: [EpicMarkdown(data)],
  );
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
