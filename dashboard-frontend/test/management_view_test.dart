import 'dart:async';
import 'dart:convert';
import 'dart:ui' show SemanticsAction, SemanticsActionEvent;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:product_factory_dashboard/limited_list.dart';
import 'package:product_factory_dashboard/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

Map<String, dynamic> _product() => {
  'slug': 'demo',
  'name': 'Demo product voor regressie',
  'status': 'active',
  'mission': 'De bestaande productinstellingen blijven bereikbaar.',
  'softwareFactoryProjectKey': 'DEMO',
  'targetRepositoryName': 'factory/demo',
  'workspaceOwnership': 'product-factory',
  'developmentMode': 'autonomous',
  'maxStoriesPerCycle': 3,
  'wipLimit': 2,
  'aiProvider': 'openai',
  'aiModel': 'test-model',
  'iterationTimes': ['03:00'],
  'meetingRequestedTopics': <String>[],
  'meetingRequestedAt': null,
};

Map<String, dynamic> _closedIteration() => {
  'id': 'iteration-regression',
  'productSlug': 'demo',
  'sequenceNumber': 7,
  'status': 'ACCEPTED',
  'mode': 'autonomous',
  'currentRole': null,
  'candidateCount': 1,
  'acceptedCandidateCount': 1,
  'revisionRounds': 0,
  'criticVerdict': 'ACCEPT',
  'outcomeReason': 'ACCEPT',
  'startedAt': '2026-08-12T10:00:00Z',
  'completedAt': '2026-08-12T10:05:00Z',
};

