import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/product_scope.dart';

Map<String, dynamic> _product(Object? slug, String name) => {
  'slug': slug,
  'name': name,
};

Map<String, dynamic> _iteration(Object? slug, Object? sequence) => {
  'id': 'iteration-$slug-$sequence',
  'productSlug': slug,
  'sequenceNumber': sequence,
};

Map<String, dynamic> _candidate(Object? id, Object? slug, Object? sequence) => {
  'id': id,
  'productSlug': slug,
  'iterationSequenceNumber': sequence,
};

void main() {
  group('canonieke productselectie', () {
    final products = [
      _product('eerste', 'Eerste'),
      _product('Tweede', 'Tweede'),
    ];

    test('herstelt alleen een exact unieke niet-lege String-slug', () {
      final restored = selectProductScope(products, 'Tweede');
      expect(restored.activeProduct, same(products[1]));
      expect(restored.activeSlug, 'Tweede');
      expect(restored.removeStoredPreference, isFalse);

      for (final invalid in <Object?>[
        null,
        '',
        'tweede',
        ' Tweede ',
        2,
        'verdwenen',
      ]) {
        final fallback = selectProductScope(products, invalid);
        expect(fallback.activeProduct, same(products.first));
        expect(
          fallback.removeStoredPreference,
          invalid != null,
          reason: 'voorkeur $invalid',
        );
      }
    });

    test('behoudt API-volgorde en sluit ongeldige en ambigue slugs uit', () {
      final first = _product('zelfde', 'Eerste duplicaat');
      final result = selectProductScope([
        _product(null, 'Ontbreekt'),
        _product(42, 'Verkeerd type'),
        _product('', 'Leeg'),
        first,
        _product('zelfde', 'Tweede duplicaat'),
      ], 'zelfde');

      expect(result.products.map((product) => product['name']), [
        'Eerste duplicaat',
        'Tweede duplicaat',
      ]);
      expect(result.activeProduct, same(first));
      expect(result.removeStoredPreference, isTrue);
    });

    test(
      'zonder producten bestaat geen scope en wordt voorkeur verwijderd',
      () {
        final result = selectProductScope([
          _product(null, 'Ontbreekt'),
          _product('', 'Leeg'),
        ], 'eerste');
        expect(result.products, isEmpty);
        expect(result.activeProduct, isNull);
        expect(result.removeStoredPreference, isTrue);
      },
    );
  });

  test('cyclus en story gebruiken uitsluitend exacte canonieke mapping', () {
    final exactIteration = _iteration('Scope', 7);
    final iterations = <dynamic>[
      exactIteration,
      _iteration('scope', 8),
      _iteration(' Scope ', 9),
      _iteration('', 10),
      _iteration(12, 11),
      _iteration('Scope', 12),
      _iteration('Scope', 12),
      _iteration('Scope', '13'),
      {'slug': 'Scope', 'sequenceNumber': 14},
    ];
    final exactCandidate = _candidate(1, 'Scope', 7);
    final candidates = <dynamic>[
      exactCandidate,
      _candidate(2, 'scope', 7),
      _candidate(3, 'Scope', 8),
      _candidate(4, 'Scope', 12),
      _candidate(5, 'Scope', '7'),
      _candidate(6, '', 7),
      _candidate(7, 12, 7),
      {'id': 8, 'productSlug': 'Scope'},
      'geen record',
    ];

    expect(iterationsInProductScope(iterations, 'Scope'), [
      exactIteration,
      iterations[5],
      iterations[6],
      iterations[7],
    ]);
    final linked = linkedStoriesInProductScope(
      candidates: candidates,
      iterations: iterations,
      productSlug: 'Scope',
    );
    expect(linked, [same(exactCandidate)]);
  });

  test('Beheer leidt levering uitsluitend via een unieke kandidaat af', () {
    final scopeCandidate = _candidate(1, 'Scope', 7);
    final candidates = <dynamic>[
      scopeCandidate,
      _candidate(2, 'Anders', 8),
      _candidate(3, 'Scope', 9),
      _candidate(3, 'Scope', 10),
      _candidate(4, '', 11),
      _candidate(5, 5, 12),
    ];
    final exactDelivery = {'candidateId': 1, 'productSlug': 'Anders'};
    final deliveries = <dynamic>[
      exactDelivery,
      {'candidateId': 2, 'productSlug': 'Scope'},
      {'candidateId': 3, 'productSlug': 'Scope'},
      {'candidateId': 4, 'productSlug': 'Scope'},
      {'candidateId': 5, 'productSlug': 'Scope'},
      {'candidateId': '1', 'productSlug': 'Scope'},
      {'productSlug': 'Scope'},
    ];

    expect(candidatesInManagementScope(candidates, 'Scope'), [
      same(scopeCandidate),
      same(candidates[2]),
      same(candidates[3]),
    ]);
    expect(
      deliveriesInManagementScope(
        deliveries: deliveries,
        candidates: candidates,
        productSlug: 'Scope',
      ),
      [same(exactDelivery)],
    );
  });
}
