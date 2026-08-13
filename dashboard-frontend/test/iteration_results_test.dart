import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/iteration_results.dart';

Map<String, dynamic> _iteration(String id, String product, int sequence) => {
  'id': id,
  'productSlug': product,
  'sequenceNumber': sequence,
  'title': 'Niet gebruiken als koppelsleutel',
};

Map<String, dynamic> _candidate(
  int id,
  String product,
  Object? sequence, {
  String title = 'Zelfde titel',
}) => {
  'id': id,
  'productSlug': product,
  'iterationSequenceNumber': sequence,
  'title': title,
  'status': 'ACCEPTED',
};

Map<String, dynamic> _delivery(
  int candidateId,
  String product,
  Object? iterationId,
) => {
  'candidateId': candidateId,
  'productSlug': product,
  'iterationId': iterationId,
  'title': 'Zelfde titel',
  'status': 'DONE',
};

void main() {
  test(
    'koppelt kandidaten op product en cyclusnummer en leveringen op product en cyclus-id',
    () {
      final first = _iteration('iter-a-1', 'alpha', 1);
      final second = _iteration('iter-b-1', 'beta', 1);
      final candidateA = _candidate(10, 'alpha', 1);
      final candidateB = _candidate(11, 'beta', 1);
      final deliveryA = _delivery(11, 'alpha', 'iter-a-1');
      final deliveryB = _delivery(10, 'beta', 'iter-b-1');

      final result = groupIterationResults(
        iterations: [first, second],
        candidates: [candidateA, candidateB],
        deliveries: [deliveryA, deliveryB],
      );

      expect(result.resultsFor(first).candidates, [same(candidateA)]);
      expect(result.resultsFor(first).deliveries, [same(deliveryA)]);
      expect(result.resultsFor(second).candidates, [same(candidateB)]);
      expect(result.resultsFor(second).deliveries, [same(deliveryB)]);
      expect(result.unlinkedCount, 0);
    },
  );

  test(
    'ontbrekende, anders getypeerde en kruisproductrelaties blijven niet-koppelbaar',
    () {
      final iteration = _iteration('iter-1', 'alpha', 1);
      final candidates = <dynamic>[
        _candidate(1, 'alpha', null),
        _candidate(2, 'alpha', '1'),
        _candidate(3, 'beta', 1),
        {'id': 4, 'iterationSequenceNumber': 1},
        'ongeldig kandidaatrecord',
      ];
      final deliveries = <dynamic>[
        _delivery(1, 'alpha', null),
        _delivery(2, 'alpha', 1),
        _delivery(3, 'beta', 'iter-1'),
        {'candidateId': 4, 'iterationId': 'iter-1'},
        'ongeldig leveringsrecord',
      ];

      final result = groupIterationResults(
        iterations: [iteration],
        candidates: candidates,
        deliveries: deliveries,
      );

      expect(result.resultsFor(iteration).candidates, isEmpty);
      expect(result.resultsFor(iteration).deliveries, isEmpty);
      expect(result.unlinkedCandidates, candidates);
      expect(result.unlinkedDeliveries, deliveries);
      expect(result.unlinkedCount, 10);
    },
  );

  test(
    'dubbele geldige cyclesleutels maken uitsluitend de betreffende relatie ambigu',
    () {
      final first = _iteration('duplicate-id', 'alpha', 7);
      final second = _iteration('duplicate-id', 'alpha', 7);
      final candidate = _candidate(1, 'alpha', 7);
      final delivery = _delivery(1, 'alpha', 'duplicate-id');

      final result = groupIterationResults(
        iterations: [first, second],
        candidates: [candidate],
        deliveries: [delivery],
      );

      for (final group in result.iterations) {
        expect(group.candidates, isEmpty);
        expect(group.deliveries, isEmpty);
      }
      expect(result.unlinkedCandidates, [same(candidate)]);
      expect(result.unlinkedDeliveries, [same(delivery)]);
    },
  );

  test(
    'titel, kandidaat-id, lijstpositie en ongerelateerde waarschijnlijkheidsvelden zijn nooit fallback',
    () {
      final iteration = _iteration('iter-9', 'alpha', 9);
      final candidate = {
        ..._candidate(
          42,
          'alpha',
          8,
          title: 'Niet gebruiken als koppelsleutel',
        ),
        'sequenceNumber': 9,
        'iterationId': 'iter-9',
        'probability': 1.0,
      };
      final delivery = {
        ..._delivery(42, 'alpha', 'iter-8'),
        'sequenceNumber': 9,
        'probability': 1.0,
      };

      final result = groupIterationResults(
        iterations: [iteration],
        candidates: [candidate],
        deliveries: [delivery],
      );

      expect(result.resultsFor(iteration).candidates, isEmpty);
      expect(result.resultsFor(iteration).deliveries, isEmpty);
      expect(result.unlinkedCount, 2);
    },
  );

  test(
    'ieder geladen record komt exact eenmaal in één groep of de niet-koppelbare categorie',
    () {
      final iterations = [
        _iteration('iter-a-1', 'alpha', 1),
        _iteration('iter-a-2', 'alpha', 2),
      ];
      final candidates = [
        _candidate(1, 'alpha', 1),
        _candidate(2, 'alpha', 2),
        _candidate(3, 'alpha', 99),
      ];
      final deliveries = [
        _delivery(1, 'alpha', 'iter-a-1'),
        _delivery(2, 'alpha', 'iter-a-2'),
        _delivery(3, 'alpha', 'missing'),
      ];

      final result = groupIterationResults(
        iterations: iterations,
        candidates: candidates,
        deliveries: deliveries,
      );

      final countedCandidates =
          result.iterations.fold<int>(
            0,
            (sum, group) => sum + group.candidates.length,
          ) +
          result.unlinkedCandidates.length;
      final countedDeliveries =
          result.iterations.fold<int>(
            0,
            (sum, group) => sum + group.deliveries.length,
          ) +
          result.unlinkedDeliveries.length;
      expect(countedCandidates, candidates.length);
      expect(countedDeliveries, deliveries.length);
      expect(
        result.iterations.expand((group) => group.candidates).toSet().length,
        2,
      );
      expect(
        result.iterations.expand((group) => group.deliveries).toSet().length,
        2,
      );
    },
  );
}
