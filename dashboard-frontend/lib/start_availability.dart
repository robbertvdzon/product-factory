const String kStartAvailabilityUnknownReason =
    'Startbeschikbaarheid kan niet betrouwbaar worden vastgesteld.';
const String kStartAvailabilityInactiveReason =
    'Starten is niet beschikbaar omdat dit product niet actief is.';
const String kStartAvailabilityWorkspaceReason =
    'Starten is niet beschikbaar omdat deze workspace niet door Product Factory wordt beheerd.';
const String kStartAvailabilityAdditionalReason =
    'Daarnaast is nog 1 andere voorwaarde niet vervuld.';
const String kProductMustBeActive = 'Product moet actief zijn.';
const String kWorkspaceMustBeManaged =
    'Workspace moet door Product Factory worden beheerd.';

enum _MetadataCategory { sufficient, insufficient, unknown }

/// Eén verliesarm presentatiemodel voor de twee voorwaarden van de bestaande startactie.
///
/// Alleen [status] en [workspaceOwnership] worden uit het geselecteerde product gelezen. Andere
/// productvelden en alle cyclusdata kunnen de uitkomst daardoor niet beïnvloeden of in de
/// read-only detailweergave terechtkomen.
class StartAvailability {
  StartAvailability._({
    required this.canStart,
    required this.primaryReason,
    required this.additionalReason,
    required this.productStatusLabel,
    required this.workspaceOwnershipLabel,
    required List<String> unmetConditions,
  }) : unmetConditions = List.unmodifiable(unmetConditions);

  factory StartAvailability.fromProduct(Map<String, dynamic> product) {
    final status = _statusCategory(product);
    final workspaceOwnership = _workspaceCategory(product);
    final unmetConditions = <String>[
      if (status != _MetadataCategory.sufficient) kProductMustBeActive,
      if (workspaceOwnership != _MetadataCategory.sufficient)
        kWorkspaceMustBeManaged,
    ];
    final canStart = unmetConditions.isEmpty;

    String? primaryReason;
    if (!canStart) {
      if (status == _MetadataCategory.unknown ||
          workspaceOwnership == _MetadataCategory.unknown) {
        primaryReason = kStartAvailabilityUnknownReason;
      } else if (status == _MetadataCategory.insufficient) {
        primaryReason = kStartAvailabilityInactiveReason;
      } else {
        primaryReason = kStartAvailabilityWorkspaceReason;
      }
    }

    return StartAvailability._(
      canStart: canStart,
      primaryReason: primaryReason,
      additionalReason: unmetConditions.length == 2
          ? kStartAvailabilityAdditionalReason
          : null,
      productStatusLabel: switch (status) {
        _MetadataCategory.sufficient => 'Actief',
        _MetadataCategory.insufficient => 'Niet actief',
        _MetadataCategory.unknown => 'Onbekend',
      },
      workspaceOwnershipLabel: switch (workspaceOwnership) {
        _MetadataCategory.sufficient => 'Door Product Factory beheerd',
        _MetadataCategory.insufficient => 'Niet door Product Factory beheerd',
        _MetadataCategory.unknown => 'Onbekend',
      },
      unmetConditions: unmetConditions,
    );
  }

  final bool canStart;
  final String? primaryReason;
  final String? additionalReason;
  final String productStatusLabel;
  final String workspaceOwnershipLabel;
  final List<String> unmetConditions;

  static _MetadataCategory _statusCategory(Map<String, dynamic> product) {
    if (!product.containsKey('status')) return _MetadataCategory.unknown;
    return switch (product['status']) {
      'active' => _MetadataCategory.sufficient,
      'draft' || 'paused' || 'archived' => _MetadataCategory.insufficient,
      _ => _MetadataCategory.unknown,
    };
  }

  static _MetadataCategory _workspaceCategory(Map<String, dynamic> product) {
    if (!product.containsKey('workspaceOwnership')) {
      return _MetadataCategory.unknown;
    }
    return switch (product['workspaceOwnership']) {
      'product-factory' => _MetadataCategory.sufficient,
      'owner' => _MetadataCategory.insufficient,
      _ => _MetadataCategory.unknown,
    };
  }
}
