library;

/// De gekoppelde opbrengst van één geladen cyclus. [iteration] is exact hetzelfde
/// map-object als in de invoerlijst, zodat ook onverwachte dubbele ids defensief
/// als afzonderlijke cycli behandeld kunnen worden.
class IterationLinkedResults {
  IterationLinkedResults(this.iteration);

  final Map<String, dynamic> iteration;
  final List<Map<String, dynamic>> candidates = [];
  final List<Map<String, dynamic>> deliveries = [];
}

/// Volledige, verliesvrije verdeling van de geladen opbrengst.
class IterationResultsGrouping {
  IterationResultsGrouping({
    required this.iterations,
    required this.unlinkedCandidates,
    required this.unlinkedDeliveries,
  });

  final List<IterationLinkedResults> iterations;
  final List<dynamic> unlinkedCandidates;
  final List<dynamic> unlinkedDeliveries;

  int get unlinkedCount =>
      unlinkedCandidates.length + unlinkedDeliveries.length;

  IterationLinkedResults resultsFor(Map<String, dynamic> iteration) =>
      iterations.singleWhere(
        (result) => identical(result.iteration, iteration),
      );
}

/// Koppelt uitsluitend via de twee contractueel toegestane relaties:
///
/// - kandidaat: exact `productSlug` + integer `iterationSequenceNumber`;
/// - levering: exact `productSlug` + string `iterationId`.
///
/// Een relatie is alleen geldig als zij precies één geladen cyclus aanwijst. Er
/// wordt bewust niet genormaliseerd en er is geen fallback op titel, id van een
/// kandidaat, positie, volgorde of waarschijnlijkheid.
IterationResultsGrouping groupIterationResults({
  required List<dynamic> iterations,
  required List<dynamic> candidates,
  required List<dynamic> deliveries,
}) {
  final groups = <IterationLinkedResults>[];
  final candidatesByKey = <(String, int), List<IterationLinkedResults>>{};
  final deliveriesByKey = <(String, String), List<IterationLinkedResults>>{};

  for (final value in iterations) {
    if (value is! Map<String, dynamic>) continue;
    final group = IterationLinkedResults(value);
    groups.add(group);

    final productSlug = value['productSlug'];
    final sequenceNumber = value['sequenceNumber'];
    if (productSlug is String &&
        productSlug.isNotEmpty &&
        sequenceNumber is int) {
      candidatesByKey
          .putIfAbsent((productSlug, sequenceNumber), () => [])
          .add(group);
    }

    final iterationId = value['id'];
    if (productSlug is String &&
        productSlug.isNotEmpty &&
        iterationId is String &&
        iterationId.isNotEmpty) {
      deliveriesByKey
          .putIfAbsent((productSlug, iterationId), () => [])
          .add(group);
    }
  }

  final unlinkedCandidates = <dynamic>[];
  for (final value in candidates) {
    if (value is! Map<String, dynamic>) {
      unlinkedCandidates.add(value);
      continue;
    }
    final productSlug = value['productSlug'];
    final sequenceNumber = value['iterationSequenceNumber'];
    final matches =
        productSlug is String && productSlug.isNotEmpty && sequenceNumber is int
        ? candidatesByKey[(productSlug, sequenceNumber)]
        : null;
    if (matches?.length == 1) {
      matches!.single.candidates.add(value);
    } else {
      unlinkedCandidates.add(value);
    }
  }

  final unlinkedDeliveries = <dynamic>[];
  for (final value in deliveries) {
    if (value is! Map<String, dynamic>) {
      unlinkedDeliveries.add(value);
      continue;
    }
    final productSlug = value['productSlug'];
    final iterationId = value['iterationId'];
    final matches =
        productSlug is String &&
            productSlug.isNotEmpty &&
            iterationId is String &&
            iterationId.isNotEmpty
        ? deliveriesByKey[(productSlug, iterationId)]
        : null;
    if (matches?.length == 1) {
      matches!.single.deliveries.add(value);
    } else {
      unlinkedDeliveries.add(value);
    }
  }

  return IterationResultsGrouping(
    iterations: groups,
    unlinkedCandidates: unlinkedCandidates,
    unlinkedDeliveries: unlinkedDeliveries,
  );
}
