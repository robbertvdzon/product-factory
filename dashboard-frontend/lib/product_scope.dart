import 'package:shared_preferences/shared_preferences.dart';

const activeProductSlugPreferenceKey =
    'product-factory.dashboard.active-product-slug';

class ProductScopeSelection {
  const ProductScopeSelection({
    required this.products,
    required this.activeProduct,
    required this.removeStoredPreference,
  });

  final List<Map<String, dynamic>> products;
  final Map<String, dynamic>? activeProduct;
  final bool removeStoredPreference;

  String? get activeSlug => activeProduct?['slug'] as String?;
}

/// Selecteert alleen producten met het canonieke, niet-lege String-veld `slug`.
/// Een opgeslagen voorkeur wordt uitsluitend hersteld als die exact één product
/// aanwijst; iedere andere situatie valt terug op het eerste API-record.
ProductScopeSelection selectProductScope(
  List<dynamic> products,
  Object? preferredSlug,
) {
  final available = products
      .whereType<Map<String, dynamic>>()
      .where((product) {
        final slug = product['slug'];
        return slug is String && slug.isNotEmpty;
      })
      .toList(growable: false);
  final matches = preferredSlug is String && preferredSlug.isNotEmpty
      ? available
            .where((product) => product['slug'] == preferredSlug)
            .toList(growable: false)
      : const <Map<String, dynamic>>[];
  final active = matches.length == 1 ? matches.single : available.firstOrNull;
  return ProductScopeSelection(
    products: available,
    activeProduct: active,
    removeStoredPreference:
        preferredSlug != null && (available.isEmpty || matches.length != 1),
  );
}

List<Map<String, dynamic>> iterationsInProductScope(
  List<dynamic> iterations,
  String productSlug,
) => iterations
    .whereType<Map<String, dynamic>>()
    .where((iteration) => iteration['productSlug'] == productSlug)
    .toList(growable: false);

/// Kandidaten horen alleen bij de actieve scope wanneer zowel hun canonieke
/// slug klopt als hun integer cyclusnummer exact één cyclus in die scope vindt.
List<Map<String, dynamic>> linkedStoriesInProductScope({
  required List<dynamic> candidates,
  required List<dynamic> iterations,
  required String productSlug,
}) {
  final scopedIterations = iterationsInProductScope(iterations, productSlug);
  final sequenceCounts = <int, int>{};
  for (final iteration in scopedIterations) {
    final sequenceNumber = iteration['sequenceNumber'];
    if (sequenceNumber is int) {
      sequenceCounts.update(
        sequenceNumber,
        (count) => count + 1,
        ifAbsent: () => 1,
      );
    }
  }
  return candidates
      .whereType<Map<String, dynamic>>()
      .where((candidate) {
        final sequenceNumber = candidate['iterationSequenceNumber'];
        return candidate['productSlug'] == productSlug &&
            sequenceNumber is int &&
            sequenceCounts[sequenceNumber] == 1;
      })
      .toList(growable: false);
}

List<Map<String, dynamic>> candidatesInManagementScope(
  List<dynamic> candidates,
  String productSlug,
) => candidates
    .whereType<Map<String, dynamic>>()
    .where((candidate) => candidate['productSlug'] == productSlug)
    .toList(growable: false);

/// Een levering ontleent haar scope uitsluitend aan exact één kandidaat-id en
/// vervolgens aan de canonieke kandidaat-slug. Het leveringrecord zelf is geen
/// alternatieve bron voor productscope.
List<Map<String, dynamic>> deliveriesInManagementScope({
  required List<dynamic> deliveries,
  required List<dynamic> candidates,
  required String productSlug,
}) {
  final candidatesById = <int, List<Map<String, dynamic>>>{};
  for (final candidate in candidates.whereType<Map<String, dynamic>>()) {
    final id = candidate['id'];
    if (id is int) {
      candidatesById.putIfAbsent(id, () => []).add(candidate);
    }
  }
  return deliveries
      .whereType<Map<String, dynamic>>()
      .where((delivery) {
        final candidateId = delivery['candidateId'];
        if (candidateId is! int) return false;
        final matches = candidatesById[candidateId];
        return matches?.length == 1 &&
            matches!.single['productSlug'] == productSlug;
      })
      .toList(growable: false);
}

class ProductScopePreferences {
  const ProductScopePreferences();

  Future<Object?> read() async {
    try {
      return (await SharedPreferences.getInstance()).get(
        activeProductSlugPreferenceKey,
      );
    } catch (_) {
      // De productscope blijft bruikbaar wanneer browseropslag niet beschikbaar is.
      return null;
    }
  }

  Future<void> save(String slug) async {
    try {
      await (await SharedPreferences.getInstance()).setString(
        activeProductSlugPreferenceKey,
        slug,
      );
    } catch (_) {
      // Een opslagfout mag de lokale presentatiewissel niet terugdraaien.
    }
  }

  Future<void> remove() async {
    try {
      await (await SharedPreferences.getInstance()).remove(
        activeProductSlugPreferenceKey,
      );
    } catch (_) {
      // Zonder beschikbare browseropslag is er niets blijvends te verwijderen.
    }
  }
}
