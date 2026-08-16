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

String? validateOwnerFocus(String value) {
  final trimmed = value.trim();
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
