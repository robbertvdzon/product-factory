import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_dashboard/config.dart';
import 'package:product_factory_dashboard/environment_identity.dart';
import 'package:product_factory_dashboard/formatting.dart';

const _revision = '0123456789abcdef0123456789abcdef01234567';
const _deployedAt = '2026-08-15T18:04:19Z';

void main() {
  group('EnvironmentIdentityPresentation', () {
    test('normaliseert uitsluitend de drie gesloten omgevingswaarden', () {
      const supported = {
        'production': 'Productie',
        'acceptance': 'Acceptatie',
        'preview': 'Preview',
      };
      for (final entry in supported.entries) {
        expect(
          EnvironmentIdentityPresentation.fromBuildMetadata(
            environment: entry.key,
          ).environment,
          entry.value,
        );
      }

      for (final value in [null, '', 'Production', ' production', 'local', 1]) {
        expect(
          EnvironmentIdentityPresentation.fromBuildMetadata(
            environment: value,
          ).environment,
          kEnvironmentIdentityUnknown,
        );
      }
    });

    test('accepteert alleen een volledige hexadecimale bronrevisie', () {
      expect(
        EnvironmentIdentityPresentation.fromBuildMetadata(
          sourceRevision: _revision,
        ).revision,
        '0123456789ab',
      );
      expect(
        EnvironmentIdentityPresentation.fromBuildMetadata(
          sourceRevision: _revision.toUpperCase(),
        ).revision,
        '0123456789AB',
      );

      for (final value in [
        null,
        '',
        '0123456789ab',
        '${_revision}0',
        'g123456789abcdef0123456789abcdef01234567',
        '$_revision commitbericht',
        123,
      ]) {
        expect(
          EnvironmentIdentityPresentation.fromBuildMetadata(
            sourceRevision: value,
          ).revision,
          kEnvironmentIdentityUnknown,
        );
      }
    });

    test('accepteert alleen ISO-8601-tijden met expliciete tijdzone', () {
      for (final value in [
        _deployedAt,
        '2026-08-15T20:04:19+02:00',
        '2026-08-15T20:04+0200',
        '2026-08-15T18:04:19.123456Z',
      ]) {
        expect(
          EnvironmentIdentityPresentation.fromBuildMetadata(
            deployedAt: value,
          ).deployedAt,
          formatDateTime(DateTime.parse(value)),
        );
      }

      for (final value in [
        null,
        '',
        '2026-08-15T18:04:19',
        '2026-08-15 18:04:19Z',
        '2026-02-30T18:04:19Z',
        '2026-08-15T25:04:19Z',
        '2026-08-15T18:04:19+24:00',
        '2026-08-15T18:04:19+02:60',
        'morgen om twaalf uur',
        123,
      ]) {
        expect(
          EnvironmentIdentityPresentation.fromBuildMetadata(
            deployedAt: value,
          ).deployedAt,
          kEnvironmentIdentityUnknown,
        );
      }
    });

    test('valideert ieder veld onafhankelijk', () {
      final missingEnvironment =
          EnvironmentIdentityPresentation.fromBuildMetadata(
            sourceRevision: _revision,
            deployedAt: _deployedAt,
          );
      expect(missingEnvironment.environment, kEnvironmentIdentityUnknown);
      expect(missingEnvironment.revision, '0123456789ab');
      expect(
        missingEnvironment.deployedAt,
        formatDateTime(DateTime.parse(_deployedAt)),
      );

      final invalidRevision = EnvironmentIdentityPresentation.fromBuildMetadata(
        environment: 'acceptance',
        sourceRevision: 'ongeldig',
        deployedAt: _deployedAt,
      );
      expect(invalidRevision.environment, 'Acceptatie');
      expect(invalidRevision.revision, kEnvironmentIdentityUnknown);
      expect(
        invalidRevision.deployedAt,
        formatDateTime(DateTime.parse(_deployedAt)),
      );

      final invalidTime = EnvironmentIdentityPresentation.fromBuildMetadata(
        environment: 'preview',
        sourceRevision: _revision,
        deployedAt: '2026-08-15T18:04:19',
      );
      expect(invalidTime.environment, 'Preview');
      expect(invalidTime.revision, '0123456789ab');
      expect(invalidTime.deployedAt, kEnvironmentIdentityUnknown);
    });

    test('bewaart of toont geen ongeldige gevoelige invoer', () {
      const sentinels = [
        'feat: toon productie-identiteit',
        'Voornaam Achternaam',
        'persoon@example.invalid',
        'ghp_TOKENACHTIGEWAARDE',
        'https://git.internal.invalid/organisatie/repository',
        'SECRET_CONFIG=verborgen',
      ];
      for (final sentinel in sentinels) {
        final identity = EnvironmentIdentityPresentation.fromBuildMetadata(
          environment: sentinel,
          sourceRevision: sentinel,
          deployedAt: sentinel,
        );
        expect(identity.environment, kEnvironmentIdentityUnknown);
        expect(identity.revision, kEnvironmentIdentityUnknown);
        expect(identity.deployedAt, kEnvironmentIdentityUnknown);
        expect(
          '${identity.environment}${identity.revision}${identity.deployedAt}',
          isNot(contains(sentinel)),
        );
      }
    });

    test('lokale builddefaults blijven per veld veilig bruikbaar', () {
      expect(AppConfig.buildEnvironment, isEmpty);
      expect(AppConfig.sourceRevision, isEmpty);
      expect(AppConfig.deployedAt, isEmpty);
      expect(AppConfig.environmentIdentity.environment, 'Onbekend');
      expect(AppConfig.environmentIdentity.revision, 'Onbekend');
      expect(AppConfig.environmentIdentity.deployedAt, 'Onbekend');
    });
  });
}
