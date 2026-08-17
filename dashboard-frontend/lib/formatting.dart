/// Gedeelde formatteerhulpjes voor datum/tijd en duur. Bewust zonder extra dependency (zoals `intl`):
/// het dashboard heeft maar één vast formaat nodig en de backend levert altijd ISO-8601 in UTC.
library;

/// Parseert een backendtijdstempel defensief: alles wat geen bruikbare ISO-8601-waarde is levert `null`,
/// zodat een onverwacht (of ontbrekend) veld nooit de hele lijst laat crashen.
DateTime? parseInstant(Object? value) {
  if (value is DateTime) return value.toLocal();
  if (value is! String) return null;
  final text = value.trim();
  if (text.isEmpty) return null;
  return DateTime.tryParse(text)?.toLocal();
}

/// Vast, leesbaar formaat in de lokale tijdzone van de browser: `dd-MM-yyyy HH:mm`.
String formatDateTime(Object? value, {String fallback = '—'}) {
  final moment = parseInstant(value);
  if (moment == null) return fallback;
  String pad(int number) => number.toString().padLeft(2, '0');
  return '${pad(moment.day)}-${pad(moment.month)}-${moment.year} '
      '${pad(moment.hour)}:${pad(moment.minute)}';
}

/// Compacte duur met maximaal twee eenheden: `2u 13m`, `4m 12s` of `35s`. Een negatieve duur (klokverschil
/// tussen server en browser) wordt als `0s` getoond in plaats van als onzinnige waarde.
String formatDuration(Duration duration) {
  final total = duration.isNegative ? Duration.zero : duration;
  final hours = total.inHours;
  final minutes = total.inMinutes % 60;
  final seconds = total.inSeconds % 60;
  if (hours > 0) return '${hours}u ${minutes}m';
  if (minutes > 0) return '${minutes}m ${seconds}s';
  return '${seconds}s';
}

/// Start- en doorlooptijd van een productcyclus, klaar om als tekst te tonen.
class IterationTiming {
  const IterationTiming({
    required this.startLabel,
    required this.durationLabel,
  });

  /// Starttijd op basis van `startedAt`, met `createdAt` als terugvaloptie.
  final String startLabel;

  /// Doorlooptijd; `null` als de cyclus nog niet gestart is en er dus niets te meten valt.
  final String? durationLabel;
}

/// Leidt start- en doorlooptijd af uit een `ShadowIterationView`-map.
///
/// - afgeronde cyclus: `completedAt - startedAt`;
/// - lopende cyclus: tijd sinds `startedAt`, herkenbaar als 'loopt nog' (loopt mee met de auto-refresh);
/// - nog niet gestart: aanmaaktijd als starttijd en geen doorlooptijd.
IterationTiming iterationTiming(
  Map<String, dynamic> iteration, {
  DateTime? now,
}) {
  final started = parseInstant(iteration['startedAt']);
  final created = parseInstant(iteration['createdAt']);
  final completed = parseInstant(iteration['completedAt']);
  final startLabel = formatDateTime(
    iteration['startedAt'] ?? iteration['createdAt'],
  );
  if (started == null) {
    return IterationTiming(
      startLabel: created == null
          ? startLabel
          : formatDateTime(iteration['createdAt']),
      durationLabel: null,
    );
  }
  if (completed != null) {
    return IterationTiming(
      startLabel: startLabel,
      durationLabel: formatDuration(completed.difference(started)),
    );
  }
  final reference = now ?? DateTime.now();
  return IterationTiming(
    startLabel: startLabel,
    durationLabel:
        'loopt nog: ${formatDuration(reference.difference(started))}',
  );
}

/// Structurele vergelijking van JSON-achtige waarden (List/Map/String/num/bool/null zoals `jsonDecode`
/// die oplevert). Gebruikt door auto-refreshende schermen om te bepalen of opnieuw opgehaalde data
/// daadwerkelijk verschilt van wat al op het scherm staat, zodat een ongewijzigde ververscyclus geen
/// rebuild (en dus geen scroll-/selectiereset) veroorzaakt.
bool deepEquals(Object? a, Object? b) {
  if (identical(a, b)) return true;
  if (a is List && b is List) {
    if (a.length != b.length) return false;
    for (var index = 0; index < a.length; index++) {
      if (!deepEquals(a[index], b[index])) return false;
    }
    return true;
  }
  if (a is Map && b is Map) {
    if (a.length != b.length) return false;
    for (final key in a.keys) {
      if (!b.containsKey(key) || !deepEquals(a[key], b[key])) return false;
    }
    return true;
  }
  return a == b;
}

/// Sorteert nieuwste eerst op het eerste tijdstempelveld dat een bruikbare waarde heeft.
/// Items zonder enig bruikbaar tijdstempel zakken naar onderen en behouden onderling hun volgorde.
List<Map<String, dynamic>> sortedByNewestFirst(
  List<dynamic> items,
  List<String> timestampFields,
) {
  final sortable = items.cast<Map<String, dynamic>>().toList();
  DateTime? stamp(Map<String, dynamic> item) {
    for (final field in timestampFields) {
      final moment = parseInstant(item[field]);
      if (moment != null) return moment;
    }
    return null;
  }

  sortable.sort((left, right) {
    final leftStamp = stamp(left);
    final rightStamp = stamp(right);
    if (leftStamp == null && rightStamp == null) return 0;
    if (leftStamp == null) return 1;
    if (rightStamp == null) return -1;
    return rightStamp.compareTo(leftStamp);
  });
  return sortable;
}
