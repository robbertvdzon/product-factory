import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/formatting.dart';

void main() {
  group('parseInstant', () {
    test('leest een ISO-8601-tijdstempel als lokale tijd', () {
      final parsed = parseInstant('2026-08-08T17:13:00Z');
      expect(parsed, isNotNull);
      expect(parsed!.isUtc, isFalse);
      expect(parsed.toUtc(), DateTime.utc(2026, 8, 8, 17, 13));
    });

    test('geeft null bij ontbrekende of onbruikbare waarden', () {
      expect(parseInstant(null), isNull);
      expect(parseInstant(''), isNull);
      expect(parseInstant('   '), isNull);
      expect(parseInstant('geen datum'), isNull);
      expect(parseInstant(42), isNull);
    });
  });

  group('formatDateTime', () {
    test('gebruikt het vaste formaat dd-MM-yyyy HH:mm', () {
      final local = DateTime(2026, 8, 8, 19, 13);
      expect(formatDateTime(local.toIso8601String()), '08-08-2026 19:13');
    });

    test('vult enkele cijfers aan met een nul', () {
      final local = DateTime(2026, 1, 2, 3, 4);
      expect(formatDateTime(local.toIso8601String()), '02-01-2026 03:04');
    });

    test('valt terug op een streepje bij een lege waarde', () {
      expect(formatDateTime(null), '—');
      expect(formatDateTime('onzin', fallback: 'onbekend'), 'onbekend');
    });
  });

  group('formatDuration', () {
    test('toont uren en minuten', () {
      expect(formatDuration(const Duration(hours: 2, minutes: 13)), '2u 13m');
      expect(
        formatDuration(const Duration(hours: 1, minutes: 0, seconds: 59)),
        '1u 0m',
      );
    });

    test('toont minuten en seconden', () {
      expect(formatDuration(const Duration(minutes: 4, seconds: 12)), '4m 12s');
    });

    test('toont alleen seconden onder een minuut', () {
      expect(formatDuration(const Duration(seconds: 35)), '35s');
      expect(formatDuration(Duration.zero), '0s');
    });

    test('behandelt een negatieve duur als nul', () {
      expect(formatDuration(const Duration(seconds: -20)), '0s');
    });
  });

  group('iterationTiming', () {
    final now = DateTime(2026, 8, 8, 20, 0);

    test('toont start en doorlooptijd van een afgeronde cyclus', () {
      final timing = iterationTiming({
        'createdAt': DateTime(2026, 8, 8, 18, 55).toIso8601String(),
        'startedAt': DateTime(2026, 8, 8, 19, 0).toIso8601String(),
        'completedAt': DateTime(2026, 8, 8, 21, 13).toIso8601String(),
      }, now: now);
      expect(timing.startLabel, '08-08-2026 19:00');
      expect(timing.durationLabel, '2u 13m');
    });

    test(
      'toont een lopende cyclus als "loopt nog" met de tijd sinds start',
      () {
        final timing = iterationTiming({
          'createdAt': DateTime(2026, 8, 8, 19, 50).toIso8601String(),
          'startedAt': DateTime(2026, 8, 8, 19, 55, 48).toIso8601String(),
          'completedAt': null,
        }, now: now);
        expect(timing.startLabel, '08-08-2026 19:55');
        expect(timing.durationLabel, 'loopt nog: 4m 12s');
      },
    );

    test(
      'valt bij een niet-gestarte cyclus terug op createdAt zonder duur',
      () {
        final timing = iterationTiming({
          'createdAt': DateTime(2026, 8, 8, 19, 13).toIso8601String(),
          'startedAt': null,
          'completedAt': null,
        }, now: now);
        expect(timing.startLabel, '08-08-2026 19:13');
        expect(timing.durationLabel, isNull);
      },
    );

    test('blijft bruikbaar als alle tijdstempels ontbreken', () {
      final timing = iterationTiming(<String, dynamic>{}, now: now);
      expect(timing.startLabel, '—');
      expect(timing.durationLabel, isNull);
    });
  });

  group('sortedByNewestFirst', () {
    test('zet het nieuwste item vooraan', () {
      final sorted = sortedByNewestFirst(
        [
          {'id': 'oud', 'createdAt': '2026-08-01T10:00:00Z'},
          {'id': 'nieuw', 'createdAt': '2026-08-08T10:00:00Z'},
          {'id': 'midden', 'createdAt': '2026-08-04T10:00:00Z'},
        ],
        ['createdAt'],
      );
      expect(sorted.map((item) => item['id']), ['nieuw', 'midden', 'oud']);
    });

    test('gebruikt het eerste veld dat een bruikbare waarde heeft', () {
      final sorted = sortedByNewestFirst(
        [
          {
            'id': 'alleen-created',
            'startedAt': null,
            'createdAt': '2026-08-08T12:00:00Z',
          },
          {
            'id': 'gestart',
            'startedAt': '2026-08-08T13:00:00Z',
            'createdAt': '2026-08-01T09:00:00Z',
          },
        ],
        ['startedAt', 'createdAt'],
      );
      expect(sorted.map((item) => item['id']), ['gestart', 'alleen-created']);
    });

    test('zet items zonder tijdstempel achteraan', () {
      final sorted = sortedByNewestFirst(
        [
          {'id': 'zonder'},
          {'id': 'met', 'createdAt': '2026-08-01T10:00:00Z'},
        ],
        ['createdAt'],
      );
      expect(sorted.map((item) => item['id']), ['met', 'zonder']);
    });

    test('laat de bronlijst ongemoeid', () {
      final source = <dynamic>[
        {'id': 'a', 'createdAt': '2026-08-01T10:00:00Z'},
        {'id': 'b', 'createdAt': '2026-08-08T10:00:00Z'},
      ];
      sortedByNewestFirst(source, ['createdAt']);
      expect((source.first as Map<String, dynamic>)['id'], 'a');
    });
  });
}
