import 'environment_identity.dart';

abstract final class AppConfig {
  static const apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8081',
  );
  static const googleClientId = String.fromEnvironment('GOOGLE_CLIENT_ID');
  static const authRequired = bool.fromEnvironment(
    'AUTH_REQUIRED',
    defaultValue: false,
  );
  static const acceptanceDataset = bool.fromEnvironment(
    'ACCEPTANCE_DATASET',
    defaultValue: false,
  );
  static const buildEnvironment = String.fromEnvironment('BUILD_ENVIRONMENT');
  static const sourceRevision = String.fromEnvironment('SOURCE_REVISION');
  static const deployedAt = String.fromEnvironment('DEPLOYED_AT');

  static EnvironmentIdentityPresentation get environmentIdentity =>
      EnvironmentIdentityPresentation.fromBuildMetadata(
        environment: buildEnvironment,
        sourceRevision: sourceRevision,
        deployedAt: deployedAt,
      );
}
