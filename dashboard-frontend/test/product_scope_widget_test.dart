import 'dart:convert';
import 'dart:ui' show SemanticsAction;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:product_factory_dashboard/product_scope.dart';
import 'package:shared_preferences/shared_preferences.dart';

http.Response _json(Object body, {int status = 200}) =>
    http.Response(jsonEncode(body), status);

Map<String, dynamic> _product(String slug, String name) => {
  'slug': slug,
  'name': name,
  'status': 'active',
  'workspaceOwnership': 'product-factory',
  'developmentMode': 'autonomous',
  'meetingRequestedTopics': <String>[],
};

Map<String, dynamic> _iteration(String slug, int sequence) => {
  'id': '$slug-$sequence',
  'productSlug': slug,
  'sequenceNumber': sequence,
  'mode': 'autonomous',
  'status': 'RUNNING',
  'currentRole': 'critic',
  'candidateCount': 1,
  'acceptedCandidateCount': 0,
  'revisionRounds': 0,
  'createdAt': '2026-08-${sequence.toString().padLeft(2, '0')}T10:00:00Z',
  'startedAt': '2026-08-${sequence.toString().padLeft(2, '0')}T10:00:00Z',
};

Map<String, dynamic> _candidate(
  int id,
  Object? slug,
  Object? sequence,
  String title,
) => {
  'id': id,
  'productSlug': slug,
  'iterationSequenceNumber': sequence,
  'title': title,
  'description': 'Details van $title',
  'acceptanceCriteria': 'Exacte acceptatie voor $title',
  'status': 'ACCEPTED',
  'createdAt': '2026-08-${id.toString().padLeft(2, '0')}T11:00:00Z',
};

Map<String, dynamic> _delivery(int id, int? candidateId, String title) => {
  'id': id,
  if (candidateId != null) 'candidateId': candidateId,
  // Bewust misleidend: Beheer mag deze slug niet als productscope gebruiken.
  'productSlug': candidateId == 1 ? 'Beta' : 'Alpha',
  'title': title,
  'status': 'DONE',
  'externalStoryKey': 'PRODUCT-$id',
  'remotePhase': 'developed',
  'createdAt': '2026-08-${id.toString().padLeft(2, '0')}T12:00:00Z',
};

class _Fixture {
  final products = [
    _product('Alpha', 'Alpha product'),
    _product('Beta', 'Beta product'),
  ];
  final iterations = [
    _iteration('Alpha', 1),
    _iteration('Beta', 2),
    _iteration('Alpha', 3),
    _iteration('Alpha', 3),
    _iteration('alpha', 4),
  ];
  final candidates = [
    _candidate(1, 'Alpha', 1, 'Alpha gekoppeld'),
    _candidate(2, 'Beta', 2, 'Beta gekoppeld'),
    _candidate(3, 'Alpha', 3, 'Alpha ambigu'),
    _candidate(4, 'Alpha', 999, 'Alpha zonder cyclus'),
    _candidate(5, 'alpha', 1, 'Afwijkende slug'),
    _candidate(6, 'Alpha', '1', 'Verkeerd type'),
    _candidate(7, null, 1, 'Ontbrekende slug'),
  ];
  final deliveries = [
    _delivery(101, 1, 'Levering voor Alpha'),
    _delivery(102, 2, 'Levering voor Beta'),
    _delivery(103, 999, 'Levering zonder kandidaat'),
  ];
}

MockClient _client(_Fixture fixture, List<String> calls) =>
    MockClient((request) async {
      calls.add('${request.method} ${request.url.path}');
      switch (request.url.path) {
        case '/api/products':
          return _json(fixture.products);
        case '/api/shadow-iterations':
          return _json(fixture.iterations);
        case '/api/story-candidates':
          return _json(fixture.candidates);
        case '/api/autonomy/deliveries':
          return _json(fixture.deliveries);
        case '/api/ai-catalog':
          return _json(<String, dynamic>{});
        default:
          return _json(<dynamic>[]);
      }
    });

