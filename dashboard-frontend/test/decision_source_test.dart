import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/classification.dart';

void main() {
  group('classifyDecisionSource', () {
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
