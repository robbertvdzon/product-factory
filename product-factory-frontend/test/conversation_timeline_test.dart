import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_frontend/conversation_timeline.dart';

void main() {
  testWidgets(
    'chat laadt paginas en behoudt leespositie en vaste invoer bij nieuwe berichten',
    (tester) async {
      var newest = 100;
      final calls = <String>[];
      Future<Map<String, Object?>> fetch({
        String? before,
        String? after,
      }) async {
        calls.add('$before/$after');
        final from = after == null
            ? (before == null ? newest - 29 : int.parse(before) - 30)
            : int.parse(after) + 1;
        final to = after == null
            ? (before == null ? newest : int.parse(before) - 1)
            : newest;
        return {
          'messages': [
            for (var i = from; i <= to; i++) {'id': '$i', 'text': 'Bericht $i'},
          ],
          'hasMore': after == null && from > 1,
        };
      }

      Widget screen(int revision) => MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 450,
            height: 600,
            child: ConversationTimeline(
              revision: revision,
              fetch: fetch,
              title: 'Gesprek',
              composer: const TextField(
                decoration: InputDecoration(labelText: 'Je bericht'),
              ),
              messageBuilder: (m) => SizedBox(
                height: (int.parse('${m['id']}') % 3 + 1) * 65.0,
                child: Text('${m['text']}'),
              ),
            ),
          ),
        ),
      );
      await tester.pumpWidget(screen(1));
      await tester.pumpAndSettle();
      expect(calls, ['null/null']);
      expect(find.text('Bericht 100'), findsOneWidget);
      expect(find.text('Bericht 1'), findsNothing);
      final composer = tester.getRect(find.byType(TextField));
      final list = tester.widget<ListView>(
        find.byKey(const ValueKey('conversation-scroll')),
      );
      final scroll = list.controller!;
      scroll.jumpTo(scroll.position.maxScrollExtent);
      await tester.pumpAndSettle();
      expect(calls, contains('71/null'));
      expect(tester.getRect(find.byType(TextField)), composer);
      final viewport = tester.getRect(
        find.byKey(const ValueKey('conversation-scroll')),
      );
      final readingLabel = find
          .textContaining('Bericht ')
          .evaluate()
          .map((e) => (e.widget as Text).data!)
          .firstWhere(
            (text) => viewport.contains(tester.getCenter(find.text(text))),
          );
      final reading = tester.getTopLeft(find.text(readingLabel));
      final anchor = scroll.position.maxScrollExtent - scroll.offset;
      newest = 101;
      await tester.pumpWidget(screen(2));
      await tester.pumpAndSettle();
      expect(calls.last, 'null/100');
      expect(find.text('Nieuw bericht ↓'), findsOneWidget);
      expect(
        scroll.position.maxScrollExtent - scroll.offset,
        closeTo(anchor, 1),
      );
      expect(tester.getRect(find.byType(TextField)), composer);
      expect(
        tester.getTopLeft(find.text(readingLabel)).dy,
        closeTo(reading.dy, 1),
      );
      await tester.tap(find.text('Nieuw bericht ↓'));
      await tester.pumpAndSettle();
      expect(scroll.offset, 0);
      expect(find.text('Bericht 101'), findsOneWidget);
    },
  );
}
