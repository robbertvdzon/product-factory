import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/classification.dart';

void main() {
  group('canResumeIteration', () {
    test('staat revisie en herstelbare leveringsfouten toe', () {
      expect(
        canResumeIteration(status: 'NEEDS_REVISION', outcomeReason: null),
        isTrue,
      );
      expect(
        canResumeIteration(
          status: 'FAILED',
          outcomeReason: 'DELIVERY_DEPENDENCY_UNRESOLVED',
        ),
        isTrue,
      );
      expect(
        canResumeIteration(
          status: 'FAILED',
          outcomeReason: 'NO_DELIVERABLE_CANDIDATE',
        ),
        isTrue,
      );
    });

    test('staat gewone technische fouten en echte afwijzingen niet toe', () {
      expect(
        canResumeIteration(
          status: 'FAILED',
          outcomeReason: 'TECHNICAL_FAILURE',
        ),
        isFalse,
      );
      expect(
        canResumeIteration(status: 'REJECTED', outcomeReason: 'REJECT'),
        isFalse,
      );
    });
  });

  group('classifyIterationOutcome', () {
    test('ACCEPTED wordt richting-gekozen', () {
      expect(
        classifyIterationOutcome(status: 'ACCEPTED', criticVerdict: 'ACCEPT'),
        kRichtingGekozen,
      );
    });

    test('NO_CHANGE wordt richting-gekozen zonder dubbele levering', () {
      expect(
        classifyIterationOutcome(status: 'NO_CHANGE', criticVerdict: 'ACCEPT'),
        kRichtingGekozen,
      );
    });

    test('NEEDS_REVISION wordt onderzoek-onvoldoende', () {
      expect(
        classifyIterationOutcome(
          status: 'NEEDS_REVISION',
          criticVerdict: 'REVISE',
        ),
        kOnderzoekOnvoldoende,
      );
    });

    test('REJECTED wordt richting-verworpen', () {
      expect(
        classifyIterationOutcome(status: 'REJECTED', criticVerdict: 'REJECT'),
        kRichtingVerworpen,
      );
    });

    test('FAILED wordt technische fout', () {
      final classification = classifyIterationOutcome(
        status: 'FAILED',
        errorMessage: 'Workspace-publicatie tijdelijk niet beschikbaar.',
      );
      expect(classification, kTechnischeFout);
      expect(classification, isNot(kGuardrailConflict));
      expect(classification, contains('technische fout'));
      expect(classification, isNot(contains('guardrail')));
    });

    test(
      'fallback: lopende/wachtende iteraties (QUEUED/RUNNING) zonder ondubbelzinnige uitkomst',
      () {
        expect(
          classifyIterationOutcome(status: 'QUEUED'),
          kOnderzoekOnvoldoende,
        );
        expect(
          classifyIterationOutcome(status: 'RUNNING'),
          kOnderzoekOnvoldoende,
        );
      },
    );

    test(
      'fallback: onbekende of ontbrekende statuswaarde wordt niet-classificeerbaar',
      () {
        expect(
          classifyIterationOutcome(status: 'ONVOORZIEN'),
          kNietClassificeerbaar,
        );
        expect(classifyIterationOutcome(status: null), kNietClassificeerbaar);
        expect(classifyIterationOutcome(status: ''), kNietClassificeerbaar);
      },
    );

    test(
      'representatief voor een tijdens uitvoering afgebroken iteratie: een onbekende, '
      'niet-QUEUED/RUNNING statuswaarde mapt deterministisch naar niet-classificeerbaar '
      '(er bestaat geen apart CANCELLED-statusveld in het datamodel)',
      () {
        expect(
          classifyIterationOutcome(status: 'CANCELLED'),
          kNietClassificeerbaar,
        );
        // Determinisme: herhaalde aanroepen met dezelfde onbekende status geven altijd hetzelfde
        // resultaat.
        expect(
          classifyIterationOutcome(status: 'CANCELLED'),
          kNietClassificeerbaar,
        );
      },
    );

    test('elke classificatiewaarde zit in de vaste toegestane lijst', () {
      for (final status in [
        'ACCEPTED',
        'NO_CHANGE',
        'NEEDS_REVISION',
        'REJECTED',
        'FAILED',
        'QUEUED',
        'RUNNING',
        'ONVOORZIEN',
        null,
      ]) {
        expect(
          kIterationClassifications,
          contains(classifyIterationOutcome(status: status)),
        );
      }
    });
  });

  group('WCAG 2.1 AA-contrast per badgevariant', () {
    for (final classification in kIterationClassifications) {
      test('$classification haalt minimaal 4.5:1', () {
        final colors = kClassificationColors[classification]!;
        final ratio = contrastRatio(colors.background, colors.foreground);
        expect(ratio, greaterThanOrEqualTo(4.5));
      });
    }

    test('contrastRatio is symmetrisch en 1.0 voor gelijke kleuren', () {
      final colors = kClassificationColors[kRichtingGekozen]!;
      expect(
        contrastRatio(colors.background, colors.foreground),
        contrastRatio(colors.foreground, colors.background),
      );
      expect(contrastRatio(colors.background, colors.background), 1.0);
    });
  });
}
