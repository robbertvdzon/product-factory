import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/main.dart';

Map<String, dynamic> _product() => {
  'slug': 'demo',
  'name': 'Demo product',
  'mission': 'Demo missie tekst uniek 12345',
  'softwareFactoryProjectKey': 'DEMOPROJECT',
  'targetRepositoryName': 'org/demo-repo',
  'workspaceOwnership': 'product-factory',
  'developmentMode': 'manual',
  'maxStoriesPerCycle': 3,
  'wipLimit': 2,
  'aiProvider': 'openai',
  'aiModel': 'gpt-4o-mini',
  'iterationTimes': ['03:00', '15:00'],
};

Map<String, dynamic> _aiCatalog() => {
  'openai': ['gpt-4o-mini', 'gpt-4o'],
  'anthropic': ['claude-3-5-sonnet'],
};

/// Volgt exact hetzelfde openpatroon als `_OverviewPageState._editProductSettings` in main.dart:
/// `traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop` voor de focus-trap en de publieke
/// [SettingsButton] die de focus na sluiten weer op zichzelf zet, zodat het toetsenbordgedrag van
/// de echte productkaart getest wordt, niet een losse benadering ervan.
class _SettingsHarness extends StatefulWidget {
  const _SettingsHarness({
    required this.product,
    required this.aiCatalog,
    required this.onClosed,
  });

  final Map<String, dynamic> product;
  final Map<String, dynamic> aiCatalog;
  final ValueChanged<Map<String, dynamic>?> onClosed;

  @override
  State<_SettingsHarness> createState() => _SettingsHarnessState();
}

class _SettingsHarnessState extends State<_SettingsHarness> {
  Future<void> _open(BuildContext dialogContext) async {
    final settings = await showDialog<Map<String, dynamic>>(
      context: dialogContext,
      traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
      builder: (_) => ProductSettingsDialog(
        product: widget.product,
        aiCatalog: widget.aiCatalog,
      ),
    );
    widget.onClosed(settings);
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    home: Scaffold(
      body: Center(
        child: Builder(
          builder: (innerContext) =>
              SettingsButton(onPressed: () => _open(innerContext)),
        ),
      ),
    ),
  );
}

Future<void> _openDialog(
  WidgetTester tester, {
  ValueChanged<Map<String, dynamic>?>? onClosed,
}) async {
  tester.view.physicalSize = const Size(1400, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    _SettingsHarness(
      product: _product(),
      aiCatalog: _aiCatalog(),
      onClosed: onClosed ?? (_) {},
    ),
  );
  await tester.tap(find.byType(SettingsButton));
  await tester.pumpAndSettle();
}

bool _focusIsWithinDialog(WidgetTester tester) {
  final ctx = tester.binding.focusManager.primaryFocus?.context;
  if (ctx == null) return false;
  return ctx.findAncestorWidgetOfExactType<AlertDialog>() != null;
}

void main() {
  testWidgets(
    'de alleen-lezen sectie toont missie, Software Factory-project en workspace met label en '
    'toelichting, en deze velden zijn niet bewerkbaar',
    (tester) async {
      await _openDialog(tester);

      expect(find.text('Missie'), findsOneWidget);
      expect(find.text('Software Factory-project'), findsOneWidget);
      expect(find.text('Workspace'), findsOneWidget);
      expect(
        find.textContaining('niet bewerkbaar').evaluate().length,
        greaterThanOrEqualTo(3),
      );

      expect(find.text('Demo missie tekst uniek 12345'), findsOneWidget);
      expect(find.text('DEMOPROJECT'), findsOneWidget);
      expect(find.text('product-factory'), findsOneWidget);

      final missionField = find.ancestor(
        of: find.text('Demo missie tekst uniek 12345'),
        matching: find.byType(TextField),
      );
      final projectField = find.ancestor(
        of: find.text('DEMOPROJECT'),
        matching: find.byType(TextField),
      );
      final workspaceField = find.ancestor(
        of: find.text('product-factory'),
        matching: find.byType(TextField),
      );
      expect(tester.widget<TextField>(missionField).readOnly, isTrue);
      expect(tester.widget<TextField>(projectField).readOnly, isTrue);
      expect(tester.widget<TextField>(workspaceField).readOnly, isTrue);
    },
  );

  testWidgets(
    'elk bewerkbaar veld (max stories, wip-limiet, doelrepository, AI-model, cyclustijden) '
    'wijzigt, slaat op en komt met de juiste sleutel in de opslag-payload terecht',
    (tester) async {
      Map<String, dynamic>? saved;
      await _openDialog(tester, onClosed: (settings) => saved = settings);

      final maxStoriesField = find.ancestor(
        of: find.text('Max stories per cyclus'),
        matching: find.byType(TextField),
      );
      await tester.ensureVisible(maxStoriesField);
      await tester.pump();
      await tester.enterText(maxStoriesField, '9');

      final wipField = find.ancestor(
        of: find.text('WIP-limiet'),
        matching: find.byType(TextField),
      );
      await tester.ensureVisible(wipField);
      await tester.pump();
      await tester.enterText(wipField, '5');

      final repoField = find.ancestor(
        of: find.text('Doelrepository'),
        matching: find.byType(TextField),
      );
      await tester.ensureVisible(repoField);
      await tester.pump();
      await tester.enterText(repoField, 'org/nieuwe-repo');

      final providerField = find.ancestor(
        of: find.text('Provider'),
        matching: find.byType(DropdownButtonFormField<String>),
      );
      await tester.ensureVisible(providerField);
      await tester.pump();
      await tester.tap(providerField);
      await tester.pumpAndSettle();
      await tester.tap(find.text('anthropic').last);
      await tester.pumpAndSettle();

      final chip0300 = find.ancestor(
        of: find.text('03:00'),
        matching: find.byType(Chip),
      );
      await tester.ensureVisible(chip0300);
      await tester.pump();
      await tester.tap(
        find.descendant(of: chip0300, matching: find.byIcon(Icons.cancel)),
      );
      await tester.pump();

      final saveButton = find.widgetWithText(FilledButton, 'Opslaan');
      await tester.ensureVisible(saveButton);
      await tester.pump();
      await tester.tap(saveButton);
      await tester.pumpAndSettle();

      expect(saved, isNotNull);
      expect(saved!['maxStoriesPerCycle'], 9);
      expect(saved!['wipLimit'], 5);
      expect(saved!['targetRepositoryName'], 'org/nieuwe-repo');
      expect(saved!['aiProvider'], 'anthropic');
      expect(saved!['aiModel'], 'claude-3-5-sonnet');
      expect(saved!['iterationTimes'], ['15:00']);
    },
  );

  testWidgets(
    'de dialoog opent met focus binnen de dialoog en houdt de tab-focus binnen de dialoog '
    '(focus-trap)',
    (tester) async {
      await _openDialog(tester);

      expect(_focusIsWithinDialog(tester), isTrue);

      for (var i = 0; i < 25; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        expect(_focusIsWithinDialog(tester), isTrue);
      }
    },
  );

  testWidgets(
    'Escape sluit de dialoog en de focus keert terug naar de Instellingen-knop',
    (tester) async {
      await _openDialog(tester);
      expect(find.byType(AlertDialog), findsOneWidget);

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();

      expect(find.byType(AlertDialog), findsNothing);

      final ctx = tester.binding.focusManager.primaryFocus?.context;
      expect(ctx, isNotNull);
      expect(ctx!.findAncestorWidgetOfExactType<SettingsButton>(), isNotNull);
    },
  );
}
