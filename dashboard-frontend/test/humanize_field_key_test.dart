import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/main.dart';

/// Unit tests voor product-91: `humanizeFieldKey` zet een JSON-veldnaam om naar een leesbaar
/// label. Puur/side-effectvrij, nog niet gekoppeld aan `_readableArtifactFields`/`_roleLabel`
/// (main.dart, regel ~1143 resp. ~1403) — dat is scope van een vervolgstory.
void main() {
  group('humanizeFieldKey - bekende sleutels', () {
    for (final key in ['findings', 'decision', 'story', 'verdict', 'reason']) {
      test('"$key" levert een vast, leesbaar label op', () {
        final label = humanizeFieldKey(key);
        expect(label, isNot(equals(key)));
        expect(label.contains('_'), isFalse);
        expect(RegExp(r'[a-z][A-Z]').hasMatch(label), isFalse);
      });
    }
  });

  test(
    'onbekende snake_case-sleutel wordt spatie-gescheiden met hoofdletters',
    () {
      expect(humanizeFieldKey('source_urls'), 'Source Urls');
    },
  );

  test(
    'onbekende camelCase-sleutel wordt spatie-gescheiden met hoofdletters',
    () {
      expect(humanizeFieldKey('unexpectedFieldName'), 'Unexpected Field Name');
    },
  );

  test('herhaalde aanroep met dezelfde invoer levert identieke uitvoer', () {
    for (final key in [
      'findings',
      'source_urls',
      'unexpectedFieldName',
      'reason',
    ]) {
      final first = humanizeFieldKey(key);
      final second = humanizeFieldKey(key);
      expect(second, equals(first));
    }
  });
}
