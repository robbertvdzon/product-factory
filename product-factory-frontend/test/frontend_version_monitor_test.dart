import 'package:flutter_test/flutter_test.dart';
import 'package:product_factory_frontend/build_identity.dart';
import 'package:product_factory_frontend/frontend_version_monitor.dart';

void main() {
  test('een afwijkende geldige build levert precies één vernieuwmelding', () {
    final tracker = VersionUpdateTracker();
    final current = identity('0.1.0+aaaaaaaaaaaa');
    final latest = identity('0.1.0+bbbbbbbbbbbb');

    expect(tracker.shouldNotify(current, latest), isTrue);
    expect(tracker.shouldNotify(current, latest), isFalse);
  });

  test('gelijke of onbekende builds leveren geen vernieuwmelding', () {
    final tracker = VersionUpdateTracker();
    final current = identity('0.1.0+aaaaaaaaaaaa');

    expect(tracker.shouldNotify(current, current), isFalse);
    expect(
      tracker.shouldNotify(current, identity(BuildIdentity.unknown)),
      isFalse,
    );
  });
}

BuildIdentity identity(String buildIdentity) => BuildIdentity(
  applicationVersion: '0.1.0',
  apiVersion: '1',
  gitRevision: ''.padLeft(40, 'a'),
  buildTime: '2026-08-24T18:00:00Z',
  environment: 'production',
  buildIdentity: buildIdentity,
);
