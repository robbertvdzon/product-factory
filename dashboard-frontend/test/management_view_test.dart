import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/main.dart';

http.Response _json(Object body, {int status = 200}) =>
    http.Response(jsonEncode(body), status);

Map<String, dynamic> _candidate(
  int id, {
  String? title,
  String description = 'Beschrijving van de kandidaat',
}) => {
  'id': id,
  'title': title ?? 'Kandidaat $id',
  'description': description,
  'acceptanceCriteria': 'De kandidaat blijft volledig leesbaar.',
  'criticReason': 'Goedgekeurd voor deze synthetische test.',
  'productSlug': 'demo',
  'iterationSequenceNumber': id,
  'status': 'ACCEPTED',
  'createdAt': '2026-08-${id.toString().padLeft(2, '0')}T10:00:00Z',
};

Map<String, dynamic> _delivery(
  int id, {
  String status = 'DONE',
  String? title,
  String? externalStoryKey,
  String? errorMessage,
}) => {
  'id': 1000 + id,
  'candidateId': id,
  'title': title ?? 'Levering $id',
  'productSlug': 'demo',
  'status': status,
  'externalStoryKey': externalStoryKey ?? 'PRODUCT-$id',
  'remotePhase': 'developed',
  'errorMessage': errorMessage,
  'createdAt': '2026-08-${id.toString().padLeft(2, '0')}T11:00:00Z',
};

MockClient _client({
  Future<http.Response> Function()? candidates,
  Future<http.Response> Function()? deliveries,
  List<String>? calls,
  List<dynamic> humanActions = const [],
  List<dynamic> settledQuestions = const [],
}) {
  return MockClient((request) async {
    calls?.add(request.url.path);
    switch (request.url.path) {
      case '/api/story-candidates':
        return candidates?.call() ?? _json(<dynamic>[]);
      case '/api/autonomy/deliveries':
        return deliveries?.call() ?? _json(<dynamic>[]);
      case '/api/autonomy/human-actions':
        return _json(humanActions);
      case '/api/roadmap/settled-questions':
        return _json(settledQuestions);
      case '/api/ai-catalog':
        return _json(<String, dynamic>{});
      default:
        return _json(<dynamic>[]);
    }
  });
}