MockClient _client({
  Future<http.Response> Function()? candidates,
  Future<http.Response> Function()? deliveries,
  Future<http.Response?> Function(http.Request request)? override,
  List<String>? calls,
  List<dynamic> products = const [],
  List<dynamic> humanActions = const [],
  List<dynamic> settledQuestions = const [],
}) {
  return MockClient((request) async {
    calls?.add(request.url.path);
    final overridden = await override?.call(request);
    if (overridden != null) return overridden;
    switch (request.url.path) {
      case '/api/products':
        return _json(products);
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
  setUp(() => SharedPreferences.setMockInitialValues({}));

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
          .getTopLeft(find.text('Software Factory-stories — Alle producten'))
          .dy;
      final queueHeadingY = tester
          .getTopLeft(find.text('Storywachtrij — Alle producten'))
          .dy;
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

  testWidgets(
    'hoofdscherm behoudt metrieken, productactie en terminale bewijsregel',
    (tester) async {
      final requests = <String>[];
      final candidate = _candidate(1, title: 'Gekoppelde cyclusopbrengst')
        ..['iterationSequenceNumber'] = 7;
      final delivery = _delivery(1)..['iterationId'] = 'iteration-regression';
      final client = _client(
        candidates: () async => _json([candidate]),
        deliveries: () async => _json([delivery]),
        override: (request) async {
          requests.add('${request.method} ${request.url.path}');
          switch (request.url.path) {
            case '/api/products':
              return _json([_product()]);
            case '/api/ai-catalog':
              return _json({
                'openai': ['test-model'],
              });
            case '/api/shadow-iterations':
              return _json([_closedIteration()]);
            case '/api/products/demo/cycles':
              return _json(<String, dynamic>{}, status: 202);
          }
          return null;
        },
      );

      await http.runWithClient(() async {
        await _pumpDashboard(tester, client);

        const metricValues = {
          'Producten': '1',
          'Interne storykandidaten': '1',
          'Workspace-publicaties': '0',
          'Shadow-iteraties': '1',
          'Software Factory-stories': '1',
        };
        for (final entry in metricValues.entries) {
          final card = find.ancestor(
            of: find.text(entry.key),
            matching: find.byType(MetricCard),
          );
          expect(card, findsOneWidget);
          expect(
            find.descendant(of: card, matching: find.text(entry.value)),
            findsOneWidget,
          );
        }
        expect(
          find.byKey(const ValueKey('active-product-name')),
          findsOneWidget,
        );

        await tester.tap(find.byType(SettingsButton));
        await tester.pumpAndSettle();
        expect(
          find.text('De bestaande productinstellingen blijven bereikbaar.'),
          findsOneWidget,
        );
        await tester.tap(find.text('Annuleren'));
        await tester.pumpAndSettle();

        final cycleButton = find.byType(StartCycleButton);
        expect(cycleButton, findsOneWidget);
        expect(
          tester
              .widget<FilledButton>(
                find.descendant(
                  of: cycleButton,
                  matching: find.byType(FilledButton),
                ),
              )
              .onPressed,
          isNotNull,
        );
        await tester.tap(cycleButton);
        await tester.pumpAndSettle();
        expect(find.byType(ManualCycleStartDialog), findsOneWidget);
        await tester.tap(find.widgetWithText(FilledButton, 'Cyclus starten'));
        for (var pump = 0; pump < 4; pump++) {
          await tester.pump();
        }
        expect(requests, contains('POST /api/products/demo/cycles'));

        expect(find.byType(IterationEvidenceRow), findsOneWidget);
        expect(
          find.text('Cyclusuitkomst: richting-gekozen', findRichText: true),
          findsOneWidget,
        );
        expect(
          find.text(
            'Reden: Alle kandidaten zijn leverbaar',
            findRichText: true,
          ),
          findsOneWidget,
        );
        expect(
          find.text(
            'Beslisbron: Evaluatie-agent (Afgeleid)',
            findRichText: true,
          ),
          findsOneWidget,
        );
        expect(
          find.textContaining('Gekoppelde opbrengst:', findRichText: true),
          findsOneWidget,
        );
        expect(find.text('Bekijk cyclusdetail'), findsOneWidget);
        expect(find.text('Toon opbrengst'), findsNothing);
        await _disposeDashboard(tester);
      }, () => client);
    },
  );

  testWidgets(
    'hoofdscherm behoudt roadmap, vragen, sessies, overleggen, token- en workspace-acties',
    (tester) async {
      final requests = <String>[];
      final client = _client(
        override: (request) async {
          requests.add('${request.method} ${request.url.path}');
          switch (request.url.path) {
            case '/api/products':
              return _json([_product()]);
            case '/api/ai-catalog':
              return _json({
                'openai': ['test-model'],
              });
            case '/api/workspace/publications':
              return _json([
                {
                  'runId': 'publication-run',
                  'productSlug': 'demo',
                  'artifactPath': 'dossiers/behouden.md',
                  'status': 'PUBLISHED',
                },
              ]);
            case '/api/autonomy/human-actions':
              return _json([
                {
                  'id': 9,
                  'title': 'Tokenactie functioneel behouden',
                  'category': 'TOKEN',
                  'reason': 'Integratie wacht op configuratie',
                  'status': 'OPEN',
                  'createdAt': '2026-08-12T09:00:00Z',
                },
              ]);
            case '/api/roadmap/epics':
              return _json([
                {
                  'id': 'epic-1',
                  'productSlug': 'demo',
                  'title': 'Roadmap-epic functioneel behouden',
                  'description': 'Details van de behouden epic',
                  'status': 'OPEN',
                  'customerRank': 1,
                  'processRank': 1,
                  'priorityScore': 100,
                  'roadmapRank': 1,
                  'dependencyIds': <String>[],
                  'blockedByIds': <String>[],
                },
              ]);
            case '/api/roadmap/settled-questions':
              return _json([
                {
                  'productSlug': 'demo',
                  'content': 'Behouden onderzoeksvraag met antwoord',
                  'createdAt': '2026-08-12T08:00:00Z',
                },
              ]);
            case '/api/roadmap/sessions':
              return _json([
                {
                  'productSlug': 'demo',
                  'sequenceNumber': 3,
                  'status': 'COMPLETED',
                  'summary': 'Behouden roadmapsessieresultaat',
                  'workspaceRunId': 'roadmap-run',
                  'completedAt': '2026-08-12T07:00:00Z',
                },
              ]);
            case '/api/meetings':
              return _json([
                {
                  'id': 'meeting-1',
                  'productSlug': 'demo',
                  'sequenceNumber': 4,
                  'status': 'CLOSED',
                  'initiator': 'owner',
                  'outcomeSummary': 'Behouden overlegresultaat',
                  'workspaceRunId': 'meeting-run',
                  'closedAt': '2026-08-12T06:00:00Z',
                },
              ]);
            case '/api/products/demo/meetings/meeting-1':
              return _json({
                'id': 'meeting-1',
                'sequenceNumber': 4,
                'status': 'CLOSED',
                'initiator': 'owner',
                'requestedTopics': <String>[],
                'outcomeSummary': 'Behouden overlegresultaat',
                'workspaceRunId': 'meeting-run',
              });
            case '/api/products/demo/meetings/meeting-1/messages':
              return _json([
                {
                  'sender': 'owner',
                  'content': 'Bewaard overlegbericht',
                  'createdAt': '2026-08-12T06:00:00Z',
                },
              ]);
            case '/api/products/demo/roadmap/epics/epic-1/verifications':
              return _json(<dynamic>[]);
            case '/api/autonomy/human-actions/9/complete':
              return _json(<String, dynamic>{});
          }
          if (request.url.path.startsWith('/api/workspace/publications/') &&
              request.url.path.endsWith('/artifact')) {
            return http.Response('Inhoud van behouden publicatie', 200);
          }
          return null;
        },
      );

      await http.runWithClient(() async {
        await _pumpDashboard(tester, client);

        expect(find.text('Roadmap-epic functioneel behouden'), findsOneWidget);
        await tester.tap(find.text('Roadmap-epic functioneel behouden'));
        await tester.pumpAndSettle();
        expect(find.text('Epicdetails'), findsOneWidget);
        expect(find.text('Details van de behouden epic'), findsOneWidget);
        await tester.tap(find.text('Annuleren'));
        await tester.pumpAndSettle();

        expect(
          find.text('demo · Behouden onderzoeksvraag met antwoord'),
          findsOneWidget,
        );
        expect(find.text('demo · roadmap-sessie 3'), findsOneWidget);
        expect(
          find.textContaining('Behouden roadmapsessieresultaat'),
          findsOneWidget,
        );
        await tester.tap(find.byTooltip('Verslag bekijken'));
        await tester.pumpAndSettle();
        expect(find.text('Inhoud van behouden publicatie'), findsOneWidget);
        await tester.tap(find.text('Sluiten'));
        await tester.pumpAndSettle();

        expect(find.text('demo · overleg 4'), findsOneWidget);
        await tester.tap(find.text('demo · overleg 4'));
        await tester.pumpAndSettle();
        expect(find.text('Bewaard overlegbericht'), findsOneWidget);
        await tester.tap(find.text('Sluiten'));
        await tester.pumpAndSettle();

        expect(find.text('Tokenactie functioneel behouden'), findsOneWidget);
        await tester.tap(find.widgetWithText(FilledButton, 'Gereed melden'));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byType(TextField),
          'Configuratie is buiten het dashboard afgerond',
        );
        await tester.tap(
          find.descendant(
            of: find.byType(AlertDialog),
            matching: find.widgetWithText(FilledButton, 'Gereed melden'),
          ),
        );
        for (var pump = 0; pump < 4; pump++) {
          await tester.pump();
        }
        expect(
          requests,
          contains('POST /api/autonomy/human-actions/9/complete'),
        );

        expect(find.text('dossiers/behouden.md'), findsOneWidget);
        await tester.tap(find.text('dossiers/behouden.md'));
        await tester.pumpAndSettle();
        expect(find.text('Inhoud van behouden publicatie'), findsOneWidget);
        await tester.tap(find.text('Sluiten'));
        await tester.pumpAndSettle();
        await _disposeDashboard(tester);
      }, () => client);
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

  testWidgets(
    'productspecifieke leveringen blijven laden totdat kandidaten geladen zijn',
    (tester) async {
      final pendingCandidates = Completer<http.Response>();
      final client = _client(
        products: [_product()],
        candidates: () => pendingCandidates.future,
        deliveries: () async => _json([_delivery(1)]),
      );
      await _pumpDashboard(tester, client);
      await _openManagement(tester);

      expect(
        find.text(
          'Software Factory-leveringen voor deze productscope worden bepaald zodra storykandidaten zijn geladen.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('PRODUCT-1'), findsNothing);
      expect(
        find.text('Nog geen stories naar de Software Factory gestuurd'),
        findsNothing,
      );

      pendingCandidates.complete(_json([_candidate(1)]));
      await tester.pump();
      await tester.pump();
      expect(find.textContaining('PRODUCT-1'), findsWidgets);
      await _disposeDashboard(tester);
    },
  );

  testWidgets(
    'productspecifieke leveringen tonen kandidaatfout niet als lege lijst',
    (tester) async {
      final client = _client(
        products: [_product()],
        candidates: () async => _json({'error': 'stuk'}, status: 500),
        deliveries: () async => _json([_delivery(1)]),
      );
      await _pumpDashboard(tester, client);
      await _openManagement(tester);

      expect(
        find.text(
          'Software Factory-leveringen voor deze productscope zijn niet beschikbaar omdat storykandidaten niet beschikbaar zijn.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('PRODUCT-1'), findsNothing);
      expect(
        find.text('Nog geen stories naar de Software Factory gestuurd'),
        findsNothing,
      );
      await _disposeDashboard(tester);
    },
  );

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
    'leveringslijst en alle vier wachtrijcategorieën hebben onafhankelijke 5/+10-tellers',
    (tester) async {
      var candidateCalls = 0;
      var deliveryCalls = 0;
      final candidates = <Map<String, dynamic>>[
        for (var id = 1; id <= 16; id++)
          _candidate(id, title: 'Foutkandidaat $id'),
        for (var id = 101; id <= 116; id++)
          _candidate(id, title: 'Bezigkandidaat $id'),
        for (var id = 201; id <= 216; id++)
          _candidate(id, title: 'Wachtrijkandidaat $id'),
        for (var id = 301; id <= 316; id++)
          _candidate(id, title: 'Klare kandidaat $id'),
      ];
      final deliveries = <Map<String, dynamic>>[
        for (var id = 1; id <= 16; id++) _delivery(id, status: 'ERROR'),
        for (var id = 101; id <= 116; id++) _delivery(id, status: 'RUNNING'),
        for (var id = 301; id <= 316; id++) _delivery(id, status: 'DONE'),
      ];
      final client = _client(
        candidates: () async {
          candidateCalls++;
          return _json(candidates);
        },
        deliveries: () async {
          deliveryCalls++;
          return _json(deliveries);
        },
      );
      await _pumpDashboard(tester, client);
      await _openManagement(tester);

      expect(find.text('Fout (16)'), findsOneWidget);
      expect(find.text('Bezig (16)'), findsOneWidget);
      expect(find.text('In wachtrij (16)'), findsOneWidget);
      expect(find.text('Klaar (16)'), findsOneWidget);
      expect(find.byType(LimitedListSection), findsNWidgets(5));

      List<int> visibleCounts() => find
          .byType(LimitedListSection)
          .evaluate()
          .map((element) => (element.widget as LimitedListSection).visibleCount)
          .toList();

      // Volgorde: leveringen, Fout, Bezig, In wachtrij, Klaar. Na elke
      // actie verandert uitsluitend de teller van de aangeklikte lijst.
      expect(visibleCounts(), [5, 5, 5, 5, 5]);
      final expected = List<int>.filled(5, 5);
      for (final index in [1, 3, 0, 4, 2]) {
        final section = find.byType(LimitedListSection).at(index);
        await tester.tap(
          find.descendant(of: section, matching: find.byType(TextButton)),
        );
        await tester.pump();
        expected[index] = 15;
        expect(visibleCounts(), expected);
      }
      expect(find.text('Meer (nog 1)'), findsNWidgets(4));
      expect(find.text('Meer (nog 33)'), findsOneWidget);

      await tester.pump(const Duration(seconds: 5));
      for (var pump = 0; pump < 5; pump++) {
        await tester.pump();
      }

      expect(candidateCalls, 2);
      expect(deliveryCalls, 2);
      expect(visibleCounts(), [15, 15, 15, 15, 15]);
      expect(find.byType(SoftwareFactoryDeliveryTile), findsNWidgets(15));
      expect(find.text('Meer (nog 1)'), findsNWidgets(4));
      expect(find.text('Meer (nog 33)'), findsOneWidget);
      await _disposeDashboard(tester);
    },
  );

  testWidgets(
    'beheerlinks en vervolgacties volgen op beide weergaven de volledige visuele tabvolgorde',
    (tester) async {
      final semantics = tester.ensureSemantics();
      final client = _client(
        candidates: () async => _json([_candidate(1)]),
        override: (request) async {
          switch (request.url.path) {
            case '/api/products':
              return _json([_product()]);
            case '/api/ai-catalog':
              return _json({
                'openai': ['test-model'],
              });
          }
          return null;
        },
      );
      await _pumpDashboard(tester, client);

      Finder link = find.byType(DashboardNavigationLink);
      expect(tester.getSemantics(link).flagsCollection.isLink, isTrue);
      expect(
        tester
            .getSemantics(link)
            .getSemanticsData()
            .hasAction(SemanticsAction.focus),
        isTrue,
      );
      expect(
        tester
            .getSemantics(link)
            .getSemanticsData()
            .hasAction(SemanticsAction.tap),
        isTrue,
      );
      final overviewOrder = <Finder>[
        link,
        find.widgetWithText(FilledButton, 'Product toevoegen'),
        find.byTooltip('Vernieuwen'),
        find.byType(ProductScopePicker),
        find.byType(StartCycleButton),
        find.widgetWithText(OutlinedButton, 'Pauzeren'),
        find.byType(SettingsButton),
        find.widgetWithText(OutlinedButton, 'Geheugen'),
        find.widgetWithText(OutlinedButton, 'Start overleg'),
        find.widgetWithText(OutlinedButton, 'Start roadmap-sessie nu'),
      ];
      for (final target in overviewOrder) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        expect(
          _containsPrimaryFocus(tester, target),
          isTrue,
          reason: 'Onverwachte Tab-stop in de hoofdschermvolgorde bij $target.',
        );
      }

      tester.binding.focusManager.primaryFocus?.unfocus();
      await tester.pump();
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
      final backLinkSemantics = tester.getSemantics(link);
      expect(
        backLinkSemantics.getSemanticsData().hasAction(SemanticsAction.focus),
        isTrue,
      );
      expect(
        backLinkSemantics.getSemanticsData().hasAction(SemanticsAction.tap),
        isTrue,
      );
      tester.binding.performSemanticsAction(
        SemanticsActionEvent(
          type: SemanticsAction.focus,
          nodeId: backLinkSemantics.id,
          viewId: tester.view.viewId,
        ),
      );
      await tester.pump();
      expect(_containsPrimaryFocus(tester, link), isTrue);

      final managementTextButton = tester.widget<TextButton>(
        find.descendant(of: link, matching: find.byType(TextButton)),
      );
      expect(
        managementTextButton.style?.side?.resolve({WidgetState.focused})?.width,
        3,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(
        _containsPrimaryFocus(tester, find.byType(ProductScopePicker)),
        isTrue,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      final candidateAction = find.ancestor(
        of: find.text('Kandidaat 1'),
        matching: find.byType(ListTile),
      );
      expect(_containsPrimaryFocus(tester, candidateAction), isTrue);

      tester.binding.performSemanticsAction(
        SemanticsActionEvent(
          type: SemanticsAction.focus,
          nodeId: tester.getSemantics(link).id,
          viewId: tester.view.viewId,
        ),
      );
      await tester.pump();
      expect(_containsPrimaryFocus(tester, link), isTrue);
      await tester.pump(const Duration(seconds: 5));
      for (var pump = 0; pump < 5; pump++) {
        await tester.pump();
      }
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
