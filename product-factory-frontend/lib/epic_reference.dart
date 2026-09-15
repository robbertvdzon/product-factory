/// Globale, stabiele epicreferentie naast de leesbare titel.
String epicTitle(Map<String, dynamic> epic) {
  final reference = epic['reference']?.toString() ?? '';
  final title = epic['title']?.toString() ?? '';
  return reference.isEmpty ? title : '$reference · $title';
}
