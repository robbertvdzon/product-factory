import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_frontend/build_identity.dart';

void main() {
  test('geldige buildidentiteit blijft volledig zichtbaar', () {
    final identity = BuildIdentity.validated(
      applicationVersion: '0.1.0',
      apiVersion: '1',
      gitRevision: '0123456789abcdef0123456789abcdef01234567',
      buildTime: '2026-08-24T18:00:00Z',
      environment: 'production',
      buildIdentity: '0.1.0+0123456789ab',
    );

    expect(identity.applicationVersion, '0.1.0');
    expect(identity.gitRevision, hasLength(40));
    expect(identity.environment, 'production');
  });

  test('ongeldige buildmetadata wordt gesloten als Onbekend weergegeven', () {
    final identity = BuildIdentity.validated(
      applicationVersion: 'snapshot',
      apiVersion: 'first',
      gitRevision: 'main',
      buildTime: 'gisteren',
      environment: 'staging',
      buildIdentity: '',
    );

    expect(identity.applicationVersion, BuildIdentity.unknown);
    expect(identity.apiVersion, BuildIdentity.unknown);
    expect(identity.gitRevision, BuildIdentity.unknown);
    expect(identity.buildTime, BuildIdentity.unknown);
    expect(identity.environment, BuildIdentity.unknown);
    expect(identity.buildIdentity, BuildIdentity.unknown);
  });
}
