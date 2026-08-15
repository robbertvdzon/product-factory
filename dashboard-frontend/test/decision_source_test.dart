import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/classification.dart';

void main() {
  group('iterationDecisionPresentation', () {
    test('expliciet record heeft voorrang op FAILED en errorMessage', () {
      final result = iterationDecisionPresentation({
        'id': 'iteration-1',
        'status': 'FAILED',
        'criticVerdict': null,
        'errorMessage': 'Deze technische fallback mag niet winnen',
        'decision': {
          'iterationId': 'iteration-1',
          'actorType': 'HUMAN',
          'mechanism': 'MANUAL_CANCELLATION',
          'reasonCode': 'MANUALLY_CANCELLED',
          'decidedAt': '2026-08-12T11:01:00Z',
        },
      });

      expect(result.sourceText, 'Beslisbron: Mens');
      expect(result.reasonText, 'Reden: Handmatig geannuleerd');
      expect(result.mechanism, 'Handmatige annulering');
      expect(result.derived, isFalse);
    });

    test(
      'record van een andere iterationId wordt niet aan deze cyclus gekoppeld',
      () {
        final result = iterationDecisionPresentation({
          'id': 'iteration-2',
          'status': 'FAILED',
          'criticVerdict': null,
          'errorMessage': 'Netwerkfout',
          'decision': {
            'iterationId': 'iteration-1',
            'actorType': 'HUMAN',
            'mechanism': 'MANUAL_CANCELLATION',
            'reasonCode': 'MANUALLY_CANCELLED',
            'decidedAt': '2026-08-12T11:01:00Z',
          },
        });

        expect(result.sourceText, 'Beslisbron: Technische fout (Afgeleid)');
        expect(result.reason, isNull);
      },
    );

    test(
      'historische fallback behoudt classificatie en markeert die afgeleid',
      () {
        final result = iterationDecisionPresentation({
          'status': 'FAILED',
          'criticVerdict': null,
          'errorMessage': 'Netwerkfout',
        });

        expect(result.sourceText, 'Beslisbron: Technische fout (Afgeleid)');
        expect(result.reason, isNull);
        expect(result.derived, isTrue);
      },
    );

    test('niet-classificeerbare historie blijft uitsluitend onbekend', () {
      final result = iterationDecisionPresentation({
        'status': 'TOEKOMSTIGE_STATUS',
        'criticVerdict': 'ONBEKEND',
        'errorMessage': null,
      });

      expect(result.sourceText, 'Beslisbron: Onbekend');
      expect(result.derived, isFalse);
      expect(result.source, isNot(kBeslisbronMens));
      expect(result.source, isNot(kBeslisbronTechnischeFout));
    });
  });

  group('classifyDecisionSource', () {
    test('exact reeds geleverd resultaat blijft een evaluatiebesluit', () {
      expect(
        classifyDecisionSource(
          criticVerdict: 'ACCEPT',
          status: 'NO_CHANGE',
          errorMessage: null,
        ),
        kBeslisbronEvaluatieAgent,
      );
    });
    const verdicts = ['ACCEPT', 'REVISE', 'REJECT', 'WARNING_ONLY_REVISE'];
    const statuses = ['ACCEPTED', 'NEEDS_REVISION', 'REJECTED', 'FAILED'];
    const provenCombinations = {
      'ACCEPT|ACCEPTED',
      'REVISE|NEEDS_REVISION',
      'REJECT|REJECTED',
    };

    for (final verdict in verdicts) {
      for (final status in statuses) {
        test('$verdict met $status wordt conservatief geclassificeerd', () {
          final result = classifyDecisionSource(
            criticVerdict: verdict,
            status: status,
            errorMessage: 'synthetische foutmelding',
          );
          expect(
            result,
            provenCombinations.contains('$verdict|$status')
                ? kBeslisbronEvaluatieAgent
                : kBeslisbronOnbekend,
          );
        });
      }
    }

    test('warning-only enginepad ACCEPT met ACCEPTED is Evaluatie-agent', () {
      expect(
        classifyDecisionSource(
          criticVerdict: 'ACCEPT',
          status: 'ACCEPTED',
          errorMessage: null,
        ),
        kBeslisbronEvaluatieAgent,
      );
    });

    for (final absentVerdict in <String?>[null, '', '   ', '\t\n']) {
      test('FAILED met afwezig verdict en foutmelding is Technische fout', () {
        expect(
          classifyDecisionSource(
            criticVerdict: absentVerdict,
            status: 'FAILED',
            errorMessage: '  netwerkverbinding verbroken  ',
          ),
          kBeslisbronTechnischeFout,
        );
      });
    }

    for (final absentError in <String?>[null, '', '   ', '\t\n']) {
      test('FAILED zonder niet-lege foutmelding is Onbekend', () {
        expect(
          classifyDecisionSource(
            criticVerdict: null,
            status: 'FAILED',
            errorMessage: absentError,
          ),
          kBeslisbronOnbekend,
        );
      });
    }

    for (final presentVerdict in [
      'ACCEPT',
      'REVISE',
      'REJECT',
      'WARNING_ONLY_REVISE',
      'ONBEKEND',
    ]) {
      test('FAILED met niet-leeg verdict $presentVerdict is Onbekend', () {
        expect(
          classifyDecisionSource(
            criticVerdict: presentVerdict,
            status: 'FAILED',
            errorMessage: 'technische details',
          ),
          kBeslisbronOnbekend,
        );
      });
    }

    test('witruimte wordt verwijderd voor de exacte vergelijking', () {
      expect(
        classifyDecisionSource(
          criticVerdict: '  REVISE ',
          status: '\nNEEDS_REVISION\t',
          errorMessage: null,
        ),
        kBeslisbronEvaluatieAgent,
      );
    });

    for (final values in <(String?, String?, String?)>[
      (null, null, null),
      ('', '', ''),
      ('   ', '\t', '\n'),
      ('accept', 'ACCEPTED', null),
      ('ACCEPT', 'accepted', null),
      ('ONBEKEND', 'ACCEPTED', null),
      ('ACCEPT', 'ONBEKEND', null),
      ('ACCEPT', 'REJECTED', null),
      ('WARNING_ONLY_REVISE', 'NEEDS_REVISION', null),
      (null, 'QUEUED', null),
      (null, 'RUNNING', null),
    ]) {
      test(
        'ontbrekende, onbekende of tegenstrijdige waarden zijn Onbekend',
        () {
          expect(
            classifyDecisionSource(
              criticVerdict: values.$1,
              status: values.$2,
              errorMessage: values.$3,
            ),
            kBeslisbronOnbekend,
          );
        },
      );
    }

    test(
      'uitputtende invoerset produceert uitsluitend de gesloten waarden',
      () {
        const inputs = <String?>[
          null,
          '',
          '   ',
          'ACCEPT',
          'REVISE',
          'REJECT',
          'WARNING_ONLY_REVISE',
          'ACCEPTED',
          'NEEDS_REVISION',
          'REJECTED',
          'FAILED',
          'ONBEKEND',
        ];
        final produced = <String>{};
        for (final verdict in inputs) {
          for (final status in inputs) {
            for (final error in inputs) {
              produced.add(
                classifyDecisionSource(
                  criticVerdict: verdict,
                  status: status,
                  errorMessage: error,
                ),
              );
            }
          }
        }

        expect(produced, equals(kBeslisbronnen.toSet()));
        expect(produced, isNot(contains('Mens')));
        expect(produced, isNot(contains('Guardrail')));
        expect(produced.difference(kBeslisbronnen.toSet()), isEmpty);
      },
    );
  });
}