Future<void> _pumpDashboard(
  WidgetTester tester,
  MockClient client, {
  Size size = const Size(1200, 7000),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await http.runWithClient(() async {
    await tester.pumpWidget(const ProductFactoryDashboard());
    for (var pump = 0; pump < 6; pump++) {
      await tester.pump();
    }
  }, () => client);
}

Future<void> _chooseScope(WidgetTester tester, String label) async {
  await tester.tap(find.byType(ProductScopePicker));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
  await tester.tap(find.text(label).last);
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

bool _containsFocus(WidgetTester tester, Finder finder) {
  final context = tester.binding.focusManager.primaryFocus?.context;
  if (context is! Element) return false;
  if (finder.evaluate().contains(context)) return true;
  var found = false;
  context.visitAncestorElements((ancestor) {
    found = finder.evaluate().contains(ancestor);
    return !found;
  });
  return found;
}

void main() {
  testWidgets(
    'herstelt een geldige voorkeur zonder de API-volgorde te wijzigen',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        activeProductSlugPreferenceKey: 'Beta',
      });
      final calls = <String>[];
      await _pumpDashboard(tester, _client(_Fixture(), calls));

      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('active-product-name')))
            .data,
        'Beta product',
      );
      expect(find.text('Beta · iteratie 2'), findsOneWidget);
      expect(find.text('Alpha · iteratie 1'), findsNothing);
      expect(find.text('Beta gekoppeld'), findsOneWidget);
      expect(find.text('Alpha gekoppeld'), findsNothing);
    },
  );

  testWidgets(
    'verwijdert een onbekende voorkeur en kiest het eerste API-product',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        activeProductSlugPreferenceKey: 'Onbekend',
      });
      await _pumpDashboard(tester, _client(_Fixture(), <String>[]));

      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('active-product-name')))
            .data,
        'Alpha product',
      );
      final preferences = await SharedPreferences.getInstance();
      expect(preferences.containsKey(activeProductSlugPreferenceKey), isFalse);
    },
  );

  testWidgets(
    'zonder producten verdwijnt voorkeur en bestaat geen fictieve scope',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        activeProductSlugPreferenceKey: 'Alpha',
      });
      final fixture = _Fixture()..products.clear();
      await _pumpDashboard(tester, _client(fixture, <String>[]));

      expect(find.byKey(const ValueKey('empty-product-scope')), findsOneWidget);
      expect(find.byType(ProductScopePicker), findsNothing);
      expect(find.text('Cyclus starten'), findsNothing);
      expect(find.text('Eerdere cycli'), findsNothing);
      expect(find.text('Gekoppelde stories'), findsNothing);
      expect(find.byType(StartCycleButton), findsNothing);
      final preferences = await SharedPreferences.getInstance();
      expect(preferences.containsKey(activeProductSlugPreferenceKey), isFalse);
    },
  );

  testWidgets(
    'productwissel verandert alleen zichtbare scope en voorkeur, houdt focus en meldt tellingen',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final fixture = _Fixture();
      final originalJson = jsonEncode({
        'products': fixture.products,
        'iterations': fixture.iterations,
        'candidates': fixture.candidates,
        'deliveries': fixture.deliveries,
      });
      final calls = <String>[];
      await _pumpDashboard(tester, _client(fixture, calls));
      final semanticsHandle = tester.ensureSemantics();
      final callsBeforeSwitch = List<String>.of(calls);

      await _chooseScope(tester, 'Beta product');

      expect(calls, callsBeforeSwitch);
      expect(
        jsonEncode({
          'products': fixture.products,
          'iterations': fixture.iterations,
          'candidates': fixture.candidates,
          'deliveries': fixture.deliveries,
        }),
        originalJson,
      );
      expect(find.text('Beta · iteratie 2'), findsOneWidget);
      expect(find.text('Alpha · iteratie 1'), findsNothing);
      expect(find.text('Beta gekoppeld'), findsOneWidget);
      expect(find.text('Alpha ambigu'), findsNothing);
      expect(find.text('Alpha zonder cyclus'), findsNothing);
      expect(_containsFocus(tester, find.byType(ProductScopePicker)), isTrue);
      final status = tester.widget<ProductScopeStatus>(
        find.byType(ProductScopeStatus),
      );
      expect(status.message, contains('1 eerdere cycli'));
      expect(status.message, contains('1 gekoppelde stories'));
      final preferences = await SharedPreferences.getInstance();
      expect(preferences.getString(activeProductSlugPreferenceKey), 'Beta');
      semanticsHandle.dispose();
    },
  );

  testWidgets('scope-status is een aria-live polite-equivalent', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: ProductScopeStatus(message: 'Scope bijgewerkt')),
      ),
    );
    await tester.pump();

    final semantics = tester.getSemantics(find.byType(ProductScopeStatus));
    expect(semantics.flagsCollection.isLiveRegion, isTrue);
    expect(semantics.getSemanticsData().label, contains('Scope bijgewerkt'));
  });

  testWidgets(
    'scopekoppen staan op brede en smalle viewports in vaste leesvolgorde',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      await _pumpDashboard(
        tester,
        _client(_Fixture(), <String>[]),
        size: const Size(320, 7000),
      );

      final activeY = tester
          .getTopLeft(find.byKey(const ValueKey('active-product-name')))
          .dy;
      final startY = tester.getTopLeft(find.text('Cyclus starten')).dy;
      final cyclesY = tester.getTopLeft(find.text('Eerdere cycli')).dy;
      final storiesY = tester.getTopLeft(find.text('Gekoppelde stories')).dy;
      expect(activeY, lessThan(startY));
      expect(startY, lessThan(cyclesY));
      expect(cyclesY, lessThan(storiesY));
      expect(tester.takeException(), isNull);

      await tester.tap(find.text('Alpha gekoppeld'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('Details van Alpha gekoppeld'), findsOneWidget);
      expect(find.text('Details van Beta gekoppeld'), findsNothing);
    },
  );

  testWidgets('scopekoppen behouden dezelfde volgorde op een brede viewport', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    await _pumpDashboard(tester, _client(_Fixture(), <String>[]));

    final positions = [
      tester.getTopLeft(find.byKey(const ValueKey('active-product-name'))).dy,
      tester.getTopLeft(find.text('Cyclus starten')).dy,
      tester.getTopLeft(find.text('Eerdere cycli')).dy,
      tester.getTopLeft(find.text('Gekoppelde stories')).dy,
    ];
    expect(positions, orderedEquals(List<double>.of(positions)..sort()));
  });

  testWidgets(
    'productkeuze exposeert naam en waarde en werkt volledig met toetsenbord',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        activeProductSlugPreferenceKey: 'Alpha',
      });
      await _pumpDashboard(tester, _client(_Fixture(), <String>[]));
      final picker = find.byType(ProductScopePicker);

      for (var tabs = 0; tabs < 8 && !_containsFocus(tester, picker); tabs++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(_containsFocus(tester, picker), isTrue);
      final semantics = tester.getSemantics(picker).getSemanticsData();
      expect(semantics.label, contains('Actief product'));
      expect(semantics.value, contains('Alpha product'));
      expect(semantics.hasAction(SemanticsAction.tap), isTrue);
      final dropdown = tester.widget<DropdownButtonFormField<Object>>(
        find.descendant(
          of: picker,
          matching: find.byType(DropdownButtonFormField<Object>),
        ),
      );
      expect(dropdown.decoration.focusedBorder, isA<OutlineInputBorder>());
      expect(
        (dropdown.decoration.focusedBorder! as OutlineInputBorder)
            .borderSide
            .width,
        3,
      );

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('active-product-name')))
            .data,
        'Beta product',
      );
      expect(_containsFocus(tester, picker), isTrue);
    },
  );

  testWidgets(
    'Beheer filtert via kandidaten en Alle producten blijft tijdelijk',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        activeProductSlugPreferenceKey: 'Alpha',
      });
      final calls = <String>[];
      await _pumpDashboard(tester, _client(_Fixture(), calls));
      await tester.tap(find.text('Beheer'));
      await tester.pump();
      await tester.pump();

      expect(
        find.text('Software Factory-stories — Alpha product'),
        findsOneWidget,
      );
      expect(find.text('Storywachtrij — Alpha product'), findsOneWidget);
      expect(find.textContaining('Levering voor Alpha'), findsWidgets);
      expect(find.textContaining('Levering voor Beta'), findsNothing);
      expect(find.text('Alpha gekoppeld'), findsOneWidget);
      expect(find.text('Beta gekoppeld'), findsNothing);

      final callsBeforeSwitch = List<String>.of(calls);
      await _chooseScope(tester, 'Alle producten');
      expect(calls, callsBeforeSwitch);
      expect(
        find.text('Software Factory-stories — Alle producten'),
        findsOneWidget,
      );
      expect(find.text('Storywachtrij — Alle producten'), findsOneWidget);
      expect(find.textContaining('Levering zonder kandidaat'), findsWidgets);
      expect(find.text('Ontbrekende slug'), findsOneWidget);
      final preferences = await SharedPreferences.getInstance();
      expect(preferences.getString(activeProductSlugPreferenceKey), 'Alpha');

      await tester.tap(find.text('Terug naar overzicht'));
      for (var pump = 0; pump < 3; pump++) {
        await tester.pump();
      }
      expect(
        find.byKey(const ValueKey('management-product-scope-status')),
        findsNothing,
      );
      expect(find.byKey(const ValueKey('product-scope-status')), findsNothing);
      expect(find.textContaining('Beheerscope Alle producten.'), findsNothing);
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('active-product-name')))
            .data,
        'Alpha product',
      );

      await tester.tap(find.text('Beheer'));
      await tester.pump();
      await tester.pump();
      await _chooseScope(tester, 'Beta product');
      expect(preferences.getString(activeProductSlugPreferenceKey), 'Beta');
      expect(
        find.text('Software Factory-stories — Beta product'),
        findsOneWidget,
      );
      expect(find.textContaining('Levering voor Beta'), findsWidgets);
      expect(find.textContaining('Levering voor Alpha'), findsNothing);
      await tester.tap(find.text('Terug naar overzicht'));
      for (var pump = 0; pump < 3; pump++) {
        await tester.pump();
      }
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('active-product-name')))
            .data,
        'Beta product',
      );
    },
  );
}