Future<void> _pumpDashboard(
  WidgetTester tester,
  MockClient client, {
  Size size = const Size(1200, 7000),
  double textScale = 1,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  tester.platformDispatcher.textScaleFactorTestValue = textScale;
  addTearDown(tester.view.reset);
  addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

  await http.runWithClient(() async {
    await tester.pumpWidget(const ProductFactoryDashboard());
    for (var pump = 0; pump < 5; pump++) {
      await tester.pump();
    }
  }, () => client);
}

Future<void> _openManagement(WidgetTester tester) async {
  await tester.tap(find.text('Beheer'));
  for (var pump = 0; pump < 3; pump++) {
    await tester.pump();
  }
}

Future<void> _disposeDashboard(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
}

bool _containsPrimaryFocus(WidgetTester tester, Finder finder) {
  final focusContext = tester.binding.focusManager.primaryFocus?.context;
  if (focusContext is! Element) return false;
  final targets = finder.evaluate().toSet();
  if (targets.contains(focusContext)) return true;
  var found = false;
  focusContext.visitAncestorElements((ancestor) {
    found = targets.contains(ancestor);
    return !found;
  });
  return found;
}

void main() {
  testWidgets(
    'verplaatst beide globale lijsten verliesvrij naar Beheer zonder nieuwe requests',
    (tester) async {
      final calls = <String>[];
      final candidates = [
        _candidate(1),
        _candidate(2),
        _candidate(3),
        _candidate(4),
      ];
      final deliveries = [
        _delivery(1, status: 'ERROR', errorMessage: 'Publicatie geweigerd'),
        _delivery(2, status: 'RUNNING'),
        _delivery(4, status: 'DONE'),
        _delivery(
          20,
          title: 'Niet aan een kandidaat gekoppelde levering',
          externalStoryKey: null,
        )..['externalStoryKey'] = null,
      ];
      final client = _client(
        candidates: () async => _json(candidates),
        deliveries: () async => _json(deliveries),
        calls: calls,
        humanActions: [
          {
            'id': 1,
            'title': 'Tokenactie blijft staan',
            'category': 'TOKEN',
            'reason': 'nodig',
            'status': 'OPEN',
            'createdAt': '2026-08-01T10:00:00Z',
          },
        ],
        settledQuestions: [
          {
            'productSlug': 'demo',
            'content': 'Onderzoeksvraag blijft staan',
            'createdAt': '2026-08-01T10:00:00Z',
          },
        ],
      );

      await _pumpDashboard(tester, client);

      // De metriek blijft op het hoofdscherm, de globale secties en records niet.
      expect(find.text('Software Factory-stories'), findsOneWidget);
      expect(
        find.ancestor(
          of: find.text('Software Factory-stories'),
          matching: find.byType(MetricCard),
        ),
        findsOneWidget,
      );
      expect(find.text('Storywachtrij'), findsNothing);
      expect(find.text('Kandidaat 1'), findsNothing);
      expect(find.text('Levering 1'), findsNothing);
      expect(find.text('Epic-roadmap'), findsOneWidget);
      expect(find.text('Afgehandelde onderzoeksvragen'), findsOneWidget);
      expect(find.text('Roadmap-sessies'), findsOneWidget);
      expect(find.text('Overleggen'), findsOneWidget);
      expect(find.text('Benodigde access tokens'), findsOneWidget);
      expect(find.text('Workspace'), findsOneWidget);

      await _openManagement(tester);

      final deliveryHeadingY = tester
          .getTopLeft(find.text('Software Factory-stories'))
          .dy;
      final queueHeadingY = tester.getTopLeft(find.text('Storywachtrij')).dy;
      expect(deliveryHeadingY, lessThan(queueHeadingY));
      expect(find.byType(SoftwareFactoryDeliveryTile), findsNWidgets(4));
      expect(
        find.text(
          'wordt verstuurd · Niet aan een kandidaat gekoppelde levering',
        ),
        findsOneWidget,
      );
      for (var id = 1; id <= 4; id++) {
        expect(find.text('Kandidaat $id'), findsOneWidget);
      }
      expect(find.text('Fout (1)'), findsOneWidget);
      expect(find.text('Bezig (1)'), findsOneWidget);
      expect(find.text('In wachtrij (1)'), findsOneWidget);
      expect(find.text('Klaar (1)'), findsOneWidget);

      await tester.tap(find.text('Kandidaat 1'));
      await tester.pumpAndSettle();
      expect(find.text('ERROR'), findsOneWidget);
      expect(find.text('PRODUCT-1'), findsOneWidget);
      expect(find.text('developed'), findsOneWidget);
      expect(find.text('Publicatie geweigerd'), findsOneWidget);
      await tester.tap(find.text('Sluiten'));
      await tester.pumpAndSettle();

      const expectedReadPaths = {
        '/api/products',
        '/api/workspace/publications',
        '/api/autonomy/human-actions',
        '/api/ai-catalog',
        '/api/meetings',
        '/api/roadmap/epics',
        '/api/roadmap/settled-questions',
        '/api/roadmap/sessions',
        '/api/shadow-iterations',
        '/api/story-candidates',
        '/api/autonomy/deliveries',
      };
      expect(calls.toSet(), expectedReadPaths);
      for (final path in expectedReadPaths) {
        expect(calls.where((call) => call == path), hasLength(1));
      }
      await _disposeDashboard(tester);
    },
  );

  testWidgets('kandidaatbron toont geladen lege toestand onafhankelijk', (
    tester,
  ) async {
    final client = _client(
      candidates: () async => _json(<dynamic>[]),
      deliveries: () async => _json([_delivery(1)]),
    );
    await _pumpDashboard(tester, client);
    await _openManagement(tester);

    expect(find.text('Nog geen storykandidaten'), findsOneWidget);
    expect(find.textContaining('PRODUCT-1'), findsOneWidget);
    expect(find.textContaining('niet beschikbaar'), findsNothing);
    await _disposeDashboard(tester);
  });

  testWidgets('fout in kandidaatbron verbergt geladen leveringen niet', (
    tester,
  ) async {
    final client = _client(
      candidates: () async => _json({'error': 'stuk'}, status: 500),
      deliveries: () async => _json([_delivery(1)]),
    );
    await _pumpDashboard(tester, client);
    await _openManagement(tester);

    expect(
      find.text('Storykandidaten voor de storywachtrij zijn niet beschikbaar.'),
      findsOneWidget,
    );
    expect(find.textContaining('PRODUCT-1'), findsOneWidget);
    expect(find.text('Nog geen storykandidaten'), findsNothing);
    await _disposeDashboard(tester);
  });

  testWidgets('ladende kandidaatbron verbergt geladen leveringen niet', (
    tester,
  ) async {
    final pendingCandidates = Completer<http.Response>();
    final client = _client(
      candidates: () => pendingCandidates.future,
      deliveries: () async => _json([_delivery(1)]),
    );
    await _pumpDashboard(tester, client);
    await _openManagement(tester);

    expect(
      find.text('Storykandidaten voor de storywachtrij worden geladen.'),
      findsOneWidget,
    );
    expect(find.textContaining('PRODUCT-1'), findsOneWidget);
    pendingCandidates.complete(_json(<dynamic>[]));
    await tester.pump();
    await _disposeDashboard(tester);
  });

  testWidgets('leveringsbron toont geladen lege toestand onafhankelijk', (
    tester,
  ) async {
    final client = _client(
      candidates: () async => _json([_candidate(1)]),
      deliveries: () async => _json(<dynamic>[]),
    );
    await _pumpDashboard(tester, client);
    await _openManagement(tester);

    expect(
      find.text('Nog geen stories naar de Software Factory gestuurd'),
      findsOneWidget,
    );
    expect(find.text('Kandidaat 1'), findsOneWidget);
    expect(find.text('In wachtrij (1)'), findsOneWidget);
    await _disposeDashboard(tester);
  });

  testWidgets(
    'fout in leveringsbron toont fout én markeert geladen kandidaten als onvolledig',
    (tester) async {
      final client = _client(
        candidates: () async => _json([_candidate(1)]),
        deliveries: () async => _json({'error': 'stuk'}, status: 500),
      );
      await _pumpDashboard(tester, client);
      await _openManagement(tester);

      expect(
        find.text('Software Factory-leveringen zijn niet beschikbaar.'),
        findsOneWidget,
      );
      expect(
        find.text(
          '1 storykandidaten geladen. Storywachtrij is onvolledig omdat Software Factory-leveringen niet beschikbaar zijn.',
        ),
        findsOneWidget,
      );
      expect(
        find.text('Nog geen stories naar de Software Factory gestuurd'),
        findsNothing,
      );
      expect(find.text('Nog geen storykandidaten'), findsNothing);
      await _disposeDashboard(tester);
    },
  );

  testWidgets(
    'ladende leveringsbron markeert de wachtrij onvolledig zonder kandidaatbron als leeg te tonen',
    (tester) async {
      final pendingDeliveries = Completer<http.Response>();
      final client = _client(
        candidates: () async => _json([_candidate(1)]),
        deliveries: () => pendingDeliveries.future,
      );
      await _pumpDashboard(tester, client);
      await _openManagement(tester);

      expect(
        find.text('Software Factory-leveringen worden geladen.'),
        findsOneWidget,
      );
      expect(
        find.text(
          '1 storykandidaten geladen. Storywachtrij is onvolledig zolang Software Factory-leveringen worden geladen.',
        ),
        findsOneWidget,
      );
      expect(find.text('Nog geen storykandidaten'), findsNothing);
      pendingDeliveries.complete(_json(<dynamic>[]));
      await tester.pump();
      await _disposeDashboard(tester);
    },
  );

  testWidgets(
    'leveringslijst en wachtrijcategorie hebben onafhankelijke 5/+10-tellers die refresh overleven',
    (tester) async {
      var candidateCalls = 0;
      var deliveryCalls = 0;
      List<dynamic> candidatesFor(int total) => [
        for (var id = 1; id <= total; id++) _candidate(id),
      ];
      List<dynamic> deliveriesFor(int total) => [
        for (var id = 1; id <= total; id++) _delivery(id),
      ];
      final client = _client(
        candidates: () async {
          candidateCalls++;
          return _json(candidatesFor(candidateCalls == 1 ? 16 : 18));
        },
        deliveries: () async {
          deliveryCalls++;
          return _json(deliveriesFor(deliveryCalls == 1 ? 16 : 18));
        },
      );
      await _pumpDashboard(tester, client);
      await _openManagement(tester);

      expect(find.byType(SoftwareFactoryDeliveryTile), findsNWidgets(5));
      expect(find.text('Kandidaat 16'), findsOneWidget);
      expect(find.text('Kandidaat 11'), findsNothing);
      expect(find.text('Meer (nog 11)'), findsNWidgets(2));

      await tester.tap(find.text('Meer (nog 11)').at(0));
      await tester.pump();
      expect(find.byType(SoftwareFactoryDeliveryTile), findsNWidgets(15));
      expect(find.text('Kandidaat 11'), findsNothing);
      expect(find.text('Meer (nog 1)'), findsOneWidget);
      expect(find.text('Meer (nog 11)'), findsOneWidget);

      await tester.tap(find.text('Meer (nog 11)'));
      await tester.pump();
      expect(find.text('Kandidaat 2'), findsOneWidget);
      expect(find.text('Kandidaat 1'), findsNothing);
      expect(find.text('Meer (nog 1)'), findsNWidgets(2));

      await tester.pump(const Duration(seconds: 5));
      for (var pump = 0; pump < 5; pump++) {
        await tester.pump();
      }

      expect(candidateCalls, 2);
      expect(deliveryCalls, 2);
      expect(find.byType(SoftwareFactoryDeliveryTile), findsNWidgets(15));
      expect(find.text('Kandidaat 18'), findsOneWidget);
      expect(find.text('Kandidaat 4'), findsOneWidget);
      expect(find.text('Kandidaat 3'), findsNothing);
      expect(find.text('Meer (nog 3)'), findsNWidgets(2));
      await _disposeDashboard(tester);
    },
  );

  testWidgets(
    'beheerlinks volgen visuele tabvolgorde, hebben linksemantiek en expliciete focusstijl',
    (tester) async {
      final semantics = tester.ensureSemantics();
      final client = _client();
      await _pumpDashboard(tester, client);

      Finder link = find.byType(DashboardNavigationLink);
      expect(tester.getSemantics(link).flagsCollection.isLink, isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_containsPrimaryFocus(tester, link), isTrue);
      final mainTextButton = tester.widget<TextButton>(
        find.descendant(of: link, matching: find.byType(TextButton)),
      );
      expect(
        mainTextButton.style?.side?.resolve({WidgetState.focused})?.width,
        3,
      );

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      link = find.byType(DashboardNavigationLink);
      expect(find.text('Terug naar overzicht'), findsOneWidget);
      expect(tester.getSemantics(link).flagsCollection.isLink, isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_containsPrimaryFocus(tester, link), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      for (var pump = 0; pump < 3; pump++) {
        await tester.pump();
      }
      expect(find.text('Productoverzicht'), findsOneWidget);
      semantics.dispose();
      await _disposeDashboard(tester);
    },
  );

  testWidgets(
    'Beheer blijft bruikbaar op 320 pixels en 200% tekst met lange records en Meer-acties',
    (tester) async {
      final longText = List.filled(
        2,
        'zeer lange tekst die zonder horizontale pagina-scroll moet teruglopen',
      ).join(' ');
      final candidates = [
        for (var id = 1; id <= 6; id++)
          _candidate(
            id,
            title: 'Kandidaat $id $longText',
            description: longText,
          ),
      ];
      final deliveries = [
        for (var id = 1; id <= 6; id++)
          _delivery(id, title: 'Levering $id $longText'),
      ];
      final client = _client(
        candidates: () async => _json(candidates),
        deliveries: () async => _json(deliveries),
      );
      await _pumpDashboard(
        tester,
        client,
        size: const Size(320, 12000),
        textScale: 2,
      );
      await _openManagement(tester);

      expect(tester.takeException(), isNull);
      expect(find.text('Terug naar overzicht'), findsOneWidget);
      expect(find.byType(SoftwareFactoryDeliveryTile), findsNWidgets(5));
      expect(find.text('Meer (nog 1)'), findsNWidgets(2));
      expect(
        find.textContaining('Levering 6 zeer lange tekst'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Kandidaat 6 zeer lange tekst'),
        findsOneWidget,
      );

      await tester.tap(find.text('Meer (nog 1)').at(0));
      await tester.pump();
      await tester.ensureVisible(find.text('Meer (nog 1)'));
      await tester.pump();
      await tester.tap(find.text('Meer (nog 1)'));
      await tester.pump();
      expect(find.byType(SoftwareFactoryDeliveryTile), findsNWidgets(6));
      expect(find.text('Meer (nog 1)'), findsNothing);
      expect(tester.takeException(), isNull);

      await tester.ensureVisible(
        find.textContaining('Kandidaat 6 zeer lange tekst'),
      );
      await tester.pump();
      await tester.tap(find.textContaining('Kandidaat 6 zeer lange tekst'));
      await tester.pumpAndSettle();
      expect(find.text('Sluiten'), findsOneWidget);
      expect(find.textContaining('zeer lange tekst'), findsWidgets);
      expect(tester.takeException(), isNull);
      await tester.tap(find.text('Sluiten'));
      await tester.pumpAndSettle();
      await _disposeDashboard(tester);
    },
  );

  testWidgets(
    'laad- en foutmeldingen blijven op 320 pixels en 200% tekst volledig bereikbaar',
    (tester) async {
      final pendingCandidates = Completer<http.Response>();
      final client = _client(
        candidates: () => pendingCandidates.future,
        deliveries: () async => _json({'error': 'stuk'}, status: 500),
      );
      await _pumpDashboard(
        tester,
        client,
        size: const Size(320, 4000),
        textScale: 2,
      );
      await _openManagement(tester);

      expect(
        find.text('Software Factory-leveringen zijn niet beschikbaar.'),
        findsOneWidget,
      );
      expect(
        find.text('Storykandidaten voor de storywachtrij worden geladen.'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);

      pendingCandidates.complete(_json([_candidate(1)]));
      await tester.pump();
      await tester.pump();
      expect(
        find.text(
          '1 storykandidaten geladen. Storywachtrij is onvolledig omdat Software Factory-leveringen niet beschikbaar zijn.',
        ),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
      await _disposeDashboard(tester);
    },
  );
}
