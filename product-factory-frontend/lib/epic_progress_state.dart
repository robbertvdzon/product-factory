import 'package:flutter/material.dart';

/// Dezelfde betekenis voor de stappenlijst en de compacte epic-reis.
enum EpicProgressState {
  done('Afgerond', Icons.check_circle_outline, Icons.check),
  failed('Niet geslaagd', Icons.cancel_outlined, Icons.close),
  current('Bezig', Icons.play_circle_outline, Icons.circle),
  pending('Nog niet uitgevoerd', Icons.radio_button_unchecked, null);

  const EpicProgressState(this.label, this.icon, this.compactIcon);

  final String label;
  final IconData icon;
  final IconData? compactIcon;

  static EpicProgressState fromValue(Object? value) => switch (value) {
    'DONE' => done,
    'FAILED' => failed,
    'CURRENT' => current,
    _ => pending,
  };
}
