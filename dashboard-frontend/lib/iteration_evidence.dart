library;

import 'classification.dart';
import 'formatting.dart';

const String kEvidenceUnknown = 'Onbekend';

const Set<String> kProductFactoryEvidenceStatuses = {
  'ACCEPTED',
  'NEEDS_REVISION',
  'REJECTED',
  'NO_CHANGE',
  'FAILED',
};

/// Houdt de speciale bewijsweergave strikt afgebakend tot het bedoelde product
/// en de expliciet ondersteunde eindstatussen.
bool shouldShowIterationEvidence(Map<String, dynamic> iteration) =>
    iteration['productSlug'] == 'product-factory' &&
    kProductFactoryEvidenceStatuses.contains(iteration['status']);

/// Gebruikersgerichte operationele reden. De optionele fallback voorkomt dat
/// een onbekende backendcode als ruwe waarde in een privacy-minimale weergave
/// terechtkomt; bestaande detail- en kaartweergaven behouden standaard hun
/// huidige verliesvrije fallback.
String outcomeReasonLabel(
  String reason, {
  String? unknownFallback,
}) => switch (reason) {
  'ACCEPT' => 'Alle kandidaten zijn leverbaar',
  'PARTIAL_ACCEPT' =>
    'Minstens één kandidaat is leverbaar; andere vragen nog werk',
  'ALREADY_DELIVERED' => 'Het resultaat was al eerder geleverd',
  'CANDIDATE_REVISE' =>
    'Een kandidaat heeft nog een lokale inhoudelijke reparatie nodig',
  'RESEARCH_GAP' => 'Noodzakelijke brononderbouwing ontbreekt',
  'POLICY_CONFLICT' => 'Er is nog een privacy-, rechten- of beleidsconflict',
  'OWNER_DECISION_REQUIRED' => 'Een echte beslissing van de eigenaar is nodig',
  'DELIVERY_DEPENDENCY_UNRESOLVED' =>
    'Technische levering mislukt: een story-afhankelijkheid werd niet herkend',
  'NO_DELIVERABLE_CANDIDATE' =>
    'Technische levering leverde geen bruikbare kandidaat op',
  'TECHNICAL_FAILURE' => 'De cyclus is door een technische fout gestopt',
  'REJECT' => 'De gekozen richting is fundamenteel afgewezen',
  _ => unknownFallback ?? reason,
};

bool isExplicitManualCancellation(Map<String, dynamic> iteration) {
  final decision = iteration['decision'];
  return iteration['status'] == 'FAILED' &&
      decision is Map &&
      decision['iterationId'] == iteration['id'] &&
      decision['actorType'] == 'HUMAN' &&
      decision['mechanism'] == 'MANUAL_CANCELLATION' &&
      decision['reasonCode'] == 'MANUALLY_CANCELLED';
}

class IterationEvidencePresentation {
  const IterationEvidencePresentation({
    required this.date,
    required this.outcome,
    required this.reason,
    required this.decisionSource,
  });

  final String date;
  final String outcome;
  final String reason;
  final String decisionSource;
}

/// Bouwt uitsluitend de vijf veilige presentatiewaarden van een bewijsregel uit
/// de bestaande formatterings-, classificatie-, reden- en provenancelogica.
IterationEvidencePresentation iterationEvidencePresentation(
  Map<String, dynamic> iteration,
) {
  final decision = iterationDecisionPresentation(iteration);
  final manualCancellation = isExplicitManualCancellation(iteration);
  final rawReason = iteration['outcomeReason'];
  final status = iteration['status'];
  final criticVerdict = iteration['criticVerdict'];
  final errorMessage = iteration['errorMessage'];
  final evidenceDate =
      parseInstant(iteration['startedAt']) ??
      parseInstant(iteration['createdAt']);

  return IterationEvidencePresentation(
    date: formatDateTime(evidenceDate, fallback: kEvidenceUnknown),
    outcome: manualCancellation
        ? kRedenHandmatigGeannuleerd
        : classifyIterationOutcome(
            status: status is String ? status : null,
            criticVerdict: criticVerdict is String ? criticVerdict : null,
            errorMessage: errorMessage is String ? errorMessage : null,
          ),
    reason: manualCancellation
        ? decision.reason ?? kEvidenceUnknown
        : rawReason is String && rawReason.trim().isNotEmpty
        ? outcomeReasonLabel(rawReason, unknownFallback: kEvidenceUnknown)
        : kEvidenceUnknown,
    decisionSource: manualCancellation
        ? decision.source
        : decision.derived
        ? '${decision.source} (Afgeleid)'
        : kEvidenceUnknown,
  );
}
