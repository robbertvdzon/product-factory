import 'formatting.dart';

const kEnvironmentIdentityUnknown = 'Onbekend';

final RegExp _fullSourceRevision = RegExp(r'^[0-9a-fA-F]{40}$');
final RegExp _zonedIso8601 = RegExp(
  r'^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:[.,]\d{1,6})?)?(Z|([+-])(\d{2})(?::?(\d{2}))?)$',
);

/// Het enige presentatiemodel voor buildgebonden omgevingsmetadata.
///
/// Ieder veld wordt onafhankelijk fail-closed gevalideerd. Ruwe invoer wordt
/// niet bewaard, zodat ongeldige buildwaarden ook niet per ongeluk door een
/// widget kunnen worden getoond.
class EnvironmentIdentityPresentation {
  const EnvironmentIdentityPresentation._({
    required this.environment,
    required this.revision,
    required this.deployedAt,
  });

  factory EnvironmentIdentityPresentation.fromBuildMetadata({
    Object? environment,
    Object? sourceRevision,
    Object? deployedAt,
  }) {
    return EnvironmentIdentityPresentation._(
      environment: _environmentLabel(environment),
      revision: _revisionLabel(sourceRevision),
      deployedAt: _deploymentTimeLabel(deployedAt),
    );
  }

  final String environment;
  final String revision;
  final String deployedAt;
}

String _environmentLabel(Object? value) => switch (value) {
  'production' => 'Productie',
  'acceptance' => 'Acceptatie',
  'preview' => 'Preview',
  _ => kEnvironmentIdentityUnknown,
};

String _revisionLabel(Object? value) {
  if (value is! String || !_fullSourceRevision.hasMatch(value)) {
    return kEnvironmentIdentityUnknown;
  }
  return value.substring(0, 12);
}

String _deploymentTimeLabel(Object? value) {
  if (value is! String) return kEnvironmentIdentityUnknown;
  final match = _zonedIso8601.firstMatch(value);
  if (match == null || !_hasValidCalendarFields(match)) {
    return kEnvironmentIdentityUnknown;
  }
  final moment = DateTime.tryParse(value);
  if (moment == null) return kEnvironmentIdentityUnknown;
  return formatDateTime(moment, fallback: kEnvironmentIdentityUnknown);
}

bool _hasValidCalendarFields(RegExpMatch match) {
  final year = int.parse(match.group(1)!);
  final month = int.parse(match.group(2)!);
  final day = int.parse(match.group(3)!);
  final hour = int.parse(match.group(4)!);
  final minute = int.parse(match.group(5)!);
  final second = int.tryParse(match.group(6) ?? '0') ?? -1;
  final timezoneHour = int.tryParse(match.group(9) ?? '0') ?? -1;
  final timezoneMinute = int.tryParse(match.group(10) ?? '0') ?? -1;
  if (month < 1 ||
      month > 12 ||
      day < 1 ||
      hour > 23 ||
      minute > 59 ||
      second > 59 ||
      timezoneHour > 23 ||
      timezoneMinute > 59) {
    return false;
  }
  final daysInMonth = DateTime.utc(year, month + 1, 0).day;
  return day <= daysInMonth;
}
