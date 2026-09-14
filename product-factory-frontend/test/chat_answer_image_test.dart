import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_frontend/chat_answer_image.dart';

void main() {
  final png = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==',
  );
  testWidgets('screenshot op smal scherm vergroot met bron en downloadactie', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 850));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: ChatAnswerImage(
              image: const {
                'id': 'image-1',
                'kind': 'SCREENSHOT',
                'environment': 'PRODUCTION',
                'caption': 'Het publieke homescherm',
                'sourceUrl': 'https://example.test/',
                'filename': 'chat-image-01.png',
              },
              bytes: Future.value(png),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Screenshot · Productie'), findsOneWidget);
    expect(find.text('Bron: https://example.test/'), findsOneWidget);
    await tester.tap(find.text('Vergroten'));
    await tester.pumpAndSettle();
    expect(find.byType(InteractiveViewer), findsOneWidget);
    expect(find.byTooltip('Afbeelding downloaden'), findsOneWidget);
    await tester.tap(find.byTooltip('Sluiten'));
    await tester.pumpAndSettle();
    expect(find.byType(Dialog), findsNothing);
    expect(tester.takeException(), isNull);
  });
  testWidgets('ontwerp wordt niet als screenshot gepresenteerd', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ChatAnswerImage(
            image: const {
              'kind': 'DESIGN',
              'caption': 'Een mogelijke indeling',
              'filename': 'chat-image-02.png',
            },
            bytes: Future.value(png),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Ontwerpschets'), findsOneWidget);
    expect(find.textContaining('Screenshot'), findsNothing);
    expect(find.textContaining('Bron:'), findsNothing);
  });
}
