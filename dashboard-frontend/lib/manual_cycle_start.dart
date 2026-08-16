const String autonomousDefaultFocus =
    'Bepaal autonoom de belangrijkste nog onbeantwoorde productvraag op basis van missie, bestaand dossier en eerdere iteraties.';
const int maxOwnerFocusLength = 300;

enum ManualStartOrigin { autonomousDefault, ownerInput }

extension ManualStartOriginPresentation on ManualStartOrigin {
  String get label => switch (this) {
    ManualStartOrigin.autonomousDefault => 'Autonome standaard',
    ManualStartOrigin.ownerInput => 'Eigenaarinput',
  };

  String get requestValue => switch (this) {
    ManualStartOrigin.autonomousDefault => 'AUTONOMOUS_DEFAULT',
    ManualStartOrigin.ownerInput => 'OWNER_INPUT',
  };
}

bool _isManualStartTrimCodeUnit(int codeUnit) =>
    (codeUnit >= 0x0009 && codeUnit <= 0x000D) ||
    codeUnit == 0x0020 ||
    codeUnit == 0x0085 ||
    codeUnit == 0x00A0 ||
    codeUnit == 0x1680 ||
    (codeUnit >= 0x2000 && codeUnit <= 0x200A) ||
    codeUnit == 0x2028 ||
    codeUnit == 0x2029 ||
    codeUnit == 0x202F ||
    codeUnit == 0x205F ||
    codeUnit == 0x3000 ||
    codeUnit == 0xFEFF;

/// Expliciet gedeeld whitespacecontract voor eigenaarinput. Dart en de JVM
/// verschillen in hun ingebouwde trimdefinitie; deze Unicode-set staat daarom
/// ook letterlijk in de runtime zodat validatie en opslag bytegelijk blijven.
String trimManualStartFocus(String value) {
  var start = 0;
  var end = value.length;
  while (start < end && _isManualStartTrimCodeUnit(value.codeUnitAt(start))) {
    start++;
  }
  while (end > start && _isManualStartTrimCodeUnit(value.codeUnitAt(end - 1))) {
    end--;
  }
  return value.substring(start, end);
}

String? validateOwnerFocus(String value) {
  final trimmed = trimManualStartFocus(value);
  if (trimmed.isEmpty) return 'Vul een onderzoeksvraag in.';
  if (trimmed.length > maxOwnerFocusLength) {
    return 'Gebruik maximaal 300 tekens na het verwijderen van witruimte aan begin en einde.';
  }
  return null;
}

String? manualStartOriginLabel(Object? value) => switch (value) {
  'AUTONOMOUS_DEFAULT' => 'Autonome standaard',
  'OWNER_INPUT' => 'Eigenaarinput',
  _ => null,
};

class ManualCycleStartSubmission {
  const ManualCycleStartSubmission({required this.focus, required this.origin});

  final String focus;
  final ManualStartOrigin origin;
}
