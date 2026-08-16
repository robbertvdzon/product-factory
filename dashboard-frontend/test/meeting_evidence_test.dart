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
}
