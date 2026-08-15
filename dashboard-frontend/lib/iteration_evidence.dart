library;

import 'classification.dart';
import 'formatting.dart';

const String kEvidenceUnknown = 'Onbekend';

const Set<String> kTerminalIterationStatuses = {
  'ACCEPTED',
  'NEEDS_REVISION',
  'REJECTED',
  'NO_CHANGE',
  'FAILED',
};

const Set<String> kActiveIterationStatuses = {'QUEUED', 'RUNNING'};

enum IterationHistoryKind { terminal, active, unknown }

/// De productslug bepaalt alleen de scope en identificatie. De status bepaalt
/// voor ieder product welk veilige presentatiemodel van toepassing is.
IterationHistoryKind iterationHistoryKind(Map<String, dynamic> iteration) {
  final status = iteration['status'];
  if (kTerminalIterationStatuses.contains(status)) {
    return IterationHistoryKind.terminal;
  }
  if (kActiveIterationStatuses.contains(status)) {
    return IterationHistoryKind.active;
  }
  return IterationHistoryKind.unknown;
}

/// Compatibele selector voor de terminale bewijsregel.
bool shouldShowIterationEvidence(Map<String, dynamic> iteration) =>
    iterationHistoryKind(iteration) == IterationHistoryKind.terminal;

class IterationProgressPresentation {
  const IterationProgressPresentation({
    required this.status,
    this.currentStep,
    this.progress,
  });

  final String status;
  final String? currentStep;
  final String? progress;
}

/// Actieve backendstatussen en rollen worden alleen via een gesloten mapping
/// getoond. Daardoor kan een onbekende vrije waarde niet als stap of voortgang
/// in het compacte overzicht terechtkomen.
IterationProgressPresentation iterationProgressPresentation(
  Map<String, dynamic> iteration,
) {
  final status = iteration['status'];
  final active = kActiveIterationStatuses.contains(status);
  final rawRole = iteration['currentRole'];
  final role = rawRole is String ? rawRole.trim().toUpperCase() : null;
  const roleLabels = {
    'RESEARCHER': 'Onderzoeker',
    'PRODUCT_OWNER': 'Product owner',
    'UX_DESIGNER': 'UX-ontwerp',
    'STORY_WRITER': 'Story writer',
    'CRITIC': 'Criticus',
    'SUMMARY': 'Samenvatting',
  };

  return IterationProgressPresentation(
    status: switch (status) {
      'QUEUED' => 'In wachtrij',
      'RUNNING' => 'Bezig',
      _ => kEvidenceUnknown,
    },
    currentStep: active ? roleLabels[role] : null,
    progress: switch (status) {
      'QUEUED' => 'Wacht op uitvoering',
      'RUNNING' => 'Wordt uitgevoerd',
      _ => null,
    },
  );
}

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
        : decision.derived && decision.source != kBeslisbronOnbekend
        ? '${decision.source} (Afgeleid)'
        : kEvidenceUnknown,
  );
}
