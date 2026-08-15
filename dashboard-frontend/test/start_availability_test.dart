import 'dart:collection';

import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/start_availability.dart';

const Object _absent = Object();

enum _ExpectedCategory { sufficient, insufficient, unknown }

class _Case {
  const _Case(this.name, this.value, this.category, this.label);

  final String name;
  final Object? value;
  final _ExpectedCategory category;
  final String label;
}

const _statusCases = [
  _Case(
    'geldig en voldoende',
    'active',
    _ExpectedCategory.sufficient,
    'Actief',
  ),
  _Case(
    'geldig maar onvoldoende: draft',
    'draft',
    _ExpectedCategory.insufficient,
    'Niet actief',
  ),
  _Case(
    'geldig maar onvoldoende: paused',
    'paused',
    _ExpectedCategory.insufficient,
    'Niet actief',
  ),
  _Case(
    'geldig maar onvoldoende: archived',
    'archived',
    _ExpectedCategory.insufficient,
    'Niet actief',
  ),
  _Case('ontbrekende sleutel', _absent, _ExpectedCategory.unknown, 'Onbekend'),
  _Case('null', null, _ExpectedCategory.unknown, 'Onbekend'),
  _Case('lege tekst', '', _ExpectedCategory.unknown, 'Onbekend'),
  _Case('onbekende tekst', 'ACTIVE', _ExpectedCategory.unknown, 'Onbekend'),
  _Case('onbekend type', 7, _ExpectedCategory.unknown, 'Onbekend'),
  _Case(
    'niet-getrimde tekst',
    ' active',
    _ExpectedCategory.unknown,
    'Onbekend',
  ),
];

const _workspaceCases = [
  _Case(
    'geldig en voldoende',
    'product-factory',
    _ExpectedCategory.sufficient,
    'Door Product Factory beheerd',
  ),
  _Case(
    'geldig maar onvoldoende',
    'owner',
    _ExpectedCategory.insufficient,
    'Niet door Product Factory beheerd',
  ),
  _Case('ontbrekende sleutel', _absent, _ExpectedCategory.unknown, 'Onbekend'),
  _Case('null', null, _ExpectedCategory.unknown, 'Onbekend'),
  _Case('lege tekst', '', _ExpectedCategory.unknown, 'Onbekend'),
  _Case(
    'onbekende tekst',
    'PRODUCT-FACTORY',
    _ExpectedCategory.unknown,
    'Onbekend',
  ),
  _Case('onbekend type', true, _ExpectedCategory.unknown, 'Onbekend'),
  _Case(
    'niet-getrimde tekst',
    'product-factory ',
    _ExpectedCategory.unknown,
    'Onbekend',
  ),
];

Map<String, dynamic> _product(_Case status, _Case workspace) => {
  if (!identical(status.value, _absent)) 'status': status.value,
  if (!identical(workspace.value, _absent))
    'workspaceOwnership': workspace.value,
};

String? _expectedPrimaryReason(_Case status, _Case workspace) {
  if (status.category == _ExpectedCategory.unknown ||
      workspace.category == _ExpectedCategory.unknown) {
    return kStartAvailabilityUnknownReason;
  }
  if (status.category == _ExpectedCategory.insufficient) {
    return kStartAvailabilityInactiveReason;
  }
  if (workspace.category == _ExpectedCategory.insufficient) {
    return kStartAvailabilityWorkspaceReason;
  }
  return null;
}

class _ReadTrackingMap extends MapBase<String, dynamic> {
  _ReadTrackingMap(this._values);

  final Map<String, dynamic> _values;
  final Set<Object?> accessedKeys = {};

  @override
  dynamic operator [](Object? key) {
    accessedKeys.add(key);
    return _values[key];
  }

  @override
  bool containsKey(Object? key) {
    accessedKeys.add(key);
    return _values.containsKey(key);
  }

  @override
  void operator []=(String key, dynamic value) =>
      throw UnsupportedError('read-only testmap');

  @override
  void clear() => throw UnsupportedError('read-only testmap');

  @override
  Iterable<String> get keys => _values.keys;

  @override
  dynamic remove(Object? key) => throw UnsupportedError('read-only testmap');
}

void main() {
  group('StartAvailability.fromProduct', () {
    for (final status in _statusCases) {
      for (final workspace in _workspaceCases) {
        test('status ${status.name} × workspace ${workspace.name}', () {
          final availability = StartAvailability.fromProduct(
            _product(status, workspace),
          );
          final expectedUnmetConditions = [
            if (status.category != _ExpectedCategory.sufficient)
              kProductMustBeActive,
            if (workspace.category != _ExpectedCategory.sufficient)
              kWorkspaceMustBeManaged,
          ];

          expect(
            availability.canStart,
            status.category == _ExpectedCategory.sufficient &&
                workspace.category == _ExpectedCategory.sufficient,
          );
          expect(
            availability.primaryReason,
            _expectedPrimaryReason(status, workspace),
          );
          expect(
            availability.additionalReason,
            expectedUnmetConditions.length == 2
                ? kStartAvailabilityAdditionalReason
                : null,
          );
          expect(availability.productStatusLabel, status.label);
          expect(availability.workspaceOwnershipLabel, workspace.label);
          expect(availability.unmetConditions, expectedUnmetConditions);
        });
      }
    }

    test(
      'leest uitsluitend status en workspaceOwnership uit het geselecteerde product',
      () {
        final product = _ReadTrackingMap({
          'slug': 'technische-id-die-niet-mag-worden-getoond',
          'name': 'Ander product dat niet mag worden getoond',
          'status': 'mystery-raw-status',
          'workspaceOwnership': {'raw': 'workspace-secret'},
          'mission': 'vertrouwelijke configuratie',
          'iterations': [
            {'status': 'RUNNING', 'startedAt': '2020-01-01T00:00:00Z'},
          ],
        });

        final availability = StartAvailability.fromProduct(product);
        final renderedValues = [
          availability.primaryReason,
          availability.additionalReason,
          availability.productStatusLabel,
          availability.workspaceOwnershipLabel,
          ...availability.unmetConditions,
        ].join(' ');

        expect(product.accessedKeys, {'status', 'workspaceOwnership'});
        expect(availability.canStart, isFalse);
        expect(renderedValues, isNot(contains('mystery-raw-status')));
        expect(renderedValues, isNot(contains('workspace-secret')));
        expect(renderedValues, isNot(contains('technische-id')));
        expect(renderedValues, isNot(contains('RUNNING')));
      },
    );
  });
}
