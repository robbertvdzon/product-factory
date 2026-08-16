import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/api.dart';
import 'package:product_factory_dashboard/meeting_dialog.dart';

class _FakeMeetingApi extends DashboardApi {
  const _FakeMeetingApi() : super('http://localhost', null);

  @override
  Future<Map<String, dynamic>> meeting(String slug, String id) async => {
    'id': id,
    'productSlug': slug,
    'sequenceNumber': 3,
    'initiator': 'owner',
    'status': 'OPEN',
    'requestedTopics': <String>[],
  };

  @override
  Future<List<dynamic>> meetingMessages(String slug, String id) async => [
    {
      'sender': 'ai',
      'id': 1,
      'content': 'Ik heb de productieomgeving onderzocht.',
      'createdAt': '2026-08-15T18:00:00Z',
      'consultedSources': [
        'https://product.example/zoeken',
        'oc logs deployment/runtime',
      ],
      'memoryChanges': [
        {
          'action': 'REPLACE',
          'productSlug': slug,
          'memoryId': 42,
          'title': 'Actieve databasekeuze',
          'reason': 'De eigenaar bevestigde de overstap.',
        },
      ],
      'images': [
        {
          'id': 'media-ai-1',
          'filename': 'voorstel.png',
          'altText': 'Visueel voorstel van de AI',
          'source': 'ai',
        },
      ],
    },
  ];

  @override
  Future<Uint8List> meetingImage(
    String slug,
    String mediaId,
  ) async => base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  );
}

class _AsyncMeetingApi extends DashboardApi {
  _AsyncMeetingApi() : super('http://localhost', null);

  bool sent = false;
  int pollsAfterSend = 0;

  @override
  Future<Map<String, dynamic>> meeting(String slug, String id) async => {
    'id': id,
    'productSlug': slug,
    'sequenceNumber': 4,
    'initiator': 'owner',
    'status': 'OPEN',
    'requestedTopics': <String>[],
  };

  @override
  Future<Map<String, dynamic>> sendMeetingMessage(
    String slug,
    String id,
    String content, {
    List<String> imageAssetIds = const [],
  }) async {
    sent = true;
    return {
      'id': 10,
      'sender': 'owner',
      'content': content,
      'images': <dynamic>[],
    };
  }

  @override
  Future<List<dynamic>> meetingMessages(String slug, String id) async {
    if (!sent) return [];
    pollsAfterSend += 1;
    final owner = {
      'id': 10,
      'sender': 'owner',
      'content': 'Maak een screenshotvoorstel',
      'createdAt': '2026-08-16T19:21:18Z',
      'images': <dynamic>[],
    };
    if (pollsAfterSend < 2) return [owner];
    return [
      owner,
      {
        'id': 11,
        'sender': 'ai',
        'content': 'Het screenshotvoorstel is klaar.',
        'createdAt': '2026-08-16T19:23:30Z',
        'images': <dynamic>[],
      },
    ];
  }
}

void main() {
  testWidgets(
    'overleg toont geraadpleegde bronnen en toegepaste geheugenwijzigingen',
    (tester) async {
      tester.view.physicalSize = const Size(1200, 1200);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: MeetingDialog(
              api: _FakeMeetingApi(),
              productSlug: 'demo',
              meetingId: 'meeting-demo-0003',
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Geraadpleegde bronnen (2)'), findsOneWidget);
      expect(find.text('Geheugen aangepast'), findsOneWidget);
      expect(
        find.textContaining('REPLACE: demo / Actieve databasekeuze'),
        findsOneWidget,
      );
      expect(find.bySemanticsLabel('Visueel voorstel van de AI'), findsWidgets);

      await tester.tap(find.text('Geraadpleegde bronnen (2)'));
      await tester.pumpAndSettle();
      expect(find.text('• https://product.example/zoeken'), findsOneWidget);

      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('overleg wacht via polling op een asynchrone AI-reactie', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1200, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final api = _AsyncMeetingApi();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MeetingDialog(
            api: api,
            productSlug: 'demo',
            meetingId: 'meeting-demo-0004',
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byType(TextField),
      'Maak een screenshotvoorstel',
    );
    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();
    expect(find.text('AI denkt na…'), findsOneWidget);
    expect(find.text('Het screenshotvoorstel is klaar.'), findsNothing);

    await tester.pump(const Duration(seconds: 3));
    await tester.pump();
    expect(find.text('Het screenshotvoorstel is klaar.'), findsOneWidget);
    expect(find.text('AI denkt na…'), findsNothing);

    await tester.pumpWidget(const SizedBox());
  });
}
