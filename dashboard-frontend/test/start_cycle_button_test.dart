import 'dart:convert';
import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/classification.dart';
import 'package:product_factory_dashboard/main.dart';

/// Dekt de herpositionering van de CTA 'Start productcyclus nu' op de Producten-kaart
/// (product-156): eigen rij vóór en visueel gescheiden van de secundaire knoppenrij, AA-contrast,
/// het statuslabel als tekst (niet uitsluitend kleur), tab-volgorde, functionele gelijkheid van de
/// klik-actie en een kleinere kaarthoogte na de herindeling (gemeten tegen een `_LegacyProductCard`
/// die de vóór-deze-wijziging-structuur reconstrueert).
Map<String, dynamic> _product({String status = 'active'}) => {
  'slug': 'demo',
  'name': 'Demo product',
  'status': status,
  'workspaceOwnership': 'product-factory',
  'developmentMode': 'autonomous',
  'maxStoriesPerCycle': 3,
  'wipLimit': 2,
  'aiProvider': 'openai',
  'aiModel': 'gpt-4o-mini',
  'iterationTimes': ['03:00'],
  'meetingRequestedTopics': <String>[],
  'meetingRequestedAt': null,
};

/// Bouwt een [MockClient] die alle door `OverviewPage` gebruikte `/api/...`-endpoints beantwoordt
/// (conform de projectconventie dat widgettests géén echte HTTP-calls doen).
MockClient _buildMockClient(
  Map<String, dynamic> product,
  List<Map<String, String>> callLog,
) {
  return MockClient((request) async {
    callLog.add({'method': request.method, 'path': request.url.path});
    switch (request.url.path) {
      case '/api/products':
        return http.Response(jsonEncode([product]), 200);
      case '/api/ai-catalog':
        return http.Response(jsonEncode(<String, dynamic>{}), 200);
      case '/api/products/demo/cycles':
        return http.Response('', 202);
      default:
        return http.Response(jsonEncode(<dynamic>[]), 200);
    }
  });
}

