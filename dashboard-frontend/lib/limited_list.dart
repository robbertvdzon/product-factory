import 'package:flutter/material.dart';

/// Aantal items dat een overzichtslijst standaard toont.
const int kInitialVisibleItems = 5;

/// Aantal items dat er per klik op 'Meer' bij komt.
const int kShowMoreStep = 10;

/// Toont maximaal [visibleCount] items met daaronder een 'Meer'-knop zolang er nog items verborgen zijn.
///
/// De teller zit bewust *buiten* deze widget (in de state van de pagina), zodat de auto-refresh van het
/// dashboard de uitklapstand behoudt en nieuwe items gewoon bovenaan verschijnen.
class LimitedListSection extends StatelessWidget {
  const LimitedListSection({
    required this.itemCount,
    required this.visibleCount,
    required this.itemBuilder,
    required this.onShowMore,
    super.key,
  });

  final int itemCount;
  final int visibleCount;
  final Widget Function(BuildContext context, int index) itemBuilder;
  final VoidCallback onShowMore;

  @override
  Widget build(BuildContext context) {
    final shown = visibleCount < itemCount ? visibleCount : itemCount;
    final hidden = itemCount - shown;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var index = 0; index < shown; index++) itemBuilder(context, index),
        if (hidden > 0)
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: onShowMore,
              icon: const Icon(Icons.expand_more),
              label: Text('Meer (nog $hidden)'),
            ),
          ),
      ],
    );
  }
}
