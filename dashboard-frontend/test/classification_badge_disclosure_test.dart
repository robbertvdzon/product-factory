import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/classification.dart';

/// Toetst het inline uitklappaneel met scope-disclaimer van [ClassificationBadge]: toetsenbord-only
/// bereikbaarheid/activatie, de expanded-toggle zonder dialoogwidget, de paneeltekst per variant,
/// Escape met focusherstel, het Semantics-label per variant en de afwezigheid van een externe
/// iteratielog-link. Dit vult `classification_badge_widget_test.dart` (dat de badge-teksten in de
/// iteratierij toetst) aan met het nieuwe uitklapgedrag uit deze story.
class _FocusableBadgeHarness extends StatelessWidget {
  const _FocusableBadgeHarness({required this.classification});

  final String classification;

  @override
  Widget build(BuildContext context) => MaterialApp(
    home: Scaffold(
      body: Column(
        children: [
          // Een eerste focusbare widget vóór de badge, zodat een Tab-druk aantoonbaar de focus
          // van hier náár de badge verplaatst (en dus niet toevallig al op de badge start).
          const TextButton(onPressed: null, child: Text('voor-badge')),
          ClassificationBadge(classification: classification),
        ],
      ),
    ),
  );
}

Future<void> _focusBadgeViaTab(WidgetTester tester) async {
  // Uitsluitend toetsenbordinvoer: geen enkele tap. Twee Tabs zijn nodig omdat de eerste
  // TextButton (disabled, maar nog altijd een focus-stop in de traversal-volgorde) eerst focus
  // krijgt vanuit de niet-gefocuste beginstand.
  await tester.sendKeyEvent(LogicalKeyboardKey.tab);
  await tester.pump();
  await tester.sendKeyEvent(LogicalKeyboardKey.tab);
  await tester.pump();
}

void main() {
  group('toetsenbordbereikbaarheid en -activatie per badgevariant', () {
    for (final classification in kIterationClassifications) {
      testWidgets(
        '$classification: bereikbaar via Tab, activeerbaar via Enter of Space',
        (tester) async {
          await tester.pumpWidget(
            _FocusableBadgeHarness(classification: classification),
          );
          await tester.pump();

          await _focusBadgeViaTab(tester);

          final semanticsBeforeActivation = tester.getSemantics(
            find.byType(ClassificationBadge),
          );
          expect(
            semanticsBeforeActivation.flagsCollection.isFocused,
            Tristate.isTrue,
          );
          expect(
            semanticsBeforeActivation.flagsCollection.isExpanded,
            Tristate.isFalse,
          );
          expect(find.text(kBadgeScopeDisclaimer), findsNothing);

          await tester.sendKeyEvent(LogicalKeyboardKey.enter);
          await tester.pump();

          expect(find.text(kBadgeScopeDisclaimer), findsOneWidget);
          final semanticsAfterEnter = tester.getSemantics(
            find.byType(ClassificationBadge),
          );
          expect(
            semanticsAfterEnter.flagsCollection.isExpanded,
            Tristate.isTrue,
          );

          // Sluiten via Space moet even goed werken als openen.
          await tester.sendKeyEvent(LogicalKeyboardKey.space);
          await tester.pump();
          expect(find.text(kBadgeScopeDisclaimer), findsNothing);
        },
      );
    }
  });

  group('geen dialoog, wél inline paneel', () {
    testWidgets('activeren opent geen showDialog/AlertDialog/Dialog-widget', (
      tester,
    ) async {
      await tester.pumpWidget(
        const _FocusableBadgeHarness(classification: kRichtingGekozen),
      );
      await tester.pump();

      await _focusBadgeViaTab(tester);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();

      expect(find.text(kBadgeScopeDisclaimer), findsOneWidget);
      expect(find.byType(Dialog), findsNothing);
      expect(find.byType(AlertDialog), findsNothing);
    });
  });

  group('paneeltekst per variant', () {
    for (final classification in kIterationClassifications) {
      testWidgets(
        '$classification: geactiveerd paneel bevat exact de scope-disclaimerzin',
        (tester) async {
          await tester.pumpWidget(
            _FocusableBadgeHarness(classification: classification),
          );
          await tester.pump();

          await _focusBadgeViaTab(tester);
          await tester.sendKeyEvent(LogicalKeyboardKey.enter);
          await tester.pump();

          expect(
            find.text('Dit toont wat de uitkomst was, niet waarom.'),
            findsOneWidget,
          );
        },
      );
    }
  });

  group('Escape sluit het paneel en herstelt focus', () {
    testWidgets(
      'Escape klapt het paneel in en de focus keert terug naar het badge',
      (tester) async {
        await tester.pumpWidget(
          const _FocusableBadgeHarness(classification: kGuardrailConflict),
        );
        await tester.pump();

        await _focusBadgeViaTab(tester);
        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pump();
        expect(find.text(kBadgeScopeDisclaimer), findsOneWidget);

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pump();

        expect(find.text(kBadgeScopeDisclaimer), findsNothing);
        final semantics = tester.getSemantics(find.byType(ClassificationBadge));
        expect(semantics.flagsCollection.isExpanded, Tristate.isFalse);
        expect(semantics.flagsCollection.isFocused, Tristate.isTrue);
      },
    );
  });

  group('Semantics-label per variant', () {
    for (final classification in kIterationClassifications) {
      testWidgets('$classification: label bevat de volledige categorienaam', (
        tester,
      ) async {
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: ClassificationBadge(classification: classification),
            ),
          ),
        );
        await tester.pump();

        final semantics = tester.getSemantics(find.byType(ClassificationBadge));
        expect(semantics.label, contains(classification));
      });
    }
  });

  group('geen externe iteratielog-link in het paneel', () {
    testWidgets(
      'het geopende paneel bevat geen Link/InkWell-navigatie of URL-achtige tekst',
      (tester) async {
        await tester.pumpWidget(
          const _FocusableBadgeHarness(classification: kNietClassificeerbaar),
        );
        await tester.pump();

        await _focusBadgeViaTab(tester);
        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pump();

        final panel = find.ancestor(
          of: find.text(kBadgeScopeDisclaimer),
          matching: find.byType(Container),
        );
        expect(panel, findsWidgets);
        expect(
          find.descendant(of: panel.first, matching: find.byType(InkWell)),
          findsNothing,
        );
        expect(
          find.descendant(
            of: panel.first,
            matching: find.byType(GestureDetector),
          ),
          findsNothing,
        );
        expect(find.textContaining('http'), findsNothing);
        expect(find.textContaining('/iterations/'), findsNothing);
      },
    );
  });

  group('rijklikgedrag blijft intact', () {
    testWidgets(
      'tappen op het badge triggert de rij-onTap niet; tappen ernaast wel',
      (tester) async {
        var rowTapCount = 0;
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: ListTile(
                // Zelfde structuur als de echte iteratierij in main.dart: badge in een Wrap in de
                // title, niet in trailing (dat heeft een vaste, te kleine hoogte voor het paneel).
                title: Wrap(
                  spacing: 8,
                  children: const [
                    Text('iteratie 1'),
                    ClassificationBadge(classification: kRichtingGekozen),
                  ],
                ),
                onTap: () => rowTapCount++,
              ),
            ),
          ),
        );
        await tester.pump();

        await tester.tap(find.byType(ClassificationBadge));
        await tester.pump();

        expect(rowTapCount, 0);
        expect(find.text(kBadgeScopeDisclaimer), findsOneWidget);

        await tester.tap(find.text('iteratie 1'));
        await tester.pump();

        expect(rowTapCount, 1);
      },
    );
  });
}
