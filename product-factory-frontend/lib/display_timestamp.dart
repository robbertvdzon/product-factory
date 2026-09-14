/// Human-readable local time; legacy messages without a timestamp stay blank.
String displayTimestamp(Object? value) {
  final date = DateTime.tryParse(value?.toString() ?? '')?.toLocal();
  if (date == null) return '';
  const months = ['jan', 'feb', 'mrt', 'apr', 'mei', 'jun', 'jul', 'aug', 'sep', 'okt', 'nov', 'dec'];
  String two(int value) => value.toString().padLeft(2, '0');
  return '${date.day} ${months[date.month - 1]} ${date.year} · ${two(date.hour)}:${two(date.minute)}';
}