Future<void> _withDashboard(
  WidgetTester tester,
  Map<String, dynamic> product,
  List<Map<String, String>> callLog,
  double width,
  Future<void> Function() body,
) async {
  tester.view.physicalSize = Size(width, 3000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  final mockClient = _buildMockClient(product, callLog);
  await http.runWithClient(() async {
    await tester.pumpWidget(const ProductFactoryDashboard());
    await tester.pump();
    await tester.pump();
    await body();
  }, () => mockClient);
}

/// Reconstructie van de productkaart-knoppenrij zoals die vóór deze story was: alle vier de
/// knoppen (inclusief 'Start productcyclus nu') samen in één `Wrap`, zonder eigen CTA-rij. Dient
/// als vaste referentie om de kaarthoogte vóór/na deze wijziging te kunnen vergelijken, zonder
/// afhankelijk te zijn van git-geschiedenis.
class _LegacyProductCard extends StatelessWidget {
  const _LegacyProductCard({required this.width});
  final double width;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: width,
    child: Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.inventory_2_outlined),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Demo product',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                const Chip(label: Text('active')),
                const SizedBox(width: 8),
                const Chip(label: Text('autonomous')),
              ],
            ),
            const SizedBox(height: 8),
            const Text(
              'Active: geplande productcycli en leveringen mogen draaien.',
            ),
            const Text(
              'Autonomous: de Product Factory mag geaccepteerde stories '
              'zelfstandig naar de Software Factory sturen.',
            ),
            Align(
              alignment: Alignment.centerRight,
              child: Wrap(
                spacing: 8,
                children: [
                  FilledButton.icon(
                    onPressed: () {},
                    icon: const Icon(Icons.auto_awesome),
                    label: const Text('Start productcyclus nu'),
                  ),
                  OutlinedButton.icon(
                    onPressed: () {},
                    icon: const Icon(Icons.pause),
                    label: const Text('Pauzeren'),
                  ),
                  SettingsButton(onPressed: () async {}),
                  OutlinedButton.icon(
                    onPressed: () {},
                    icon: const Icon(Icons.forum_outlined),
                    label: const Text('Start overleg'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

void main() {
  testWidgets(
    "'Start productcyclus nu' staat als eigen StartCycleButton-widget op een losstaande rij, "
    'vóór en boven de secundaire knoppenrij, met een stijl die door rand/grootte verschilt',
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, _product(), callLog, 900, () async {
        expect(find.byType(StartCycleButton), findsOneWidget);

        // Niet meer onderdeel van de Wrap met de secundaire knoppen.
        final secondaryWrap = find.ancestor(
          of: find.widgetWithText(OutlinedButton, 'Pauzeren'),
          matching: find.byType(Wrap),
        );
        expect(secondaryWrap, findsOneWidget);
        expect(
          find.descendant(
            of: secondaryWrap,
            matching: find.byType(StartCycleButton),
          ),
          findsNothing,
        );

        // De CTA staat visueel boven de secundaire knoppenrij.
        final ctaTop = tester.getTopLeft(find.byType(StartCycleButton)).dy;
        final secondaryTop = tester
            .getTopLeft(find.widgetWithText(OutlinedButton, 'Pauzeren'))
            .dy;
        expect(ctaTop, lessThan(secondaryTop));

        // Stijl verschilt van de secundaire knoppen: eigen (niet-Outlined) widgettype mét rand.
        final ctaButtonWidget = tester.widget<FilledButton>(
          find.descendant(
            of: find.byType(StartCycleButton),
            matching: find.byType(FilledButton),
          ),
        );
        final resolvedSide = ctaButtonWidget.style?.side?.resolve(
          <WidgetState>{},
        );
        expect(resolvedSide, isNotNull);
      });
    },
  );

  testWidgets(
    'de tekst/achtergrond-contrastverhouding van de CTA voldoet aan WCAG 2.1 AA (>=4.5:1)',
    (tester) async {
      final ratio = contrastRatio(
        kStartCycleButtonBackground,
        kStartCycleButtonForeground,
      );
      expect(ratio, greaterThanOrEqualTo(4.5));
    },
  );

  testWidgets(
    "de status (autonomous/manual) blijft als leesbare tekst naast de productnaam zichtbaar",
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, _product(), callLog, 900, () async {
        expect(find.text('active'), findsOneWidget);
        expect(find.text('autonomous'), findsOneWidget);
      });
    },
  );

  testWidgets(
    "de CTA is het eerste interactieve element in de tab-volgorde ná de kaart-heading, vóór "
    "Pauzeren/Instellingen/Start overleg",
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, _product(), callLog, 900, () async {
        final targets = <Type, Finder>{
          StartCycleButton: find.byType(StartCycleButton),
          SettingsButton: find.byType(SettingsButton),
        };
        final pauzerenFinder = find.widgetWithText(OutlinedButton, 'Pauzeren');
        final overlegFinder = find.widgetWithText(
          OutlinedButton,
          'Start overleg',
        );

        final order = <Type>[];
        for (var i = 0; i < 40 && order.length < 4; i++) {
          await tester.sendKeyEvent(LogicalKeyboardKey.tab);
          await tester.pump();

          if (!order.contains(StartCycleButton) &&
              tester
                      .getSemantics(targets[StartCycleButton]!)
                      .flagsCollection
                      .isFocused ==
                  Tristate.isTrue) {
            order.add(StartCycleButton);
          }
          if (!order.contains(SettingsButton) &&
              tester
                      .getSemantics(targets[SettingsButton]!)
                      .flagsCollection
                      .isFocused ==
                  Tristate.isTrue) {
            order.add(SettingsButton);
          }
          if (!order.contains(OutlinedButton) &&
              pauzerenFinder.evaluate().isNotEmpty &&
              tester.getSemantics(pauzerenFinder).flagsCollection.isFocused ==
                  Tristate.isTrue) {
            order.add(OutlinedButton);
          }
          if (!order.contains(ActionChip) &&
              overlegFinder.evaluate().isNotEmpty &&
              tester.getSemantics(overlegFinder).flagsCollection.isFocused ==
                  Tristate.isTrue) {
            order.add(ActionChip);
          }
        }

        expect(order, isNotEmpty);
        expect(order.first, StartCycleButton);
      });
    },
  );

  testWidgets(
    "tap op 'Start productcyclus nu' triggert dezelfde _startCycle-aanroep (POST .../cycles) als "
    'vóór deze wijziging',
    (tester) async {
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, _product(), callLog, 900, () async {
        expect(
          find.descendant(
            of: find.byType(StartCycleButton),
            matching: find.text('Start productcyclus nu'),
          ),
          findsOneWidget,
        );

        await tester.tap(find.byType(StartCycleButton));
        await tester.pump();
        await tester.pump();

        expect(
          callLog.any(
            (call) =>
                call['method'] == 'POST' &&
                call['path'] == '/api/products/demo/cycles',
          ),
          isTrue,
        );
      });
    },
  );

  testWidgets(
    'de knop is uitgeschakeld wanneer het product niet actief of niet product-factory-owned is, '
    'net als vóór deze wijziging',
    (tester) async {
      final product = _product()..['status'] = 'paused';
      final callLog = <Map<String, String>>[];
      await _withDashboard(tester, product, callLog, 900, () async {
        final button = tester.widget<FilledButton>(
          find.descendant(
            of: find.byType(StartCycleButton),
            matching: find.byType(FilledButton),
          ),
        );
        expect(button.onPressed, isNull);
      });
    },
  );

  testWidgets(
    'de kaarthoogte na deze wijziging is kleiner dan de kaarthoogte vóór de wijziging, gemeten op '
    'dezelfde testviewportbreedte',
    (tester) async {
      const viewportWidth = 488.0;
      final callLog = <Map<String, String>>[];

      late double newCardHeight;
      late double newCardWidth;
      await _withDashboard(
        tester,
        _product(),
        callLog,
        viewportWidth,
        () async {
          final card = find.ancestor(
            of: find.text('Demo product'),
            matching: find.byType(Card),
          );
          final size = tester.getSize(card);
          newCardHeight = size.height;
          newCardWidth = size.width;
        },
      );

      // De legacy-kaart wordt op exact dezelfde breedte gerenderd als de daadwerkelijke kaart
      // hierboven (viewportbreedte minus de vaste ListView- en Card-padding uit main.dart).
      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData(
            colorScheme: ColorScheme.fromSeed(
              seedColor: const Color(0xff325d4d),
            ),
          ),
          home: Scaffold(
            body: SingleChildScrollView(
              child: _LegacyProductCard(width: newCardWidth),
            ),
          ),
        ),
      );
      await tester.pump();
      final legacyCardHeight = tester.getSize(find.byType(Card)).height;

      expect(newCardHeight, lessThan(legacyCardHeight));
    },
  );
}
