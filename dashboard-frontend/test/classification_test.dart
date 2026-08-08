import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/classification.dart';

void main() {
  group('classifyIterationOutcome', () {
    test('ACCEPTED wordt richting-gekozen', () {
      expect(
        classifyIterationOutcome(status: 'ACCEPTED', criticVerdict: 'ACCEPT'),
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

    test('FAILED wordt guardrail-conflict', () {
      expect(
        classifyIterationOutcome(
          status: 'FAILED',
          errorMessage: 'Workspace-publicatie tijdelijk niet beschikbaar.',
        ),
        kGuardrailConflict,
      );
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

    test('fallback: onbekende of ontbrekende statuswaarde', () {
      expect(
        classifyIterationOutcome(status: 'ONVOORZIEN'),
        kOnderzoekOnvoldoende,
      );
      expect(classifyIterationOutcome(status: null), kOnderzoekOnvoldoende);
    });

    test('elke classificatiewaarde zit in de vaste toegestane lijst', () {
      for (final status in [
        'ACCEPTED',
        'NEEDS_REVISION',
        'REJECTED',
        'FAILED',
        'QUEUED',
        'RUNNING',
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
